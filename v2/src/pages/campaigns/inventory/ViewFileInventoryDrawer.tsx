import { AgGridTable } from "@components/ui/AgGridTable/AgGridTable";
import { Badge } from "@components/ui/Badge";
import MapBoxWrapper, { MapControlsConfig } from "@components/ui/Mapbox";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { useGetInventoryByFileIdQuery } from "@services/inventory/inventorySlice";
import { useTranslate } from "@tolgee/react";
import { getLatitude, getLongitude } from "@utils/inventory.utils";
import type { ColDef } from "ag-grid-community";
import React, { useState, useMemo, useRef } from "react";

import { MapInventoryItem } from "../../../types/inventory.types";
import { InventoryFileSelectedItem } from "../../../types/inventory.types";

interface ViewFileInventoryDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  onBack?: () => void;
  fileId: string;
  fileName: string;
  campaignId: string;
}

// Map configuration
const mapConfig: MapControlsConfig = {
  showDrawingTools: false,
  enableSelect: false,
  enablePolygon: false,
  enableCircle: false,
  enableLine: false,
  enableDelete: false,
  showViewTools: true,
  enableMountainView: false,
  enable3D: true,
  showMapStyles: false,
  enabledStyles: [],
  search: {
    enabled: false,
    showResults: false,
    searchTypes: [],
    limit: 5,
    showPOIFilter: false,
  },
};

export const ViewFileInventoryDrawer: React.FC<
  ViewFileInventoryDrawerProps
> = ({ isOpen, onClose, onBack, fileId, fileName, campaignId }) => {
  const { t } = useTranslate(["campaigns"]);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Store mapping of inventory IDs to original items for popup
  const inventoryItemsMapRef = useRef<Map<string, InventoryFileSelectedItem>>(
    new Map(),
  );

  // Fetch inventory list by file ID
  const {
    data: inventoryData,
    isLoading,
    isFetching,
  } = useGetInventoryByFileIdQuery(
    {
      fileId,
      params: {
        page: currentPage - 1,
        size: pageSize,
        sortBy: "name",
        sortDir: "asc",
      },
    },
    {
      skip: !isOpen || !fileId || !campaignId,
    },
  );

  // Transform inventory items for table
  const tableData = useMemo(() => {
    if (!inventoryData?.data?.content) return [];
    return inventoryData.data.content.map(
      (item: InventoryFileSelectedItem, index: number) => ({
        ...item,
        srId: index,
      }),
    );
  }, [inventoryData]);

  // Transform inventory items for Mapbox (minimal MapInventoryItem format)
  // Only creates minimal structure needed for map markers, popup uses original data
  const mapInventoryItems = useMemo((): MapInventoryItem[] => {
    if (!inventoryData?.data?.content) return [];

    // Clear and rebuild the map
    inventoryItemsMapRef.current.clear();

    return inventoryData.data.content.map((item: InventoryFileSelectedItem) => {
      // Store original item for popup to use directly
      inventoryItemsMapRef.current.set(item.id, item);

      // Create minimal MapInventoryItem - only what MapBoxWrapper needs for markers
      const lat = item.location?.latitude || 0;
      const lng = item.location?.longitude || 0;

      return {
        id: item.id,
        detail: {
          id: item.id,
          name: item.inventoryName,
          externalId: item.id,
          referenceId: item.referenceId,
          mediaOwnerId: "",
          mediaOwnerName: "",
          inventoryType: item.type,
          category: item.category || "",
          venueType: "",
          thumbnail: "",
          images: [],
          format: "",
          size: "",
          operationMode: "",
          execution: "",
          screens: 0,
          sov: 0,
          isSelected: false,
          isCompliant: true,
          panels: [],
        },
        location: {
          location: {
            address: item.location?.address || "",
            country: item.location?.country || "",
            state: item.location?.state || "",
            city: "",
            zipCode: "",
            locationCoordinates: {
              coordinates: [{ latitude: lat, longitude: lng }],
              type: "Point",
            },
          },
          poi: { types: [], nearbyPOIs: [], categories: [] },
          demographics: {
            age: "",
            gender: "",
            overall: "",
            ageGender: "",
            income: "",
            behaviour: "",
            interest: "",
            highestIndexScore: "",
          },
        },
      };
    });
  }, [inventoryData]);

  // Calculate map center from inventory items
  const mapCenter: [number, number] = useMemo(() => {
    if (mapInventoryItems.length === 0) return [0, 0];

    const validItems = mapInventoryItems.filter((item) => {
      const lat = getLatitude(item.location.location);
      const lng = getLongitude(item.location.location);
      return lat !== undefined && lng !== undefined && lat !== 0 && lng !== 0;
    });

    if (validItems.length === 0) return [0, 0];

    const avgLng =
      validItems.reduce(
        (sum, item) => sum + (getLongitude(item.location.location) || 0),
        0,
      ) / validItems.length;
    const avgLat =
      validItems.reduce(
        (sum, item) => sum + (getLatitude(item.location.location) || 0),
        0,
      ) / validItems.length;

    return [avgLng, avgLat];
  }, [mapInventoryItems]);

  // Popup component for map markers - uses original InventoryFileSelectedItem data
  const InventoryMapPopupComponent = ({ item }: { item: MapInventoryItem }) => {
    // Get original item from map
    const originalItem = inventoryItemsMapRef.current.get(item.detail.id);

    if (!originalItem) {
      return null;
    }

    return (
      <div className="flex-1 min-w-0 space-y-1">
        {originalItem.thumbnail && (
          <img
            src={originalItem.thumbnail}
            alt={originalItem.inventoryName}
            className="rounded-md w-full h-32 object-cover"
          />
        )}
        <div className="inline-flex justify-start items-center">
          <h3 className="text-xs font-semibold leading-4 truncate max-w-[250px]">
            {originalItem.inventoryName || "-"}
          </h3>
        </div>
        <p className="text-xs text-secondary leading-4">
          {originalItem.location?.address ||
            `${originalItem.location?.country || ""}, ${originalItem.location?.state || ""}`}
        </p>
        <div className="flex flex-wrap gap-1.5">
          <Badge variant="outline" size="sm">
            {originalItem.type || "-"}
          </Badge>
          {originalItem.category && (
            <Badge
              className="outline-mw-rose-warning-400 text-mw-rose-warning-400"
              variant="outline"
              size="sm"
            >
              {originalItem.category}
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
        {originalItem.referenceId && (
          <p className="text-xs text-mw-neutral-500">
            {t("viewFileInventoryDrawer.refId")}: {originalItem.referenceId}
          </p>
        )}
      </div>
    );
  };

  const columnDefs = useMemo<ColDef<InventoryFileSelectedItem>[]>(
    () => [
      {
        headerName: t("viewFileInventoryDrawer.columns.srNo"),
        width: 80,
        valueGetter: (params) => (params.node?.rowIndex ?? 0) + 1,
      },
      {
        colId: "inventoryName",
        headerName: t("viewFileInventoryDrawer.columns.inventoryName"),
        field: "inventoryName",
        flex: 1,
        minWidth: 120,
        valueFormatter: (params) => (params.value ? params.value : "--"),
      },
      {
        colId: "referenceId",
        headerName: t("viewFileInventoryDrawer.columns.referenceId"),
        field: "referenceId",
        minWidth: 100,
        valueFormatter: (params) => (params.value ? params.value : "--"),
      },
      {
        colId: "latitude",
        headerName: t("viewFileInventoryDrawer.columns.latitude"),
        minWidth: 110,
        valueFormatter: (params) =>
          params.data ? params.data?.location?.latitude?.toFixed(6) : "--",
      },
      {
        colId: "longitude",
        headerName: t("viewFileInventoryDrawer.columns.longitude"),
        minWidth: 110,
        valueFormatter: (params) =>
          params.data ? params.data?.location?.longitude?.toFixed(6) : "--",
      },
      {
        colId: "type",
        headerName: t("viewFileInventoryDrawer.columns.inventoryType"),
        field: "type",
        minWidth: 120,
        valueFormatter: (params) => (params.value ? params.value : "--"),
      },
    ],
    [currentPage, pageSize, t],
  );

  const handlePageChange = (page: number) => {
    setCurrentPage(page);
  };

  const handlePageSizeChange = (size: number) => {
    setPageSize(size);
    setCurrentPage(1);
  };

  const handleBack = () => {
    if (onBack) {
      onBack();
    } else {
      onClose();
    }
  };

  // Calculate pagination values
  const totalItems = inventoryData?.data?.totalElements || 0;

  return (
    <>
      <ModalDrawer
        isOpen={isOpen}
        onClose={handleBack}
        title={fileName}
        size="custom"
        customWidth="60vw"
        showCloseButton={false}
        showBackButton={true}
      >
        <div className="flex flex-col gap-4 h-full">
          {/* Mapbox Section - Fixed Height */}
          {mapInventoryItems.length > 0 && mapCenter[0] !== 0 && (
            <div className="shrink-0">
              <div className="h-[350px] w-full">
                <MapBoxWrapper
                  defaultCenter={mapCenter}
                  defaultZoom={12}
                  controlsConfig={mapConfig}
                  locationsList={mapInventoryItems}
                  PopupComponent={InventoryMapPopupComponent}
                />
              </div>
            </div>
          )}

          {/* Table Section - Scrollable */}
          <div className="flex-1 overflow-hidden flex flex-col">
            <div className="flex-1 min-h-[200px] rounded-lg">
              <AgGridTable<InventoryFileSelectedItem>
                rowData={tableData}
                columnDefs={columnDefs}
                getRowId={(row) => row.id}
                loading={isLoading || isFetching}
                emptyMessage={t("viewFileInventoryDrawer.emptyMessage")}
                className="h-full"
                height="100%"
                serverSidePagination
                serverSideConfig={{
                  totalCount: totalItems,
                  currentPage,
                  pageSize,
                  onPageChange: handlePageChange,
                  onPageSizeChange: handlePageSizeChange,
                  pageSizeSelector: [10, 25, 50, 100],
                }}
              />
            </div>
          </div>
        </div>
      </ModalDrawer>
    </>
  );
};

export default ViewFileInventoryDrawer;
