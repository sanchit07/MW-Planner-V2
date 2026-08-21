import { Card, CardContent } from "@components/ui/card";
import { useTranslate } from "@tolgee/react";
import React from "react";
import { Targeting } from "src/types/campaign.types";

import { PresentationTheme } from "./types";
import { getThemePrimaryBackgroundStyle } from "./utils";

interface MediaPlanTargetingProps {
  targeting?: Targeting;
  theme?: PresentationTheme;
}

/** "18_24" → "18-24", "lower_middle" → "Lower Middle", "male" → "Male". */
const titleCase = (code: string): string =>
  code
    .split(/[-_]/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");

const formatAge = (code: string): string => code.replace(/_/g, "-");

const Chips: React.FC<{
  items: string[];
  emptyLabel: string;
}> = ({ items, emptyLabel }) =>
  items.length > 0 ? (
    <div className="flex flex-wrap gap-1.5">
      {items.map((item) => (
        <span
          key={item}
          className="rounded-full border border-container-border bg-mw-neutral-50 px-2.5 py-0.5 text-xs text-mw-neutral-700"
        >
          {item}
        </span>
      ))}
    </div>
  ) : (
    <span className="text-sm text-mw-neutral-400">{emptyLabel}</span>
  );

const MediaPlanTargetingComponent: React.FC<MediaPlanTargetingProps> = ({
  targeting,
  theme,
}) => {
  const { t } = useTranslate(["campaigns"]);
  const demographics = targeting?.demographics;
  const emptyLabel = t("media_plan.targeting_card.not_selected");

  const ageChips = (demographics?.age || []).map(formatAge);
  const genderChips = (demographics?.gender || []).map(titleCase);
  const incomeChips = (demographics?.income || []).map(titleCase);
  const behaviourChips = demographics?.behavior || [];
  const interestChips = demographics?.interests || [];

  // Unique top-level venue categories across digital + classic (e.g. "transit"
  // / "retail" → "Transit" / "Retail"); the raw codes are too granular to chip.
  const venueChips = Array.from(
    new Set(
      [
        ...(targeting?.venueTypes?.digitalOoh || []),
        ...(targeting?.venueTypes?.classicOoh || []),
      ].map((code) => code.split("-")[0]),
    ),
  ).map(titleCase);

  return (
    <Card id="media-plan-targeting-card" className="mt-4 overflow-hidden p-0">
      {/* Section banner (theme-primary background) */}
      <div
        id="media-plan-targeting-header"
        className="px-6 py-5 text-white"
        style={getThemePrimaryBackgroundStyle(theme)}
      >
        <h2
          id="media-plan-targeting-title"
          className="text-2xl font-bold leading-8"
        >
          {t("media_plan.targeting_card.title")}
        </h2>
        <p id="media-plan-targeting-subtitle" className="text-sm text-white/80">
          {t("media_plan.targeting_card.subtitle")}
        </p>
      </div>

      <CardContent id="media-plan-targeting-content" className="mt-4 p-6">
        <div className="grid min-h-[41.5rem] grid-cols-1 items-stretch gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {/* Demographics */}
          <div
            id="media-plan-targeting-demographics"
            className="flex flex-col gap-4 rounded-lg border border-container-border p-4"
          >
            <div>
              <p className="text-xs font-medium uppercase tracking-wider text-mw-neutral-500">
                {t("media_plan.targeting_card.demographics")}
              </p>
              <p className="text-xs text-mw-neutral-400">
                {t("media_plan.targeting_card.demographics_sub")}
              </p>
            </div>
            <div className="flex flex-col gap-1.5">
              <p className="text-[11px] uppercase tracking-wider text-mw-neutral-400">
                {t("media_plan.targeting_card.age")}
              </p>
              <Chips items={ageChips} emptyLabel={emptyLabel} />
            </div>
            <div className="flex flex-col gap-1.5">
              <p className="text-[11px] uppercase tracking-wider text-mw-neutral-400">
                {t("media_plan.targeting_card.gender")}
              </p>
              <Chips items={genderChips} emptyLabel={emptyLabel} />
            </div>
            <div className="flex flex-col gap-1.5">
              <p className="text-[11px] uppercase tracking-wider text-mw-neutral-400">
                {t("media_plan.targeting_card.income")}
              </p>
              <Chips items={incomeChips} emptyLabel={emptyLabel} />
            </div>
          </div>

          {/* Venue Types */}
          <div
            id="media-plan-targeting-venue-types"
            className="flex flex-col gap-4 rounded-lg border border-container-border p-4"
          >
            <div>
              <p className="text-xs font-medium uppercase tracking-wider text-mw-neutral-500">
                {t("media_plan.targeting_card.venue_types")}
              </p>
              <p className="text-xs text-mw-neutral-400">
                {t("media_plan.targeting_card.venue_types_sub")}
              </p>
            </div>
            <Chips items={venueChips} emptyLabel={emptyLabel} />
          </div>

          {/* Behaviour */}
          <div
            id="media-plan-targeting-behaviour"
            className="flex flex-col gap-4 rounded-lg border border-container-border p-4"
          >
            <div>
              <p className="text-xs font-medium uppercase tracking-wider text-mw-neutral-500">
                {t("media_plan.targeting_card.behaviour")}
              </p>
              <p className="text-xs text-mw-neutral-400">
                {t("media_plan.targeting_card.behaviour_sub")}
              </p>
            </div>
            <Chips items={behaviourChips} emptyLabel={emptyLabel} />
          </div>

          {/* Interests */}
          <div
            id="media-plan-targeting-interests"
            className="flex flex-col gap-4 rounded-lg border border-container-border p-4"
          >
            <div>
              <p className="text-xs font-medium uppercase tracking-wider text-mw-neutral-500">
                {t("media_plan.targeting_card.interests")}
              </p>
              <p className="text-xs text-mw-neutral-400">
                {t("media_plan.targeting_card.interests_sub")}
              </p>
            </div>
            <Chips items={interestChips} emptyLabel={emptyLabel} />
          </div>
        </div>
      </CardContent>
    </Card>
  );
};

export default MediaPlanTargetingComponent;
