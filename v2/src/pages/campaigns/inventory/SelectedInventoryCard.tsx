import FitnessScoreCard from "@components/common/FitnessScoreCard";
import { Badge } from "@components/ui/Badge";
import { Card, CardContent } from "@components/ui/card";
import { Spinner } from "@components/ui/Spinner";
import { Tooltip } from "@components/ui/Tooltip";
import { useTranslate } from "@tolgee/react";
import { formatCurrencyWithLocale } from "@utils/currency";
import {
  getChipOverflow,
  MAX_VISIBLE_VENUE_TYPES,
} from "@utils/inventory-display.utils";
import clsx from "clsx";
import {
  CalendarClock,
  ChevronDown,
  Eye,
  Sparkles,
  Trash2,
} from "lucide-react";
import React from "react";

import type { RecommendationScore } from "./view/useRecommendationScores";
import type { AvailabilityEntry } from "./view/useSelectedInventoryAvailability";
import type { InventoryItem } from "../../../types/inventory.types";

interface SelectedInventoryCardProps {
  item: InventoryItem;
  index: number;
  isSelected: boolean;
  isExpanded: boolean;
  score?: RecommendationScore;
  campaignCurrency?: string;
  /** Availability % over the campaign range for this inventory. */
  availability?: AvailabilityEntry;
  showViewDetailsButton?: boolean;
  showDeleteInventoryButton?: boolean;
  onCardClick: () => void;
  onToggleExpand: () => void;
  onViewDetails?: () => void;
  onDelete?: () => void;
}

/** Coerce a tag field (string | string[] | other) to a trimmed display string. */
const toTag = (value: unknown): string => {
  if (typeof value === "string") return value.trim();
  if (Array.isArray(value)) return value.filter(Boolean).join(", ").trim();
  if (value != null && typeof value !== "object") return String(value).trim();
  return "";
};

/** A single inventory row card in the View / Manual map list. */
const SelectedInventoryCard: React.FC<SelectedInventoryCardProps> = ({
  item,
  index,
  isSelected,
  isExpanded,
  score,
  campaignCurrency,
  availability,
  showViewDetailsButton = false,
  showDeleteInventoryButton = false,
  onCardClick,
  onToggleExpand,
  onViewDetails,
  onDelete,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);

  // Venue types shown the same way as sizes: first MAX_VISIBLE_VENUE_TYPES
  // inline, rest collapsed to "+N" with a hover tooltip listing all.
  const venue = getChipOverflow(item.detail.venueType, MAX_VISIBLE_VENUE_TYPES);

  return (
    <Card
      className={clsx(
        "cursor-pointer transition-colors",
        isSelected ? "bg-[#E8F0F7]!" : "bg-white",
      )}
      onClick={onCardClick}
    >
      <CardContent className="p-2! space-y-2">
        {/* Name + actions */}
        <div className="flex items-start justify-between gap-2">
          <h3 className="text-base font-semibold text-mw-grey-900 leading-snug truncate">
            {item.detail.name || ""}
          </h3>
          <div className="flex items-center gap-3 shrink-0 text-mw-neutral-600">
            {showViewDetailsButton && (
              <button
                className="hover:text-mw-primary-600"
                onClick={(e) => {
                  e.stopPropagation();
                  onViewDetails?.();
                }}
                aria-label={tCampaigns("inventories.item.view_details")}
              >
                <Eye className="size-5" />
              </button>
            )}
            {showDeleteInventoryButton && (
              <button
                className="text-mw-error-500 hover:text-mw-error-600"
                onClick={(e) => {
                  e.stopPropagation();
                  onDelete?.();
                }}
              >
                <Trash2 className="size-4" />
              </button>
            )}
            <button
              className="hover:text-mw-grey-800"
              onClick={(e) => {
                e.stopPropagation();
                onToggleExpand();
              }}
              aria-label="toggle"
            >
              <ChevronDown
                className={clsx(
                  "size-5 transition-transform",
                  isExpanded && "rotate-180",
                )}
              />
            </button>
          </div>
        </div>

        {/* Serial + smart score */}
        <div className="flex items-center gap-2 flex-wrap">
          <span className="rounded-md bg-mw-neutral-100 px-2.5 py-1 text-xs font-medium text-mw-grey-700">
            INV - {String(index + 1).padStart(3, "0")}
          </span>
          {score && (
            <Tooltip content={<FitnessScoreCard scores={score.components} />}>
              <span className="inline-flex items-center gap-1 rounded-md bg-gradient-to-r from-mw-purple-warning-100 to-mw-purple-warning-50 px-2.5 py-1 text-xs font-medium text-mw-purple-warning-600">
                <Sparkles className="size-3.5" />
                {tCampaigns("inventoryMapView.smartScore")} - {score.score}
              </span>
            </Tooltip>
          )}
          {score?.availability &&
            !score.availability.allAvailable &&
            (score.availability.totalDays ?? 0) > 0 && (
              <Tooltip
                content={
                  score.availability.summary ||
                  tCampaigns("inventoryDetailCard.limitedAvailability")
                }
              >
                <span
                  className="inline-flex items-center gap-1 rounded-md bg-mw-warning-50 px-2.5 py-1 text-xs font-medium text-mw-warning-600"
                  data-testid={`badge-limited-availability-${item.detail.referenceId || item.detail.id}`}
                >
                  <CalendarClock className="size-3.5" />
                  {tCampaigns("inventoryDetailCard.limitedAvailability")} (
                  {score.availability.availableDays}/
                  {score.availability.totalDays})
                </span>
              </Tooltip>
            )}
        </div>

        {/* Address */}
        <p className="text-sm text-mw-neutral-500 leading-snug">
          {item.location?.location?.address ||
            [item.location?.location?.country, item.location?.location?.state]
              .filter(Boolean)
              .join(", ") ||
            "--"}
        </p>

        {/* Tags */}
        <div className="flex flex-wrap gap-2">
          {toTag(item.detail.inventoryType) && (
            <Badge
              variant="outline"
              size="sm"
              className="outline-mw-primary-500 text-mw-primary-500"
            >
              {toTag(item.detail.inventoryType)}
            </Badge>
          )}
          {venue.count > 0 &&
            (venue.overflowCount > 0 ? (
              <Tooltip
                content={
                  <div className="flex flex-col">
                    {venue.labels.map((v) => (
                      <span key={v}>{v}</span>
                    ))}
                  </div>
                }
              >
                <Badge
                  variant="outline"
                  size="sm"
                  className="outline-mw-success-500 text-mw-success-500"
                >
                  {`${venue.visibleText} +${venue.overflowCount}`}
                </Badge>
              </Tooltip>
            ) : (
              <Badge
                variant="outline"
                size="sm"
                className="outline-mw-success-500 text-mw-success-500"
              >
                {venue.visibleText}
              </Badge>
            ))}
          {toTag(item.detail.environment) && (
            <Badge
              variant="outline"
              size="sm"
              className="outline-mw-rose-warning-400 text-mw-rose-warning-400"
            >
              {toTag(item.detail.environment)}
            </Badge>
          )}
          {item.detail.size && (
            <Badge
              variant="outline"
              size="sm"
              className="outline-mw-orange-warning-500 text-mw-orange-warning-500"
            >
              {item.detail.size.toUpperCase()}
            </Badge>
          )}
        </div>

        {/* Expanded: key metrics */}
        {isExpanded && (
          <div className="border-t border-mw-neutral-100 pt-3 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-sm text-mw-neutral-500">
                {tCampaigns("inventoryMapView.mediaOwner")}
              </span>
              <span className="text-sm font-medium text-mw-grey-900">
                {item.detail.mediaOwnerName || "--"}
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-mw-neutral-500">
                {tCampaigns("inventoryMapView.cpm")}
              </span>
              <span className="text-sm font-medium text-mw-grey-900">
                {formatCurrencyWithLocale(
                  item.performance.cpmRate,
                  campaignCurrency,
                )}
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-mw-neutral-500">
                {tCampaigns("inventoryMapView.impression")}
              </span>
              <span className="text-sm font-medium text-mw-grey-900">
                {(
                  item.performance.estimatedImpressions ??
                  item.performance.estimatedImpression ??
                  0
                ).toLocaleString()}
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-mw-neutral-500">
                {tCampaigns("inventoryMapView.availability")}
              </span>
              <span className="text-sm font-medium text-mw-success-500">
                {availability?.loading ? (
                  <Spinner size="sm" variant="primary" />
                ) : availability?.percent != null ? (
                  `${availability.percent}%`
                ) : (
                  "--"
                )}
              </span>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
};

export default SelectedInventoryCard;
