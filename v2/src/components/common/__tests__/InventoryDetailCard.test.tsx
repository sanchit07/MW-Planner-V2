import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import {
  InventoryClassification,
  Environment,
  EmptyValueDisplay,
} from "../../../constants/inventory.constants";
import {
  InventoryItem,
  InventorySchedule,
} from "../../../types/inventory.types";
import { InventoryDetailCard } from "../InventoryDetailCard";

const mockFetchReachFrequency = vi.fn();

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("@utils/budget.utils", () => ({
  formatNumber: (n: number | undefined) => (n != null ? String(n) : "N/A"),
  normalizeGoalType: (goalType?: string) => {
    if (!goalType) return undefined;
    const normalized = goalType.toUpperCase().replace(/[\s%]+/g, "");
    if (normalized === "ADPLAYS" || normalized === "ADPLAY") return "ADPLAYS";
    if (normalized === "SOV" || normalized === "SHAREOFVOICE") return "SOV";
    if (normalized === "IMPRESSIONS") return "IMPRESSIONS";
    if (normalized === "REACH" || normalized === "UNIQUEUSERS") return "REACH";
    return normalized;
  },
}));

vi.mock("@utils/dateUtils", () => ({
  findDurationInDays: (startDateStr: string, endDateStr: string) => {
    const start = new Date(startDateStr);
    const end = new Date(endDateStr);
    if (isNaN(start.getTime()) || isNaN(end.getTime())) return 0;
    const msPerDay = 24 * 60 * 60 * 1000;
    const diffDays =
      Math.ceil((end.getTime() - start.getTime()) / msPerDay) + 1;
    return diffDays > 0 ? diffDays : 0;
  },
}));

vi.mock("@utils/inventory.utils", () => ({
  sortDaysStartingFromMonday: (days: string[]) => [...(days || [])].sort(),
}));

vi.mock("@utils/schedule.utils", () => ({
  formatSize: () => ({ name: "Large", colorClass: "text-blue-500" }),
}));

vi.mock("@services/inventory/inventorySlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/inventory/inventorySlice")>();
  return {
    ...actual,
    useLazyGetInventoryReachFrequencyQuery: () => [mockFetchReachFrequency, {}],
  };
});

const defaultItem: InventoryItem = {
  id: "1",
  detail: {
    id: "d1",
    name: "Test Inventory",
    externalId: "",
    referenceId: "REF-001",
    mediaOwnerId: "",
    mediaOwnerName: "Owner Co",
    inventoryType: InventoryClassification.DIGITAL,
    category: "",
    venueType: [],
    thumbnail: "https://example.com/img.png",
    images: [],
    format: "Digital",
    environment: Environment.OUTDOOR,
    size: "",
    operationMode: "",
    execution: "",
    screens: 10,
    sov: 15,
    isSelected: false,
    isCompliant: false,
    bookingMode: "",
    panels: [
      {
        size: "Large",
        pixelWidth: 0,
        pixelHeight: 0,
        physicalWidth: 0,
        physicalHeight: 0,
        panelCount: 1,
        unit: "",
      },
    ],
  },
  location: {
    location: {
      address: "123 Main St",
      country: "US",
      state: "CA",
      city: "LA",
      zipCode: "",
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
  },
  performance: {
    cpmRate: 100,
    estimatedCost: 5000,
    perDayCost: 0,
    perDayAdPlays: 5,
    totalAdPlays: 100,
    plannedSot: 2,
    totalSot: 24,
  },
  operations: {} as InventoryItem["operations"],
  schedules: [],
} as InventoryItem;

const defaultSchedule: InventorySchedule = {
  id: "s1",
  name: "Schedule 1",
  startDate: "2025-01-01",
  endDate: "2025-01-31",
  scheduleDays: ["MONDAY", "TUESDAY"],
  bookingMatrix: { "0": [9, 10, 11], "1": [14, 15] },
  duration: 15,
  spotsPerHour: 4,
  spotsPerLoop: 1,
  order: 0,
  impressions: 0,
  adPlays: 100,
  sov: 10,
  sot: 0,
  plannedSot: 0,
  pricing: 12000,
  discount: { value: 10, discountType: "VALUE" },
};

const defaultProps = {
  item: defaultItem,
  formatCurrency: (amount: number | null | undefined, currency?: string) =>
    amount != null ? `${currency ?? ""} ${amount}` : "-",
  tCampaigns: (key: string) => key,
  showSmartSuggestionScore: false,
};

describe("InventoryDetailCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchReachFrequency.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: [
            {
              impressions: 1000,
              reach: 500,
              frequency: 2,
              status: "COMPLETED",
            },
          ],
        }),
    });
  });

  describe("rendering", () => {
    it("renders item name and reference id", () => {
      render(<InventoryDetailCard {...defaultProps} />);
      expect(screen.getByText("Test Inventory")).toBeInTheDocument();
      expect(screen.getByText("REF-001")).toBeInTheDocument();
    });

    it("renders address when present", () => {
      render(<InventoryDetailCard {...defaultProps} />);
      expect(screen.getByText("123 Main St")).toBeInTheDocument();
    });

    it("renders country and state when address is missing", () => {
      const item = {
        ...defaultItem,
        location: {
          ...defaultItem.location,
          location: { ...defaultItem.location.location, address: "" },
        },
      };
      render(<InventoryDetailCard {...defaultProps} item={item} />);
      expect(screen.getByText("US, CA")).toBeInTheDocument();
    });

    it("renders badges for inventory type, format, environment", () => {
      render(<InventoryDetailCard {...defaultProps} />);
      expect(screen.getAllByText("Digital").length).toBeGreaterThanOrEqual(1);
      expect(
        screen.getByText("inventoryEnvironment.outdoor"),
      ).toBeInTheDocument();
    });

    it("renders a venue type badge when venueType is present", () => {
      render(
        <InventoryDetailCard
          {...defaultProps}
          item={{
            ...defaultItem,
            detail: { ...defaultItem.detail, venueType: ["Shopping Mall"] },
          }}
        />,
      );
      expect(screen.getByText("Shopping Mall")).toBeInTheDocument();
    });

    it("does not render a venue type badge when venueType is empty", () => {
      render(<InventoryDetailCard {...defaultProps} />);
      expect(screen.queryByText("Shopping Mall")).not.toBeInTheDocument();
    });

    it("collapses extra venue types into a +N badge showing the first two", () => {
      render(
        <InventoryDetailCard
          {...defaultProps}
          item={{
            ...defaultItem,
            detail: {
              ...defaultItem.detail,
              venueType: ["Outdoor", "Billboards", "Highway"],
            },
          }}
        />,
      );
      expect(screen.getByText("Outdoor, Billboards +1")).toBeInTheDocument();
    });

    it("hides image when fromPriceManagement is true", () => {
      render(<InventoryDetailCard {...defaultProps} fromPriceManagement />);
      const img = screen.queryByRole("img", { name: /REF-001/i });
      expect(img).not.toBeInTheDocument();
    });

    it("shows image when not fromPriceManagement", () => {
      render(<InventoryDetailCard {...defaultProps} />);
      const img = screen.getByRole("img", { name: "REF-001" });
      expect(img).toBeInTheDocument();
    });

    it("applies cardClassName", () => {
      const { container } = render(
        <InventoryDetailCard {...defaultProps} cardClassName="custom-card" />,
      );
      expect(container.querySelector(".custom-card")).toBeInTheDocument();
    });
  });

  describe("checkbox", () => {
    it("shows checkbox when showCheckbox is true", () => {
      render(
        <InventoryDetailCard
          {...defaultProps}
          showCheckbox
          onCheckboxChange={vi.fn()}
        />,
      );
      expect(screen.getByRole("checkbox")).toBeInTheDocument();
    });

    it("calls onCheckboxChange when checkbox is toggled", async () => {
      const user = userEvent.setup();
      const onCheckboxChange = vi.fn();
      render(
        <InventoryDetailCard
          {...defaultProps}
          showCheckbox
          checkboxChecked={false}
          onCheckboxChange={onCheckboxChange}
        />,
      );
      await user.click(screen.getByRole("checkbox"));
      expect(onCheckboxChange).toHaveBeenCalledWith(true);
    });

    it("disables checkbox when checkboxDisabled is true", () => {
      render(
        <InventoryDetailCard
          {...defaultProps}
          showCheckbox
          checkboxDisabled
          onCheckboxChange={vi.fn()}
        />,
      );
      expect(screen.getByRole("checkbox")).toBeDisabled();
    });
  });

  describe("expand and toggle", () => {
    it("shows expand button when not fromSchedule and not fromPriceManagement", () => {
      render(<InventoryDetailCard {...defaultProps} />);
      const buttons = screen.getAllByRole("button");
      expect(buttons.length).toBeGreaterThan(0);
    });

    it("expands and fetches reach frequency when expand button is clicked", async () => {
      const user = userEvent.setup();
      mockFetchReachFrequency.mockReturnValue({
        unwrap: () =>
          Promise.resolve({
            success: true,
            data: [
              {
                impressions: 1000,
                reach: 500,
                frequency: 2,
                status: "COMPLETED",
              },
            ],
          }),
      });
      render(<InventoryDetailCard {...defaultProps} campaignCurrency="USD" />);
      const expandButtons = screen.getAllByRole("button");
      const expandBtn = expandButtons[0];
      await user.click(expandBtn);
      await waitFor(() => {
        expect(
          screen.getByText("inventories.metrics.impressions"),
        ).toBeInTheDocument();
      });
      expect(mockFetchReachFrequency).toHaveBeenCalled();
      await waitFor(() => {
        expect(screen.getByText("1,000")).toBeInTheDocument();
      });
    });

    it("toggles expanded state when expand button is clicked twice", async () => {
      const user = userEvent.setup();
      mockFetchReachFrequency.mockReturnValue({
        unwrap: () =>
          Promise.resolve({
            success: true,
            data: [
              {
                impressions: 1000,
                reach: 500,
                frequency: 2,
                status: "COMPLETED",
              },
            ],
          }),
      });
      render(<InventoryDetailCard {...defaultProps} />);
      const expandBtn = screen.getAllByRole("button")[0];
      await user.click(expandBtn);
      await waitFor(() => {
        expect(
          screen.getByText("inventories.metrics.impressions"),
        ).toBeInTheDocument();
      });
      await user.click(expandBtn);
      expect(
        screen.queryByText("inventories.metrics.impressions"),
      ).not.toBeInTheDocument();
    });

    it("does not show expand toggle when fromSchedule", () => {
      const itemWithSchedules = {
        ...defaultItem,
        schedules: [defaultSchedule],
      };
      render(
        <InventoryDetailCard
          {...defaultProps}
          item={itemWithSchedules}
          fromSchedule
        />,
      );
      expect(
        screen.getByRole("button", { name: /Schedule 1/ }),
      ).toBeInTheDocument();
    });

    it("does not show expand toggle when fromPriceManagement", () => {
      render(<InventoryDetailCard {...defaultProps} fromPriceManagement />);
      expect(screen.getAllByRole("button")).toHaveLength(1);
      expect(
        screen.getByRole("button", { name: /inventories\.item\.view_details/ }),
      ).toBeInTheDocument();
    });
  });

  describe("footer and view details", () => {
    it("shows estimated cost in footer", () => {
      render(<InventoryDetailCard {...defaultProps} campaignCurrency="USD" />);
      expect(
        screen.getByText(/inventoryMapView\.estimatedCost/),
      ).toBeInTheDocument();
      expect(screen.getByText(/5000/)).toBeInTheDocument();
    });

    it("shows view details button when not fromSchedule", () => {
      render(<InventoryDetailCard {...defaultProps} onViewDetails={vi.fn()} />);
      expect(
        screen.getByRole("button", { name: /inventories\.item\.view_details/ }),
      ).toBeInTheDocument();
    });

    it("calls onViewDetails with item when view details is clicked", async () => {
      const user = userEvent.setup();
      const onViewDetails = vi.fn();
      render(
        <InventoryDetailCard {...defaultProps} onViewDetails={onViewDetails} />,
      );
      await user.click(
        screen.getByRole("button", { name: /inventories\.item\.view_details/ }),
      );
      expect(onViewDetails).toHaveBeenCalledTimes(1);
      expect(onViewDetails).toHaveBeenCalledWith(defaultProps.item);
    });

    it("hides view details button when fromSchedule", () => {
      render(
        <InventoryDetailCard
          {...defaultProps}
          fromSchedule
          onViewDetails={vi.fn()}
        />,
      );
      expect(
        screen.queryByRole("button", { name: /view_details/ }),
      ).not.toBeInTheDocument();
    });
  });

  describe("fromSchedule", () => {
    it("renders schedule dropdown and config when fromSchedule and schedules exist", () => {
      const itemWithSchedules = {
        ...defaultItem,
        schedules: [defaultSchedule],
      };
      render(
        <InventoryDetailCard
          {...defaultProps}
          item={itemWithSchedules}
          fromSchedule
        />,
      );
      expect(screen.getByText("Schedule 1")).toBeInTheDocument();
      expect(
        screen.getByText("viewCampaign.scheduleTab.scheduleConfiguration"),
      ).toBeInTheDocument();
    });

    it("calls handleScheduleChange when another schedule is selected", async () => {
      const user = userEvent.setup();
      const itemWithSchedules = {
        ...defaultItem,
        schedules: [
          defaultSchedule,
          { ...defaultSchedule, id: "s2", name: "Schedule 2" },
        ],
      };
      render(
        <InventoryDetailCard
          {...defaultProps}
          item={itemWithSchedules}
          fromSchedule
        />,
      );
      const trigger = screen.getByRole("button", { name: /Schedule 1/ });
      await user.click(trigger);
      await waitFor(() => {
        expect(
          screen.getByRole("menuitem", { name: /Schedule 2/ }),
        ).toBeInTheDocument();
      });
      await user.click(screen.getByRole("menuitem", { name: /Schedule 2/ }));
      expect(screen.getByText("Schedule 2")).toBeInTheDocument();
    });
  });

  describe("expanded content metrics", () => {
    it("shows CPM and estimated cost in expanded content", async () => {
      const user = userEvent.setup();
      render(<InventoryDetailCard {...defaultProps} campaignCurrency="USD" />);
      await user.click(screen.getAllByRole("button")[0]);
      await waitFor(() => {
        expect(
          screen.getByText("inventories.metrics.cpm_rate"),
        ).toBeInTheDocument();
      });
      expect(
        screen.getByText("inventories.metrics.est_cost"),
      ).toBeInTheDocument();
    });

    it("shows ad plays row when inventory type is not CLASSIC", async () => {
      const user = userEvent.setup();
      render(<InventoryDetailCard {...defaultProps} />);
      await user.click(screen.getAllByRole("button")[0]);
      await waitFor(() => {
        expect(
          screen.getByText("inventories.metrics.ad_plays"),
        ).toBeInTheDocument();
      });
      expect(screen.getByText("100")).toBeInTheDocument();
    });

    it("hides ad plays row when inventory type is CLASSIC", async () => {
      const user = userEvent.setup();
      const itemClassic = {
        ...defaultItem,
        detail: {
          ...defaultItem.detail,
          inventoryType: InventoryClassification.CLASSIC,
        },
      };
      render(<InventoryDetailCard {...defaultProps} item={itemClassic} />);
      await user.click(screen.getAllByRole("button")[0]);
      await waitFor(() => {
        expect(
          screen.getByText("inventories.metrics.impressions"),
        ).toBeInTheDocument();
      });
      expect(
        screen.queryByText("inventories.metrics.ad_plays"),
      ).not.toBeInTheDocument();
    });

    it("shows cps_rate label when goalType is SOV", async () => {
      const user = userEvent.setup();
      render(
        <InventoryDetailCard
          {...defaultProps}
          campaignCurrency="USD"
          goalType="SOV"
        />,
      );
      await user.click(screen.getAllByRole("button")[0]);
      await waitFor(() => {
        expect(
          screen.getByText("inventories.metrics.cps_rate"),
        ).toBeInTheDocument();
      });
      expect(
        screen.queryByText("inventories.metrics.cpm_rate"),
      ).not.toBeInTheDocument();
    });

    it("shows cps_rate label when goalType is ADPLAYS", async () => {
      const user = userEvent.setup();
      render(
        <InventoryDetailCard
          {...defaultProps}
          campaignCurrency="USD"
          goalType="ADPLAYS"
        />,
      );
      await user.click(screen.getAllByRole("button")[0]);
      await waitFor(() => {
        expect(
          screen.getByText("inventories.metrics.cps_rate"),
        ).toBeInTheDocument();
      });
    });

    it("shows spotRate value when goalType is SOV and spotRate is set", async () => {
      const user = userEvent.setup();
      const itemWithSpotRate = {
        ...defaultItem,
        performance: { ...defaultItem.performance, spotRate: 25.5 },
      };
      render(
        <InventoryDetailCard
          {...defaultProps}
          item={itemWithSpotRate}
          campaignCurrency="USD"
          goalType="SOV"
        />,
      );
      await user.click(screen.getAllByRole("button")[0]);
      await waitFor(() => {
        expect(
          screen.getByText("inventories.metrics.cps_rate"),
        ).toBeInTheDocument();
      });
      expect(screen.getByText(/25\.50/)).toBeInTheDocument();
    });

    it("shows NA when goalType is SOV and spotRate is missing", async () => {
      const user = userEvent.setup();
      const itemNoSpotRate = {
        ...defaultItem,
        performance: { ...defaultItem.performance, spotRate: undefined },
      };
      render(
        <InventoryDetailCard
          {...defaultProps}
          item={itemNoSpotRate}
          campaignCurrency="USD"
          goalType="SOV"
        />,
      );
      await user.click(screen.getAllByRole("button")[0]);
      await waitFor(() => {
        expect(
          screen.getByText("inventories.metrics.cps_rate"),
        ).toBeInTheDocument();
      });
      expect(screen.getByText("inventories.metrics.na")).toBeInTheDocument();
    });

    it("shows cpm_rate label when goalType is CPM", async () => {
      const user = userEvent.setup();
      render(
        <InventoryDetailCard
          {...defaultProps}
          campaignCurrency="USD"
          goalType="CPM"
        />,
      );
      await user.click(screen.getAllByRole("button")[0]);
      await waitFor(() => {
        expect(
          screen.getByText("inventories.metrics.cpm_rate"),
        ).toBeInTheDocument();
      });
      expect(
        screen.queryByText("inventories.metrics.cps_rate"),
      ).not.toBeInTheDocument();
    });

    it("shows estimated cost with 2 decimal places", async () => {
      const user = userEvent.setup();
      const itemWithCost = {
        ...defaultItem,
        performance: { ...defaultItem.performance, estimatedCost: 1234.5 },
      };
      render(
        <InventoryDetailCard
          {...defaultProps}
          item={itemWithCost}
          campaignCurrency="USD"
        />,
      );
      await user.click(screen.getAllByRole("button")[0]);
      await waitFor(() => {
        expect(
          screen.getByText("inventories.metrics.est_cost"),
        ).toBeInTheDocument();
      });
      expect(screen.getByText(/1234\.50/)).toBeInTheDocument();
    });

    it("uses emptyValueDisplay for missing reachFrequencyData", async () => {
      const user = userEvent.setup();
      mockFetchReachFrequency.mockReturnValue({
        unwrap: () => Promise.resolve({ success: true, data: null }),
      });
      render(
        <InventoryDetailCard
          {...defaultProps}
          emptyValueDisplay={EmptyValueDisplay.DASH}
        />,
      );
      await user.click(screen.getAllByRole("button")[0]);
      await waitFor(() => {
        expect(
          screen.getByText("inventories.metrics.impressions"),
        ).toBeInTheDocument();
      });
      expect(
        screen.getAllByText(EmptyValueDisplay.DASH).length,
      ).toBeGreaterThanOrEqual(1);
    });
  });

  describe("Est. daily plays in footer", () => {
    it("shows Est. daily plays using perDayAdPlays when totalAdPlays is set and non-CLASSIC", () => {
      render(<InventoryDetailCard {...defaultProps} />);
      expect(
        screen.getByText(/5.*inventories\.metrics\.daily_plays/),
      ).toBeInTheDocument();
    });

    it("shows N/A for Est. when CLASSIC", () => {
      const itemClassic = {
        ...defaultItem,
        detail: {
          ...defaultItem.detail,
          inventoryType: InventoryClassification.CLASSIC,
        },
        performance: { ...defaultItem.performance, totalAdPlays: 50 },
      };
      render(<InventoryDetailCard {...defaultProps} item={itemClassic} />);
      expect(
        screen.getByText(EmptyValueDisplay.NOT_APPLICABLE),
      ).toBeInTheDocument();
    });
  });

  describe("campaign duration and reach/frequency API", () => {
    beforeEach(() => {
      vi.clearAllMocks();
    });

    it("calculates duration from campaign dates and calls API when expanded", async () => {
      const user = userEvent.setup();
      const mockUnwrap = vi.fn().mockResolvedValue({
        success: true,
        data: [
          {
            impressions: 5000,
            reach: 2500,
            frequency: 2.0,
            status: "COMPLETED",
          },
        ],
      });
      mockFetchReachFrequency.mockReturnValue({ unwrap: mockUnwrap });

      // slotDuration: 10, clientPerLoop: 10 → spotsPerHour = floor(3600 / 10 / 10) = 36
      const itemWithOperations = {
        ...defaultItem,
        operations: {
          slotDuration: 10,
          clientPerLoop: 10,
        } as InventoryItem["operations"],
      };

      render(
        <InventoryDetailCard
          {...defaultProps}
          item={itemWithOperations}
          campaignStartDate="2026-03-14"
          campaignEndDate="2026-03-15"
        />,
      );

      const expandButton = screen.getAllByRole("button")[0];
      await user.click(expandButton);

      await waitFor(() => {
        expect(mockFetchReachFrequency).toHaveBeenCalledWith({
          inventories: [
            expect.objectContaining({
              referenceId: "REF-001",
              type: "billboard",
              spotsPerHour: 36, // floor(3600 / 10 / 10) = 36
            }),
          ],
          duration: 2, // March 14 to March 15 should be 2 days (inclusive)
        });
      });
    });

    it("calculates duration for multi-day campaign", async () => {
      const user = userEvent.setup();
      const mockUnwrap = vi.fn().mockResolvedValue({
        success: true,
        data: [{ impressions: 10000, reach: 5000, frequency: 2.0 }],
      });
      mockFetchReachFrequency.mockReturnValue({ unwrap: mockUnwrap });

      render(
        <InventoryDetailCard
          {...defaultProps}
          campaignStartDate="2026-03-01"
          campaignEndDate="2026-03-31"
        />,
      );

      const expandButton = screen.getAllByRole("button")[0];
      await user.click(expandButton);

      await waitFor(() => {
        expect(mockFetchReachFrequency).toHaveBeenCalledWith(
          expect.objectContaining({
            duration: 31, // March has 31 days
          }),
        );
      });
    });

    it("handles Date objects for campaign dates", async () => {
      const user = userEvent.setup();
      const mockUnwrap = vi.fn().mockResolvedValue({
        success: true,
        data: [{ impressions: 3000, reach: 1500, frequency: 2.0 }],
      });
      mockFetchReachFrequency.mockReturnValue({ unwrap: mockUnwrap });

      const startDate = new Date("2026-03-10");
      const endDate = new Date("2026-03-12");

      render(
        <InventoryDetailCard
          {...defaultProps}
          campaignStartDate={startDate}
          campaignEndDate={endDate}
        />,
      );

      const expandButton = screen.getAllByRole("button")[0];
      await user.click(expandButton);

      await waitFor(() => {
        expect(mockFetchReachFrequency).toHaveBeenCalledWith(
          expect.objectContaining({
            duration: 3, // March 10, 11, 12 = 3 days
          }),
        );
      });
    });

    it("falls back to slotDuration when campaign dates are missing", async () => {
      const user = userEvent.setup();
      const mockUnwrap = vi.fn().mockResolvedValue({
        success: true,
        data: [{ impressions: 1000, reach: 500, frequency: 2.0 }],
      });
      mockFetchReachFrequency.mockReturnValue({ unwrap: mockUnwrap });

      const itemWithSlotDuration = {
        ...defaultItem,
        operations: {
          ...defaultItem.operations,
          slotDuration: 14,
        },
      };

      render(
        <InventoryDetailCard {...defaultProps} item={itemWithSlotDuration} />,
      );

      const expandButton = screen.getAllByRole("button")[0];
      await user.click(expandButton);

      await waitFor(() => {
        expect(mockFetchReachFrequency).toHaveBeenCalledWith(
          expect.objectContaining({
            duration: 14, // Should use slotDuration
          }),
        );
      });
    });

    it("prefers campaign dates over slotDuration when both are available", async () => {
      const user = userEvent.setup();
      const mockUnwrap = vi.fn().mockResolvedValue({
        success: true,
        data: [{ impressions: 2000, reach: 1000, frequency: 2.0 }],
      });
      mockFetchReachFrequency.mockReturnValue({ unwrap: mockUnwrap });

      const itemWithSlotDuration = {
        ...defaultItem,
        operations: {
          ...defaultItem.operations,
          slotDuration: 100, // This should be ignored
        },
      };

      render(
        <InventoryDetailCard
          {...defaultProps}
          item={itemWithSlotDuration}
          campaignStartDate="2026-03-01"
          campaignEndDate="2026-03-07"
        />,
      );

      const expandButton = screen.getAllByRole("button")[0];
      await user.click(expandButton);

      await waitFor(() => {
        expect(mockFetchReachFrequency).toHaveBeenCalledWith(
          expect.objectContaining({
            duration: 7, // Should use campaign dates, not slotDuration
          }),
        );
      });
    });

    it("handles null campaign dates gracefully", async () => {
      const user = userEvent.setup();
      const mockUnwrap = vi.fn().mockResolvedValue({
        success: true,
        data: [{ impressions: 1000, reach: 500, frequency: 2.0 }],
      });
      mockFetchReachFrequency.mockReturnValue({ unwrap: mockUnwrap });

      render(
        <InventoryDetailCard
          {...defaultProps}
          campaignStartDate={null}
          campaignEndDate={null}
        />,
      );

      const expandButton = screen.getAllByRole("button")[0];
      await user.click(expandButton);

      await waitFor(() => {
        expect(mockFetchReachFrequency).toHaveBeenCalledWith(
          expect.objectContaining({
            duration: 0, // Should default to 0
          }),
        );
      });
    });

    it("handles only startDate provided", async () => {
      const user = userEvent.setup();
      const mockUnwrap = vi.fn().mockResolvedValue({
        success: true,
        data: [{ impressions: 1000, reach: 500, frequency: 2.0 }],
      });
      mockFetchReachFrequency.mockReturnValue({ unwrap: mockUnwrap });

      render(
        <InventoryDetailCard
          {...defaultProps}
          campaignStartDate="2026-03-01"
          campaignEndDate={undefined}
        />,
      );

      const expandButton = screen.getAllByRole("button")[0];
      await user.click(expandButton);

      await waitFor(() => {
        expect(mockFetchReachFrequency).toHaveBeenCalledWith(
          expect.objectContaining({
            duration: 0, // Should default to 0 when endDate is missing
          }),
        );
      });
    });

    it("does not call API when fromSchedule is true", async () => {
      render(
        <InventoryDetailCard
          {...defaultProps}
          fromSchedule={true}
          campaignStartDate="2026-03-01"
          campaignEndDate="2026-03-07"
        />,
      );

      // Should not call API even without user interaction since fromSchedule prevents API calls
      expect(mockFetchReachFrequency).not.toHaveBeenCalled();
    });

    it("does not call API when fromPriceManagement is true", async () => {
      render(
        <InventoryDetailCard
          {...defaultProps}
          fromPriceManagement={true}
          campaignStartDate="2026-03-01"
          campaignEndDate="2026-03-07"
        />,
      );

      // Should not call API even without user interaction since fromPriceManagement prevents API calls
      expect(mockFetchReachFrequency).not.toHaveBeenCalled();
    });

    it("uses local performance data when performanceSource is local", async () => {
      const user = userEvent.setup();
      const itemWithPerformance = {
        ...defaultItem,
        performance: {
          ...defaultItem.performance,
          estimatedReach: 4000,
          estimatedFrequency: 3.5,
          estimatedImpressions: 14000,
        },
      };

      render(
        <InventoryDetailCard
          {...defaultProps}
          item={itemWithPerformance}
          performanceSource="local"
          campaignStartDate="2026-03-01"
          campaignEndDate="2026-03-07"
        />,
      );

      const expandButton = screen.getAllByRole("button")[0];
      await user.click(expandButton);

      await waitFor(() => {
        expect(mockFetchReachFrequency).not.toHaveBeenCalled();
      });
    });

    it("uses filter performance data and skips API when performanceSource is local-if-available and impression is present", async () => {
      const user = userEvent.setup();
      const itemWithImpression = {
        ...defaultItem,
        performance: {
          ...defaultItem.performance,
          estimatedReach: 4000,
          estimatedFrequency: 3.5,
          // raw /filter field is singular
          estimatedImpression: 14000,
        },
      };

      render(
        <InventoryDetailCard
          {...defaultProps}
          item={itemWithImpression}
          performanceSource="local-if-available"
          campaignStartDate="2026-03-01"
          campaignEndDate="2026-03-07"
        />,
      );

      await user.click(screen.getAllByRole("button")[0]);

      await waitFor(() => {
        expect(screen.getByText("14,000")).toBeInTheDocument();
      });
      expect(mockFetchReachFrequency).not.toHaveBeenCalled();
    });

    it("calls API when performanceSource is local-if-available but impression is absent", async () => {
      const user = userEvent.setup();
      const mockUnwrap = vi.fn().mockResolvedValue({
        success: true,
        data: [{ impressions: 1000, reach: 500, frequency: 2 }],
      });
      mockFetchReachFrequency.mockReturnValue({ unwrap: mockUnwrap });

      // defaultItem.performance has no estimatedImpressions
      render(
        <InventoryDetailCard
          {...defaultProps}
          performanceSource="local-if-available"
          campaignStartDate="2026-03-01"
          campaignEndDate="2026-03-07"
        />,
      );

      await user.click(screen.getAllByRole("button")[0]);

      await waitFor(() => {
        expect(mockFetchReachFrequency).toHaveBeenCalled();
      });
    });

    it("handles API errors gracefully", async () => {
      const user = userEvent.setup();
      const mockUnwrap = vi
        .fn()
        .mockRejectedValue(new Error("API call failed"));
      mockFetchReachFrequency.mockReturnValue({ unwrap: mockUnwrap });

      const consoleErrorSpy = vi
        .spyOn(console, "error")
        .mockImplementation(() => {});

      render(
        <InventoryDetailCard
          {...defaultProps}
          campaignStartDate="2026-03-01"
          campaignEndDate="2026-03-07"
        />,
      );

      const expandButton = screen.getAllByRole("button")[0];
      await user.click(expandButton);

      await waitFor(() => {
        expect(consoleErrorSpy).toHaveBeenCalledWith(
          "Error loading reach and frequency data:",
          expect.any(Error),
        );
      });

      consoleErrorSpy.mockRestore();
    });

    it("calculates single day campaign correctly", async () => {
      const user = userEvent.setup();
      const mockUnwrap = vi.fn().mockResolvedValue({
        success: true,
        data: [{ impressions: 500, reach: 250, frequency: 2.0 }],
      });
      mockFetchReachFrequency.mockReturnValue({ unwrap: mockUnwrap });

      render(
        <InventoryDetailCard
          {...defaultProps}
          campaignStartDate="2026-03-15"
          campaignEndDate="2026-03-15"
        />,
      );

      const expandButton = screen.getAllByRole("button")[0];
      await user.click(expandButton);

      await waitFor(() => {
        expect(mockFetchReachFrequency).toHaveBeenCalledWith(
          expect.objectContaining({
            duration: 1, // Single day should be 1
          }),
        );
      });
    });

    it("passes dayparts built from schedule bookingMatrix to reach/frequency API", async () => {
      const user = userEvent.setup();
      const mockUnwrap = vi.fn().mockResolvedValue({
        success: true,
        data: [{ impressions: 1000, reach: 500, frequency: 2.0 }],
      });
      mockFetchReachFrequency.mockReturnValue({ unwrap: mockUnwrap });

      const scheduleWithMatrix: InventorySchedule = {
        ...defaultSchedule,
        bookingMatrix: {
          "2025-06-02": [8, 9],
          "2025-06-03": [14, 15],
        },
      };
      const itemWithSchedule = {
        ...defaultItem,
        schedules: [scheduleWithMatrix],
      };

      render(
        <InventoryDetailCard
          {...defaultProps}
          item={itemWithSchedule}
          campaignStartDate="2025-06-01"
          campaignEndDate="2025-06-30"
        />,
      );

      const expandButton = screen.getAllByRole("button")[0];
      await user.click(expandButton);

      await waitFor(() => {
        expect(mockFetchReachFrequency).toHaveBeenCalledWith(
          expect.objectContaining({
            inventories: expect.arrayContaining([
              expect.objectContaining({
                dayparts: [
                  { scheduledDate: "2025-06-02", scheduledTime: ["08", "09"] },
                  { scheduledDate: "2025-06-03", scheduledTime: ["14", "15"] },
                ],
              }),
            ]),
          }),
        );
      });
    });

    it("merges dayparts from multiple schedules unioning hours on same date", async () => {
      const user = userEvent.setup();
      const mockUnwrap = vi.fn().mockResolvedValue({
        success: true,
        data: [{ impressions: 1000, reach: 500, frequency: 2.0 }],
      });
      mockFetchReachFrequency.mockReturnValue({ unwrap: mockUnwrap });

      const schedule1: InventorySchedule = {
        ...defaultSchedule,
        id: "s1",
        bookingMatrix: { "2025-06-02": [8, 9], "2025-06-03": [14] },
      };
      const schedule2: InventorySchedule = {
        ...defaultSchedule,
        id: "s2",
        bookingMatrix: { "2025-06-02": [10], "2025-06-04": [6] },
      };
      const itemWithTwoSchedules = {
        ...defaultItem,
        schedules: [schedule1, schedule2],
      };

      render(
        <InventoryDetailCard
          {...defaultProps}
          item={itemWithTwoSchedules}
          campaignStartDate="2025-06-01"
          campaignEndDate="2025-06-30"
        />,
      );

      const expandButton = screen.getAllByRole("button")[0];
      await user.click(expandButton);

      await waitFor(() => {
        expect(mockFetchReachFrequency).toHaveBeenCalledWith(
          expect.objectContaining({
            inventories: expect.arrayContaining([
              expect.objectContaining({
                dayparts: [
                  // 2025-06-02: union of [8,9] and [10] → [8,9,10]
                  {
                    scheduledDate: "2025-06-02",
                    scheduledTime: ["08", "09", "10"],
                  },
                  { scheduledDate: "2025-06-03", scheduledTime: ["14"] },
                  { scheduledDate: "2025-06-04", scheduledTime: ["06"] },
                ],
              }),
            ]),
          }),
        );
      });
    });

    it("omits dayparts when item has no schedules", async () => {
      const user = userEvent.setup();
      const mockUnwrap = vi.fn().mockResolvedValue({
        success: true,
        data: [{ impressions: 1000, reach: 500, frequency: 2.0 }],
      });
      mockFetchReachFrequency.mockReturnValue({ unwrap: mockUnwrap });

      render(
        <InventoryDetailCard
          {...defaultProps}
          campaignStartDate="2025-06-01"
          campaignEndDate="2025-06-30"
        />,
      );

      const expandButton = screen.getAllByRole("button")[0];
      await user.click(expandButton);

      await waitFor(() => {
        const callArg = mockFetchReachFrequency.mock.calls[0][0];
        const inventory = callArg?.inventories?.[0];
        expect(inventory?.dayparts).toBeUndefined();
      });
    });
  });

  describe("missing-field guards", () => {
    it("does not crash when detail.size is undefined", () => {
      const item = {
        ...defaultItem,
        detail: {
          ...defaultItem.detail,
          size: undefined,
        },
      } as unknown as InventoryItem;
      expect(() =>
        render(<InventoryDetailCard {...defaultProps} item={item} />),
      ).not.toThrow();
    });

    it("does not crash when inventoryType is undefined", () => {
      const item = {
        ...defaultItem,
        detail: { ...defaultItem.detail, inventoryType: undefined },
      } as unknown as InventoryItem;
      expect(() =>
        render(<InventoryDetailCard {...defaultProps} item={item} />),
      ).not.toThrow();
    });
  });

  describe("size badge", () => {
    const withSize = (size: string | undefined) =>
      ({
        ...defaultItem,
        detail: { ...defaultItem.detail, size },
      }) as unknown as InventoryItem;

    it("renders the translated size label", () => {
      render(<InventoryDetailCard {...defaultProps} item={withSize("M")} />);
      expect(screen.getByText("inventorySize.m.label")).toBeInTheDocument();
    });

    it("does not render a size badge when detail.size is absent", () => {
      render(
        <InventoryDetailCard {...defaultProps} item={withSize(undefined)} />,
      );
      expect(screen.queryByText(/inventorySize\./)).not.toBeInTheDocument();
    });
  });
});

describe("availability annotations (non-auto-selected browse items)", () => {
  const limitedAvailability = {
    availableDays: 3,
    totalDays: 7,
    availabilityPercentage: 42.8,
    summary: "Limited availability for your dates: 3/7 days available",
    allAvailable: false,
  };

  it("shows the amber Limited availability badge via availabilityInfo even when showSmartSuggestionScore is off", () => {
    render(
      <InventoryDetailCard
        {...defaultProps}
        availabilityInfo={limitedAvailability}
      />,
    );
    expect(
      screen.getByTestId("badge-limited-availability-d1"),
    ).toBeInTheDocument();
    expect(screen.getByText(/3\/7/)).toBeInTheDocument();
  });

  it("does not show the badge when availabilityInfo says fully available", () => {
    render(
      <InventoryDetailCard
        {...defaultProps}
        availabilityInfo={{
          ...limitedAvailability,
          availableDays: 7,
          availabilityPercentage: 100,
          allAvailable: true,
        }}
      />,
    );
    expect(
      screen.queryByTestId("badge-limited-availability-d1"),
    ).not.toBeInTheDocument();
  });

  it("shows an explicit unavailable state instead of the amber badge when unavailableForDates", () => {
    render(
      <InventoryDetailCard
        {...defaultProps}
        availabilityInfo={{
          ...limitedAvailability,
          availableDays: 0,
          availabilityPercentage: 0,
          summary: "Unavailable for your dates: 0/7 days available",
        }}
        unavailableForDates
      />,
    );
    expect(screen.getByTestId("badge-unavailable-d1")).toBeInTheDocument();
    expect(
      screen.queryByTestId("badge-limited-availability-d1"),
    ).not.toBeInTheDocument();
  });

  it("renders no availability badges without availabilityInfo (unchanged browse rows)", () => {
    render(<InventoryDetailCard {...defaultProps} />);
    expect(
      screen.queryByTestId("badge-limited-availability-d1"),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId("badge-unavailable-d1")).not.toBeInTheDocument();
  });
});
