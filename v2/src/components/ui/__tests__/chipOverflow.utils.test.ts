import { describe, it, expect } from "vitest";

import { computeVisibleChipCount } from "../chipOverflow.utils";

// Feedback SI 47: visible venue-type chips must be decided by available
// width — show what fits, push the rest into a "+N" badge.
describe("computeVisibleChipCount", () => {
  const GAP = 4;

  it("shows all chips when they fit without a badge", () => {
    // 3 chips of 60px + 2 gaps = 188 <= 200
    expect(computeVisibleChipCount(200, [60, 60, 60], 40, GAP)).toBe(3);
  });

  it("reserves space for the +N badge when not all fit", () => {
    // all: 4*60 + 3*4 = 252 > 200 → badge (40) reserved,
    // chips at 64 each: 40+64+64 = 168, +64 = 232 > 200 → 2 fit
    expect(computeVisibleChipCount(200, [60, 60, 60, 60], 40, GAP)).toBe(2);
  });

  it("shows only the badge when the container is very narrow", () => {
    expect(computeVisibleChipCount(50, [60, 60], 40, GAP)).toBe(0);
  });

  it("returns 0 for no chips", () => {
    expect(computeVisibleChipCount(200, [], 40, GAP)).toBe(0);
  });

  it("never returns a negative count for zero-width containers", () => {
    expect(computeVisibleChipCount(0, [60, 60], 40, GAP)).toBe(0);
  });
});
