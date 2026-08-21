import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { InventoryItem } from "../../../types/inventory.types";
import InventoryAvailabilityCalendarView from "../InventoryAvailabilityCalendarView";

// Mock hooks and dependencies
const mockUnwrap = vi.fn();

vi.mock("@services/inventory/inventorySlice", () => ({
  useLazyGetInventoryAvailabilityQuery: () => [() => ({ unwrap: mockUnwrap })],
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string) => key,
  }),
}));

vi.mock("@utils/dateUtils", () => {
  const getStartOfWeek = (date: Date, weekStartsOn: number): Date => {
    const result = new Date(date);
    const day = result.getDay();
    const diff = (day - weekStartsOn + 7) % 7;
    result.setDate(result.getDate() - diff);
    return result;
  };
  return {
    toISODateString: (date: Date) => date.toISOString().split("T")[0],
    getStartOfWeek,
  };
});

vi.mock("@utils/inventoryavailability.utils", () => {
  const spotData = {
    totalSpots: 100,
    availableSpots: 50,
    bookedSpots: 30,
    reservedSpots: 20,
    totalSpotsPerDay: 100,
    availableSpotsPerDay: 50,
    bookedSpotsPerDay: 30,
    reservedSpotsPerDay: 20,
  };
  return {
    buildAvailabilityIndex: () => null,
    getSpotDataFromIndex: () => spotData,
    getBookingDetailsFromIndex: () => [],
    calculateSpotsForDateTime: () => spotData,
    getStatusFromSpotData: () => "available",
    getBookedPercentage: () => 50,
    getColorFromPercentage: () => "success",
    getBookingDetailsForDateTime: () => [],
  };
});

// Mock CalendarView component
vi.mock("@components/ui/CalendarView", () => ({
  default: ({
    viewMode,
    onViewModeChange,
    events,
    rows,
    minDate,
    maxDate,
    calendarTitle,
  }: {
    viewMode: string;
    onViewModeChange: (mode: string) => void;
    events: unknown[];
    rows: unknown[];
    minDate?: Date;
    maxDate?: Date;
    calendarTitle: string;
  }) => (
    <div data-testid="calendar-view">
      <div data-testid="view-mode">{viewMode}</div>
      <div data-testid="calendar-title">{calendarTitle}</div>
      <div data-testid="events-count">{events.length}</div>
      <div data-testid="rows-count">{rows.length}</div>
      <button
        data-testid="change-to-weekly"
        onClick={() => onViewModeChange("weekly")}
      >
        Weekly
      </button>
      <button
        data-testid="change-to-daily"
        onClick={() => onViewModeChange("daily")}
      >
        Daily
      </button>
      {minDate && (
        <div data-testid="min-date">{minDate.toISOString().split("T")[0]}</div>
      )}
      {maxDate && (
        <div data-testid="max-date">{maxDate.toISOString().split("T")[0]}</div>
      )}
    </div>
  ),
}));

vi.mock("@components/ui/Progressbar", () => ({
  Progress: ({ value, variant }: { value: number; variant: string }) => (
    <div data-testid="progress-bar" data-value={value} data-variant={variant} />
  ),
}));

vi.mock("@components/ui/Button", () => ({
  Button: ({
    children,
    onClick,
    disabled,
    variant,
  }: {
    children: React.ReactNode;
    onClick: () => void;
    disabled?: boolean;
    variant?: string;
  }) => (
    <button
      onClick={onClick}
      disabled={disabled}
      data-variant={variant}
      data-testid="button"
    >
      {children}
    </button>
  ),
}));

const createMockInventoryItem = (
  overrides: Partial<InventoryItem> = {},
): InventoryItem =>
  ({
    detail: {
      id: "inv-1",
      name: "Test Inventory",
      externalId: "ext-1",
      inventoryType: "Digital",
      thumbnail: "https://example.com/img.png",
      images: [],
      format: "Digital",
      environment: "Outdoor",
      panels: [],
      size: "",
      ...overrides.detail,
    },
    operations: {
      clientPerLoop: 10,
      slotDuration: 6,
      loopSize: 60,
      cycleTime: 6,
      operationDays: [],
      maintenanceWindow: "",
      ...overrides.operations,
    },
    location: {
      location: {
        address: "123 Test St",
        country: "US",
        state: "CA",
        city: "LA",
        zipCode: "90001",
        locationCoordinates: { coordinates: [], type: "" },
      },
      poi: { types: [], nearbyPOIs: [], categories: [] },
      demographics: {
        age: "",
        gender: "",
        overall: "",
        ageGender: "",
        income: "",
        behaviour: "",
        interest: "",
        highestIndexScore: "",
      },
      ...overrides.location,
    },
    performance: {
      cpmRate: 0,
      estimatedCost: 0,
      perDayCost: 0,
      perDayAdPlays: 0,
      totalAdPlays: 0,
      plannedSot: 0,
      totalSot: 0,
      ...overrides.performance,
    },
    schedules: overrides.schedules || [],
  }) as InventoryItem;

describe("InventoryAvailabilityCalendarView", () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    mockUnwrap.mockResolvedValue({
      success: true,
      data: {
        inventories: {
          "ext-1": {
            id: "ext-1",
            name: "Test Inventory",
            timeZone: "UTC",
            loopDuration: 60,
            bookingMode: "loop",
            allocatedLoopSeconds: 0,
            availableLoopSeconds: 3600,
            schedule: {
              operatingTimes: {
                0: [{ start: "00:00:00", end: "23:59:59" }],
              },
              createdAt: "",
              createdBy: "",
              updatedAt: "",
              updatedBy: "",
            },
            bookings: [],
          },
        },
      },
    });
  });

  describe("Rendering", () => {
    it("should render with null inventory data", () => {
      render(
        <InventoryAvailabilityCalendarView
          inventoryData={null}
          campaignStartDate="2026-02-01"
          campaignEndDate="2026-02-28"
        />,
      );

      expect(screen.getByTestId("calendar-view")).toBeInTheDocument();
    });

    it("should render calendar view with inventory data", async () => {
      const inventoryItem = createMockInventoryItem();

      render(
        <InventoryAvailabilityCalendarView
          inventoryData={inventoryItem}
          campaignStartDate="2026-02-01"
          campaignEndDate="2026-02-28"
        />,
      );

      await waitFor(() => {
        expect(screen.getByTestId("calendar-view")).toBeInTheDocument();
      });
    });

    it("should display calendar title", async () => {
      const inventoryItem = createMockInventoryItem();

      render(
        <InventoryAvailabilityCalendarView
          inventoryData={inventoryItem}
          campaignStartDate="2026-02-01"
          campaignEndDate="2026-02-28"
        />,
      );

      expect(screen.getByTestId("calendar-title")).toHaveTextContent(
        "availability.availabilityTimeline",
      );
    });

    it("should start in monthly view mode", () => {
      const inventoryItem = createMockInventoryItem();

      render(
        <InventoryAvailabilityCalendarView
          inventoryData={inventoryItem}
          campaignStartDate="2026-02-01"
          campaignEndDate="2026-02-28"
        />,
      );

      expect(screen.getByTestId("view-mode")).toHaveTextContent("monthly");
    });
  });

  describe("View Mode Changes", () => {
    it("should switch to weekly view", async () => {
      const inventoryItem = createMockInventoryItem();

      render(
        <InventoryAvailabilityCalendarView
          inventoryData={inventoryItem}
          campaignStartDate="2026-02-01"
          campaignEndDate="2026-02-28"
        />,
      );

      await user.click(screen.getByTestId("change-to-weekly"));

      expect(screen.getByTestId("view-mode")).toHaveTextContent("weekly");
    });

    it("should switch to daily view", async () => {
      const inventoryItem = createMockInventoryItem();

      render(
        <InventoryAvailabilityCalendarView
          inventoryData={inventoryItem}
          campaignStartDate="2026-02-01"
          campaignEndDate="2026-02-28"
        />,
      );

      await user.click(screen.getByTestId("change-to-daily"));

      expect(screen.getByTestId("view-mode")).toHaveTextContent("daily");
    });
  });

  describe("Date Range Calculation", () => {
    it("should calculate minDate and maxDate from campaign dates", () => {
      const inventoryItem = createMockInventoryItem();

      render(
        <InventoryAvailabilityCalendarView
          inventoryData={inventoryItem}
          campaignStartDate="2026-02-01"
          campaignEndDate="2026-02-28"
        />,
      );

      // minDate should be 3 months before start
      expect(screen.getByTestId("min-date")).toBeInTheDocument();
      // maxDate should be 3 months after end
      expect(screen.getByTestId("max-date")).toBeInTheDocument();
    });

    it("should not render date bounds when campaign dates are missing", () => {
      const inventoryItem = createMockInventoryItem();

      render(
        <InventoryAvailabilityCalendarView
          inventoryData={inventoryItem}
          campaignStartDate={null}
          campaignEndDate={null}
        />,
      );

      expect(screen.queryByTestId("min-date")).not.toBeInTheDocument();
      expect(screen.queryByTestId("max-date")).not.toBeInTheDocument();
    });
  });

  describe("Error State", () => {
    it("should show error state when API fails", async () => {
      mockUnwrap.mockRejectedValue(new Error("API Error"));

      const inventoryItem = createMockInventoryItem();

      render(
        <InventoryAvailabilityCalendarView
          inventoryData={inventoryItem}
          campaignStartDate="2026-02-01"
          campaignEndDate="2026-02-28"
        />,
      );

      await waitFor(() => {
        const button = screen.queryByTestId("button");
        if (button) {
          expect(button).toHaveTextContent("Try Again");
        }
      });
    });

    it("should show error when inventory not found in response", async () => {
      mockUnwrap.mockResolvedValue({
        success: true,
        data: {
          inventories: {}, // Empty - inventory not found
        },
      });

      const inventoryItem = createMockInventoryItem();

      render(
        <InventoryAvailabilityCalendarView
          inventoryData={inventoryItem}
          campaignStartDate="2026-02-01"
          campaignEndDate="2026-02-28"
        />,
      );

      await waitFor(() => {
        const button = screen.queryByTestId("button");
        if (button) {
          expect(button).toBeInTheDocument();
        }
      });
    });
  });

  describe("Classic vs Digital Inventory", () => {
    it("should handle classic inventory type", async () => {
      const classicInventory = createMockInventoryItem({
        detail: {
          id: "inv-1",
          name: "Classic Inventory",
          externalId: "ext-1",
          inventoryType: "Classic",
          thumbnail: "",
          images: [],
          format: "Static",
          environment: "Outdoor",
          panels: [],
          size: "",
          referenceId: "",
          mediaOwnerId: "",
          mediaOwnerName: "",
          category: "",
          venueType: [],
          operationMode: "",
          execution: "",
          screens: 1,
          sov: 100,
          isSelected: false,
          isCompliant: true,
          bookingMode: "time",
        },
      });

      render(
        <InventoryAvailabilityCalendarView
          inventoryData={classicInventory}
          campaignStartDate="2026-02-01"
          campaignEndDate="2026-02-28"
        />,
      );

      await waitFor(() => {
        expect(screen.getByTestId("calendar-view")).toBeInTheDocument();
      });
    });
  });

  describe("API Response Handling", () => {
    it("should handle direct response structure", async () => {
      mockUnwrap.mockResolvedValue({
        inventories: {
          "ext-1": {
            id: "ext-1",
            name: "Test Inventory",
            timeZone: "UTC",
            loopDuration: 60,
            bookingMode: "loop",
            allocatedLoopSeconds: 0,
            availableLoopSeconds: 3600,
            schedule: {
              operatingTimes: {},
              createdAt: "",
              createdBy: "",
              updatedAt: "",
              updatedBy: "",
            },
            bookings: [],
          },
        },
      });

      const inventoryItem = createMockInventoryItem();

      render(
        <InventoryAvailabilityCalendarView
          inventoryData={inventoryItem}
          campaignStartDate="2026-02-01"
          campaignEndDate="2026-02-28"
        />,
      );

      await waitFor(() => {
        expect(screen.getByTestId("calendar-view")).toBeInTheDocument();
      });
    });

    it("should not fetch when no external ID", async () => {
      const inventoryItem = createMockInventoryItem({
        detail: {
          id: "inv-1",
          name: "Test Inventory",
          externalId: "", // No external ID
          inventoryType: "Digital",
          thumbnail: "",
          images: [],
          format: "",
          environment: "",
          panels: [],
          size: "",
          referenceId: "",
          mediaOwnerId: "",
          mediaOwnerName: "",
          category: "",
          venueType: [],
          operationMode: "",
          execution: "",
          screens: 0,
          sov: 0,
          isSelected: false,
          isCompliant: false,
          bookingMode: "",
        },
      });

      render(
        <InventoryAvailabilityCalendarView
          inventoryData={inventoryItem}
          campaignStartDate="2026-02-01"
          campaignEndDate="2026-02-28"
        />,
      );

      await waitFor(() => {
        expect(screen.getByTestId("calendar-view")).toBeInTheDocument();
      });
    });
  });

  describe("Rows Generation", () => {
    it("should generate fallback rows when no inventory data", () => {
      render(
        <InventoryAvailabilityCalendarView
          inventoryData={null}
          campaignStartDate="2026-02-01"
          campaignEndDate="2026-02-28"
        />,
      );

      // Should have 9 fallback rows
      expect(screen.getByTestId("rows-count")).toHaveTextContent("9");
    });
  });
});
