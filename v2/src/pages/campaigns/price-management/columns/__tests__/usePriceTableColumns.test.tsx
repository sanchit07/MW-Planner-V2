import "@testing-library/jest-dom";
import type {
  CellRenderContext,
  ParentRowData,
} from "@components/common/HierarchicalTable";
import { render, renderHook, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import type { PendingPriceEdits } from "../../types";
import { usePriceTableColumns } from "../usePriceTableColumns";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("../../components/InlineProposedPriceCell", () => ({
  InlineProposedPriceCell: (props: Record<string, unknown>) => (
    <button
      type="button"
      data-testid="inline-proposed-price"
      data-value={String(props.value)}
      data-currency={String(props.currency)}
      data-row-key={String(props.rowKey)}
      data-is-inventory-row={String(props.isInventoryRow)}
      data-is-draft={String(props.isDraft)}
      onClick={() => (props.onSave as (n: number) => void)?.(999)}
    />
  ),
}));

const cellContext = {} as CellRenderContext;

const parentRow: ParentRowData = {
  id: "inv-1",
  inventoryName: "Digital Billboard Alpha",
  dateRange: "Jul 25 - Jul 31, 2022",
  timeSlot: "table.multiple",
  sov: 15,
  adPlays: 11340,
  currentRate: 2200,
  proposedRate: 2200,
  campaignInventoryScheduleId: "cis-1",
};

const childRow: ParentRowData = {
  id: "sch-1",
  parentId: "inv-1",
  inventoryName: "Schedule #4",
  dateRange: "Aug 01 - Aug 07, 2022",
  timeSlot: "9 AM - 5 PM",
  sov: 15,
  adPlays: 3600,
  currentRate: 950,
  proposedRate: 760,
  campaignInventoryScheduleId: "cis-1",
  originalSchedule: { id: "original-sch-1" },
  discount: { valueType: "PERCENTAGE", value: 20 },
};

const onDraftChange = vi.fn();
const onDiscardRow = vi.fn();

const getColumns = (pendingEdits: PendingPriceEdits = {}) =>
  renderHook(() =>
    usePriceTableColumns({
      currency: "USD",
      pendingEdits,
      onDraftChange,
      onDiscardRow,
    }),
  ).result.current;

const columnByKey = (key: string, pendingEdits: PendingPriceEdits = {}) => {
  const column = getColumns(pendingEdits).find((col) => col.key === key);
  if (!column) throw new Error(`Column ${key} not found`);
  return column;
};

describe("usePriceTableColumns", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("exposes the redesigned visible columns in order", () => {
    const keys = getColumns().map((column) => column.key);
    const visibleOrder = [
      "inventoryName",
      "dateRange",
      "timeSlot",
      "sovAdPlays",
      "currentRate",
      "proposedRate",
    ];

    expect(keys.filter((key) => visibleOrder.includes(key))).toEqual(
      visibleOrder,
    );
  });

  it("no longer defines serial number, flight date or standalone ad plays columns", () => {
    const keys = getColumns().map((column) => column.key);

    expect(keys).not.toContain("srNo");
    expect(keys).not.toContain("flightDate");
    expect(keys).not.toContain("adPlays");
  });

  it("does not make the visible columns sortable", () => {
    [
      "inventoryName",
      "dateRange",
      "timeSlot",
      "sovAdPlays",
      "currentRate",
      "proposedRate",
    ].forEach((key) => {
      expect(columnByKey(key).sortable).toBeUndefined();
    });
  });

  it("right-aligns the numeric columns", () => {
    expect(columnByKey("sovAdPlays").align).toBe("right");
    expect(columnByKey("currentRate").align).toBe("right");
    expect(columnByKey("proposedRate").align).toBe("right");
  });

  it("renders the inventory name with no acceptance badge", () => {
    const column = columnByKey("inventoryName");
    render(
      <>{column.render?.(parentRow.inventoryName, parentRow, cellContext)}</>,
    );

    expect(screen.getByText("Digital Billboard Alpha")).toBeInTheDocument();
    // The Accept flow is gone, so the "Not Accepted" pill went with it
    expect(screen.queryByText("table.not_accepted")).not.toBeInTheDocument();
  });

  it("wraps long names instead of widening the column", () => {
    const column = columnByKey("inventoryName");
    const longName = "[2026 Q2] A very long inventory name that will not fit";
    render(<>{column.render?.(longName, parentRow, cellContext)}</>);

    const name = screen.getByText(longName);
    // The cell is whitespace-nowrap, so the span must opt back into wrapping
    expect(name).toHaveClass("whitespace-normal");
    expect(name).toHaveClass("break-words");
    expect(name).toHaveClass("max-w-[240px]");
    expect(name).not.toHaveClass("truncate");
  });

  it("wraps schedule names too", () => {
    const column = columnByKey("inventoryName");
    render(
      <>
        {column.renderChild?.(childRow.inventoryName, childRow, cellContext)}
      </>,
    );

    const name = screen.getByText("Schedule #4");
    expect(name).toHaveClass("whitespace-normal");
    expect(name).toHaveClass("break-words");
  });

  it("renders a cinema secondary line (operator · hall · showtime) when cinemaFields is present", () => {
    const column = columnByKey("inventoryName");
    const cinemaRow: ParentRowData = {
      ...parentRow,
      inventoryName: "GV Plaza Hall 3",
      cinemaFields: {
        operator: "GV Cinemas",
        hallName: "Hall 3",
        showtimeWindows: [{ label: "Matinee" }, { label: "Evening" }],
      },
    };
    render(
      <>{column.render?.(cinemaRow.inventoryName, cinemaRow, cellContext)}</>,
    );

    expect(screen.getByText("GV Plaza Hall 3")).toBeInTheDocument();
    expect(
      screen.getByText("GV Cinemas · Hall 3 · Matinee, Evening"),
    ).toBeInTheDocument();
  });

  it("does not render a cinema secondary line for non-cinema rows", () => {
    const column = columnByKey("inventoryName");
    render(
      <>{column.render?.(parentRow.inventoryName, parentRow, cellContext)}</>,
    );

    expect(
      screen.queryByText(/·/, { selector: "span.text-xs" }),
    ).not.toBeInTheDocument();
  });

  it("stacks SOV over ad plays in a single column", () => {
    const column = columnByKey("sovAdPlays");
    render(<>{column.render?.(undefined, parentRow, cellContext)}</>);

    expect(screen.getByText("15.0%")).toBeInTheDocument();
    expect(screen.getByText(/11,340/)).toBeInTheDocument();
  });

  it("renders the inline price editor keyed by the inventory id, showing the server value", () => {
    const column = columnByKey("proposedRate");
    render(
      <>{column.render?.(parentRow.proposedRate, parentRow, cellContext)}</>,
    );

    const cell = screen.getByTestId("inline-proposed-price");
    expect(cell).toHaveAttribute("data-value", "2200");
    expect(cell).toHaveAttribute("data-currency", "USD");
    expect(cell).toHaveAttribute("data-row-key", "inv-1");
    expect(cell).toHaveAttribute("data-is-inventory-row", "true");
    expect(cell).toHaveAttribute("data-is-draft", "false");
  });

  it("uses the parentId:id row key for a schedule row", () => {
    const column = columnByKey("proposedRate");
    render(
      <>{column.renderChild?.(childRow.proposedRate, childRow, cellContext)}</>,
    );

    const cell = screen.getByTestId("inline-proposed-price");
    expect(cell).toHaveAttribute("data-value", "760");
    expect(cell).toHaveAttribute("data-row-key", "inv-1:sch-1");
    expect(cell).toHaveAttribute("data-is-inventory-row", "false");
  });

  it("shows the pending draft value and marks it as unsaved when one exists", () => {
    const pendingEdits: PendingPriceEdits = {
      "inv-1": {
        newPrice: 2500,
        originalPrice: 2200,
        campaignInventoryScheduleId: "cis-1",
        isInventoryRow: true,
        inventoryId: "inv-1",
        label: "Digital Billboard Alpha",
      },
    };
    const column = columnByKey("proposedRate", pendingEdits);
    render(
      <>{column.render?.(parentRow.proposedRate, parentRow, cellContext)}</>,
    );

    const cell = screen.getByTestId("inline-proposed-price");
    expect(cell).toHaveAttribute("data-value", "2500");
    expect(cell).toHaveAttribute("data-is-draft", "true");
  });

  it("moves the inventory row when one of its schedules has a staged edit", () => {
    // An inventory's price is the sum of its schedules, so a schedule edit of
    // -140 has to show on the parent too: 2200 -> 2060.
    const pendingEdits: PendingPriceEdits = {
      "inv-1:sch-1": {
        newPrice: 760,
        originalPrice: 900,
        campaignInventoryScheduleId: "cis-1",
        scheduleId: "original-sch-1",
        isInventoryRow: false,
        inventoryId: "inv-1",
        label: "Schedule #4",
      },
    };
    const column = columnByKey("proposedRate", pendingEdits);
    render(
      <>{column.render?.(parentRow.proposedRate, parentRow, cellContext)}</>,
    );

    const cell = screen.getByTestId("inline-proposed-price");
    expect(cell).toHaveAttribute("data-value", "2060");
    expect(cell).toHaveAttribute("data-is-draft", "true");
  });

  it("lets a direct inventory edit win over its schedules' staged edits", () => {
    const pendingEdits: PendingPriceEdits = {
      "inv-1": {
        newPrice: 3000,
        originalPrice: 2200,
        campaignInventoryScheduleId: "cis-1",
        isInventoryRow: true,
        inventoryId: "inv-1",
        label: "Digital Billboard Alpha",
      },
      "inv-1:sch-1": {
        newPrice: 760,
        originalPrice: 900,
        campaignInventoryScheduleId: "cis-1",
        scheduleId: "original-sch-1",
        isInventoryRow: false,
        inventoryId: "inv-1",
        label: "Schedule #4",
      },
    };
    const column = columnByKey("proposedRate", pendingEdits);
    render(
      <>{column.render?.(parentRow.proposedRate, parentRow, cellContext)}</>,
    );

    // 3000 exactly - not 3000 - 140, and not 2200 - 140
    expect(screen.getByTestId("inline-proposed-price")).toHaveAttribute(
      "data-value",
      "3000",
    );
  });

  it("does not let another inventory's schedule edit move this row", () => {
    const pendingEdits: PendingPriceEdits = {
      "inv-9:sch-1": {
        newPrice: 100,
        originalPrice: 500,
        campaignInventoryScheduleId: "cis-9",
        scheduleId: "sch-1",
        isInventoryRow: false,
        inventoryId: "inv-9",
        label: "Schedule #1",
      },
    };
    const column = columnByKey("proposedRate", pendingEdits);
    render(
      <>{column.render?.(parentRow.proposedRate, parentRow, cellContext)}</>,
    );

    const cell = screen.getByTestId("inline-proposed-price");
    expect(cell).toHaveAttribute("data-value", "2200");
    expect(cell).toHaveAttribute("data-is-draft", "false");
  });

  it("stages an edit via onDraftChange instead of calling any API", () => {
    const column = columnByKey("proposedRate");
    render(
      <>{column.render?.(parentRow.proposedRate, parentRow, cellContext)}</>,
    );

    screen.getByTestId("inline-proposed-price").click();

    expect(onDraftChange).toHaveBeenCalledWith(
      "inv-1",
      expect.objectContaining({
        newPrice: 999,
        campaignInventoryScheduleId: "cis-1",
        scheduleId: undefined,
        isInventoryRow: true,
        label: "Digital Billboard Alpha",
      }),
    );
  });

  it("cascades an inventory edit to its schedules, split pro-rata", () => {
    const inventoryWithSchedules: ParentRowData = {
      ...parentRow,
      proposedRate: 1800,
      children: [
        {
          id: "s1",
          parentId: "inv-1",
          inventoryName: "Schedule #1",
          proposedRate: 1400,
          originalSchedule: { id: "orig-s1" },
        },
        {
          id: "s2",
          parentId: "inv-1",
          inventoryName: "Schedule #2",
          proposedRate: 300,
          originalSchedule: { id: "orig-s2" },
        },
        {
          id: "s3",
          parentId: "inv-1",
          inventoryName: "Schedule #3",
          proposedRate: 100,
          originalSchedule: { id: "orig-s3" },
        },
      ],
    };

    const column = columnByKey("proposedRate");
    render(
      <>
        {column.render?.(
          inventoryWithSchedules.proposedRate,
          inventoryWithSchedules,
          cellContext,
        )}
      </>,
    );

    // The mock cell reports a save of 999
    screen.getByTestId("inline-proposed-price").click();

    // Parent, then one edit per schedule
    expect(onDraftChange).toHaveBeenCalledTimes(4);

    expect(onDraftChange).toHaveBeenCalledWith(
      "inv-1",
      expect.objectContaining({ newPrice: 999, isInventoryRow: true }),
    );

    // 999 split by 1400/300/100 shares of 1800
    const staged = Object.fromEntries(
      onDraftChange.mock.calls
        .filter(([key]) => key !== "inv-1")
        .map(([key, edit]) => [key, edit]),
    );

    expect(staged["inv-1:s1"]).toEqual(
      expect.objectContaining({
        newPrice: 777,
        originalPrice: 1400,
        scheduleId: "orig-s1",
        isInventoryRow: false,
        inventoryId: "inv-1",
      }),
    );
    expect(staged["inv-1:s2"]).toEqual(
      expect.objectContaining({ newPrice: 166.5, originalPrice: 300 }),
    );
    expect(staged["inv-1:s3"]).toEqual(
      expect.objectContaining({ newPrice: 55.5, originalPrice: 100 }),
    );

    // The parts add back up to what was typed
    const total =
      staged["inv-1:s1"].newPrice +
      staged["inv-1:s2"].newPrice +
      staged["inv-1:s3"].newPrice;
    expect(Math.round(total * 100) / 100).toBe(999);
  });

  it("does not cascade when the inventory has no schedules", () => {
    const column = columnByKey("proposedRate");
    render(
      <>{column.render?.(parentRow.proposedRate, parentRow, cellContext)}</>,
    );

    screen.getByTestId("inline-proposed-price").click();

    expect(onDraftChange).toHaveBeenCalledTimes(1);
  });

  it("stages a schedule edit with its schedule id", () => {
    const column = columnByKey("proposedRate");
    render(
      <>{column.renderChild?.(childRow.proposedRate, childRow, cellContext)}</>,
    );

    screen.getByTestId("inline-proposed-price").click();

    expect(onDraftChange).toHaveBeenCalledWith(
      "inv-1:sch-1",
      expect.objectContaining({
        newPrice: 999,
        campaignInventoryScheduleId: "cis-1",
        scheduleId: "original-sch-1",
        isInventoryRow: false,
      }),
    );
  });

  it("discards any staged whole-inventory override when a schedule is edited directly", () => {
    // Editing the inventory first would leave a stale override on "inv-1" -
    // editing one of its schedules afterwards must drop it so the parent
    // goes back to showing the sum of its schedules.
    const column = columnByKey("proposedRate");
    render(
      <>{column.renderChild?.(childRow.proposedRate, childRow, cellContext)}</>,
    );

    screen.getByTestId("inline-proposed-price").click();

    expect(onDiscardRow).toHaveBeenCalledWith("inv-1");
  });

  it("formats the schedule discount object on child rows", () => {
    const column = columnByKey("discount");
    render(
      <>{column.renderChild?.(childRow.discount, childRow, cellContext)}</>,
    );

    expect(screen.getByText("20.00%")).toBeInTheDocument();
  });

  it("renders a placeholder for missing values", () => {
    const column = columnByKey("dateRange");
    render(<>{column.render?.(undefined, parentRow, cellContext)}</>);

    expect(screen.getByText("--")).toBeInTheDocument();
  });
});
