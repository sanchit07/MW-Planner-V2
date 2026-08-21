import InventoryThumbnail from "@components/common/InventoryThumbnail";
import { Card, CardContent } from "@components/ui/card";
import { useTranslate } from "@tolgee/react";
import { normalizeGoalType } from "@utils/budget.utils";
import { formatCurrency } from "@utils/campaign.utils";
import { formatCompactNumber } from "@utils/dashboard.utils";
import React from "react";
import { InventoryItem } from "src/types/inventory.types";

import {
  MediaPlanHeaderInfo,
  PresentationTheme,
  SelectedInventory,
} from "./types";
import { getThemePrimaryBackgroundStyle } from "./utils";

interface MediaPlanInventorySnapshotsProps {
  selectedInventory?: SelectedInventory;
  headerInfo?: MediaPlanHeaderInfo;
  /** Campaign goal — SOV/ADPLAYS price on CPS (spot rate), else CPM. */
  goalType?: string;
  theme?: PresentationTheme;
}

/** Panels shown per Inventory Snapshots block (3 columns × 2 rows). */
const PANELS_PER_BLOCK = 6;

/** Per-item impressions from the /selected-inventory API's performance object. */
const impressionsOf = (item: InventoryItem): number => {
  const perf = item.performance as
    | { estimatedImpressions?: number; estimatedImpression?: number }
    | undefined;
  return perf?.estimatedImpression ?? perf?.estimatedImpressions ?? 0;
};

/** Media-owner avatar initials — first letters of the first two words,
 * ignoring separators. "Urban Displays - Chicago" → "UD", "Jeki" → "J". */
const ownerInitials = (name: string): string =>
  name
    .split(/[^A-Za-z0-9]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]!.toUpperCase())
    .join("") || "—";

const channelLabel = (inventoryType?: string): string => {
  const t = (inventoryType || "").toLowerCase();
  if (t.includes("classic")) return "Classic";
  if (t.includes("digital")) return "Digital";
  if (t.includes("cinema")) return "Cinema";
  if (t.includes("retail")) return "Retail";
  return inventoryType || "";
};

const MediaPlanInventorySnapshotsComponent: React.FC<
  MediaPlanInventorySnapshotsProps
> = ({ selectedInventory, headerInfo, goalType, theme }) => {
  const { t } = useTranslate(["campaigns"]);
  const currency = headerInfo?.currency;
  // Same rule as the manual-edit inventory card: SOV / AD_PLAYS goals are
  // priced per spot (CPS = spotRate); everything else shows CPM.
  const normalizedGoal = normalizeGoalType(goalType);
  const isCPSGoal = normalizedGoal === "SOV" || normalizedGoal === "ADPLAYS";

  const items = [...(selectedInventory?.locations || [])].sort(
    (a, b) => impressionsOf(b) - impressionsOf(a),
  );

  const renderCard = (item: InventoryItem, key: React.Key) => {
    const impressions = impressionsOf(item);
    const playsPerDay = item.performance?.perDayAdPlays || 0;
    const cost = item.performance?.estimatedCost || 0;
    const cpm = item.performance?.cpmRate || 0;
    const spotRate = item.performance?.spotRate;
    const sov = item.performance?.sov;
    const city = item.location?.location?.city || "";
    const type = channelLabel(item.detail?.inventoryType);
    const format = item.detail?.format || "";
    const badge = type.toUpperCase();
    const metaLine = [[type, format].filter(Boolean).join(" "), city]
      .filter(Boolean)
      .join(" · ");
    const images = item.detail?.images?.length
      ? item.detail.images.slice(0, 2)
      : [];
    const metric = (label: string, value: string) => (
      <div className="leading-none">
        <p className="text-[9px] uppercase tracking-wider text-mw-neutral-400">
          {label}
        </p>
        <p className="mt-0.5 text-sm font-bold text-mw-neutral-900">{value}</p>
      </div>
    );
    return (
      <div
        key={key}
        className="overflow-hidden rounded-xl border border-container-border"
      >
        {/* Image strip + channel badge */}
        <div className="relative flex h-28 w-full bg-mw-neutral-100">
          {images.length > 0 ? (
            images.map((src, i) => (
              <InventoryThumbnail
                key={`${item.detail?.referenceId || "inventory"}-${i}`}
                src={src}
                alt={item.detail?.name || ""}
                className="h-28 flex-1 object-cover"
              />
            ))
          ) : (
            <InventoryThumbnail
              src={item.detail?.thumbnail}
              alt={item.detail?.name || ""}
              className="h-28 w-full object-cover"
            />
          )}
          <span
            className="absolute left-3 top-3 rounded-md px-2.5 py-1 text-xs font-bold text-white"
            style={getThemePrimaryBackgroundStyle(theme)}
          >
            {badge}
          </span>
        </div>

        <div className="p-3">
          <p className="truncate text-base font-bold leading-tight text-mw-neutral-900">
            {item.detail?.name}
          </p>
          <p className="truncate text-xs leading-tight text-mw-neutral-500">
            {metaLine}
          </p>

          <div className="mt-2 grid grid-cols-3 gap-x-3 gap-y-1.5">
            {metric(
              t("media_plan.inventory_snapshots.impressions"),
              formatCompactNumber(impressions),
            )}
            {metric(
              t("media_plan.inventory_snapshots.plays_day"),
              type === "Classic" ? "-" : formatCompactNumber(playsPerDay),
            )}
            {isCPSGoal
              ? metric(
                  t("media_plan.inventory_snapshots.cps"),
                  spotRate != null ? formatCurrency(spotRate, currency) : "—",
                )
              : metric(
                  t("media_plan.inventory_snapshots.cpm"),
                  formatCurrency(cpm, currency),
                )}
            {metric(
              t("media_plan.inventory_snapshots.cost"),
              formatCurrency(cost, currency),
            )}
            {metric(
              t("media_plan.inventory_snapshots.sov"),
              sov != null ? `${sov.toFixed(1)}%` : "—",
            )}
          </div>

          {item.detail?.mediaOwnerName && (
            <div className="mt-2 flex items-center gap-2 border-t border-container-border pt-2">
              <span className="flex h-5 w-5 items-center justify-center rounded bg-mw-primary-50 text-[9px] font-bold text-mw-primary-600">
                {ownerInitials(item.detail.mediaOwnerName)}
              </span>
              <span className="truncate text-xs text-mw-neutral-600">
                {item.detail.mediaOwnerName}
              </span>
            </div>
          )}
        </div>
      </div>
    );
  };

  // Empty state — a single banner block.
  if (items.length === 0) {
    return (
      <Card
        id="media-plan-inventory-snapshots-card"
        className="mt-4 overflow-hidden p-0"
      >
        <div
          id="media-plan-inventory-snapshots-header"
          className="px-6 py-5 text-white"
          style={getThemePrimaryBackgroundStyle(theme)}
        >
          <h2 className="text-2xl font-bold leading-8">
            {t("media_plan.inventory_snapshots.title")}
          </h2>
        </div>
        <CardContent className="mt-4 p-6">
          <p
            id="media-plan-inventory-snapshots-empty"
            className="py-6 text-center text-sm text-mw-neutral-400"
          >
            {t("media_plan.inventory_snapshots.empty")}
          </p>
        </CardContent>
      </Card>
    );
  }

  // Paginate into blocks of 6 (3×2); one full banner section per block.
  const blocks: InventoryItem[][] = [];
  for (let i = 0; i < items.length; i += PANELS_PER_BLOCK) {
    blocks.push(items.slice(i, i + PANELS_PER_BLOCK));
  }

  return (
    <>
      {blocks.map((block, bi) => {
        const start = bi * PANELS_PER_BLOCK + 1;
        const end = bi * PANELS_PER_BLOCK + block.length;
        return (
          <Card
            key={`block-${bi}`}
            id={bi === 0 ? "media-plan-inventory-snapshots-card" : undefined}
            className="mt-4 overflow-hidden p-0"
          >
            <div
              id={
                bi === 0 ? "media-plan-inventory-snapshots-header" : undefined
              }
              className="px-6 py-5 text-white"
              style={getThemePrimaryBackgroundStyle(theme)}
            >
              <h2 className="text-2xl font-bold leading-8">
                {t("media_plan.inventory_snapshots.title")}
              </h2>
              <p className="text-sm text-white/80">
                {t("media_plan.inventory_snapshots.subtitle", {
                  start,
                  end,
                  total: items.length,
                })}
              </p>
            </div>

            <CardContent className="mt-4 p-6">
              <div
                id={
                  bi === 0 ? "media-plan-inventory-snapshots-grid" : undefined
                }
                className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3"
              >
                {block.map((item, i) =>
                  renderCard(item, item.detail?.referenceId || `${bi}-${i}`),
                )}
              </div>

              {/* Filler so a single-row block still matches the taller
                  two-row blocks / PPT slide height. */}
              {block.length <= 3 && <div aria-hidden className="h-70" />}

              <p
                className="mt-4 border-t border-container-border pt-4 text-xs leading-5"
                style={{ color: "hsl(var(--muted-foreground))" }}
              >
                {t("media_plan.inventory_snapshots.note", {
                  total: items.length,
                  shown: block.length,
                })}
              </p>
            </CardContent>
          </Card>
        );
      })}
    </>
  );
};

export default MediaPlanInventorySnapshotsComponent;
