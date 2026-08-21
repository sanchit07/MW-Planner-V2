import {
  ParentTableColumn,
  ParentRowData,
} from "@components/common/HierarchicalTable";
import { useTranslate } from "@tolgee/react";
import { formatCurrency } from "@utils/campaign.utils";
import React, { useCallback, useMemo } from "react";

import { InlineProposedPriceCell } from "../components/InlineProposedPriceCell";
import {
  distributeProRata,
  getPendingScheduleDelta,
  PendingPriceEdit,
  PendingPriceEdits,
} from "../types";

interface UsePriceTableColumnsArgs {
  currency: string;
  /** Unsaved proposed-price edits, keyed by row key. */
  pendingEdits: PendingPriceEdits;
  /** Stages an edit - does not call the API. */
  onDraftChange: (rowKey: string, edit: PendingPriceEdit) => void;
  /** Drops a staged edit for the given row key. */
  onDiscardRow: (rowKey: string) => void;
}

/** Same key scheme InlinePriceEditContext uses for "one editor at a time". */
const getRowKey = (row: ParentRowData, isInventoryRow: boolean): string =>
  isInventoryRow ? row.id : `${row.parentId}:${row.id}`;

const getCampaignInventoryScheduleId = (
  row: ParentRowData,
): string | undefined =>
  typeof row.campaignInventoryScheduleId === "string"
    ? row.campaignInventoryScheduleId
    : undefined;

/**
 * Schedule rows post the schedule's own id alongside the composite id. The API
 * response nests it under `originalSchedule`, falling back to the row id.
 */
const getScheduleId = (row: ParentRowData): string | undefined => {
  const originalSchedule = row.originalSchedule as { id?: string } | undefined;
  return originalSchedule?.id ?? row.id;
};

/** The schedule rows nested under an inventory, if it has any. */
const getSchedules = (row: ParentRowData): ParentRowData[] =>
  Array.isArray(row.children) ? (row.children as ParentRowData[]) : [];

interface CinemaFieldsLike {
  operator?: string;
  hallName?: string;
  hallNumber?: number;
  showtimeWindows?: Array<{ label?: string }>;
}

/**
 * Secondary line for cinema rows: "operator · hall · showtime". Cinema is
 * bought by operator/hall/showtime-window; this surfaces the buy environment
 * under the inventory name. Rendered only when the row carries cinemaFields
 * (defensive — non-cinema rows have none), so it is a no-op otherwise.
 */
const renderCinemaSubline = (row: ParentRowData): React.ReactNode => {
  const cinemaFields = row.cinemaFields as CinemaFieldsLike | undefined;
  if (!cinemaFields) return null;

  const hall =
    cinemaFields.hallName ||
    (cinemaFields.hallNumber != null ? String(cinemaFields.hallNumber) : "");
  const showtime = (cinemaFields.showtimeWindows || [])
    .map((window) => window?.label)
    .filter(Boolean)
    .join(", ");
  const parts = [cinemaFields.operator, hall, showtime].filter(Boolean);
  if (parts.length === 0) return null;

  return (
    <span className="mt-0.5 block text-xs font-normal text-mw-neutral-500 dark:text-mw-neutral-400">
      {parts.join(" · ")}
    </span>
  );
};

/**
 * Column definitions for the campaign price management table.
 *
 * A single column set is shared by inventory (parent) and schedule (child) rows so
 * that both align on the same grid. Where a child row needs different content, the
 * column provides `renderChild`.
 */
export const usePriceTableColumns = ({
  currency,
  pendingEdits,
  onDraftChange,
  onDiscardRow,
}: UsePriceTableColumnsArgs): ParentTableColumn<ParentRowData>[] => {
  const { t } = useTranslate(["price"]);

  const stringToDisplay = useCallback((value: unknown): string => {
    return typeof value === "string" && value ? value : "--";
  }, []);

  const numberToDisplay = useCallback((value: unknown): string => {
    return typeof value === "number" ? value.toLocaleString() : "--";
  }, []);

  const percentageToDisplay = useCallback((value: unknown): string => {
    return typeof value === "number" ? `${value.toFixed(1)}%` : "--";
  }, []);

  const currencyToDisplay = useCallback(
    (value: unknown): string => {
      return typeof value === "number" && value > 0
        ? formatCurrency(value, currency)
        : "--";
    },
    [currency],
  );

  const formatDiscountValue = useCallback((value: unknown): string => {
    if (!value || typeof value !== "object") return "--";
    const discount = value as { valueType?: string; value?: number | string };
    if (discount.valueType === "PERCENTAGE") {
      return `${Number(discount.value).toFixed(2)}%`;
    }
    if (discount.valueType === "FIXED") {
      return String(Number(discount.value).toFixed(2));
    }
    return "--";
  }, []);

  const renderProposedPrice = useCallback(
    (value: unknown, row: ParentRowData, isInventoryRow: boolean) => {
      // campaignInventoryScheduleId is shared by an inventory and its
      // schedules, so it cannot identify a single cell - the row key does.
      const rowKey = getRowKey(row, isInventoryRow);
      const pending = pendingEdits[rowKey];
      const serverValue = Number(value) || 0;

      // An inventory's price is the sum of its schedules, so a staged schedule
      // edit has to move the parent row too. A direct edit on the inventory
      // wins - it replaces the aggregate outright rather than adjusting it.
      const scheduleDelta =
        isInventoryRow && !pending
          ? getPendingScheduleDelta(pendingEdits, row.id)
          : 0;

      return (
        <InlineProposedPriceCell
          value={pending ? pending.newPrice : serverValue + scheduleDelta}
          currency={currency}
          rowKey={rowKey}
          isInventoryRow={isInventoryRow}
          isDraft={Boolean(pending) || scheduleDelta !== 0}
          originalPrice={serverValue}
          onSave={(newPrice) => {
            onDraftChange(rowKey, {
              newPrice,
              // Always the server value, never a previous draft - re-editing a
              // staged row must not compound the delta.
              originalPrice: serverValue,
              campaignInventoryScheduleId:
                getCampaignInventoryScheduleId(row) ?? "",
              scheduleId: isInventoryRow ? undefined : getScheduleId(row),
              isInventoryRow,
              inventoryId: isInventoryRow
                ? row.id
                : ((row.parentId as string | undefined) ?? row.id),
              label:
                typeof row.inventoryName === "string" ? row.inventoryName : "",
            });

            if (!isInventoryRow) {
              // A direct schedule edit invalidates any earlier whole-inventory
              // override staged below - the parent must go back to showing
              // the sum of its schedules, not the stale override.
              const inventoryId =
                (row.parentId as string | undefined) ?? row.id;
              onDiscardRow(inventoryId);
              return;
            }

            // Editing an inventory has to cascade to its schedules - the
            // backend does not redistribute, so each schedule needs an explicit
            // new price. Split pro-rata by each schedule's share of the total.

            const schedules = getSchedules(row);
            if (schedules.length === 0) return;

            const distributed = distributeProRata(
              newPrice,
              schedules.map((schedule) => ({
                id: schedule.id,
                currentPrice: Number(schedule.proposedRate) || 0,
              })),
            );

            distributed.forEach(({ id, newPrice: schedulePrice }) => {
              const schedule = schedules.find((item) => item.id === id);
              if (!schedule) return;

              onDraftChange(`${row.id}:${schedule.id}`, {
                newPrice: schedulePrice,
                originalPrice: Number(schedule.proposedRate) || 0,
                campaignInventoryScheduleId:
                  getCampaignInventoryScheduleId(row) ?? "",
                scheduleId: getScheduleId(schedule),
                isInventoryRow: false,
                inventoryId: row.id,
                label:
                  typeof schedule.inventoryName === "string"
                    ? schedule.inventoryName
                    : "",
              });
            });
          }}
        />
      );
    },
    [currency, pendingEdits, onDraftChange, onDiscardRow],
  );

  return useMemo(
    () => [
      {
        key: "inventoryName",
        header: t("table.columns.inventory_name"),
        align: "left",
        width: "240px",
        sortKey: "name",
        render: (value, row) => (
          // Names are unbounded. Cap the width and let them wrap onto more
          // lines instead of widening the column. whitespace-normal is needed
          // because the cell itself is whitespace-nowrap.
          <span className="block max-w-[240px] whitespace-normal break-words text-sm font-medium text-mw-black dark:text-white">
            {stringToDisplay(value)}
            {renderCinemaSubline(row)}
          </span>
        ),
        renderChild: (value) => (
          <span className="block max-w-[240px] whitespace-normal break-words pl-8 text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-400">
            {stringToDisplay(value)}
          </span>
        ),
      },
      {
        key: "dateRange",
        header: t("table.columns.date_range"),
        align: "left",
        render: (value) => (
          <span className="text-sm font-normal">{stringToDisplay(value)}</span>
        ),
      },
      {
        key: "timeSlot",
        header: t("table.columns.time_slot"),
        align: "left",
        render: (value) => (
          <span className="text-sm font-normal">{stringToDisplay(value)}</span>
        ),
      },
      {
        key: "sovAdPlays",
        header: t("table.columns.sov_ad_plays"),
        align: "right",
        render: (_value, row) => (
          <div className="flex flex-col items-end">
            <span className="text-sm font-medium">
              {percentageToDisplay(row.sov)}
            </span>
            <span className="text-xs font-normal text-secondary">
              {numberToDisplay(row.adPlays)} {t("table.plays")}
            </span>
          </div>
        ),
      },
      {
        key: "mediaOwner",
        header: t("table.columns.media_owner"),
        align: "left",
        render: (value) => (
          <span className="text-sm font-normal">{stringToDisplay(value)}</span>
        ),
      },
      {
        key: "impression",
        header: t("table.columns.impression"),
        align: "left",
        sortable: true,
        render: (value) => (
          <span className="text-sm font-normal">{numberToDisplay(value)}</span>
        ),
      },
      {
        key: "bonusType",
        header: t("table.columns.bonus_type"),
        align: "left",
        render: (value) => (
          <span className="text-sm font-normal">{stringToDisplay(value)}</span>
        ),
      },
      {
        key: "discount",
        header: t("table.columns.discount"),
        align: "left",
        sortable: true,
        render: (value) => (
          <span className="text-sm font-normal">
            {percentageToDisplay(value)}
          </span>
        ),
        renderChild: (value) => (
          <span className="text-sm font-normal">
            {formatDiscountValue(value)}
          </span>
        ),
      },
      {
        key: "monthlyRateCard",
        header: t("table.columns.monthly_rate_card"),
        align: "right",
        sortable: true,
        render: (value) => (
          <span className="text-sm font-normal">
            {currencyToDisplay(value)}
          </span>
        ),
      },
      {
        key: "weeklyRateCard",
        header: t("table.columns.weekly_rate_card"),
        align: "right",
        sortable: true,
        render: (value) => (
          <span className="text-sm font-normal">
            {currencyToDisplay(value)}
          </span>
        ),
      },
      {
        key: "dailyRate",
        header: t("table.columns.daily_rate"),
        align: "right",
        sortable: true,
        render: (value) => (
          <span className="text-sm font-normal">
            {currencyToDisplay(value)}
          </span>
        ),
      },
      {
        key: "cpmRate",
        header: t("table.columns.cpm_rate"),
        align: "right",
        sortable: true,
        render: (value) => (
          <span className="text-sm font-normal">
            {currencyToDisplay(value)}
          </span>
        ),
      },
      {
        key: "cpsRate",
        header: t("table.columns.cps_rate"),
        align: "right",
        sortable: true,
        render: (value) => (
          <span className="text-sm font-normal">
            {currencyToDisplay(value)}
          </span>
        ),
      },
      {
        key: "reach",
        header: t("table.columns.reach"),
        align: "left",
        sortable: true,
        render: (value) => (
          <span className="text-sm font-normal">{numberToDisplay(value)}</span>
        ),
      },
      {
        key: "currentRate",
        header: t("table.columns.initial_price"),
        align: "right",
        render: (value) => (
          <span className="text-sm font-normal">
            {currencyToDisplay(value)}
          </span>
        ),
      },
      {
        key: "proposedRate",
        header: t("table.columns.proposed_price"),
        align: "right",
        render: (value, row) => renderProposedPrice(value, row, true),
        renderChild: (value, row) => renderProposedPrice(value, row, false),
      },
    ],
    [
      t,
      stringToDisplay,
      numberToDisplay,
      percentageToDisplay,
      currencyToDisplay,
      formatDiscountValue,
      renderProposedPrice,
    ],
  );
};
