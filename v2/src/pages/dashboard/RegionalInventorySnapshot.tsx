import RegionTooltip, {
  RegionTooltipApiItem,
} from "@components/dashboard/RegionTooltip";
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@components/ui/card";
import { Loading } from "@components/ui/Spinner";
import {
  useGetSalesPerformanceSummaryQuery,
  type SalesPerformanceSummaryItem,
} from "@services/dashboard/dashboardSlice";
import { useAppSelector } from "@store";
import { useTranslate } from "@tolgee/react";
import {
  PeriodOption,
  DateRange,
  calculateDateRangeForPeriod,
} from "@utils/dashboard.utils";
import { useMemo, useEffect, useRef } from "react";
import ReactDOMServer from "react-dom/server";
import { Chart } from "react-google-charts";

// Helper function to map utilization to discrete category
// Returns 1 for Low (<60%), 2 for Medium (60-79%), 3 for High (≥80%)
export const getUtilizationCategory = (utilization: number): number => {
  if (utilization >= 80) {
    return 3; // High
  }
  if (utilization >= 60) {
    return 2; // Medium
  }
  return 1; // Low
};

type CountryMapItem = SalesPerformanceSummaryItem & {
  country: string;
  digitalBillboard?: number;
  staticCount?: number;
  transit?: number;
  retail?: number;
};

const createTooltipContent = (
  item: CountryMapItem,
  currencyCode?: string,
): string => {
  return ReactDOMServer.renderToString(
    <RegionTooltip
      currencyCode={currencyCode}
      {...(item as RegionTooltipApiItem)}
    />,
  );
};

const CHART_HEADER: unknown[] = [
  "Country",
  "Utilization Category",
  { role: "tooltip", type: "string", p: { html: true } },
];

export const getData = (
  content: SalesPerformanceSummaryItem[] | undefined,
  currencyCode?: string,
): unknown[][] => {
  const rows =
    content
      ?.filter((item): item is CountryMapItem =>
        Boolean(item.country && item.country.trim() !== ""),
      )
      .map((item) => [
        item.country,
        getUtilizationCategory(item.utilization),
        createTooltipContent(item, currencyCode),
      ]) ?? [];
  return [CHART_HEADER, ...rows];
};

export const getOptions = () => ({
  height: 600,
  keepAspectRatio: false,
  colorAxis: {
    // Discrete color mapping: 1=Low, 2=Medium, 3=High
    values: [1, 2, 3],
    colors: ["#72A876", "#FBC56D", "#C52828"], // Green, Yellow, Red
    minValue: 1,
    maxValue: 3,
  },
  displayMode: "regions",
  resolution: "countries",
  backgroundColor: "#ffff",
  datalessRegionColor: "white",
  defaultColor: "#E5E7EB", // Light gray for regions without data
  borderColor: "#000000",
  legend: "none", // Hide color axis legend
  tooltip: {
    isHtml: true,
  },
});

// Extracted for testability and to reduce cognitive complexity (Sonar: S3776)
export const applyChartStyles = (container: HTMLDivElement): void => {
  const colorScaleElements = container.querySelectorAll(
    "svg rect[fill^='url'], svg line, svg polyline, svg polygon",
  );
  colorScaleElements.forEach((element) => {
    const el = element as SVGElement;
    const tag = el.tagName.toLowerCase();
    const fill = el.getAttribute("fill") ?? "";
    const hasStroke = Boolean(el.getAttribute("stroke"));
    if (
      fill.includes("url") ||
      hasStroke ||
      tag === "line" ||
      tag === "polyline"
    ) {
      if (tag === "line" || tag === "polyline") {
        el.style.display = "none";
      }
    }
  });

  const svgPaths = container.querySelectorAll("svg path");
  svgPaths.forEach((path) => {
    const svgPath = path as SVGPathElement;
    const inLegend = Boolean(svgPath.closest("[class*='legend']"));
    const inScale = Boolean(svgPath.closest("[class*='scale']"));
    if (!inLegend && !inScale) {
      svgPath.style.stroke = "#717171";
      svgPath.style.strokeWidth = "0.62px";
      svgPath.style.strokeLinejoin = "round";
    }
  });

  const colorAxisLines = container.querySelectorAll(
    "svg g[aria-label*='color'], svg g[aria-label*='legend'], svg line[stroke-width]",
  );
  colorAxisLines.forEach((line) => {
    const lineEl = line as SVGElement;
    if (lineEl.tagName.toLowerCase() === "line") {
      lineEl.style.display = "none";
    }
  });
};

interface RegionalInventorySnapshotProps {
  selectedPeriod: PeriodOption;
  dateRange?: DateRange;
}

const RegionalInventorySnapshot: React.FC<RegionalInventorySnapshotProps> = ({
  selectedPeriod,
  dateRange,
}) => {
  const { t: tDashboard } = useTranslate(["dashboard"]);
  const chartOptions = useMemo(() => getOptions(), []);
  const chartContainerRef = useRef<HTMLDivElement>(null);
  const { startDate, endDate } = useMemo(
    () => calculateDateRangeForPeriod(selectedPeriod, dateRange),
    [selectedPeriod, dateRange],
  );
  const user = useAppSelector((s) => s.profile.profile);
  const companyId = user?.activeCompanyId || user?.current_company?.id || "";

  const { data, isLoading, isFetching } = useGetSalesPerformanceSummaryQuery(
    {
      startDate,
      endDate,
      page: 0,
      size: 100,
      showBy: "country",
      companyId,
    },
    { skip: !startDate || !endDate },
  );

  const chartData = useMemo(
    () => getData(data?.data?.content),
    [data?.data?.content],
  );

  useEffect(() => {
    const applyStyles = () => {
      if (chartContainerRef.current) {
        applyChartStyles(chartContainerRef.current);
      }
    };

    applyStyles();
    const timeoutId = setTimeout(applyStyles, 500);
    const timeoutId2 = setTimeout(applyStyles, 1000);

    const observer = new MutationObserver(applyStyles);

    if (chartContainerRef.current) {
      observer.observe(chartContainerRef.current, {
        childList: true,
        subtree: true,
      });
    }

    return () => {
      clearTimeout(timeoutId);
      clearTimeout(timeoutId2);
      observer.disconnect();
    };
  }, [chartData]);

  return (
    <>
      <Card className="p-4">
        <CardHeader className="pb-4 border-b border-mw-neutral-100">
          <CardTitle className="text-base font-medium leading-5">
            {tDashboard("regionalInventory.title")}
          </CardTitle>
        </CardHeader>
        <CardContent className="relative pt-4 pb-0 px-0">
          {isFetching && !isLoading && (
            <Loading
              overlay
              size="md"
              variant="primary"
              text={tDashboard("loading")}
            />
          )}
          <div
            ref={chartContainerRef}
            data-testid="regional-chart-container"
            style={{ width: "90%", height: "600px" }}
          >
            {isLoading ? (
              <div
                className="flex items-center justify-center h-full text-mw-neutral-500"
                data-testid="regional-chart-loading"
              >
                {tDashboard("regionalInventory.loading")}
              </div>
            ) : (
              <Chart
                chartType="GeoChart"
                data={chartData}
                options={chartOptions}
              />
            )}
          </div>
        </CardContent>
        <CardFooter className="pt-4">
          <div className="inline-flex w-full gap-4 items-center justify-center space-x-4 ">
            <div className="inline-flex items-center gap-2">
              <div
                className="w-4 h-4 rounded-sm shrink-0"
                style={{ backgroundColor: "#C52828" }}
              />
              <span className="text-sm text-mw-neutral-700 font-normal truncate">
                {tDashboard("regionalInventory.highLabel")}
              </span>
            </div>
            <div className="inline-flex items-center gap-2">
              <div
                className="w-4 h-4 rounded-sm shrink-0"
                style={{ backgroundColor: "#FBC56D" }}
              />
              <span className="text-sm text-mw-neutral-700 font-normal truncate">
                {tDashboard("regionalInventory.mediumLabel")}
              </span>
            </div>
            <div className="inline-flex items-center gap-2">
              <div
                className="w-4 h-4 rounded-sm shrink-0"
                style={{ backgroundColor: "#72A876" }}
              />
              <span className="text-sm text-mw-neutral-700 font-normal truncate">
                {tDashboard("regionalInventory.lowLabel")}
              </span>
            </div>
          </div>
        </CardFooter>
      </Card>
    </>
  );
};

export default RegionalInventorySnapshot;
