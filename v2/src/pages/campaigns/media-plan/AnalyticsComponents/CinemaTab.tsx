import { useTranslate } from "@tolgee/react";
import { formatCompactNumber } from "@utils/dashboard.utils";
import React from "react";

import type { AnalyticsExcelData, CinemaInventoryRow } from "../analyticsTypes";

interface CinemaTabProps {
  analyticsData: AnalyticsExcelData;
}

const BORDER_COLOR = "rgb(226, 232, 240)";

/**
 * Cinema analytics table. Cinema is bought by operator/hall/showtime-window
 * with genre/rating constraints — films are only an indicative read-only
 * preview, never a buy unit. Rendered only when cinema rows exist (gated by
 * AnalyticsView, mirrored in the Excel/PPT exports).
 */
const CinemaTab: React.FC<CinemaTabProps> = ({ analyticsData }) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const cinemaData: CinemaInventoryRow[] = analyticsData.cinemaInventory || [];
  const currencyLabel = analyticsData.campaignDetails?.currency ?? "";

  const col = (key: string, params?: Record<string, string | number>) =>
    tCampaigns(`mediaPlanAnalytics.cinema.columns.${key}`, params);

  return (
    <section
      id="media-plan-analytics-cinema-card"
      className="overflow-hidden rounded-md border bg-white"
      style={{ borderColor: BORDER_COLOR }}
    >
      <div
        id="media-plan-analytics-cinema-header"
        className="px-4 py-2 text-sm font-semibold text-white"
        style={{ background: "rgb(37, 99, 235)" }}
      >
        {tCampaigns("mediaPlanAnalytics.cinema.title", {
          count: cinemaData.length,
        })}
      </div>

      {/* Indicative film line-up note — the buy is operator/hall/showtime. */}
      <div
        id="media-plan-analytics-cinema-note"
        className="px-4 py-2 text-xs italic text-mw-neutral-500"
        style={{ background: "rgba(37, 99, 235, 0.04)" }}
      >
        {tCampaigns("mediaPlanAnalytics.cinema.indicativeNote")}
      </div>

      <div id="media-plan-analytics-cinema-table" className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead style={{ background: "rgba(37, 99, 235, 0.063)" }}>
            <tr>
              <th className="px-4 py-2 text-left font-medium">
                {col("inventory")}
              </th>
              <th className="px-4 py-2 text-left font-medium">
                {col("operator")}
              </th>
              <th className="px-4 py-2 text-left font-medium">
                {col("cinema")}
              </th>
              <th className="px-4 py-2 text-left font-medium">{col("hall")}</th>
              <th className="px-4 py-2 text-left font-medium">
                {col("showtimeWindows")}
              </th>
              <th className="px-4 py-2 text-left font-medium">
                {col("genres")}
              </th>
              <th className="px-4 py-2 text-left font-medium">
                {col("ratings")}
              </th>
              <th className="px-4 py-2 text-right font-medium">
                {col("impressions")}
              </th>
              <th className="px-4 py-2 text-right font-medium">
                {col("cpm", { currency: currencyLabel })}
              </th>
            </tr>
          </thead>
          <tbody>
            {cinemaData.length === 0 ? (
              <tr>
                <td
                  colSpan={9}
                  className="px-4 py-6 text-center text-mw-neutral-500"
                >
                  {tCampaigns("mediaPlanAnalytics.cinema.noData")}
                </td>
              </tr>
            ) : (
              cinemaData.map((row) => (
                <tr
                  key={row.id}
                  id={`media-plan-analytics-cinema-row-${row.id}`}
                  className="border-t"
                  style={{ borderColor: BORDER_COLOR }}
                >
                  <td className="px-4 py-2 font-medium">{row.name || "-"}</td>
                  <td className="px-4 py-2">{row.operator || "-"}</td>
                  <td className="px-4 py-2">{row.cinemaName || "-"}</td>
                  <td className="px-4 py-2">{row.hall || "-"}</td>
                  <td className="px-4 py-2">{row.showtimeWindows || "-"}</td>
                  <td className="px-4 py-2">{row.genres || "-"}</td>
                  <td className="px-4 py-2">{row.ratings || "-"}</td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {formatCompactNumber(row.impressions || 0, 1)}
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {(row.cpm || 0).toFixed(2)}
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

export default CinemaTab;
