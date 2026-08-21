import { SuccessResponse } from "@api/axiosBaseQuery";
import InventoryAvailabilityCalendarView from "@components/common/InventoryAvailabilityCalendarView";
import InventoryThumbnail from "@components/common/InventoryThumbnail";
import { Badge } from "@components/ui/Badge";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import { Gallery, MediaType } from "@components/ui/Gallery";
import { Label } from "@components/ui/Label";
import MapBoxWrapper, { MapControlsConfig } from "@components/ui/Mapbox";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@components/ui/Tabs";
import { Tooltip } from "@components/ui/Tooltip";
import { TOOLTIP_CONTENT } from "@constants/tooltip.constants";
import { useAnnounce } from "@hooks/useAnnounce";
import { useGetInventoryDetailsQuery } from "@services/inventory/inventorySlice";
import { useTranslate } from "@tolgee/react";
import {
  getInventoryTypeLabel,
  getOperatingWindow,
} from "@utils/inventory.utils";
import {
  mapInventoryDetailsResponseToInventoryItem,
  parseFirstGeomToLatLong,
} from "@utils/inventoryDetailsMapper";
import { formatTime } from "@utils/optimization.utils";
import { Copy, Film, Info } from "lucide-react";
import React, { useState, useMemo } from "react";
import {
  CINEMA_FILMS,
  CINEMA_LINEUP_AS_OF,
} from "src/constants/cinema-films.constants";
import { POIPlaceData } from "src/types/campaign.types";

import type {
  InventoryDetailsResponse,
  InventoryItem,
  Media,
} from "../../../types/inventory.types";

interface InventoryDetailsDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  availablePOIs?: POIPlaceData[];
  campaignStartDate: string | Date | null | undefined;
  campaignEndDate: string | Date | null | undefined;
  externalInventoryId?: string;
}

// Map configuration
const mapConfig: MapControlsConfig = {
  showDrawingTools: false,
  enableSelect: false,
  enablePolygon: false,
  enableCircle: false,
  enableLine: false,
  enableDelete: false,
  showViewTools: false,
  enableMountainView: false,
  enable3D: false,
  showMapStyles: false,
  enabledStyles: [],
  search: {
    enabled: false,
    showResults: true,
    searchTypes: ["place", "poi", "address"],
    limit: 5,
    showPOIFilter: false,
  },
};

function InventoryMapPopupContent({ item }: { item: InventoryItem }) {
  const { t: tCommon } = useTranslate(["common"]);
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { showSuccess } = useAnnounce();
  return (
    <div className="flex-1 min-w-0 space-y-1">
      <InventoryThumbnail
        src={item.detail.thumbnail}
        alt={item.detail.mediaOwnerName}
        className="rounded-md w-200 h-40"
      />
      <div className="inline-flex justify-start items-center gap-1 min-w-0">
        <Tooltip content={item.detail.name || ""} triggerClassName="min-w-0">
          <h3 className="text-xs font-semibold leading-4 truncate max-w-[250px]">
            {item.detail.name || ""}
          </h3>
        </Tooltip>
        {item.detail.name && (
          <button
            type="button"
            onClick={() => {
              navigator.clipboard.writeText(item.detail.name);
              showSuccess(tCampaigns("inventoryDetails.nameCopied"));
            }}
            aria-label={tCampaigns("inventoryDetails.copyName")}
            className="shrink-0 text-mw-neutral-400 hover:text-mw-neutral-600"
          >
            <Copy className="size-3" />
          </button>
        )}
      </div>

      <p className="text-xs text-secondary leading-4">
        {item.location.location.address ||
          `${item.location.location.country}, ${item.location.location.state}`}
      </p>
      <div className="flex flex-wrap gap-1.5">
        <Badge variant="outline" size="sm">
          {tCommon(
            `inventoryClassification.${item.detail.inventoryType.toLowerCase()}`,
          ) || item.detail.inventoryType}
        </Badge>
        {item.detail.format && (
          <Badge
            className="outline-mw-rose-warning-400 text-mw-rose-warning-400"
            variant="outline"
            size="sm"
          >
            {item.detail.format}
          </Badge>
        )}
        {item.detail.environment && (
          <Badge
            className="outline-mw-neutral-400 text-mw-neutral-400"
            variant="outline"
            size="sm"
          >
            {tCommon(
              `inventoryEnvironment.${item.detail.environment.toLowerCase()}`,
            ) || item.detail.environment}
          </Badge>
        )}
        {item.detail.size && (
          <Badge
            className="outline-mw-orange-warning-500 text-mw-orange-warning-500"
            variant="outline"
            size="sm"
          >
            {tCommon(`inventorySize.${item.detail.size.toLowerCase()}.label`) ||
              item.detail.size}
          </Badge>
        )}
      </div>
    </div>
  );
}

export const InventoryDetailsDrawer: React.FC<InventoryDetailsDrawerProps> = ({
  isOpen,
  onClose,
  availablePOIs,
  campaignStartDate,
  campaignEndDate,
  externalInventoryId,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { showSuccess } = useAnnounce();
  const [activeTab, setActiveTab] = useState("details");
  const shouldFetch = Boolean(externalInventoryId?.trim());
  const { data: response } = useGetInventoryDetailsQuery<
    SuccessResponse<[InventoryDetailsResponse]>
  >({ inventoryId: externalInventoryId || "" }, { skip: !shouldFetch });
  const raw = response as
    | SuccessResponse<[InventoryDetailsResponse]>
    | undefined;
  const inventoryDetails: InventoryDetailsResponse | undefined =
    response?.[0] ?? (raw as unknown as InventoryDetailsResponse | undefined);

  const inventoryData = useMemo(
    () => mapInventoryDetailsResponseToInventoryItem(inventoryDetails),
    [inventoryDetails],
  );

  // Cinema inventory carries cinemaFields; used to render the read-only
  // indicative film line-up preview (films are never a buy unit).
  const isCinema = Boolean(inventoryDetails?.cinemaFields);
  const lineupAsOfLabel = useMemo(() => {
    try {
      return new Date(CINEMA_LINEUP_AS_OF).toLocaleDateString(undefined, {
        year: "numeric",
        month: "short",
        day: "numeric",
      });
    } catch {
      return CINEMA_LINEUP_AS_OF;
    }
  }, []);

  // "Digital"/"Classic" plus "Transit" when the venue falls under a transit
  // category (Bus, Rail & Metro, Taxi & Rideshare, Commercial Fleet), mirroring
  // the DIGITAL/DIGITAL_TRANSIT/CLASSIC/CLASSIC_TRANSIT split used in Targeting.
  const inventoryTypeLabel = useMemo(
    () =>
      getInventoryTypeLabel(
        inventoryDetails?.typeName,
        inventoryDetails?.venues,
        tCampaigns,
      ),
    [inventoryDetails?.typeName, inventoryDetails?.venues, tCampaigns],
  );

  // Show the window the inventory is open on EVERY day: the latest start and the
  // earliest end across all days/slots (see getOperatingWindow).
  const operationTimes = useMemo(
    () =>
      getOperatingWindow(
        inventoryDetails?.schedule?.operatingTimes as
          | Record<string, Array<{ start: string; end: string }>>
          | undefined,
      ),
    [inventoryDetails?.schedule?.operatingTimes],
  );

  const geomsData = useMemo(
    () => parseFirstGeomToLatLong(inventoryDetails?.geoms),
    [inventoryDetails?.geoms],
  );

  const carouselImages = useMemo(() => {
    if (!inventoryDetails) return [];
    const mediaData = inventoryDetails.medias;
    if (!mediaData?.length) return [];
    return mediaData.map((media: Media) => ({
      id: media.url,
      src: media.url,
      type: "image" as MediaType,
      alt: inventoryDetails.name ?? undefined,
    }));
  }, [inventoryDetails]);

  if (!inventoryDetails) return null;

  const handleClose = () => {
    setActiveTab("details");
    onClose();
  };

  return (
    <ModalDrawer
      isOpen={isOpen}
      onClose={handleClose}
      title={
        <span className="flex items-center gap-2 min-w-0">
          <span>{inventoryDetails?.name}</span>
          <button
            type="button"
            onClick={() => {
              if (inventoryDetails?.name) {
                navigator.clipboard.writeText(inventoryDetails.name);
                showSuccess(tCampaigns("inventoryDetails.nameCopied"));
              }
            }}
            aria-label={tCampaigns("inventoryDetails.copyName")}
            className="shrink-0 text-mw-neutral-400 hover:text-mw-neutral-600"
          >
            <Copy className="size-4" />
          </button>
        </span>
      }
      size="custom"
      customWidth="60vw"
    >
      <div className="space-y-6 h-full overflow-hidden">
        {/* Tabs */}
        <Tabs
          value={activeTab}
          onValueChange={setActiveTab}
          className="h-full flex flex-col overflow-hidden"
        >
          <TabsList>
            <TabsTrigger value="details">
              {tCampaigns("inventoryDetails.tabs.details")}
            </TabsTrigger>
            <TabsTrigger value="location">
              {tCampaigns("inventoryDetails.tabs.locationMap")}
            </TabsTrigger>
            {/* <TabsTrigger value="performance">Performance</TabsTrigger> */}
            <TabsTrigger value="operations">
              {tCampaigns("inventoryDetails.tabs.operations")}
            </TabsTrigger>
            {/* <TabsTrigger value="busyness">Busyness</TabsTrigger> */}
            <TabsTrigger value="availability">
              {tCampaigns("inventoryDetails.tabs.availability")}
            </TabsTrigger>
          </TabsList>

          {/* Details Tab */}
          <TabsContent value="details" className="flex-1 overflow-hidden">
            <div className="space-y-2 pt-2 h-full overflow-auto scrollbar-thin">
              {/* Image Carousel */}
              {carouselImages.length > 0 ? (
                <Gallery
                  items={carouselImages}
                  showThumbnails={true}
                  showCounter={false}
                  showNavigation={true}
                  thumbnailSize="sm"
                  className="mx-w-4xl h-[555px]"
                />
              ) : (
                <div className="relative w-full h-[535px] bg-mw-neutral-100 dark:bg-mw-neutral-800 rounded-lg overflow-hidden flex items-center justify-center">
                  <span className="text-mw-neutral-400">
                    {tCampaigns("inventoryDetails.noImageAvailable")}
                  </span>
                </div>
              )}

              <Card>
                <CardHeader className="p-4 pb-0">
                  <CardTitle className="text-lg font-medium mb-3 leading-6">
                    {tCampaigns("inventoryDetails.inventoryInformations")}
                  </CardTitle>
                  <div className="h-px bg-mw-neutral-100" />
                </CardHeader>
                <CardContent className="pt-4">
                  <div className="grid grid-cols-5 gap-6">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns("inventoryDetails.labels.mediaOwner")}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryDetails.mediaOwner,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-semibold leading-4">
                        {inventoryDetails?.mediaOwnerName ||
                          tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>

                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns("inventoryDetails.labels.type")}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryDetails.type,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-medium leading-4">
                        {inventoryTypeLabel ||
                          tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>

                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns("inventoryDetails.labels.format")}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryDetails.format,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-medium leading-4">
                        {inventoryDetails?.displayFormatName ||
                          inventoryDetails?.venues?.[0]?.name ||
                          tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>

                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns("inventoryDetails.labels.size")}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryDetails.size,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-medium leading-4">
                        {inventoryDetails?.size?.toUpperCase() ||
                          tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>

                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns("inventoryDetails.labels.screens")}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryDetails.screens,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-medium leading-4">
                        {inventoryDetails?.panels?.length ||
                          tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>

                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns("inventoryDetails.labels.operationMode")}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryDetails.operationMode,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-medium leading-4">
                        {(() => {
                          const bookingMode =
                            inventoryDetails?.digitalFields?.bookingMode;
                          if (bookingMode === "time")
                            return tCampaigns(
                              "inventoryDetails.bookingMode.spot",
                            );
                          if (bookingMode === "loop")
                            return tCampaigns(
                              "inventoryDetails.bookingMode.loop",
                            );
                          return (
                            bookingMode ||
                            tCampaigns("inventoryDetails.notAvailable")
                          );
                        })()}
                      </p>
                    </div>

                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns("inventoryDetails.labels.inventoryId")}
                        </Label>
                      </div>
                      <p className="text-sm font-medium leading-4">
                        {inventoryDetails?.referenceId ||
                          tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>
                  </div>
                </CardContent>
              </Card>

              {/* Indicative film line-up — cinema only, read-only preview. */}
              {isCinema && (
                <Card>
                  <CardHeader className="pb-2">
                    <CardTitle className="flex items-center gap-2 text-sm">
                      <Film className="size-4 text-mw-neutral-500" />
                      {tCampaigns("inventoryDetails.cinema.lineupTitle")}
                    </CardTitle>
                    <p className="text-xs text-mw-neutral-400">
                      {tCampaigns("inventoryDetails.cinema.lineupAsOf", {
                        date: lineupAsOfLabel,
                      })}
                    </p>
                  </CardHeader>
                  <CardContent>
                    <ul className="divide-y divide-mw-neutral-100">
                      {CINEMA_FILMS.map((film) => (
                        <li
                          key={film.id}
                          className="flex items-center justify-between gap-3 py-2"
                        >
                          <div className="min-w-0">
                            <p className="truncate text-sm font-medium text-mw-neutral-800">
                              {film.title}
                            </p>
                            <p className="truncate text-xs text-mw-neutral-500">
                              {film.genres.join(", ")}
                            </p>
                          </div>
                          <div className="flex shrink-0 items-center gap-1.5">
                            <Badge variant="outline" size="sm">
                              {film.rating}
                            </Badge>
                            <Badge variant="outline" size="sm">
                              {tCampaigns("inventoryDetails.cinema.runtime", {
                                minutes: String(film.durationMinutes),
                              })}
                            </Badge>
                          </div>
                        </li>
                      ))}
                    </ul>
                    <p className="mt-3 text-xs italic text-mw-neutral-400">
                      {tCampaigns("inventoryDetails.cinema.notABuyUnit")}
                    </p>
                  </CardContent>
                </Card>
              )}
            </div>
          </TabsContent>

          {/* Location & Map Tab */}
          <TabsContent value="location" className="flex-1 overflow-hidden">
            <div className="space-y-2 pt-2 h-full overflow-auto scrollbar-thin">
              <div className="max-h-[300px]">
                <MapBoxWrapper
                  defaultCenter={[geomsData.long, geomsData.lat]}
                  defaultZoom={15}
                  controlsConfig={mapConfig}
                  locationsList={inventoryData ? [inventoryData] : []}
                  selectedItemId={inventoryDetails?.id}
                  PopupComponent={InventoryMapPopupContent}
                  availablePOIs={availablePOIs}
                />
              </div>
              {/* Location Details Card */}
              <Card>
                <CardHeader className="p-4 pb-0">
                  <CardTitle className="text-lg font-medium mb-3 leading-6">
                    {tCampaigns("inventoryDetails.locationDetails")}
                  </CardTitle>
                  <div className="h-px bg-mw-neutral-100" />
                </CardHeader>
                <CardContent className="pt-4">
                  <div className="grid grid-cols-4 gap-6">
                    <div className="col-span-4">
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns(
                            "inventoryDetails.locationLabels.address",
                          )}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryLocation.address,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-semibold leading-4">
                        {inventoryDetails?.address ||
                          tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>

                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns(
                            "inventoryDetails.locationLabels.coordinates",
                          )}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryLocation.coordinates,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-medium leading-4">
                        {geomsData.lat !== 0 || geomsData.long !== 0
                          ? `${geomsData.lat.toFixed(6)}, ${geomsData.long.toFixed(6)}`
                          : tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>

                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns(
                            "inventoryDetails.locationLabels.visibility",
                          )}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryLocation.visibility,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-medium leading-4">
                        {tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>

                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns(
                            "inventoryDetails.locationLabels.venueType",
                          )}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryLocation.venueType,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-medium leading-4">
                        {inventoryDetails?.venues &&
                        inventoryDetails?.venues.length
                          ? inventoryDetails?.venues[0]?.name
                          : tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>
                  </div>
                </CardContent>
              </Card>

              {/* Nearby Points of Interest Card */}
              <Card>
                <CardHeader className="p-4 pb-0">
                  <CardTitle className="text-lg font-medium mb-3 leading-6">
                    {tCampaigns("inventoryDetails.nearbyPOI")}
                  </CardTitle>
                  <div className="h-px bg-mw-neutral-100" />
                </CardHeader>
                <CardContent className="pt-4">
                  <p className="text-sm font-medium leading-4">
                    {tCampaigns("inventoryDetails.notAvailable")}
                  </p>
                </CardContent>
              </Card>
            </div>
          </TabsContent>

          {/* Performance Tab */}
          {/* <TabsContent value="performance">
            <div className="py-6 text-center text-mw-neutral-500">
              <p>Performance metrics will be displayed here</p>
            </div>
          </TabsContent> */}

          {/* Operations Tab */}
          <TabsContent value="operations" className="flex-1 overflow-hidden">
            <div className="space-y-2 pt-2 h-full overflow-auto scrollbar-thin">
              {/* Operating Schedule Card */}
              <Card>
                <CardHeader className="p-4 pb-0">
                  <CardTitle className="text-lg font-medium mb-3 leading-6">
                    {tCampaigns("inventoryDetails.operatingSchedule")}
                  </CardTitle>
                  <div className="h-px bg-mw-neutral-100" />
                </CardHeader>
                <CardContent className="pt-4">
                  <div className="grid grid-cols-4 gap-6">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns(
                            "inventoryDetails.scheduleLabels.startTime",
                          )}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryOperations.startTime,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-semibold leading-4 ">
                        {operationTimes?.startTime
                          ? formatTime(operationTimes?.startTime)
                          : tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>

                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns(
                            "inventoryDetails.scheduleLabels.endTime",
                          )}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryOperations.endTime,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-semibold leading-4">
                        {operationTimes?.endTime
                          ? formatTime(operationTimes?.endTime)
                          : tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>

                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns(
                            "inventoryDetails.scheduleLabels.operatingDays",
                          )}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryOperations.operatingDays,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-semibold leading-4">
                        {inventoryDetails?.schedule?.operatingTimes
                          ? tCampaigns("inventoryDetails.daysPerWeek", {
                              count: Object.keys(
                                inventoryDetails.schedule.operatingTimes,
                              ).length,
                            })
                          : tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>

                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns(
                            "inventoryDetails.scheduleLabels.maintenanceWindow",
                          )}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryOperations
                              .maintenanceWindow,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-semibold leading-4">
                        {tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>
                  </div>
                </CardContent>
              </Card>

              {/* Loop Configuration Card */}
              <Card>
                <CardHeader className="p-4 pb-0">
                  <CardTitle className="text-lg font-medium mb-3 leading-6">
                    {tCampaigns("inventoryDetails.loopConfiguration")}
                  </CardTitle>
                  <div className="h-px bg-mw-neutral-100" />
                </CardHeader>
                <CardContent className="pt-4">
                  <div className="grid grid-cols-4 gap-6">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns("inventoryDetails.loopLabels.loopSize")}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryOperations.loopSize,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-semibold leading-4">
                        {inventoryDetails?.digitalFields?.loopsPerHour ??
                          tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>

                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns(
                            "inventoryDetails.loopLabels.slotDuration",
                          )}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryOperations.slotDuration,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-semibold leading-4">
                        {inventoryDetails?.digitalFields?.spotDuration ??
                          tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>

                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns(
                            "inventoryDetails.loopLabels.clientsPerLoop",
                          )}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryOperations.clientsPerLoop,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-semibold leading-4">
                        {inventoryDetails?.digitalFields?.spotsPerLoop ??
                          tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>

                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Label>
                          {tCampaigns("inventoryDetails.loopLabels.cycleTime")}
                        </Label>
                        <Tooltip
                          content={tCampaigns(
                            TOOLTIP_CONTENT.inventoryOperations.cycleTime,
                          )}
                          className="whitespace-normal w-44 break-words"
                        >
                          <Info className="size-3.5 text-mw-neutral-500 cursor-pointer" />
                        </Tooltip>
                      </div>
                      <p className="text-sm font-semibold leading-4">
                        {inventoryDetails?.digitalFields?.loopDuration ??
                          tCampaigns("inventoryDetails.notAvailable")}
                      </p>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </div>
          </TabsContent>

          {/* Busyness Tab */}
          {/* <TabsContent value="busyness">
            <div className="py-6 text-center text-mw-neutral-500">
              <p>Busyness data will be displayed here</p>
            </div>
          </TabsContent> */}

          {/* Availability Tab */}
          <TabsContent
            value="availability"
            className="flex-1 h-full overflow-hidden"
          >
            <div className="h-full overflow-hidden pt-4">
              <InventoryAvailabilityCalendarView
                inventoryData={inventoryData}
                campaignStartDate={campaignStartDate}
                campaignEndDate={campaignEndDate}
              />
            </div>
          </TabsContent>
        </Tabs>
      </div>
    </ModalDrawer>
  );
};

export default InventoryDetailsDrawer;
