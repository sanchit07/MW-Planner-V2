import { describe, it, expect } from "vitest";

import { InventoryItem } from "../../types/inventory.types";
import {
  Blackout,
  Booking,
  BookingSlot,
  InventoryAvailabilityData,
  OperatingTime,
} from "../../types/price-management.types";
import {
  SpotData,
  isDateBlackedOut,
  OperatingHourRange,
  getOperatingHours,
  isHourWithinOperatingTimes,
  getTotalOperatingHoursCount,
  getHourFraction,
  getAllOperatingHours,
  parseSlotTimes,
  isTimeInBookingSlot,
  hasBookingsForDateTime,
  calculateSpotsForClassicInventory,
  calculateSpotsForDailyView,
  calculateSpotsForWeeklyView,
  calculateSpotsForMonthlyView,
  calculateSpotsForDateTime,
  getStatusFromSpotData,
  getBookedPercentage,
  getColorFromPercentage,
  getBookingDetailsForDateTime,
  buildAvailabilityIndex,
  getAvailabilityPercentFromIndex,
} from "../inventoryavailability.utils";

// Test fixtures
const createMockBlackout = (startDate: string, endDate: string): Blackout => ({
  id: "blackout-1",
  startDate,
  endDate,
  reason: "Maintenance",
  createdAt: "2026-01-01T00:00:00Z",
  createdBy: "admin",
});

const createMockOperatingTimes = (
  times: Record<string, Array<{ start: string; end: string }>>,
): Record<string, OperatingTime[]> => times;

const createMockBookingSlot = (
  overrides: Partial<BookingSlot> = {},
): BookingSlot => ({
  id: "slot-1",
  bookingId: "booking-1",
  bookingType: "guaranteed",
  inventoryId: "inv-1",
  startTime: "2026-02-10T07:00:00Z",
  endTime: "2026-02-13T11:00:00Z",
  status: "booked",
  slotPositions: [],
  loopSecondsAllocated: 60,
  secondsAllocated: 60,
  creativeDuration: 10,
  timeZone: "UTC",
  createdAt: "2026-01-01T00:00:00Z",
  createdBy: "admin",
  expiresAt: "2026-03-01T00:00:00Z",
  ...overrides,
});

const createMockBooking = (overrides: Partial<Booking> = {}): Booking => ({
  id: "booking-1",
  bookingType: "guaranteed",
  status: "booked",
  dealId: "deal-1",
  dealName: "Test Campaign",
  brand: "Test Brand",
  agency: "Test Agency",
  slots: [createMockBookingSlot(overrides)],
  sspId: 1,
  metadata: {},
  createdAt: "2026-01-01T00:00:00Z",
  createdBy: "admin",
  updatedAt: "2026-01-01T00:00:00Z",
  updatedBy: "admin",
  expiresAt: "2026-03-01T00:00:00Z",
  expirationExtendedAt: "",
  expirationExtendedBy: "",
  ...overrides,
});

const createMockInventoryData = (
  overrides: Partial<InventoryAvailabilityData> = {},
): InventoryAvailabilityData => ({
  id: "inv-1",
  name: "Test Inventory",
  timeZone: "UTC",
  loopDuration: 60,
  bookingMode: "loop",
  allocatedLoopSeconds: 0,
  availableLoopSeconds: 3600,
  schedule: {
    operatingTimes: {
      0: [{ start: "00:00:00", end: "23:59:59" }],
      1: [{ start: "00:00:00", end: "23:59:59" }],
      2: [{ start: "00:00:00", end: "23:59:59" }],
      3: [{ start: "00:00:00", end: "23:59:59" }],
      4: [{ start: "00:00:00", end: "23:59:59" }],
      5: [{ start: "00:00:00", end: "23:59:59" }],
      6: [{ start: "00:00:00", end: "23:59:59" }],
    },
    createdAt: "2026-01-01T00:00:00Z",
    createdBy: "admin",
    updatedAt: "2026-01-01T00:00:00Z",
    updatedBy: "admin",
  },
  ...overrides,
});

const createMockInventoryItem = (
  overrides: Partial<InventoryItem> = {},
): InventoryItem =>
  ({
    detail: {
      id: "inv-1",
      name: "Test Inventory",
      externalId: "ext-1",
      inventoryType: "Digital",
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
    location: overrides.location || ({} as InventoryItem["location"]),
    performance: overrides.performance || ({} as InventoryItem["performance"]),
    schedules: overrides.schedules || [],
  }) as InventoryItem;

describe("inventoryavailability.utils", () => {
  describe("isDateBlackedOut", () => {
    it("should return false when no blackouts are provided", () => {
      const date = new Date("2026-02-10");
      expect(isDateBlackedOut(date, undefined)).toBe(false);
    });

    it("should return false when blackouts array is empty", () => {
      const date = new Date("2026-02-10");
      expect(isDateBlackedOut(date, [])).toBe(false);
    });

    it("should return true when date is within blackout period", () => {
      const date = new Date("2026-02-10");
      const blackouts = [createMockBlackout("2026-02-08", "2026-02-12")];
      expect(isDateBlackedOut(date, blackouts)).toBe(true);
    });

    it("should return true when date equals blackout start date", () => {
      const date = new Date("2026-02-08");
      const blackouts = [createMockBlackout("2026-02-08", "2026-02-12")];
      expect(isDateBlackedOut(date, blackouts)).toBe(true);
    });

    it("should return true when date equals blackout end date", () => {
      const date = new Date("2026-02-12");
      const blackouts = [createMockBlackout("2026-02-08", "2026-02-12")];
      expect(isDateBlackedOut(date, blackouts)).toBe(true);
    });

    it("should return false when date is outside blackout period", () => {
      const date = new Date("2026-02-15");
      const blackouts = [createMockBlackout("2026-02-08", "2026-02-12")];
      expect(isDateBlackedOut(date, blackouts)).toBe(false);
    });

    it("should check multiple blackout periods", () => {
      const date = new Date("2026-02-20");
      const blackouts = [
        createMockBlackout("2026-02-08", "2026-02-12"),
        createMockBlackout("2026-02-18", "2026-02-22"),
      ];
      expect(isDateBlackedOut(date, blackouts)).toBe(true);
    });
  });

  describe("getOperatingHours", () => {
    it("should return null when operatingTimes is undefined", () => {
      const date = new Date("2026-02-10");
      expect(getOperatingHours(date, undefined)).toBeNull();
    });

    it("should return operating hours for numeric day index", () => {
      const date = new Date("2026-02-10"); // Tuesday = 2
      const operatingTimes = createMockOperatingTimes({
        2: [{ start: "09:00:00", end: "17:00:00" }],
      });
      const result = getOperatingHours(date, operatingTimes);
      expect(result).toEqual([
        { start: 9, end: 17, startMinute: 0, endMinute: 0 },
      ]);
    });

    it("should return operating hours for day name format", () => {
      const date = new Date("2026-02-10"); // Tuesday
      const operatingTimes = createMockOperatingTimes({
        tuesday: [{ start: "08:30:00", end: "18:45:00" }],
      });
      const result = getOperatingHours(date, operatingTimes);
      expect(result).toEqual([
        { start: 8, end: 18, startMinute: 30, endMinute: 45 },
      ]);
    });

    it("should handle midnight as end time (00:00:00 -> 24)", () => {
      const date = new Date("2026-02-10");
      const operatingTimes = createMockOperatingTimes({
        2: [{ start: "20:00:00", end: "00:00:00" }],
      });
      const result = getOperatingHours(date, operatingTimes);
      expect(result).toEqual([
        { start: 20, end: 24, startMinute: 0, endMinute: 0 },
      ]);
    });

    it("should handle multiple operating time slots", () => {
      const date = new Date("2026-02-10");
      const operatingTimes = createMockOperatingTimes({
        2: [
          { start: "23:30:00", end: "00:00:00" },
          { start: "00:00:00", end: "15:00:00" },
        ],
      });
      const result = getOperatingHours(date, operatingTimes);
      expect(result).toHaveLength(2);
      expect(result).toEqual([
        { start: 23, end: 24, startMinute: 30, endMinute: 0 },
        { start: 0, end: 15, startMinute: 0, endMinute: 0 },
      ]);
    });

    it("should return null for empty times array", () => {
      const date = new Date("2026-02-10");
      const operatingTimes = createMockOperatingTimes({
        2: [],
      });
      expect(getOperatingHours(date, operatingTimes)).toBeNull();
    });

    it("should return null when day has no operating times", () => {
      const date = new Date("2026-02-10"); // Tuesday = 2
      const operatingTimes = createMockOperatingTimes({
        1: [{ start: "09:00:00", end: "17:00:00" }], // Monday only
      });
      expect(getOperatingHours(date, operatingTimes)).toBeNull();
    });
  });

  describe("isHourWithinOperatingTimes", () => {
    const ranges: OperatingHourRange[] = [
      { start: 9, end: 12, startMinute: 0, endMinute: 0 },
      { start: 14, end: 18, startMinute: 0, endMinute: 30 },
    ];

    it("should return true for hour within range", () => {
      expect(isHourWithinOperatingTimes(10, ranges)).toBe(true);
      expect(isHourWithinOperatingTimes(15, ranges)).toBe(true);
    });

    it("should return true for start hour", () => {
      expect(isHourWithinOperatingTimes(9, ranges)).toBe(true);
      expect(isHourWithinOperatingTimes(14, ranges)).toBe(true);
    });

    it("should return true for end hour when endMinute > 0", () => {
      expect(isHourWithinOperatingTimes(18, ranges)).toBe(true);
    });

    it("should return false for end hour when endMinute = 0", () => {
      expect(isHourWithinOperatingTimes(12, ranges)).toBe(false);
    });

    it("should return false for hour outside all ranges", () => {
      expect(isHourWithinOperatingTimes(8, ranges)).toBe(false);
      expect(isHourWithinOperatingTimes(13, ranges)).toBe(false);
      expect(isHourWithinOperatingTimes(19, ranges)).toBe(false);
    });
  });

  describe("getTotalOperatingHoursCount", () => {
    it("should calculate total hours for simple range", () => {
      const ranges: OperatingHourRange[] = [
        { start: 9, end: 17, startMinute: 0, endMinute: 0 },
      ];
      expect(getTotalOperatingHoursCount(ranges)).toBe(8);
    });

    it("should account for minutes in calculation", () => {
      const ranges: OperatingHourRange[] = [
        { start: 9, end: 17, startMinute: 30, endMinute: 30 },
      ];
      // 8 hours - 0.5 (start) + 0.5 (end) = 8
      expect(getTotalOperatingHoursCount(ranges)).toBe(8);
    });

    it("should sum multiple ranges", () => {
      const ranges: OperatingHourRange[] = [
        { start: 9, end: 12, startMinute: 0, endMinute: 0 },
        { start: 14, end: 18, startMinute: 0, endMinute: 0 },
      ];
      expect(getTotalOperatingHoursCount(ranges)).toBe(7);
    });

    it("should handle empty ranges", () => {
      expect(getTotalOperatingHoursCount([])).toBe(0);
    });
  });

  describe("getHourFraction", () => {
    it("should return 0 for hour before start", () => {
      expect(getHourFraction(8, 9, 0, 17, 0)).toBe(0);
    });

    it("should return 0 for hour after end", () => {
      expect(getHourFraction(18, 9, 0, 17, 0)).toBe(0);
    });

    it("should return 1 for full hour within range", () => {
      expect(getHourFraction(10, 9, 0, 17, 0)).toBe(1);
    });

    it("should return partial fraction for start hour with minutes", () => {
      // Start at 9:30, checking hour 9 -> fraction = (60-30)/60 = 0.5
      expect(getHourFraction(9, 9, 30, 17, 0)).toBe(0.5);
    });

    it("should return partial fraction for end hour with minutes", () => {
      // End at 17:15, checking hour 17 -> fraction = 15/60 = 0.25
      expect(getHourFraction(17, 9, 0, 17, 15)).toBe(0.25);
    });

    it("should return 0 for end hour with 0 minutes", () => {
      expect(getHourFraction(17, 9, 0, 17, 0)).toBe(0);
    });

    it("should treat endMinute 59 as full hour", () => {
      expect(getHourFraction(17, 9, 0, 17, 59)).toBe(1);
    });
  });

  describe("getAllOperatingHours", () => {
    it("should return all hours in range", () => {
      const ranges: OperatingHourRange[] = [
        { start: 9, end: 12, startMinute: 0, endMinute: 0 },
      ];
      expect(getAllOperatingHours(ranges)).toEqual([9, 10, 11]);
    });

    it("should combine and deduplicate hours from multiple ranges", () => {
      const ranges: OperatingHourRange[] = [
        { start: 9, end: 11, startMinute: 0, endMinute: 0 },
        { start: 10, end: 13, startMinute: 0, endMinute: 0 },
      ];
      expect(getAllOperatingHours(ranges)).toEqual([9, 10, 11, 12]);
    });

    it("should return sorted hours", () => {
      const ranges: OperatingHourRange[] = [
        { start: 14, end: 16, startMinute: 0, endMinute: 0 },
        { start: 9, end: 11, startMinute: 0, endMinute: 0 },
      ];
      expect(getAllOperatingHours(ranges)).toEqual([9, 10, 14, 15]);
    });

    it("should return empty array for empty ranges", () => {
      expect(getAllOperatingHours([])).toEqual([]);
    });
  });

  describe("parseSlotTimes", () => {
    it("should parse slot times correctly", () => {
      const slot = createMockBookingSlot({
        startTime: "2026-02-10T09:00:00Z",
        endTime: "2026-02-10T17:00:00Z",
      });
      const result = parseSlotTimes(slot, "UTC");
      expect(result.slotStart).toBeInstanceOf(Date);
      expect(result.slotEnd).toBeInstanceOf(Date);
    });
  });

  describe("isTimeInBookingSlot", () => {
    const slot = createMockBookingSlot({
      startTime: "2026-02-10T07:00:00Z",
      endTime: "2026-02-13T11:00:00Z",
    });

    it("should return false for date before slot start", () => {
      const date = new Date("2026-02-09");
      expect(isTimeInBookingSlot(date, undefined, slot, "UTC")).toBe(false);
    });

    it("should return false for date after slot end", () => {
      const date = new Date("2026-02-14");
      expect(isTimeInBookingSlot(date, undefined, slot, "UTC")).toBe(false);
    });

    it("should return true for date within slot range (no hour)", () => {
      const date = new Date("2026-02-11");
      expect(isTimeInBookingSlot(date, undefined, slot, "UTC")).toBe(true);
    });

    it("should return true for hour within daily time window", () => {
      const date = new Date("2026-02-11");
      expect(isTimeInBookingSlot(date, 10, slot, "UTC")).toBe(true);
    });

    it("should return false for hour outside daily time window", () => {
      // On start date, window is 07:00–24:00; hours before 7 are outside
      const startDate = new Date("2026-02-10");
      expect(isTimeInBookingSlot(startDate, 0, slot, "UTC")).toBe(false);
      expect(isTimeInBookingSlot(startDate, 5, slot, "UTC")).toBe(false);
    });

    it("should return true for start hour", () => {
      const date = new Date("2026-02-11");
      expect(isTimeInBookingSlot(date, 7, slot, "UTC")).toBe(true);
    });

    it("should return false for end hour with 0 minutes", () => {
      // On end date, window is 00:00–11:00 (11:00 exclusive), so hour 11 is outside
      const endDate = new Date("2026-02-13");
      expect(isTimeInBookingSlot(endDate, 11, slot, "UTC")).toBe(false);
    });

    it("should handle midnight end time", () => {
      const midnightSlot = createMockBookingSlot({
        startTime: "2026-02-10T20:00:00Z",
        endTime: "2026-02-11T00:00:00Z",
      });
      const date = new Date("2026-02-10");
      expect(isTimeInBookingSlot(date, 22, midnightSlot, "UTC")).toBe(true);
      expect(isTimeInBookingSlot(date, 23, midnightSlot, "UTC")).toBe(true);
    });
  });

  describe("hasBookingsForDateTime", () => {
    it("should return no bookings when bookings array is empty", () => {
      const inventoryData = createMockInventoryData({ bookings: [] });
      const date = new Date("2026-02-11");
      const result = hasBookingsForDateTime(date, 10, inventoryData);
      expect(result).toEqual({
        hasBookings: false,
        isReserved: false,
        isBooked: false,
      });
    });

    it("should return no bookings when bookings is undefined", () => {
      const inventoryData = createMockInventoryData({ bookings: undefined });
      const date = new Date("2026-02-11");
      const result = hasBookingsForDateTime(date, 10, inventoryData);
      expect(result).toEqual({
        hasBookings: false,
        isReserved: false,
        isBooked: false,
      });
    });

    it("should detect booked status", () => {
      const booking = createMockBooking({ status: "booked" });
      const inventoryData = createMockInventoryData({ bookings: [booking] });
      const date = new Date("2026-02-11");
      const result = hasBookingsForDateTime(date, 10, inventoryData);
      expect(result.isBooked).toBe(true);
      expect(result.hasBookings).toBe(true);
    });

    it("should detect reserved status", () => {
      const booking = createMockBooking({ status: "reserved" });
      const inventoryData = createMockInventoryData({ bookings: [booking] });
      const date = new Date("2026-02-11");
      const result = hasBookingsForDateTime(date, 10, inventoryData);
      expect(result.isReserved).toBe(true);
      expect(result.hasBookings).toBe(true);
    });

    it("should ignore non-guaranteed bookings", () => {
      const booking = createMockBooking({ bookingType: "tentative" });
      const inventoryData = createMockInventoryData({ bookings: [booking] });
      const date = new Date("2026-02-11");
      const result = hasBookingsForDateTime(date, 10, inventoryData);
      expect(result.hasBookings).toBe(false);
    });
  });

  describe("calculateSpotsForClassicInventory", () => {
    it("should return zeros for blacked out date", () => {
      const inventoryData = createMockInventoryData({
        blackouts: [createMockBlackout("2026-02-10", "2026-02-10")],
      });
      const date = new Date("2026-02-10");
      const result = calculateSpotsForClassicInventory(date, 10, inventoryData);
      expect(result).toEqual({
        totalSpots: 0,
        availableSpots: 0,
        bookedSpots: 0,
        reservedSpots: 0,
        isBlocked: true,
      });
    });

    it("should return zeros when no operating hours", () => {
      const inventoryData = createMockInventoryData({
        schedule: {
          operatingTimes: {},
          createdAt: "",
          createdBy: "",
          updatedAt: "",
          updatedBy: "",
        },
      });
      const date = new Date("2026-02-10");
      const result = calculateSpotsForClassicInventory(
        date,
        undefined,
        inventoryData,
      );
      expect(result.totalSpots).toBe(0);
    });

    it("should return available for date without bookings", () => {
      const inventoryData = createMockInventoryData({ bookings: [] });
      const date = new Date("2026-02-10");
      const result = calculateSpotsForClassicInventory(
        date,
        undefined,
        inventoryData,
      );
      expect(result).toEqual({
        totalSpots: 1,
        availableSpots: 1,
        bookedSpots: 0,
        reservedSpots: 0,
      });
    });

    it("should return booked for date with booked slot", () => {
      const booking = createMockBooking({ status: "booked" });
      const inventoryData = createMockInventoryData({ bookings: [booking] });
      const date = new Date("2026-02-11");
      const result = calculateSpotsForClassicInventory(date, 10, inventoryData);
      expect(result).toEqual({
        totalSpots: 1,
        availableSpots: 0,
        bookedSpots: 1,
        reservedSpots: 0,
      });
    });

    it("should return reserved for date with reserved slot", () => {
      const booking = createMockBooking({ status: "reserved" });
      const inventoryData = createMockInventoryData({ bookings: [booking] });
      const date = new Date("2026-02-11");
      const result = calculateSpotsForClassicInventory(date, 10, inventoryData);
      expect(result).toEqual({
        totalSpots: 1,
        availableSpots: 0,
        bookedSpots: 0,
        reservedSpots: 1,
      });
    });

    it("should return zeros for hour outside operating times", () => {
      const inventoryData = createMockInventoryData({
        schedule: {
          operatingTimes: {
            2: [{ start: "09:00:00", end: "17:00:00" }],
          },
          createdAt: "",
          createdBy: "",
          updatedAt: "",
          updatedBy: "",
        },
      });
      const date = new Date("2026-02-10"); // Tuesday
      const result = calculateSpotsForClassicInventory(date, 5, inventoryData);
      expect(result.totalSpots).toBe(0);
    });
  });

  describe("calculateSpotsForDailyView", () => {
    it("should return zeros for blacked out date", () => {
      const inventoryData = createMockInventoryData({
        blackouts: [createMockBlackout("2026-02-10", "2026-02-10")],
      });
      const inventoryItem = createMockInventoryItem();
      const date = new Date("2026-02-10");
      const result = calculateSpotsForDailyView(
        date,
        10,
        inventoryData,
        10,
        1,
        inventoryItem,
      );
      expect(result.totalSpots).toBe(0);
    });

    it("should return zeros when inventoryDetails is null", () => {
      const inventoryData = createMockInventoryData();
      const date = new Date("2026-02-10");
      const result = calculateSpotsForDailyView(
        date,
        10,
        inventoryData,
        10,
        1,
        null,
      );
      expect(result.totalSpots).toBe(0);
    });

    it("should return zeros for hour outside operating times", () => {
      const inventoryData = createMockInventoryData({
        schedule: {
          operatingTimes: {
            2: [{ start: "09:00:00", end: "17:00:00" }],
          },
          createdAt: "",
          createdBy: "",
          updatedAt: "",
          updatedBy: "",
        },
      });
      const inventoryItem = createMockInventoryItem();
      const date = new Date("2026-02-10"); // Tuesday
      const result = calculateSpotsForDailyView(
        date,
        5,
        inventoryData,
        10,
        1,
        inventoryItem,
      );
      expect(result.totalSpots).toBe(0);
    });

    it("should calculate spots correctly for available hour", () => {
      const inventoryData = createMockInventoryData({ bookings: [] });
      const inventoryItem = createMockInventoryItem();
      const date = new Date("2026-02-10");
      const result = calculateSpotsForDailyView(
        date,
        10,
        inventoryData,
        10,
        1,
        inventoryItem,
      );
      expect(result.totalSpots).toBeGreaterThan(0);
      expect(result.bookedSpots).toBe(0);
      expect(result.reservedSpots).toBe(0);
    });

    it("should track booked spots by position", () => {
      const slot = createMockBookingSlot({
        slotPositions: [1],
        status: "booked",
      });
      const booking = createMockBooking({
        status: "booked",
        slots: [slot],
      });
      const inventoryData = createMockInventoryData({ bookings: [booking] });
      const inventoryItem = createMockInventoryItem();
      const date = new Date("2026-02-11");
      const result = calculateSpotsForDailyView(
        date,
        10,
        inventoryData,
        10,
        1,
        inventoryItem,
      );
      expect(result.bookedSpots).toBeGreaterThan(0);
    });
  });

  describe("calculateSpotsForWeeklyView", () => {
    it("should return zeros for blacked out date", () => {
      const inventoryData = createMockInventoryData({
        blackouts: [createMockBlackout("2026-02-10", "2026-02-10")],
      });
      const inventoryItem = createMockInventoryItem();
      const date = new Date("2026-02-10");
      const result = calculateSpotsForWeeklyView(
        date,
        inventoryData,
        10,
        1,
        inventoryItem,
      );
      expect(result.totalSpots).toBe(0);
    });

    it("should return zeros when inventoryDetails is null", () => {
      const inventoryData = createMockInventoryData();
      const date = new Date("2026-02-10");
      const result = calculateSpotsForWeeklyView(
        date,
        inventoryData,
        10,
        1,
        null,
      );
      expect(result.totalSpots).toBe(0);
    });

    it("should calculate total spots for a day", () => {
      const inventoryData = createMockInventoryData({ bookings: [] });
      const inventoryItem = createMockInventoryItem();
      const date = new Date("2026-02-10");
      const result = calculateSpotsForWeeklyView(
        date,
        inventoryData,
        10,
        1,
        inventoryItem,
      );
      expect(result.totalSpots).toBeGreaterThan(0);
      expect(result.availableSpots).toBeGreaterThan(0);
    });
  });

  describe("calculateSpotsForMonthlyView", () => {
    it("should return zeros when inventoryDetails is null", () => {
      const inventoryData = createMockInventoryData();
      const date = new Date("2026-02-10");
      const result = calculateSpotsForMonthlyView(
        date,
        inventoryData,
        10,
        null,
      );
      expect(result.totalSpots).toBe(0);
    });

    it("should return zeros for blacked out date", () => {
      const inventoryData = createMockInventoryData({
        blackouts: [createMockBlackout("2026-02-10", "2026-02-10")],
      });
      const inventoryItem = createMockInventoryItem();
      const date = new Date("2026-02-10");
      const result = calculateSpotsForMonthlyView(
        date,
        inventoryData,
        10,
        inventoryItem,
      );
      expect(result.totalSpots).toBe(0);
    });

    it("should calculate total daily spots", () => {
      const inventoryData = createMockInventoryData({ bookings: [] });
      const inventoryItem = createMockInventoryItem();
      const date = new Date("2026-02-10");
      const result = calculateSpotsForMonthlyView(
        date,
        inventoryData,
        10,
        inventoryItem,
      );
      expect(result.totalSpots).toBeGreaterThan(0);
      expect(result.availableSpots).toBe(result.totalSpots);
    });

    it("should cap booked spots at total spots", () => {
      // Create a slot that would exceed total spots
      const slot = createMockBookingSlot({
        loopSecondsAllocated: 999999,
        status: "booked",
      });
      const booking = createMockBooking({
        status: "booked",
        slots: [slot],
      });
      const inventoryData = createMockInventoryData({ bookings: [booking] });
      const inventoryItem = createMockInventoryItem();
      const date = new Date("2026-02-11");
      const result = calculateSpotsForMonthlyView(
        date,
        inventoryData,
        10,
        inventoryItem,
      );
      expect(result.bookedSpots).toBeLessThanOrEqual(result.totalSpots);
    });
  });

  describe("calculateSpotsForDateTime", () => {
    it("should route to classic calculation when isClassic is true", () => {
      const inventoryData = createMockInventoryData({ bookings: [] });
      const inventoryItem = createMockInventoryItem();
      const date = new Date("2026-02-10");
      const result = calculateSpotsForDateTime(
        date,
        10,
        inventoryData,
        10,
        inventoryItem,
        undefined,
        true,
      );
      expect(result.totalSpots).toBe(1);
    });

    it("should route to daily view for hour + spotPosition", () => {
      const inventoryData = createMockInventoryData({ bookings: [] });
      const inventoryItem = createMockInventoryItem();
      const date = new Date("2026-02-10");
      const result = calculateSpotsForDateTime(
        date,
        10,
        inventoryData,
        10,
        inventoryItem,
        1,
        false,
      );
      expect(result.totalSpots).toBeGreaterThan(0);
    });

    it("should route to weekly view for spotPosition without hour", () => {
      const inventoryData = createMockInventoryData({ bookings: [] });
      const inventoryItem = createMockInventoryItem();
      const date = new Date("2026-02-10");
      const result = calculateSpotsForDateTime(
        date,
        undefined,
        inventoryData,
        10,
        inventoryItem,
        1,
        false,
      );
      expect(result.totalSpots).toBeGreaterThan(0);
    });

    it("should route to monthly view for no hour and no spotPosition", () => {
      const inventoryData = createMockInventoryData({ bookings: [] });
      const inventoryItem = createMockInventoryItem();
      const date = new Date("2026-02-10");
      const result = calculateSpotsForDateTime(
        date,
        undefined,
        inventoryData,
        10,
        inventoryItem,
        undefined,
        false,
      );
      expect(result.totalSpots).toBeGreaterThan(0);
    });

    it("should return fallback for edge case (hour without spotPosition for digital)", () => {
      const inventoryData = createMockInventoryData({ bookings: [] });
      const inventoryItem = createMockInventoryItem();
      const date = new Date("2026-02-10");
      // This case hits the fallback - hour provided but no spotPosition for digital
      const result = calculateSpotsForDateTime(
        date,
        10,
        inventoryData,
        10,
        inventoryItem,
        undefined,
        false,
      );
      expect(result).toBeDefined();
    });
  });

  describe("getStatusFromSpotData", () => {
    it("should return available when totalSpots is 0", () => {
      const spotData: SpotData = {
        totalSpots: 0,
        availableSpots: 0,
        bookedSpots: 0,
        reservedSpots: 0,
      };
      expect(getStatusFromSpotData(spotData)).toBe("available");
    });

    it("should return fully_booked in monthly view when booked is max", () => {
      const spotData: SpotData = {
        totalSpots: 10,
        availableSpots: 2,
        bookedSpots: 5,
        reservedSpots: 3,
      };
      expect(getStatusFromSpotData(spotData, true)).toBe("fully_booked");
    });

    it("should return booked in non-monthly view when booked is max", () => {
      const spotData: SpotData = {
        totalSpots: 10,
        availableSpots: 2,
        bookedSpots: 5,
        reservedSpots: 3,
      };
      expect(getStatusFromSpotData(spotData, false)).toBe("booked");
    });

    it("should return reserved when reserved is max", () => {
      const spotData: SpotData = {
        totalSpots: 10,
        availableSpots: 2,
        bookedSpots: 3,
        reservedSpots: 5,
      };
      expect(getStatusFromSpotData(spotData)).toBe("reserved");
    });

    it("should return available when available is max", () => {
      const spotData: SpotData = {
        totalSpots: 10,
        availableSpots: 8,
        bookedSpots: 1,
        reservedSpots: 1,
      };
      expect(getStatusFromSpotData(spotData)).toBe("available");
    });
  });

  describe("getBookedPercentage", () => {
    it("should return 0 when totalSpots is 0", () => {
      const spotData: SpotData = {
        totalSpots: 0,
        availableSpots: 0,
        bookedSpots: 0,
        reservedSpots: 0,
      };
      expect(getBookedPercentage(spotData)).toBe(0);
    });

    it("should calculate percentage correctly", () => {
      const spotData: SpotData = {
        totalSpots: 100,
        availableSpots: 50,
        bookedSpots: 30,
        reservedSpots: 20,
      };
      expect(getBookedPercentage(spotData)).toBe(50);
    });

    it("should return 100 when fully booked", () => {
      const spotData: SpotData = {
        totalSpots: 100,
        availableSpots: 0,
        bookedSpots: 70,
        reservedSpots: 30,
      };
      expect(getBookedPercentage(spotData)).toBe(100);
    });
  });

  describe("getColorFromPercentage", () => {
    it("should return success for low percentage", () => {
      expect(getColorFromPercentage(20)).toBe("success");
    });

    it("should return primary for 25-50%", () => {
      expect(getColorFromPercentage(40)).toBe("primary");
    });

    it("should return warning for 50-75%", () => {
      expect(getColorFromPercentage(60)).toBe("warning");
    });

    it("should return neutral for 75-95%", () => {
      expect(getColorFromPercentage(80)).toBe("neutral");
    });

    it("should return error for 95-100%", () => {
      expect(getColorFromPercentage(98)).toBe("error");
    });

    it("should return error for 100%", () => {
      expect(getColorFromPercentage(100)).toBe("error");
    });
  });

  describe("getBookingDetailsForDateTime", () => {
    it("should return empty array when no bookings", () => {
      const inventoryData = createMockInventoryData({ bookings: [] });
      const date = new Date("2026-02-11");
      expect(
        getBookingDetailsForDateTime(date, 10, inventoryData, undefined),
      ).toEqual([]);
    });

    it("should return matching bookings", () => {
      const booking = createMockBooking();
      const inventoryData = createMockInventoryData({ bookings: [booking] });
      const date = new Date("2026-02-11");
      const result = getBookingDetailsForDateTime(date, 10, inventoryData, [
        booking,
      ]);
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe(booking.id);
    });

    it("should filter out bookings that don't match time", () => {
      const booking = createMockBooking();
      const inventoryData = createMockInventoryData({ bookings: [booking] });
      const date = new Date("2026-02-20"); // Outside booking range
      const result = getBookingDetailsForDateTime(date, 10, inventoryData, [
        booking,
      ]);
      expect(result).toHaveLength(0);
    });

    it("should only include guaranteed bookings", () => {
      const tentativeBooking = createMockBooking({ bookingType: "tentative" });
      const guaranteedBooking = createMockBooking({
        bookingType: "guaranteed",
      });
      const inventoryData = createMockInventoryData({
        bookings: [tentativeBooking, guaranteedBooking],
      });
      const date = new Date("2026-02-11");
      const result = getBookingDetailsForDateTime(date, 10, inventoryData, [
        tentativeBooking,
        guaranteedBooking,
      ]);
      expect(result).toHaveLength(1);
      expect(result[0].bookingType).toBe("guaranteed");
    });
  });

  describe("getAvailabilityPercentFromIndex", () => {
    it("returns 100 when the inventory has no bookings", () => {
      const inventoryData = createMockInventoryData(); // no bookings
      const item = createMockInventoryItem();
      const index = buildAvailabilityIndex(inventoryData, item);
      expect(index).not.toBeNull();

      const result = getAvailabilityPercentFromIndex(
        index!,
        new Date("2026-02-10"),
        new Date("2026-02-12"),
      );
      expect(result).toBe(100);
    });

    it("returns a reduced integer percentage when bookings exist", () => {
      const slot = createMockBookingSlot({
        startTime: "2026-02-10T00:00:00Z",
        endTime: "2026-02-13T00:00:00Z",
        slotPositions: [1],
        status: "booked",
      });
      const booking = createMockBooking({ status: "booked", slots: [slot] });
      const inventoryData = createMockInventoryData({ bookings: [booking] });
      const item = createMockInventoryItem();
      const index = buildAvailabilityIndex(inventoryData, item);
      expect(index).not.toBeNull();

      const result = getAvailabilityPercentFromIndex(
        index!,
        new Date("2026-02-10"),
        new Date("2026-02-12"),
      );
      expect(result).not.toBeNull();
      expect(result).toBeLessThan(100);
      expect(result).toBeGreaterThanOrEqual(0);
      expect(Number.isInteger(result)).toBe(true);
    });

    it("returns null when startDate is after endDate", () => {
      const inventoryData = createMockInventoryData();
      const item = createMockInventoryItem();
      const index = buildAvailabilityIndex(inventoryData, item);

      const result = getAvailabilityPercentFromIndex(
        index!,
        new Date("2026-02-12"),
        new Date("2026-02-10"),
      );
      expect(result).toBeNull();
    });

    it("returns null when there is no operating capacity in the range", () => {
      const inventoryData = createMockInventoryData({
        schedule: {
          operatingTimes: {
            0: [],
            1: [],
            2: [],
            3: [],
            4: [],
            5: [],
            6: [],
          },
          createdAt: "2026-01-01T00:00:00Z",
          createdBy: "admin",
          updatedAt: "2026-01-01T00:00:00Z",
          updatedBy: "admin",
        },
      });
      const item = createMockInventoryItem();
      const index = buildAvailabilityIndex(inventoryData, item);
      expect(index).not.toBeNull();

      const result = getAvailabilityPercentFromIndex(
        index!,
        new Date("2026-02-10"),
        new Date("2026-02-12"),
      );
      expect(result).toBeNull();
    });
  });
});
