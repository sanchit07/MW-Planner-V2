import { InventoryDetailCard } from "@components/common/InventoryDetailCard";
import { Badge } from "@components/ui/Badge";
import { Checkbox } from "@components/ui/Checkbox";
// PL3-I2: Dropdown imports retained for the hidden Group By control. Restore
// together with the commented-out Group By block in the render below.
// import {
//   Dropdown,
//   DropdownContent,
//   DropdownItem,
//   DropdownSeparator,
//   DropdownTrigger,
// } from "@components/ui/Dropdown";
import { Spinner } from "@components/ui/Spinner";
import { useInfiniteScroll } from "@hooks/useInfiniteScroll";
import { useMediaOwnerIds } from "@hooks/useMediaOwnerIds";
import InventorySkeleton from "@pages/campaigns/inventory/InventorySkeleton";
import {
  useGetVenuesQuery,
  useLazyGetInventoryListQuery,
  useLazyGetSelectedInventoryQuery,
} from "@services/inventory/inventorySlice";
import { useTolgee } from "@tolgee/react";
import {
  buildVenueIdMap,
  buildVenueSlugToNamePath,
  buildVenueTypeIdFilter,
  filterSelectedInventoryClientSide,
} from "@utils/inventory.utils";
import { clsx } from "clsx";
import { Earth } from "lucide-react";
import React, {
  useCallback,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from "react";
import { EmptyValueDisplay } from "src/constants/inventory.constants";
import type { CampaignCreateResponse } from "src/types/campaign.types";

import type { RecommendationScoreMap } from "./view/useRecommendationScores";

import {
  excludeByReferenceId,
  type ListPhase,
  markAllSelected,
} from "./twoPhaseList";
import type {
  CampaignForecastData,
  GeofencingLocation,
  InventoryFilterParams,
  InventoryFilterRequest,
  InventoryItem,
  InventoryFilters,
} from "../../../types/inventory.types";

export interface InventoryListPanelSelectAllState {
  checked: boolean;
  indeterminate: boolean;
}

export interface InventoryListLoadedPayload {
  content: InventoryItem[];
  totalElements: number;
  last: boolean;
  append: boolean;
  sovValuesToMerge: Record<string, number>;
}

export interface InventoryListPanelProps {
  campaignId: string;
  campaignData: CampaignCreateResponse | null;
  inventoryFilters: InventoryFilters;
  filtersLoadedFromStorage: boolean;
  isUploadDrawerOpen: boolean;
  groupBy: string;
  setGroupBy: (value: string) => void;
  getSelectAllState: () => InventoryListPanelSelectAllState;
  handleSelectAll: (checked: boolean) => void;
  isSelecting: boolean;
  isBulkSyncing?: boolean;
  totalElements: number;
  forecastData?: CampaignForecastData | null;
  /** Selected count for the header badge (client-side manual selection). */
  selectedCount?: number;
  tCampaigns: (key: string) => string;
  inventoryItems: InventoryItem[];
  handleViewDetails: (externalId: string) => void;
  campaignCurrency: string;
  formatCurrency: (value: number, currency?: string) => string;
  handleItemSelection: (id: string, selected: boolean) => void;
  sovValues: Record<string, number>;
  onInventoryLoaded: (payload: InventoryListLoadedPayload) => void;
  /** Highlighted inventory id (e.g. for the map fly-to). */
  selectedInventoryId?: string;
  /** Fired when a card is clicked (e.g. to fly the map to it). */
  onCardClick?: (item: InventoryItem) => void;
  /**
   * Two-phase list: on the initial load, page through /selected-inventory first
   * (selected shown on top), then continue with /filter (deduped). Search/filter
   * reloads fall back to /filter-only (unchanged). Defaults to false.
   */
  selectedFirst?: boolean;
  /**
   * referenceId → recommendation-run annotations (availability, isExcluded).
   * When provided, browse-list items flagged by the run show the amber
   * "Limited availability" badge even though they weren't auto-selected, and
   * run-excluded items render an explicit "Unavailable for your dates" state.
   */
  scoresByReferenceId?: RecommendationScoreMap;
}

export interface InventoryListPanelRef {
  reload: (searchQueryOverride?: string) => Promise<void>;
}

const InventoryListPanelInner = (
  {
    campaignId,
    campaignData,
    inventoryFilters,
    filtersLoadedFromStorage,
    isUploadDrawerOpen,
    // PL3-I2: groupBy / setGroupBy retained on props but unused while the
    // Group By control is hidden. Restore with the commented-out block below.
    // groupBy,
    // setGroupBy,
    getSelectAllState,
    handleSelectAll,
    isSelecting,
    isBulkSyncing = false,
    totalElements,
    forecastData,
    selectedCount,
    tCampaigns,
    inventoryItems,
    handleViewDetails,
    campaignCurrency,
    formatCurrency,
    handleItemSelection,
    sovValues,
    onInventoryLoaded,
    selectedInventoryId,
    onCardClick,
    selectedFirst = false,
    scoresByReferenceId,
  }: InventoryListPanelProps,
  ref: React.Ref<InventoryListPanelRef | null>,
) => {
  const [fetchInventoryList] = useLazyGetInventoryListQuery();
  const [fetchSelectedInventory] = useLazyGetSelectedInventoryQuery();
  // Venue tree → stringValue-to-id lookup, for building the /filter
  // venueTypeIdFilter payload from the drawer's selected venue stringValues.
  const language = useTolgee(["language"]).getLanguage();
  const { data: venuesData = [] } = useGetVenuesQuery({ language });
  const venueIdMap = useMemo(() => buildVenueIdMap(venuesData), [venuesData]);
  // slug → display-name path, for client-side venueTypes matching on the
  // /selected-inventory response (which carries name paths, not slugs).
  const venueSlugToNamePath = useMemo(
    () => buildVenueSlugToNamePath(venuesData),
    [venuesData],
  );
  const mediaOwnerIds = useMediaOwnerIds();
  const mediaOwnerIdsRef = useRef(mediaOwnerIds);
  mediaOwnerIdsRef.current = mediaOwnerIds;
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  // Per-phase errors so a /filter failure doesn't hide already-loaded selected
  // items, and a /selected-inventory failure isn't silently erased when the
  // chained /filter succeeds. The banner shows when either is set.
  const [selectedLoadError, setSelectedLoadError] = useState<string | null>(
    null,
  );
  const [filterLoadError, setFilterLoadError] = useState<string | null>(null);
  const loadError = selectedLoadError ?? filterLoadError;
  const [isLastPage, setIsLastPage] = useState(false);
  // Latches true once the first /filter page has resolved, so the count badge
  // only renders real totals (not the initial 0/0) after the API completes.
  const [hasLoadedOnce, setHasLoadedOnce] = useState(false);

  const hasInitialLoadRef = useRef(false);

  // Two-phase paging state (only used when selectedFirst is true).
  const phaseRef = useRef<ListPhase>("filter");
  const selectedPageRef = useRef(0);
  const filterPageRef = useRef(-1);
  const selectedRefIdsRef = useRef<Set<string>>(new Set());
  // Bumped on every reset (initial load / reload / filter change). In-flight
  // loads capture it and bail if superseded, so a reload during an in-flight
  // page cannot write stale results or double-load.
  const loadTokenRef = useRef(0);

  const buildBaseFilterParams = useCallback(
    (
      filtersToUse: InventoryFilters,
      searchQueryOverride?: string,
    ): Partial<InventoryFilterRequest> => {
      const baseParams: Partial<InventoryFilterRequest> = {};
      if (filtersToUse.mediaOwners?.length > 0) {
        baseParams.mediaOwnerIds = filtersToUse.mediaOwners;
      }
      if (filtersToUse.sizes?.length > 0) {
        baseParams.sizes = filtersToUse.sizes;
      }
      if (filtersToUse.venueTypes?.length > 0) {
        const venueTypeIdFilter = buildVenueTypeIdFilter(
          filtersToUse.venueTypes,
          filtersToUse.inventoryClassification,
          venueIdMap,
        );
        if (venueTypeIdFilter) {
          baseParams.venueTypeIdFilter = venueTypeIdFilter;
        }
      }
      if (filtersToUse.bookingMode?.length > 0) {
        baseParams.bookingMode = filtersToUse.bookingMode;
      }
      if (filtersToUse.environments?.length > 0) {
        baseParams.environments = filtersToUse.environments;
      }
      if (filtersToUse.inventoryClassification?.length > 0) {
        baseParams.classifications = filtersToUse.inventoryClassification;
      }
      if (filtersToUse.latitude?.trim()) {
        baseParams.latitude = filtersToUse.latitude.trim();
      }
      if (filtersToUse.longitude?.trim()) {
        baseParams.longitude = filtersToUse.longitude.trim();
      }
      const searchQuery = searchQueryOverride ?? filtersToUse.searchbyquery;
      if (searchQuery?.trim()) {
        baseParams.name = searchQuery.trim();
      }
      if (
        filtersToUse.programmaticSupport &&
        filtersToUse.programmaticSupport !== "ALL"
      ) {
        baseParams.programmaticSupport = filtersToUse.programmaticSupport;
      }
      if (filtersToUse.dealTypes?.length > 0) {
        baseParams.dealTypes = filtersToUse.dealTypes.map((d) =>
          d.toLowerCase(),
        );
      }
      if (filtersToUse.cinemaGenres?.length) {
        baseParams.cinemaGenres = filtersToUse.cinemaGenres;
      }
      if (filtersToUse.cinemaRatings?.length) {
        baseParams.cinemaRatings = filtersToUse.cinemaRatings;
      }
      return baseParams;
    },
    [venueIdMap],
  );

  const buildFilterParams = useCallback(
    (page: number, searchQueryOverride?: string): InventoryFilterParams => {
      const params: InventoryFilterParams = {
        page,
        size: 10,
        sortBy: "name",
        sortDir: "asc",
        ...buildBaseFilterParams(inventoryFilters, searchQueryOverride),
      };

      if (campaignData) {
        const data = campaignData as {
          countryId?: string;
          startDate?: string;
          endDate?: string;
          goals?: {
            goalType?: string;
          };
          targeting?: {
            demographics?: {
              age?: string[];
              gender?: string[];
              venues?: string[];
            };
            geofencing?: {
              geometries?: Array<{
                type: string;
                coordinates: unknown;
                included: boolean;
                poi?: string[];
                metadata?: Record<string, string>;
              }>;
              locations?: Array<{
                poi?: string[];
                metadata?: Record<string, string>;
              }>;
            };
            inventoryCluster?: string[];
          };
        };
        params.countries = data.countryId ? [data.countryId] : [];

        if (data.startDate) params.startDate = data.startDate;
        if (data.endDate) params.endDate = data.endDate;

        if (data.goals?.goalType) {
          params.goalType = data.goals.goalType;
        }

        const targeting = data.targeting;
        if (targeting) {
          if (targeting.demographics?.age?.length) {
            params.demographics ??= {};
            params.demographics.age = targeting.demographics.age;
          }
          if (targeting.demographics?.gender?.length) {
            params.demographics ??= {};
            params.demographics.gender = targeting.demographics.gender;
          }

          if (targeting.geofencing) {
            params.geofencing = {
              geometries:
                targeting.geofencing.geometries?.map((geo) => ({
                  type: geo.type as "Polygon" | "Circle" | "LineString",
                  coordinates: geo.coordinates as number[][],
                  included: geo.included,
                })) ?? [],
              locations: (targeting.geofencing.locations ??
                []) as GeofencingLocation[],
            };
          }

          if (targeting.inventoryCluster?.length) {
            params.inventoryCluster = targeting.inventoryCluster;
          }
        }
      }

      return params;
    },
    [inventoryFilters, campaignData, buildBaseFilterParams],
  );

  const collectSov = useCallback(
    (items: InventoryItem[]): Record<string, number> => {
      const newSovValues: Record<string, number> = {};
      items.forEach((item) => {
        if (item.detail.sov && !sovValues[item.detail.id]) {
          newSovValues[item.detail.id] = item.detail.sov;
        }
      });
      return newSovValues;
    },
    [sovValues],
  );

  // Browse-list page (/filter). In two-phase mode, already-shown selected items
  // (selectedRefIdsRef) are excluded so nothing appears twice. If a page is
  // fully deduped (0 visible items) and it isn't the last page, keep advancing
  // pages within the same load — otherwise the list would stall (the infinite
  // scroll only re-fires when scrollHeight grows on a real scroll event).
  const loadFilterPage = useCallback(
    async (page: number, append = false, searchQueryOverride?: string) => {
      if (!campaignId) {
        console.warn("No campaign ID available");
        return;
      }
      const token = loadTokenRef.current;
      try {
        if (append) setIsLoadingMore(true);
        else setIsLoading(true);
        setFilterLoadError(null);

        let currentPage = page;
        let firstWrite = true;
        // Skip fully-deduped pages so at least one item is appended (or we hit
        // the last page).
        for (;;) {
          const params = buildFilterParams(currentPage, searchQueryOverride);
          const result = await fetchInventoryList({
            campaignId,
            params,
          }).unwrap();
          if (token !== loadTokenRef.current) return; // superseded by a reset
          if (!result.success || !result.data) break;

          const { content, totalElements: total, last } = result.data;
          const deduped = excludeByReferenceId(
            content,
            selectedRefIdsRef.current,
          );
          phaseRef.current = "filter";
          filterPageRef.current = currentPage;
          setIsLastPage(last);
          onInventoryLoaded({
            content: deduped,
            totalElements: total,
            last,
            // Only the first write of this load respects the caller's append;
            // continuation pages always append.
            append: firstWrite ? append : true,
            sovValuesToMerge: collectSov(deduped),
          });
          firstWrite = false;

          if (deduped.length > 0 || last) break;
          currentPage += 1;
        }
      } catch (error) {
        console.error("Error loading inventory:", error);
        setFilterLoadError(error?.toString() || "Failed to load inventory");
      } finally {
        setIsLoading(false);
        setIsLoadingMore(false);
        setHasLoadedOnce(true);
      }
    },
    [
      campaignId,
      buildFilterParams,
      fetchInventoryList,
      collectSov,
      onInventoryLoaded,
    ],
  );

  // Selected-inventory page (/selected-inventory). Items are marked selected and
  // their referenceIds accumulated for /filter dedup. When the last selected
  // page is reached, immediately continue into the /filter phase so the list
  // never stalls (a last selected page that appends 0 items would otherwise not
  // grow scrollHeight and no further scroll event would fire).
  const loadSelectedPage = useCallback(
    async (
      page: number,
      append = false,
      searchQueryOverride?: string,
    ): Promise<void> => {
      if (!campaignId) return;
      const token = loadTokenRef.current;
      try {
        if (append) setIsLoadingMore(true);
        else setIsLoading(true);
        setSelectedLoadError(null);

        const result = await fetchSelectedInventory({
          campaignId,
          params: { page, size: 1000 },
          ...(mediaOwnerIdsRef.current.length > 0
            ? { mediaOwnerIds: mediaOwnerIdsRef.current }
            : {}),
        }).unwrap();
        if (token !== loadTokenRef.current) return; // superseded by a reset

        if (result.success && result.data) {
          const { content, last } = result.data;
          // /selected-inventory does not accept the inventory filters, so the
          // selected items are narrowed on the client to stay consistent with
          // the server-side /filter list. lat/long are the only filters left
          // to the server (see filterSelectedInventoryClientSide).
          const filtered = filterSelectedInventoryClientSide(
            content,
            inventoryFilters,
            { searchOverride: searchQueryOverride, venueSlugToNamePath },
          );
          // Only accumulate the shown items' referenceIds for /filter dedup;
          // items hidden here also fail the same filters server-side, so /filter
          // won't return them and there is nothing to dedup.
          filtered.forEach((item) => {
            if (item.detail.referenceId) {
              selectedRefIdsRef.current.add(item.detail.referenceId);
            }
          });
          const marked = markAllSelected(filtered);
          selectedPageRef.current = page;
          setIsLastPage(false); // filter phase still follows
          onInventoryLoaded({
            content: marked,
            totalElements: result.data.totalElements ?? 0,
            last: false,
            append,
            sovValuesToMerge: collectSov(marked),
          });

          if (last) {
            phaseRef.current = "filter";
          }
        }
      } catch (error) {
        console.error("Error loading selected inventory:", error);
        setSelectedLoadError(error?.toString() || "Failed to load inventory");
        // End the selected phase so the chain below still loads /filter and
        // infinite scroll doesn't keep paging a failing endpoint.
        phaseRef.current = "filter";
      } finally {
        setIsLoading(false);
        setIsLoadingMore(false);
      }
      // Chain into /filter as soon as the selected phase ends. Done outside the
      // try so a failed selected page doesn't strand the list. loadFilterPage
      // re-checks the token, so a superseded run is a no-op. The search term is
      // forwarded so a search-triggered reload keeps filtering the /filter phase.
      if (phaseRef.current === "filter" && token === loadTokenRef.current) {
        await loadFilterPage(0, true, searchQueryOverride);
      }
    },
    [
      campaignId,
      fetchSelectedInventory,
      inventoryFilters,
      venueSlugToNamePath,
      collectSov,
      onInventoryLoaded,
      loadFilterPage,
    ],
  );

  // Full (re)load: two-phase (selected → filter) when selectedFirst, else
  // /filter-only. Resets all paging state and invalidates any in-flight load.
  // Used for the initial load, search/filter reloads (via the ref) and the
  // filter-change effect, so the selected-first phase — and its client-side
  // filtering — re-runs on every filter change, not just the first load.
  const beginLoad = useCallback(
    async (searchQueryOverride?: string) => {
      loadTokenRef.current += 1;
      selectedRefIdsRef.current = new Set();
      selectedPageRef.current = 0;
      filterPageRef.current = -1;
      setIsLastPage(false);
      setSelectedLoadError(null);
      setFilterLoadError(null);

      if (selectedFirst) {
        phaseRef.current = "selected";
        // loadSelectedPage chains into /filter itself once the selected phase ends.
        await loadSelectedPage(0, false, searchQueryOverride);
      } else {
        phaseRef.current = "filter";
        await loadFilterPage(0, false, searchQueryOverride);
      }
    },
    [selectedFirst, loadSelectedPage, loadFilterPage],
  );

  const onLoadMore = useCallback(() => {
    if (selectedFirst && phaseRef.current === "selected") {
      loadSelectedPage(selectedPageRef.current + 1, true);
    } else {
      loadFilterPage(filterPageRef.current + 1, true);
    }
  }, [selectedFirst, loadSelectedPage, loadFilterPage]);

  const scrollContainerRef = useInfiniteScroll({
    hasMore: !isLastPage,
    loading: isLoadingMore,
    onLoadMore,
    thresholdRatio: 0.8,
    throttleMs: 200,
  });

  useImperativeHandle(
    ref,
    () => ({
      // Search / filter reload. In selectedFirst mode this re-runs the two-phase
      // selected → filter flow so the /selected-inventory items are re-fetched
      // and client-side filtered on every apply/search; otherwise /filter-only.
      reload: async (searchQueryOverride?: string) => {
        await beginLoad(searchQueryOverride);
      },
    }),
    [beginLoad],
  );

  useEffect(() => {
    if (campaignId && filtersLoadedFromStorage && !hasInitialLoadRef.current) {
      const timer = setTimeout(() => {
        hasInitialLoadRef.current = true;
        beginLoad();
      }, 0);
      return () => clearTimeout(timer);
    }
  }, [campaignId, filtersLoadedFromStorage, beginLoad]);

  // Scroll the selected card into view (e.g. when a map pin is clicked).
  useEffect(() => {
    if (!selectedInventoryId) return;
    const container = scrollContainerRef.current;
    const el = container?.querySelector(
      `[data-inv-id="${selectedInventoryId}"]`,
    );
    el?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [selectedInventoryId, scrollContainerRef]);

  const campaignTargeting = campaignData
    ? (campaignData as { targeting?: unknown }).targeting
    : undefined;

  useEffect(() => {
    if (hasInitialLoadRef.current && campaignId && !isUploadDrawerOpen) {
      // Filter change → full reload. In selectedFirst mode beginLoad re-runs the
      // selected → filter flow so /selected-inventory is re-fetched and
      // client-side filtered; otherwise it is /filter-only.
      beginLoad();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    inventoryFilters.mediaOwners,
    inventoryFilters.venueTypes,
    inventoryFilters.bookingMode,
    inventoryFilters.latitude,
    inventoryFilters.longitude,
    isUploadDrawerOpen,
    campaignTargeting,
  ]);

  return (
    <div
      ref={scrollContainerRef}
      className={`px-4 self-stretch h-full min-h-[510px] flex flex-col gap-2 scrollbar-thin relative overflow-y-auto`}
    >
      <div className="flex items-center justify-between px-2 pb-2">
        <div className="flex items-center gap-2">
          <div className="text-black text-sm font-medium leading-none">
            <Checkbox
              label={tCampaigns("inventoryPageForm.selectAll")}
              checked={getSelectAllState().checked}
              isIndeterminate={getSelectAllState().indeterminate}
              onChange={() => {
                const { checked, indeterminate } = getSelectAllState();
                handleSelectAll(!checked && !indeterminate);
              }}
              disabled={isSelecting || isBulkSyncing || totalElements === 0}
            />
          </div>
          {/* Show the loader while the initial list loads or a bulk
              select/deselect is syncing; only render the count once ready. */}
          {!hasLoadedOnce || isBulkSyncing ? (
            <Spinner size="sm" variant="primary" />
          ) : (
            <Badge variant="secondary" size="md">
              {(selectedCount ?? forecastData?.totalInventories) || 0}/
              {totalElements}
            </Badge>
          )}
        </div>
        {/*
          PL3-I2: Group By is an unfinished stub (only "All Inventories" was
          ever wired, and `groupBy` is not used to group the list). Hidden until
          real grouping is built. Restore this block + the `groupBy`/`setGroupBy`
          props to re-enable.
        */}
        {/* <div className="flex items-center gap-2">
          <span className="text-sm font-medium leading-none text-mw-neutral-800">
            {tCampaigns("inventories.group_by")}
          </span>
          <Dropdown>
            <DropdownTrigger className="w-52">
              <span className="text-sm">
                {groupBy === "all" &&
                  tCampaigns("inventories.group_by_options.all")}
                {groupBy === "city" &&
                  tCampaigns("inventories.group_by_options.city")}
                {groupBy === "owner" &&
                  tCampaigns("inventories.group_by_options.owner")}
                {groupBy === "type" &&
                  tCampaigns("inventories.group_by_options.type")}
              </span>
            </DropdownTrigger>
            <DropdownContent align="right" className="w-52">
              <DropdownItem onClick={() => setGroupBy("all")}>
                <div className="text-sm leading-none">
                  {tCampaigns("inventories.group_by_options.all")}
                </div>
              </DropdownItem>
              <DropdownSeparator />
            </DropdownContent>
          </Dropdown>
        </div> */}
      </div>

      {/* Error state — shown above whatever did load (partial results stay) */}
      {loadError && !isLoading && (
        <div className="p-4 bg-red-50 border border-red-200 rounded-md flex items-center justify-between gap-3">
          <p className="text-sm text-red-800">
            {tCampaigns("inventories.list.loadError")}
          </p>
          <button
            type="button"
            className="text-sm font-medium text-red-800 underline shrink-0 cursor-pointer"
            onClick={() => beginLoad()}
          >
            {tCampaigns("inventories.list.retry")}
          </button>
        </div>
      )}

      {/* Skeleton loader for initial loading */}
      {isLoading && <InventorySkeleton count={5} />}
      {/* Inventory list — rendered even when a phase failed, so a /filter
          error doesn't hide successfully loaded selected inventories */}
      {!isLoading &&
        inventoryItems.map((item) => {
          const rec = item.detail.referenceId
            ? scoresByReferenceId?.[item.detail.referenceId]
            : undefined;
          return (
          <div
            key={item.detail.id}
            data-inv-id={item.detail.id}
            onClick={() => onCardClick?.(item)}
          >
            <InventoryDetailCard
              item={item}
              availabilityInfo={rec?.availability}
              unavailableForDates={rec?.isExcluded === true}
              onViewDetails={() => handleViewDetails(item.detail.externalId)}
              campaignCurrency={campaignCurrency}
              formatCurrency={(value, currency) =>
                formatCurrency(value || 0, currency)
              }
              tCampaigns={tCampaigns}
              showCheckbox={true}
              checkboxChecked={item.detail.isSelected}
              checkboxDisabled={isSelecting}
              onCheckboxChange={(checked) =>
                handleItemSelection(item.detail.id, checked)
              }
              showSovDropdown={true}
              sovValue={sovValues[item.detail.id]}
              cardClassName={clsx(
                "hover:bg-mw-primary-50",
                !item.detail.isCompliant && "border-mw-warning-500",
                selectedInventoryId === item.detail.id && "bg-[#E8F0F7]!",
              )}
              emptyValueDisplay={EmptyValueDisplay.NOT_APPLICABLE}
              showSmartSuggestionScore={false}
              performanceSource="local-if-available"
              campaignStartDate={campaignData?.startDate}
              campaignEndDate={campaignData?.endDate}
              goalType={campaignData?.goals?.goalType}
              isNegotiated={campaignData?.isNegotiated}
            />
          </div>
          );
        })}

      {/* Loading skeleton for "load more" (appended at the bottom) */}
      {isLoadingMore && <InventorySkeleton count={3} />}

      {!isLoading &&
        !isLoadingMore &&
        !loadError &&
        inventoryItems.length === 0 && (
          <div className="flex flex-col items-center justify-center h-64">
            <Earth className="w-12 h-12 text-mw-neutral-300 mb-4" />
            <p className="text-lg font-medium text-mw-neutral-600">
              {tCampaigns("inventories.list.empty")}
            </p>
            <p className="text-sm text-mw-neutral-500 mt-2">
              {tCampaigns("inventories.list.emptyHint")}
            </p>
          </div>
        )}
    </div>
  );
};

const InventoryListPanel = React.forwardRef(InventoryListPanelInner);
InventoryListPanel.displayName = "InventoryListPanel";

export default InventoryListPanel;
