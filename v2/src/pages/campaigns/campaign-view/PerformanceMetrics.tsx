import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import { Progress } from "@components/ui/Progressbar";
import { Tooltip } from "@components/ui/Tooltip";
import { TOOLTIP_CONTENT } from "@constants/tooltip.constants";
import { useTranslate } from "@tolgee/react";
import { formatCurrencyWithLocale } from "@utils/currency";
import { cn } from "@utils/tailwindMerge";
import { Info } from "lucide-react";

import { ViewCampaign } from "../../../types/campaign.types";
import { formatNumber } from "../../../utils/budget.utils";

interface MetricCardProps {
  title: string;
  value: string;
  subtitle?: string;
  valueColor:
    | "default"
    | "primary"
    | "success"
    | "warning"
    | "error"
    | "teal"
    | "lightGreen"
    | "orange";
  bgColor?:
    | "default"
    | "success"
    | "warning"
    | "error"
    | "primary"
    | "secondary"
    | "cyan"
    | "lime"
    | "peach";
  infoIcon?: boolean;
  tooltipContent?: string;
  description?: string;
  progressBar?: {
    percentage: number;
    color:
      | "primary"
      | "success"
      | "warning"
      | "error"
      | "teal"
      | "lightGreen"
      | "orange";
  };
  campaignCurrency?: string;
}

const MetricCard: React.FC<MetricCardProps> = ({
  title,
  value,
  subtitle = "",
  valueColor,
  bgColor,
  infoIcon = false,
  tooltipContent,
  description,
  progressBar,
}) => {
  const valueColorClasses = {
    primary: "text-mw-primary-500 dark:text-mw-primary-500",
    default: "text-mw-primary-500 dark:text-mw-primary-500",
    success: "text-mw-success-500 dark:text-mw-success-500",
    warning: "text-mw-warning-500 dark:text-mw-warning-500",
    error: "text-mw-error-500 dark:text-mw-error-500",
    teal: "text-mw-teal-600 dark:text-mw-teal-600",
    lightGreen: "text-mw-light-green-600 dark:text-mw-light-green-600",
    orange: "text-mw-orange-600 dark:text-mw-orange-600",
  };

  const bgColorClasses = {
    default: "!bg-mw-primary-50 dark:!bg-mw-primary-900/20",
    success: "!bg-mw-success-50 dark:!bg-mw-success-900/20",
    warning: "!bg-mw-warning-50 dark:!bg-mw-warning-900/20",
    error: "!bg-mw-error-50 dark:!bg-mw-error-900/20",
    primary: "!bg-mw-primary-50 dark:!bg-mw-primary-900/20",
    secondary: "!bg-mw-secondary-50 dark:!bg-mw-secondary-900/20",
    cyan: "!bg-mw-cyan-50 dark:!bg-mw-cyan-50",
    lime: "!bg-mw-lime-50 dark:!bg-mw-lime-50",
    peach: "!bg-mw-peach-50 dark:!bg-mw-peach-50",
  };

  const selectedBgColor = bgColor
    ? bgColorClasses[bgColor as keyof typeof bgColorClasses] ||
      bgColorClasses.default
    : valueColor === "default" ||
        valueColor === "success" ||
        valueColor === "warning" ||
        valueColor === "error"
      ? bgColorClasses[valueColor]
      : bgColorClasses.default;

  return (
    <Card className={cn("p-0 overflow-hidden", selectedBgColor)}>
      <div className="p-4 space-y-3 h-full">
        <div className="flex items-start justify-between">
          <div className="flex-1 min-w-0">
            <p className="text-sm font-semibold text-mw-neutral-700 dark:text-mw-neutral-300 mb-1 leading-4">
              {title}
            </p>
            <p
              className={cn(
                "text-lg font-semibold leading-6",
                valueColorClasses[valueColor],
              )}
            >
              {value}
            </p>
            {subtitle && (
              <p className="text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-400 mt-1 leading-4">
                {subtitle}
              </p>
            )}
          </div>
          {infoIcon && (
            <Tooltip
              content={tooltipContent ?? ""}
              className="whitespace-normal w-44 break-words"
            >
              <Info className="h-4 w-4 text-mw-neutral-500 shrink-0 ml-2 cursor-help" />
            </Tooltip>
          )}
        </div>

        {progressBar && (
          <div className="space-y-2">
            <Progress
              value={progressBar?.percentage}
              showPercentage={false}
              variant={progressBar.color}
              progressTrackVariant="white"
            />
            {description && (
              <p className="text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-400 leading-4">
                {description}
              </p>
            )}
          </div>
        )}
      </div>
    </Card>
  );
};

interface PerformanceMetricsProps {
  forecastData?: ViewCampaign["performance"];
  campaignCurrency?: string;
}

const PerformanceMetrics: React.FC<PerformanceMetricsProps> = ({
  forecastData,
  campaignCurrency,
}) => {
  const { t: tCampaigns } = useTranslate("campaigns");
  // Calculate metrics from API data
  const metrics = forecastData
    ? {
        totalImpressions: {
          title: tCampaigns(
            "performanceMetrics.metrics.totalImpressions.title",
          ),
          value: formatNumber(forecastData.estimatedImpression),
          subtitle: tCampaigns(
            "performanceMetrics.metrics.totalImpressions.subtitle",
          ),
          color: "default" as const,
        },
        estimatedReach: {
          title: tCampaigns("performanceMetrics.metrics.estimatedReach.title"),
          value: formatNumber(forecastData.estimatedReach),
          subtitle: tCampaigns(
            "performanceMetrics.metrics.estimatedReach.subtitle",
          ),
          color: "success" as const,
        },
        frequency: {
          title: tCampaigns("performanceMetrics.metrics.frequency.title"),
          value: forecastData.estimatedFrequency?.toFixed(2),
          subtitle: tCampaigns("performanceMetrics.metrics.frequency.subtitle"),
          color: "error" as const,
        },
        plannedAdPlays: {
          title: tCampaigns("performanceMetrics.metrics.plannedAdPlays.title"),
          value: formatNumber(forecastData.estimatedAdPlays),
          subtitle: tCampaigns(
            "performanceMetrics.metrics.plannedAdPlays.subtitle",
          ),
          color: "success" as const,
        },
        avgCPMCost: {
          title: tCampaigns("performanceMetrics.metrics.avgCPMCost.title"),
          value: formatCurrencyWithLocale(
            forecastData.avgCpm,
            campaignCurrency,
          ),
          subtitle: tCampaigns(
            "performanceMetrics.metrics.avgCPMCost.subtitle",
          ),
          color: "lightGreen" as const,
        },
        eCPM: {
          title: tCampaigns("performanceMetrics.metrics.eCPM.title"),
          value: formatCurrencyWithLocale(
            forecastData.avgECpm,
            campaignCurrency,
          ),
          subtitle: tCampaigns("performanceMetrics.metrics.eCPM.subtitle"),
          color: "orange" as const,
        },
        sov: {
          title: tCampaigns("performanceMetrics.metrics.sov.title"),
          value: tCampaigns("performanceMetrics.metrics.sov.valueFormat", {
            pct: forecastData.sov?.toFixed(2),
          }),
          subtitle: tCampaigns("performanceMetrics.metrics.sov.subtitle"),
          color: "primary" as const,
          percentage: forecastData.sov?.toFixed(2),
          description: tCampaigns("performanceMetrics.metrics.sov.description"),
        },
        sot: {
          title: tCampaigns("performanceMetrics.metrics.sot.title"),
          value: tCampaigns("performanceMetrics.metrics.sot.valueFormat", {
            planned: forecastData.plannedSot?.toFixed(2),
            total: forecastData.totalSot?.toFixed(2),
          }),
          subtitle: tCampaigns("performanceMetrics.metrics.sot.subtitle"),
          color: "warning" as const,
          percentage: (forecastData.plannedSot / forecastData.totalSot) * 100,
          description: tCampaigns("performanceMetrics.metrics.sot.description"),
        },
      }
    : {
        totalImpressions: {
          title: tCampaigns(
            "performanceMetrics.metrics.totalImpressions.title",
          ),
          value: "-",
          subtitle: tCampaigns(
            "performanceMetrics.metrics.totalImpressions.subtitle",
          ),
          color: "default" as const,
        },
        estimatedReach: {
          title: tCampaigns("performanceMetrics.metrics.estimatedReach.title"),
          value: "-",
          subtitle: tCampaigns(
            "performanceMetrics.metrics.estimatedReach.subtitle",
          ),
          color: "success" as const,
        },
        frequency: {
          title: tCampaigns("performanceMetrics.metrics.frequency.title"),
          value: "-",
          subtitle: tCampaigns("performanceMetrics.metrics.frequency.subtitle"),
          color: "error" as const,
        },
        plannedAdPlays: {
          title: tCampaigns("performanceMetrics.metrics.plannedAdPlays.title"),
          value: "-",
          subtitle: tCampaigns(
            "performanceMetrics.metrics.plannedAdPlays.subtitle",
          ),
          color: "success" as const,
        },
        avgCPMCost: {
          title: tCampaigns("performanceMetrics.metrics.avgCPMCost.title"),
          value: "-",
          subtitle: tCampaigns(
            "performanceMetrics.metrics.avgCPMCost.subtitle",
          ),
          color: "lightGreen" as const,
        },
        eCPM: {
          title: tCampaigns("performanceMetrics.metrics.eCPM.title"),
          value: "-",
          subtitle: tCampaigns("performanceMetrics.metrics.eCPM.subtitle"),
          color: "orange" as const,
        },
        sov: {
          title: tCampaigns("performanceMetrics.metrics.sov.title"),
          value: "-",
          subtitle: tCampaigns("performanceMetrics.metrics.sov.subtitle"),
          color: "primary" as const,
          percentage: 0,
          description: tCampaigns("performanceMetrics.metrics.sov.description"),
        },
        sot: {
          title: tCampaigns("performanceMetrics.metrics.sot.title"),
          value: "-",
          subtitle: tCampaigns("performanceMetrics.metrics.sot.subtitle"),
          color: "warning" as const,
          percentage: 0,
          description: tCampaigns("performanceMetrics.metrics.sot.description"),
        },
      };

  return (
    <Card className="p-4">
      <CardHeader>
        <CardTitle className="text-base font-medium border-b border-container-border pb-4 leading-5">
          {tCampaigns("performanceMetrics.title")}
        </CardTitle>
      </CardHeader>
      <CardContent className="pt-4 pl-0 pr-0 space-y-4 pb-0">
        {/* Row 1: Total Impressions, Estimated Reach, Frequency */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <MetricCard
            title={metrics.totalImpressions.title}
            value={metrics.totalImpressions.value}
            subtitle={metrics.totalImpressions.subtitle}
            valueColor={metrics.totalImpressions.color}
            bgColor="primary"
            infoIcon={true}
            tooltipContent={tCampaigns(
              TOOLTIP_CONTENT.performance.totalImpressions,
            )}
          />
          <MetricCard
            title={metrics.estimatedReach.title}
            value={metrics.estimatedReach.value}
            subtitle={metrics.estimatedReach.subtitle}
            valueColor={metrics.estimatedReach.color}
            bgColor="success"
            infoIcon={true}
            tooltipContent={tCampaigns(
              TOOLTIP_CONTENT.performance.estimatedReach,
            )}
          />
          <MetricCard
            title={metrics.frequency.title}
            value={metrics.frequency.value}
            subtitle={metrics.frequency.subtitle}
            valueColor={metrics.frequency.color}
            bgColor="error"
            infoIcon={true}
            tooltipContent={tCampaigns(TOOLTIP_CONTENT.performance.frequency)}
          />
        </div>

        {/* Row 2: Planned Ad Plays, Avg CPM Cost, eCPM */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <MetricCard
            title={metrics.plannedAdPlays.title}
            value={metrics.plannedAdPlays.value}
            subtitle={metrics.plannedAdPlays.subtitle}
            valueColor={metrics.plannedAdPlays.color}
            bgColor="cyan"
            infoIcon={true}
            tooltipContent={tCampaigns(
              TOOLTIP_CONTENT.performance.plannedAdPlays,
            )}
          />
          <MetricCard
            title={metrics.avgCPMCost.title}
            value={metrics.avgCPMCost.value}
            subtitle={metrics.avgCPMCost.subtitle}
            valueColor={metrics.avgCPMCost.color}
            bgColor="lime"
            infoIcon={true}
            tooltipContent={tCampaigns(TOOLTIP_CONTENT.performance.avgCpmCost)}
          />
          <MetricCard
            title={metrics.eCPM.title}
            value={metrics.eCPM.value}
            subtitle={metrics.eCPM.subtitle}
            valueColor={metrics.eCPM.color}
            bgColor="peach"
            infoIcon={true}
            tooltipContent={tCampaigns(TOOLTIP_CONTENT.performance.eCpm)}
          />
        </div>

        {/* Row 3: Share of Voice (SOV) and Share of Time (SOT) */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <MetricCard
            title={metrics.sov.title}
            value={metrics.sov.value}
            valueColor={metrics.sov.color}
            infoIcon={true}
            tooltipContent={tCampaigns(TOOLTIP_CONTENT.performance.sov)}
            progressBar={{
              percentage: Number(metrics.sov.percentage) || 0,
              color: metrics.sov.color,
            }}
            description={metrics.sov.description}
            bgColor="secondary"
          />
          <MetricCard
            title={metrics.sot.title}
            value={metrics.sot.value}
            valueColor={metrics.sot.color}
            infoIcon={true}
            tooltipContent={tCampaigns(TOOLTIP_CONTENT.performance.sot)}
            progressBar={{
              percentage: metrics.sot.percentage,
              color: metrics.sot.color,
            }}
            description={metrics.sot.description}
            bgColor="warning"
          />
        </div>
      </CardContent>
    </Card>
  );
};

export default PerformanceMetrics;
