import { useTranslate } from "@tolgee/react";
import { formatCurrencyWithLocale } from "@utils/currency";
import { clsx } from "clsx";

import type { ChannelKey, ChannelRow } from "./selection-stats.utils";

interface BudgetByChannelPopupProps {
  isOpen: boolean;
  onClose: () => void;
  rows: ChannelRow[];
  currency: string;
}

const LABEL_KEY: Record<ChannelKey, string> = {
  digital: "optimization.budgetAllocation.digital",
  classic: "optimization.budgetAllocation.classic",
  cinema: "optimization.budgetAllocation.cinema",
};

/**
 * Popup shown from the "Edit Manually" footer's "Budget by channel" toggle:
 * compares Step-2 planned budget per channel against the current selection
 * cost per channel, with a Total row and an over-budget note.
 * Purely presentational — all data is derived by the parent via
 * computeChannelRows (selection-stats.utils).
 */
const BudgetByChannelPopup = ({
  isOpen,
  onClose,
  rows,
  currency,
}: BudgetByChannelPopupProps) => {
  const { t } = useTranslate(["campaigns"]);

  if (!isOpen) return null;

  const total = rows.reduce(
    (acc, r) => ({
      planned: acc.planned + r.planned,
      selected: acc.selected + r.selected,
      difference: acc.difference + r.difference,
    }),
    { planned: 0, selected: 0, difference: 0 },
  );
  const anyOver = rows.some((r) => r.difference > 0);
  const money = (v: number) => formatCurrencyWithLocale(v, currency, 0);
  const diffText = (v: number) => (v > 0 ? `+ ${money(v)}` : money(v));
  const diffClass = (v: number) =>
    clsx(v > 0 ? "text-mw-warning-500" : "text-mw-neutral-700");

  return (
    <>
      <div className="fixed inset-0 z-40" onClick={onClose} />
      <div
        data-testid="budget-by-channel-popup"
        className="absolute bottom-full right-0 z-50 mb-2 w-[520px] rounded-lg border border-mw-neutral-200 bg-white p-4 shadow-lg"
      >
        <h3 className="text-sm font-semibold text-mw-neutral-900">
          {t("inventories.manual.channelPopup.title")}
        </h3>
        <p className="mt-1 text-xs text-mw-neutral-500">
          {t("inventories.manual.channelPopup.subtitle")}
        </p>
        <table className="mt-3 w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-mw-neutral-500">
              <th className="py-2">
                {t("inventories.manual.channelPopup.channel")}
              </th>
              <th className="py-2">
                {t("inventories.manual.channelPopup.planned")}
              </th>
              <th className="py-2">
                {t("inventories.manual.channelPopup.selected")}
              </th>
              <th className="py-2">
                {t("inventories.manual.channelPopup.difference")}
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr
                key={r.key}
                data-testid={`channel-row-${r.key}`}
                className="border-t border-mw-neutral-100"
              >
                <td className="py-2">
                  <div className="text-mw-neutral-900">
                    {t(LABEL_KEY[r.key] ?? r.key)}
                  </div>
                  <div
                    data-testid={`channel-inventories-${r.key}`}
                    className="text-xs text-mw-neutral-400"
                  >
                    {r.inventories}{" "}
                    {t("inventories.manual.channelPopup.inventories")}
                  </div>
                </td>
                <td className="py-2">{money(r.planned)}</td>
                <td className="py-2">{money(r.selected)}</td>
                <td className={clsx("py-2", diffClass(r.difference))}>
                  {diffText(r.difference)}
                </td>
              </tr>
            ))}
            <tr
              data-testid="channel-row-total"
              className="border-t border-mw-neutral-200 font-semibold"
            >
              <td className="py-2">
                {t("inventories.manual.channelPopup.total")}
              </td>
              <td className="py-2">{money(total.planned)}</td>
              <td className="py-2">{money(total.selected)}</td>
              <td className={clsx("py-2", diffClass(total.difference))}>
                {diffText(total.difference)}
              </td>
            </tr>
          </tbody>
        </table>
        {anyOver && (
          <div
            data-testid="over-budget-note"
            className="mt-3 rounded-md bg-mw-warning-50 p-3 text-xs text-mw-warning-700"
          >
            {t("inventories.manual.channelPopup.overNote")}
          </div>
        )}
      </div>
    </>
  );
};

export default BudgetByChannelPopup;
