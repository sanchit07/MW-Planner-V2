import { useTranslate } from "@tolgee/react";
import { normalizeGoalType } from "@utils/budget.utils";
import { formatCurrency } from "@utils/campaign.utils";
import { formatCompactNumber } from "@utils/dashboard.utils";
import React from "react";
import { CampaignForecastData } from "src/types/inventory.types";

import type { AnalyticsExcelData, StatePlanningRow } from "../analyticsTypes";
import { computeExpectedDelivery } from "../utils";

interface GeographySummary {
  cityCount: number;
  countryCount: number;
  poiCount: number;
}

interface PlanHeaderInfo {
  status?: string;
  duration?: number;
  budget?: number;
  startDate?: string;
  endDate?: string;
}

interface KeyValueRow {
  id: string;
  label: string;
  value?: React.ReactNode;
}

interface CampaignPlanTabProps {
  analyticsData: AnalyticsExcelData;
  performanceMetrics?: CampaignForecastData | null;
  geographySummary?: GeographySummary;
  channelCount?: number;
  goalType?: string;
  headerInfo?: PlanHeaderInfo;
  mediaChannels?: string[];
  clientType?: string;
  planNumber?: string;
}

/** "DIGITAL_OOH" -> "Digital OOH" */
const formatChannelLabel = (channel: string): string => {
  const [first, ...rest] = channel.split("_");
  if (!first) return channel;
  return [first.charAt(0) + first.slice(1).toLowerCase(), ...rest].join(" ");
};

const CampaignPlanTab: React.FC<CampaignPlanTabProps> = ({
  analyticsData,
  performanceMetrics,
  geographySummary,
  channelCount = 0,
  goalType,
  headerInfo,
  mediaChannels = [],
  clientType,
  planNumber,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const statePlanningData: StatePlanningRow[] =
    analyticsData.statePlanning || [];

  const cityInsightsData: StatePlanningRow[] =
    analyticsData.cityPlanning && analyticsData.cityPlanning.length > 0
      ? analyticsData.cityPlanning
      : statePlanningData;

  const currencyLabel = analyticsData.campaignDetails?.currency ?? "";

  const renderSummaryCard = (
    title: string,
    rows: KeyValueRow[],
    footnote?: string,
    labelWidth: string = "42%",
  ) => {
    const cardId = title
      .toLowerCase()
      .replace(/\s+/g, "-")
      .replace(/[()]/g, "");
    return (
      <section
        id={`media-plan-analytics-campaign-plan-${cardId}-card`}
        className="overflow-hidden rounded-md border bg-white"
        style={{ borderColor: "rgb(226, 232, 240)" }}
      >
        <div
          id={`media-plan-analytics-campaign-plan-${cardId}-header`}
          className="flex items-center justify-between gap-2 px-3 py-2 text-sm font-semibold text-white"
          style={{ background: "rgb(37, 99, 235)" }}
        >
          <span id={`media-plan-analytics-campaign-plan-${cardId}-title`}>
            {title}
          </span>
        </div>
        <div
          id={`media-plan-analytics-campaign-plan-${cardId}-content`}
          className="overflow-auto"
        >
          <table
            className="w-full border-collapse text-sm"
            style={{ borderColor: "rgb(215, 218, 224)" }}
          >
            <tbody>
              {rows.map((row) => (
                <tr
                  key={row.id}
                  id={`media-plan-analytics-campaign-plan-${cardId}-${row.id}`}
                >
                  <td
                    className="px-3 py-1.5 text-left font-semibold"
                    style={{
                      border: "1px solid rgb(215, 218, 224)",
                      color: "rgb(100, 116, 139)",
                      background: "rgb(250, 251, 252)",
                      width: labelWidth,
                    }}
                  >
                    {row.label}
                  </td>
                  <td
                    id={`media-plan-analytics-campaign-plan-${cardId}-${row.id}-value`}
                    className="px-3 py-1.5 text-left"
                    style={{
                      border: "1px solid rgb(215, 218, 224)",
                      color: "rgb(15, 23, 42)",
                    }}
                  >
                    {row.value}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {footnote && (
          <p
            id={`media-plan-analytics-campaign-plan-${cardId}-footnote`}
            className="px-3 py-2 text-[11px] leading-snug"
            style={{ color: "hsl(var(--muted-foreground))" }}
          >
            {footnote}
          </p>
        )}
      </section>
    );
  };

  const renderCityInsightsTable = () => (
    <section
      id="media-plan-analytics-campaign-plan-city-insights-card"
      className="overflow-hidden rounded-md border bg-white"
      style={{ borderColor: "rgb(226, 232, 240)" }}
    >
      <div
        id="media-plan-analytics-campaign-plan-city-insights-header"
        className="flex items-center justify-between gap-2 px-3 py-2 text-sm font-semibold text-white"
        style={{ background: "rgb(37, 99, 235)" }}
      >
        <span id="media-plan-analytics-campaign-plan-city-insights-title">
          {tCampaigns("mediaPlanAnalytics.campaignPlan.cityInsights.title")}
        </span>
      </div>
      <div
        id="media-plan-analytics-campaign-plan-city-insights-subtitle"
        className="border-b px-3 py-1 text-xs"
        style={{
          background: "rgba(37, 99, 235, 0.03)",
          borderColor: "rgb(215, 218, 224)",
          color: "rgb(100, 116, 139)",
        }}
      >
        {tCampaigns("mediaPlanAnalytics.campaignPlan.cityInsights.subtitle")}
      </div>
      <div
        id="media-plan-analytics-campaign-plan-city-insights-table"
        className="overflow-auto"
        style={{ maxHeight: "460px" }}
      >
        <table
          className="w-full border-collapse text-sm"
          style={{ borderColor: "rgb(215, 218, 224)" }}
        >
          <thead
            style={{
              background: "rgb(241, 243, 245)",
              color: "rgb(15, 23, 42)",
              position: "sticky",
              top: 0,
              zIndex: 1,
            }}
          >
            <tr>
              <th
                className="whitespace-nowrap px-3 py-1.5 font-semibold text-left"
                style={{
                  border: "1px solid rgb(215, 218, 224)",
                  background: "rgb(241, 243, 245)",
                }}
              >
                {tCampaigns(
                  "mediaPlanAnalytics.campaignPlan.cityInsights.cityName",
                )}
              </th>
              {[
                tCampaigns("mediaPlanAnalytics.columns.population"),
                tCampaigns("mediaPlanAnalytics.columns.inventories"),
                tCampaigns(
                  "mediaPlanAnalytics.campaignPlan.cityInsights.impressions",
                ),
                tCampaigns("mediaPlanAnalytics.columns.reach"),
                tCampaigns("mediaPlanAnalytics.columns.frequency"),
                tCampaigns("mediaPlanAnalytics.columns.cpm", {
                  currency: currencyLabel,
                }),
              ].map((label) => (
                <th
                  key={label}
                  className="whitespace-nowrap px-3 py-1.5 font-semibold text-right"
                  style={{
                    border: "1px solid rgb(215, 218, 224)",
                    background: "rgb(241, 243, 245)",
                  }}
                >
                  {label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {cityInsightsData.map((row) => (
              <tr
                key={row.id}
                id={`media-plan-analytics-campaign-plan-city-insights-row-${row.id}`}
              >
                <td
                  className="px-3 py-1.5 text-left font-semibold"
                  style={{
                    border: "1px solid rgb(215, 218, 224)",
                    color: "rgb(15, 23, 42)",
                  }}
                >
                  {row.stateName}
                </td>
                <td
                  className="px-3 py-1.5 text-right tabular-nums"
                  style={{
                    border: "1px solid rgb(215, 218, 224)",
                    color: "rgb(15, 23, 42)",
                  }}
                >
                  {row.population}
                </td>
                <td
                  className="px-3 py-1.5 text-right tabular-nums"
                  style={{
                    border: "1px solid rgb(215, 218, 224)",
                    color: "rgb(15, 23, 42)",
                  }}
                >
                  {row.inventories}
                </td>
                <td
                  className="px-3 py-1.5 text-right tabular-nums"
                  style={{
                    border: "1px solid rgb(215, 218, 224)",
                    color: "rgb(15, 23, 42)",
                  }}
                >
                  {row.oohImpressions}
                </td>
                <td
                  className="px-3 py-1.5 text-right tabular-nums"
                  style={{
                    border: "1px solid rgb(215, 218, 224)",
                    color: "rgb(15, 23, 42)",
                  }}
                >
                  {row.reach}
                </td>
                <td
                  className="px-3 py-1.5 text-right tabular-nums"
                  style={{
                    border: "1px solid rgb(215, 218, 224)",
                    color: "rgb(15, 23, 42)",
                  }}
                >
                  {row.frequency}
                </td>
                <td
                  className="px-3 py-1.5 text-right tabular-nums"
                  style={{
                    border: "1px solid rgb(215, 218, 224)",
                    color: "rgb(15, 23, 42)",
                  }}
                >
                  {row.cpm.toFixed(2)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );

  // Same computeExpectedDelivery used by the presentation view's Goals &
  // KPIs "Expected Delivery" bars — sine-wave-modulated equal split per
  // series (see utils.ts), so both views stay consistent.
  const delivery = computeExpectedDelivery(
    headerInfo?.startDate,
    headerInfo?.endDate,
    performanceMetrics?.estimatedImpression || 0,
    performanceMetrics?.estimatedReach || 0,
  );
  const deliveryGranularityLabel = tCampaigns(
    `media_plan.goals_kpis.granularity_${delivery.granularity}`,
  );

  const renderDeliveryBreakdownTable = () => (
    <section
      id="media-plan-analytics-campaign-plan-delivery-breakdown-card"
      className="overflow-hidden rounded-md border bg-white"
      style={{ borderColor: "rgb(226, 232, 240)" }}
    >
      <div
        id="media-plan-analytics-campaign-plan-delivery-breakdown-header"
        className="flex items-center justify-between gap-2 px-3 py-2 text-sm font-semibold text-white"
        style={{ background: "rgb(37, 99, 235)" }}
      >
        <span>
          {tCampaigns(
            "mediaPlanAnalytics.campaignPlan.deliveryBreakdown.title",
          )}
        </span>
        <span className="text-xs font-normal">
          {tCampaigns(
            "mediaPlanAnalytics.campaignPlan.deliveryBreakdown.badge",
            { granularity: deliveryGranularityLabel },
          )}
        </span>
      </div>
      <div
        id="media-plan-analytics-campaign-plan-delivery-breakdown-subtitle"
        className="border-b px-3 py-1 text-xs"
        style={{
          background: "rgba(37, 99, 235, 0.03)",
          borderColor: "rgb(215, 218, 224)",
          color: "rgb(100, 116, 139)",
        }}
      >
        {tCampaigns(
          "mediaPlanAnalytics.campaignPlan.deliveryBreakdown.subtitle",
        )}
      </div>
      <div
        id="media-plan-analytics-campaign-plan-delivery-breakdown-table"
        className="overflow-auto"
      >
        <table
          className="w-full border-collapse text-sm"
          style={{ borderColor: "rgb(215, 218, 224)" }}
        >
          <thead
            style={{
              background: "rgb(241, 243, 245)",
              color: "rgb(15, 23, 42)",
            }}
          >
            <tr>
              <th
                className="whitespace-nowrap px-3 py-1.5 font-semibold text-left"
                style={{
                  border: "1px solid rgb(215, 218, 224)",
                  background: "rgb(241, 243, 245)",
                }}
              >
                {tCampaigns(
                  "mediaPlanAnalytics.campaignPlan.deliveryBreakdown.period",
                )}
              </th>
              <th
                className="whitespace-nowrap px-3 py-1.5 font-semibold text-right"
                style={{
                  border: "1px solid rgb(215, 218, 224)",
                  background: "rgb(241, 243, 245)",
                }}
              >
                {tCampaigns(
                  "mediaPlanAnalytics.campaignPlan.cityInsights.impressions",
                )}
              </th>
              <th
                className="whitespace-nowrap px-3 py-1.5 font-semibold text-right"
                style={{
                  border: "1px solid rgb(215, 218, 224)",
                  background: "rgb(241, 243, 245)",
                }}
              >
                {tCampaigns("mediaPlanAnalytics.columns.reach")}
              </th>
            </tr>
          </thead>
          <tbody>
            {delivery.bins.map((bin) => (
              <tr
                key={bin.label}
                id={`media-plan-analytics-campaign-plan-delivery-breakdown-row-${bin.label
                  .toLowerCase()
                  .replace(/\s+/g, "-")}`}
              >
                <td
                  className="px-3 py-1.5 text-left font-semibold"
                  style={{
                    border: "1px solid rgb(215, 218, 224)",
                    color: "rgb(15, 23, 42)",
                  }}
                >
                  {bin.label}
                </td>
                <td
                  className="px-3 py-1.5 text-right tabular-nums"
                  style={{
                    border: "1px solid rgb(215, 218, 224)",
                    color: "rgb(15, 23, 42)",
                  }}
                >
                  {formatCompactNumber(bin.value, 1)}
                </td>
                <td
                  className="px-3 py-1.5 text-right tabular-nums"
                  style={{
                    border: "1px solid rgb(215, 218, 224)",
                    color: "rgb(15, 23, 42)",
                  }}
                >
                  {formatCompactNumber(bin.reach, 1)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );

  const planDetailsRows: KeyValueRow[] = [
    {
      id: "campaign-name",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.campaignDetails.campaignName",
      ),
      value: analyticsData.campaignDetails?.campaignName,
    },
    {
      id: "campaign-id",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.campaignDetails.campaignId",
      ),
      value: planNumber || analyticsData.campaignDetails?.campaignId,
    },
    {
      id: "status",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.campaignDetails.status",
      ),
      value: headerInfo?.status
        ? tCampaigns(`campaignsList.status.${headerInfo.status}`) ||
          headerInfo.status
        : "",
    },
    {
      id: "start-date",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.campaignDetails.start",
      ),
      value: analyticsData.campaignDetails?.startDate,
    },
    {
      id: "end-date",
      label: tCampaigns("mediaPlanAnalytics.campaignPlan.campaignDetails.end"),
      value: analyticsData.campaignDetails?.endDate,
    },
    {
      id: "duration",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.campaignDetails.duration",
      ),
      value:
        headerInfo?.duration !== undefined
          ? `${headerInfo.duration} ${tCampaigns("mediaPlanAnalytics.campaignPlan.days")}`
          : "",
    },
    {
      id: "currency",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.campaignDetails.currency",
      ),
      value: currencyLabel,
    },
    {
      id: "channels",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.campaignDetails.channels",
      ),
      value: mediaChannels.map(formatChannelLabel).join(", "),
    },
    {
      id: "budget",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.campaignDetails.budget",
      ),
      value: formatCurrency(headerInfo?.budget || 0, currencyLabel),
    },
  ];

  const clientTypeLabel =
    clientType === "DIRECT_ADVERTISER"
      ? tCampaigns(
          "mediaPlanAnalytics.campaignPlan.buyerDetails.clientTypeDirect",
        )
      : clientType === "AGENCY"
        ? tCampaigns(
            "mediaPlanAnalytics.campaignPlan.buyerDetails.clientTypeAgency",
          )
        : (clientType ?? "");

  const buyerDetailsRows: KeyValueRow[] = [
    {
      id: "client-type",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.buyerDetails.clientType",
      ),
      value: clientTypeLabel,
    },
    {
      id: "planned-by",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.buyerDetails.plannedBy",
      ),
      value: analyticsData.campaignDetails?.createdBy,
    },
    {
      id: "company",
      label: tCampaigns("mediaPlanAnalytics.campaignPlan.buyerDetails.company"),
      value: analyticsData.campaignDetails?.company,
    },
    {
      id: "brand",
      label: tCampaigns("mediaPlanAnalytics.campaignPlan.buyerDetails.brand"),
      value: analyticsData.campaignDetails?.brand,
    },
    {
      id: "brand-category",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.buyerDetails.brandCategory",
      ),
      value: analyticsData.campaignDetails?.brandCategory,
    },
  ];

  // SOV/AD_PLAYS goals are priced per spot → relabel Avg CPM as Avg CPS (same value).
  const normalizedGoal = normalizeGoalType(goalType);
  const isCPSGoal = normalizedGoal === "SOV" || normalizedGoal === "ADPLAYS";
  const sotPercent =
    performanceMetrics?.plannedSot && performanceMetrics?.totalSot
      ? (performanceMetrics.plannedSot / performanceMetrics.totalSot) * 100
      : 0;

  // Ad plays only apply to digital (slot-based) inventory — hide the row for
  // classic-only campaigns (mirrors transformEstimatedPerformanceMetrics).
  const hasDigitalInventory =
    analyticsData.estimatedPerformanceMetrics?.hasDigitalInventory ?? true;

  const estimatedPerformanceRows: KeyValueRow[] = [
    {
      id: "total-impressions",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.estimation.totalImpressions",
      ),
      value: formatCompactNumber(performanceMetrics?.estimatedImpression || 0),
    },
    {
      id: "estimated-reach",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.estimation.estimatedReach",
      ),
      value: formatCompactNumber(performanceMetrics?.estimatedReach || 0),
    },
    {
      id: "avg-frequency",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.estimation.avgFrequency",
      ),
      value: (performanceMetrics?.estimatedFrequency || 0).toFixed(2),
    },
    ...(hasDigitalInventory
      ? [
          {
            id: "ad-plays",
            label: tCampaigns(
              "mediaPlanAnalytics.campaignPlan.estimation.adPlays",
            ),
            value: (performanceMetrics?.estimatedAdPlays || 0).toLocaleString(),
          },
        ]
      : []),
    {
      id: "cpm",
      label: tCampaigns(
        `mediaPlanAnalytics.campaignPlan.estimation.${isCPSGoal ? "avgCps" : "avgCpm"}`,
      ),
      value: formatCurrency(performanceMetrics?.avgCpm || 0, currencyLabel),
    },
    {
      id: "ecpm",
      label: tCampaigns("mediaPlanAnalytics.campaignPlan.estimation.ecpm"),
      value: formatCurrency(performanceMetrics?.avgECpm || 0, currencyLabel),
    },
    {
      id: "sov",
      label: tCampaigns("mediaPlanAnalytics.campaignPlan.estimation.sov"),
      value: `${(performanceMetrics?.sov || 0).toFixed(1)}%`,
    },
    {
      id: "sot",
      label: tCampaigns("mediaPlanAnalytics.campaignPlan.estimation.sot"),
      value: `${sotPercent.toFixed(1)}%`,
    },
    {
      id: "total-cost",
      label: tCampaigns("mediaPlanAnalytics.campaignPlan.estimation.totalCost"),
      value: formatCurrency(performanceMetrics?.totalCost || 0, currencyLabel),
    },
    {
      id: "inventories",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.estimation.inventories",
      ),
      value: (performanceMetrics?.totalInventories || 0).toLocaleString(),
    },
    {
      id: "cities",
      label: tCampaigns("mediaPlanAnalytics.campaignPlan.estimation.cities"),
      value: (geographySummary?.cityCount || 0).toLocaleString(),
    },
    {
      id: "channels",
      label: tCampaigns("mediaPlanAnalytics.campaignPlan.estimation.channels"),
      value: channelCount.toLocaleString(),
    },
  ];

  const targetingApplied = analyticsData.targetingApplied;
  const targetingAppliedRows: KeyValueRow[] = [
    {
      id: "demographics",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.targetingApplied.demographics",
      ),
      value: targetingApplied?.demographics || "--",
    },
    {
      id: "income",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.targetingApplied.income",
      ),
      value: targetingApplied?.income || "--",
    },
    {
      id: "interests",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.targetingApplied.interests",
      ),
      value: targetingApplied?.interests || "--",
    },
    {
      id: "venue-environments",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.targetingApplied.venueEnvironments",
      ),
      value: targetingApplied?.venueEnvironments || "--",
    },
    {
      id: "behaviour",
      label: tCampaigns(
        "mediaPlanAnalytics.campaignPlan.targetingApplied.behaviour",
      ),
      value: targetingApplied?.behaviour || "--",
    },
  ];

  return (
    <div
      id="media-plan-analytics-campaign-plan-container"
      className="space-y-6"
    >
      {/* Three Summary Cards */}
      <div
        id="media-plan-analytics-campaign-plan-summary-cards"
        className="grid grid-cols-1 gap-4 lg:grid-cols-3"
      >
        {renderSummaryCard(
          tCampaigns("mediaPlanAnalytics.campaignPlan.campaignDetails.title"),
          planDetailsRows,
        )}
        {renderSummaryCard(
          tCampaigns("mediaPlanAnalytics.campaignPlan.buyerDetails.title"),
          buyerDetailsRows,
        )}
        {renderSummaryCard(
          tCampaigns("mediaPlanAnalytics.campaignPlan.estimation.title"),
          estimatedPerformanceRows,
          tCampaigns("mediaPlanAnalytics.campaignPlan.estimation.note"),
        )}
      </div>

      {/* Targeting Applied */}
      {renderSummaryCard(
        tCampaigns("mediaPlanAnalytics.campaignPlan.targetingApplied.title"),
        targetingAppliedRows,
        undefined,
        "15%",
      )}

      {/* City Insights Table */}
      {renderCityInsightsTable()}

      {/* Delivery Breakdown Table */}
      {renderDeliveryBreakdownTable()}
    </div>
  );
};

export default CampaignPlanTab;
