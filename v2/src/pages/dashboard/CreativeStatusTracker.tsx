import DoughnutChart, {
  type DoughnutChartDataItem,
  type DoughnutLabelType,
} from "@components/common/DoughnutChart";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import { Progress } from "@components/ui/Progressbar";
import { useTranslate } from "@tolgee/react";
import { clsx } from "clsx";
import React, { useMemo } from "react";

// Types
export interface CreativeStatusData {
  processing: number;
  accepted: number;
  inadequate: number;
}

export interface CreativeBreakdown {
  totalCreatives: number;
  images: number;
  videos: number;
}

export interface DisplayFormats {
  images: number;
  videos: number;
}

export interface CreativeStatusTrackerData {
  status: CreativeStatusData;
  breakdown: CreativeBreakdown;
  displayFormats: DisplayFormats;
}

export interface CreativeStatusTrackerProps {
  /** Data to display */
  data?: CreativeStatusTrackerData;
  /** Additional CSS class */
  className?: string;
  /** Whether to show labels inside donut segments (default: false) */
  showDoughnutLabels?: boolean;
  /** Type of label to show: 'none', 'value', or 'percentage' (default: 'none') */
  donutLabelType?: DoughnutLabelType;
  /** Color for donut labels (default: '#ffffff') */
  donutLabelColor?: string;
  /** When true, appends tracker label in header (e.g. agency view) */
  isAgency?: boolean;
}

// Default sample data matching the design
const defaultData: CreativeStatusTrackerData = {
  status: {
    processing: 28,
    accepted: 48,
    inadequate: 22,
  },
  breakdown: {
    totalCreatives: 28,
    images: 16,
    videos: 12,
  },
  displayFormats: {
    images: 71,
    videos: 40,
  },
};

// Doughnut chart colors
const STATUS_COLORS = {
  processing: "#FBC56D", // Yellow/Amber
  accepted: "#72A876", // Green
  inadequate: "#D86F6F", // Red
};

const CreativeStatusTracker: React.FC<CreativeStatusTrackerProps> = ({
  data = defaultData,
  className,
  showDoughnutLabels = false,
  donutLabelType = "none",
  donutLabelColor = "#ffffff",
  isAgency = false,
}) => {
  const { t: tDashboard } = useTranslate(["dashboard"]);

  // Calculate total and percentages
  const total = useMemo(() => {
    return (
      data.status.processing + data.status.accepted + data.status.inadequate
    );
  }, [data.status]);

  const percentages = useMemo(() => {
    return {
      processing:
        total > 0 ? Math.round((data.status.processing / total) * 100) : 0,
      accepted:
        total > 0 ? Math.round((data.status.accepted / total) * 100) : 0,
      inadequate:
        total > 0 ? Math.round((data.status.inadequate / total) * 100) : 0,
    };
  }, [data.status, total]);

  const donutChartData: DoughnutChartDataItem[] = useMemo(() => {
    return [
      {
        label: tDashboard("creativeStatus.processing"),
        value: data.status.processing,
        color: STATUS_COLORS.processing,
        percentage: percentages.processing,
      },
      {
        label: tDashboard("creativeStatus.accepted"),
        value: data.status.accepted,
        color: STATUS_COLORS.accepted,
        percentage: percentages.accepted,
      },
      {
        label: tDashboard("creativeStatus.inadequate"),
        value: data.status.inadequate,
        color: STATUS_COLORS.inadequate,
        percentage: percentages.inadequate,
      },
    ];
  }, [data.status, percentages, tDashboard]);

  return (
    <Card className={clsx("p-4", className)}>
      {/* Header */}
      <CardHeader className="pb-4 border-b border-container-border">
        <div className="flex items-center justify-between">
          <CardTitle className="text-m font-medium text-mw-neutral-800">
            {tDashboard("creativeStatus.title")}
            {isAgency && <>{tDashboard("creativeStatus.tracker")}</>}
          </CardTitle>
        </div>
      </CardHeader>

      <CardContent className="pt-4">
        <div className="flex flex-col lg:flex-row gap-6">
          {/* Left Section - Doughnut Chart + Breakdown */}
          <div className="flex-1">
            {/* Doughnut Chart */}
            <div className="shrink-0">
              <DoughnutChart
                data={donutChartData}
                emphasizeLargest={false}
                showLegend={false}
                showDoughnutLabels={showDoughnutLabels}
                donutLabelType={donutLabelType}
                donutLabelColor={donutLabelColor}
                cutout="65%"
              />
            </div>
          </div>

          {/* Right Section - Status Legend + Display Formats */}
          <div className="flex-1">
            {/* Status Legend */}
            <div className="space-y-4 mb-4">
              {Object.entries(STATUS_COLORS).map(([statusKey, colorValue]) => {
                const statusValue =
                  data.status[statusKey as keyof CreativeStatusData];
                const statusPercentage =
                  percentages[statusKey as keyof typeof percentages];
                return (
                  <div
                    key={statusKey}
                    className="flex items-center justify-between"
                  >
                    <div className="flex items-center gap-2">
                      <span
                        className="w-3 h-3 rounded-sm"
                        style={{ backgroundColor: colorValue }}
                      />
                      <span className="text-xs font-medium text-mw-neutral-500">
                        {tDashboard(`creativeStatus.${statusKey}`)}
                      </span>
                    </div>
                    <span className="text-s font-medium text-mw-neutral-700">
                      <span className="font-medium">{statusValue}</span>
                      <span className="text-[10px] text-mw-neutral-200 ml-1">
                        ({statusPercentage}%)
                      </span>
                    </span>
                  </div>
                );
              })}
            </div>

            {/* Display Formats */}
            <div className="pt-4">
              <div className="text-m font-medium text-mw-neutral-800 mb-4 border-b border-container-border pb-2">
                {tDashboard("creativeStatus.displayFormats")}
              </div>

              <div className="space-y-6">
                {/* Images Progress */}
                <Progress
                  label={tDashboard("creativeStatus.images")}
                  labelClassNames="text-s! font-semibold! text-black! pb-4!"
                  percentageLabelClassNames="text-s! font-semibold! text-mw-primary-500!"
                  value={data.displayFormats.images}
                  max={100}
                  variant="primary"
                  size="lg"
                  showPercentage={true}
                  showInfo={false}
                />

                {/* Videos Progress */}
                <Progress
                  label={tDashboard("creativeStatus.videos")}
                  labelClassNames="text-s! font-semibold! text-black! pb-4!"
                  percentageLabelClassNames="text-s! font-semibold! text-mw-primary-500!"
                  value={data.displayFormats.videos}
                  max={100}
                  variant="secondary"
                  size="lg"
                  showPercentage={true}
                  showInfo={false}
                />
              </div>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
};

export default CreativeStatusTracker;
