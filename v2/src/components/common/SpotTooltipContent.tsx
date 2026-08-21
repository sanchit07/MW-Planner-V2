import { useTranslate } from "@tolgee/react";
import type { SpotData } from "@utils/inventoryavailability.utils";
import {
  formatDateForTooltip,
  formatTimeForTooltip,
} from "@utils/inventoryAvailabilityUI.utils";
import React from "react";
import type { Booking } from "src/types/price-management.types";

export interface SpotTooltipContentProps {
  spotData: SpotData;
  date?: Date | null;
  hour?: number;
  isClassic: boolean;
  isParentRow?: boolean;
  bookingDetails?: Booking[];
  spotLabel?: string;
  campaignStartDate?: string | Date | null;
  campaignEndDate?: string | Date | null;
  isDailyView: boolean;
}

export const SpotTooltipContent: React.FC<SpotTooltipContentProps> = ({
  spotData,
  date,
  hour,
  isClassic,
  isParentRow = false,
  bookingDetails,
  spotLabel,
  campaignStartDate,
  campaignEndDate,
  isDailyView,
}) => {
  const { t: tPrice } = useTranslate(["price"]);
  if (isClassic) {
    const status =
      spotData.bookedSpots > 0
        ? tPrice("calendar.legend.booked")
        : spotData.reservedSpots > 0
          ? tPrice("calendar.legend.reserved")
          : tPrice("calendar.legend.available");
    const statusColor =
      spotData.bookedSpots > 0
        ? "text-mw-error-500"
        : spotData.reservedSpots > 0
          ? "text-mw-primary-500"
          : "text-emerald-500";

    return (
      <div className="bg-white rounded-lg min-w-[180px] text-xs">
        <div className="font-semibold text-mw-neutral-900 mb-2">
          {date ? formatDateForTooltip(date) : ""}
        </div>
        {hour !== undefined && !isParentRow && (
          <div className="text-[10px] text-mw-neutral-600 mb-2">
            {formatTimeForTooltip(hour)} -{" "}
            {formatTimeForTooltip(hour + 1 >= 24 ? 23 : hour + 1)}
          </div>
        )}
        <div className="flex items-center gap-2">
          <span
            className={`w-2.5 h-2.5 rounded-full ${
              spotData.bookedSpots > 0
                ? "bg-mw-error-500"
                : spotData.reservedSpots > 0
                  ? "bg-mw-primary-500"
                  : "bg-emerald-500"
            }`}
          />
          <span className={`font-semibold ${statusColor}`}>{status}</span>
        </div>
        {bookingDetails &&
          bookingDetails.length > 0 &&
          (spotData.bookedSpots > 0 || spotData.reservedSpots > 0) && (
            <div className="mt-3 pt-2 border-t border-container-border space-y-1">
              <div className="flex justify-between">
                <span className="text-mw-neutral-500">
                  {tPrice("calendar.tooltip.campaign")}:
                </span>
                <span className="font-medium text-mw-neutral-900">
                  {bookingDetails[0].dealName || tPrice("calendar.tooltip.na")}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-mw-neutral-500">
                  {tPrice("calendar.tooltip.brand")}:
                </span>
                <span className="font-medium text-mw-neutral-900">
                  {bookingDetails[0].brand || tPrice("calendar.tooltip.na")}
                </span>
              </div>
            </div>
          )}
      </div>
    );
  }

  const isHourlyView = isDailyView && hour !== undefined && !isParentRow;
  const bookedSpots = spotData.bookedSpots + spotData.reservedSpots;
  let showCampaignDetails =
    isHourlyView &&
    bookingDetails &&
    bookingDetails.length > 0 &&
    (spotData.bookedSpots > 0 || spotData.reservedSpots > 0);
  showCampaignDetails =
    showCampaignDetails && bookedSpots > spotData.availableSpots;

  if (
    showCampaignDetails &&
    date &&
    hour !== undefined &&
    spotLabel &&
    bookingDetails?.length
  ) {
    const booking = bookingDetails[0];
    const nextHour = hour + 1;
    const endTime = nextHour >= 24 ? "23:59" : formatTimeForTooltip(nextHour);

    const cStartDate = campaignStartDate
      ? formatDateForTooltip(new Date(campaignStartDate))
      : booking.slots[0]?.startTime
        ? formatDateForTooltip(new Date(booking.slots[0].startTime))
        : "";

    const cEndDate = campaignEndDate
      ? formatDateForTooltip(new Date(campaignEndDate))
      : booking.slots[0]?.endTime
        ? formatDateForTooltip(new Date(booking.slots[0].endTime))
        : "";

    const totalSpots = spotData.totalSpots;
    const sov =
      totalSpots > 0 ? Math.round((bookedSpots / totalSpots) * 100) : 0;

    return (
      <div className="bg-white rounded-lg min-w-[250px] text-xs">
        <div className="text-base font-semibold text-mw-neutral-900 mb-2">
          {formatDateForTooltip(date)}
        </div>
        <div className="text-[10px] text-mw-neutral-700 pb-2 border-b border-container-border mb-4">
          {spotLabel} - {formatTimeForTooltip(hour)}{" "}
          {tPrice("calendar.tooltip.to")} {endTime}
        </div>
        <div className="space-y-2">
          <div className="flex justify-between items-start">
            <span className="text-mw-neutral-500">
              {tPrice("calendar.tooltip.campaign")}:
            </span>
            <span className="font-medium text-mw-neutral-900 text-right">
              {booking.dealName || tPrice("calendar.tooltip.na")}
            </span>
          </div>
          <div className="flex justify-between items-start">
            <span className="text-mw-neutral-500">
              {tPrice("calendar.tooltip.agency")}:
            </span>
            <span className="font-medium text-mw-neutral-900 text-right">
              {booking.agency || tPrice("calendar.tooltip.na")}
            </span>
          </div>
          <div className="flex justify-between items-start">
            <span className="text-mw-neutral-500">
              {tPrice("calendar.tooltip.brand")}:
            </span>
            <span className="font-medium text-mw-neutral-900 text-right">
              {booking.brand || tPrice("calendar.tooltip.na")}
            </span>
          </div>
          <div className="flex justify-between items-start">
            <span className="text-mw-neutral-500">
              {tPrice("calendar.tooltip.start_date")}:
            </span>
            <span className="font-medium text-mw-neutral-900 text-right">
              {cStartDate || tPrice("calendar.tooltip.na")}
            </span>
          </div>
          <div className="flex justify-between items-start">
            <span className="text-mw-neutral-500">
              {tPrice("calendar.tooltip.end_date")}:
            </span>
            <span className="font-medium text-mw-neutral-900 text-right">
              {cEndDate || tPrice("calendar.tooltip.na")}
            </span>
          </div>
          <div className="flex justify-between items-start">
            <span className="text-mw-neutral-500">
              {tPrice("calendar.tooltip.sov")}:
            </span>
            <span className="font-medium text-mw-neutral-900 text-right">
              {sov}%
            </span>
          </div>
        </div>
      </div>
    );
  }

  const isDailyViewWithSpots =
    isHourlyView && spotData.totalSpotsPerDay !== undefined;
  const displayTotalSpots = isDailyViewWithSpots
    ? (spotData.totalSpotsPerDay ?? spotData.totalSpots)
    : spotData.totalSpots;
  const displayAvailableSpots = isDailyViewWithSpots
    ? (spotData.availableSpotsPerDay ?? spotData.availableSpots)
    : spotData.availableSpots;
  const displayBookedSpots = isDailyViewWithSpots
    ? (spotData.bookedSpotsPerDay ?? spotData.bookedSpots)
    : spotData.bookedSpots;
  const displayReservedSpots = isDailyViewWithSpots
    ? (spotData.reservedSpotsPerDay ?? spotData.reservedSpots)
    : spotData.reservedSpots;

  return (
    <div className="bg-white rounded-lg min-w-[180px] text-xs">
      <div className="font-semibold text-mw-neutral-900 mb-1">
        {date ? formatDateForTooltip(date) : ""}
      </div>
      <div className="text-mw-neutral-600 text-[10px] pb-1 mb-2 border-b border-container-border">
        {displayTotalSpots} {tPrice("calendar.tooltip.total_spots")}
      </div>
      <div className="space-y-1.5">
        <div className="flex items-center gap-2">
          <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
          <span className="text-mw-neutral-600">
            {tPrice("calendar.tooltip.available_spots")}
          </span>
          <span className="ml-auto font-semibold text-mw-neutral-900">
            {displayAvailableSpots}
          </span>
        </div>
        <div className="flex items-center gap-2">
          <span className="w-2.5 h-2.5 rounded-full bg-mw-error-500" />
          <span className="text-mw-neutral-600">
            {tPrice("calendar.tooltip.booked_spots")}
          </span>
          <span className="ml-auto font-semibold text-mw-neutral-900">
            {displayBookedSpots}
          </span>
        </div>
        <div className="flex items-center gap-2">
          <span className="w-2.5 h-2.5 rounded-full bg-blue-500" />
          <span className="text-mw-neutral-600">
            {tPrice("calendar.tooltip.reserved_spots")}
          </span>
          <span className="ml-auto font-semibold text-mw-neutral-900">
            {displayReservedSpots}
          </span>
        </div>
      </div>
    </div>
  );
};
