import { useNamespace } from "@components/Tolgee/RouteNamespaceManager";
import { Button } from "@components/ui/Button";
import { DateRangePicker } from "@components/ui/DateRangePicker";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownTrigger,
} from "@components/ui/Dropdown";
import {
  useGetDashboardWidgetsQuery,
  type DashboardWidget,
} from "@services/dashboard/dashboardSlice";
import { useTranslate } from "@tolgee/react";
import {
  createNDaysPresets,
  getPeriodLabel,
  type DateRange,
  type PeriodOption,
} from "@utils/dashboard.utils";
import React, { useState, useEffect, useMemo, useCallback } from "react";

import AudienceReachPerformance from "./AudienceReachPerformance";
import BudgetTracker from "./BudgetTracker";
import CampaignOverview from "./CampaignOverview";
import CampaignPerformance from "./CampaignPerformance";
import CreativeStatusTrackerWidget from "./CreativeStatusTrackerWidget";
import CustomizeLayoutDrawer, {
  defaultWidgetVisibility,
  type WidgetVisibility,
} from "./CustomizeLayoutDrawer";
import {
  getBudgetChildKeys,
  getRevenueChildKeys,
} from "./dashboardWidgetConfig";
import {
  loadWidgetVisibilityFromStorage,
  saveWidgetVisibilityToStorage,
} from "./dashboardWidgetPersistence";
// import InventoryUtilizationSummary from "./InventoryUtilizationSummary";
import RegionalInventorySnapshot from "./RegionalInventorySnapshot";
import RevenuePerformance from "./RevenuePerformance";
import PageHeader from "../../components/PageHeader";

const DashboardPage: React.FC = () => {
  const { namespace } = useNamespace();
  const { t: tDashboard } = useTranslate([namespace]);
  const { t: tCommon } = useTranslate(["common"]);
  const [selectedPeriod, setSelectedPeriod] =
    useState<PeriodOption>("last-7-days");
  const [dateRange, setDateRange] = useState<DateRange>({
    from: null,
    to: null,
  });
  const [isCustomizeDrawerOpen, setIsCustomizeDrawerOpen] = useState(false);

  const { data: widgetsData, isLoading: isLoadingWidgets } =
    useGetDashboardWidgetsQuery();
  const widgetsList =
    widgetsData && "data" in widgetsData ? widgetsData.data : undefined;

  // Initialize widget visibility from API or localStorage or default
  const [widgetVisibility, setWidgetVisibility] = useState<WidgetVisibility>(
    () => {
      const storedVisibility = loadWidgetVisibilityFromStorage();
      return storedVisibility || defaultWidgetVisibility;
    },
  );

  // Update widget visibility when API data is loaded (API wins over stored)
  useEffect(() => {
    if (widgetsList) {
      const apiVisibility: WidgetVisibility = {};
      widgetsList.forEach((widget: DashboardWidget) => {
        apiVisibility[widget.key] = widget.isEnable;
      });
      setWidgetVisibility((prev) => ({ ...prev, ...apiVisibility }));
    }
  }, [widgetsList]);

  // Initialize date range when "date range" is selected
  useEffect(() => {
    if (selectedPeriod === "date-range" && !dateRange.from && !dateRange.to) {
      const today = new Date();
      const oneMonthLater = new Date();
      oneMonthLater.setMonth(today.getMonth() + 1);

      setDateRange({
        from: today,
        to: oneMonthLater,
      });
    }
  }, [selectedPeriod, dateRange.from, dateRange.to]);

  const dashboardDatePresets = useMemo(
    () => createNDaysPresets(tCommon),
    [tCommon],
  );

  const handlePeriodChange = useCallback((value: string) => {
    const period = value as PeriodOption;
    setSelectedPeriod(period);
    if (period !== "date-range") {
      setDateRange({ from: null, to: null });
    }
  }, []);

  // Hidden: Download is an unimplemented stub (no-op). Restore with the
  // Download button block and `handleDownload` dep below.
  // const handleDownload = useCallback(() => {}, []);

  const handleCustomize = useCallback(() => {
    setIsCustomizeDrawerOpen(true);
  }, []);

  const handleCloseCustomizeDrawer = useCallback(() => {
    setIsCustomizeDrawerOpen(false);
  }, []);

  const handleWidgetVisibilityChange = useCallback(
    (visibility: WidgetVisibility) => {
      setWidgetVisibility(visibility);
      saveWidgetVisibilityToStorage(visibility);
    },
    [],
  );

  const isWidgetEnabled = useCallback(
    (widgetKey: string): boolean => {
      if (!widgetsList) return false;
      const widget = widgetsList.find((w) => w.key === widgetKey);
      if (!widget) return false;
      return widgetVisibility[widgetKey] !== false && widget.isEnable;
    },
    [widgetsList, widgetVisibility],
  );

  const shouldShowRevenuePerformance = useMemo(() => {
    if (!widgetsList) return false;
    const revenueChildKeys = getRevenueChildKeys();
    return revenueChildKeys.some((key) => {
      const widget = widgetsList.find((w) => w.key === key);
      if (!widget) return false;
      return widgetVisibility[key] !== false && widget.isEnable;
    });
  }, [widgetsList, widgetVisibility]);

  const shouldShowBudgetTracker = useMemo(() => {
    if (!widgetsList) return false;
    const budgetChildKeys = getBudgetChildKeys();
    return budgetChildKeys.some((key) => {
      const widget = widgetsList.find((w) => w.key === key);
      if (!widget) return false;
      return widgetVisibility[key] !== false && widget.isEnable;
    });
  }, [widgetsList, widgetVisibility]);

  const nothingVisible = useMemo(() => {
    if (!widgetsList || isLoadingWidgets) return false;
    return (
      !shouldShowRevenuePerformance &&
      !isWidgetEnabled("campaign-overview") &&
      !isWidgetEnabled("campaign-performance") &&
      !shouldShowBudgetTracker &&
      !isWidgetEnabled("audience-reach-performance") &&
      !isWidgetEnabled("regional-inventory-snapshot")
    );
  }, [
    widgetsList,
    isLoadingWidgets,
    shouldShowRevenuePerformance,
    shouldShowBudgetTracker,
    isWidgetEnabled,
  ]);

  // Check if Inventory Utilization Summary section should be displayed
  // const shouldShowInventoryUtilization = useMemo(() => {
  //   if (!widgetsData?.data) return false;
  //   const inventoryChildKeys = ["inventory-overview", "utilization-breakdown"];
  //   return inventoryChildKeys.some((key) => {
  //     const widget = widgetsData.data?.find((w) => w.key === key);
  //     if (!widget) return false;
  //     return widgetVisibility[key] !== false && widget.isEnable;
  //   });
  // }, [widgetsData?.data, widgetVisibility]);

  const headerActions = useMemo(
    () => (
      <div className="flex items-center gap-2">
        <Dropdown value={selectedPeriod} onChange={handlePeriodChange}>
          <DropdownTrigger className="min-w-[140px] justify-between">
            {tDashboard(getPeriodLabel(selectedPeriod))}
          </DropdownTrigger>
          <DropdownContent>
            <DropdownItem value="last-7-days">
              {tDashboard("filters.last7Days")}
            </DropdownItem>
            <DropdownItem value="last-30-days">
              {tDashboard("filters.last30Days")}
            </DropdownItem>
            <DropdownItem value="last-month">
              {tDashboard("filters.lastMonth")}
            </DropdownItem>
            <DropdownItem value="quarterly">
              {tDashboard("filters.quarterly")}
            </DropdownItem>
            <DropdownItem value="yearly">
              {tDashboard("filters.yearly")}
            </DropdownItem>
            <DropdownItem value="date-range">
              {tDashboard("filters.dateRange")}
            </DropdownItem>
          </DropdownContent>
        </Dropdown>
        {selectedPeriod === "date-range" && (
          <DateRangePicker
            value={dateRange}
            onChange={setDateRange}
            onBlur={() => {
              // A single click only sets `from` — closing the picker
              // without a second click used to leave `to` null, which
              // silently fell back to a last-7-days request instead of the
              // single day the user picked. Treat it as a single-day range.
              if (dateRange.from && !dateRange.to) {
                setDateRange({ from: dateRange.from, to: dateRange.from });
              }
            }}
            placeholder={tDashboard("filters.selectDateRange")}
            className="w-80"
            presets={dashboardDatePresets}
          />
        )}
        {/* Hidden: Download button — unimplemented stub (no-op handler).
            Restore with the `handleDownload` callback and Download import. */}
        {/* <Button
          variant="outline"
          size="iconMd"
          onClick={handleDownload}
          title={tDashboard("actions.download")}
          aria-label={tDashboard("actions.download")}
          className="outline-mw-primary-500 text-mw-primary-500"
        >
          <Download className="h-4 w-4" />
        </Button> */}
        <Button
          variant="outline"
          size="md"
          onClick={handleCustomize}
          className="outline-mw-primary-500 text-mw-primary-500"
          title={tDashboard("actions.customize")}
          aria-label={tDashboard("actions.customizeLayout")}
        >
          {tDashboard("actions.customize")}
        </Button>
      </div>
    ),
    [
      selectedPeriod,
      dateRange,
      dashboardDatePresets,
      handlePeriodChange,
      // handleDownload, // Hidden with the Download button above
      handleCustomize,
      tDashboard,
    ],
  );

  return (
    <div id="dashboard-page" className="h-full flex flex-col">
      <PageHeader
        title={tDashboard("title")}
        descriptionKey={tDashboard("welcome")}
        actions={headerActions}
      />
      <main
        className="p-4 overflow-y-auto flex-1 space-y-4"
        aria-label={tDashboard("actions.dashboardContent")}
      >
        {shouldShowRevenuePerformance && (
          <RevenuePerformance
            widgetVisibility={widgetVisibility}
            selectedPeriod={selectedPeriod}
            dateRange={dateRange}
          />
        )}
        {isWidgetEnabled("campaign-overview") && (
          <CampaignOverview
            selectedPeriod={selectedPeriod}
            dateRange={dateRange}
          />
        )}
        {isWidgetEnabled("campaign-performance") && (
          <CampaignPerformance
            selectedPeriod={selectedPeriod}
            dateRange={dateRange}
          />
        )}
        {shouldShowBudgetTracker && (
          <BudgetTracker
            selectedPeriod={selectedPeriod}
            dateRange={dateRange}
            showOverview={isWidgetEnabled("budget-overview")}
            showPerformanceSummary={isWidgetEnabled(
              "budget-performance-summary",
            )}
          />
        )}
        {/* {shouldShowInventoryUtilization && (
          <InventoryUtilizationSummary
            showOverview={isWidgetEnabled("inventory-overview")}
            showUtilizationBreakdown={isWidgetEnabled("utilization-breakdown")}
            onViewAvailability={() => {
              // Navigate to availability view or open modal
            }}
          />
        )} */}
        {isWidgetEnabled("creative-status") && <CreativeStatusTrackerWidget />}
        {isWidgetEnabled("audience-reach-performance") && (
          <AudienceReachPerformance
            selectedPeriod={selectedPeriod}
            dateRange={dateRange}
          />
        )}
        {isWidgetEnabled("regional-inventory-snapshot") && (
          <RegionalInventorySnapshot
            selectedPeriod={selectedPeriod}
            dateRange={dateRange}
          />
        )}
        {nothingVisible && (
          <div className="flex flex-col items-center justify-center py-24 gap-4 text-center">
            <p className="text-mw-neutral-500 text-sm">
              {tDashboard("actions.noWidgetsVisible")}
            </p>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setIsCustomizeDrawerOpen(true)}
            >
              {tDashboard("actions.openCustomize")}
            </Button>
          </div>
        )}
        {isLoadingWidgets && !widgetsList && (
          <div
            className="flex items-center justify-center py-12 text-mw-neutral-500"
            role="status"
            aria-live="polite"
          >
            {tDashboard("actions.loadingDashboard")}
          </div>
        )}
      </main>
      <CustomizeLayoutDrawer
        isOpen={isCustomizeDrawerOpen}
        onClose={handleCloseCustomizeDrawer}
        widgetVisibility={widgetVisibility}
        onWidgetVisibilityChange={handleWidgetVisibilityChange}
        widgets={widgetsList}
        isLoadingWidgets={isLoadingWidgets}
      />
    </div>
  );
};

export default DashboardPage;
