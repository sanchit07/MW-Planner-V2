import { Tooltip } from "@components/ui/Tooltip";
import { TOOLTIP_CONTENT } from "@constants/tooltip.constants";
import { useTranslate } from "@tolgee/react";
import { formatCurrencyWithLocale } from "@utils/currency";
import { Info } from "lucide-react";
import React from "react";

import type { CampaignForecastData } from "../../../../types/inventory.types";

interface ForecastTilesProps {
  forecastData: CampaignForecastData;
  campaignCurrency: string | undefined;
  /** Campaign's total budget — used to compute the Remaining tile. */
  budget?: number | null;
}

/** The forecast metric tiles shown at the top of the Plan Summary. */
const ForecastTiles: React.FC<ForecastTilesProps> = ({
  forecastData,
  campaignCurrency,
  budget,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);

  const hasCo2Data = Boolean(forecastData?.co2PerPlay);
  const co2Tooltip = hasCo2Data
    ? tCampaigns(TOOLTIP_CONTENT.planSummary.co2PerPlay)
    : `${tCampaigns(TOOLTIP_CONTENT.planSummary.co2PerPlay)} ${tCampaigns(
        TOOLTIP_CONTENT.planSummary.co2PerPlayNoData,
      )}`;

  const sotPercent =
    forecastData?.totalSot && forecastData.totalSot > 0
      ? ((forecastData.plannedSot / forecastData.totalSot) * 100).toFixed(1)
      : "0";

  const remaining = (budget ?? 0) - (forecastData?.totalCost ?? 0);

  const tiles: {
    label: string;
    value: string;
    beta?: boolean;
    tooltip?: string;
  }[] = [
    {
      label: tCampaigns("inventories.planSummary.tiles.inventories"),
      value: (forecastData?.totalInventories ?? 0).toLocaleString(),
      tooltip: tCampaigns(TOOLTIP_CONTENT.planSummary.inventories),
    },
    {
      label: tCampaigns("inventories.planSummary.tiles.impressions"),
      value: (forecastData?.estimatedImpression ?? 0).toLocaleString(),
      tooltip: tCampaigns(TOOLTIP_CONTENT.planSummary.impressions),
    },
    {
      label: tCampaigns("inventories.planSummary.tiles.reach"),
      value: (forecastData?.estimatedReach ?? 0).toLocaleString(),
      tooltip: tCampaigns(TOOLTIP_CONTENT.planSummary.reach),
    },
    {
      label: tCampaigns("inventories.planSummary.tiles.frequency"),
      value: `${(forecastData?.estimatedFrequency ?? 0).toFixed(1)}x`,
      tooltip: tCampaigns(TOOLTIP_CONTENT.planSummary.frequency),
    },
    {
      label: tCampaigns("inventories.planSummary.tiles.ad_plays"),
      value: (forecastData?.estimatedAdPlays ?? 0).toLocaleString(),
      tooltip: tCampaigns(TOOLTIP_CONTENT.planSummary.adPlays),
    },
    {
      label: tCampaigns("inventories.planSummary.tiles.co2_per_play"),
      value: `${(forecastData?.co2PerPlay ?? 0).toFixed(3)} kg`,
      beta: true,
      tooltip: co2Tooltip,
    },
    {
      label: tCampaigns("inventories.planSummary.tiles.avg_cpm"),
      value: formatCurrencyWithLocale(
        isNaN(forecastData?.avgCpm) ? 0 : forecastData?.avgCpm,
        campaignCurrency,
      ),
      tooltip: tCampaigns(TOOLTIP_CONTENT.planSummary.avgCpm),
    },
    {
      label: tCampaigns("inventories.planSummary.tiles.ecpm"),
      value: formatCurrencyWithLocale(
        isNaN(forecastData?.avgECpm) ? 0 : forecastData?.avgECpm,
        campaignCurrency,
      ),
      tooltip: tCampaigns(TOOLTIP_CONTENT.planSummary.eCpm),
    },
    {
      label: tCampaigns("inventories.planSummary.tiles.sov"),
      value: `${(forecastData?.sov ?? 0).toFixed(2)}%`,
      tooltip: tCampaigns(TOOLTIP_CONTENT.planSummary.sov),
    },
    {
      label: tCampaigns("inventories.planSummary.tiles.sot"),
      value: `${forecastData?.plannedSot?.toFixed(2) ?? "-"}H`,
      tooltip: tCampaigns(TOOLTIP_CONTENT.planSummary.sot, {
        percent: sotPercent,
      }),
    },
    {
      label: tCampaigns("inventories.planSummary.tiles.cost"),
      value: formatCurrencyWithLocale(
        forecastData?.totalCost ?? 0,
        campaignCurrency,
      ),
      tooltip: tCampaigns(TOOLTIP_CONTENT.planSummary.cost),
    },
    {
      label: tCampaigns("inventories.planSummary.tiles.remaining"),
      value: formatCurrencyWithLocale(remaining, campaignCurrency),
      tooltip: tCampaigns(TOOLTIP_CONTENT.planSummary.remaining),
    },
  ];

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
      {tiles.map((tile) => (
        <div
          key={tile.label}
          className="rounded-lg border border-mw-neutral-100 bg-white px-3 py-2"
        >
          <div className="flex items-center gap-1 mb-1.5">
            <p className="text-xs font-semibold text-mw-grey-800 leading-none truncate">
              {tile.label}
            </p>
            {tile.tooltip && (
              <Tooltip content={tile.tooltip}>
                <Info className="size-3 text-mw-neutral-400 cursor-help shrink-0" />
              </Tooltip>
            )}
            {tile.beta && (
              <span className="shrink-0 rounded-full border border-mw-primary-200 bg-mw-primary-50 px-1.5 py-px text-[9px] font-semibold uppercase leading-none tracking-wide text-mw-primary-600">
                {tCampaigns("inventories.planSummary.tiles.beta")}
              </span>
            )}
          </div>
          <p className="text-sm font-semibold text-[#2563eb] leading-tight truncate">
            {tile.value}
          </p>
        </div>
      ))}
    </div>
  );
};

export default ForecastTiles;
