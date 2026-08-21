import { Checkbox } from "@components/ui/Checkbox";
import { Dropdown, DropdownTrigger } from "@components/ui/Dropdown";
import { Progress } from "@components/ui/Progressbar";
import { useTranslate } from "@tolgee/react";
import { clsx } from "clsx";
import { Calendar, Info, MoreHorizontal } from "lucide-react";
import React from "react";
import { useNavigate } from "react-router-dom";

import { Button } from "../../../components/ui/Button";
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
} from "../../../components/ui/card";
import { StatusBadge } from "../../../components/ui/StatusBadge";
import { Tooltip } from "../../../components/ui/Tooltip";
import { FALLBACK_VALUES } from "../../../constants/campaign.constants";
import { TOOLTIP_CONTENT } from "../../../constants/tooltip.constants";
import { CampaignDisplay } from "../../../types/campaign-display.types";
import { normalizeGoalType } from "../../../utils/budget.utils";
import { formatDisplayDate } from "../../../utils/dateUtils";
import { CampaignActionsDropdownContent } from "../components/CampaignActionsDropdownContent";

export interface CampaignCardProps {
  campaign: CampaignDisplay;
  selected?: boolean;
  onSelect?: (id: string | number) => void;
  onClick?: () => void;
  className?: string;
  id?: string;
  onRefresh?: () => void;
}

const STATUS_BORDER_CLASSES: Record<string, string> = {
  Draft: "border-l-4 border-l-mw-neutral-400",
  Planned: "border-l-4 border-l-mw-primary-500",
  Active: "border-l-4 border-l-mw-success-500",
  Completed: "border-l-4 border-l-mw-teal-500",
  Reviewing: "border-l-4 border-l-mw-warning-400",
  Negotiating: "border-l-4 border-l-mw-warning-500",
  Pending: "border-l-4 border-l-mw-warning-300",
  Approved: "border-l-4 border-l-mw-success-400",
  Archived: "border-l-4 border-l-mw-neutral-300",
  Rejected: "border-l-4 border-l-mw-error-500",
  Paused: "border-l-4 border-l-mw-orange-500",
};

const GOAL_PILL_CLASSES: Record<string, string> = {
  IMPRESSIONS:
    "bg-mw-primary-100 dark:bg-mw-primary-900/30 text-mw-primary-700 dark:text-mw-primary-300",
  REACH:
    "bg-mw-success-100 dark:bg-mw-success-900/30 text-mw-success-700 dark:text-mw-success-300",
  SOV: "bg-mw-teal-100 dark:bg-mw-teal-900/30 text-mw-teal-700 dark:text-mw-teal-300",
  ADPLAYS:
    "bg-mw-warning-100 dark:bg-mw-warning-900/30 text-mw-warning-700 dark:text-mw-warning-300",
};

export const CampaignCard: React.FC<CampaignCardProps> = ({
  campaign,
  selected = false,
  onSelect,
  className,
  id,
  onRefresh,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { t: tCommon } = useTranslate(["common"]);
  const navigate = useNavigate();

  const statusBorderClass =
    STATUS_BORDER_CLASSES[campaign.status] ??
    "border-l-4 border-l-mw-neutral-200";

  const budgetUtilPct =
    campaign.rawBudget && campaign.rawBudget > 0
      ? Math.min(((campaign.rawTotalCost ?? 0) / campaign.rawBudget) * 100, 100)
      : 0;

  const goalPillClass =
    GOAL_PILL_CLASSES[campaign.goals?.goalType ?? ""] ??
    "bg-mw-neutral-100 dark:bg-mw-neutral-700 text-mw-neutral-600 dark:text-mw-neutral-300";

  return (
    <Card
      id={id || `campaign-card-${campaign.id}`}
      className={clsx(
        "hover:shadow-lg transition-all duration-200 border-mw-neutral-100 flex flex-col h-full",
        statusBorderClass,
        className,
      )}
    >
      {/* Header with checkbox and status */}
      <CardHeader
        id={`${id || `campaign-card-${campaign.id}`}-header`}
        className="flex-row items-start justify-start p-4"
      >
        <div className="flex-1 flex items-center gap-2 space-y-1 min-w-0">
          <Checkbox
            id={`${id || `campaign-card-${campaign.id}`}-checkbox`}
            checked={selected}
            onChange={(e) => {
              e.stopPropagation();
              onSelect?.(campaign.id);
            }}
            className={clsx("shrink-0", className)}
          />
          <div className="flex flex-col space-y-1 min-w-0">
            <Tooltip content={campaign.campaignName}>
              <p className="font-medium text-primary text-sm truncate">
                {campaign.campaignName}
              </p>
            </Tooltip>
            {campaign.planNumber && (
              <p className="text-sm text-secondary">
                {tCampaigns("viewCampaign.ID")}: {campaign.planNumber}
              </p>
            )}
          </div>
        </div>
        <div className="flex justify-end items-end">
          <StatusBadge status={campaign.status.toLocaleLowerCase()}>
            {tCampaigns(
              `campaignsList.status.${campaign.status.toUpperCase()}`,
            ) || campaign.status}
          </StatusBadge>
        </div>
      </CardHeader>

      {/* Content */}
      <CardContent className="p-4 space-y-3 flex-1 flex flex-col">
        {/* Flight Dates */}
        <div className="flex items-center gap-2">
          <Calendar className="size-5 text-mw-neutral-300" />
          <span className="text-sm text-secondary">
            {formatDisplayDate(campaign.startDate, tCommon)} -{" "}
            {formatDisplayDate(campaign.endDate, tCommon)}
          </span>
          {campaign.daysLeft !== FALLBACK_VALUES.DAYS_LEFT && (
            <>
              <div className="size-3 rounded-full bg-zinc-300" />
              <span className="text-sm text-mw-neutral-500">
                {tCampaigns("campaignsList.durationDays", {
                  n: campaign.daysLeft,
                })}
              </span>
            </>
          )}
        </div>

        <div className="h-px bg-mw-neutral-100" />

        {/* Brand + Goal type pill */}
        <div className="flex items-start justify-between gap-2">
          <div>
            <p className="text-sm text-mw-neutral-500">
              {tCampaigns("card.brand")}
            </p>
            <p className="font-medium text-primary dark:text-white text-sm">
              {campaign.brand}
            </p>
          </div>
          {campaign.goals?.goalType && (
            <span
              className={clsx(
                "mt-1 shrink-0 px-2 py-0.5 text-xs font-medium rounded-full",
                goalPillClass,
              )}
            >
              {(() => {
                const raw = campaign.goals.goalType;
                const key = normalizeGoalType(raw);
                const KNOWN = ["IMPRESSIONS", "REACH", "SOV", "ADPLAYS"];
                return key && KNOWN.includes(key)
                  ? tCampaigns(`campaignsList.goalTypes.${key}`)
                  : raw;
              })()}
            </span>
          )}
        </div>

        {/* Inventories Used */}
        <div>
          <p className="text-sm text-mw-neutral-500">
            {tCampaigns("card.inventories_used")}
          </p>
          <p className="font-medium text-primary dark:text-white text-sm">
            {campaign.inventory} {tCampaigns("card.units")}
          </p>
        </div>

        {/* Budget with utilization bar */}
        <div className="space-y-1">
          <div className="flex items-center justify-between">
            <p className="text-sm text-mw-neutral-500">
              {tCampaigns("card.budget_utilization")}
            </p>
            <p className="font-medium text-primary dark:text-white text-sm">
              {campaign.budget}
            </p>
          </div>
          {budgetUtilPct > 0 && (
            <Progress
              value={budgetUtilPct}
              showPercentage={false}
              variant="primary"
              progressTrackVariant="default"
            />
          )}
        </div>

        <div className="h-px bg-mw-neutral-100" />

        {/* Metrics Grid */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-sm text-mw-neutral-500">
                {tCampaigns("card.impressions")}
              </span>
              <Tooltip
                content={tCampaigns(
                  TOOLTIP_CONTENT.performance.totalImpressions,
                )}
              >
                <Info className="size-3.5 text-mw-neutral-500 cursor-help" />
              </Tooltip>
            </div>
            <p className="text-sm font-medium text-primary dark:text-white text-base leading-5">
              {campaign.impressions?.toLocaleString()}
            </p>
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-sm text-mw-neutral-500">
                {tCampaigns("card.reach")}
              </span>
              <Tooltip
                content={tCampaigns(TOOLTIP_CONTENT.performance.estimatedReach)}
              >
                <Info className="size-3.5 text-mw-neutral-500 cursor-help" />
              </Tooltip>
            </div>
            <p className="text-sm font-medium text-primary dark:text-white text-base leading-5">
              {campaign.reach.toLocaleString()}
            </p>
          </div>
        </div>

        {/* SOV and SOT metrics */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-sm text-mw-neutral-500">
                {tCampaigns("card.sov")}
              </span>
              <Tooltip content={tCampaigns(TOOLTIP_CONTENT.performance.sov)}>
                <Info className="size-3.5 text-mw-neutral-500 cursor-help" />
              </Tooltip>
            </div>
            <p className="text-sm font-medium text-primary dark:text-white text-base leading-5">
              {campaign.sov ? campaign.sov.toFixed(2) : "0"}%
            </p>
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-sm text-mw-neutral-500">
                {tCampaigns("card.sot")}
              </span>
              <Tooltip content={tCampaigns(TOOLTIP_CONTENT.performance.sot)}>
                <Info className="size-3.5 text-mw-neutral-500 cursor-help" />
              </Tooltip>
            </div>
            <p className="text-sm font-medium text-primary dark:text-white text-base leading-5">
              {campaign?.plannedSot
                ? campaign.plannedSot.toLocaleString(undefined, {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2,
                  })
                : "0.00"}{" "}
              H
            </p>
          </div>
        </div>
      </CardContent>

      {/* Footer with View Details Button and Actions */}
      <CardFooter className="p-4 gap-2" onClick={(e) => e.stopPropagation()}>
        <Button
          variant="outline"
          size="lg"
          className="flex-1 outline-mw-primary-500 text-mw-primary-500"
          onClick={() => navigate(`/campaigns/view/${campaign.id}`)}
        >
          {tCampaigns("card.view_details")}
        </Button>
        <Dropdown>
          <DropdownTrigger asChild>
            <Button
              variant="outline"
              size="iconMd"
              className="outline-mw-primary-500 text-mw-primary-500 focus:bg-mw-primary-200"
            >
              <MoreHorizontal className="size-4" />
            </Button>
          </DropdownTrigger>
          <CampaignActionsDropdownContent
            campaignId={campaign.id}
            campaignData={{
              status: campaign.status,
              createdByUsername: campaign.userName,
            }}
            className="container-border!"
            handlers={{
              onRefresh,
            }}
          />
        </Dropdown>
      </CardFooter>
    </Card>
  );
};

export default CampaignCard;
