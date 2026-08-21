import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { InventoryMapViewDrawer } from "../InventoryMapViewDrawer";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

// The mobility heatmap hook uses an RTK Query hook; stub the module so these
// tests don't need a Redux store.
vi.mock("@components/map/MobilityHeatmap", () => ({
  toCountrySlug: (name?: string) =>
    name ? name.toLowerCase().replace(/\s+/g, "-") : undefined,
  useMobilityHeatmapLayer: () => ({
    isLoading: false,
    isError: false,
    pointCount: 0,
    isEmpty: false,
  }),
  MobilityHeatmapControl: () => null,
}));

// InventoryMapView pulls availability via an RTK query hook; stub it so these
// tests don't need a Redux store.
vi.mock("../view/useSelectedInventoryAvailability", () => ({
  useSelectedInventoryAvailability: () => ({
    availabilityById: {},
    requestAvailability: vi.fn(),
  }),
}));

// Capture the PopupComponent prop that the drawer passes to MapBoxWrapper so we
// can render the otherwise-private InventoryMapPopupComponent closure directly.
let capturedPopupComponent: React.ComponentType<{ item: unknown }> | undefined;
let loadItemsOnMount = false;

vi.mock("@components/ui/Mapbox", () => ({
  default: function MockMapBox({
    PopupComponent,
  }: {
    PopupComponent?: React.ComponentType<{ item: unknown }>;
  }) {
    capturedPopupComponent = PopupComponent;
    return React.createElement("div", {
      "data-testid": "mapbox-wrapper",
      children: "Map",
    });
  },
}));

vi.mock("@components/common/SelectedInventoryListContainer", () => ({
  SelectedInventoryListContainer: React.forwardRef(
    function MockSelectedInventoryListContainer(
      {
        emptyMessage,
        onInitialLoad,
      }: {
        emptyMessage?: string;
        onInitialLoad?: (items: unknown[]) => void;
      },
      ref: React.Ref<{ refetch: () => void }>,
    ) {
      if (ref && typeof ref === "object" && "current" in ref) {
        (ref as React.MutableRefObject<{ refetch: () => void }>).current = {
          refetch: vi.fn(),
        };
      }
      // Drive the drawer so the map mounts: it only renders MapBoxWrapper when
      // selectedItems.length > 0, which is populated via onInitialLoad. Only do
      // this when a test opts in, so empty-state tests keep working.
      React.useEffect(() => {
        if (loadItemsOnMount) onInitialLoad?.(mapTestItems);
        // eslint-disable-next-line react-hooks/exhaustive-deps
      }, []);
      return React.createElement("div", {
        "data-testid": "selected-inventory-list",
        children: React.createElement("span", {
          children: emptyMessage || "No inventories found",
        }),
      });
    },
  ),
}));

const placeholderThumbnail = "../../../../img/MW-logo-trans_1754045676555.png";

const mapTestItems = [
  {
    detail: {
      id: "inv-1",
      name: "Inventory One",
      mediaOwnerName: "Owner A",
      thumbnail: placeholderThumbnail,
      inventoryType: "Digital",
      format: "Billboard",
      environment: "Outdoor",
      panels: [],
    },
    location: {
      location: {
        address: "123 Street",
        country: "JP",
        state: "Tokyo",
        latitude: 35.6,
        longitude: 139.7,
      },
    },
    performance: { estimatedCost: 100 },
  },
];

describe("InventoryMapViewDrawer", () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    campaignId: "campaign-1",
  };

  beforeEach(() => {
    vi.clearAllMocks();
    capturedPopupComponent = undefined;
    loadItemsOnMount = false;
  });

  describe("rendering", () => {
    it("renders nothing when isOpen is false", () => {
      render(<InventoryMapViewDrawer {...defaultProps} isOpen={false} />);
      expect(
        screen.queryByText("inventoryMapView.title"),
      ).not.toBeInTheDocument();
    });

    it("renders drawer with title Selected Inventories when open", () => {
      render(<InventoryMapViewDrawer {...defaultProps} />);
      expect(screen.getByText("inventoryMapView.title")).toBeInTheDocument();
    });

    it("renders SelectedInventoryListContainer when open", () => {
      render(<InventoryMapViewDrawer {...defaultProps} />);
      expect(screen.getByTestId("selected-inventory-list")).toBeInTheDocument();
    });

    it("does not render map when selectedItems is empty", () => {
      render(<InventoryMapViewDrawer {...defaultProps} />);
      expect(screen.queryByTestId("mapbox-wrapper")).not.toBeInTheDocument();
    });

    it("renders empty message in list container", () => {
      render(<InventoryMapViewDrawer {...defaultProps} />);
      expect(
        screen.getByText("inventoryMapView.emptyMessage"),
      ).toBeInTheDocument();
    });
  });

  describe("props", () => {
    it("accepts showDeleteInventoryButton false", () => {
      render(
        <InventoryMapViewDrawer
          {...defaultProps}
          showDeleteInventoryButton={false}
        />,
      );
      expect(screen.getByTestId("selected-inventory-list")).toBeInTheDocument();
    });
  });

  describe("map popup image (PL3-I61)", () => {
    it("renders the map when items are loaded and captures the popup component", () => {
      loadItemsOnMount = true;
      render(<InventoryMapViewDrawer {...defaultProps} />);
      expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      expect(capturedPopupComponent).toBeDefined();
    });

    it("constrains the popup image size so the placeholder logo renders compact", () => {
      loadItemsOnMount = true;
      render(<InventoryMapViewDrawer {...defaultProps} />);

      const PopupComponent = capturedPopupComponent;
      expect(PopupComponent).toBeDefined();

      const { container } = render(
        React.createElement(PopupComponent!, { item: mapTestItems[0] }),
      );
      const img = container.querySelector("img") as HTMLImageElement;
      expect(img).toBeTruthy();
      expect(img.className).toContain("object-cover");
      expect(img.className).toContain("h-32");
      expect(img.className).toContain("w-full");
    });
  });

  describe("close", () => {
    it("calls onClose when drawer close button is clicked", async () => {
      const user = userEvent.setup();
      render(<InventoryMapViewDrawer {...defaultProps} />);
      const closeButton = document.querySelector(
        "[id='modal-drawer-close']",
      ) as HTMLElement;
      if (closeButton) {
        await user.click(closeButton);
        expect(defaultProps.onClose).toHaveBeenCalled();
      }
    });
  });
});
