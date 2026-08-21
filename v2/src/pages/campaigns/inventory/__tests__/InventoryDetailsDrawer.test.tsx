import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { InventoryDetailsDrawer } from "../InventoryDetailsDrawer";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const mockInventoryDetailsResponse = [
  {
    id: "ext-1",
    name: "Test Inventory",
    address: "123 Main St",
    adminLevel0Name: "Singapore",
    adminLevel1Name: "Central",
    adminLevel2Name: "Singapore",
    postalCode: "123456",
    referenceId: "ref-1",
    mediaOwnerId: "mo-1",
    mediaOwnerName: "Media Owner A",
    typeName: "Digital",
    typePath: "OUTDOOR",
    displayFormatName: "Static",
    environment: "Outdoor",
    size: "xl",
    thumbnailUrl: "",
    medias: [],
    geoms: ["POINT(103.8 1.35)"],
    schedule: {
      operatingTimes: { MONDAY: [{ start: "08:00", end: "22:00" }] },
    },
    panels: [],
    prices: [{ cpm: 10 }],
    digitalFields: {
      bookingMode: "spot",
      playerCount: 1,
      loopDuration: 60,
      spotDuration: 15,
      spotsPerLoop: 4,
    },
    venues: [{ name: "Billboard" }],
  },
];

const mockUseGetInventoryDetailsQuery = vi.fn(
  (args: { inventoryId: string }, options?: { skip?: boolean }) => {
    const skip = options?.skip ?? !args.inventoryId?.trim();
    if (skip) return { data: undefined };
    return {
      data: mockInventoryDetailsResponse,
    };
  },
);

vi.mock("@services/inventory/inventorySlice", () => ({
  useGetInventoryDetailsQuery: (
    args: { inventoryId: string },
    options?: { skip?: boolean },
  ) => mockUseGetInventoryDetailsQuery(args, options),
}));

vi.mock("@components/ui/Mapbox", () => ({
  default: function MapboxMock() {
    return <div data-testid="mapbox-wrapper">Map</div>;
  },
}));

vi.mock("@components/common/InventoryAvailabilityCalendarView", () => ({
  default: () => <div data-testid="availability-calendar">Availability</div>,
}));

describe("InventoryDetailsDrawer", () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    campaignStartDate: null,
    campaignEndDate: null,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseGetInventoryDetailsQuery.mockImplementation(
      (args: { inventoryId: string }, options?: { skip?: boolean }) => {
        const skip = options?.skip ?? !args.inventoryId?.trim();
        if (skip) return { data: undefined };
        return {
          data: mockInventoryDetailsResponse,
        };
      },
    );
  });

  describe("rendering", () => {
    it("returns null when externalInventoryId is not provided (query skipped)", () => {
      const { container } = render(
        <InventoryDetailsDrawer {...defaultProps} />,
      );
      expect(container.firstChild).toBeNull();
    });

    it("returns null when externalInventoryId is empty string (query skipped)", () => {
      const { container } = render(
        <InventoryDetailsDrawer {...defaultProps} externalInventoryId="" />,
      );
      expect(container.firstChild).toBeNull();
    });

    it("returns null when externalInventoryId is whitespace only (query skipped)", () => {
      const { container } = render(
        <InventoryDetailsDrawer {...defaultProps} externalInventoryId="   " />,
      );
      expect(mockUseGetInventoryDetailsQuery).toHaveBeenCalledWith(
        { inventoryId: "   " },
        { skip: true },
      );
      expect(container.firstChild).toBeNull();
    });

    it("renders drawer with inventory details when externalInventoryId is provided and query returns data", () => {
      render(
        <InventoryDetailsDrawer
          {...defaultProps}
          externalInventoryId="ext-1"
        />,
      );
      expect(screen.getByText("Test Inventory")).toBeInTheDocument();
    });

    it("renders details tab content by default", () => {
      render(
        <InventoryDetailsDrawer
          {...defaultProps}
          externalInventoryId="ext-1"
        />,
      );
      expect(
        screen.getByText("inventoryDetails.inventoryInformations"),
      ).toBeInTheDocument();
    });

    it("renders all tab triggers", () => {
      render(
        <InventoryDetailsDrawer
          {...defaultProps}
          externalInventoryId="ext-1"
        />,
      );
      expect(
        screen.getByRole("button", { name: "inventoryDetails.tabs.details" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", {
          name: "inventoryDetails.tabs.locationMap",
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", {
          name: "inventoryDetails.tabs.operations",
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", {
          name: "inventoryDetails.tabs.availability",
        }),
      ).toBeInTheDocument();
    });

    it("switches to location tab when clicked", async () => {
      const user = userEvent.setup();
      render(
        <InventoryDetailsDrawer
          {...defaultProps}
          externalInventoryId="ext-1"
        />,
      );
      await user.click(
        screen.getByRole("button", {
          name: "inventoryDetails.tabs.locationMap",
        }),
      );
      expect(
        screen.getByText("inventoryDetails.locationDetails"),
      ).toBeInTheDocument();
      expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
    });

    it("switches to operations tab when clicked", async () => {
      const user = userEvent.setup();
      render(
        <InventoryDetailsDrawer
          {...defaultProps}
          externalInventoryId="ext-1"
        />,
      );
      await user.click(
        screen.getByRole("button", {
          name: "inventoryDetails.tabs.operations",
        }),
      );
      expect(
        screen.getByText("inventoryDetails.operatingSchedule"),
      ).toBeInTheDocument();
    });

    it("switches to availability tab when clicked", async () => {
      const user = userEvent.setup();
      render(
        <InventoryDetailsDrawer
          {...defaultProps}
          externalInventoryId="ext-1"
        />,
      );
      await user.click(
        screen.getByRole("button", {
          name: "inventoryDetails.tabs.availability",
        }),
      );
      expect(screen.getByTestId("availability-calendar")).toBeInTheDocument();
    });
  });

  describe("query behavior", () => {
    it("calls useGetInventoryDetailsQuery with inventoryId and skip false when externalInventoryId is provided", () => {
      render(
        <InventoryDetailsDrawer
          {...defaultProps}
          externalInventoryId="ext-1"
        />,
      );
      expect(mockUseGetInventoryDetailsQuery).toHaveBeenCalledWith(
        { inventoryId: "ext-1" },
        { skip: false },
      );
    });

    it("calls useGetInventoryDetailsQuery with skip true when externalInventoryId is empty", () => {
      render(
        <InventoryDetailsDrawer {...defaultProps} externalInventoryId="" />,
      );
      expect(mockUseGetInventoryDetailsQuery).toHaveBeenCalledWith(
        { inventoryId: "" },
        { skip: true },
      );
    });
  });

  describe("close behavior", () => {
    it("calls onClose when Escape key is pressed", async () => {
      const onClose = vi.fn();
      render(
        <InventoryDetailsDrawer
          isOpen={true}
          onClose={onClose}
          externalInventoryId="ext-1"
          campaignStartDate={null}
          campaignEndDate={null}
        />,
      );
      await userEvent.keyboard("{Escape}");
      expect(onClose).toHaveBeenCalled();
    });
  });

  describe("details content", () => {
    it("displays media owner, type, format, size from inventory details", () => {
      render(
        <InventoryDetailsDrawer
          {...defaultProps}
          externalInventoryId="ext-1"
        />,
      );
      expect(screen.getByText("Media Owner A")).toBeInTheDocument();
      // Type is derived from typeName + venue category, not shown raw -
      // "Billboard" venue is non-transit, so it's the plain Digital label.
      expect(
        screen.getByText("inventoryDetails.typeLabel.digital"),
      ).toBeInTheDocument();
      expect(screen.getByText("Static")).toBeInTheDocument();
      // Size code comes back lowercase from the API - displayed uppercased.
      expect(screen.getByText("XL")).toBeInTheDocument();
    });

    it("shows the Transit type label when the venue is a transit category", () => {
      mockUseGetInventoryDetailsQuery.mockImplementationOnce(
        (args: { inventoryId: string }, options?: { skip?: boolean }) => {
          const skip = options?.skip ?? !args.inventoryId?.trim();
          if (skip) return { data: undefined };
          return {
            data: [
              {
                ...mockInventoryDetailsResponse[0],
                venues: [{ name: "Bus" }],
              },
            ],
          };
        },
      );
      render(
        <InventoryDetailsDrawer
          {...defaultProps}
          externalInventoryId="ext-1"
        />,
      );
      expect(
        screen.getByText("inventoryDetails.typeLabel.digitalTransit"),
      ).toBeInTheDocument();
    });

    it("displays no image placeholder when medias is empty", () => {
      render(
        <InventoryDetailsDrawer
          {...defaultProps}
          externalInventoryId="ext-1"
        />,
      );
      expect(
        screen.getByText("inventoryDetails.noImageAvailable"),
      ).toBeInTheDocument();
    });
  });
});
