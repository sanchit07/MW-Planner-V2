import { Badge } from "@components/ui/Badge";
import { formatCurrency } from "@utils/campaign.utils";
import React from "react";

export interface RegionTooltipApiItem {
  country: string;
  inventories: number;
  utilization: number;
  countCampaigns: number;
  revenue: number;
  digitalBillboard?: number;
  staticCount?: number;
  transit?: number;
  retail?: number;
}

interface RegionTooltipProps extends RegionTooltipApiItem {
  currencyCode?: string;
}

const getUtilizationBadgeStyle = (
  utilization: number,
): "success" | "warning" | "destructive" => {
  if (utilization >= 80) {
    return "destructive";
  }
  if (utilization >= 60 && utilization < 80) {
    return "warning";
  }
  return "success";
};

const RegionTooltip: React.FC<RegionTooltipProps> = ({
  country,
  inventories,
  utilization,
  countCampaigns,
  revenue,
  currencyCode,
  digitalBillboard,
  staticCount,
  transit,
  retail,
}) => {
  const utilizationBadge = getUtilizationBadgeStyle(utilization);

  return (
    <div
      role="tooltip"
      aria-label={country ? `Region details for ${country}` : "Region details"}
    >
      <div className="justify-start text-secondary text-xs font-normal leading-4 mb-2">
        {inventories.toLocaleString()} Inventories • {countCampaigns} plans
      </div>
      <div className="self-stretch h-0 outline outline-1 outline-offset-[-0.50px] outline-container-border " />
      <div className="grid grid-cols-2 gap-2 my-2 pt-2">
        <div className="space-y-2">
          <p className="text-xs font-normal text-mw-neutral-500">Utilization</p>
          <Badge variant={utilizationBadge}>
            {utilization.toFixed(1)}% Utilized
          </Badge>
        </div>
        <div className="space-y-2">
          <p className="text-xs font-normal text-mw-neutral-500">
            Total Revenue
          </p>
          <p className="text-xs font-normal text-mw-neutral-700">
            {formatCurrency(revenue ?? 0, currencyCode)}
          </p>
        </div>
      </div>
      <div className="self-stretch h-0 outline outline-1 outline-offset-[-0.50px] outline-container-border " />
      <div className="grid grid-cols-2 gap-2 my-2 pt-2">
        <div className="space-y-2">
          <p className="text-xs font-normal text-mw-neutral-500">
            Digital Billboard
          </p>
          <p className="text-xs font-normal text-mw-neutral-700">
            {digitalBillboard?.toLocaleString()}
          </p>
        </div>
        <div className="space-y-2">
          <p className="text-xs font-normal text-mw-neutral-500">Static</p>
          <p className="text-xs font-normal text-mw-neutral-700">
            {staticCount?.toLocaleString()}
          </p>
        </div>
        <div className="space-y-2">
          <p className="text-xs font-normal text-mw-neutral-500">Transit</p>
          <p className="text-xs font-normal text-mw-neutral-700">
            {transit?.toLocaleString()}
          </p>
        </div>
        <div className="space-y-2">
          <p className="text-xs font-normal text-mw-neutral-500">Retail</p>
          <p className="text-xs font-normal text-mw-neutral-700">
            {retail?.toLocaleString()}
          </p>
        </div>
      </div>
    </div>
  );
};

export default RegionTooltip;
