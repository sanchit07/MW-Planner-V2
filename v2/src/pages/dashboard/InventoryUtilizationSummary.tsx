import HorizontalBarChart from "@components/common/HorizontalBarChart";
import { Button } from "@components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownTrigger,
} from "@components/ui/Dropdown";
import {
  InventoryType,
  InventoryClassification,
} from "@constants/inventory.constants";
import { useTranslate } from "@tolgee/react";
import { TrendingUp, Calendar, Warehouse } from "lucide-react";
import React, { useState, useMemo } from "react";

import SummaryCard from "./SummaryCard";

// Types
export interface UtilizationData {
  booked: number;
  reserved: number;
  down: number;
  available: number;
}

export interface InventorySummaryData {
  activeCampaigns: {
    count: number;
    inReviewing: number;
  };
  totalInventories: {
    count: number;
    activeCount: number;
  };
  utilizationRate: {
    percentage: number;
    unitsBooked: number;
    changePercentage: number;
    isPositiveChange: boolean;
  };
  utilization: UtilizationData;
}

export interface InventoryUtilizationSummaryProps {
  /** Summary data to display */
  data?: InventorySummaryData;
  /** Selected inventory filter value */
  selectedInventory?: string;
  /** Callback when inventory filter changes */
  onInventoryChange?: (value: string) => void;
  /** Callback when View Availability button is clicked */
  onViewAvailability?: () => void;
  /** Additional CSS class */
  className?: string;
  showOverview?: boolean;
  showUtilizationBreakdown?: boolean;
}

// Default sample data
const defaultData: InventorySummaryData = {
  activeCampaigns: {
    count: 120,
    inReviewing: 22,
  },
  totalInventories: {
    count: 1200,
    activeCount: 1200,
  },
  utilizationRate: {
    percentage: 78.5,
    unitsBooked: 79,
    changePercentage: 12,
    isPositiveChange: false,
  },
  utilization: {
    booked: 62,
    reserved: 16,
    down: 16,
    available: 6,
  },
};

const defaultInventoryOptions = [
  InventoryClassification.DIGITAL,
  InventoryClassification.CLASSIC,
  InventoryType.TRANSIT,
  InventoryType.STREET_FURNITURE,
  InventoryType.PLACE_BASED,
  InventoryType.DIGITAL_NETWORK,
  InventoryType.RETAIL,
];

const InventoryUtilizationSummary: React.FC<
  InventoryUtilizationSummaryProps
> = ({
  data = defaultData,
  selectedInventory: controlledSelectedInventory,
  onInventoryChange,
  onViewAvailability,
  className,
  showOverview = false,
  showUtilizationBreakdown = false,
}) => {
  const [internalSelectedInventory, setInternalSelectedInventory] =
    useState("all");
  const { t: tDashboard } = useTranslate(["dashboard"]);
  const { t: tCommon } = useTranslate(["common"]);

  const LABEL_MAP: Record<string, string> = {
    [InventoryClassification.DIGITAL]: tCommon(
      "inventoryClassification.digital",
    ),
    [InventoryClassification.CLASSIC]: tCommon(
      "inventoryClassification.classic",
    ),
    [InventoryType.TRANSIT]: tCommon("inventoryType.transit"),
    [InventoryType.STREET_FURNITURE]: tCommon("inventoryType.street_furniture"),
    [InventoryType.PLACE_BASED]: tCommon("inventoryType.place_based"),
    [InventoryType.DIGITAL_NETWORK]: tCommon("inventoryType.digital_network"),
    [InventoryType.RETAIL]: tCommon("inventoryType.retail"),
  };

  const inventoryOptions = defaultInventoryOptions.map((item) => ({
    value: item,
    label: LABEL_MAP[item] || item,
  }));

  const selectedInventory =
    controlledSelectedInventory ?? internalSelectedInventory;

  const handleInventoryChange = (value: string) => {
    if (onInventoryChange) {
      onInventoryChange(value);
    } else {
      setInternalSelectedInventory(value);
    }
  };

  const selectedInventoryLabel = useMemo(() => {
    return (
      inventoryOptions.find((opt) => opt.value === selectedInventory)?.label ||
      tDashboard("inventoryUtilization.allInventories")
    );
  }, [inventoryOptions, selectedInventory]);

  // Prepare horizontal stacked bar chart data
  const utilizationChartData = useMemo(() => {
    return {
      labels: [""],
      datasets: [
        {
          label: tDashboard("inventoryUtilization.booked"),
          data: [data.utilization.booked],
          backgroundColor: "#72A876", // Green
          stack: "utilization",
        },
        {
          label: tDashboard("inventoryUtilization.reserved"),
          data: [data.utilization.reserved],
          backgroundColor: "#87CAED", // Blue
          stack: "utilization",
        },
        {
          label: tDashboard("inventoryUtilization.down"),
          data: [data.utilization.down],
          backgroundColor: "#FBC56D", // Amber/Orange
          stack: "utilization",
        },
        {
          label: tDashboard("inventoryUtilization.available"),
          data: [data.utilization.available],
          backgroundColor: "#A0A0A0", // Gray
          stack: "utilization",
        },
      ],
    };
  }, [data.utilization, tDashboard]);

  return (
    <>
      {(showOverview || showUtilizationBreakdown) && (
        <Card className={className ? `p-4 ${className}` : "p-4"}>
          {/* Header */}
          <CardHeader className="pb-4 border-b border-container-border">
            <div className="flex items-center justify-between">
              <CardTitle className="text-m font-medium text-mw-neutral-800">
                {tDashboard("inventoryUtilization.title")}
              </CardTitle>
              <div className="flex items-center gap-2">
                <span className="text-s text-mw-neutral-800">
                  {tDashboard("inventoryUtilization.dropdownLabel")}
                </span>
                <Dropdown
                  value={selectedInventory}
                  onChange={handleInventoryChange}
                >
                  <DropdownTrigger className="min-w-[160px] justify-between text-og-black">
                    {selectedInventoryLabel}
                  </DropdownTrigger>
                  <DropdownContent>
                    {inventoryOptions.map((option) => (
                      <DropdownItem key={option.value} value={option.value}>
                        {option.label}
                      </DropdownItem>
                    ))}
                  </DropdownContent>
                </Dropdown>
              </div>
            </div>
          </CardHeader>

          <CardContent className="pt-4 px-0">
            {/* Summary Cards */}
            {showOverview && (
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
                {/* Active Campaigns */}
                <SummaryCard
                  icon={<Calendar className="w-6 h-6 text-mw-primary-500" />}
                  iconBgColor="bg-mw-primary-50"
                  title={tDashboard("inventoryUtilization.activeCampaigns")}
                  value={data.activeCampaigns.count}
                  subtitle={`${data.activeCampaigns.inReviewing} ${tDashboard("inventoryUtilization.inReviewing")}`}
                />

                {/* Total Inventories */}
                <SummaryCard
                  icon={<Warehouse className="w-6 h-6 text-mw-secondary-500" />}
                  iconBgColor="bg-mw-secondary-50"
                  title={tDashboard("inventoryUtilization.totalInventories")}
                  value={data.totalInventories.count.toLocaleString()}
                  subtitle={tDashboard(
                    "inventoryUtilization.activeInventories",
                  )}
                />

                {/* Utilization Rate */}
                <SummaryCard
                  icon={<TrendingUp className="w-6 h-6 text-mw-success-500" />}
                  iconBgColor="bg-mw-success-50"
                  title={tDashboard("inventoryUtilization.utilizationRate")}
                  value={`${data.utilizationRate.percentage}%`}
                  subtitle={`${data.utilizationRate.unitsBooked}  ${tDashboard("inventoryUtilization.unitsBooked")}`}
                  trend={{
                    value: data.utilizationRate.changePercentage,
                    isPositive: data.utilizationRate.isPositiveChange,
                  }}
                />
              </div>
            )}

            {/* Utilization Breakdown */}
            {showUtilizationBreakdown && (
              <Card className="px-0 py-0">
                <CardHeader className="pb-4 border-b border-container-border px-4 pt-4">
                  <CardTitle className="flex items-center justify-between">
                    <span className="text-m font-medium text-mw-neutral-800">
                      {tDashboard("inventoryUtilization.utilizationBreakdown")}
                    </span>
                    <Button
                      variant="outline"
                      size="sm"
                      className="outline-mw-primary-500 text-mw-primary-500"
                      onClick={onViewAvailability}
                    >
                      {tDashboard("inventoryUtilization.viewAvailability")}
                    </Button>
                  </CardTitle>
                </CardHeader>
                <CardContent className="pt-4 px-4 pb-4">
                  {/* Horizontal Stacked Bar Chart */}
                  <div className="mb-4">
                    <HorizontalBarChart
                      labels={utilizationChartData.labels}
                      datasets={utilizationChartData.datasets}
                      stacked={true}
                      height={60}
                      showLegend={true}
                      showXAxisGrid={false}
                      showYAxisGrid={false}
                      showXAxisLabels={false}
                      showYAxisLabels={false}
                      showTooltip={false}
                      showDataLabels={true}
                      dataLabelColor="#ffffff"
                      dataLabelFontSize={11}
                      formatDataLabel={(value, datasetLabel) =>
                        `${value}% ${value > 50 ? datasetLabel : ""}`
                      }
                      barThickness={32}
                    />
                  </div>
                </CardContent>
              </Card>
            )}
          </CardContent>
        </Card>
      )}
    </>
  );
};

export default InventoryUtilizationSummary;
