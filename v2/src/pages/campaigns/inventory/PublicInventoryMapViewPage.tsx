import { Badge } from "@components/ui/Badge";
import { Card, CardContent, CardHeader } from "@components/ui/card";
import { Input } from "@components/ui/Input";
import MapBoxWrapper from "@components/ui/Mapbox";
import { Tooltip } from "@components/ui/Tooltip";
import {
  useLazyGetPublicInventoriesQuery,
  useGetPublicCampaignQuery,
} from "@services/public-access/publicAccessSlice";
import { useTranslate } from "@tolgee/react";
import { extractGeofencingPOIs } from "@utils/geofencing-pois";
import { getLatitude, getLongitude } from "@utils/inventory.utils";
import clsx from "clsx";
import { Info, Search } from "lucide-react";
import type { Map as MapboxMap } from "mapbox-gl";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";

import MapJumpToDropdown, {
  type MapJumpOption,
} from "./manual-selection/MapJumpToDropdown";
import { readOnlyMapConfig } from "./mapConfig";
import type { InventoryItem, dayString } from "../../../types/inventory.types";
import { formatCurrencyWithLocale } from "../../../utils/currency";
import GeoFencingPOIPopup from "../geofencing/GeoFencingPOIPopup";

// Read-only map: 3D toggle only, no drawing tools, no built-in search — same
// as the Inventory View / Manual-edit maps (see ../inventory/mapConfig.ts).
const mapConfig = readOnlyMapConfig;

export const PublicInventoryMapViewPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const publicToken = searchParams.get("token");
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { t: tCommon } = useTranslate(["common"]);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedInventoryId, setSelectedInventoryId] = useState<
    string | undefined
  >(undefined);
  const [campaignCurrency, setCampaignCurrency] = useState<string>("");

  // API hooks from public access slice
  const [getPublicInventories] = useLazyGetPublicInventoriesQuery();
  const { data: publicCampaign } = useGetPublicCampaignQuery(
    { publicToken: publicToken || "" },
    { skip: !publicToken },
  );
  const campaignData = publicCampaign?.data;

  // Read-only "jump-to" options: geofencing locations + POIs, same source as
  // the manual-edit map. Selecting one flies the map to its coords.
  const availablePOIs = useMemo(
    () => extractGeofencingPOIs(campaignData),
    [campaignData],
  );
  const locationOptions = useMemo<MapJumpOption[]>(() => {
    const locations = campaignData?.targeting?.geofencing?.locations ?? [];
    return locations
      .filter((l) => typeof l.lat === "number" && typeof l.lng === "number")
      .map((l, i) => ({
        id: l.id || `loc-${i}`,
        label: l.name || l.address || `Location ${i + 1}`,
        lng: l.lng,
        lat: l.lat,
      }));
  }, [campaignData]);
  const poiJumpOptions = useMemo<MapJumpOption[]>(
    () =>
      availablePOIs
        .filter(
          (p) =>
            typeof p.locationLat === "number" &&
            typeof p.locationLng === "number",
        )
        .map((p, i) => ({
          id: `${p.displayName}-${i}`,
          label: p.displayName || p.primaryTypeDisplayName || `POI ${i + 1}`,
          lng: p.locationLng,
          lat: p.locationLat,
        })),
    [availablePOIs],
  );
  const [flyTarget, setFlyTarget] = useState<{
    lng: number;
    lat: number;
  } | null>(null);
  const mapRef = useRef<MapboxMap | null>(null);

  // State for API-fetched data
  const [selectedItems, setSelectedItems] = useState<InventoryItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [isLastPage, setIsLastPage] = useState(false);
  const [totalElements, setTotalElements] = useState(0);
  const currentPageRef = useRef(0);
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const hasInitialLoadRef = useRef(false);
  const isLoadingRef = useRef(false); // Prevent multiple simultaneous API calls
  const hasCheckedAutoLoadRef = useRef(false); // Track if we've already checked for auto-load
  const searchInputRef = useRef<HTMLInputElement>(null);

  // Load public inventories from API
  const loadSelectedInventory = useCallback(
    async (page: number, append = false) => {
      if (!publicToken) {
        console.error("Public token is missing");
        return;
      }

      // Prevent multiple simultaneous API calls
      if (isLoadingRef.current) {
        return;
      }

      try {
        isLoadingRef.current = true;
        if (append) {
          setIsLoadingMore(true);
        } else {
          setIsLoading(true);
        }

        const result = await getPublicInventories({
          publicToken,
          page,
          size: 10,
          sortBy: "name",
          sortDir: "asc",
        }).unwrap();

        if (result.success && result.data) {
          const { content, totalElements: total, last } = result.data;

          if (
            !append &&
            content.length > 0 &&
            content[0].performance.currency
          ) {
            setCampaignCurrency(content[0].performance.currency);
          }

          // Map PublicInventoryItem to InventoryItem format
          const mappedContent: InventoryItem[] = content.map((item) => {
            const firstPanel = item.detail.panels?.[0];
            const firstVenueType = item.detail.venueType?.[0] || "";
            const operatingTimesKeys = item.operations?.operatingTimes
              ? Object.keys(item.operations.operatingTimes)
              : [];

            return {
              detail: {
                id: item.detail.id,
                name: item.detail.name,
                externalId: item.detail.externalId,
                referenceId: item.detail.referenceId,
                mediaOwnerId: item.detail.mediaOwnerId,
                mediaOwnerName: item.detail.mediaOwnerName,
                inventoryType: item.detail.inventoryType,
                category: firstVenueType,
                venueType: item.detail.venueType ?? [],
                thumbnail: item.detail.thumbnail,
                images: item.detail.thumbnail ? [item.detail.thumbnail] : [],
                format: item.detail.format,
                environment: item.detail.environment,
                size: firstPanel?.size ?? "",
                operationMode: operatingTimesKeys[0] || "",
                execution: item.detail.execution,
                screens: item.detail.screens,
                sov: item.detail.sov,
                isSelected: item.detail.isSelected,
                isCompliant: item.detail.isCompliant,
                bookingMode: item.detail.bookingMode,
                panels: item.detail.panels.map((panel) => ({
                  pixelWidth: panel.pixelWidth,
                  pixelHeight: panel.pixelHeight,
                  physicalWidth: panel.physicalWidth,
                  physicalHeight: panel.physicalHeight,
                  panelCount: panel.panelCount,
                  unit: panel.unit,
                  size: panel.size,
                })),
              },
              location: {
                location: item.location.location,
                poi: {
                  types: [],
                  nearbyPOIs: [],
                  categories: [],
                },
                demographics: item.location.demographics,
              },
              performance: {
                cpmRate: item.performance.cpmRate,
                estimatedCost: item.performance.estimatedCost,
                perDayCost: item.performance.perDayCost,
                perDayAdPlays: item.performance.perDayAdPlays,
                totalAdPlays: item.performance.totalAdPlays,
                plannedSot: 0, // Not available in public API response
                totalSot: item.performance.totalSot,
              },
              operations: {
                operationDays: item.operations?.operatingTimes
                  ? (Object.keys(
                      item.operations.operatingTimes,
                    ) as Array<dayString>)
                  : [],
                operatingTimes: item.operations?.operatingTimes as Record<
                  dayString,
                  Array<{ start: string; end: string }>
                >,
                maintenanceWindow: item.operations?.maintenanceWindow || "",
                loopSize: item.operations?.loopSize || 0,
                slotDuration: item.operations?.slotDuration || 0,
                clientPerLoop: item.operations?.clientPerLoop || 0,
                cycleTime: item.operations?.cycleTime || 0,
              },
              schedules: item.schedules.map((schedule) => ({
                id: schedule.id,
                name: schedule.name,
                startDate: schedule.startDate,
                endDate: schedule.endDate,
                scheduleDays: schedule.scheduleDays as Array<dayString>,
                bookingMatrix: schedule.bookingMatrix,
                duration: schedule.duration,
                spotsPerLoop: schedule.spotsPerLoop,
                spotsPerHour: schedule.spotsPerHour,
                adPlays: 0,
                sov: 0,
                impressions: 0,
                sot: 0,
                plannedSot: 0,
                order: 0,
                pricing: 0,
                discount: null,
                bonusType: "",
                inventoryId: item.detail.id,
              })),
            };
          });

          if (append) {
            setSelectedItems((prev) => [...prev, ...mappedContent]);
            // Reset auto-load check when new data is appended (for next potential auto-load)
            if (mappedContent.length > 0) {
              hasCheckedAutoLoadRef.current = false;
            }
          } else {
            setSelectedItems(mappedContent);
            // Reset auto-load check on initial load
            hasCheckedAutoLoadRef.current = false;
          }

          setTotalElements(total);
          setIsLastPage(last);
          currentPageRef.current = page;
        }
      } catch (error) {
        console.error("Error loading public inventories:", error);
      } finally {
        setIsLoading(false);
        setIsLoadingMore(false);
        isLoadingRef.current = false;
      }
    },
    [publicToken, getPublicInventories],
  );

  // Load data when component mounts
  useEffect(() => {
    if (publicToken && !hasInitialLoadRef.current) {
      hasInitialLoadRef.current = true;
      currentPageRef.current = 0;
      hasCheckedAutoLoadRef.current = false; // Reset auto-load check
      loadSelectedInventory(0, false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [publicToken]);

  // Check if we need to load more when items are rendered (for cases where content fits in container)
  useEffect(() => {
    if (
      !publicToken ||
      isLoadingMore ||
      isLastPage ||
      isLoadingRef.current ||
      selectedItems.length === 0 || // Don't auto-load if we don't have initial data yet
      hasCheckedAutoLoadRef.current // Don't check again if we've already checked
    ) {
      return;
    }

    // Small delay to ensure DOM is updated
    const timeoutId = setTimeout(() => {
      const container = scrollContainerRef.current;
      if (container) {
        const { scrollHeight, clientHeight } = container;
        // Only auto-load if:
        // 1. Content fits in container (no scrollbar)
        // 2. We have more items to load (not on last page)
        // 3. Current items count is less than total
        // 4. We're not already loading
        if (
          scrollHeight <= clientHeight &&
          !isLastPage &&
          selectedItems.length < totalElements &&
          !isLoadingRef.current
        ) {
          hasCheckedAutoLoadRef.current = true; // Mark as checked
          const nextPage = currentPageRef.current + 1;
          loadSelectedInventory(nextPage, true);
        } else {
          // If we don't need to auto-load, mark as checked anyway
          hasCheckedAutoLoadRef.current = true;
        }
      }
    }, 300);

    return () => clearTimeout(timeoutId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedItems.length, publicToken, isLastPage, totalElements]);

  // Handle scroll for pagination
  const handleScroll = useCallback(() => {
    const container = scrollContainerRef.current;
    if (
      !container ||
      isLoadingMore ||
      isLastPage ||
      !publicToken ||
      isLoadingRef.current
    ) {
      return;
    }

    const { scrollTop, scrollHeight, clientHeight } = container;

    // Check if we can scroll (content is taller than container)
    if (scrollHeight <= clientHeight) {
      // If content fits in container, don't trigger load here
      // The auto-load effect will handle this case
      return;
    }

    const scrollPercentage = (scrollTop + clientHeight) / scrollHeight;

    // Load more when scrolled to 80%
    if (scrollPercentage >= 0.8) {
      const nextPage = currentPageRef.current + 1;
      loadSelectedInventory(nextPage, true);
    }
  }, [isLoadingMore, isLastPage, publicToken, loadSelectedInventory]);

  // Attach scroll listener with throttling
  useEffect(() => {
    const container = scrollContainerRef.current;
    if (!container) return;

    let scrollTimeout: ReturnType<typeof setTimeout> | null = null;
    const throttledHandleScroll = () => {
      if (scrollTimeout) return;
      scrollTimeout = setTimeout(() => {
        handleScroll();
        scrollTimeout = null;
      }, 200);
    };

    container.addEventListener("scroll", throttledHandleScroll);
    return () => {
      container.removeEventListener("scroll", throttledHandleScroll);
      if (scrollTimeout) clearTimeout(scrollTimeout);
    };
  }, [handleScroll]);

  // Filter items by search query (only displayName and address)
  const filteredItems = useMemo(() => {
    return selectedItems.filter((item) => {
      if (!searchQuery) return true;
      const query = searchQuery.toLowerCase();
      return (
        item.detail.name?.toLowerCase().includes(query) ||
        item.location.location.address?.toLowerCase().includes(query)
      );
    });
  }, [selectedItems, searchQuery]);

  // Calculate map center from selected items (used as initial center before fitBounds fires)
  const mapCenter: [number, number] = useMemo(() => {
    if (selectedItems.length === 0) return [0, 0];

    const avgLng =
      selectedItems.reduce(
        (sum, item) => sum + (getLongitude(item.location.location) || 0),
        0,
      ) / selectedItems.length;
    const avgLat =
      selectedItems.reduce(
        (sum, item) => sum + (getLatitude(item.location.location) || 0),
        0,
      ) / selectedItems.length;

    return [avgLng, avgLat];
  }, [selectedItems]);

  // Fit map to all inventory markers once the map is ready
  const handleMapReady = useCallback(
    (map: MapboxMap) => {
      mapRef.current = map;
      const coords = selectedItems
        .map((item) => ({
          lng: getLongitude(item.location.location) || 0,
          lat: getLatitude(item.location.location) || 0,
        }))
        .filter((c) => c.lng !== 0 || c.lat !== 0);

      if (coords.length === 0) return;

      const minLng = Math.min(...coords.map((c) => c.lng));
      const maxLng = Math.max(...coords.map((c) => c.lng));
      const minLat = Math.min(...coords.map((c) => c.lat));
      const maxLat = Math.max(...coords.map((c) => c.lat));

      map.fitBounds(
        [
          [minLng, minLat],
          [maxLng, maxLat],
        ],
        {
          padding: 80,
          maxZoom: 14,
          animate: false,
        },
      );
    },
    [selectedItems],
  );

  // Fly to a coord chosen from the Locations / POIs dropdowns.
  useEffect(() => {
    if (mapRef.current && flyTarget) {
      mapRef.current.flyTo({
        center: [flyTarget.lng, flyTarget.lat],
        zoom: 15,
        duration: 1200,
      });
    }
  }, [flyTarget]);

  // Handle card click to show popup on map
  const handleCardClick = (item: InventoryItem) => {
    setSelectedInventoryId(item.detail.id);
  };

  const handleSearchChange = (value: string) => {
    const wasFocused = document.activeElement === searchInputRef.current;
    setSearchQuery(value);
    // Restore focus if it was lost during state update
    if (wasFocused && searchInputRef.current) {
      // Use requestAnimationFrame to ensure focus is restored after React's state update
      requestAnimationFrame(() => {
        if (
          searchInputRef.current &&
          document.activeElement !== searchInputRef.current
        ) {
          searchInputRef.current.focus();
        }
      });
    }
  };

  const InventoryMapPopupComponent = ({ item }: { item: InventoryItem }) => {
    return (
      <div className="flex-1 min-w-0 space-y-1">
        <img
          src={item.detail.thumbnail}
          alt={item.detail.mediaOwnerName}
          className="rounded-md"
        />
        <div className="inline-flex justify-start items-center">
          <h3 className="text-xs font-semibold leading-4 truncate max-w-[250px]">
            {item.detail.name || ""}
          </h3>
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
              {tCommon(
                `inventorySize.${item.detail.size.toLowerCase()}.label`,
              ) || item.detail.size}
            </Badge>
          )}
        </div>
      </div>
    );
  };

  return (
    <div className="h-screen w-screen overflow-hidden bg-mw-neutral-50">
      <div className="flex gap-4 h-full p-4">
        {/* Left Side - Map */}
        <div className="relative flex-1">
          <div className="absolute top-7 left-7 right-7 z-10 flex gap-2">
            <MapJumpToDropdown
              label={tCampaigns("inventoryMapView.locationsDropdown")}
              options={locationOptions}
              emptyText={tCampaigns("inventoryMapView.locationsEmpty")}
              onSelect={setFlyTarget}
              className="flex-1 min-w-0"
            />
            <MapJumpToDropdown
              label={tCampaigns("inventoryMapView.poiFilter")}
              options={poiJumpOptions}
              emptyText={tCampaigns("inventoryMapView.poiEmpty")}
              onSelect={setFlyTarget}
              className="flex-1 min-w-0"
            />
          </div>
          <Card className="pt-4 h-full">
            <CardContent className="h-full">
              {selectedItems.length > 0 && (
                <MapBoxWrapper
                  defaultCenter={mapCenter}
                  defaultZoom={12}
                  controlsConfig={mapConfig}
                  locationsList={selectedItems}
                  availablePOIs={availablePOIs}
                  onMapReady={handleMapReady}
                  selectedItemId={selectedInventoryId}
                  PopupComponent={InventoryMapPopupComponent}
                  POIPopupComponent={GeoFencingPOIPopup}
                />
              )}
            </CardContent>
          </Card>
        </div>

        {/* Right Side - Selected Inventories List */}
        <div className="w-80 space-y-4 overflow-hidden">
          <Card className="h-full">
            <div className="p-4">
              <div className="inline-flex justify-start items-center gap-2">
                <h4 className="font-medium text-sm">
                  {tCampaigns("publicInventoryMap.selectedInventories")}
                </h4>
              </div>
            </div>
            {/* Search and Count Header */}
            <CardContent className="p-4 h-full">
              <div className="space-y-3">
                <div className="relative">
                  <Input
                    type="text"
                    ref={searchInputRef}
                    placeholder={tCampaigns("inventories.search_placeholder")}
                    value={searchQuery}
                    onChange={(e) => handleSearchChange(e.target.value)}
                    className="pr-8"
                  />
                  <Search className="absolute right-2 top-1/2 transform -translate-y-1/2 w-4 h-4 text-gray-400" />
                </div>

                <div className="flex items-center gap-2">
                  <p className="text-sm font-medium text-mw-neutral-900">
                    {totalElements}{" "}
                    {totalElements === 1
                      ? tCampaigns("selectedInventoryListContainer.inventory")
                      : tCampaigns(
                          "selectedInventoryListContainer.inventories",
                        )}
                  </p>
                </div>
              </div>

              {/* Scrollable Inventory Cards */}
              <div
                ref={scrollContainerRef}
                className="flex-1 space-y-3 pr-2 max-h-[calc(100vh-280px)] overflow-y-auto scrollbar-thin"
              >
                {isLoading && !isLoadingMore ? (
                  <div className="flex flex-col items-center justify-center h-64 text-center">
                    <p className="text-sm font-medium text-mw-neutral-600">
                      {tCampaigns("inventoryPageForm.loadingInventories")}
                    </p>
                  </div>
                ) : filteredItems.length === 0 ? (
                  <div className="flex flex-col items-center justify-center h-64 text-center">
                    <p className="text-sm font-medium text-mw-neutral-600">
                      {tCampaigns("inventoryPageForm.noInventoriesFound")}
                    </p>
                    <p className="text-xs text-mw-neutral-500 mt-1">
                      {searchQuery
                        ? tCampaigns("inventoryPageForm.tryAdjustingSearch")
                        : tCampaigns(
                            "inventoryPageForm.selectInventoriesToView",
                          )}
                    </p>
                  </div>
                ) : (
                  <>
                    {filteredItems.map((item) => (
                      <Card
                        key={item.detail.id}
                        className="hover:bg-mw-primary-50 transition-colors cursor-pointer"
                        onClick={() => handleCardClick(item)}
                      >
                        <CardHeader
                          className={clsx(
                            "p-3",
                            selectedInventoryId === item.detail.id
                              ? "bg-mw-primary-50"
                              : "bg-white",
                          )}
                        >
                          <div className="flex items-start justify-between gap-2 border-b border-mw-neutral-100 pb-2">
                            {/* Content */}
                            <div className="flex-1 min-w-0 space-y-1">
                              <div className="inline-flex justify-start items-center">
                                <h3 className="text-xs font-semibold leading-4 truncate max-w-[250px]">
                                  {item.detail.name || ""}
                                </h3>
                                <Info className="size-4" />
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
                                    {tCommon(
                                      `inventorySize.${item.detail.size.toLowerCase()}.label`,
                                    ) || item.detail.size}
                                  </Badge>
                                )}
                              </div>
                            </div>
                          </div>
                        </CardHeader>

                        <CardContent
                          className={clsx(
                            selectedInventoryId === item.detail.id
                              ? "bg-mw-primary-50"
                              : "bg-white",
                          )}
                        >
                          <div className="w-full flex items-center gap-1">
                            <span className="text-xs font-medium text-mw-primary-500">
                              {tCampaigns("inventoryMapView.estimatedCost")}:
                            </span>
                            <span className="text-xs font-semibold text-mw-primary-500">
                              {formatCurrencyWithLocale(
                                item.performance.estimatedCost,
                                campaignCurrency,
                              )}
                            </span>
                            <Tooltip
                              content={tCampaigns(
                                "inventoryMapView.estimatedCost",
                              )}
                            >
                              <Info className="size-4 text-mw-primary-500" />
                            </Tooltip>
                          </div>
                        </CardContent>
                      </Card>
                    ))}
                    {isLoadingMore && (
                      <div className="flex justify-center py-4">
                        <p className="text-sm text-mw-neutral-500">
                          {tCampaigns("publicInventoryMap.loadingMore")}
                        </p>
                      </div>
                    )}
                  </>
                )}
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default PublicInventoryMapViewPage;
