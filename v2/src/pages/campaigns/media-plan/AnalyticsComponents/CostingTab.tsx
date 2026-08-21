import { useTranslate } from "@tolgee/react";
import { formatCurrency } from "@utils/campaign.utils";
import { formatCompactNumber } from "@utils/dashboard.utils";
import React from "react";

import type {
  AnalyticsExcelData,
  CostingInventoryRow,
} from "../analyticsTypes";

interface CostingTabProps {
  analyticsData: AnalyticsExcelData;
}

const CostingTab: React.FC<CostingTabProps> = ({ analyticsData }) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const rows: CostingInventoryRow[] = analyticsData.costingInventoryRows || [];
  const currencyLabel = analyticsData.campaignDetails?.currency ?? "";

  const col = (key: string) =>
    tCampaigns(`mediaPlanAnalytics.costing.columns.${key}`);

  const totalMediaCost = rows.reduce((sum, row) => sum + row.mediaCost, 0);
  const totalFeeShare = rows.reduce((sum, row) => sum + row.feeShare, 0);
  const totalCost = rows.reduce((sum, row) => sum + row.total, 0);

  return (
    <section
      id="media-plan-analytics-costing-card"
      className="overflow-hidden rounded-md border bg-white"
      style={{ borderColor: "rgb(226, 232, 240)" }}
    >
      <div
        id="media-plan-analytics-costing-header"
        className="px-4 py-2 text-sm font-semibold text-white"
        style={{ background: "rgb(37, 99, 235)" }}
      >
        {tCampaigns("mediaPlanAnalytics.costing.title")}
      </div>
      <div id="media-plan-analytics-costing-table" className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead style={{ background: "rgba(37, 99, 235, 0.063)" }}>
            <tr>
              <th className="px-4 py-2 text-left font-medium">
                {col("inventory")}
              </th>
              <th className="px-4 py-2 text-left font-medium">{col("city")}</th>
              <th className="px-4 py-2 text-right font-medium">
                {col("baseCpm")}
              </th>
              <th className="px-4 py-2 text-right font-medium">
                {col("impressions")}
              </th>
              <th className="px-4 py-2 text-right font-medium">
                {col("mediaCost")}
              </th>
              <th className="px-4 py-2 text-right font-medium">
                {col("feeShare")}
              </th>
              <th className="px-4 py-2 text-right font-medium">
                {col("total")}
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td
                  colSpan={7}
                  className="px-4 py-6 text-center text-mw-neutral-500"
                >
                  {tCampaigns("mediaPlanAnalytics.costing.noData")}
                </td>
              </tr>
            ) : (
              <>
                {rows.map((row) => (
                  <tr
                    key={row.id}
                    id={`media-plan-analytics-costing-row-${row.id}`}
                    className="border-t"
                    style={{ borderColor: "rgb(226, 232, 240)" }}
                  >
                    <td className="px-4 py-2 font-medium">{row.name}</td>
                    <td
                      className="px-4 py-2"
                      style={{ color: "rgb(100, 116, 139)" }}
                    >
                      {row.city}
                    </td>
                    <td className="px-4 py-2 text-right tabular-nums">
                      {row.baseCpm.toFixed(2)}
                    </td>
                    <td className="px-4 py-2 text-right tabular-nums">
                      {formatCompactNumber(row.impressions, 1)}
                    </td>
                    <td className="px-4 py-2 text-right tabular-nums">
                      {formatCurrency(row.mediaCost, currencyLabel, 2)}
                    </td>
                    <td className="px-4 py-2 text-right tabular-nums">
                      {formatCurrency(row.feeShare, currencyLabel, 2)}
                    </td>
                    <td className="px-4 py-2 text-right font-semibold tabular-nums">
                      {formatCurrency(row.total, currencyLabel, 2)}
                    </td>
                  </tr>
                ))}
                <tr
                  id="media-plan-analytics-costing-totals-row"
                  className="border-t"
                  style={{
                    borderColor: "rgb(226, 232, 240)",
                    background: "rgba(37, 99, 235, 0.03)",
                  }}
                >
                  <td className="px-4 py-2 font-semibold" colSpan={4}>
                    {tCampaigns("mediaPlanAnalytics.costing.totals")}
                  </td>
                  <td className="px-4 py-2 text-right font-semibold tabular-nums">
                    {formatCurrency(totalMediaCost, currencyLabel, 2)}
                  </td>
                  <td className="px-4 py-2 text-right font-semibold tabular-nums">
                    {formatCurrency(totalFeeShare, currencyLabel, 2)}
                  </td>
                  <td className="px-4 py-2 text-right font-semibold tabular-nums">
                    {formatCurrency(totalCost, currencyLabel, 2)}
                  </td>
                </tr>
              </>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
};

export default CostingTab;
