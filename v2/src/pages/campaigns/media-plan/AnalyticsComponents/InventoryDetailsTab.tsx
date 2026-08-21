import { useTranslate } from "@tolgee/react";
import { normalizeGoalType } from "@utils/budget.utils";
import { formatCompactNumber } from "@utils/dashboard.utils";
import React from "react";

import type {
  AnalyticsExcelData,
  InventoryDetailsRow,
} from "../analyticsTypes";

interface InventoryDetailsTabProps {
  analyticsData: AnalyticsExcelData;
  goalType?: string;
}

/** "classic network" -> "Classic Network" (fallback label for non classic/digital types) */
const formatTypeName = (type: string): string =>
  type
    .split(" ")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");

const InventoryDetailsTab: React.FC<InventoryDetailsTabProps> = ({
  analyticsData,
  goalType,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const inventoryData: InventoryDetailsRow[] =
    analyticsData.inventoryDetails || [];
  const currencyLabel = analyticsData.campaignDetails?.currency ?? "";

  // SOV/AD_PLAYS goals price per spot — swap CPM for CPS (a different field,
  // performance.spotRate, not just a relabeled cpmRate). Matches
  // InventoryDetailCard.tsx's per-item CPM/CPS logic.
  const isCPSGoal = (() => {
    const normalized = normalizeGoalType(goalType);
    return normalized === "SOV" || normalized === "ADPLAYS";
  })();

  const col = (key: string) =>
    tCampaigns(`mediaPlanAnalytics.inventoryDetails.columns.${key}`);

  // Only Classic/Digital channels exist today — other types fall back to a
  // title-cased label (e.g. "Mobile").
  const getChannelLabel = (type: InventoryDetailsRow["type"]): string => {
    if (type.includes("digital")) {
      return tCampaigns("media_plan.inventory_mix.channel_digital_ooh");
    }
    if (type.includes("classic")) {
      return tCampaigns("media_plan.inventory_mix.channel_classic_ooh");
    }
    return formatTypeName(type);
  };

  return (
    <section
      id="media-plan-analytics-inventory-details-card"
      className="overflow-hidden rounded-md border bg-white"
      style={{ borderColor: "rgb(226, 232, 240)" }}
    >
      <div
        id="media-plan-analytics-inventory-details-header"
        className="px-4 py-2 text-sm font-semibold text-white"
        style={{ background: "rgb(37, 99, 235)" }}
      >
        {tCampaigns("mediaPlanAnalytics.inventoryDetails.title", {
          count: inventoryData.length,
        })}
      </div>
      <div
        id="media-plan-analytics-inventory-details-table"
        className="overflow-x-auto"
      >
        <table className="w-full text-sm">
          <thead style={{ background: "rgba(37, 99, 235, 0.063)" }}>
            <tr>
              <th className="px-4 py-2 text-left font-medium">
                {col("inventory")}
              </th>
              <th className="px-4 py-2 text-left font-medium">
                {col("channel")}
              </th>
              <th className="px-4 py-2 text-left font-medium">
                {col("format")}
              </th>
              <th className="px-4 py-2 text-left font-medium">{col("city")}</th>
              <th className="px-4 py-2 text-left font-medium">
                {col("mediaOwner")}
              </th>
              <th className="px-4 py-2 text-right font-medium">
                {col("impressions")}
              </th>
              <th className="px-4 py-2 text-right font-medium">
                {col("playsPerDay")}
              </th>
              <th className="px-4 py-2 text-right font-medium">
                {tCampaigns(
                  `mediaPlanAnalytics.columns.${isCPSGoal ? "cps" : "cpm"}`,
                  { currency: currencyLabel },
                )}
              </th>
            </tr>
          </thead>
          <tbody>
            {inventoryData.length === 0 ? (
              <tr>
                <td
                  colSpan={8}
                  className="px-4 py-6 text-center text-mw-neutral-500"
                >
                  {tCampaigns("mediaPlanAnalytics.inventoryDetails.noData")}
                </td>
              </tr>
            ) : (
              inventoryData.map((row) => (
                <tr
                  key={row.id}
                  id={`media-plan-analytics-inventory-details-row-${row.id}`}
                  className="border-t"
                  style={{ borderColor: "rgb(226, 232, 240)" }}
                >
                  <td className="px-4 py-2">
                    <div className="flex items-center gap-2">
                      {row.thumbnailUrl ? (
                        <img
                          src={row.thumbnailUrl}
                          alt={row.billboardName || ""}
                          className="h-8 w-12 rounded object-cover"
                        />
                      ) : (
                        <div
                          className="h-8 w-12 rounded"
                          style={{ background: "rgba(37, 99, 235, 0.063)" }}
                        />
                      )}
                      <span className="font-medium">{row.billboardName}</span>
                    </div>
                  </td>
                  <td className="px-4 py-2">{getChannelLabel(row.type)}</td>
                  <td className="px-4 py-2">{row.format}</td>
                  <td className="px-4 py-2">{row.city}</td>
                  <td
                    className="px-4 py-2"
                    style={{ color: "rgb(100, 116, 139)" }}
                  >
                    {row.mediaOwner}
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {formatCompactNumber(row.impressions || 0, 1)}
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {row.type.includes("classic")
                      ? "-"
                      : (row.playsPerDay || 0).toLocaleString()}
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {isCPSGoal
                      ? (row.spotRate || 0).toFixed(2)
                      : row.type.includes("classic")
                        ? "-"
                        : (row.cpm || 0).toFixed(2)}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
};

export default InventoryDetailsTab;
