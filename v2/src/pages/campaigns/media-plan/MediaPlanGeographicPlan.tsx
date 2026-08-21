import { Card, CardContent } from "@components/ui/card";
import { useTranslate } from "@tolgee/react";
import { formatNumber, normalizeGoalType } from "@utils/budget.utils";
import { formatCurrency } from "@utils/campaign.utils";
import React from "react";
import { CostSplitByCampaignData } from "src/types/campaign.types";
import { CampaignForecastData } from "src/types/inventory.types";

import { PresentationTheme } from "./types";
import { MediaPlanHeaderInfo } from "./types";
import { getThemePrimaryBackgroundStyle } from "./utils";

interface MediaPlanGeographicPlanProps {
  costSplitData?: CostSplitByCampaignData[];
  headerInfo?: MediaPlanHeaderInfo;
  performanceMetrics?: CampaignForecastData | null;
  /** Campaign goal — SOV/ADPLAYS relabel the CPM column + summary as CPS. */
  goalType?: string;
  theme?: PresentationTheme;
}

/** City rows per container — beyond this the section spills into another
 * Card (same pattern as Inventory Snapshots) so height never grows unbounded. */
const ROWS_PER_PAGE = 8;

const MediaPlanGeographicPlanComponent: React.FC<
  MediaPlanGeographicPlanProps
> = ({
  costSplitData = [],
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
      ? "media_plan.geographic_plan.col_cps"
      : "media_plan.geographic_plan.col_cpm",
  );
  const avgCpmLabel = t(
    isCPSGoal
      ? "media_plan.geographic_plan.summary_avg_cps"
      : "media_plan.geographic_plan.summary_avg_cpm",
  );
  const rows = [...costSplitData].sort(
    (a, b) => (b.impressions || 0) - (a.impressions || 0),
  );

  const cityCountry = (row: CostSplitByCampaignData) =>
    row.country || t("media_plan.geographic_plan.not_available");

  const totalInventories = rows.reduce(
    (sum, r) => sum + (r.totalInventories || 0),
    0,
  );
  const totalImpressions = rows.reduce(
    (sum, r) => sum + (r.impressions || 0),
    0,
  );
  const totalCost = rows.reduce((sum, r) => sum + (r.totalAmount || 0), 0);
  const avgCpm = performanceMetrics?.avgCpm ?? 0;

  // Distinct countries across the displayed cities, for the footer note.
  const countryCount = new Set(
    rows.map((r) => r.country).filter((c): c is string => Boolean(c)),
  ).size;

  // Top market by impressions + its share, for the footer note.
  const topCity = rows.reduce<CostSplitByCampaignData | null>(
    (top, r) =>
      !top || (r.impressions || 0) > (top.impressions || 0) ? r : top,
    null,
  );
  const topShare =
    topCity && totalImpressions > 0
      ? ((topCity.impressions || 0) / totalImpressions) * 100
      : 0;

  const cell = "px-4 py-3 text-right";

  // Empty state — single banner-only card.
  if (rows.length === 0) {
    return (
      <Card
        id="media-plan-geographic-plan-card"
        className="mt-4 overflow-hidden p-0"
      >
        <div
          id="media-plan-geographic-plan-header"
          className="px-6 py-5 text-white"
          style={getThemePrimaryBackgroundStyle(theme)}
        >
          <h2 className="text-2xl font-bold leading-8">
            {t("media_plan.geographic_plan.title")}
          </h2>
        </div>
        <CardContent className="mt-4 p-6">
          <p
            id="media-plan-geographic-plan-empty"
            className="py-6 text-center text-sm text-mw-neutral-400"
          >
            {t("media_plan.geographic_plan.empty")}
          </p>
        </CardContent>
      </Card>
    );
  }

  // Paginate city rows into fixed-size containers so the card height stays
  // bounded no matter how many cities the plan spans (same as Inventory
  // Snapshots). The summary/footer renders once — on the last table page if
  // it has room (< 6 rows), otherwise it spills into its own trailing page.
  const pages: CostSplitByCampaignData[][] = [];
  for (let i = 0; i < rows.length; i += ROWS_PER_PAGE) {
    pages.push(rows.slice(i, i + ROWS_PER_PAGE));
  }
  const lastPageRowCount = pages[pages.length - 1]?.length ?? 0;
  const summaryOnOwnPage = lastPageRowCount >= 6;

  const summaryBlock = (
    <>
      <div
        id="media-plan-geographic-plan-summary"
        className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4"
      >
        <div className="rounded-lg border border-container-border px-4 py-3">
          <p className="text-xs uppercase tracking-wider text-mw-neutral-500">
            {t("media_plan.geographic_plan.summary_cities")}
          </p>
          <p className="text-lg font-semibold text-mw-neutral-900">
            {rows.length.toLocaleString()}
          </p>
        </div>
        <div className="rounded-lg border border-container-border px-4 py-3">
          <p className="text-xs uppercase tracking-wider text-mw-neutral-500">
            {t("media_plan.geographic_plan.summary_inventories")}
          </p>
          <p className="text-lg font-semibold text-mw-neutral-900">
            {totalInventories.toLocaleString()}
          </p>
        </div>
        <div className="rounded-lg border border-container-border px-4 py-3">
          <p className="text-xs uppercase tracking-wider text-mw-neutral-500">
            {t("media_plan.geographic_plan.summary_total_cost")}
          </p>
          <p className="text-lg font-semibold text-mw-neutral-900">
            {formatCurrency(totalCost, currency)}
          </p>
        </div>
        <div className="rounded-lg border border-container-border px-4 py-3">
          <p className="text-xs uppercase tracking-wider text-mw-neutral-500">
            {avgCpmLabel}
          </p>
          <p className="text-lg font-semibold text-mw-neutral-900">
            {formatCurrency(avgCpm, currency)}
          </p>
        </div>
      </div>

      {/* Filler so the card matches the PPT slide's fixed height */}
      <div aria-hidden className="h-70" />

      {topCity && (
        <p
          id="media-plan-geographic-plan-note"
          className="mt-4 border-t border-container-border pt-4 text-xs leading-5"
          style={{ color: "hsl(var(--muted-foreground))" }}
        >
          {t("media_plan.geographic_plan.note", {
            cities: rows.length,
            countries: countryCount,
            topMarket: topCity.name,
            share: topShare.toFixed(0),
          })}
        </p>
      )}
    </>
  );

  return (
    <>
      {pages.map((pageRows, pi) => {
        const start = pi * ROWS_PER_PAGE + 1;
        const end = pi * ROWS_PER_PAGE + pageRows.length;
        const isLastTablePage = pi === pages.length - 1;
        const showSummaryHere = isLastTablePage && !summaryOnOwnPage;
        return (
          <Card
            key={pi}
            id={pi === 0 ? "media-plan-geographic-plan-card" : undefined}
            className="mt-4 overflow-hidden p-0"
          >
            <div
              id={pi === 0 ? "media-plan-geographic-plan-header" : undefined}
              className="px-6 py-5 text-white"
              style={getThemePrimaryBackgroundStyle(theme)}
            >
              <h2 className="text-2xl font-bold leading-8">
                {t("media_plan.geographic_plan.title")}
              </h2>
              <p className="text-sm text-white/80">
                {pages.length > 1
                  ? t("media_plan.geographic_plan.page_range", {
                      start,
                      end,
                      total: rows.length,
                    })
                  : t("media_plan.geographic_plan.subtitle", {
                      inventories: totalInventories,
                      cities: rows.length,
                    })}
              </p>
            </div>

            <CardContent
              id={pi === 0 ? "media-plan-geographic-plan-content" : undefined}
              className="mt-4 p-6"
            >
              <div className="overflow-x-auto rounded-lg border border-container-border">
                <table
                  id={pi === 0 ? "media-plan-geographic-plan-table" : undefined}
                  className="w-full text-sm"
                >
                  <thead>
                    <tr className="border-b border-container-border bg-mw-neutral-50 text-xs uppercase tracking-wider text-mw-neutral-500">
                      <th className="px-4 py-3 text-left font-medium">
                        {t("media_plan.geographic_plan.col_city")}
                      </th>
                      <th className="px-4 py-3 text-left font-medium">
                        {t("media_plan.geographic_plan.col_country")}
                      </th>
                      <th className="px-4 py-3 text-right font-medium">
                        {t("media_plan.geographic_plan.col_inventories")}
                      </th>
                      <th className="px-4 py-3 text-right font-medium">
                        {t("media_plan.geographic_plan.col_impressions")}
                      </th>
                      <th className="px-4 py-3 text-right font-medium">
                        {t("media_plan.geographic_plan.col_reach")}
                      </th>
                      <th className="px-4 py-3 text-right font-medium">
                        {t("media_plan.geographic_plan.col_cost")}
                      </th>
                      <th className="px-4 py-3 text-right font-medium">
                        {cpmColLabel}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageRows.map((row) => (
                      <tr
                        key={row.name}
                        className="border-b border-container-border text-mw-neutral-700 last:border-b-0"
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
                        <td className="px-4 py-3 text-left">
                          {cityCountry(row)}
                        </td>
                        <td className={cell}>
                          {(row.totalInventories || 0).toLocaleString()}
                        </td>
                        <td className={cell}>
                          {formatNumber(row.impressions || 0)}
                        </td>
                        <td className={cell}>{formatNumber(row.reach || 0)}</td>
                        <td className={cell}>
                          {formatCurrency(row.totalAmount || 0, currency)}
                        </td>
                        <td className={cell}>
                          {formatCurrency(row.avgCpm || 0, currency)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {showSummaryHere && summaryBlock}
            </CardContent>
          </Card>
        );
      })}

      {summaryOnOwnPage && (
        <Card className="mt-4 overflow-hidden p-0">
          <div
            className="px-6 py-5 text-white"
            style={getThemePrimaryBackgroundStyle(theme)}
          >
            <h2 className="text-2xl font-bold leading-8">
              {t("media_plan.geographic_plan.title")}
            </h2>
            <p className="text-sm text-white/80">
              {t("media_plan.geographic_plan.subtitle", {
                inventories: totalInventories,
                cities: rows.length,
              })}
            </p>
          </div>
          <CardContent className="mt-4 p-6">{summaryBlock}</CardContent>
        </Card>
      )}
    </>
  );
};

export default MediaPlanGeographicPlanComponent;
