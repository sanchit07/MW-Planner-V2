import { InventoryDetailCard } from "@components/common/InventoryDetailCard";
import { SelectedInventoryListContainer } from "@components/common/SelectedInventoryListContainer";
import { Badge } from "@components/ui/Badge";
import MapBoxWrapper, { MapControlsConfig } from "@components/ui/Mapbox";
import { EmptyValueDisplay } from "@constants/inventory.constants";
import { useTranslate } from "@tolgee/react";
import { formatCurrency } from "@utils/campaign.utils";
import { getLatitude, getLongitude } from "@utils/inventory.utils";
import { clsx } from "clsx";
import { useMemo, useState } from "react";
import { CampaignCreateResponse } from "src/types/campaign.types";
import { InventoryItem } from "src/types/inventory.types";

import InventoryAvailabilityCalendarView from "../../../components/common/InventoryAvailabilityCalendarView";
import InventoryDetailsDrawer from "../inventory/InventoryDetailsDrawer";

interface MapAvailabilityViewProps {
  isMapView: boolean;
  campaignId: string | undefined;
  campaignCurrency?: string;
  campaignData?: CampaignCreateResponse | null;
}

const MapAvailabilityView: React.FC<MapAvailabilityViewProps> = ({
  isMapView,
  campaignId,
  campaignCurrency,
  campaignData,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedInventoryId, setSelectedInventoryId] = useState<
    string | undefined
  >(undefined);

  const [isDetailsDrawerOpen, setIsDetailsDrawerOpen] = useState(false);
  const [selectedInventory, setSelectedInventory] =
    useState<InventoryItem | null>(null);
  const [selectedItems, setSelectedItems] = useState<InventoryItem[]>([]);

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
      showResults: false,
      searchTypes: ["place", "poi", "address"],
      limit: 5,
      showPOIFilter: false,
    },
  };

  // Filter function for the container
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

  // Calculate map center from selected items
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

  // Get filtered items for map
  const filteredItems = useMemo(() => {
    return filterItems(selectedItems, searchQuery);
  }, [selectedItems, searchQuery, filterItems]);

  // Handle view details
  const handleViewDetails = (inventory: InventoryItem) => {
    setSelectedInventory(inventory);
    setSelectedInventoryId(inventory.detail.id);
    setIsDetailsDrawerOpen(true);
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
            {item.detail.inventoryType}
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
              {item.detail.environment}
            </Badge>
          )}
          {item.detail.size && (
            <Badge
              className="outline-mw-orange-warning-500 text-mw-orange-warning-500"
              variant="outline"
              size="sm"
            >
              {item.detail.size.toUpperCase()}
            </Badge>
          )}
        </div>
      </div>
    );
  };

  // Handle card click to show popup on map
  const handleCardClick = (item: InventoryItem) => {
    setSelectedInventory(item);
    setSelectedInventoryId(item.detail.id);
  };

  return (
    <>
      <div className="flex gap-4 h-full">
        <div className="inventory-list w-100 h-full">
          <SelectedInventoryListContainer
            campaignId={campaignId}
            enabled={!!campaignId}
            containerClassName="flex flex-col h-full"
            showSimpleHeader={true}
            simpleHeaderContent={
              <div className="inline-flex justify-start items-center gap-2">
                <h4 className="font-medium text-sm">
                  {tCampaigns("inventories.title")}
                </h4>
              </div>
            }
            showSearch={true}
            searchClassName="pt-0!"
            searchPlaceholder={tCampaigns("inventories.search_placeholder")}
            searchValue={searchQuery}
            onSearchChange={setSearchQuery}
            searchBeforeContent={true}
            contentClassName="flex-1 flex flex-col gap-4"
            filterItems={filterItems}
            emptyMessage={tCampaigns("inventoryPageForm.noInventoriesFound")}
            onInitialLoad={(items) => {
              setSelectedItems(items);
              // Auto-select first item on initial load
              if (
                items.length > 0 &&
                !selectedInventory &&
                !selectedInventoryId
              ) {
                setSelectedInventory(items[0]);
                setSelectedInventoryId(items[0].detail.id);
              }
            }}
            onLoadMore={(items) =>
              setSelectedItems((prev) => [...prev, ...items])
            }
            renderItem={(item, index) => (
              <div
                key={`map-availability-item-${index}`}
                onClick={() => handleCardClick(item)}
                className={clsx("border rounded-lg border-container-border", {
                  " border-mw-primary-500":
                    selectedInventoryId === item.detail.id,
                })}
              >
                <InventoryDetailCard
                  cardClassName={clsx({
                    "bg-mw-primary-50!": selectedInventoryId === item.detail.id,
                  })}
                  key={item.detail.id}
                  item={item}
                  campaignCurrency={campaignCurrency}
                  formatCurrency={formatCurrency}
                  tCampaigns={tCampaigns}
                  emptyValueDisplay={EmptyValueDisplay.DASH}
                  fromPriceManagement={true}
                  onViewDetails={() => handleViewDetails(item)}
                  showSmartSuggestionScore={false}
                  campaignStartDate={campaignData?.startDate}
                  campaignEndDate={campaignData?.endDate}
                  isNegotiated={campaignData?.isNegotiated}
                />
              </div>
            )}
          />
        </div>
        <div className="flex-1 min-w-0 overflow-hidden">
          {isMapView ? (
            <MapBoxWrapper
              defaultCenter={mapCenter}
              defaultZoom={12}
              controlsConfig={mapConfig}
              locationsList={filteredItems}
              selectedItemId={selectedInventoryId}
              PopupComponent={InventoryMapPopupComponent}
            />
          ) : (
            <InventoryAvailabilityCalendarView
              inventoryData={selectedInventory}
              campaignStartDate={campaignData?.startDate}
              campaignEndDate={campaignData?.endDate}
            />
          )}
        </div>
      </div>

      {/* Details Drawer */}
      <InventoryDetailsDrawer
        isOpen={isDetailsDrawerOpen}
        onClose={() => setIsDetailsDrawerOpen(false)}
        campaignStartDate={campaignData?.startDate}
        campaignEndDate={campaignData?.endDate}
        externalInventoryId={selectedInventory?.detail.externalId || ""}
      />
    </>
  );
};

export default MapAvailabilityView;
