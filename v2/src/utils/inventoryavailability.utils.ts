import { InventoryItem } from "src/types/inventory.types";
import {
  Blackout,
  BookingSlot,
  InventoryAvailabilityData,
  OperatingTime,
  Booking,
} from "src/types/price-management.types";

// Types for inventory availability calculations
export type SpotStatus =
  | "available"
  | "booked"
  | "reserved"
  | "fully_booked"
  | "blocked";

export interface SpotData {
  totalSpots: number;
  availableSpots: number;
  bookedSpots: number;
  reservedSpots: number;
  // Per-day totals for daily view with spot rows
  totalSpotsPerDay?: number;
  availableSpotsPerDay?: number;
  bookedSpotsPerDay?: number;
  reservedSpotsPerDay?: number;
  /** Date falls inside an IMS blackout window — inventory is blocked, not merely closed. */
  isBlocked?: boolean;
}

/** Ensure all spot counts are integers (floor available to avoid 7.9999 display). */
function toIntegerSpotData(s: SpotData): SpotData {
  const total = Math.round(s.totalSpots);
  const booked = Math.round(s.bookedSpots);
  const reserved = Math.round(s.reservedSpots);
  const available = Math.floor(Math.max(0, total - booked - reserved));
  const out: SpotData = {
    ...s,
    totalSpots: total,
    bookedSpots: booked,
    reservedSpots: reserved,
    availableSpots: available,
  };
  if (s.totalSpotsPerDay != null) {
    const td = Math.round(s.totalSpotsPerDay);
    const bd = Math.round(s.bookedSpotsPerDay ?? 0);
    const rd = Math.round(s.reservedSpotsPerDay ?? 0);
    out.totalSpotsPerDay = td;
    out.bookedSpotsPerDay = bd;
    out.reservedSpotsPerDay = rd;
    out.availableSpotsPerDay = Math.floor(Math.max(0, td - bd - rd));
  }
  return out;
}

// Helper function to check if date is in blackout period
export const isDateBlackedOut = (
  date: Date,
  blackouts?: Blackout[],
): boolean => {
  const dateStr = date.toISOString().split("T")[0];
  return blackouts
    ? blackouts.some(
        (blackout) =>
          dateStr >= blackout.startDate && dateStr <= blackout.endDate,
      )
    : false;
};

// Type for operating hour range (with minute precision)
export interface OperatingHourRange {
  start: number;
  end: number;
  startMinute: number;
  endMinute: number;
}

// Helper function to get all operating hour ranges for a day
// Handles multiple operating time slots like [{ start: "23:30:00", end: "00:00:00" }, { start: "00:00:00", end: "15:00:00" }]
export const getOperatingHours = (
  date: Date,
  operatingTimes?: Record<string, OperatingTime[]>,
): OperatingHourRange[] | null => {
  if (!operatingTimes) return null;

  // Support both numeric day index and day name formats
  const dayIndex = date.getDay();
  const dayNames = [
    "sunday",
    "monday",
    "tuesday",
    "wednesday",
    "thursday",
    "friday",
    "saturday",
  ];
  const dayName = dayNames[dayIndex];

  // Try numeric index first, then day name, then additionalProp format
  const times =
    operatingTimes[dayIndex] ||
    operatingTimes[dayName] ||
    operatingTimes[`additionalProp${dayIndex}`];

  if (!times || times.length === 0) return null;

  // Process all operating time slots
  const ranges: OperatingHourRange[] = [];

  for (const timeSlot of times) {
    const startHour = parseInt(timeSlot.start.split(":")[0], 10);
    const startMinute = parseInt(timeSlot.start.split(":")[1], 10);
    const endHour = parseInt(timeSlot.end.split(":")[0], 10);
    const endMinute = parseInt(timeSlot.end.split(":")[1], 10);

    // Handle midnight (00:00:00) as end time - it means end of day (24:00)
    const effectiveEndHour = endHour === 0 && endMinute === 0 ? 24 : endHour;
    const effectiveEndMinute = endHour === 0 && endMinute === 0 ? 0 : endMinute;

    ranges.push({
      start: startHour,
      end: effectiveEndHour,
      startMinute: startMinute,
      endMinute: effectiveEndMinute,
    });
  }

  return ranges.length > 0 ? ranges : null;
};

// Helper function to check if a specific hour is within any operating time range
export const isHourWithinOperatingTimes = (
  hour: number,
  operatingRanges: OperatingHourRange[],
): boolean => {
  return operatingRanges.some((range) => {
    // Hour is within range if:
    // - hour > start hour OR (hour == start hour and we consider partial hours)
    // - hour < end hour OR (hour == end hour and there are end minutes)
    if (hour > range.start && hour < range.end) return true;
    if (hour === range.start) return true; // Start hour is always included
    if (hour === range.end && range.endMinute > 0) return true; // End hour included if has minutes
    return false;
  });
};

// Helper function to calculate total operating hours count from all ranges (with minute precision)
export const getTotalOperatingHoursCount = (
  operatingRanges: OperatingHourRange[],
): number => {
  return operatingRanges.reduce((total, range) => {
    const fullHours = range.end - range.start;
    // Subtract start minutes fraction and add end minutes fraction
    const startFraction = range.startMinute / 60;
    const endFraction = range.endMinute / 60;
    return total + fullHours - startFraction + endFraction;
  }, 0);
};

// Helper function to get the fraction of an hour that is within operating/booking time
// Returns a value between 0 and 1 representing what portion of the hour is active
export const getHourFraction = (
  hour: number,
  startHour: number,
  startMinute: number,
  endHour: number,
  endMinute: number,
): number => {
  // If hour is completely outside the range
  if (hour < startHour || hour > endHour) return 0;
  if (hour === endHour && endMinute === 0) return 0;

  let fractionStart = 0; // Start of the active portion within this hour (0-60)
  let fractionEnd = 60; // End of the active portion within this hour (0-60)

  // If this is the start hour, active portion starts from startMinute
  if (hour === startHour) {
    fractionStart = startMinute;
  }

  // If this is the end hour, active portion ends at endMinute
  if (hour === endHour) {
    fractionEnd = endMinute === 59 ? 60 : endMinute; // Treat endMinute 59 as full hour
  }

  // Calculate the fraction of the hour that is active
  return Math.max(0, (fractionEnd - fractionStart) / 60);
};

// Helper function to get all operating hours as an array
export const getAllOperatingHours = (
  operatingRanges: OperatingHourRange[],
): number[] => {
  const hours: number[] = [];
  for (const range of operatingRanges) {
    for (let h = range.start; h < range.end; h++) {
      if (!hours.includes(h)) {
        hours.push(h);
      }
    }
  }
  return hours.sort((a, b) => a - b);
};

/** Each hour that has operating time with its fraction (0–1). Used so partial hours (e.g. 11:30 PM–12 AM) don't over-count booked spots. */
export const getOperatingHourFractions = (
  operatingRanges: OperatingHourRange[],
): { hour: number; fraction: number }[] => {
  const byHour = new Map<number, number>();
  for (const range of operatingRanges) {
    const lastHour = range.endMinute > 0 ? range.end : range.end - 1;
    for (let h = range.start; h <= lastHour; h++) {
      const frac = getHourFraction(
        h,
        range.start,
        range.startMinute,
        range.end,
        range.endMinute,
      );
      if (frac > 0) {
        byHour.set(h, (byHour.get(h) ?? 0) + frac);
      }
    }
  }
  return Array.from(byHour.entries())
    .map(([hour, fraction]) => ({ hour, fraction }))
    .sort((a, b) => a.hour - b.hour);
};

// Helper function to parse slot times in the inventory timezone
export const parseSlotTimes = (
  slot: BookingSlot,
  timeZone: string,
): { slotStart: Date; slotEnd: Date } => {
  const slotStart = new Date(slot.startTime);
  const slotEnd = new Date(slot.endTime);

  // Convert to the inventory's timezone
  const slotStartStr = slotStart.toLocaleString("en-US", {
    timeZone: timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });
  const slotEndStr = slotEnd.toLocaleString("en-US", {
    timeZone: timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });

  return {
    slotStart: new Date(slotStartStr),
    slotEnd: new Date(slotEndStr),
  };
};

// Helper function to get hour range for a specific date in a booking slot
function getHourRangeForDate(
  dateOnly: Date,
  slotStartDate: Date,
  slotEndDate: Date,
  slotStart: Date,
  slotEnd: Date,
): { startHour: number; endHour: number; endMinute: number } {
  if (
    dateOnly.getTime() === slotStartDate.getTime() &&
    dateOnly.getTime() === slotEndDate.getTime()
  ) {
    return {
      startHour: slotStart.getHours(),
      endHour: slotEnd.getHours(),
      endMinute: slotEnd.getMinutes(),
    };
  }
  if (dateOnly.getTime() === slotStartDate.getTime()) {
    return {
      startHour: slotStart.getHours(),
      endHour: 24,
      endMinute: 0,
    };
  }
  if (dateOnly.getTime() === slotEndDate.getTime()) {
    return {
      startHour: 0,
      endHour: slotEnd.getHours(),
      endMinute: slotEnd.getMinutes(),
    };
  }
  return {
    startHour: 0,
    endHour: 24,
    endMinute: 0,
  };
}

// Helper function to check if a date/time falls within a booking slot.
// Allocates booking by day: e.g. 2026-02-10T13:00:00 to 2026-02-13T15:00:00
// - 10 Feb: 13:00 to 24:00 (hours 13-23)
// - 11–12 Feb: full day (hours 0-23)
// - 13 Feb: 00:00 to 15:00 (hours 0-14, 15:00 exclusive)
export const isTimeInBookingSlot = (
  date: Date,
  hour: number | undefined,
  slot: BookingSlot,
  timeZone: string,
): boolean => {
  const { slotStart, slotEnd } = parseSlotTimes(slot, timeZone);

  const dateOnly = new Date(
    date.getFullYear(),
    date.getMonth(),
    date.getDate(),
  );
  const slotStartDate = new Date(
    slotStart.getFullYear(),
    slotStart.getMonth(),
    slotStart.getDate(),
  );
  const slotEndDate = new Date(
    slotEnd.getFullYear(),
    slotEnd.getMonth(),
    slotEnd.getDate(),
  );

  if (dateOnly < slotStartDate || dateOnly > slotEndDate) {
    return false;
  }

  if (hour === undefined) {
    return true;
  }

  const { startHour, endHour, endMinute } = getHourRangeForDate(
    dateOnly,
    slotStartDate,
    slotEndDate,
    slotStart,
    slotEnd,
  );

  const effectiveEndHour = endHour === 0 && endMinute === 0 ? 24 : endHour;
  const effectiveEndMinute = endHour === 0 && endMinute === 0 ? 0 : endMinute;

  if (hour < startHour) return false;
  if (hour > effectiveEndHour) return false;
  if (hour === effectiveEndHour && effectiveEndMinute === 0) return false;

  return true;
};

// Helper function to check booking status for a slot
function checkSlotStatus(
  booking: Booking,
  slot: BookingSlot,
): { isBooked: boolean; isReserved: boolean } {
  const isBooked = booking.status === "booked" || slot.status === "booked";
  const isReserved =
    booking.status === "reserved" || slot.status === "reserved";
  return { isBooked, isReserved };
}

// Helper function to check if a date/time has any bookings (for classic inventory)
// Classic inventory doesn't have spot-based booking - it's either booked or not
export const hasBookingsForDateTime = (
  date: Date,
  hour: number | undefined,
  inventoryData: InventoryAvailabilityData,
): { hasBookings: boolean; isReserved: boolean; isBooked: boolean } => {
  const { bookings } = inventoryData;

  if (!bookings || bookings.length === 0) {
    return { hasBookings: false, isReserved: false, isBooked: false };
  }

  // Filter guaranteed bookings only
  const guaranteedBookings = bookings.filter(
    (booking) => booking.bookingType === "guaranteed",
  );

  let isBooked = false;
  let isReserved = false;

  for (const booking of guaranteedBookings) {
    for (const slot of booking.slots) {
      if (isTimeInBookingSlot(date, hour, slot, inventoryData.timeZone)) {
        const status = checkSlotStatus(booking, slot);
        if (status.isBooked) {
          isBooked = true;
        } else if (status.isReserved) {
          isReserved = true;
        }
      }
    }
  }

  return {
    hasBookings: isBooked || isReserved,
    isReserved,
    isBooked,
  };
};

// Calculate spots for classic inventory (simplified: booked or not)
export const calculateSpotsForClassicInventory = (
  date: Date,
  hour: number | undefined,
  inventoryData: InventoryAvailabilityData,
): SpotData => {
  const { blackouts } = inventoryData;

  // Check if date is blacked out
  if (isDateBlackedOut(date, blackouts)) {
    return {
      totalSpots: 0,
      availableSpots: 0,
      bookedSpots: 0,
      reservedSpots: 0,
      isBlocked: true,
    };
  }

  // Get operating hours (now returns array of ranges)
  const operatingTimes = inventoryData.schedule?.operatingTimes || {
    monday: [{ start: "00:00:00", end: "23:59:59" }],
    tuesday: [{ start: "00:00:00", end: "23:59:59" }],
    wednesday: [{ start: "00:00:00", end: "23:59:59" }],
    thursday: [{ start: "00:00:00", end: "23:59:59" }],
    friday: [{ start: "00:00:00", end: "23:59:59" }],
    saturday: [{ start: "00:00:00", end: "23:59:59" }],
    sunday: [{ start: "00:00:00", end: "23:59:59" }],
  };
  const operatingRanges = getOperatingHours(date, operatingTimes);
  if (!operatingRanges || operatingRanges.length === 0) {
    return {
      totalSpots: 0,
      availableSpots: 0,
      bookedSpots: 0,
      reservedSpots: 0,
    };
  }

  // If specific hour is provided, check if it's within operating hours
  if (
    hour !== undefined &&
    !isHourWithinOperatingTimes(hour, operatingRanges)
  ) {
    return {
      totalSpots: 0,
      availableSpots: 0,
      bookedSpots: 0,
      reservedSpots: 0,
    };
  }

  // For classic inventory, we use 1 as total (representing the single booking slot)
  // Either it's booked (1) or available (0)
  const bookingStatus = hasBookingsForDateTime(date, hour, inventoryData);

  if (bookingStatus.isBooked) {
    return {
      totalSpots: 1,
      availableSpots: 0,
      bookedSpots: 1,
      reservedSpots: 0,
    };
  }

  if (bookingStatus.isReserved) {
    return {
      totalSpots: 1,
      availableSpots: 0,
      bookedSpots: 0,
      reservedSpots: 1,
    };
  }

  // Available
  return {
    totalSpots: 1,
    availableSpots: 1,
    bookedSpots: 0,
    reservedSpots: 0,
  };
};

// Helper function to process booking slots for daily view
function processBookingSlotsForDailyView(
  date: Date,
  hour: number,
  guaranteedBookings: Booking[],
  inventoryData: InventoryAvailabilityData,
  inventoryDetails: InventoryItem,
  loopsPerHour: number,
  spotPosition: number,
): { bookedSpots: number; reservedSpots: number } {
  let bookedSpots = 0;
  let reservedSpots = 0;

  guaranteedBookings.forEach((booking) => {
    booking.slots.forEach((slot) => {
      if (!isTimeInBookingSlot(date, hour, slot, inventoryData.timeZone))
        return;

      const allocatedLoops = Math.floor(
        slot.loopSecondsAllocated / inventoryDetails.operations.slotDuration,
      );
      const shouldCountSlot =
        !slot.slotPositions || slot.slotPositions.length === 0
          ? spotPosition <= allocatedLoops
          : slot.slotPositions.includes(spotPosition);
      const status = checkSlotStatus(booking, slot);
      if (shouldCountSlot) {
        if (status.isBooked) {
          bookedSpots += loopsPerHour;
        } else if (status.isReserved) {
          reservedSpots += loopsPerHour;
        }
      }
    });
  });

  return { bookedSpots, reservedSpots };
}

// Calculate spots for daily view (hourly with specific spot) - Digital inventory
export const calculateSpotsForDailyView = (
  date: Date,
  hour: number,
  inventoryData: InventoryAvailabilityData,
  _clientPerLoop: number,
  spotPosition: number,
  inventoryDetails: InventoryItem | null,
): SpotData => {
  const { bookings, blackouts, loopDuration } = inventoryData;

  // Check if date is blacked out
  if (isDateBlackedOut(date, blackouts) || !inventoryDetails) {
    return {
      totalSpots: 0,
      availableSpots: 0,
      bookedSpots: 0,
      reservedSpots: 0,
      isBlocked: isDateBlackedOut(date, blackouts),
    };
  }

  // Get operating hours (now returns array of ranges)
  const operatingRanges = getOperatingHours(
    date,
    inventoryData.schedule?.operatingTimes,
  );
  if (!operatingRanges || operatingRanges.length === 0) {
    return {
      totalSpots: 0,
      availableSpots: 0,
      bookedSpots: 0,
      reservedSpots: 0,
    };
  }

  // Check if the specific hour is within operating hours
  if (!isHourWithinOperatingTimes(hour, operatingRanges)) {
    return {
      totalSpots: 0,
      availableSpots: 0,
      bookedSpots: 0,
      reservedSpots: 0,
    };
  }

  const loopDurationMinutes = loopDuration / 60;
  const loopsPerHour = Math.floor(60 / loopDurationMinutes);
  const totalSpotsForHour = loopsPerHour;
  const totalSpotsPerDayForSpot = loopsPerHour;

  // Filter guaranteed bookings only
  const guaranteedBookings = bookings
    ? bookings.filter((booking) => booking.bookingType === "guaranteed")
    : [];

  const { bookedSpots, reservedSpots } = processBookingSlotsForDailyView(
    date,
    hour,
    guaranteedBookings,
    inventoryData,
    inventoryDetails,
    loopsPerHour,
    spotPosition,
  );

  const availableSpots = totalSpotsForHour - bookedSpots - reservedSpots;

  return toIntegerSpotData({
    totalSpots: totalSpotsForHour,
    availableSpots: Math.max(0, availableSpots),
    bookedSpots,
    reservedSpots,
    totalSpotsPerDay: totalSpotsPerDayForSpot,
    availableSpotsPerDay: Math.max(0, availableSpots),
    bookedSpotsPerDay: bookedSpots,
    reservedSpotsPerDay: reservedSpots,
  });
};

// Helper function to process booking slots for weekly view
function processBookingSlotsForWeeklyView(
  date: Date,
  hourFractions: { hour: number; fraction: number }[],
  guaranteedBookings: Booking[],
  inventoryData: InventoryAvailabilityData,
  inventoryDetails: InventoryItem,
  loopsPerHour: number,
  spotPosition: number,
): { bookedSpots: number; reservedSpots: number } {
  let bookedSpots = 0;
  let reservedSpots = 0;

  for (const { hour: dayHour, fraction } of hourFractions) {
    const spotsForThisHour = Math.ceil(loopsPerHour * fraction);
    guaranteedBookings.forEach((booking) => {
      booking.slots.forEach((slot) => {
        if (!isTimeInBookingSlot(date, dayHour, slot, inventoryData.timeZone))
          return;

        const allocatedSpots = Math.floor(
          slot.loopSecondsAllocated / inventoryDetails.operations.slotDuration,
        );
        const shouldCountSlot =
          !slot.slotPositions || slot.slotPositions.length === 0
            ? spotPosition <= allocatedSpots
            : slot.slotPositions.includes(spotPosition);
        const status = checkSlotStatus(booking, slot);
        if (shouldCountSlot) {
          if (status.isBooked) {
            bookedSpots += spotsForThisHour;
          } else if (status.isReserved) {
            reservedSpots += spotsForThisHour;
          }
        }
      });
    });
  }

  return { bookedSpots, reservedSpots };
}

// Calculate spots for weekly view (day-wise with specific spot) - Digital inventory
export const calculateSpotsForWeeklyView = (
  date: Date,
  inventoryData: InventoryAvailabilityData,
  _clientPerLoop: number,
  spotPosition: number,
  inventoryDetails: InventoryItem | null,
): SpotData => {
  const { bookings, blackouts, loopDuration } = inventoryData;

  // Check if date is blacked out
  if (isDateBlackedOut(date, blackouts) || !inventoryDetails) {
    return {
      totalSpots: 0,
      availableSpots: 0,
      bookedSpots: 0,
      reservedSpots: 0,
      isBlocked: isDateBlackedOut(date, blackouts),
    };
  }

  // Get operating hours (now returns array of ranges)
  const operatingRanges = getOperatingHours(
    date,
    inventoryData.schedule?.operatingTimes,
  );
  if (!operatingRanges || operatingRanges.length === 0) {
    return {
      totalSpots: 0,
      availableSpots: 0,
      bookedSpots: 0,
      reservedSpots: 0,
    };
  }

  const loopDurationMinutes = loopDuration / 60;
  const operatingHoursCount = getTotalOperatingHoursCount(operatingRanges);
  const loopsPerHour = Math.floor(60 / loopDurationMinutes);
  const spotsPerHourForSpot = loopsPerHour;
  const totalSpotsForDayForSpot = spotsPerHourForSpot * operatingHoursCount;

  // Filter guaranteed bookings only
  const guaranteedBookings = bookings
    ? bookings.filter((booking) => booking.bookingType === "guaranteed")
    : [];

  const hourFractions = getOperatingHourFractions(operatingRanges);
  const { bookedSpots, reservedSpots } = processBookingSlotsForWeeklyView(
    date,
    hourFractions,
    guaranteedBookings,
    inventoryData,
    inventoryDetails,
    loopsPerHour,
    spotPosition,
  );

  const availableSpots = totalSpotsForDayForSpot - bookedSpots - reservedSpots;

  return toIntegerSpotData({
    totalSpots: totalSpotsForDayForSpot,
    availableSpots: Math.max(0, availableSpots),
    bookedSpots,
    reservedSpots,
  });
};

// Helper to calculate spots based on slot status
function calculateSpotsByStatus(
  slot: BookingSlot,
  booking: Booking,
  spotsAmount: number,
): { bookedSpots: number; reservedSpots: number } {
  const status = checkSlotStatus(booking, slot);

  return {
    bookedSpots: status.isBooked ? spotsAmount : 0,
    reservedSpots: status.isReserved ? spotsAmount : 0,
  };
}

// Helper function to calculate spots for a slot in monthly view (per hour)
function calculateSpotsForSlotHour(
  hour: number,
  rangeHours: {
    startHour: number;
    startMinute: number;
    endHour: number;
    endMinute: number;
  },
  slot: BookingSlot,
  booking: Booking,
  spotsPerHourForSlot: number,
  spotPerHour: number,
): { bookedSpots: number; reservedSpots: number } {
  const hourFraction = getHourFraction(
    hour,
    rangeHours.startHour,
    rangeHours.startMinute,
    rangeHours.endHour,
    rangeHours.endMinute,
  );

  if (hourFraction <= 0) {
    return { bookedSpots: 0, reservedSpots: 0 };
  }

  const spotsToAdd =
    !slot.slotPositions || slot.slotPositions.length === 0
      ? Math.ceil(spotsPerHourForSlot * hourFraction)
      : Math.ceil(slot.slotPositions.length * spotPerHour * hourFraction);

  return calculateSpotsByStatus(slot, booking, spotsToAdd);
}

// Helper function to process a single booking slot for monthly view
function processSlotForMonthlyView(
  booking: Booking,
  slot: BookingSlot,
  date: Date,
  operatingRanges: OperatingHourRange[],
  inventoryData: InventoryAvailabilityData,
  inventoryDetails: InventoryItem,
  spotPerHour: number,
): { bookedSpots: number; reservedSpots: number } {
  let bookedSpots = 0;
  let reservedSpots = 0;

  const { slotStart, slotEnd } = parseSlotTimes(slot, inventoryData.timeZone);

  const dateOnly = new Date(
    date.getFullYear(),
    date.getMonth(),
    date.getDate(),
  );
  const slotStartDate = new Date(
    slotStart.getFullYear(),
    slotStart.getMonth(),
    slotStart.getDate(),
  );
  const slotEndDate = new Date(
    slotEnd.getFullYear(),
    slotEnd.getMonth(),
    slotEnd.getDate(),
  );

  // Only process if slot overlaps with this date
  if (dateOnly < slotStartDate || dateOnly > slotEndDate) {
    return { bookedSpots: 0, reservedSpots: 0 };
  }

  const {
    startHour: rangeStartHour,
    endHour: rangeEndHour,
    endMinute: rangeEndMinute,
  } = getHourRangeForDate(
    dateOnly,
    slotStartDate,
    slotEndDate,
    slotStart,
    slotEnd,
  );
  const rangeStartMinute =
    dateOnly.getTime() === slotStartDate.getTime() ? slotStart.getMinutes() : 0;

  const effectiveEndHour =
    rangeEndHour === 0 && rangeEndMinute === 0 ? 24 : rangeEndHour;
  const effectiveEndMinute =
    rangeEndHour === 0 && rangeEndMinute === 0 ? 0 : rangeEndMinute;
  const lastHourToIterate =
    effectiveEndMinute > 0 ? effectiveEndHour + 1 : effectiveEndHour;

  const totalAllocatedSpotsPerHour =
    Math.floor(
      slot.loopSecondsAllocated / inventoryDetails.operations.slotDuration,
    ) * spotPerHour;
  const spotsPerHourForSlot = totalAllocatedSpotsPerHour;

  for (let hour = rangeStartHour; hour < lastHourToIterate; hour++) {
    if (!isHourWithinOperatingTimes(hour, operatingRanges)) continue;

    const hourSpots = calculateSpotsForSlotHour(
      hour,
      {
        startHour: rangeStartHour,
        startMinute: rangeStartMinute,
        endHour: effectiveEndHour,
        endMinute: effectiveEndMinute,
      },
      slot,
      booking,
      spotsPerHourForSlot,
      spotPerHour,
    );

    bookedSpots += hourSpots.bookedSpots;
    reservedSpots += hourSpots.reservedSpots;
  }

  return { bookedSpots, reservedSpots };
}

// Calculate spots for monthly view (whole day, no specific spot) - Digital inventory
export const calculateSpotsForMonthlyView = (
  date: Date,
  inventoryData: InventoryAvailabilityData,
  clientPerLoop: number,
  inventoryDetails: InventoryItem | null,
): SpotData => {
  if (!inventoryDetails) {
    return {
      totalSpots: 0,
      availableSpots: 0,
      bookedSpots: 0,
      reservedSpots: 0,
    };
  }
  const { bookings, blackouts, loopDuration } = inventoryData;

  // Check if date is blacked out
  if (isDateBlackedOut(date, blackouts)) {
    return {
      totalSpots: 0,
      availableSpots: 0,
      bookedSpots: 0,
      reservedSpots: 0,
      isBlocked: true,
    };
  }

  // Get operating hours (now returns array of ranges)
  const operatingRanges = getOperatingHours(
    date,
    inventoryData.schedule?.operatingTimes,
  );
  if (!operatingRanges || operatingRanges.length === 0) {
    return {
      totalSpots: 0,
      availableSpots: 0,
      bookedSpots: 0,
      reservedSpots: 0,
    };
  }

  const loopDurationMinutes = loopDuration / 60;
  const totalSpotsPerHour = Math.floor(
    (60 / loopDurationMinutes) * clientPerLoop,
  );
  const spotPerHour = totalSpotsPerHour / clientPerLoop;
  const operatingHoursCount = getTotalOperatingHoursCount(operatingRanges);
  const totalSpotsPerDay = totalSpotsPerHour * operatingHoursCount;

  let bookedSpots = 0;
  let reservedSpots = 0;

  // Filter guaranteed bookings only
  const guaranteedBookings = bookings
    ? bookings.filter((booking) => booking.bookingType === "guaranteed")
    : [];

  // Process each booking slot
  guaranteedBookings.forEach((booking) => {
    booking.slots.forEach((slot) => {
      const slotSpots = processSlotForMonthlyView(
        booking,
        slot,
        date,
        operatingRanges,
        inventoryData,
        inventoryDetails,
        spotPerHour,
      );
      bookedSpots += slotSpots.bookedSpots;
      reservedSpots += slotSpots.reservedSpots;
    });
  });

  const availableSpots = totalSpotsPerDay - bookedSpots - reservedSpots;
  bookedSpots = Math.min(bookedSpots, totalSpotsPerDay);
  reservedSpots = Math.min(reservedSpots, totalSpotsPerDay);

  return toIntegerSpotData({
    totalSpots: totalSpotsPerDay,
    availableSpots: Math.max(0, availableSpots),
    bookedSpots,
    reservedSpots,
  });
};

// Main function to calculate spots - routes to appropriate view-specific function
export const calculateSpotsForDateTime = (
  date: Date,
  hour: number | undefined,
  inventoryData: InventoryAvailabilityData,
  clientPerLoop: number,
  inventoryDetails: InventoryItem | null,
  spotPosition?: number,
  isClassic?: boolean,
): SpotData => {
  // For classic inventory, use simplified calculation (booked or not)
  if (isClassic) {
    return calculateSpotsForClassicInventory(date, hour, inventoryData);
  }

  // For daily view (hourly with specific spot)
  if (hour !== undefined && spotPosition !== undefined) {
    return calculateSpotsForDailyView(
      date,
      hour,
      inventoryData,
      clientPerLoop,
      spotPosition,
      inventoryDetails,
    );
  }

  // For weekly view (day-wise with specific spot)
  if (hour === undefined && spotPosition !== undefined) {
    return calculateSpotsForWeeklyView(
      date,
      inventoryData,
      clientPerLoop,
      spotPosition,
      inventoryDetails,
    );
  }

  // For monthly view (whole day, no specific spot)
  if (hour === undefined && spotPosition === undefined) {
    return calculateSpotsForMonthlyView(
      date,
      inventoryData,
      clientPerLoop,
      inventoryDetails,
    );
  }

  // Fallback (should not reach here)
  return {
    totalSpots: 0,
    availableSpots: 0,
    bookedSpots: 0,
    reservedSpots: 0,
  };
};

// Helper function to determine status from spot data
export const getStatusFromSpotData = (
  spotData: SpotData,
  monthlyView?: boolean,
): SpotStatus => {
  if (spotData.isBlocked) return "blocked";
  if (spotData.totalSpots === 0) return "available";

  // Determine status based on maximum spots
  const maxSpots = Math.max(
    spotData.bookedSpots,
    spotData.reservedSpots,
    spotData.availableSpots,
  );

  if (spotData.bookedSpots === maxSpots && spotData.bookedSpots > 0) {
    return monthlyView ? "fully_booked" : "booked";
  }

  if (spotData.reservedSpots === maxSpots && spotData.reservedSpots > 0) {
    return "reserved";
  }

  return "available";
};

// Helper function to calculate booked percentage
export const getBookedPercentage = (spotData: SpotData): number => {
  if (spotData.totalSpots === 0) return 0;
  const booked = spotData.bookedSpots + spotData.reservedSpots;
  return (booked / spotData.totalSpots) * 100;
};

// Helper function to get color based on booked percentage
export const getColorFromPercentage = (
  percentage: number,
):
  | "success"
  | "error"
  | "secondary"
  | "warning"
  | "primary"
  | "info"
  | "neutral"
  | "teal"
  | "lightGreen"
  | "orange"
  | undefined => {
  if (percentage >= 100) return "error";
  if (percentage > 95) return "error";
  if (percentage > 75) return "neutral";
  if (percentage > 50) return "warning";
  if (percentage > 25) return "primary";
  return "success";
};

// Helper function to get booking details for a specific date/hour
export const getBookingDetailsForDateTime = (
  date: Date,
  hour: number | undefined,
  inventoryData: InventoryAvailabilityData,
  bookings?: Booking[],
): Booking[] => {
  const guaranteedBookings = bookings
    ? bookings.filter((booking) => booking.bookingType === "guaranteed")
    : [];

  return guaranteedBookings.filter((booking) =>
    booking.slots.some((slot) =>
      isTimeInBookingSlot(date, hour, slot, inventoryData.timeZone),
    ),
  );
};

// --- Availability index for fast calendar rendering ---

const toDateStr = (date: Date): string => date.toISOString().split("T")[0];

export type AvailabilityViewMode = "monthly" | "weekly" | "daily";

export interface ParsedSlot {
  startDateStr: string;
  endDateStr: string;
  startHour: number;
  startMinute: number;
  endHour: number;
  endMinute: number;
  booking: Booking;
  slot: BookingSlot;
  slotPositions: number[];
  loopSecondsAllocated: number;
  /** For bookingMode "time": seconds allocated in this slot (from slot.secondsAllocated). */
  secondsAllocated: number;
}

export interface AvailabilityIndex {
  parsedSlots: ParsedSlot[];
  operatingRangesByDay: Record<number, OperatingHourRange[]>;
  totalOperatingHoursByDay: Record<number, number>;
  /** For bookingMode "time": total operating seconds per day (hours * 3600). */
  totalOperatingSecondsByDay?: Record<number, number>;
  blackouts: Blackout[];
  timeZone: string;
  isClassic: boolean;
  bookingMode: "loop" | "time";
  loopsPerHour: number;
  spotPerHour: number;
  clientPerLoop: number;
  slotDuration: number;
}

/**
 * Get the effective hour range for a slot on a specific date.
 * Booking 2026-02-10T13:00:00 to 2026-02-13T15:00:00:
 * - 10 Feb: 13:00 to 24:00 (11 hours)
 * - 11–12 Feb: full day (24 hours)
 * - 13 Feb: 00:00 to 15:00 (15 hours, end exclusive)
 */
function getSlotHourRangeForDate(
  dateStr: string,
  p: ParsedSlot,
): {
  startHour: number;
  startMinute: number;
  endHour: number;
  endMinute: number;
} | null {
  if (dateStr < p.startDateStr || dateStr > p.endDateStr) return null;
  if (dateStr === p.startDateStr && dateStr === p.endDateStr) {
    return {
      startHour: p.startHour,
      startMinute: p.startMinute,
      endHour: p.endHour,
      endMinute: p.endMinute,
    };
  }
  if (dateStr === p.startDateStr) {
    return {
      startHour: p.startHour,
      startMinute: p.startMinute,
      endHour: 24,
      endMinute: 0,
    };
  }
  if (dateStr === p.endDateStr) {
    return {
      startHour: 0,
      startMinute: 0,
      endHour: p.endHour,
      endMinute: p.endMinute,
    };
  }
  return { startHour: 0, startMinute: 0, endHour: 24, endMinute: 0 };
}

function isDateTimeInParsedSlot(
  dateStr: string,
  hour: number | undefined,
  p: ParsedSlot,
): boolean {
  if (dateStr < p.startDateStr || dateStr > p.endDateStr) return false;
  const range = getSlotHourRangeForDate(dateStr, p);
  if (!range) return false;
  if (hour === undefined) return true;
  if (hour < range.startHour) return false;
  if (hour > range.endHour) return false;
  if (hour === range.endHour && range.endMinute === 0) return false;
  if (hour === range.startHour && range.startMinute > 0) return true;
  return true;
}

function isDateBlackedOutFromIndex(
  dateStr: string,
  blackouts: Blackout[],
): boolean {
  return blackouts.some((b) => dateStr >= b.startDate && dateStr <= b.endDate);
}

/**
 * For bookingMode "time": get the portion of this slot's secondsAllocated
 * that falls within the given (dateStr, hour). Used to attribute booked/reserved
 * seconds to the correct hour for availability.
 */
function getSlotSecondsAllocatedInHour(
  dateStr: string,
  hour: number,
  p: ParsedSlot,
  operatingRanges: OperatingHourRange[],
): number {
  if (!isDateTimeInParsedSlot(dateStr, hour, p)) return 0;
  if (!isHourWithinOperatingTimes(hour, operatingRanges)) return 0;
  const dayRange = getSlotHourRangeForDate(dateStr, p);
  if (!dayRange) return 0;

  const effectiveEndHour = dayRange.endHour === 24 ? 24 : dayRange.endHour;
  const hourFraction = getHourFraction(
    hour,
    dayRange.startHour,
    dayRange.startMinute,
    effectiveEndHour,
    dayRange.endMinute,
  );
  if (hourFraction <= 0) return 0;

  const slotStartSeconds =
    dayRange.startHour * 3600 + dayRange.startMinute * 60;
  const slotEndSeconds =
    (dayRange.endHour === 24 ? 24 : dayRange.endHour) * 3600 +
    dayRange.endMinute * 60;
  const slotDurationSeconds = Math.max(1, slotEndSeconds - slotStartSeconds);
  const overlapSeconds = hourFraction * 3600;
  const proportion = Math.min(1, overlapSeconds / slotDurationSeconds);
  return Math.round(p.secondsAllocated * proportion);
}

// Helper function to build operating ranges for all days of the week
function buildOperatingRangesByDay(
  operatingTimes: Record<string, OperatingTime[]>,
): {
  operatingRangesByDay: Record<number, OperatingHourRange[]>;
  totalOperatingHoursByDay: Record<number, number>;
} {
  const operatingRangesByDay: Record<number, OperatingHourRange[]> = {};
  const totalOperatingHoursByDay: Record<number, number> = {};
  const baseDate = new Date(2020, 0, 5);

  for (let d = 0; d < 7; d++) {
    const date = new Date(baseDate);
    date.setDate(baseDate.getDate() + d);
    const ranges = getOperatingHours(date, operatingTimes);
    operatingRangesByDay[date.getDay()] = ranges ?? [];
    totalOperatingHoursByDay[date.getDay()] = ranges
      ? getTotalOperatingHoursCount(ranges)
      : 0;
  }

  return { operatingRangesByDay, totalOperatingHoursByDay };
}

// Helper function to parse a booking slot into ParsedSlot format
function parseBookingSlot(
  booking: Booking,
  slot: BookingSlot,
  timeZone: string,
): ParsedSlot {
  const { slotStart, slotEnd } = parseSlotTimes(slot, timeZone);
  const startDateStr = toDateStr(
    new Date(
      slotStart.getFullYear(),
      slotStart.getMonth(),
      slotStart.getDate(),
    ),
  );
  const endDateStr = toDateStr(
    new Date(slotEnd.getFullYear(), slotEnd.getMonth(), slotEnd.getDate()),
  );
  const slotStartHour = slotStart.getHours();
  const slotStartMinute = slotStart.getMinutes();
  let slotEndHour = slotEnd.getHours();
  let slotEndMinute = slotEnd.getMinutes();

  if (slotEndHour === 0 && slotEndMinute === 0) {
    slotEndHour = 24;
    slotEndMinute = 0;
  }

  return {
    startDateStr,
    endDateStr,
    startHour: slotStartHour,
    startMinute: slotStartMinute,
    endHour: slotEndHour,
    endMinute: slotEndMinute,
    booking,
    slot,
    slotPositions: slot.slotPositions ?? [],
    loopSecondsAllocated: slot.loopSecondsAllocated,
    secondsAllocated: slot.secondsAllocated ?? 0,
  };
}

/**
 * Build a one-time index from availability data. Use this when data loads;
 * then use getSpotDataFromIndex for each cell so calendar date changes only
 * do fast lookups (date/hour in range) instead of re-parsing slots.
 */
export function buildAvailabilityIndex(
  inventoryData: InventoryAvailabilityData,
  inventoryDetails: InventoryItem | null,
): AvailabilityIndex | null {
  const {
    bookings,
    blackouts = [],
    schedule,
    timeZone,
    bookingMode,
  } = inventoryData;
  const operatingTimes = schedule?.operatingTimes;
  if (!operatingTimes) return null;

  const { operatingRangesByDay, totalOperatingHoursByDay } =
    buildOperatingRangesByDay(operatingTimes);

  const isClassic =
    !inventoryDetails || inventoryDetails.detail?.inventoryType === "Classic";
  const clientPerLoop = inventoryDetails?.operations?.clientPerLoop ?? 10;
  const slotDuration = inventoryDetails?.operations?.slotDuration ?? 60;

  const isTimeMode = bookingMode === "time";
  let loopsPerHour = 0;
  let spotPerHour = 0;
  let totalOperatingSecondsByDay: Record<number, number> | undefined;

  if (isTimeMode) {
    totalOperatingSecondsByDay = {} as Record<number, number>;
    for (let d = 0; d < 7; d++) {
      const hours = totalOperatingHoursByDay[d] ?? 0;
      totalOperatingSecondsByDay[d] = Math.round(hours * 3600);
    }
  } else {
    const loopDurationMinutes = inventoryData.loopDuration / 60;
    loopsPerHour = Math.floor(60 / loopDurationMinutes);
    const totalSpotsPerHour = loopsPerHour * clientPerLoop;
    spotPerHour = totalSpotsPerHour / clientPerLoop;
  }

  const parsedSlots: ParsedSlot[] = [];
  const guaranteedBookings = bookings
    ? bookings.filter((b) => b.bookingType === "guaranteed")
    : [];

  for (const booking of guaranteedBookings) {
    for (const slot of booking.slots) {
      parsedSlots.push(parseBookingSlot(booking, slot, timeZone));
    }
  }

  return {
    parsedSlots,
    operatingRangesByDay,
    totalOperatingHoursByDay,
    totalOperatingSecondsByDay,
    blackouts,
    timeZone,
    isClassic,
    bookingMode: bookingMode ?? "loop",
    loopsPerHour,
    spotPerHour,
    clientPerLoop,
    slotDuration,
  };
}

// Helper function to calculate count for a position check
function calculateCountForPosition(
  positions: number | number[],
  spotPosition: number,
  countValue: number,
): number {
  if (Array.isArray(positions)) {
    return positions.includes(spotPosition) ? countValue : 0;
  }
  return spotPosition <= positions ? countValue : 0;
}

// Helper function for daily view spot calculation
function getDailyViewSpotData(
  dateStr: string,
  hour: number,
  spotPosition: number,
  index: AvailabilityIndex,
): SpotData {
  const { loopsPerHour, slotDuration } = index;
  const totalSpotsForHour = loopsPerHour;
  let bookedSpots = 0;
  let reservedSpots = 0;

  for (const p of index.parsedSlots) {
    if (!isDateTimeInParsedSlot(dateStr, hour, p)) continue;
    const positions =
      p.slotPositions.length === 0
        ? Math.floor(p.loopSecondsAllocated / slotDuration)
        : p.slotPositions;
    const count = calculateCountForPosition(
      positions,
      spotPosition,
      loopsPerHour,
    );
    if (count === 0) continue;
    const status = checkSlotStatus(p.booking, p.slot);

    if (status.isBooked) {
      bookedSpots += count;
    } else if (status.isReserved) {
      reservedSpots += count;
    }
  }

  const availableSpots = Math.max(
    0,
    totalSpotsForHour - bookedSpots - reservedSpots,
  );
  return toIntegerSpotData({
    totalSpots: totalSpotsForHour,
    availableSpots,
    bookedSpots,
    reservedSpots,
    totalSpotsPerDay: totalSpotsForHour,
    availableSpotsPerDay: availableSpots,
    bookedSpotsPerDay: bookedSpots,
    reservedSpotsPerDay: reservedSpots,
  });
}

// --- Time booking mode: values are seconds (UI can show as hours = seconds/3600) ---

function getDailyViewSpotDataForTimeMode(
  dateStr: string,
  hour: number,
  operatingRanges: OperatingHourRange[],
  index: AvailabilityIndex,
): SpotData {
  const hourFractions = getOperatingHourFractions(operatingRanges);
  const fractionForHour =
    hourFractions.find((hf) => hf.hour === hour)?.fraction ?? 0;
  const totalSecondsForHour = Math.round(fractionForHour * 3600);
  if (totalSecondsForHour <= 0) {
    return toIntegerSpotData({
      totalSpots: 0,
      availableSpots: 0,
      bookedSpots: 0,
      reservedSpots: 0,
      totalSpotsPerDay: 0,
      availableSpotsPerDay: 0,
      bookedSpotsPerDay: 0,
      reservedSpotsPerDay: 0,
    });
  }

  let bookedSeconds = 0;
  let reservedSeconds = 0;
  for (const p of index.parsedSlots) {
    const sec = getSlotSecondsAllocatedInHour(
      dateStr,
      hour,
      p,
      operatingRanges,
    );
    if (sec === 0) continue;
    const status = checkSlotStatus(p.booking, p.slot);
    if (status.isBooked) bookedSeconds += sec;
    else if (status.isReserved) reservedSeconds += sec;
  }

  const availableSeconds = Math.max(
    0,
    totalSecondsForHour - bookedSeconds - reservedSeconds,
  );
  return toIntegerSpotData({
    totalSpots: totalSecondsForHour,
    availableSpots: availableSeconds,
    bookedSpots: bookedSeconds,
    reservedSpots: reservedSeconds,
    totalSpotsPerDay: totalSecondsForHour,
    availableSpotsPerDay: availableSeconds,
    bookedSpotsPerDay: bookedSeconds,
    reservedSpotsPerDay: reservedSeconds,
  });
}

function getWeeklyViewSpotDataForTimeMode(
  dateStr: string,
  dayOfWeek: number,
  operatingRanges: OperatingHourRange[],
  index: AvailabilityIndex,
): SpotData {
  const totalSecondsByDay = index.totalOperatingSecondsByDay?.[dayOfWeek] ?? 0;
  if (totalSecondsByDay <= 0) {
    return toIntegerSpotData({
      totalSpots: 0,
      availableSpots: 0,
      bookedSpots: 0,
      reservedSpots: 0,
    });
  }

  const hourFractions = getOperatingHourFractions(operatingRanges);
  let bookedSeconds = 0;
  let reservedSeconds = 0;
  for (const { hour: dayHour } of hourFractions) {
    for (const p of index.parsedSlots) {
      const sec = getSlotSecondsAllocatedInHour(
        dateStr,
        dayHour,
        p,
        operatingRanges,
      );
      if (sec === 0) continue;
      const status = checkSlotStatus(p.booking, p.slot);
      if (status.isBooked) bookedSeconds += sec;
      else if (status.isReserved) reservedSeconds += sec;
    }
  }

  const availableSeconds = Math.max(
    0,
    totalSecondsByDay - bookedSeconds - reservedSeconds,
  );
  return toIntegerSpotData({
    totalSpots: totalSecondsByDay,
    availableSpots: availableSeconds,
    bookedSpots: bookedSeconds,
    reservedSpots: reservedSeconds,
  });
}

function getMonthlyViewSpotDataForTimeMode(
  dateStr: string,
  dayOfWeek: number,
  operatingRanges: OperatingHourRange[],
  index: AvailabilityIndex,
): SpotData {
  const totalSecondsByDay = index.totalOperatingSecondsByDay?.[dayOfWeek] ?? 0;
  if (totalSecondsByDay <= 0) {
    return toIntegerSpotData({
      totalSpots: 0,
      availableSpots: 0,
      bookedSpots: 0,
      reservedSpots: 0,
    });
  }

  const hourFractions = getOperatingHourFractions(operatingRanges);
  let bookedSeconds = 0;
  let reservedSeconds = 0;
  for (const { hour: dayHour } of hourFractions) {
    for (const p of index.parsedSlots) {
      const sec = getSlotSecondsAllocatedInHour(
        dateStr,
        dayHour,
        p,
        operatingRanges,
      );
      if (sec === 0) continue;
      const status = checkSlotStatus(p.booking, p.slot);
      if (status.isBooked) bookedSeconds += sec;
      else if (status.isReserved) reservedSeconds += sec;
    }
  }

  const availableSeconds = Math.max(
    0,
    totalSecondsByDay - bookedSeconds - reservedSeconds,
  );
  return toIntegerSpotData({
    totalSpots: totalSecondsByDay,
    availableSpots: availableSeconds,
    bookedSpots: bookedSeconds,
    reservedSpots: reservedSeconds,
  });
}

// Helper function to process slots for a specific hour in weekly view
function processWeeklySlotForHour(
  p: ParsedSlot,
  spotPosition: number,
  spotsForThisHour: number,
  slotDuration: number,
): { bookedSpots: number; reservedSpots: number } {
  const positions =
    p.slotPositions.length === 0
      ? Math.floor(p.loopSecondsAllocated / slotDuration)
      : p.slotPositions;
  const count = calculateCountForPosition(
    positions,
    spotPosition,
    spotsForThisHour,
  );

  if (count === 0) {
    return { bookedSpots: 0, reservedSpots: 0 };
  }

  const status = checkSlotStatus(p.booking, p.slot);

  return {
    bookedSpots: status.isBooked ? count : 0,
    reservedSpots: status.isReserved ? count : 0,
  };
}

// Helper function for weekly view spot calculation
function getWeeklyViewSpotData(
  dateStr: string,
  dayOfWeek: number,
  spotPosition: number,
  operatingRanges: OperatingHourRange[],
  index: AvailabilityIndex,
): SpotData {
  const { loopsPerHour, slotDuration } = index;
  const operatingHoursCount = index.totalOperatingHoursByDay[dayOfWeek] ?? 0;
  const totalSpotsForDayForSpot = loopsPerHour * operatingHoursCount;
  let bookedSpots = 0;
  let reservedSpots = 0;

  const hourFractions = getOperatingHourFractions(operatingRanges);
  for (const { hour: dayHour, fraction } of hourFractions) {
    const spotsForThisHour = Math.ceil(loopsPerHour * fraction);
    for (const p of index.parsedSlots) {
      if (!isDateTimeInParsedSlot(dateStr, dayHour, p)) continue;

      const slotSpots = processWeeklySlotForHour(
        p,
        spotPosition,
        spotsForThisHour,
        slotDuration,
      );
      bookedSpots += slotSpots.bookedSpots;
      reservedSpots += slotSpots.reservedSpots;
    }
  }

  const availableSpots = Math.max(
    0,
    totalSpotsForDayForSpot - bookedSpots - reservedSpots,
  );
  return toIntegerSpotData({
    totalSpots: totalSpotsForDayForSpot,
    availableSpots,
    bookedSpots,
    reservedSpots,
  });
}

// Helper to calculate spots based on positions
function calculateSpotsForPositions(
  p: ParsedSlot,
  spotsPerHourForSlot: number,
  spotPerHour: number,
  hourFraction: number,
): { bookedSpots: number; reservedSpots: number } {
  let spotsAmount = 0;

  if (p.slotPositions.length === 0) {
    spotsAmount = Math.ceil(spotsPerHourForSlot * hourFraction);
  } else {
    const positionsPerHour = p.slotPositions.length * spotPerHour;
    spotsAmount = Math.ceil(positionsPerHour * hourFraction);
  }

  return calculateSpotsByStatus(p.slot, p.booking, spotsAmount);
}

// Helper function to process spot calculation for a single hour in monthly view
function processMonthlySlotForHour(
  h: number,
  operatingRanges: OperatingHourRange[],
  dayRange: {
    startHour: number;
    startMinute: number;
    endHour: number;
    endMinute: number;
  },
  p: ParsedSlot,
  spotsPerHourForSlot: number,
  spotPerHour: number,
): { bookedSpots: number; reservedSpots: number } {
  if (!isHourWithinOperatingTimes(h, operatingRanges)) {
    return { bookedSpots: 0, reservedSpots: 0 };
  }

  const effectiveEndHour = dayRange.endHour === 24 ? 24 : dayRange.endHour;
  const effectiveEndMinute = dayRange.endMinute;

  const hourFraction = getHourFraction(
    h,
    dayRange.startHour,
    dayRange.startMinute,
    effectiveEndHour,
    effectiveEndMinute,
  );

  if (hourFraction <= 0) {
    return { bookedSpots: 0, reservedSpots: 0 };
  }

  return calculateSpotsForPositions(
    p,
    spotsPerHourForSlot,
    spotPerHour,
    hourFraction,
  );
}

// Helper function for monthly view spot calculation
function getMonthlyViewSpotData(
  dateStr: string,
  dayOfWeek: number,
  operatingRanges: OperatingHourRange[],
  index: AvailabilityIndex,
): SpotData {
  const { loopsPerHour, clientPerLoop, spotPerHour, slotDuration } = index;
  const operatingHoursCount = index.totalOperatingHoursByDay[dayOfWeek] ?? 0;
  const totalSpotsPerDay = loopsPerHour * clientPerLoop * operatingHoursCount;
  let bookedSpots = 0;
  let reservedSpots = 0;

  for (const p of index.parsedSlots) {
    const dayRange = getSlotHourRangeForDate(dateStr, p);
    if (!dayRange) continue;

    const effectiveEndHour = dayRange.endHour === 24 ? 24 : dayRange.endHour;
    const effectiveEndMinute = dayRange.endMinute;
    const lastHourToIterate =
      effectiveEndMinute > 0 ? effectiveEndHour + 1 : effectiveEndHour;
    const totalAllocatedSpotsPerHour =
      Math.floor(p.loopSecondsAllocated / slotDuration) * spotPerHour;
    const spotsPerHourForSlot = totalAllocatedSpotsPerHour;

    for (let h = dayRange.startHour; h < lastHourToIterate; h++) {
      const hourSpots = processMonthlySlotForHour(
        h,
        operatingRanges,
        dayRange,
        p,
        spotsPerHourForSlot,
        spotPerHour,
      );
      bookedSpots += hourSpots.bookedSpots;
      reservedSpots += hourSpots.reservedSpots;
    }
  }

  const availableSpots = Math.max(
    0,
    totalSpotsPerDay - bookedSpots - reservedSpots,
  );
  bookedSpots = Math.min(bookedSpots, totalSpotsPerDay);
  reservedSpots = Math.min(reservedSpots, totalSpotsPerDay);

  return toIntegerSpotData({
    totalSpots: totalSpotsPerDay,
    availableSpots,
    bookedSpots,
    reservedSpots,
  });
}

// Helper function for classic inventory spot data
function getClassicInventorySpotData(
  dateStr: string,
  hour: number | undefined,
  index: AvailabilityIndex,
): SpotData {
  let isBooked = false;
  let isReserved = false;

  for (const p of index.parsedSlots) {
    if (!isDateTimeInParsedSlot(dateStr, hour, p)) continue;
    const status = checkSlotStatus(p.booking, p.slot);
    if (status.isBooked) {
      isBooked = true;
    } else if (status.isReserved) {
      isReserved = true;
    }
  }

  if (isBooked) {
    return {
      totalSpots: 1,
      availableSpots: 0,
      bookedSpots: 1,
      reservedSpots: 0,
    };
  }
  if (isReserved) {
    return {
      totalSpots: 1,
      availableSpots: 0,
      bookedSpots: 0,
      reservedSpots: 1,
    };
  }
  return { totalSpots: 1, availableSpots: 1, bookedSpots: 0, reservedSpots: 0 };
}

/**
 * Fast spot data lookup using pre-built index. When calendar date changes,
 * only date/hour-in-range checks are done; no slot parsing.
 */
export function getSpotDataFromIndex(
  date: Date,
  hour: number | undefined,
  viewMode: AvailabilityViewMode,
  index: AvailabilityIndex,
  spotPosition?: number,
): SpotData {
  const dateStr = toDateStr(date);
  const dayOfWeek = date.getDay();
  const operatingRanges = index.operatingRangesByDay[dayOfWeek] ?? [];

  const emptySpotData: SpotData = {
    totalSpots: 0,
    availableSpots: 0,
    bookedSpots: 0,
    reservedSpots: 0,
  };

  if (isDateBlackedOutFromIndex(dateStr, index.blackouts)) {
    return { ...emptySpotData, isBlocked: true };
  }
  if (!operatingRanges.length) {
    return emptySpotData;
  }
  if (
    hour !== undefined &&
    !isHourWithinOperatingTimes(hour, operatingRanges)
  ) {
    return emptySpotData;
  }

  if (index.isClassic) {
    return getClassicInventorySpotData(dateStr, hour, index);
  }

  const isTimeMode = index.bookingMode === "time";

  if (isTimeMode) {
    if (viewMode === "daily" && hour !== undefined) {
      return getDailyViewSpotDataForTimeMode(
        dateStr,
        hour,
        operatingRanges,
        index,
      );
    }
    if (viewMode === "weekly" && hour === undefined) {
      return getWeeklyViewSpotDataForTimeMode(
        dateStr,
        dayOfWeek,
        operatingRanges,
        index,
      );
    }
    if (viewMode === "monthly" && hour === undefined) {
      return getMonthlyViewSpotDataForTimeMode(
        dateStr,
        dayOfWeek,
        operatingRanges,
        index,
      );
    }
    return toIntegerSpotData(emptySpotData);
  }

  if (
    viewMode === "daily" &&
    hour !== undefined &&
    spotPosition !== undefined
  ) {
    return getDailyViewSpotData(dateStr, hour, spotPosition, index);
  }

  if (
    viewMode === "weekly" &&
    hour === undefined &&
    spotPosition !== undefined
  ) {
    return getWeeklyViewSpotData(
      dateStr,
      dayOfWeek,
      spotPosition,
      operatingRanges,
      index,
    );
  }

  if (viewMode === "monthly" && hour === undefined) {
    return getMonthlyViewSpotData(dateStr, dayOfWeek, operatingRanges, index);
  }

  return toIntegerSpotData(emptySpotData);
}

export function getBookingDetailsFromIndex(
  date: Date,
  hour: number | undefined,
  index: AvailabilityIndex,
): Booking[] {
  const dateStr = toDateStr(date);
  const seen = new Set<string>();
  const result: Booking[] = [];
  for (const p of index.parsedSlots) {
    if (!isDateTimeInParsedSlot(dateStr, hour, p)) continue;
    if (seen.has(p.booking.id)) continue;
    seen.add(p.booking.id);
    result.push(p.booking);
  }
  return result;
}

/**
 * Availability percentage for an inventory over an inclusive date range.
 *
 * Sums the per-day spot data (monthly-view granularity) across every day in
 * `[startDate, endDate]` and returns `round(sumAvailable / sumTotal * 100)`.
 * `availableSpots` already excludes booked and reserved, so reserved spots
 * count as unavailable.
 *
 * Blackout and non-operating days contribute 0 to both sums and are excluded
 * naturally. Returns `null` when there is no operating capacity in the range
 * (nothing meaningful to display).
 */
export function getAvailabilityPercentFromIndex(
  index: AvailabilityIndex,
  startDate: Date,
  endDate: Date,
): number | null {
  if (startDate > endDate) return null;

  let sumTotal = 0;
  let sumAvailable = 0;

  const cursor = new Date(
    startDate.getFullYear(),
    startDate.getMonth(),
    startDate.getDate(),
  );
  const last = new Date(
    endDate.getFullYear(),
    endDate.getMonth(),
    endDate.getDate(),
  );

  while (cursor <= last) {
    const spot = getSpotDataFromIndex(
      new Date(cursor),
      undefined,
      "monthly",
      index,
    );
    sumTotal += spot.totalSpots;
    sumAvailable += spot.availableSpots;
    cursor.setDate(cursor.getDate() + 1);
  }

  if (sumTotal <= 0) return null;
  return Math.round((sumAvailable / sumTotal) * 100);
}
