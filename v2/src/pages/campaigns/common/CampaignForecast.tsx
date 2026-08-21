import { Card, CardContent, CardHeader } from "@components/ui/card";
import { useTranslate } from "@tolgee/react";
import { normalizeGoalType } from "@utils/budget.utils";
import { formatCurrencyWithLocale } from "@utils/currency";
import React, { useEffect, useState } from "react";

import { CampaignForecastData } from "../../../types/inventory.types";

interface CampaignForecastProps {
  campaignCurrency: string | undefined;
  forecastDataValue: CampaignForecastData;
  showSovSot?: boolean;
  goalType?: string;
}

const CampaignForecast: React.FC<CampaignForecastProps> = ({
  campaignCurrency,
  forecastDataValue,
  showSovSot = true,
  goalType,
}) => {
  const normalizedGoalType = normalizeGoalType(goalType);
  const hideAvgCpm =
    normalizedGoalType === "SOV" || normalizedGoalType === "ADPLAYS";
  const { t: tCampaigns } = useTranslate("campaigns");

  const [forecastData, setForecastDataValue] =
    useState<CampaignForecastData>(forecastDataValue);

  useEffect(() => {
    setForecastDataValue(forecastDataValue);
  }, [forecastDataValue]);

  return (
    <Card>
      <CardHeader className="p-4">
        <p className="justify-start text-sm font-medium leading-none gap-2">
          {tCampaigns("inventories.forecast.title")}
        </p>
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="self-stretch flex flex-col justify-start items-start gap-5">
          <div className="self-stretch inline-flex justify-between items-center flex-wrap content-center">
            <p className="text-mw-neutral-500 text-sm leading-none">
              {tCampaigns("inventories.forecast.est_impressions")}
            </p>
            <div className=" flex justify-end items-center gap-2.5">
              <p className="text-mw-grey-800 text-sm font-medium leading-none">
                {forecastData?.estimatedImpression?.toLocaleString() || "-"}
              </p>
            </div>
          </div>
        </div>
        <div className="self-stretch flex flex-col justify-start items-start gap-5">
          <div className="self-stretch inline-flex justify-between items-center flex-wrap content-center">
            <p className="text-mw-neutral-500 text-sm leading-none">
              {tCampaigns("inventories.forecast.est_reach")}
            </p>
            <div className=" flex justify-end items-center gap-2.5">
              <p className="text-mw-grey-800 text-sm font-medium leading-none">
                {forecastData?.estimatedReach?.toLocaleString() || "-"}
              </p>
            </div>
          </div>
        </div>
        <div className="self-stretch flex flex-col justify-start items-start gap-5">
          <div className="self-stretch inline-flex justify-between items-center flex-wrap content-center">
            <p className="text-mw-neutral-500 text-sm leading-none">
              {tCampaigns("inventories.forecast.avg_frequency")}
            </p>
            <div className=" flex justify-end items-center gap-2.5">
              <p className="text-mw-grey-800 text-sm font-medium leading-none">
                {forecastData?.estimatedFrequency?.toFixed(2) || "-"}
              </p>
            </div>
          </div>
        </div>
        <div className="self-stretch flex flex-col justify-start items-start gap-5">
          <div className="self-stretch inline-flex justify-between items-center flex-wrap content-center">
            <p className="text-mw-neutral-500 text-sm leading-none">
              {tCampaigns("inventories.forecast.est_ad_plays")}
            </p>
            <div className=" flex justify-end items-center gap-2.5">
              <p className="text-mw-grey-800 text-sm font-medium leading-none">
                {forecastData?.estimatedAdPlays?.toLocaleString() || "-"}
              </p>
            </div>
          </div>
        </div>
        {showSovSot && (
          <div className="self-stretch flex flex-col justify-start items-start gap-5">
            <div className="self-stretch inline-flex justify-between items-center flex-wrap content-center">
              <p className="text-mw-neutral-500 text-sm leading-none">
                {tCampaigns("inventories.forecast.sov_percentage")}
              </p>
              <div className=" flex justify-end items-center gap-2.5">
                <p className="text-mw-grey-800 text-sm font-medium leading-none">
                  {forecastData?.sov?.toFixed(1) || "-"}%
                </p>
              </div>
            </div>
          </div>
        )}
        {!hideAvgCpm && (
          <div className="self-stretch flex flex-col justify-start items-start gap-5">
            <div className="self-stretch inline-flex justify-between items-center flex-wrap content-center">
              <p className="text-mw-neutral-500 text-sm leading-none">
                {tCampaigns("inventories.forecast.avg_cpm")}
              </p>
              <div className=" flex justify-end items-center gap-2.5">
                <p className="text-mw-grey-800 text-sm font-medium leading-none">
                  {formatCurrencyWithLocale(
                    isNaN(forecastData.avgCpm) ? 0 : forecastData?.avgCpm,
                    campaignCurrency,
                  )}
                </p>
              </div>
            </div>
          </div>
        )}
        <div className="self-stretch flex flex-col justify-start items-start gap-5">
          <div className="self-stretch inline-flex justify-between items-center flex-wrap content-center">
            <p className="text-mw-neutral-500 text-sm leading-none">
              {tCampaigns("inventories.forecast.avg_ecpm")}
            </p>
            <div className=" flex justify-end items-center gap-2.5">
              <p className="text-mw-grey-800 text-sm font-medium leading-none">
                {formatCurrencyWithLocale(
                  forecastData?.avgECpm,
                  campaignCurrency,
                )}
              </p>
            </div>
          </div>
        </div>
        <div className="self-stretch flex flex-col justify-start items-start gap-5">
          <div className="self-stretch inline-flex justify-between items-center flex-wrap content-center">
            <p className="text-mw-neutral-500 text-sm leading-none">
              {tCampaigns("inventories.forecast.total_cost")}
            </p>
            <div className=" flex justify-end items-center gap-2.5">
              <p className="text-mw-grey-800 text-sm font-medium leading-none">
                {formatCurrencyWithLocale(
                  forecastData?.totalCost,
                  campaignCurrency,
                )}
              </p>
            </div>
          </div>
        </div>
        {showSovSot && (
          <div className="self-stretch flex flex-col justify-start items-start gap-5">
            <div className="self-stretch inline-flex justify-between items-center flex-wrap content-center">
              <p className="text-mw-neutral-500 text-sm leading-none">
                {tCampaigns("inventories.forecast.sot")}
              </p>
              <div className=" flex justify-end items-center gap-2.5">
                <p className="text-mw-grey-800 text-sm font-medium leading-none">
                  {`${forecastData?.plannedSot?.toFixed(2) ?? "-"}H out of ${forecastData?.totalSot ?? "-"} H`}
                </p>
              </div>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
};

export default CampaignForecast;
