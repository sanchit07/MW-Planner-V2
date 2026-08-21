import { Button } from "@components/ui/Button";
import { useTranslate } from "@tolgee/react";
import { formatNumberInput, parseNumberInput } from "@utils/budget.utils";
import { formatCurrency } from "@utils/campaign.utils";
import { clsx } from "clsx";
import { Pen, Save, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { useInlinePriceEditing } from "./InlinePriceEditContext";

export interface InlineProposedPriceCellProps {
  /** Effective price to display - the pending draft if there is one, the
   * last-saved price otherwise. The caller resolves which one this is. */
  value: number;
  currency: string;
  /** Identifies this cell so only one price can be edited at a time. */
  rowKey: string;
  /** Inventory rows omit the price source line; schedule rows show it. */
  isInventoryRow: boolean;
  /** True when `value` is an unsaved draft rather than the last-saved price. */
  isDraft?: boolean;
  /** The server's last-saved price, ignoring any staged draft. Edits may only
   * discount - a new price above this baseline is rejected. */
  originalPrice: number;
  /** Stages the new price - does not call the API. The caller persists all
   * staged edits together when the user saves from the summary drawer. */
  onSave: (newPrice: number) => void;
}

const MAX_DECIMALS = 2;

const hasTooManyDecimals = (value: string): boolean => {
  const [, decimals] = value.split(".");
  return Boolean(decimals && decimals.length > MAX_DECIMALS);
};

/**
 * Round to the 2 decimals the UI displays. API prices are floats, so a value
 * shown as "5,296.35" can arrive as 5296.349999999999 — seeding the input with
 * the raw number would surface that noise the moment the user clicks to edit.
 */
const toEditableAmount = (value: number): number =>
  Math.round(value * 100) / 100;

/**
 * Proposed price cell with inline editing: click the value to turn it into an
 * input, Save/Enter stages the change (no network call), Cancel/Escape
 * discards it.
 */
export const InlineProposedPriceCell: React.FC<
  InlineProposedPriceCellProps
> = ({
  value,
  currency,
  rowKey,
  isInventoryRow,
  isDraft = false,
  originalPrice,
  onSave,
}) => {
  const { t } = useTranslate(["price"]);
  const { t: tCommon } = useTranslate(["common"]);

  const { isEditing, setEditing } = useInlinePriceEditing(rowKey);
  const [draft, setDraft] = useState("");
  const [error, setError] = useState("");

  // Another cell taking over closes this one. Drop the draft so reopening shows
  // the current value again rather than the abandoned edit.
  const wasEditing = useRef(isEditing);
  useEffect(() => {
    if (wasEditing.current && !isEditing) {
      setDraft("");
      setError("");
    }
    wasEditing.current = isEditing;
  }, [isEditing]);

  const displayValue =
    typeof value === "number" && value > 0
      ? formatCurrency(value, currency)
      : "--";

  const startEditing = () => {
    setDraft(value ? String(toEditableAmount(value)) : "");
    setError("");
    setEditing(true);
  };

  const cancelEditing = () => {
    setEditing(false);
    setDraft("");
    setError("");
  };

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    // Keep only digits and a single decimal point, matching the budget input.
    // The grouping commas are re-applied on render, so they never reach state.
    const sanitized = event.target.value.replace(/[^\d.]/g, "");
    if (hasTooManyDecimals(sanitized)) return;
    setDraft(sanitized);
    setError("");
  };

  const save = () => {
    const trimmed = draft.trim();
    if (trimmed === "") {
      setError(t("drawers.add_proposal_price.error_required"));
      return;
    }

    const numericValue = parseNumberInput(trimmed);
    if (numericValue === undefined || numericValue < 0) {
      setError(t("drawers.add_proposal_price.error_invalid"));
      return;
    }

    if (numericValue > toEditableAmount(originalPrice)) {
      setError(t("drawers.add_proposal_price.error_max_price"));
      return;
    }

    // Nothing changed - leave edit mode without staging anything. Compared at
    // display precision so float noise in `value` does not look like an edit.
    if (numericValue === toEditableAmount(value)) {
      cancelEditing();
      return;
    }

    onSave(numericValue);
    cancelEditing();
  };

  if (isEditing) {
    return (
      <div
        className="flex flex-col items-end gap-1"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-end gap-1">
          {/* Raw input rather than the shared <Input>, which would prepend its
              own w-full / h-10 / border classes. Kept the reference class list
              verbatim except for five shadcn tokens this theme does not define
              (border-input, bg-background, ring-offset-background, ring-ring,
              text-muted-foreground), mapped to their mw-* equivalents. */}
          <input
            autoFocus
            type="text"
            inputMode="decimal"
            value={formatNumberInput(draft)}
            onChange={handleChange}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                save();
              }
              if (e.key === "Escape") {
                e.preventDefault();
                cancelEditing();
              }
            }}
            className="flex rounded-md border border-mw-neutral-100 dark:border-mw-neutral-600 bg-white dark:bg-mw-neutral-800 px-3 py-2 text-sm ring-offset-white dark:ring-offset-mw-neutral-800 file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-mw-neutral-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-mw-primary-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 h-8 w-32 text-right"
            aria-label={t("table.columns.proposed_price")}
          />
          <Button
            type="button"
            variant="ghost"
            size="iconMd"
            className="h-8 w-8 p-0"
            onClick={save}
            title={tCommon("buttons.save")}
            aria-label={tCommon("buttons.save")}
          >
            <Save className="size-4" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="iconMd"
            className="h-8 w-8 p-0"
            onClick={cancelEditing}
            title={tCommon("buttons.cancel")}
            aria-label={tCommon("buttons.cancel")}
          >
            <X className="size-4" />
          </Button>
        </div>
        {error && (
          <p className="text-xs text-mw-error-500 dark:text-mw-error-400">
            {error}
          </p>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col items-end">
      <button
        type="button"
        className={clsx(
          "group flex items-center justify-end gap-1.5 rounded px-2 py-1 text-right text-sm cursor-pointer transition-colors hover:bg-mw-primary-50 dark:hover:bg-mw-primary-950",
          isInventoryRow ? "w-full font-semibold" : "font-medium",
        )}
        onClick={(e) => {
          e.stopPropagation();
          startEditing();
        }}
      >
        {isDraft && (
          <span
            className="size-1.5 shrink-0 rounded-full bg-mw-primary-500"
            title={t("table.unsaved_change")}
          />
        )}
        <span className={clsx(isDraft && "text-mw-primary-500")}>
          {displayValue}
        </span>
        <Pen className="size-3 opacity-0 transition-opacity group-hover:opacity-50" />
      </button>
    </div>
  );
};

export default InlineProposedPriceCell;
