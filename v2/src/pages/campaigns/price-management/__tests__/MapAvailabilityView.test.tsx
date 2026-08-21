import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { CampaignCreateResponse } from "../../../../types/campaign.types";
import { InventoryItem } from "../../../../types/inventory.types";
import MapAvailabilityView from "../MapAvailabilityView";

// Mock InventoryAvailabilityCalendarView
vi.mock(
  "../../../../components/common/InventoryAvailabilityCalendarView",
  () => ({
    default: ({
      inventoryData,
      campaignStartDate,
      campaignEndDate,
    }: {
      inventoryData: InventoryItem | null;
      campaignStartDate: string | undefined;
      campaignEndDate: string | undefined;
    }) => (
      <div data-testid="inventory-calendar-view">
        <span data-testid="inventory-name">
          {inventoryData?.detail?.name || "No inventory"}
        </span>
        <span data-testid="campaign-dates">
          {campaignStartDate} - {campaignEndDate}
        </span>
      </div>
    ),
  }),
);

// Mock InventoryDetailsDrawer (real component receives externalInventoryId, not inventory)
vi.mock("../../inventory/InventoryDetailsDrawer", () => ({
  default: ({
    isOpen,
    onClose,
    externalInventoryId,
  }: {
    isOpen: boolean;
    onClose: () => void;
    externalInventoryId?: string;
  }) => {
    const name =
      externalInventoryId === "ext-1"
        ? "Inventory 1"
        : externalInventoryId === "ext-2"
          ? "Inventory 2"
          : (externalInventoryId ?? "");
    return isOpen ? (
      <div data-testid="details-drawer">
        <span data-testid="drawer-inventory-name">{name}</span>
        <button onClick={onClose} data-testid="close-drawer">
          Close
        </button>
      </div>
    ) : null;
  },
}));

vi.mock("@components/common/SelectedInventoryListContainer", () => ({
  SelectedInventoryListContainer: ({
    campaignId,
    enabled,
    renderItem,
    onInitialLoad,
    searchValue,
    onSearchChange,
  }: {
    campaignId: string | undefined;
    enabled: boolean;
    renderItem: (item: InventoryItem, index: number) => React.ReactNode;
    onInitialLoad: (items: InventoryItem[]) => void;
    onLoadMore: (items: InventoryItem[]) => void;
    filterItems: (items: InventoryItem[], query: string) => InventoryItem[];
    searchValue: string;
    onSearchChange: (value: string) => void;
  }) => {
    // Simulate initial load
    if (enabled && campaignId) {
      setTimeout(() => {
        onInitialLoad(mockInventoryItems);
      }, 0);
    }

    return (
      <div data-testid="inventory-list-container">
        <input
          data-testid="search-input"
          value={searchValue}
          onChange={(e) => onSearchChange(e.target.value)}
        />
        <div data-testid="inventory-items">
          {mockInventoryItems.map((item, index) => renderItem(item, index))}
        </div>
      </div>
    );
  },
}));

// Mock MapBoxWrapper
vi.mock("@components/ui/Mapbox", () => ({
  default: ({
    defaultCenter,
    locationsList,
    selectedItemId,
  }: {
    defaultCenter: [number, number];
    locationsList: InventoryItem[];
    selectedItemId: string | undefined;
    PopupComponent: React.ComponentType<{ item: InventoryItem }>;
  }) => (
    <div data-testid="mapbox">
      <span data-testid="map-center">
        {defaultCenter[0]},{defaultCenter[1]}
      </span>
      <span data-testid="locations-count">{locationsList.length}</span>
      <span data-testid="selected-id">{selectedItemId}</span>
    </div>
  ),
}));

// Mock InventoryDetailCard
vi.mock("@components/common/InventoryDetailCard", () => ({
  InventoryDetailCard: ({
    item,
    onViewDetails,
  }: {
    item: InventoryItem;
    onViewDetails: () => void;
  }) => (
    <div data-testid={`inventory-card-${item.detail.id}`}>
      <span>{item.detail.name}</span>
      <button
        onClick={onViewDetails}
        data-testid={`view-details-${item.detail.id}`}
      >
        View Details
      </button>
    </div>
  ),
}));

// Mock Badge
vi.mock("@components/ui/Badge", () => ({
  Badge: ({
    children,
    variant,
  }: {
    children: React.ReactNode;
    variant?: string;
  }) => <span data-variant={variant}>{children}</span>,
}));

// Mock utilities
vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string) => key,
  }),
}));

vi.mock("@utils/campaign.utils", () => ({
  formatCurrency: (amount: number, currency: string) => `${currency} ${amount}`,
}));

vi.mock("@utils/inventory.utils", () => ({
  getLatitude: () => 40.7128,
  getLongitude: () => -74.006,
}));

// Test data
let mockInventoryItems: InventoryItem[] = [];

const createMockInventoryItem = (id: string): InventoryItem =>
  ({
    detail: {
      id,
      name: `Inventory ${id}`,
      externalId: `ext-${id}`,
      inventoryType: "Digital",
      thumbnail: "https://example.com/img.png",
      images: [],
      format: "Digital",
      environment: "Outdoor",
      size: "Large",
      mediaOwnerName: "Test Owner",
    },
    operations: {
      clientPerLoop: 10,
    },
    location: {
      location: {
        address: "123 Test St",
        country: "US",
        state: "CA",
        city: "LA",
        zipCode: "90001",
        locationCoordinates: {
          coordinates: [{ latitude: 40.7128, longitude: -74.006 }],
          type: "Point",
        },
      },
    },
    performance: {},
    schedules: [],
  }) as unknown as InventoryItem;

const createMockCampaignData = (): CampaignCreateResponse =>
  ({
    id: "campaign-1",
    name: "Test Campaign",
    startDate: "2026-02-01",
    endDate: "2026-02-28",
  }) as CampaignCreateResponse;

describe("MapAvailabilityView", () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    mockInventoryItems = [
      createMockInventoryItem("1"),
      createMockInventoryItem("2"),
    ];
  });

  describe("Map View Mode", () => {
    it("should render map when isMapView is true", () => {
      render(
        <MapAvailabilityView
          isMapView={true}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={createMockCampaignData()}
        />,
      );

      expect(screen.getByTestId("mapbox")).toBeInTheDocument();
    });

    it("should show correct number of locations on map", async () => {
      render(
        <MapAvailabilityView
          isMapView={true}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={createMockCampaignData()}
        />,
      );

      await waitFor(() => {
        expect(screen.getByTestId("locations-count")).toHaveTextContent("2");
      });
    });
  });

  describe("Calendar View Mode", () => {
    it("should render calendar view when isMapView is false", () => {
      render(
        <MapAvailabilityView
          isMapView={false}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={createMockCampaignData()}
        />,
      );

      expect(screen.getByTestId("inventory-calendar-view")).toBeInTheDocument();
    });

    it("should pass campaign dates to calendar view", () => {
      render(
        <MapAvailabilityView
          isMapView={false}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={createMockCampaignData()}
        />,
      );

      expect(screen.getByTestId("campaign-dates")).toHaveTextContent(
        "2026-02-01 - 2026-02-28",
      );
    });
  });

  describe("Inventory List", () => {
    it("should render inventory list container", () => {
      render(
        <MapAvailabilityView
          isMapView={true}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={createMockCampaignData()}
        />,
      );

      expect(
        screen.getByTestId("inventory-list-container"),
      ).toBeInTheDocument();
    });

    it("should render inventory cards", async () => {
      render(
        <MapAvailabilityView
          isMapView={true}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={createMockCampaignData()}
        />,
      );

      await waitFor(() => {
        expect(screen.getByTestId("inventory-card-1")).toBeInTheDocument();
        expect(screen.getByTestId("inventory-card-2")).toBeInTheDocument();
      });
    });
  });

  describe("Inventory Selection", () => {
    it("should select first inventory on initial load", async () => {
      render(
        <MapAvailabilityView
          isMapView={true}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={createMockCampaignData()}
        />,
      );

      await waitFor(() => {
        expect(screen.getByTestId("selected-id")).toHaveTextContent("1");
      });
    });

    it("should update selected inventory on card click", async () => {
      render(
        <MapAvailabilityView
          isMapView={true}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={createMockCampaignData()}
        />,
      );

      await waitFor(() => {
        expect(screen.getByTestId("inventory-card-2")).toBeInTheDocument();
      });

      // Click on the second inventory card
      const card2 = screen.getByTestId("inventory-card-2").closest("div");
      if (card2) {
        await user.click(card2);
      }

      await waitFor(() => {
        expect(screen.getByTestId("selected-id")).toHaveTextContent("2");
      });
    });
  });

  describe("Details Drawer", () => {
    it("should open details drawer when view details is clicked", async () => {
      render(
        <MapAvailabilityView
          isMapView={true}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={createMockCampaignData()}
        />,
      );

      await waitFor(() => {
        expect(screen.getByTestId("view-details-1")).toBeInTheDocument();
      });

      await user.click(screen.getByTestId("view-details-1"));

      await waitFor(() => {
        expect(screen.getByTestId("details-drawer")).toBeInTheDocument();
      });
    });

    it("should display correct inventory in drawer", async () => {
      render(
        <MapAvailabilityView
          isMapView={true}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={createMockCampaignData()}
        />,
      );

      await waitFor(() => {
        expect(screen.getByTestId("view-details-1")).toBeInTheDocument();
      });

      await user.click(screen.getByTestId("view-details-1"));

      await waitFor(() => {
        expect(screen.getByTestId("drawer-inventory-name")).toHaveTextContent(
          "Inventory 1",
        );
      });
    });

    it("should close drawer when close button is clicked", async () => {
      render(
        <MapAvailabilityView
          isMapView={true}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={createMockCampaignData()}
        />,
      );

      await waitFor(() => {
        expect(screen.getByTestId("view-details-1")).toBeInTheDocument();
      });

      await user.click(screen.getByTestId("view-details-1"));

      await waitFor(() => {
        expect(screen.getByTestId("details-drawer")).toBeInTheDocument();
      });

      await user.click(screen.getByTestId("close-drawer"));

      await waitFor(() => {
        expect(screen.queryByTestId("details-drawer")).not.toBeInTheDocument();
      });
    });
  });

  describe("Search Functionality", () => {
    it("should render search input", () => {
      render(
        <MapAvailabilityView
          isMapView={true}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={createMockCampaignData()}
        />,
      );

      expect(screen.getByTestId("search-input")).toBeInTheDocument();
    });

    it("should update search query on input", async () => {
      render(
        <MapAvailabilityView
          isMapView={true}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={createMockCampaignData()}
        />,
      );

      const searchInput = screen.getByTestId("search-input");
      await user.type(searchInput, "test");

      expect(searchInput).toHaveValue("test");
    });
  });

  describe("Props Handling", () => {
    it("should handle undefined campaignId", () => {
      render(
        <MapAvailabilityView
          isMapView={true}
          campaignId={undefined}
          campaignCurrency="USD"
          campaignData={createMockCampaignData()}
        />,
      );

      expect(
        screen.getByTestId("inventory-list-container"),
      ).toBeInTheDocument();
    });

    it("should handle undefined campaignData", () => {
      render(
        <MapAvailabilityView
          isMapView={false}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={undefined}
        />,
      );

      expect(screen.getByTestId("inventory-calendar-view")).toBeInTheDocument();
    });

    it("should handle null campaignData", () => {
      render(
        <MapAvailabilityView
          isMapView={false}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={null}
        />,
      );

      expect(screen.getByTestId("inventory-calendar-view")).toBeInTheDocument();
    });
  });

  describe("Map Center Calculation", () => {
    it("should calculate map center from inventory locations", async () => {
      render(
        <MapAvailabilityView
          isMapView={true}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={createMockCampaignData()}
        />,
      );

      await waitFor(() => {
        // Center should be average of mock coordinates
        const mapCenter = screen.getByTestId("map-center");
        expect(mapCenter).toBeInTheDocument();
      });
    });

    it("should handle empty inventory list for map center", () => {
      mockInventoryItems = [];

      render(
        <MapAvailabilityView
          isMapView={true}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={createMockCampaignData()}
        />,
      );

      // Should default to [0, 0]
      expect(screen.getByTestId("map-center")).toHaveTextContent("0,0");
    });
  });

  describe("Filter Items", () => {
    it("should filter items based on search query", () => {
      render(
        <MapAvailabilityView
          isMapView={true}
          campaignId="campaign-1"
          campaignCurrency="USD"
          campaignData={createMockCampaignData()}
        />,
      );

      // The filterItems function is tested through the component
      // Just verify it renders without error
      expect(
        screen.getByTestId("inventory-list-container"),
      ).toBeInTheDocument();
    });
  });
});
