import InventoryAvailabilityCalendarView from "@components/common/InventoryAvailabilityCalendarView";
import {
  SelectedInventoryListContainer,
  SelectedInventoryListContainerRef,
} from "@components/common/SelectedInventoryListContainer";
import { Card, CardContent } from "@components/ui/card";
import { Modal } from "@components/ui/Modal";
import { useTranslate } from "@tolgee/react";
import clsx from "clsx";
import { useEffect, useMemo, useRef, useState } from "react";
import { POIPlaceData } from "src/types/campaign.types";

import InventoryDetailsDrawer from "./InventoryDetailsDrawer";
import InventoryMapPanel from "./InventoryMapPanel";
import SelectedInventoryCard from "./SelectedInventoryCard";
import type { RecommendationScoreMap } from "./view/useRecommendationScores";
import { useSelectedInventoryAvailability } from "./view/useSelectedInventoryAvailability";
import type { InventoryItem } from "../../../types/inventory.types";

interface InventoryMapViewProps {
  campaignId: string;
  campaignCurrency?: string;
  /** When provided, the card delete button removes the inventory. Omit for read-only. */
  onItemSelection?: (id: string, selected: boolean) => void;
  showDeleteInventoryButton?: boolean;
  /** Show an eye icon per card that opens the inventory details drawer. */
  showViewDetailsButton?: boolean;
  campaignStartDate?: string;
  campaignEndDate?: string;
  /** Smart score + component scores per inventory referenceId. */
  scoresByReferenceId?: RecommendationScoreMap;
  /** Right-side panel: the Mapbox map (default) or the availability calendar. */
  rightPanel?: "map" | "availability";
  availablePOIs?: POIPlaceData[];
  /** Controls whether the selected-inventory list loads (e.g. drawer open). */
  enabled?: boolean;
  /** Height/utility classes for the outer flex container. */
  className?: string;
  /** Country slug (countries master) enabling the audience mobility heatmap. */
  mobilityCountrySlug?: string;
}

/**
 * Map + selected-inventory list for a campaign. Shared by the map-view drawer
 * and the full-page "View Inventories" screen. Pass `onItemSelection` +
 * `showDeleteInventoryButton` to enable removal; omit for a read-only view.
 */
export const InventoryMapView: React.FC<InventoryMapViewProps> = ({
  campaignId,
  campaignCurrency = "",
  onItemSelection,
  showDeleteInventoryButton = true,
  showViewDetailsButton = false,
  campaignStartDate,
  campaignEndDate,
  scoresByReferenceId,
  rightPanel = "map",
  availablePOIs,
  enabled = true,
  className = "h-full",
  mobilityCountrySlug,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const [searchQuery, setSearchQuery] = useState("");
  const [itemToDelete, setItemToDelete] = useState<InventoryItem | null>(null);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [detailsExternalId, setDetailsExternalId] = useState("");
  const [isDetailsOpen, setIsDetailsOpen] = useState(false);
  const [totalElements, setTotalElements] = useState(0);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  // Availability % per inventory over the campaign range; fetched lazily when
  // a card is expanded.
  const { availabilityById, requestAvailability } =
    useSelectedInventoryAvailability({
      startDate: campaignStartDate,
      endDate: campaignEndDate,
    });

  const toggleExpanded = (item: InventoryItem) => {
    const id = item.detail.id;
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
        // Fetch availability the first time this card is expanded.
        requestAvailability(item);
      }
      return next;
    });
  };
  const [selectedInventoryId, setSelectedInventoryId] = useState<
    string | undefined
  >(undefined);
  const [selectedItems, setSelectedItems] = useState<InventoryItem[]>([]);
  const inventoryListRef = useRef<SelectedInventoryListContainerRef>(null);
  const listWrapRef = useRef<HTMLDivElement>(null);
  // The currently highlighted inventory (drives map fly-to + availability calendar).
  const selectedInventory = useMemo(
    () =>
      selectedItems.find((i) => i.detail.id === selectedInventoryId) ?? null,
    [selectedItems, selectedInventoryId],
  );

  const filterItems = useMemo(
    () => (items: InventoryItem[], query: string) => {
      if (!query) return items;
      const lowerQuery = query.toLowerCase();
      return items.filter(
        (item) =>
          item.detail.name?.toLowerCase().includes(lowerQuery) ||
          item.location.location.address?.toLowerCase().includes(lowerQuery),
      );
    },
    [],
  );

  const handleDeleteClick = (item: InventoryItem) => {
    setItemToDelete(item);
    setIsDeleteModalOpen(true);
  };

  const handleConfirmDelete = async () => {
    if (itemToDelete) {
      await onItemSelection?.(itemToDelete.detail.id, false);
      setItemToDelete(null);
      setIsDeleteModalOpen(false);
      await inventoryListRef.current?.refetch();
    }
  };

  const handleCancelDelete = () => {
    setItemToDelete(null);
    setIsDeleteModalOpen(false);
  };

  const handleCardClick = (item: InventoryItem) => {
    setSelectedInventoryId(item.detail.id);
  };

  // Scroll the list to the highlighted inventory when a map pin is clicked.
  useEffect(() => {
    if (!selectedInventoryId || !listWrapRef.current) return;
    const el = listWrapRef.current.querySelector(
      `[data-selected-inv-id="${selectedInventoryId}"]`,
    );
    el?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [selectedInventoryId]);

  const handleViewDetails = (item: InventoryItem) => {
    setDetailsExternalId(item.detail.externalId);
    setIsDetailsOpen(true);
  };

  return (
    <>
      <div className={clsx("flex gap-4", className)}>
        {/* Left Side - Selected Inventories List */}
        <div ref={listWrapRef} className="w-96 space-y-4 overflow-hidden">
          <SelectedInventoryListContainer
            ref={inventoryListRef}
            campaignId={campaignId}
            enabled={enabled && !!campaignId}
            containerClassName="h-full"
            showSimpleHeader={true}
            simpleHeaderContent={
              <div className="inline-flex justify-start items-center gap-2">
                <h4 className="text-lg font-semibold text-mw-grey-900">
                  {tCampaigns("inventories.title")}
                </h4>
                <span className="rounded-md bg-mw-primary-50 px-2.5 py-1 text-xs font-medium text-mw-primary-600">
                  {totalElements}{" "}
                  {tCampaigns("inventoryMapView.inventoriesCount")}
                </span>
              </div>
            }
            simpleHeaderClassName="p-4 pb-2"
            showSearch={true}
            searchPlaceholder={tCampaigns("inventoryMapView.searchPlaceholder")}
            searchValue={searchQuery}
            onSearchChange={setSearchQuery}
            searchBeforeContent={true}
            showCount={false}
            onTotalElementsChange={setTotalElements}
            contentClassName="p-4 pt-0 h-full"
            useNestedScrollContainer={true}
            nestedScrollClassName="flex-1 space-y-3 pr-2 max-h-[calc(100vh-280px)]"
            filterItems={filterItems}
            emptyMessage={tCampaigns("inventoryMapView.emptyMessage")}
            emptySubMessage={(query) =>
              query
                ? tCampaigns("inventoryMapView.emptySubMessageWithQuery")
                : tCampaigns("inventoryMapView.emptySubMessageNoQuery")
            }
            loadingMoreText={tCampaigns("inventoryMapView.loadingMoreText")}
            onInitialLoad={(items) => {
              setSelectedItems(items);
              if (items.length > 0 && !selectedInventoryId) {
                setSelectedInventoryId(items[0].detail.id);
              }
            }}
            onLoadMore={(items) =>
              setSelectedItems((prev) => [...prev, ...items])
            }
            renderItem={(item, index) => (
              <div key={item.detail.id} data-selected-inv-id={item.detail.id}>
                <SelectedInventoryCard
                  item={item}
                  index={index}
                  isSelected={selectedInventoryId === item.detail.id}
                  isExpanded={expandedIds.has(item.detail.id)}
                  score={scoresByReferenceId?.[item.detail.referenceId]}
                  campaignCurrency={campaignCurrency}
                  availability={availabilityById[item.detail.id]}
                  showViewDetailsButton={showViewDetailsButton}
                  showDeleteInventoryButton={showDeleteInventoryButton}
                  onCardClick={() => handleCardClick(item)}
                  onToggleExpand={() => toggleExpanded(item)}
                  onViewDetails={() => handleViewDetails(item)}
                  onDelete={() => handleDeleteClick(item)}
                />
              </div>
            )}
          />
        </div>

        {/* Right Side - Map or Availability calendar */}
        {/* min-w-0: without it this flex item grows to the daily grid's full
            content width (~2000px) and widens the whole modal past the viewport.
            Constrained, the calendar's inner overflow-auto scrolls instead. */}
        <div className="flex-1 min-w-0">
          <Card className="pt-4 h-full">
            <CardContent className="h-full">
              {rightPanel === "availability" ? (
                <InventoryAvailabilityCalendarView
                  inventoryData={selectedInventory}
                  campaignStartDate={campaignStartDate}
                  campaignEndDate={campaignEndDate}
                />
              ) : (
                <InventoryMapPanel
                  items={selectedItems}
                  selectedItemId={selectedInventoryId}
                  availablePOIs={availablePOIs}
                  onMarkerClick={(id) => setSelectedInventoryId(id)}
                  mobilityCountrySlug={mobilityCountrySlug}
                />
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      {/* Delete Confirmation Modal */}
      <Modal
        isOpen={isDeleteModalOpen}
        onClose={handleCancelDelete}
        title={tCampaigns("inventoryMapView.removeFile")}
        primaryButtonText={tCampaigns("inventoryMapView.yesRemove")}
        secondaryButtonText={tCampaigns("inventoryMapView.dontRemove")}
        onPrimaryAction={handleConfirmDelete}
        onSecondaryAction={handleCancelDelete}
        primaryButtonVariant="danger"
        size="sm"
      >
        <p>
          {tCampaigns("inventoryMapView.removeConfirmMessage")}{" "}
          <strong>
            "
            {itemToDelete?.detail.name ||
              tCampaigns("inventoryMapView.thisItem")}
            "
          </strong>
          ?
        </p>
      </Modal>

      {/* Inventory Details Drawer */}
      {showViewDetailsButton && detailsExternalId !== "" && (
        <InventoryDetailsDrawer
          isOpen={isDetailsOpen}
          onClose={() => setIsDetailsOpen(false)}
          availablePOIs={availablePOIs}
          campaignStartDate={campaignStartDate}
          campaignEndDate={campaignEndDate}
          externalInventoryId={detailsExternalId}
        />
      )}
    </>
  );
};

export default InventoryMapView;
