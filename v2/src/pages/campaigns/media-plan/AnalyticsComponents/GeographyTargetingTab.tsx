import { useTranslate } from "@tolgee/react";
import { formatNumber } from "@utils/budget.utils";
import React from "react";

import type {
  AnalyticsExcelData,
  GeographyTargetingRow,
} from "../analyticsTypes";

interface GeographyTargetingTabProps {
  analyticsData: AnalyticsExcelData;
}

const BORDER_COLOR = "rgb(226, 232, 240)";
const DEPTH_INDENT_PX = 20;

const GeographyTargetingTab: React.FC<GeographyTargetingTabProps> = ({
  analyticsData,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const rows: GeographyTargetingRow[] = analyticsData.geographyTargeting || [];
  const currencyLabel = analyticsData.campaignDetails?.currency ?? "";

  const col = (key: string) =>
    tCampaigns(`mediaPlanAnalytics.geographyTargeting.columns.${key}`);

  return (
    <section
      id="media-plan-analytics-geography-targeting-card"
      className="overflow-hidden rounded-md border bg-white"
      style={{ borderColor: BORDER_COLOR }}
    >
      <div
        className="px-4 py-2 text-sm font-semibold text-white"
        style={{ background: "rgb(37, 99, 235)" }}
      >
        {tCampaigns("mediaPlanAnalytics.geographyTargeting.title")}
      </div>
      <div
        className="border-b px-4 py-1 text-xs"
        style={{
          background: "rgba(37, 99, 235, 0.03)",
          borderColor: BORDER_COLOR,
          color: "rgb(100, 116, 139)",
        }}
      >
        {tCampaigns("mediaPlanAnalytics.geographyTargeting.subtitle")}
      </div>
      <div
        id="media-plan-analytics-geography-targeting-table"
        className="overflow-x-auto"
      >
        <table className="w-full text-sm" style={{ color: "rgb(15, 23, 42)" }}>
          <thead style={{ background: "rgba(37, 99, 235, 0.063)" }}>
            <tr>
              <th className="px-4 py-2 text-left font-medium">
                {col("geography")}
              </th>
              <th className="px-4 py-2 text-right font-medium">
                {col("inventories")}
              </th>
              <th className="px-4 py-2 text-right font-medium">
                {col("impressions")}
              </th>
              <th className="px-4 py-2 text-right font-medium">
                {col("reach")}
              </th>
              <th className="px-4 py-2 text-right font-medium">
                {col("frequency")}
              </th>
              <th className="px-4 py-2 text-right font-medium">
                {tCampaigns(
                  "mediaPlanAnalytics.geographyTargeting.columns.ecpm",
                  {
                    currency: currencyLabel,
                  },
                )}
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td
                  colSpan={6}
                  className="px-4 py-6 text-center text-mw-neutral-500"
                >
                  {tCampaigns("mediaPlanAnalytics.geographyTargeting.noData")}
                </td>
              </tr>
            ) : (
              rows.map((row) => (
                <tr
                  key={row.id}
                  id={`media-plan-analytics-geography-targeting-row-${row.id}`}
                  className="border-t"
                  style={{
                    borderColor: BORDER_COLOR,
                    background:
                      row.level === "country"
                        ? "rgb(250, 251, 252)"
                        : "transparent",
                  }}
                >
                  <td
                    className={
                      row.level === "country"
                        ? "px-4 py-2 font-semibold"
                        : "px-4 py-2"
                    }
                    style={{ paddingLeft: 16 + row.depth * DEPTH_INDENT_PX }}
                  >
                    {row.name}
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {row.inventories}
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {formatNumber(row.impressions)}
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {formatNumber(row.reach)}
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {row.frequency.toFixed(2)}
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {row.ecpm.toFixed(2)}
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

export default GeographyTargetingTab;
