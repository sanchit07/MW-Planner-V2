import { toCountrySlug } from "@components/map/MobilityHeatmap";
import { Button } from "@components/ui/Button";
import { Input } from "@components/ui/Input";
import { type TreeNode } from "@components/ui/MultiSelect";
import { useAnnounce } from "@hooks/useAnnounce";
import InventoryCsvUploadDrawer from "@pages/campaigns/inventory/InventoryCsvUploadDrawer";
import InventoryDetailsDrawer from "@pages/campaigns/inventory/InventoryDetailsDrawer";
import InventoryFilterDrawer from "@pages/campaigns/inventory/InventoryFilterDrawer";
import { useRecommendationScores } from "@pages/campaigns/inventory/view/useRecommendationScores";
import InventoryListPanel, {
  type InventoryListPanelRef,
} from "@pages/campaigns/inventory/InventoryListPanel";
import { useTranslate } from "@tolgee/react";
import { formatCurrencyWithLocale } from "@utils/currency";
import { extractGeofencingPOIs } from "@utils/geofencing-pois";
import {
  applyChannelClassificationLock,
  getTargetingVenueTypes,
  getVenueDrivenClassificationDefault,
} from "@utils/inventory.utils";
import { clsx } from "clsx";
import {
  ClipboardList,
  FileDown,
  Search,
  SlidersHorizontal,
  X,
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import type { CampaignCreateResponse } from "src/types/campaign.types";

import InventoryMapPanel from "../InventoryMapPanel";
import MapJumpToDropdown, { type MapJumpOption } from "./MapJumpToDropdown";
import PasteReferenceIdsModal, {
  type PasteReferenceIdsMode,
} from "./PasteReferenceIdsModal";
import {
  computeChannelRows,
  computeFooterTotals,
} from "./selection-stats.utils";
import SelectionFooter from "./SelectionFooter";
import { useManualInventorySelection } from "./useManualInventorySelection";
import { setInventoryFilters } from "../../../../services/stepper/stepperSlice";
import {
  type RootState,
  useAppDispatch,
  useAppSelector,
} from "../../../../store";
import type {
  InventoryFilters,
  InventoryItem,
} from "../../../../types/inventory.types";

interface ManualSelectionPageProps {
  isOpen: boolean;
  onClose: () => void;
  /** Called whenever the user manually selects/deselects an inventory item. */
  onManualEditsChange?: (hasEdits: boolean) => void;
}

/**
 * Full-page popup (slide-in) for manually editing the campaign's inventory
 * selection — list (InventoryListPanel) on the left, map on the right.
 * Selection state + handlers live in useManualInventorySelection. Only
 * openable once the recommendation has finished (the Edit Manually button is
 * disabled until then), so the list is always ready to load.
 */
const ManualSelectionPage = ({
  isOpen,
  onClose,
  onManualEditsChange,
}: ManualSelectionPageProps) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { showSuccess, showError } = useAnnounce();
  const dispatch = useAppDispatch();

  const campaignState = useAppSelector((state: RootState) => state.campaign);
  const inventoryFilters = useAppSelector(
    (state: RootState) => state.stepper.inventoryFilters,
  );
  const currentCompany = useAppSelector(
    (state: RootState) => state.profile.profile?.current_company,
  );

  const campaignData =
    campaignState?.campaignData as CampaignCreateResponse | null;
  const campaignId = campaignState.campaignId || campaignData?.id || "";
  const campaignCurrency = campaignData?.currency || "";
  const runId = campaignState.recommendationRun?.runId;

  // Recommendation-run annotations per referenceId (availability, isExcluded)
  // so browse-list items show "Limited availability" / "Unavailable" states
  // even when they weren't auto-selected.
  const scoresByReferenceId = useRecommendationScores(
    campaignId,
    runId,
    isOpen && !!runId,
  );

  const isMediaOwner = currentCompany?.company_type?.code === "MEDIA_OWNER";
  const mediaOwnerDefaultIds = useMemo(() => {
    if (!isMediaOwner || !currentCompany?.id) return [];
    const childIds =
      currentCompany.childCompanies?.items?.map((c) => c.id) ?? [];
    return Array.from(new Set([currentCompany.id, ...childIds]));
  }, [isMediaOwner, currentCompany?.id, currentCompany?.childCompanies?.items]);

  // Media-owner login → the media-owner filter lists the owned company + its
  // direct child companies (from user-info) instead of the country-wide API
  // list. `undefined` for non-media-owners keeps the current API-backed flow.
  const mediaOwnerStaticOptions = useMemo<TreeNode[] | undefined>(() => {
    if (!isMediaOwner || !currentCompany?.id) return undefined;
    const children = currentCompany.childCompanies?.items ?? [];
    return [
      {
        id: currentCompany.id,
        label: currentCompany.name,
        value: currentCompany.id,
      },
      ...children.map((c) => ({ id: c.id, label: c.name, value: c.id })),
    ];
  }, [
    isMediaOwner,
    currentCompany?.id,
    currentCompany?.name,
    currentCompany?.childCompanies?.items,
  ]);

  const effectiveFilters = useMemo(() => {
    let filters = inventoryFilters;
    if (
      isMediaOwner &&
      mediaOwnerDefaultIds.length > 0 &&
      !filters.mediaOwners?.length
    ) {
      filters = { ...filters, mediaOwners: mediaOwnerDefaultIds };
    }
    // Single campaign media channel locks the classification filter so the
    // list, bulk select-all and manual selection only touch that channel's
    // inventory. Both channels selected -> no classification forced.
    return applyChannelClassificationLock(filters, campaignData?.mediaChannels);
  }, [
    isMediaOwner,
    mediaOwnerDefaultIds,
    inventoryFilters,
    campaignData?.mediaChannels,
  ]);

  const sel = useManualInventorySelection(
    campaignId,
    campaignData,
    effectiveFilters,
    isMediaOwner ? mediaOwnerDefaultIds : [],
  );

  const [externalInventoryId, setExternalInventoryId] = useState("");
  const [isDetailsOpen, setIsDetailsOpen] = useState(false);
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  const [isUploadOpen, setIsUploadOpen] = useState(false);
  const [isPasteIdsOpen, setIsPasteIdsOpen] = useState(false);

  // Resizable list/map split — drag handle between the two panes.
  const splitContainerRef = useRef<HTMLDivElement>(null);
  const [leftWidthPercent, setLeftWidthPercent] = useState(50);
  const [isDraggingSplit, setIsDraggingSplit] = useState(false);

  useEffect(() => {
    if (!isDraggingSplit) return;
    const handleMouseMove = (e: MouseEvent) => {
      const container = splitContainerRef.current;
      if (!container) return;
      const rect = container.getBoundingClientRect();
      const percent = ((e.clientX - rect.left) / rect.width) * 100;
      setLeftWidthPercent(Math.min(75, Math.max(25, percent)));
    };
    const handleMouseUp = () => setIsDraggingSplit(false);
    window.addEventListener("mousemove", handleMouseMove);
    window.addEventListener("mouseup", handleMouseUp);
    return () => {
      window.removeEventListener("mousemove", handleMouseMove);
      window.removeEventListener("mouseup", handleMouseUp);
    };
  }, [isDraggingSplit]);
  const [selectedMapId, setSelectedMapId] = useState<string | undefined>(
    undefined,
  );
  const [flyTarget, setFlyTarget] = useState<{
    lng: number;
    lat: number;
  } | null>(null);
  const [channelsOpen, setChannelsOpen] = useState(false);

  const inventoryListPanelRef = useRef<InventoryListPanelRef>(null);
  const searchTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const availablePOIs = useMemo(
    () => extractGeofencingPOIs(campaignData),
    [campaignData],
  );

  // Read-only "jump-to" options: geofencing locations + POIs. Selecting one
  // flies the map to its coords (view only — no edit/delete).
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

  // Slide-in/out animation.
  const [mounted, setMounted] = useState(isOpen);
  const [visible, setVisible] = useState(isOpen);
  useEffect(() => {
    if (isOpen) {
      setMounted(true);
      let raf2 = 0;
      const raf1 = requestAnimationFrame(() => {
        raf2 = requestAnimationFrame(() => setVisible(true));
      });
      return () => {
        cancelAnimationFrame(raf1);
        cancelAnimationFrame(raf2);
      };
    }
    setVisible(false);
    const timer = setTimeout(() => setMounted(false), 300);
    return () => clearTimeout(timer);
  }, [isOpen]);

  // On open: reset transient state and loop-fetch the current selection.
  useEffect(() => {
    if (isOpen) {
      sel.resetSelectionState();
      sel.loadSelectedInventory();
      setChannelsOpen(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen]);

  // Surface manual edits to the parent so it can decide whether to show the
  // AI panel's Restore button — persists across this popup closing/reopening.
  useEffect(() => {
    if (sel.hasManualEdits) onManualEditsChange?.(true);
  }, [sel.hasManualEdits, onManualEditsChange]);

  // Seed the inventory filter from the Targeting step's venue-type selection,
  // once per step entry (mount): Venue Types are OVERWRITTEN with the targeting
  // union (digital+classic) so the filter always mirrors the latest targeting
  // when the user re-enters the inventory step. Within the step the field stays
  // editable (the ref stops re-seeding on campaign-data refreshes). When both
  // channels are selected but venue types were set for only one, the
  // classification defaults to that channel (one-time — user changes win).
  const filterSeededRef = useRef(false);
  useEffect(() => {
    if (filterSeededRef.current || !campaignData) return;
    filterSeededRef.current = true;
    const venueSeed = getTargetingVenueTypes(campaignData);
    const classSeed = getVenueDrivenClassificationDefault(campaignData);
    dispatch(
      setInventoryFilters({
        ...inventoryFilters,
        venueTypes: venueSeed,
        inventoryClassification: inventoryFilters.inventoryClassification.length
          ? inventoryFilters.inventoryClassification
          : classSeed,
      }),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [campaignData]);

  useEffect(() => {
    return () => {
      if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current);
    };
  }, []);

  const handleSearchChange = (value: string) => {
    if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current);
    dispatch(
      setInventoryFilters({ ...inventoryFilters, searchbyquery: value }),
    );
    if (value.trim().length === 0 || value.trim().length >= 3) {
      searchTimeoutRef.current = setTimeout(() => {
        inventoryListPanelRef.current?.reload(value);
      }, 500);
    }
  };

  const handleApplyFilters = (newFilters: InventoryFilters) => {
    dispatch(setInventoryFilters(newFilters));
    setTimeout(() => inventoryListPanelRef.current?.reload(), 0);
  };

  const getActiveFilterCount = (): number => {
    const f = inventoryFilters;
    let count = 0;
    if (f.mediaOwners?.length > 0) count++;
    if (f.venueTypes?.length > 0) count++;
    if (f.inventoryClassification?.length > 0) count++;
    if (f.bookingMode?.length > 0) count++;
    if (f.sizes?.length > 0) count++;
    if (f.environments?.length > 0) count++;
    if (f.programmaticSupport && f.programmaticSupport !== "ALL") count++;
    if (f.dealTypes?.length > 0) count++;
    if (f.cinemaGenres?.length) count++;
    if (f.cinemaRatings?.length) count++;
    if (f.latitude?.trim()) count++;
    if (f.longitude?.trim()) count++;
    return count;
  };

  const handleCsvImport = async (): Promise<{
    success: boolean;
    errors?: string[];
  }> => new Promise((resolve) => resolve({ success: true }));

  const handleViewDetails = (externalId: string) => {
    setExternalInventoryId(externalId);
    setIsDetailsOpen(true);
  };

  // Bulk-selects by pasted reference IDs via the backend, which matches
  // against the full campaign inventory set (not just what's currently
  // loaded/filtered on screen). In "replace" mode, everything matching the
  // active filters is deselected first so only the pasted IDs end up
  // selected; reloads the list afterwards to reflect the new state.
  const handlePasteIds = async (
    referenceIds: string[],
    mode: PasteReferenceIdsMode,
  ) => {
    try {
      const result = await sel.handlePasteReferenceIds(referenceIds, mode);
      await inventoryListPanelRef.current?.reload();
      const message = result?.data as unknown as string | undefined;
      showSuccess(message || tCampaigns("inventories.manual.pasteIds.success"));
    } catch (error) {
      console.error("Error bulk-selecting pasted reference IDs:", error);
      showError(tCampaigns("inventories.manual.selectFailed"));
    }
  };

  // X / Cancel: discard — just close. Reopen re-fetches the saved selection.
  const handleClose = () => onClose();

  const handleSaveSelection = async () => {
    const ok = await sel.saveSelection();
    if (ok) onClose();
  };

  const budget = campaignData?.budget ?? 0;
  const footerTotals = useMemo(
    () => computeFooterTotals(sel.selectionMap, budget),
    [sel.selectionMap, budget],
  );
  const channelRows = useMemo(
    () =>
      computeChannelRows(
        sel.selectionMap,
        campaignData?.mediaChannels ?? [],
        campaignData?.budgetAllocation,
        budget,
      ),
    [
      sel.selectionMap,
      campaignData?.mediaChannels,
      campaignData?.budgetAllocation,
      budget,
    ],
  );

  if (!mounted) return null;

  return (
    <div
      className={clsx(
        "fixed inset-0 z-50 flex flex-col overflow-hidden bg-white transition-transform duration-300 ease-in-out",
        visible ? "translate-x-0" : "translate-x-full",
      )}
    >
      {/* Header */}
      <div className="flex items-center justify-between gap-4 px-4 py-3 border-b border-mw-neutral-100 shrink-0">
        <h1 className="text-base font-semibold text-mw-grey-800">
          {tCampaigns("inventories.manual.title")}
        </h1>
        <button
          type="button"
          onClick={handleClose}
          aria-label={tCampaigns("inventories.manual.close")}
          className="text-mw-neutral-600 hover:text-mw-grey-900"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      {/* Body: list left, map right (resizable) */}
      <div
        ref={splitContainerRef}
        className={clsx(
          "flex-1 min-h-0 flex gap-0 p-4 overflow-hidden",
          isDraggingSplit && "select-none cursor-col-resize",
        )}
      >
        <div
          className="min-w-0 flex flex-col gap-3 pr-4"
          style={{ width: `${leftWidthPercent}%` }}
        >
          <div className="flex items-center gap-2 shrink-0">
            <div className="relative flex-1">
              <Input
                type="text"
                placeholder={tCampaigns("inventoryMapView.searchPlaceholder")}
                value={inventoryFilters.searchbyquery || ""}
                onChange={(e) => handleSearchChange(e.target.value)}
                className="pr-8"
              />
              <Search className="absolute right-2 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 pointer-events-none" />
            </div>
            <div className="relative">
              <Button
                variant="outline"
                size="iconMd"
                onClick={() => setIsFilterOpen(true)}
                aria-label={tCampaigns("inventories.filters.title")}
              >
                <SlidersHorizontal className="w-4 h-4" />
              </Button>
              {getActiveFilterCount() > 0 && (
                <div className="absolute -top-2 -right-1 h-5 w-5 flex items-center justify-center rounded-sm bg-mw-primary-500 text-white text-xs font-medium">
                  {getActiveFilterCount()}
                </div>
              )}
            </div>
            <Button
              variant="outline"
              size="iconMd"
              onClick={() => setIsUploadOpen(true)}
              aria-label={tCampaigns("inventories.menu.upload_csv")}
            >
              <FileDown className="w-4 h-4" />
            </Button>
            <Button
              variant="outline"
              size="iconMd"
              onClick={() => setIsPasteIdsOpen(true)}
              aria-label={tCampaigns("inventories.manual.pasteIds.title")}
            >
              <ClipboardList className="w-4 h-4" />
            </Button>
          </div>

          <div className="flex-1 min-h-0">
            <InventoryListPanel
              ref={inventoryListPanelRef}
              campaignId={campaignId}
              campaignData={campaignData}
              inventoryFilters={effectiveFilters}
              filtersLoadedFromStorage={isOpen}
              selectedFirst={true}
              isUploadDrawerOpen={isUploadOpen}
              groupBy="all"
              setGroupBy={() => {}}
              getSelectAllState={sel.getSelectAllState}
              handleSelectAll={sel.handleSelectAll}
              isSelecting={sel.isSelecting}
              totalElements={sel.totalElements}
              selectedCount={sel.selectionMap.size}
              tCampaigns={tCampaigns}
              inventoryItems={sel.inventoryItems}
              handleViewDetails={handleViewDetails}
              campaignCurrency={campaignCurrency}
              formatCurrency={formatCurrencyWithLocale}
              handleItemSelection={sel.handleItemSelection}
              sovValues={sel.sovValues}
              onInventoryLoaded={sel.handleInventoryLoaded}
              selectedInventoryId={selectedMapId}
              onCardClick={(item: InventoryItem) =>
                setSelectedMapId(item.detail.id)
              }
              scoresByReferenceId={scoresByReferenceId}
            />
          </div>
        </div>

        {/* Drag handle */}
        <div
          role="separator"
          aria-orientation="vertical"
          aria-label={tCampaigns("inventories.manual.resizeHandle")}
          onMouseDown={() => setIsDraggingSplit(true)}
          className="w-1.5 shrink-0 mx-1 rounded-full bg-mw-neutral-100 hover:bg-mw-primary-200 cursor-col-resize"
        />

        <div
          className="relative shrink-0 rounded-lg overflow-hidden border border-mw-neutral-100"
          style={{ width: `${100 - leftWidthPercent}%` }}
        >
          <div className="absolute top-3 left-3 right-3 z-10 flex gap-2">
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
          <InventoryMapPanel
            items={sel.inventoryItems}
            selectedItemId={selectedMapId}
            availablePOIs={availablePOIs}
            onMarkerClick={(id) => setSelectedMapId(id)}
            flyTo={flyTarget}
            mobilityCountrySlug={toCountrySlug(campaignData?.countryId)}
          />
        </div>
      </div>

      {/* Stats footer (budget-by-channel popup is anchored to its toggle inside) */}
      <SelectionFooter
        totals={footerTotals}
        budget={budget}
        currency={campaignCurrency}
        channelRows={channelRows}
        isSaving={sel.isSaving}
        onCancel={handleClose}
        onSave={handleSaveSelection}
        onToggleChannels={() => setChannelsOpen((v) => !v)}
        onCloseChannels={() => setChannelsOpen(false)}
        channelsOpen={channelsOpen}
      />

      {/* Details Drawer */}
      {campaignId && externalInventoryId !== "" && (
        <InventoryDetailsDrawer
          isOpen={isDetailsOpen}
          onClose={() => setIsDetailsOpen(false)}
          availablePOIs={availablePOIs}
          campaignStartDate={campaignData?.startDate}
          campaignEndDate={campaignData?.endDate}
          externalInventoryId={externalInventoryId}
        />
      )}

      {/* Filter Drawer */}
      <InventoryFilterDrawer
        isOpen={isFilterOpen}
        onClose={() => setIsFilterOpen(false)}
        filters={inventoryFilters}
        onApplyFilters={handleApplyFilters}
        country={campaignData?.countryId}
        goal_type={campaignData?.goals?.goalType}
        media_channels={campaignData?.mediaChannels}
        mediaOwnerStaticOptions={mediaOwnerStaticOptions}
      />

      {/* CSV Upload Drawer */}
      {campaignId && (
        <InventoryCsvUploadDrawer
          isOpen={isUploadOpen}
          onClose={() => {
            setIsUploadOpen(false);
            inventoryListPanelRef.current?.reload();
            sel.loadSelectedInventory();
          }}
          campaignId={campaignId}
          campaignData={campaignData}
          onImport={handleCsvImport}
        />
      )}

      {/* Paste Reference IDs Modal */}
      <PasteReferenceIdsModal
        isOpen={isPasteIdsOpen}
        onClose={() => setIsPasteIdsOpen(false)}
        onSubmit={handlePasteIds}
      />
    </div>
  );
};

export default ManualSelectionPage;
