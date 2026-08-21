import { Badge } from "@components/ui/Badge";
import { Card, CardHeader, CardTitle, CardContent } from "@components/ui/card";
import { DynamicProgressBar } from "@components/ui/DynamicProgressBar";
import { useTranslate } from "@tolgee/react";
import { formatNumber, normalizeGoalType } from "@utils/budget.utils";
import { Square, PackageOpenIcon } from "lucide-react";
import React from "react";

const KNOWN_GOAL_TYPES = ["IMPRESSIONS", "REACH", "SOV", "ADPLAYS"];

function goalTypeLabel(
  goalType: string | undefined,
  tCampaigns: (key: string) => string,
): string | undefined {
  if (!goalType) return undefined;
  const key = normalizeGoalType(goalType);
  return key && KNOWN_GOAL_TYPES.includes(key)
    ? tCampaigns(`campaignsList.goalTypes.${key}`)
    : goalType;
}

interface CampaignGoalViewProps {
  campaignId: string | undefined;
  goals?: {
    goalType: string;
    targetValue: number;
    achievedValue: number;
    weeklyBreakdown?: {
      [key: string]: number;
    };
  };
}

const CampaignGoalView: React.FC<CampaignGoalViewProps> = ({ goals }) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  // Calculate percentage difference
  const calculatePercentage = () => {
    if (!goals || !goals.targetValue || goals.targetValue === 0) return null;
    const percentage = (goals.achievedValue / goals.targetValue) * 100 - 100;
    return percentage;
  };

  // Calculate progress value for DynamicProgressBar
  // Target value is considered as 100%, values above are rounded to increments of 50
  const calculateProgressValue = () => {
    if (!goals || !goals.targetValue || goals.targetValue === 0) {
      return { value: 0, maxValue: 100 };
    }

    const percentage = (goals.achievedValue / goals.targetValue) * 100;

    // If percentage is <= 100, return as is
    if (percentage <= 100) {
      return { value: percentage, maxValue: 100 };
    }

    // If percentage > 100, round to nearest 50 (150, 200, 250, etc.)
    const roundedMax = Math.ceil(percentage / 50) * 50;

    return { value: percentage, maxValue: roundedMax };
  };

  const percentageDiff = calculatePercentage();
  const progressValues = calculateProgressValue();

  // Check if goal data exists
  const hasGoalData =
    goals &&
    goals.goalType &&
    goals.targetValue !== undefined &&
    goals.achievedValue !== undefined;

  return (
    <Card className="p-4">
      <CardHeader>
        <CardTitle className="inline-flex justify-start items-center gap-2 text-base font-medium border-b border-container-border pb-4 leading-5">
          <p>
            {tCampaigns("viewCampaign.campaignGoalView.selectedCampaignGoal")}
          </p>

          {hasGoalData && (
            <Badge variant="secondary" size="md">
              {goalTypeLabel(goals.goalType, tCampaigns)}
            </Badge>
          )}
        </CardTitle>
      </CardHeader>
      <CardContent className="pt-4 pl-0 pr-0 space-y-4 pb-0">
        {!hasGoalData ? (
          <div className="w-full self-stretch h-80 inline-flex flex-col justify-center items-center gap-4">
            <PackageOpenIcon className="size-24 text-mw-neutral-300 " />
            <p className="self-stretch text-center justify-start text-Background-Inverse text-base font-semibold  leading-5">
              {tCampaigns("viewCampaign.campaignGoalView.noGoalSelected")}
            </p>
            <p className="text-center justify-start text-Background-Inverse text-sm font-normal  leading-5">
              {tCampaigns("viewCampaign.campaignGoalView.noMetricsToShow")}
            </p>
          </div>
        ) : (
          <>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Card className="p-4">
                <CardHeader>
                  <CardTitle className="inline-flex justify-start items-center gap-1.5 text-base font-medium border-b border-container-border pb-4 leading-5">
                    <p>
                      {tCampaigns(
                        "viewCampaign.campaignGoalView.targetVsPlanned",
                      )}
                    </p>

                    {percentageDiff !== null && percentageDiff > 0 && (
                      <Badge variant="success" size="md">
                        +{percentageDiff.toFixed(0)}%{" "}
                        {tCampaigns("viewCampaign.campaignGoalView.overTarget")}
                      </Badge>
                    )}
                    {percentageDiff !== null && percentageDiff <= 0 && (
                      <Badge variant="secondary" size="md">
                        {percentageDiff.toFixed(0)}%{" "}
                        {tCampaigns(
                          "viewCampaign.campaignGoalView.underTarget",
                        )}
                      </Badge>
                    )}
                  </CardTitle>
                </CardHeader>
                <CardContent className="mt-4 space-y-4">
                  <div className="flex flex-col gap-6">
                    <DynamicProgressBar
                      value={progressValues.value}
                      maxValue={progressValues.maxValue}
                      variant="default"
                      beyondColor="bg-mw-success-500"
                      label={tCampaigns(
                        "viewCampaign.campaignGoalView.plannedProgressToTarget",
                      )}
                      infoText={tCampaigns(
                        "viewCampaign.campaignGoalView.progressInfoText",
                      )}
                      size="md"
                    />
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-20">
                    <Card className="p-4">
                      <div className="space-y-4">
                        <div className="self-stretch inline-flex justify-start items-center gap-2">
                          <Square className="size-4 text-mw-primary-500 bg-mw-primary-500" />
                          <p className="text-sm font-normal leading-4">
                            {tCampaigns(
                              "viewCampaign.campaignGoalView.yourTarget",
                            )}
                          </p>
                        </div>

                        <p className="text-lg font-semibold text-mw-primary-500 leading-6">
                          {goals?.targetValue
                            ? formatNumber(goals.targetValue)
                            : "--"}
                        </p>

                        <p className="text-sm font-normal text-mw-neutral-500 leading-4">
                          {tCampaigns("viewCampaign.campaignGoalView.target")}{" "}
                          {goals?.goalType
                            ? goalTypeLabel(goals.goalType, tCampaigns)
                            : tCampaigns(
                                "viewCampaign.campaignGoalView.impression",
                              )}
                        </p>
                      </div>
                    </Card>
                    <Card className="p-4">
                      <div className="space-y-4">
                        <div className="self-stretch inline-flex justify-start items-center gap-2">
                          <Square className="size-4 text-mw-success-500 bg-mw-success-500" />
                          <p className="text-sm font-normal leading-4">
                            {tCampaigns(
                              "viewCampaign.campaignGoalView.plannedDelivery",
                            )}
                          </p>
                        </div>

                        <p className="text-lg font-semibold text-mw-success-500 leading-6">
                          {goals?.achievedValue
                            ? formatNumber(goals.achievedValue)
                            : "--"}
                        </p>

                        <p className="text-sm font-normal text-mw-neutral-500 leading-4">
                          {tCampaigns(
                            "viewCampaign.campaignGoalView.projected",
                          )}{" "}
                          {goals?.goalType
                            ? goalTypeLabel(goals.goalType, tCampaigns)
                            : tCampaigns(
                                "viewCampaign.campaignGoalView.noGoal",
                              )}
                        </p>
                      </div>
                    </Card>
                  </div>
                </CardContent>
              </Card>
              <Card className="p-4">
                <CardHeader>
                  <CardTitle className="inline-flex justify-start items-center gap-2 text-base font-medium border-b border-container-border pb-4 leading-5">
                    <p>
                      {tCampaigns(
                        "viewCampaign.campaignGoalView.expectedGoalAchievement",
                      )}
                    </p>
                  </CardTitle>
                </CardHeader>
                <CardContent className="mt-4 space-y-4 max-h-80 overflow-y-auto scrollbar-thin scrollbar-thumb-mw-neutral-300 scrollbar-track-mw-neutral-100">
                  {goals?.weeklyBreakdown &&
                  Object.keys(goals.weeklyBreakdown).length > 0 ? (
                    Object.entries(goals.weeklyBreakdown).map(
                      ([week, percentage]) => (
                        <Card key={week} className="p-4">
                          <CardHeader>
                            <CardTitle>
                              <p className="text-base font-semibold text-mw-neutral-700 leading-5">
                                {week}
                              </p>
                            </CardTitle>
                          </CardHeader>
                          <CardContent className="pt-2 pl-0 pr-0 pb-0">
                            {percentage && !isNaN(percentage) && (
                              <p
                                className={`text-sm font-normal leading-4 ${
                                  percentage >= 100
                                    ? "text-mw-success-500"
                                    : "text-mw-neutral-500"
                                }`}
                              >
                                {percentage > 100
                                  ? tCampaigns(
                                      "viewCampaign.campaignGoalView.expectedOverdeliver",
                                      { n: percentage.toFixed(2) },
                                    )
                                  : percentage === 100
                                    ? tCampaigns(
                                        "viewCampaign.campaignGoalView.expectedReachTarget",
                                      )
                                    : tCampaigns(
                                        "viewCampaign.campaignGoalView.expectedReachPartial",
                                        {
                                          n: percentage.toFixed(2),
                                          goalType:
                                            goals?.goalType?.toLowerCase() ||
                                            "impressions",
                                        },
                                      )}
                              </p>
                            )}
                          </CardContent>
                        </Card>
                      ),
                    )
                  ) : (
                    <p className="text-sm text-mw-neutral-500 text-center py-4">
                      {tCampaigns(
                        "viewCampaign.campaignGoalView.noGoalAchievement",
                      )}
                    </p>
                  )}
                </CardContent>
              </Card>
            </div>
            <Card className="p-4">
              <CardHeader>
                <CardTitle className="inline-flex justify-start items-center gap-2 text-base font-medium border-b border-container-border pb-4 leading-5">
                  <p>
                    {tCampaigns(
                      "viewCampaign.campaignGoalView.howYourPlanAchieves",
                    )}
                  </p>
                </CardTitle>
              </CardHeader>
              <CardContent className="pt-4 pl-0 pr-0 space-y-4 pb-0">
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <Card className="p-4">
                    <CardHeader>
                      <CardTitle>
                        <p className="text-base font-semibold text-mw-neutral-700 leading-5">
                          {tCampaigns(
                            "viewCampaign.campaignGoalView.strategies.highTraffic.title",
                          )}
                        </p>
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="pt-2 pl-0 pr-0 pb-0">
                      <p className="text-mw-neutral-500 text-sm font-normal leading-4">
                        {tCampaigns(
                          "viewCampaign.campaignGoalView.strategies.highTraffic.description",
                        )}
                      </p>
                    </CardContent>
                  </Card>
                  <Card className="p-4">
                    <CardHeader>
                      <CardTitle>
                        <p className="text-base font-semibold text-mw-neutral-700 leading-5">
                          {tCampaigns(
                            "viewCampaign.campaignGoalView.strategies.formatMix.title",
                          )}
                        </p>
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="pt-2 pl-0 pr-0 pb-0">
                      <p className="text-mw-neutral-500 text-sm font-normal leading-4">
                        {tCampaigns(
                          "viewCampaign.campaignGoalView.strategies.formatMix.description",
                        )}
                      </p>
                    </CardContent>
                  </Card>
                  <Card className="p-4">
                    <CardHeader>
                      <CardTitle>
                        <p className="text-base font-semibold text-mw-neutral-700 leading-5">
                          {tCampaigns(
                            "viewCampaign.campaignGoalView.strategies.overDelivery.title",
                          )}
                        </p>
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="pt-2 pl-0 pr-0 pb-0">
                      <p className="text-mw-neutral-500 text-sm font-normal leading-4">
                        {tCampaigns(
                          "viewCampaign.campaignGoalView.strategies.overDelivery.description",
                        )}
                      </p>
                    </CardContent>
                  </Card>
                </div>
              </CardContent>
            </Card>
          </>
        )}
      </CardContent>
    </Card>
  );
};
export default CampaignGoalView;
