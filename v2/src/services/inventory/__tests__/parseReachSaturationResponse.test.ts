import { describe, expect, it } from "vitest";

import { parseReachSaturationResponse } from "../inventorySlice";

describe("parseReachSaturationResponse", () => {
  it("parses a raw string body containing bare NaN tokens", () => {
    // axios (silentJSONParsing) hands us the unparsed body when it is invalid
    // JSON — here an inventory with no forecast reports NaN.
    const raw = `[
      {
        "inventories": [
          {
            "referenceId": "JPN-JEK-D-00000-01029",
            "reach": 13497.0,
            "cpmBudget": 1767.0,
            "saturatedReach": [0.0, 25.5, 44.5],
            "saturatedReachDate": "2026-07-19"
          },
          {
            "referenceId": "JPN-JEK-C-00000-04923",
            "reach": 0.0,
            "cpmBudget": 0.0,
            "saturatedReach": [NaN, NaN, NaN],
            "saturatedReachDate": "2026-07-08"
          }
        ],
        "overallInventories": {
          "overallReach": [0.0, 14.52, 25.73],
          "overallsaturatedReachDate": "2026-07-08"
        }
      }
    ]`;

    const result = parseReachSaturationResponse(raw);

    // NaN tokens become null; the overall curve is intact.
    expect(result[0].inventories[1].saturatedReach).toEqual([null, null, null]);
    expect(result[0].overallInventories.overallReach).toEqual([
      0.0, 14.52, 25.73,
    ]);
  });

  it("also sanitizes Infinity and -Infinity tokens", () => {
    const raw = `{ "overallInventories": { "overallReach": [Infinity, -Infinity, 1.0] } }`;

    const result = parseReachSaturationResponse(raw) as unknown as {
      overallInventories: { overallReach: (number | null)[] };
    };

    expect(result.overallInventories.overallReach).toEqual([null, null, 1.0]);
  });

  it("passes through an already-parsed (finite) payload untouched", () => {
    const parsed = [
      {
        inventories: [],
        overallInventories: {
          overallReach: [0, 50, 100],
          overallsaturatedReachDate: "2026-07-08",
        },
      },
    ];

    const result = parseReachSaturationResponse(parsed);

    expect(result).toBe(parsed);
  });
});
