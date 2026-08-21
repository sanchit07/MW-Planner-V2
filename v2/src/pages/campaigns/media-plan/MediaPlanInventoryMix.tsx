import { Card, CardContent } from "@components/ui/card";
import { useTranslate } from "@tolgee/react";
import { normalizeGoalType } from "@utils/budget.utils";
import { formatCurrency } from "@utils/campaign.utils";
import { formatCompactNumber } from "@utils/dashboard.utils";
import React from "react";
import { CostSplitByCampaignData } from "src/types/campaign.types";
import { CampaignForecastData } from "src/types/inventory.types";

import { MediaPlanHeaderInfo, PresentationTheme } from "./types";
import { getThemePrimaryBackgroundStyle } from "./utils";

interface MediaPlanInventoryMixProps {
  costSplitData?: CostSplitByCampaignData[];
  /** Channels selected on the campaign (codes). Any not present in the
   * cost-split (targeted but no inventory booked) are shown as zero rows. */
  mediaChannels?: string[];
  headerInfo?: MediaPlanHeaderInfo;
  performanceMetrics?: CampaignForecastData | null;
  /** Campaign goal — SOV/ADPLAYS relabel the CPM column as CPS. */
  goalType?: string;
  theme?: PresentationTheme;
}

const MediaPlanInventoryMixComponent: React.FC<MediaPlanInventoryMixProps> = ({
  costSplitData = [],
  mediaChannels = [],
  headerInfo,
  performanceMetrics,
  goalType,
  theme,
}) => {
  const { t } = useTranslate(["campaigns"]);
  const currency = headerInfo?.currency;
  const normalizedGoal = normalizeGoalType(goalType);
  const isCPSGoal = normalizedGoal === "SOV" || normalizedGoal === "ADPLAYS";
  const cpmColLabel = t(
    isCPSGoal
      ? "media_plan.inventory_mix.col_cps"
      : "media_plan.inventory_mix.col_cpm",
  );

  // Display label for a media-channel code (falls back to the raw code).
  const channelLabels: Record<string, string> = {
    DIGITAL_OOH: t("media_plan.inventory_mix.channel_digital_ooh"),
    CLASSIC_OOH: t("media_plan.inventory_mix.channel_classic_ooh"),
  };

  // Merge the cost-split (booked channels) with targeted-but-unbooked channels,
  // which appear as zero rows so the mix reflects the full channel selection.
  const presentNames = new Set(
    costSplitData.map((r) => r.name.trim().toLowerCase()),
  );
  const emptyChannelRows: CostSplitByCampaignData[] = mediaChannels
    .map((code) => channelLabels[code] || code)
    .filter((label) => !presentNames.has(label.trim().toLowerCase()))
    .map((label) => ({
      name: label,
      avgCpm: 0,
      frequency: 0,
      impressions: 0,
      reach: 0,
      totalAmount: 0,
      totalAmountInPercentage: 0,
      totalInventories: 0,
    }));
  const rows = [...costSplitData, ...emptyChannelRows];

  // Totals for the summary row + boxes.
  const totalInventories = rows.reduce(
    (sum, r) => sum + (r.totalInventories || 0),
    0,
  );
  const totalImpressions = rows.reduce(
    (sum, r) => sum + (r.impressions || 0),
    0,
  );
  const totalCost = rows.reduce((sum, r) => sum + (r.totalAmount || 0), 0);
  const totalShare = rows.reduce(
    (sum, r) => sum + (r.totalAmountInPercentage || 0),
    0,
  );
  const displayedCpmValue = performanceMetrics?.avgCpm ?? 0;
  const isClassicChannelValue = (name: string) =>
    name.trim().toLowerCase().includes("classic");

  // Leading channel by cost, for the footer note.
  const leadChannel = rows.reduce<CostSplitByCampaignData | null>(
    (lead, r) =>
      !lead || (r.totalAmount || 0) > (lead.totalAmount || 0) ? r : lead,
    null,
  );

  const columnClasses = "px-4 py-3 text-right";

  return (
    <Card
      id="media-plan-inventory-mix-card"
      className="mt-4 overflow-hidden p-0"
    >
      {/* Section banner (theme-primary background) */}
      <div
        id="media-plan-inventory-mix-header"
        className="px-6 py-5 text-white"
        style={getThemePrimaryBackgroundStyle(theme)}
      >
        <h2
          id="media-plan-inventory-mix-title"
          className="text-2xl font-bold leading-8"
        >
          {t("media_plan.inventory_mix.title")}
        </h2>
        <p
          id="media-plan-inventory-mix-subtitle"
          className="text-sm text-white/80"
        >
          {t("media_plan.inventory_mix.subtitle", {
            inventories: totalInventories,
            channels: rows.length,
          })}
        </p>
      </div>

      <CardContent id="media-plan-inventory-mix-content" className="mt-4 p-6">
        {rows.length === 0 ? (
          <p
            id="media-plan-inventory-mix-empty"
            className="py-6 text-center text-sm text-mw-neutral-400"
          >
            {t("media_plan.inventory_mix.empty")}
          </p>
        ) : (
          <>
            <div className="overflow-x-auto rounded-lg border border-container-border">
              <table
                id="media-plan-inventory-mix-table"
                className="w-full text-sm"
              >
                <thead>
                  <tr className="border-b border-container-border bg-mw-neutral-50 text-xs uppercase tracking-wider text-mw-neutral-500">
                    <th className="px-4 py-3 text-left font-medium">
                      {t("media_plan.inventory_mix.col_media_channel")}
                    </th>
                    <th className="px-4 py-3 text-right font-medium">
                      {t("media_plan.inventory_mix.col_inventories")}
                    </th>
                    <th className="px-4 py-3 text-right font-medium">
                      {t("media_plan.inventory_mix.col_impressions")}
                    </th>
                    <th className="px-4 py-3 text-right font-medium">
                      {t("media_plan.inventory_mix.col_cost")}
                    </th>
                    <th className="px-4 py-3 text-right font-medium">
                      {cpmColLabel}
                    </th>
                    <th className="px-4 py-3 text-right font-medium">
                      {t("media_plan.inventory_mix.col_share")}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr
                      key={row.name}
                      className="border-b border-container-border last:border-b-0 text-mw-neutral-700"
                    >
                      <td className="px-4 py-3 text-left font-medium">
                        <span className="inline-flex items-center gap-2">
                          <span
                            className="h-2 w-2 rounded-full"
                            style={getThemePrimaryBackgroundStyle(theme)}
                          />
                          {row.name}
                        </span>
                      </td>
                      <td className={columnClasses}>
                        {(row.totalInventories || 0).toLocaleString()}
                      </td>
                      <td className={columnClasses}>
                        {formatCompactNumber(row.impressions || 0)}
                      </td>
                      <td className={columnClasses}>
                        {formatCurrency(row.totalAmount || 0, currency)}
                      </td>
                      <td className={columnClasses}>
                        {isClassicChannelValue(row.name)
                          ? "-"
                          : formatCurrency(displayedCpmValue, currency)}
                      </td>
                      <td
                        className={`${columnClasses} font-semibold`}
                        style={{ color: "hsl(var(--primary))" }}
                      >
                        {(row.totalAmountInPercentage || 0).toFixed(0)}%
                      </td>
                    </tr>
                  ))}
                  {/* Total row */}
                  <tr
                    id="media-plan-inventory-mix-total-row"
                    className="bg-mw-neutral-50 font-semibold text-mw-neutral-900"
                  >
                    <td className="px-4 py-3 text-left">
                      {t("media_plan.inventory_mix.total")}
                    </td>
                    <td className={columnClasses}>
                      {totalInventories.toLocaleString()}
                    </td>
                    <td className={columnClasses}>
                      {formatCompactNumber(totalImpressions)}
                    </td>
                    <td className={columnClasses}>
                      {formatCurrency(totalCost, currency)}
                    </td>
                    <td className={columnClasses}>
                      {formatCurrency(displayedCpmValue, currency)}
                    </td>
                    <td
                      className={columnClasses}
                      style={{ color: "hsl(var(--primary))" }}
                    >
                      {totalShare.toFixed(0)}%
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            {/* Summary boxes */}
            <div
              id="media-plan-inventory-mix-summary"
              className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3"
            >
              <div className="rounded-lg border border-container-border px-4 py-3">
                <p className="text-xs uppercase tracking-wider text-mw-neutral-500">
                  {t("media_plan.inventory_mix.summary_media_channels")}
                </p>
                <p className="text-lg font-semibold text-mw-neutral-900">
                  {rows.length.toLocaleString()}
                </p>
              </div>
              <div className="rounded-lg border border-container-border px-4 py-3">
                <p className="text-xs uppercase tracking-wider text-mw-neutral-500">
                  {t("media_plan.inventory_mix.summary_total_inventories")}
                </p>
                <p className="text-lg font-semibold text-mw-neutral-900">
                  {totalInventories.toLocaleString()}
                </p>
              </div>
              <div className="rounded-lg border border-container-border px-4 py-3">
                <p className="text-xs uppercase tracking-wider text-mw-neutral-500">
                  {t("media_plan.inventory_mix.summary_total_cost")}
                </p>
                <p className="text-lg font-semibold text-mw-neutral-900">
                  {formatCurrency(totalCost, currency)}
                </p>
              </div>
            </div>

            {/* Filler so the card matches the PPT slide's fixed height */}
            <div aria-hidden className="h-70" />

            {leadChannel && (
              <p
                id="media-plan-inventory-mix-note"
                className="mt-4 border-t border-container-border pt-4 text-xs leading-5"
                style={{ color: "hsl(var(--muted-foreground))" }}
              >
                {t("media_plan.inventory_mix.note", {
                  channel: leadChannel.name,
                  share: (leadChannel.totalAmountInPercentage || 0).toFixed(0),
                  inventories: leadChannel.totalInventories || 0,
                })}
              </p>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
};

export default MediaPlanInventoryMixComponent;
