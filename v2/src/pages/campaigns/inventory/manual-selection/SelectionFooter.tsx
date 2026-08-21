import { Button } from "@components/ui/Button";
import { Tooltip } from "@components/ui/Tooltip";
import { useTranslate } from "@tolgee/react";
import { formatNumber } from "@utils/budget.utils";
import { formatCurrencyWithLocale } from "@utils/currency";
import { clsx } from "clsx";
import { AlertTriangle, ChevronDown, ChevronUp } from "lucide-react";
import type { ReactNode } from "react";

import BudgetByChannelPopup from "./BudgetByChannelPopup";
import type { ChannelRow, FooterTotals } from "./selection-stats.utils";

interface SelectionFooterProps {
  totals: FooterTotals;
  budget: number;
  currency: string;
  channelRows: ChannelRow[];
  isSaving: boolean;
  onCancel: () => void;
  onSave: () => void;
  onToggleChannels: () => void;
  onCloseChannels: () => void;
  channelsOpen: boolean;
}

/**
 * Stats footer for the "Edit Manually" inventory popup: selected count,
 * estimated impressions, estimated total budget vs planned (with an
 * over-budget warning), a budget-by-channel toggle, and Cancel/Save actions.
 * Purely presentational — all state and API calls live in the parent.
 */
const SelectionFooter = ({
  totals,
  budget,
  currency,
  channelRows,
  isSaving,
  onCancel,
  onSave,
  onToggleChannels,
  onCloseChannels,
  channelsOpen,
}: SelectionFooterProps) => {
  const { t } = useTranslate(["campaigns"]);

  return (
    <div className="flex items-center gap-6 border-t border-mw-neutral-100 px-4 py-3 shrink-0">
      <Stat label={t("inventories.manual.footer.inventories")}>
        {String(totals.count).padStart(2, "0")}
      </Stat>

      <Stat label={t("inventories.manual.footer.estimatedImpressions")}>
        {formatNumber(totals.impressions)}
      </Stat>

      <Stat
        label={t("inventories.manual.footer.estimatedTotalBudget")}
        icon={
          totals.overBudget ? (
            <Tooltip
              position="top"
              triggerClassName="cursor-help"
              className="max-w-[300px] border-mw-error-200 bg-mw-error-50 p-3 text-mw-error-600 shadow-lg"
              content={
                <div data-testid="budget-warning-tip">
                  <p className="text-sm font-semibold text-mw-error-600">
                    {t("inventories.manual.footer.overBudgetTitle")}
                  </p>
                  <p className="mt-1 text-sm text-mw-error-500">
                    {t("inventories.manual.footer.overBudgetMessage", {
                      planned: formatCurrencyWithLocale(budget, currency, 0),
                      overBy: formatCurrencyWithLocale(
                        totals.overBy,
                        currency,
                        0,
                      ),
                    })}
                  </p>
                </div>
              }
            >
              <span data-testid="budget-warning">
                <AlertTriangle className="h-4 w-4 text-mw-error-500" />
              </span>
            </Tooltip>
          ) : undefined
        }
      >
        <span className={clsx(totals.overBudget && "text-mw-error-500")}>
          {formatCurrencyWithLocale(totals.cost, currency, 0)}
        </span>
        <span className="text-mw-neutral-400">
          {" "}
          {t("inventories.manual.footer.of")}{" "}
          {formatCurrencyWithLocale(budget, currency, 0)}
        </span>
      </Stat>

      <div className="relative">
        <button
          type="button"
          onClick={onToggleChannels}
          className="flex flex-col items-start text-left"
        >
          <span className="text-xs text-mw-neutral-500">
            {t("inventories.manual.footer.budgetByChannel")}
          </span>
          <span
            className={clsx(
              "flex items-center gap-1 text-sm font-semibold",
              totals.overBudget ? "text-mw-error-500" : "text-mw-grey-800",
            )}
          >
            {totals.overBudget
              ? t("inventories.manual.footer.overPlan")
              : t("inventories.manual.footer.onPlan")}
            {channelsOpen ? (
              <ChevronUp className="w-4 h-4" />
            ) : (
              <ChevronDown className="w-4 h-4" />
            )}
          </span>
        </button>

        <BudgetByChannelPopup
          isOpen={channelsOpen}
          onClose={onCloseChannels}
          rows={channelRows}
          currency={currency}
        />
      </div>

      <div className="ml-auto flex items-center gap-2">
        <Button variant="outline" onClick={onCancel} disabled={isSaving}>
          {t("inventories.manual.footer.cancel")}
        </Button>
        <Button onClick={onSave} disabled={isSaving}>
          {t("inventories.manual.footer.saveSelection")}
        </Button>
      </div>
    </div>
  );
};

const Stat = ({
  label,
  icon,
  children,
}: {
  label: string;
  icon?: ReactNode;
  children: ReactNode;
}) => (
  <div className="flex flex-col">
    <span className="flex items-center gap-1 text-xs text-mw-neutral-500">
      {label}
      {icon}
    </span>
    <span className="text-sm font-semibold text-mw-grey-800">{children}</span>
  </div>
);

export default SelectionFooter;
