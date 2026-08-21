import { useAnnounce } from "@hooks/useAnnounce";
import {
  useBulkSelectByIdsMutation,
  useBulkSelectInventoryMutation,
  useBulkSelectInventoryByReferenceIdsMutation,
  useGetVenuesQuery,
  useLazyGetSelectedInventoryQuery,
} from "@services/inventory/inventorySlice";
import { useTranslate, useTolgee } from "@tolgee/react";
import {
  buildVenueIdMap,
  buildVenueTypeIdFilter,
} from "@utils/inventory.utils";
import { useCallback, useMemo, useRef, useState } from "react";
import type { CampaignCreateResponse } from "src/types/campaign.types";

import { statFromItem, type SelectedStat } from "./selection-stats.utils";
import type {
  InventoryFilterRequest,
  InventoryFilters,
  InventoryItem,
} from "../../../../types/inventory.types";
import { buildCampaignTargetingFilters } from "../inventoryFilters.utils";
import type { InventoryListLoadedPayload } from "../InventoryListPanel";

export interface SelectAllState {
  checked: boolean;
  indeterminate: boolean;
}

export function useManualInventorySelection(
  campaignId: string,
  campaignData: CampaignCreateResponse | null,
  effectiveFilters: InventoryFilters,
  mediaOwnerIds: string[] = [],
) {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { showError } = useAnnounce();

  const [inventoryItems, setInventoryItems] = useState<InventoryItem[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [sovValues, setSovValues] = useState<Record<string, number>>({});
  const [isBulkSyncing, setIsBulkSyncing] = useState(false);
  const [hasManualEdits, setHasManualEdits] = useState(false);
  const [selectionMap, setSelectionMap] = useState<Map<string, SelectedStat>>(
    new Map(),
  );
  const [isLoadingSelection, setIsLoadingSelection] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  // null = untouched, "SELECT"/"DESELECT" = a bulk select-all/deselect-all this session.
  const [bulkMode, setBulkMode] = useState<"SELECT" | "DESELECT" | null>(null);

  // Baseline snapshot (ids selected when the popup opened) for the Save diff.
  const baselineRef = useRef<Set<string>>(new Set());
  // Stat lookup for items encountered this session (loaded list + baseline).
  const statCacheRef = useRef<Map<string, SelectedStat>>(new Map());
  // Incrementing id guarding loadSelectedInventory's terminal state writes
  // against a stale in-flight call from a fast close->reopen.
  const loadRequestIdRef = useRef(0);

  const mediaOwnerIdsRef = useRef(mediaOwnerIds);
  mediaOwnerIdsRef.current = mediaOwnerIds;

  const [bulkSelectInventory] = useBulkSelectInventoryMutation();
  const [bulkSelectInventoryByReferenceIds] =
    useBulkSelectInventoryByReferenceIdsMutation();
  const [bulkSelectByIds] = useBulkSelectByIdsMutation();
  const [fetchSelectedInventory] = useLazyGetSelectedInventoryQuery();

  const language = useTolgee(["language"]).getLanguage();
  const { data: venuesData = [] } = useGetVenuesQuery({ language });
  const venueIdMap = useMemo(() => buildVenueIdMap(venuesData), [venuesData]);

  const buildBaseFilterParams =
    useCallback((): Partial<InventoryFilterRequest> => {
      const f = effectiveFilters;
      const baseParams: Partial<InventoryFilterRequest> = {};
      if (f.mediaOwners?.length > 0) baseParams.mediaOwnerIds = f.mediaOwners;
      if (f.sizes?.length > 0) baseParams.sizes = f.sizes;
      if (f.venueTypes?.length > 0) {
        const venueTypeIdFilter = buildVenueTypeIdFilter(
          f.venueTypes,
          f.inventoryClassification,
          venueIdMap,
        );
        if (venueTypeIdFilter) baseParams.venueTypeIdFilter = venueTypeIdFilter;
      }
      if (f.bookingMode?.length > 0) baseParams.bookingMode = f.bookingMode;
      if (f.environments?.length > 0) baseParams.environments = f.environments;
      if (f.inventoryClassification?.length > 0)
        baseParams.classifications = f.inventoryClassification;
      if (f.latitude?.trim()) baseParams.latitude = f.latitude.trim();
      if (f.longitude?.trim()) baseParams.longitude = f.longitude.trim();
      if (f.searchbyquery?.trim()) baseParams.name = f.searchbyquery.trim();
      if (f.programmaticSupport && f.programmaticSupport !== "ALL")
        baseParams.programmaticSupport = f.programmaticSupport;
      if (f.dealTypes?.length > 0)
        baseParams.dealTypes = f.dealTypes.map((d) => d.toLowerCase());
      return baseParams;
    }, [effectiveFilters, venueIdMap]);

  const buildFiltersWithoutPagination =
    useCallback((): InventoryFilterRequest => {
      return {
        ...buildBaseFilterParams(),
        ...buildCampaignTargetingFilters(campaignData),
      };
    }, [campaignData, buildBaseFilterParams]);

  // Loop-fetch EVERY page of /selected-inventory into the selectionMap + baseline.
  const loadSelectedInventory = useCallback(async () => {
    if (!campaignId) return;
    const requestId = ++loadRequestIdRef.current;
    setIsLoadingSelection(true);
    try {
      const owners = mediaOwnerIdsRef.current;
      const map = new Map<string, SelectedStat>();
      let page = 0;
      let totalPages = 1;
      do {
        const res = await fetchSelectedInventory({
          campaignId,
          params: { page, size: 100, sortBy: "name", sortDir: "asc" },
          ...(owners.length > 0 ? { mediaOwnerIds: owners } : {}),
        }).unwrap();
        const data = res?.data;
        if (!data) break;
        totalPages = data.totalPages ?? 1;
        for (const it of data.content ?? []) {
          const stat = statFromItem(it);
          map.set(stat.id, stat);
          statCacheRef.current.set(stat.id, stat);
        }
        page += 1;
      } while (page < totalPages);
      // A newer loadSelectedInventory call has since started (fast
      // close->reopen) - let it own the terminal state instead.
      if (loadRequestIdRef.current !== requestId) return;
      baselineRef.current = new Set(map.keys());
      setSelectionMap(map);
      setBulkMode(null);
    } catch (error) {
      console.error("Error loading selected inventory:", error);
      showError(tCampaigns("inventories.manual.loadFailed"));
    } finally {
      if (loadRequestIdRef.current === requestId) {
        setIsLoadingSelection(false);
      }
    }
  }, [campaignId, fetchSelectedInventory, showError, tCampaigns]);

  const handleInventoryLoaded = useCallback(
    (payload: InventoryListLoadedPayload) => {
      setInventoryItems((prev) =>
        payload.append ? [...prev, ...payload.content] : payload.content,
      );
      setTotalElements(payload.totalElements);
      setSovValues((prev) => ({ ...prev, ...payload.sovValuesToMerge }));
      // Cache stats for every loaded item so toggles/selectAll can price them.
      for (const it of payload.content) {
        const stat = statFromItem(it);
        statCacheRef.current.set(stat.id, stat);
      }
      // A client-side Select-All is active: newly-paginated items must be
      // backfilled into selectionMap too, or saveSelection's bulk diff will
      // treat them as "loaded but not selected" and DESELECT them right
      // after the /select-all SELECT call. Items the user has explicitly
      // deselected are already absent from selectionMap and stay that way
      // (we only add ids that aren't present yet).
      if (bulkMode === "SELECT") {
        setSelectionMap((prev) => {
          const next = new Map(prev);
          for (const it of payload.content) {
            const stat = statFromItem(it);
            if (!next.has(stat.id)) next.set(stat.id, stat);
          }
          return next;
        });
      }
    },
    [bulkMode],
  );

  const getSelectAllState = useCallback((): SelectAllState => {
    if (bulkMode === "SELECT") return { checked: true, indeterminate: false };
    if (bulkMode === "DESELECT")
      return { checked: false, indeterminate: false };
    const selected = selectionMap.size;
    if (totalElements === 0 || selected === 0)
      return { checked: false, indeterminate: false };
    if (selected >= totalElements)
      return { checked: true, indeterminate: false };
    return { checked: false, indeterminate: true };
  }, [bulkMode, selectionMap, totalElements]);

  // Client-only select-all: mark loaded items + record intent. Footer is
  // best-effort here (unloaded items unknown) until Save + reopen.
  const handleSelectAll = useCallback(
    (checked: boolean) => {
      setHasManualEdits(true);
      setBulkMode(checked ? "SELECT" : "DESELECT");
      setInventoryItems((prev) =>
        prev.map((item) => ({
          ...item,
          detail: { ...item.detail, isSelected: checked },
        })),
      );
      setSelectionMap(() => {
        if (!checked) return new Map();
        const next = new Map<string, SelectedStat>();
        for (const item of inventoryItems) {
          const stat = statFromItem(item);
          next.set(stat.id, stat);
        }
        return next;
      });
    },
    [inventoryItems],
  );

  const handleItemSelection = useCallback((id: string, selected: boolean) => {
    setHasManualEdits(true);
    setInventoryItems((prev) =>
      prev.map((item) =>
        item.detail.id === id
          ? { ...item, detail: { ...item.detail, isSelected: selected } }
          : item,
      ),
    );
    setSelectionMap((prev) => {
      const next = new Map(prev);
      if (selected) {
        const stat =
          statCacheRef.current.get(id) ??
          ({
            id,
            estimatedCost: 0,
            estimatedImpressions: 0,
            inventoryType: "",
          } as SelectedStat);
        next.set(id, stat);
      } else {
        next.delete(id);
      }
      return next;
    });
  }, []);

  // Persist per spec §3. Resolves true on success (caller closes the popup).
  const saveSelection = useCallback(async (): Promise<boolean> => {
    if (!campaignId) return false;
    setIsSaving(true);
    try {
      if (bulkMode) {
        const filters = buildFiltersWithoutPagination();
        await bulkSelectInventory({
          campaignId,
          operationType: bulkMode,
          filters,
        }).unwrap();
        // Individual toggles made AFTER the bulk action, relative to what the
        // bulk op produced (all ids if SELECT, none if DESELECT).
        const bulkBaseline =
          bulkMode === "SELECT"
            ? new Set(inventoryItems.map((i) => i.detail.id))
            : new Set<string>();
        const added = [...selectionMap.keys()].filter(
          (id) => !bulkBaseline.has(id),
        );
        const removed = [...bulkBaseline].filter((id) => !selectionMap.has(id));
        if (added.length)
          await bulkSelectByIds({
            campaignId,
            inventoryIds: added,
            operationType: "SELECT",
          }).unwrap();
        if (removed.length)
          await bulkSelectByIds({
            campaignId,
            inventoryIds: removed,
            operationType: "DESELECT",
          }).unwrap();
      } else {
        const baseline = baselineRef.current;
        const added = [...selectionMap.keys()].filter(
          (id) => !baseline.has(id),
        );
        const removed = [...baseline].filter((id) => !selectionMap.has(id));
        if (added.length)
          await bulkSelectByIds({
            campaignId,
            inventoryIds: added,
            operationType: "SELECT",
          }).unwrap();
        if (removed.length)
          await bulkSelectByIds({
            campaignId,
            inventoryIds: removed,
            operationType: "DESELECT",
          }).unwrap();
      }
      return true;
    } catch (error) {
      console.error("Error saving selection:", error);
      showError(tCampaigns("inventories.manual.saveFailed"));
      return false;
    } finally {
      setIsSaving(false);
    }
  }, [
    campaignId,
    bulkMode,
    selectionMap,
    inventoryItems,
    buildFiltersWithoutPagination,
    bulkSelectInventory,
    bulkSelectByIds,
    showError,
    tCampaigns,
  ]);

  // Bulk-select by pasted reference IDs — matching happens server-side
  // against the full campaign inventory set (not just the currently loaded
  // page), so unlike handleSelectAll/handleItemSelection there's no
  // optimistic local item update; callers should reload the visible list
  // once this resolves. In "replace" mode, deselects everything currently
  // matching the active filters first, then selects only the pasted IDs.
  const handlePasteReferenceIds = useCallback(
    async (referenceIds: string[], mode: "add" | "replace") => {
      if (!campaignId || referenceIds.length === 0) return undefined;
      setHasManualEdits(true);
      setIsBulkSyncing(true);
      try {
        if (mode === "replace") {
          const filters = buildFiltersWithoutPagination();
          await bulkSelectInventory({
            campaignId,
            operationType: "DESELECT",
            filters,
          }).unwrap();
        }
        const result = await bulkSelectInventoryByReferenceIds({
          campaignId,
          referenceIds,
          operationType: "SELECT",
        }).unwrap();
        await loadSelectedInventory();
        return result;
      } finally {
        setIsBulkSyncing(false);
      }
    },
    [
      campaignId,
      buildFiltersWithoutPagination,
      bulkSelectInventory,
      bulkSelectInventoryByReferenceIds,
      loadSelectedInventory,
    ],
  );

  const resetSelectionState = useCallback(() => {
    setInventoryItems([]);
    setSelectionMap(new Map());
    setBulkMode(null);
    baselineRef.current = new Set();
    statCacheRef.current = new Map();
  }, []);

  return {
    inventoryItems,
    totalElements,
    sovValues,
    isBulkSyncing,
    hasManualEdits,
    selectionMap,
    isLoadingSelection,
    isSaving,
    isSelecting: false,
    getSelectAllState,
    handleSelectAll,
    handleItemSelection,
    handlePasteReferenceIds,
    handleInventoryLoaded,
    loadSelectedInventory,
    saveSelection,
    resetSelectionState,
  };
}
