import { useTranslate } from "@tolgee/react";
import { getPlaceTypeInfo, getPlaceTypeLabel } from "@utils/place-type-utils";
import { Star, StarHalf } from "lucide-react";
import React from "react";
import { POIPlaceData } from "src/types/campaign.types";

interface GeoFencingPOIPopupProps {
  poi: POIPlaceData;
  photoUrl?: string | null;
}

/** Render a 0–5 rating as five stars (full / half / empty) plus the number. */
const StarRating: React.FC<{ rating: number }> = ({ rating }) => {
  const full = Math.floor(rating);
  const hasHalf = rating - full >= 0.5;
  return (
    <span className="flex items-center gap-0.5" data-testid="poi-popup-rating">
      {Array.from({ length: 5 }).map((_, i) => {
        if (i < full)
          return (
            <Star
              key={i}
              className="h-3.5 w-3.5 fill-mw-warning-400 text-mw-warning-400"
            />
          );
        if (i === full && hasHalf)
          return (
            <StarHalf
              key={i}
              className="h-3.5 w-3.5 fill-mw-warning-400 text-mw-warning-400"
            />
          );
        return <Star key={i} className="h-3.5 w-3.5 text-mw-neutral-300" />;
      })}
      <span className="ml-1 text-xs font-semibold text-mw-primary-700">
        {rating.toFixed(1)}
      </span>
    </span>
  );
};

const GeoFencingPOIPopup: React.FC<GeoFencingPOIPopupProps> = ({
  poi,
  photoUrl,
}) => {
  const { t } = useTranslate("campaigns");
  const typeInfo = getPlaceTypeInfo(poi.primaryType ?? null);
  const TypeIcon = typeInfo.Icon;
  // Google's new Places API occasionally returns `displayName` / `address`
  // as `{ text, languageCode }` objects instead of plain strings. Unwrap
  // here as a safety net for older entries already in Redux.
  const toText = (v: unknown): string => {
    if (typeof v === "string") return v;
    if (v && typeof v === "object" && "text" in v) {
      const t = (v as { text?: unknown }).text;
      return typeof t === "string" ? t : "";
    }
    return "";
  };
  const displayName = toText(poi.displayName);
  const address = toText(poi.address);
  const category =
    toText(poi.primaryTypeDisplayName) ||
    getPlaceTypeLabel(poi.primaryType ?? null);
  const title = displayName || category || "Unknown place";
  const hasRating = typeof poi.rating === "number" && poi.rating > 0;

  return (
    <div
      data-testid="geofencing-poi-popup"
      className="min-w-[260px] max-w-[300px] bg-white rounded-lg overflow-hidden"
    >
      <div
        className={`relative h-24 w-full flex items-center justify-center ${typeInfo.bgClass}`}
      >
        <TypeIcon className={`h-10 w-10 ${typeInfo.colorClass} opacity-40`} />
        {photoUrl && (
          <img
            src={photoUrl}
            alt={displayName}
            className="absolute inset-0 w-full h-full object-cover"
            onError={(e) => {
              (e.currentTarget as HTMLImageElement).style.display = "none";
            }}
          />
        )}
      </div>

      <div className="p-3 space-y-1.5">
        <div className="grid grid-cols-[84px_1fr] gap-x-2 items-start">
          <span className="text-xs text-mw-neutral-500">
            {t("geofencingPopup.name")}
          </span>
          <span
            data-testid="poi-popup-name"
            className="text-xs font-semibold text-mw-primary-700 line-clamp-2"
          >
            {title}
          </span>
        </div>

        {address && (
          <div className="grid grid-cols-[84px_1fr] gap-x-2 items-start">
            <span className="text-xs text-mw-neutral-500">
              {t("geofencingPopup.address")}
            </span>
            <span
              data-testid="poi-popup-address"
              className="text-xs font-semibold text-mw-primary-700 line-clamp-2"
            >
              {address}
            </span>
          </div>
        )}

        {category && (
          <div className="grid grid-cols-[84px_1fr] gap-x-2 items-start">
            <span className="text-xs text-mw-neutral-500">
              {t("geofencingPopup.category")}
            </span>
            <span
              data-testid="poi-popup-category"
              className="text-xs font-semibold text-mw-primary-700 line-clamp-2"
            >
              {category}
            </span>
          </div>
        )}

        <div className="grid grid-cols-[84px_1fr] gap-x-2 items-center">
          <span className="text-xs text-mw-neutral-500">
            {t("geofencingPopup.rating")}
          </span>
          {hasRating ? (
            <StarRating rating={poi.rating as number} />
          ) : (
            <span
              data-testid="poi-popup-rating"
              className="text-xs font-semibold text-mw-primary-700"
            >
              {t("geofencingPopup.notAvailable")}
            </span>
          )}
        </div>
      </div>
    </div>
  );
};

export default GeoFencingPOIPopup;
