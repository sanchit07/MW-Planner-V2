import { useTranslate } from "@tolgee/react";
import { getPlaceTypeInfo } from "@utils/place-type-utils";
import { getPOIIconUrl } from "@utils/poi-icon-url";
import { MapPin } from "lucide-react";
import React, { useEffect, useState } from "react";
import { MapMarkerLocation } from "src/types/campaign.types";

interface GeoFencingLocationPopupProps {
  item: MapMarkerLocation;
}

const GeoFencingLocationPopup: React.FC<GeoFencingLocationPopupProps> = ({
  item,
}) => {
  const { t } = useTranslate(["campaigns"]);
  const isCircle = item.metadata?.type === "circle";
  const cc = item.metadata?.countryCode;
  const placeType = isCircle ? null : (item.metadata?.type ?? null);
  const typeLabel = item.metadata?.typeLabel ?? null;
  const typeInfo = getPlaceTypeInfo(placeType, cc);
  const TypeIcon = typeInfo.Icon;
  const googlePhotoUrl = item.metadata?.photoUrl;

  // Header image source chain:
  //   1. Google Places photo (if the search result had one)
  //   2. CloudFront POI icon for the place type (+ related-category chain)
  //   3. Lucide fallback (rendered when all <img> sources fail)
  const poiIcon = isCircle ? null : getPOIIconUrl(placeType, typeLabel);
  const imageSources = isCircle
    ? []
    : [
        ...(googlePhotoUrl ? [googlePhotoUrl] : []),
        ...(poiIcon && !poiIcon.isGeneric
          ? [poiIcon.src, ...poiIcon.fallbackSrcs]
          : []),
      ];
  const [sourceIdx, setSourceIdx] = useState(0);
  // Reset when the popup is reused for a different item.
  useEffect(() => {
    setSourceIdx(0);
  }, [item.id]);
  const currentSrc = imageSources[sourceIdx];
  const showLucideFallback = !currentSrc;

  return (
    <div
      data-testid="geofencing-location-popup"
      className="min-w-[260px] max-w-[300px] bg-white rounded-lg overflow-hidden"
    >
      {/* Photo / icon header */}
      <div
        className={`relative h-24 w-full flex items-center justify-center ${typeInfo.bgClass}`}
      >
        {showLucideFallback &&
          (isCircle ? (
            <MapPin className={`h-10 w-10 ${typeInfo.colorClass} opacity-40`} />
          ) : (
            <TypeIcon
              className={`h-10 w-10 ${typeInfo.colorClass} opacity-40`}
            />
          ))}

        {currentSrc && (
          <img
            key={currentSrc}
            src={currentSrc}
            alt={item.name}
            className="absolute inset-0 w-full h-full object-cover"
            onError={() => setSourceIdx((i) => i + 1)}
          />
        )}
      </div>

      {/* Body: name + address only */}
      <div className="p-3 space-y-1.5">
        <div className="grid grid-cols-[84px_1fr] gap-x-2 items-start">
          <span className="text-xs text-mw-neutral-500">
            {t("geofencingPopup.name")}
          </span>
          <span
            data-testid="popup-name"
            className="text-xs font-semibold text-mw-primary-700 line-clamp-2"
          >
            {item.name}
          </span>
        </div>

        {item.address && (
          <div className="grid grid-cols-[84px_1fr] gap-x-2 items-start">
            <span className="text-xs text-mw-neutral-500">
              {t("geofencingPopup.address")}
            </span>
            <span
              data-testid="popup-address"
              className="text-xs font-semibold text-mw-primary-700 line-clamp-2"
            >
              {item.address}
            </span>
          </div>
        )}
      </div>
    </div>
  );
};

export default GeoFencingLocationPopup;
