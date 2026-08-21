import { render, screen } from "@testing-library/react";
import type { SpotData } from "@utils/inventoryavailability.utils";
import type { Booking } from "src/types/price-management.types";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { SpotTooltipContent } from "../SpotTooltipContent";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("@utils/inventoryAvailabilityUI.utils", () => ({
  formatDateForTooltip: (date: Date) => date.toISOString().split("T")[0],
  formatTimeForTooltip: (hour: number) => `${String(hour).padStart(2, "0")}:00`,
}));

function createSpotData(overrides: Partial<SpotData> = {}): SpotData {
  return {
    totalSpots: 100,
    availableSpots: 60,
    bookedSpots: 25,
    reservedSpots: 15,
    ...overrides,
  };
}

function createBooking(overrides: Partial<Booking> = {}): Booking {
  return {
    id: "b1",
    dealId: "d1",
    dealName: "Test Campaign",
    brand: "Test Brand",
    agency: "Test Agency",
    status: "active",
    bookingType: "standard",
    createdAt: "",
    createdBy: "",
    updatedAt: "",
    updatedBy: "",
    expirationExtendedAt: "",
    expirationExtendedBy: "",
    expiresAt: "",
    metadata: {},
    sspId: 0,
    slots: [
      {
        id: "s1",
        bookingId: "b1",
        startTime: "2024-06-01T09:00:00Z",
        endTime: "2024-06-01T10:00:00Z",
        createdAt: "",
        createdBy: "",
        status: "active",
        inventoryId: "inv1",
        timeZone: "UTC",
        bookingType: "standard",
        creativeDuration: 0,
        loopSecondsAllocated: 0,
        secondsAllocated: 0,
        slotPositions: [],
        expiresAt: "",
      },
    ],
    ...overrides,
  };
}

describe("SpotTooltipContent", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("classic view", () => {
    it("renders Booked status when bookedSpots > 0", () => {
      const spotData = createSpotData({ bookedSpots: 5, reservedSpots: 0 });
      render(
        <SpotTooltipContent
          spotData={spotData}
          isClassic
          isDailyView={false}
        />,
      );
      expect(screen.getByText("calendar.legend.booked")).toBeInTheDocument();
    });

    it("renders Reserved status when reservedSpots > 0 and bookedSpots is 0", () => {
      const spotData = createSpotData({ bookedSpots: 0, reservedSpots: 3 });
      render(
        <SpotTooltipContent
          spotData={spotData}
          isClassic
          isDailyView={false}
        />,
      );
      expect(screen.getByText("calendar.legend.reserved")).toBeInTheDocument();
    });

    it("renders Available status when bookedSpots and reservedSpots are 0", () => {
      const spotData = createSpotData({ bookedSpots: 0, reservedSpots: 0 });
      render(
        <SpotTooltipContent
          spotData={spotData}
          isClassic
          isDailyView={false}
        />,
      );
      expect(screen.getByText("calendar.legend.available")).toBeInTheDocument();
    });

    it("renders formatted date when date is provided", () => {
      const date = new Date("2024-06-15");
      const spotData = createSpotData();
      render(
        <SpotTooltipContent
          spotData={spotData}
          date={date}
          isClassic
          isDailyView={false}
        />,
      );
      expect(screen.getByText("2024-06-15")).toBeInTheDocument();
    });

    it("renders empty date when date is null", () => {
      const spotData = createSpotData();
      const { container } = render(
        <SpotTooltipContent
          spotData={spotData}
          date={null}
          isClassic
          isDailyView={false}
        />,
      );
      const dateEl = container.querySelector(
        ".font-semibold.text-mw-neutral-900.mb-2",
      );
      expect(dateEl).toHaveTextContent("");
    });

    it("renders time range when hour is defined and not parent row", () => {
      const spotData = createSpotData();
      render(
        <SpotTooltipContent
          spotData={spotData}
          date={new Date("2024-06-15")}
          hour={9}
          isClassic
          isParentRow={false}
          isDailyView={false}
        />,
      );
      expect(screen.getByText(/09:00 - 10:00/)).toBeInTheDocument();
    });

    it("does not render time range when isParentRow is true", () => {
      const spotData = createSpotData();
      render(
        <SpotTooltipContent
          spotData={spotData}
          date={new Date("2024-06-15")}
          hour={9}
          isClassic
          isParentRow
          isDailyView={false}
        />,
      );
      expect(screen.queryByText(/09:00 - 10:00/)).not.toBeInTheDocument();
    });

    it("renders booking details when bookingDetails provided and spots booked or reserved", () => {
      const spotData = createSpotData({ bookedSpots: 2, reservedSpots: 0 });
      const bookingDetails = [
        createBooking({ dealName: "My Deal", brand: "My Brand" }),
      ];
      render(
        <SpotTooltipContent
          spotData={spotData}
          isClassic
          isDailyView={false}
          bookingDetails={bookingDetails}
        />,
      );
      expect(
        screen.getByText("calendar.tooltip.campaign:"),
      ).toBeInTheDocument();
      expect(screen.getByText("My Deal")).toBeInTheDocument();
      expect(screen.getByText("calendar.tooltip.brand:")).toBeInTheDocument();
      expect(screen.getByText("My Brand")).toBeInTheDocument();
    });

    it("shows N/A for missing dealName and brand in classic view", () => {
      const spotData = createSpotData({ bookedSpots: 1, reservedSpots: 0 });
      const bookingDetails = [createBooking({ dealName: "", brand: "" })];
      render(
        <SpotTooltipContent
          spotData={spotData}
          isClassic
          isDailyView={false}
          bookingDetails={bookingDetails}
        />,
      );
      const naElements = screen.getAllByText("calendar.tooltip.na");
      expect(naElements.length).toBeGreaterThanOrEqual(2);
    });

    it("does not render booking details when bookingDetails is empty", () => {
      const spotData = createSpotData({ bookedSpots: 1 });
      render(
        <SpotTooltipContent
          spotData={spotData}
          isClassic
          isDailyView={false}
          bookingDetails={[]}
        />,
      );
      expect(
        screen.queryByText("calendar.tooltip.campaign:"),
      ).not.toBeInTheDocument();
    });

    it("does not render booking details when all spots available", () => {
      const spotData = createSpotData({ bookedSpots: 0, reservedSpots: 0 });
      render(
        <SpotTooltipContent
          spotData={spotData}
          isClassic
          isDailyView={false}
          bookingDetails={[createBooking()]}
        />,
      );
      expect(
        screen.queryByText("calendar.tooltip.campaign:"),
      ).not.toBeInTheDocument();
    });
  });

  describe("digital campaign details view", () => {
    it("renders full campaign details when showCampaignDetails conditions are met", () => {
      const spotData = createSpotData({
        totalSpots: 10,
        availableSpots: 2,
        bookedSpots: 5,
        reservedSpots: 3,
      });
      const bookingDetails = [
        createBooking({
          dealName: "Digital Campaign",
          agency: "Agency X",
          brand: "Brand Y",
        }),
      ];
      render(
        <SpotTooltipContent
          spotData={spotData}
          date={new Date("2024-06-15")}
          hour={14}
          isClassic={false}
          isDailyView
          spotLabel="Screen A"
          bookingDetails={bookingDetails}
          campaignStartDate="2024-06-01"
          campaignEndDate="2024-06-30"
        />,
      );
      expect(screen.getByText("2024-06-15")).toBeInTheDocument();
      expect(
        screen.getByText(/Screen A - 14:00 calendar\.tooltip\.to/),
      ).toBeInTheDocument();
      expect(
        screen.getByText("calendar.tooltip.campaign:"),
      ).toBeInTheDocument();
      expect(screen.getByText("Digital Campaign")).toBeInTheDocument();
      expect(screen.getByText("calendar.tooltip.agency:")).toBeInTheDocument();
      expect(screen.getByText("Agency X")).toBeInTheDocument();
      expect(screen.getByText("calendar.tooltip.brand:")).toBeInTheDocument();
      expect(screen.getByText("Brand Y")).toBeInTheDocument();
      expect(
        screen.getByText("calendar.tooltip.start_date:"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("calendar.tooltip.end_date:"),
      ).toBeInTheDocument();
      expect(screen.getByText("calendar.tooltip.sov:")).toBeInTheDocument();
      expect(screen.getByText(/80%/)).toBeInTheDocument();
    });

    it("uses campaignStartDate and campaignEndDate when provided", () => {
      const spotData = createSpotData({
        totalSpots: 10,
        availableSpots: 1,
        bookedSpots: 9,
        reservedSpots: 0,
      });
      const bookingDetails = [createBooking()];
      render(
        <SpotTooltipContent
          spotData={spotData}
          date={new Date("2024-06-15")}
          hour={10}
          isClassic={false}
          isDailyView
          spotLabel="Spot 1"
          bookingDetails={bookingDetails}
          campaignStartDate="2024-05-01"
          campaignEndDate="2024-07-31"
        />,
      );
      expect(screen.getByText("2024-05-01")).toBeInTheDocument();
      expect(screen.getByText("2024-07-31")).toBeInTheDocument();
    });
  });

  describe("digital summary view", () => {
    it("renders Total Spots and Available, Booked, Reserved counts", () => {
      const spotData = createSpotData({
        totalSpots: 100,
        availableSpots: 50,
        bookedSpots: 30,
        reservedSpots: 20,
      });
      render(
        <SpotTooltipContent
          spotData={spotData}
          date={new Date("2024-06-15")}
          isClassic={false}
          isDailyView={false}
        />,
      );
      expect(screen.getByText("2024-06-15")).toBeInTheDocument();
      expect(
        screen.getByText(/100 calendar\.tooltip\.total_spots/),
      ).toBeInTheDocument();
      expect(
        screen.getByText("calendar.tooltip.available_spots"),
      ).toBeInTheDocument();
      expect(screen.getByText("50")).toBeInTheDocument();
      expect(
        screen.getByText("calendar.tooltip.booked_spots"),
      ).toBeInTheDocument();
      expect(screen.getByText("30")).toBeInTheDocument();
      expect(
        screen.getByText("calendar.tooltip.reserved_spots"),
      ).toBeInTheDocument();
      expect(screen.getByText("20")).toBeInTheDocument();
    });

    it("uses per-day counts when totalSpotsPerDay is defined and isDailyView with hour", () => {
      const spotData = createSpotData({
        totalSpots: 100,
        availableSpots: 40,
        bookedSpots: 30,
        reservedSpots: 30,
        totalSpotsPerDay: 50,
        availableSpotsPerDay: 20,
        bookedSpotsPerDay: 18,
        reservedSpotsPerDay: 12,
      });
      render(
        <SpotTooltipContent
          spotData={spotData}
          date={new Date("2024-06-15")}
          hour={12}
          isClassic={false}
          isDailyView
        />,
      );
      expect(
        screen.getByText(/50 calendar\.tooltip\.total_spots/),
      ).toBeInTheDocument();
      expect(screen.getByText("20")).toBeInTheDocument();
      expect(screen.getByText("18")).toBeInTheDocument();
      expect(screen.getByText("12")).toBeInTheDocument();
    });

    it("renders date when provided in digital summary view", () => {
      const date = new Date("2024-07-01");
      const spotData = createSpotData();
      render(
        <SpotTooltipContent
          spotData={spotData}
          date={date}
          isClassic={false}
          isDailyView={false}
        />,
      );
      expect(screen.getByText("2024-07-01")).toBeInTheDocument();
    });

    it("renders empty date when date is null in digital summary view", () => {
      const spotData = createSpotData();
      const { container } = render(
        <SpotTooltipContent
          spotData={spotData}
          date={null}
          isClassic={false}
          isDailyView={false}
        />,
      );
      const dateEl = container.querySelector(
        ".font-semibold.text-mw-neutral-900.mb-1",
      );
      expect(dateEl).toHaveTextContent("");
    });
  });

  describe("edge cases", () => {
    it("renders time range for hour 23 as 23:00 - 23:00", () => {
      const spotData = createSpotData();
      render(
        <SpotTooltipContent
          spotData={spotData}
          date={new Date("2024-06-15")}
          hour={23}
          isClassic
          isParentRow={false}
          isDailyView={false}
        />,
      );
      expect(screen.getByText(/23:00\s*-\s*23:00/)).toBeInTheDocument();
    });

    it("does not show campaign details block when bookedSpots + reservedSpots <= availableSpots", () => {
      const spotData = createSpotData({
        totalSpots: 10,
        availableSpots: 6,
        bookedSpots: 2,
        reservedSpots: 2,
      });
      const bookingDetails = [createBooking()];
      render(
        <SpotTooltipContent
          spotData={spotData}
          date={new Date("2024-06-15")}
          hour={10}
          isClassic={false}
          isDailyView
          spotLabel="Spot"
          bookingDetails={bookingDetails}
        />,
      );
      expect(
        screen.queryByText("calendar.tooltip.agency:"),
      ).not.toBeInTheDocument();
      expect(
        screen.getByText(/10 calendar\.tooltip\.total_spots/),
      ).toBeInTheDocument();
    });

    it("renders summary view with 0 Total Spots when totalSpots is 0", () => {
      const spotData = createSpotData({
        totalSpots: 0,
        availableSpots: 0,
        bookedSpots: 0,
        reservedSpots: 0,
      });
      render(
        <SpotTooltipContent
          spotData={spotData}
          date={new Date("2024-06-15")}
          isClassic={false}
          isDailyView={false}
        />,
      );
      expect(
        screen.getByText(/0 calendar\.tooltip\.total_spots/),
      ).toBeInTheDocument();
      expect(
        screen.getByText("calendar.tooltip.available_spots"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("calendar.tooltip.booked_spots"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("calendar.tooltip.reserved_spots"),
      ).toBeInTheDocument();
    });
  });
});
