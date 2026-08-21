import { describe, it, expect } from "vitest";

import {
  getStatusConfig,
  formatDateForTooltip,
  formatTimeForTooltip,
  parseAvailabilityResponse,
  isAvailabilitySyncStale,
  getAvailabilitySyncWarning,
  AVAILABILITY_STALE_THRESHOLD_MS,
} from "../inventoryAvailabilityUI.utils";

describe("getStatusConfig", () => {
  it("returns available config without t function", () => {
    const result = getStatusConfig("available", 30);
    expect(result.label).toBe("Available");
    expect(result.bgColor).toBe("white");
    expect(result.showProgress).toBe(true);
  });

  it("returns available config using t function", () => {
    const t = (key: string) => `translated:${key}`;
    const result = getStatusConfig("available", 50, true, t);
    expect(result.label).toBe("translated:calendar.available.label");
  });

  it("returns booked config", () => {
    const result = getStatusConfig("booked", 80);
    expect(result.label).toBe("Booked");
    expect(result.bgColor).toBe("bg-mw-error-50");
    expect(result.textColor).toBe("text-mw-error-500");
    expect(result.showProgress).toBe(true);
  });

  it("returns reserved config with showProgress based on bookedPercentage", () => {
    const notFull = getStatusConfig("reserved", 50);
    expect(notFull.label).toBe("Reserved");
    expect(notFull.showProgress).toBe(true);

    const full = getStatusConfig("reserved", 100);
    expect(full.showProgress).toBe(false);
  });

  it("returns fully_booked config with showProgress false", () => {
    const result = getStatusConfig("fully_booked", 100);
    expect(result.label).toBe("Fully Booked");
    expect(result.progressColor).toBe("error");
    expect(result.showProgress).toBe(false);
  });

  it("defaults to available config for unknown status", () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const result = getStatusConfig("unknown" as any, 20);
    expect(result.label).toBe("Available");
    expect(result.bgColor).toBe("bg-white");
  });

  it("respects showProgress=false override", () => {
    const result = getStatusConfig("available", 30, false);
    expect(result.showProgress).toBe(false);
  });
});

describe("formatDateForTooltip", () => {
  it("formats date in DD MMMM YYYY format", () => {
    const date = new Date(2026, 0, 5); // Jan 5 2026
    const result = formatDateForTooltip(date);
    expect(result).toContain("2026");
    expect(result).toContain("January");
    expect(result).toContain("05");
  });
});

describe("formatTimeForTooltip", () => {
  it("formats single-digit hours with leading zero", () => {
    expect(formatTimeForTooltip(9)).toBe("09:00");
  });

  it("formats double-digit hours correctly", () => {
    expect(formatTimeForTooltip(14)).toBe("14:00");
  });

  it("formats midnight as 00:00", () => {
    expect(formatTimeForTooltip(0)).toBe("00:00");
  });
});

describe("parseAvailabilityResponse", () => {
  it("returns null for non-object input", () => {
    expect(parseAvailabilityResponse(null)).toBeNull();
    expect(parseAvailabilityResponse("string")).toBeNull();
    expect(parseAvailabilityResponse(42)).toBeNull();
  });

  it("extracts inventories from success=true response with data.inventories", () => {
    const response = {
      success: true,
      data: { inventories: { inv1: { booked: 50 } } },
    };
    const result = parseAvailabilityResponse(response);
    expect(result).toEqual({ inv1: { booked: 50 } });
  });

  it("falls back to top-level inventories when success is missing", () => {
    const response = { inventories: { inv2: { booked: 80 } } };
    const result = parseAvailabilityResponse(response);
    expect(result).toEqual({ inv2: { booked: 80 } });
  });

  it("returns null when data.inventories is missing", () => {
    const response = { success: true, data: {} };
    expect(parseAvailabilityResponse(response)).toBeNull();
  });

  it("returns null when no inventories key at top level", () => {
    const response = { someOtherKey: "value" };
    expect(parseAvailabilityResponse(response)).toBeNull();
  });
});

describe("availability sync staleness", () => {
  const now = Date.parse("2026-08-14T12:00:00Z");
  const freshAt = new Date(now - 60 * 60 * 1000).toISOString(); // 1h ago
  const staleAt = new Date(
    now - AVAILABILITY_STALE_THRESHOLD_MS - 60_000,
  ).toISOString(); // just past threshold

  it("isAvailabilitySyncStale: false for fresh, missing, or unparseable timestamps", () => {
    expect(
      isAvailabilitySyncStale({ lastSyncedAt: freshAt, status: "SUCCESS" }, now),
    ).toBe(false);
    expect(
      isAvailabilitySyncStale({ lastSyncedAt: null, status: "SUCCESS" }, now),
    ).toBe(false);
    expect(isAvailabilitySyncStale(null, now)).toBe(false);
    expect(
      isAvailabilitySyncStale(
        { lastSyncedAt: "not-a-date", status: "SUCCESS" },
        now,
      ),
    ).toBe(false);
  });

  it("isAvailabilitySyncStale: true when older than the threshold", () => {
    expect(
      isAvailabilitySyncStale({ lastSyncedAt: staleAt, status: "SUCCESS" }, now),
    ).toBe(true);
  });

  it("getAvailabilitySyncWarning: null when fresh and successful", () => {
    expect(
      getAvailabilitySyncWarning(
        { lastSyncedAt: freshAt, status: "SUCCESS" },
        now,
      ),
    ).toBeNull();
    expect(getAvailabilitySyncWarning(null, now)).toBeNull();
  });

  it("getAvailabilitySyncWarning: 'failed' takes precedence over staleness", () => {
    expect(
      getAvailabilitySyncWarning(
        { lastSyncedAt: staleAt, status: "FAILED", error: "boom" },
        now,
      ),
    ).toBe("failed");
    expect(
      getAvailabilitySyncWarning(
        { lastSyncedAt: freshAt, status: "FAILED" },
        now,
      ),
    ).toBe("failed");
  });

  it("getAvailabilitySyncWarning: 'stale' when data is old but sync succeeded", () => {
    expect(
      getAvailabilitySyncWarning(
        { lastSyncedAt: staleAt, status: "SUCCESS" },
        now,
      ),
    ).toBe("stale");
  });
});
