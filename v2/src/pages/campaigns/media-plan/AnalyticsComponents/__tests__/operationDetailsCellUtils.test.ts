import { describe, it, expect } from "vitest";

import {
  isPinnedBottomRow,
  getPinnedCellDisplay,
  getPinnedNumberDisplay,
} from "../operationDetailsCellUtils";

describe("operationDetailsCellUtils", () => {
  const createParams = <T>(overrides: {
    node?: { rowPinned?: string };
    value?: unknown;
    data?: T;
  }) =>
    ({
      node: overrides.node ?? {},
      value: overrides.value,
      data: overrides.data,
    }) as Parameters<typeof isPinnedBottomRow>[0];

  describe("isPinnedBottomRow", () => {
    it("returns true when node.rowPinned is bottom", () => {
      const params = createParams({ node: { rowPinned: "bottom" } });
      expect(isPinnedBottomRow(params)).toBe(true);
    });

    it("returns false when node.rowPinned is top", () => {
      const params = createParams({ node: { rowPinned: "top" } });
      expect(isPinnedBottomRow(params)).toBe(false);
    });

    it("returns false when node.rowPinned is undefined", () => {
      const params = createParams({});
      expect(isPinnedBottomRow(params)).toBe(false);
    });

    it("returns false when node is missing rowPinned property", () => {
      const params = createParams({ node: {} as { rowPinned?: string } });
      expect(isPinnedBottomRow(params)).toBe(false);
    });
  });

  describe("getPinnedCellDisplay", () => {
    it("returns value as string when not pinned", () => {
      const params = createParams({ value: "2025-01-01" });
      expect(getPinnedCellDisplay(params, {})).toBe("2025-01-01");
    });

    it("returns empty string when not pinned and value is null", () => {
      const params = createParams({ value: null });
      expect(getPinnedCellDisplay(params, {})).toBe("");
    });

    it("returns empty string when not pinned and value is undefined", () => {
      const params = createParams({ value: undefined });
      expect(getPinnedCellDisplay(params, {})).toBe("");
    });

    it("returns pinnedLabel when pinned and pinnedLabel provided", () => {
      const params = createParams({ node: { rowPinned: "bottom" } });
      expect(
        getPinnedCellDisplay(params, { pinnedLabel: "Total Operation" }),
      ).toBe("Total Operation");
    });

    it("returns empty string when pinned and pinnedLabel is empty string", () => {
      const params = createParams({ node: { rowPinned: "bottom" } });
      expect(getPinnedCellDisplay(params, { pinnedLabel: "" })).toBe("");
    });

    it("returns data value for dataKey when pinned and data present", () => {
      const params = createParams({
        node: { rowPinned: "bottom" },
        data: { startDate: "NBC Prime time" },
      });
      expect(getPinnedCellDisplay(params, { dataKey: "startDate" })).toBe(
        "NBC Prime time",
      );
    });

    it("returns empty string when pinned with dataKey but data value missing", () => {
      const params = createParams({
        node: { rowPinned: "bottom" },
        data: {},
      });
      expect(getPinnedCellDisplay(params, { dataKey: "startDate" })).toBe("");
    });

    it("applies format when pinned and dataKey value present", () => {
      const params = createParams({
        node: { rowPinned: "bottom" },
        data: { count: 42 },
      });
      expect(
        getPinnedCellDisplay(params, {
          dataKey: "count",
          format: (v) => `#${v}`,
        }),
      ).toBe("#42");
    });
  });

  describe("getPinnedNumberDisplay", () => {
    it("returns value as string when not pinned and value is number", () => {
      const params = createParams({ value: 14 });
      expect(getPinnedNumberDisplay(params, "operationDays")).toBe("14");
    });

    it("applies format when not pinned and value is number", () => {
      const params = createParams({ value: 1680 });
      expect(
        getPinnedNumberDisplay(params, "totalSpots", (n) => n.toLocaleString()),
      ).toBe("1,680");
    });

    it("returns empty string when not pinned and value is null", () => {
      const params = createParams({ value: null });
      expect(getPinnedNumberDisplay(params, "operationDays")).toBe("");
    });

    it("returns data value when pinned and data has number at dataKey", () => {
      const params = createParams({
        node: { rowPinned: "bottom" },
        data: { operationDays: 14 },
      });
      expect(getPinnedNumberDisplay(params, "operationDays")).toBe("14");
    });

    it("applies format when pinned and data has number at dataKey", () => {
      const params = createParams({
        node: { rowPinned: "bottom" },
        data: { totalSpots: 1680 },
      });
      expect(
        getPinnedNumberDisplay(params, "totalSpots", (n) => n.toLocaleString()),
      ).toBe("1,680");
    });

    it("returns empty string when pinned and data has no number at dataKey", () => {
      const params = createParams({
        node: { rowPinned: "bottom" },
        data: {},
      });
      expect(getPinnedNumberDisplay(params, "operationDays")).toBe("");
    });
  });
});
