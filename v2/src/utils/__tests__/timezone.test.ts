import { afterEach, describe, expect, it, vi } from "vitest";

import { getLocalTimezone } from "../timezone";

describe("getLocalTimezone", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("returns a non-empty string", () => {
    const tz = getLocalTimezone();

    expect(typeof tz).toBe("string");
    expect(tz.length).toBeGreaterThan(0);
  });

  it("returns a valid IANA timezone string in normal environments", () => {
    const tz = getLocalTimezone();

    // IANA timezone strings contain at least one character; many contain a slash
    // (e.g. "America/New_York", "Asia/Tokyo") but "UTC" is also valid
    expect(tz).toMatch(/^[A-Za-z_/+-]+$/);
  });

  it('returns "UTC" as fallback when Intl.DateTimeFormat throws', () => {
    vi.spyOn(Intl, "DateTimeFormat").mockImplementation(() => {
      throw new Error("Intl not available");
    });

    const tz = getLocalTimezone();

    expect(tz).toBe("UTC");
  });

  it('returns "UTC" as fallback when resolvedOptions throws', () => {
    vi.spyOn(Intl, "DateTimeFormat").mockImplementation(
      () =>
        ({
          resolvedOptions: () => {
            throw new Error("resolvedOptions failed");
          },
        }) as unknown as Intl.DateTimeFormat,
    );

    const tz = getLocalTimezone();

    expect(tz).toBe("UTC");
  });
});
