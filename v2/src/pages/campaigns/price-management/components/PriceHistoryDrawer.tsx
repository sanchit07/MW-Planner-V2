import {
  HierarchicalTable,
  ParentRowData,
  ParentTableColumn,
} from "@components/common/HierarchicalTable";
import { Button } from "@components/ui/Button";
import { Card } from "@components/ui/card";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { Spinner } from "@components/ui/Spinner";
import { TablePagination } from "@components/ui/TablePagination";
import {
  useLazyGetCampaignSchedulePricesQuery,
  useLazyGetPriceSummaryQuery,
  useLazyGetPriceHistoryQuery,
} from "@services/inventory/inventorySlice";
import { useTranslate } from "@tolgee/react";
import { formatCurrency } from "@utils/campaign.utils";
import { formatDisplayDate } from "@utils/dateUtils";
import { clsx } from "clsx";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { PriceHistoryItem } from "src/types/inventory.types";

interface PriceHistoryDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  campaignId?: string;
  currency?: string;
}

/** Colour per history action, matching the price status colours. */
const ACTION_COLORS: Record<string, string> = {
  RATE_CARD: "bg-mw-neutral-400",
  PROPOSED: "bg-mw-primary-500",
  COUNTERED: "bg-mw-warning-500",
  ACCEPTED: "bg-mw-success-500",
  DECLINED: "bg-mw-error-500",
};

/** API action -> i18n key under drawers.add_proposal_price.actions */
const ACTION_KEYS: Record<string, string> = {
  RATE_CARD: "rate_card",
  PROPOSED: "proposed",
  COUNTERED: "counter",
  ACCEPTED: "accepted",
};

/** Kept in step with the CSS transition so the row unmounts after it ends. */
const EXPAND_ANIMATION_MS = 300;

const INVENTORY_PAGE_SIZE = 10;
const HISTORY_PAGE_SIZE = 10;

interface HistoryState {
  items: PriceHistoryItem[];
  page: number;
  totalElements: number;
  totalPages: number;
  isLoading: boolean;
}

/**
 * Read-only price history for every inventory in the campaign.
 *
 * History is only fetched when a row is expanded - the endpoint is per
 * inventory (keyed on campaignInventoryScheduleId), so loading all of them up
 * front would be one request per inventory.
 */
export const PriceHistoryDrawer: React.FC<PriceHistoryDrawerProps> = ({
  isOpen,
  onClose,
  campaignId,
  currency = "USD",
}) => {
  const { t } = useTranslate(["price"]);
  const { t: tCommon } = useTranslate(["common"]);

  const [fetchSchedulePrices, { data: priceData, isFetching }] =
    useLazyGetCampaignSchedulePricesQuery();
  const [fetchPriceSummary, { data: summaryResponse }] =
    useLazyGetPriceSummaryQuery();
  const [fetchPriceHistory] = useLazyGetPriceHistoryQuery();

  const [inventoryPage, setInventoryPage] = useState(0);
  // Accordion: at most one inventory open. openRow is what the table renders;
  // isContentVisible drives the transition, and lags behind on close so the
  // row stays mounted long enough to animate out.
  const [openRow, setOpenRow] = useState<string | null>(null);
  const [isContentVisible, setIsContentVisible] = useState(false);
  const closeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // History per inventory row id, so collapsing and reopening does not refetch.
  const [historyByRow, setHistoryByRow] = useState<
    Record<string, HistoryState>
  >({});

  const expandedItems = useMemo(
    () => (openRow ? new Set([openRow]) : new Set<string>()),
    [openRow],
  );

  // Two frames so the browser paints the collapsed state before transitioning.
  useEffect(() => {
    if (!openRow) return;
    let raf1 = 0;
    let raf2 = 0;
    raf1 = requestAnimationFrame(() => {
      raf2 = requestAnimationFrame(() => setIsContentVisible(true));
    });
    return () => {
      cancelAnimationFrame(raf1);
      cancelAnimationFrame(raf2);
    };
  }, [openRow]);

  useEffect(
    () => () => {
      if (closeTimerRef.current) clearTimeout(closeTimerRef.current);
    },
    [],
  );

  useEffect(() => {
    if (!isOpen || !campaignId) return;
    fetchSchedulePrices({
      campaignId,
      params: { page: inventoryPage, size: INVENTORY_PAGE_SIZE },
    });
  }, [isOpen, campaignId, inventoryPage, fetchSchedulePrices]);

  useEffect(() => {
    if (!isOpen || !campaignId) return;
    fetchPriceSummary({ campaignId });
  }, [isOpen, campaignId, fetchPriceSummary]);

  // Start clean each time the drawer opens - prices may have changed since.
  useEffect(() => {
    if (isOpen) return;
    setOpenRow(null);
    setIsContentVisible(false);
    setHistoryByRow({});
    setInventoryPage(0);
  }, [isOpen]);

  const loadHistory = useCallback(
    async (rowId: string, scheduleId: string, page: number) => {
      setHistoryByRow((prev) => ({
        ...prev,
        [rowId]: {
          items: prev[rowId]?.items ?? [],
          page,
          totalElements: prev[rowId]?.totalElements ?? 0,
          totalPages: prev[rowId]?.totalPages ?? 0,
          isLoading: true,
        },
      }));

      try {
        const response = await fetchPriceHistory({
          campaignInventoryScheduleId: scheduleId,
          params: {
            campaignInventoryScheduleId: scheduleId,
            page,
            size: HISTORY_PAGE_SIZE,
          },
        }).unwrap();

        setHistoryByRow((prev) => ({
          ...prev,
          [rowId]: {
            items: response.data?.content ?? [],
            page,
            totalElements: response.data?.totalElements ?? 0,
            totalPages: response.data?.totalPages ?? 0,
            isLoading: false,
          },
        }));
      } catch {
        setHistoryByRow((prev) => ({
          ...prev,
          [rowId]: {
            items: [],
            page,
            totalElements: 0,
            totalPages: 0,
            isLoading: false,
          },
        }));
      }
    },
    [fetchPriceHistory],
  );

  const tableData: ParentRowData[] = useMemo(
    () =>
      (priceData?.data?.content ?? []).map((item) => ({
        id: item.inventoryId,
        inventoryName: item.inventoryName,
        currentRate: item.currentRate || 0,
        proposedRate: item.proposedRate || 0,
        campaignInventoryScheduleId: item.id,
      })),
    [priceData],
  );

  const handleExpansionChange = useCallback(
    (expanded: Set<string>) => {
      // The table hands back its whole expanded set; the newly added id (if
      // any) is the row the user just clicked open.
      const opened = Array.from(expanded).find((rowId) => rowId !== openRow);

      if (closeTimerRef.current) clearTimeout(closeTimerRef.current);

      // Collapse: animate out first, then unmount the row.
      if (!opened) {
        setIsContentVisible(false);
        closeTimerRef.current = setTimeout(
          () => setOpenRow(null),
          EXPAND_ANIMATION_MS,
        );
        return;
      }

      // Fetch the first time a row is opened; the cache serves re-opens.
      if (!historyByRow[opened]) {
        const row = tableData.find((item) => item.id === opened);
        const scheduleId = row?.campaignInventoryScheduleId;
        if (typeof scheduleId === "string" && scheduleId) {
          void loadHistory(opened, scheduleId, 0);
        }
      }

      // Switching rows: collapse the current one, then open the next so the
      // two transitions read as one movement rather than a jump.
      if (openRow) {
        setIsContentVisible(false);
        closeTimerRef.current = setTimeout(
          () => setOpenRow(opened),
          EXPAND_ANIMATION_MS,
        );
        return;
      }

      setOpenRow(opened);
    },
    [openRow, historyByRow, tableData, loadHistory],
  );

  const currencyToDisplay = useCallback(
    (value: unknown): string =>
      typeof value === "number" && value > 0
        ? formatCurrency(value, currency)
        : "--",
    [currency],
  );

  const columns: ParentTableColumn<ParentRowData>[] = useMemo(
    () => [
      {
        key: "inventoryName",
        header: t("table.columns.inventory_name"),
        align: "left",
        render: (value) => (
          <span className="block max-w-[280px] whitespace-normal break-words text-sm font-medium text-mw-black dark:text-white">
            {typeof value === "string" && value ? value : "--"}
          </span>
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
        render: (value) => (
          <span className="text-sm font-semibold">
            {currencyToDisplay(value)}
          </span>
        ),
      },
    ],
    [t, currencyToDisplay],
  );

  const renderHistory = useCallback(
    (parentRow: ParentRowData) => {
      const state = historyByRow[parentRow.id];

      if (!state || state.isLoading) {
        return (
          <div className="flex items-center justify-center gap-2 py-6">
            <Spinner size="sm" variant="primary" />
            <span className="text-sm text-mw-neutral-500">
              {tCommon("messages.loading")}
            </span>
          </div>
        );
      }

      if (state.items.length === 0) {
        return (
          <p className="py-6 text-center text-sm text-mw-neutral-500">
            {t("drawers.add_proposal_price.no_history")}
          </p>
        );
      }

      return (
        <div className="space-y-2 py-2">
          <table className="w-full">
            <thead className="bg-grey-50">
              <tr className="h-8">
                {[
                  "sr_no",
                  "date",
                  "user",
                  "action",
                  "old_value",
                  "new_value",
                ].map((key) => (
                  <th
                    key={key}
                    className={clsx(
                      "p-2 text-xs font-medium leading-none text-og-black",
                      key === "old_value" || key === "new_value"
                        ? "text-right"
                        : "text-left",
                    )}
                  >
                    {t(`drawers.add_proposal_price.history_columns.${key}`)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {state.items.map((item, index) => {
                // Newest entry carries the highest number, counting down the page.
                const serialNumber =
                  state.totalElements -
                  (state.page * HISTORY_PAGE_SIZE + index);
                const actionKey = ACTION_KEYS[item.action];

                return (
                  <tr
                    key={`${item.userId}-${item.createdAt}-${index}`}
                    className="border-b border-container-border"
                  >
                    <td className="p-2 text-sm">
                      {String(serialNumber).padStart(2, "0")}
                    </td>
                    <td className="p-2 text-sm">
                      {formatDisplayDate(item.createdAt, tCommon)}
                    </td>
                    <td className="p-2 text-sm">
                      <p>{item.createdBy || "--"}</p>
                      <p className="text-xs text-secondary">
                        {item.role || "--"}
                      </p>
                    </td>
                    <td className="p-2 text-sm">
                      <div className="flex items-center gap-2">
                        <span
                          className={clsx(
                            "size-2 rounded-full",
                            ACTION_COLORS[item.action] ?? "bg-mw-neutral-500",
                          )}
                        />
                        <span className="text-mw-neutral-500">
                          {actionKey
                            ? t(
                                `drawers.add_proposal_price.actions.${actionKey}`,
                              )
                            : item.action}
                        </span>
                      </div>
                    </td>
                    <td className="p-2 text-right text-sm">
                      {currencyToDisplay(item.oldPrice)}
                    </td>
                    <td className="p-2 text-right text-sm font-medium">
                      {currencyToDisplay(item.newPrice)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>

          {state.totalPages > 1 && (
            <TablePagination
              currentPage={state.page + 1}
              totalPages={state.totalPages}
              pageSize={HISTORY_PAGE_SIZE}
              totalItems={state.totalElements}
              onPageChange={(page) => {
                const scheduleId = parentRow.campaignInventoryScheduleId;
                if (typeof scheduleId === "string") {
                  void loadHistory(parentRow.id, scheduleId, page - 1);
                }
              }}
            />
          )}
        </div>
      );
    },
    [historyByRow, t, tCommon, currencyToDisplay, loadHistory],
  );

  const summary = summaryResponse?.data;

  return (
    <ModalDrawer
      isOpen={isOpen}
      onClose={onClose}
      title={t("drawers.price_history.title")}
      size="custom"
      customWidth="60vw"
      position="right"
      id="price-history-drawer"
      showBackButton={false}
      footer={
        <div className="flex justify-end">
          <Button variant="outline" onClick={onClose}>
            {tCommon("buttons.cancel")}
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <div className="flex items-center gap-4">
          <Card className="flex-1 p-4">
            <p className="mb-2 text-sm font-normal leading-4 text-mw-neutral-700 dark:text-mw-neutral-400">
              {t("drawers.pricing_summary.current_price")}
            </p>
            <p className="text-lg font-semibold leading-6 text-mw-primary-500">
              {formatCurrency(summary?.currentPrice ?? 0, currency)}
            </p>
          </Card>
          <Card className="flex-1 p-4">
            <p className="mb-2 text-sm font-normal leading-4 text-mw-neutral-700 dark:text-mw-neutral-400">
              {t("drawers.pricing_summary.proposed_price")}
            </p>
            <p className="text-lg font-semibold leading-6 text-mw-primary-500">
              {formatCurrency(summary?.proposedPrice ?? 0, currency)}
            </p>
          </Card>
        </div>

        <HierarchicalTable
          data={tableData}
          columns={columns}
          density="comfortable"
          loading={isFetching}
          skeletonRowsCount={INVENTORY_PAGE_SIZE}
          emptyMessage={t("table.empty_message")}
          parentRowClassName="bg-mw-neutral-50 dark:bg-mw-neutral-800"
          expansion={{
            enabled: true,
            expandedItems,
            columnPosition: 0,
            onExpansionChange: handleExpansionChange,
            getItemId: (row) => row.id,
            showChildrenHeaders: false,
            renderChildren: (_children, parentRow) => (
              <tr>
                <td colSpan={columns.length + 1} className="px-4">
                  {/* grid-rows 0fr -> 1fr animates to the content's natural
                      height, so no fixed max-height has to be guessed. The
                      inner overflow-hidden is what actually clips it. */}
                  <div
                    className={clsx(
                      "grid transition-all ease-out motion-reduce:transition-none",
                      isContentVisible
                        ? "grid-rows-[1fr] opacity-100"
                        : "grid-rows-[0fr] opacity-0",
                    )}
                    style={{ transitionDuration: `${EXPAND_ANIMATION_MS}ms` }}
                  >
                    <div className="overflow-hidden">
                      {renderHistory(parentRow as ParentRowData)}
                    </div>
                  </div>
                </td>
              </tr>
            ),
          }}
        />

        {priceData?.data && priceData.data.totalPages > 1 && (
          <div className="flex justify-end">
            <TablePagination
              currentPage={inventoryPage + 1}
              totalPages={priceData.data.totalPages}
              pageSize={INVENTORY_PAGE_SIZE}
              totalItems={priceData.data.totalElements}
              onPageChange={(page) => setInventoryPage(page - 1)}
            />
          </div>
        )}
      </div>
    </ModalDrawer>
  );
};

export default PriceHistoryDrawer;
