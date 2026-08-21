import { Card } from "@components/ui/card";
import { StatusBadge } from "@components/ui/StatusBadge";
import { useTranslate } from "@tolgee/react";
import { formatDisplayDate } from "@utils/dateUtils";
import React from "react";

import { PresentationTheme } from "./types";
import { MediaPlanHeaderInfo, MediaPlanBrandDetails } from "./types";
import backLogo from "../../../assets/images/media-plan-bg.jpg";

interface MediaPlanTitleSlideProps {
  headerInfo?: MediaPlanHeaderInfo;
  brandDetails?: MediaPlanBrandDetails;
  theme: PresentationTheme;
}

const MS_PER_DAY = 1000 * 60 * 60 * 24;

const MediaPlanTitleSlide: React.FC<MediaPlanTitleSlideProps> = ({
  headerInfo,
  brandDetails,
}) => {
  const { t } = useTranslate(["campaigns"]);
  const { t: tCommon } = useTranslate(["common"]);

  const hasDates = Boolean(headerInfo?.startDate && headerInfo?.endDate);
  const durationDays =
    headerInfo?.startDate && headerInfo?.endDate
      ? Math.floor(
          (new Date(headerInfo.endDate).getTime() -
            new Date(headerInfo.startDate).getTime()) /
            MS_PER_DAY,
        ) + 1
      : null;

  const status = headerInfo?.status?.toLowerCase() || "draft";

  return (
    <Card
      id="media-plan-title-slide-card"
      className="mt-4 overflow-hidden border-0 bg-transparent p-0 shadow-none"
    >
      <div
        id="media-plan-title-slide-container"
        className="relative aspect-[16/9] w-full overflow-hidden rounded-lg border-y-5 border-mw-primary-500 bg-mw-neutral-900"
      >
        {/* Background Image (full-bleed) */}
        <div
          className="absolute inset-0"
          style={{
            backgroundImage: `url('${backLogo}')`,
            backgroundSize: "cover",
            backgroundPosition: "center",
          }}
        />

        {/* Fixed dark overlay for legibility */}
        <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/45 to-black/25" />

        {/* Content */}
        <div
          id="media-plan-title-slide-content"
          className="relative z-10 h-full flex flex-col justify-between p-8 text-white"
        >
          {/* Top row: logo + brand */}
          <div
            id="media-plan-title-slide-header"
            className="flex justify-between items-start"
          >
            <div
              id="media-plan-title-slide-logo-mw"
              className="flex items-center gap-2"
            >
              <div className="w-9 h-9 bg-white rounded-md flex items-center justify-center">
                <span className="font-bold text-sm text-mw-primary-600">
                  MP
                </span>
              </div>
              <div className="leading-tight">
                <p className="font-semibold text-sm uppercase tracking-wide">
                  {t("media_plan.title_slide.moving_walls_internal")}
                </p>
                <p className="text-[10px] text-white/70">
                  {t("media_plan.title_slide.moving_walls")}
                </p>
              </div>
            </div>

            {brandDetails?.name && (
              <div
                id="media-plan-title-slide-brand"
                className="flex items-center gap-2"
              >
                <span
                  id="media-plan-title-slide-brand-name"
                  className="font-semibold text-sm tracking-wide"
                >
                  {brandDetails.name}
                </span>
                <div className="w-9 h-9 bg-white rounded-md flex items-center justify-center">
                  <span className="font-bold text-sm text-mw-neutral-900">
                    {brandDetails.name?.[0]?.toLocaleUpperCase() || ""}
                  </span>
                </div>
              </div>
            )}
          </div>

          {/* Title (vertically centered, left-aligned) */}
          <div className="flex-1 flex items-center">
            <h1
              id="media-plan-title-slide-campaign-name"
              className="text-4xl font-bold leading-tight max-w-[70%]"
            >
              {headerInfo?.name || ""}
            </h1>
          </div>

          {/* Bottom info row */}
          <div
            id="media-plan-title-slide-footer"
            className="flex justify-between items-end"
          >
            {/* Plan dates */}
            <div id="media-plan-title-slide-plan-dates">
              <p className="text-[11px] uppercase tracking-wider text-white/70">
                {t("media_plan.title_slide.plan_dates")}
              </p>
              {hasDates && (
                <>
                  <p
                    id="media-plan-title-slide-campaign-period-value"
                    className="text-base font-semibold"
                  >
                    {formatDisplayDate(headerInfo!.startDate!, tCommon)} –{" "}
                    {formatDisplayDate(headerInfo!.endDate!, tCommon)}
                  </p>
                  {durationDays !== null && (
                    <p
                      id="media-plan-title-slide-duration"
                      className="text-xs text-white/70"
                    >
                      {t("media_plan.title_slide.days", {
                        count: durationDays,
                      })}
                    </p>
                  )}
                </>
              )}
            </div>

            {/* Prepared by + status */}
            <div
              id="media-plan-title-slide-prepared-by"
              className="flex flex-col items-end gap-1 text-right"
            >
              <p className="text-[11px] uppercase tracking-wider text-white/70">
                {t("media_plan.title_slide.planned_by")}
              </p>
              {headerInfo?.preparedBy && (
                <p
                  id="media-plan-title-slide-prepared-by-value"
                  className="text-base font-semibold"
                >
                  {headerInfo.preparedBy}
                </p>
              )}
              <p className="text-xs text-white/70">
                {t("media_plan.title_slide.moving_walls_internal")}
              </p>
              {headerInfo?.status && (
                <div id="media-plan-title-slide-status">
                  <StatusBadge status={status}>
                    {t(`campaignsList.status.${headerInfo.status}`) ||
                      headerInfo.status}
                  </StatusBadge>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </Card>
  );
};

export default MediaPlanTitleSlide;
