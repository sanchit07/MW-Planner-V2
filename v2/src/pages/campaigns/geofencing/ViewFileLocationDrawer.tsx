import { AgGridTable } from "@components/ui/AgGridTable/AgGridTable";
import { Badge } from "@components/ui/Badge";
import MapBoxWrapper, { MapControlsConfig } from "@components/ui/Mapbox";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { useGetGeoImportLocationsQuery } from "@services/inventory/inventorySlice";
import { useTranslate } from "@tolgee/react";
import { getLatitude, getLongitude } from "@utils/inventory.utils";
import type {
  ColDef,
  ICellRendererParams,
  ValueGetterParams,
} from "ag-grid-community";
import React, { useMemo, useRef } from "react";

import { GeoImportLocation } from "./types/location-csv.types";
import { MapInventoryItem } from "../../../types/inventory.types";

type LocationRow = GeoImportLocation & { srId: number };

interface ViewFileLocationDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  onBack?: () => void;
  fileId: string;
  fileName: string;
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

export const ViewFileLocationDrawer: React.FC<ViewFileLocationDrawerProps> = ({
  isOpen,
  onClose,
  onBack,
  fileId,
  fileName,
}) => {
  const { t } = useTranslate(["campaigns"]);
  // Store mapping of location indices to original items for popup
  const locationItemsMapRef = useRef<Map<number, GeoImportLocation>>(new Map());

  // Fetch location list by file ID
  const {
    data: locationData,
    isLoading,
    isFetching,
  } = useGetGeoImportLocationsQuery(
    { geoImportId: fileId },
    {
      skip: !isOpen || !fileId,
    },
  );

  // Transform location items for table
  const tableData = useMemo(() => {
    if (!locationData?.data) return [];
    return locationData.data.map((item: GeoImportLocation, index: number) => ({
      ...item,
      srId: index,
    }));
  }, [locationData]);

  // Transform location items for Mapbox (minimal MapInventoryItem format)
  const mapLocationItems = useMemo((): MapInventoryItem[] => {
    if (!locationData?.data) return [];

    // Clear and rebuild the map
    locationItemsMapRef.current.clear();

    return locationData.data.map((item: GeoImportLocation, index: number) => {
      // Store original item for popup to use directly
      locationItemsMapRef.current.set(index, item);

      const lat = parseFloat(item.latitude) || 0;
      const lng = parseFloat(item.longitude) || 0;

      // Create minimal MapInventoryItem - only what MapBoxWrapper needs for markers
      return {
        id: `location-${index}`,
        detail: {
          id: `location-${index}`,
          name: item.locationName,
          externalId: `location-${index}`,
          displayName: item.locationName,
          referenceId: "",
          mediaOwnerId: "",
          mediaOwnerName: "",
          inventoryType: item.siteType,
          category: "",
          venueType: "",
          location: {
            address: item.locationName,
            country: "",
            state: "",
            city: "",
            zipCode: "",
            locationCoordinates: {
              coordinates: [{ latitude: lat, longitude: lng }],
              type: "Point",
            },
          },
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
            address: item.locationName,
            country: "",
            state: "",
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
  }, [locationData]);

  // Calculate map center from location items
  const mapCenter: [number, number] = useMemo(() => {
    if (mapLocationItems.length === 0) return [0, 0];

    const validItems = mapLocationItems.filter((item) => {
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
  }, [mapLocationItems]);

  // Popup component for map markers - uses original GeoImportLocation data
  const LocationMapPopupComponent = ({ item }: { item: MapInventoryItem }) => {
    // Extract index from item id (format: "location-{index}")
    const indexMatch = item.detail.id.match(/location-(\d+)/);
    const index = indexMatch ? parseInt(indexMatch[1], 10) : -1;

    // Get original item from map
    const originalItem = locationItemsMapRef.current.get(index);

    if (!originalItem) {
      return null;
    }

    return (
      <div className="flex-1 min-w-0 space-y-1">
        <div className="inline-flex justify-start items-center">
          <h3 className="text-xs font-semibold leading-4 truncate max-w-[250px]">
            {originalItem.locationName || "-"}
          </h3>
        </div>
        <p className="text-xs text-secondary leading-4">
          {t("viewFileLocationDrawer.lat")}: {originalItem.latitude},{" "}
          {t("viewFileLocationDrawer.lng")}: {originalItem.longitude}
        </p>
        {originalItem.radius && (
          <p className="text-xs text-secondary leading-4">
            {t("viewFileLocationDrawer.radius")}: {originalItem.radius}
          </p>
        )}
        <div className="flex flex-wrap gap-1.5">
          <Badge variant="outline" size="sm">
            {originalItem.siteType || "-"}
          </Badge>
        </div>
      </div>
    );
  };

  const columnDefs = useMemo<ColDef<LocationRow>[]>(
    () => [
      {
        colId: "srId",
        headerName: t("viewFileLocationDrawer.columns.srNo"),
        width: 80,
        sortable: false,
        valueGetter: (params: ValueGetterParams<LocationRow>) =>
          params.data ? params.data.srId + 1 : null,
        cellRenderer: (params: ICellRendererParams<LocationRow>) =>
          params.value != null ? (
            <span className="text-mw-neutral-700 dark:text-mw-neutral-300">
              {params.value}
            </span>
          ) : null,
      },
      {
        colId: "locationName",
        headerName: t("viewFileLocationDrawer.columns.locationName"),
        flex: 1,
        minWidth: 120,
        valueGetter: (params: ValueGetterParams<LocationRow>) =>
          params.data?.locationName ?? "-",
        cellRenderer: (params: ICellRendererParams<LocationRow>) => (
          <span className="text-mw-neutral-700 dark:text-mw-neutral-300">
            {params.value ?? "-"}
          </span>
        ),
      },
      {
        colId: "latitude",
        headerName: t("viewFileLocationDrawer.columns.latitude"),
        minWidth: 100,
        valueGetter: (params: ValueGetterParams<LocationRow>) =>
          params.data?.latitude ?? "-",
        cellRenderer: (params: ICellRendererParams<LocationRow>) => (
          <span className="text-mw-neutral-700 dark:text-mw-neutral-300">
            {params.value ?? "-"}
          </span>
        ),
      },
      {
        colId: "longitude",
        headerName: t("viewFileLocationDrawer.columns.longitude"),
        minWidth: 100,
        valueGetter: (params: ValueGetterParams<LocationRow>) =>
          params.data?.longitude ?? "-",
        cellRenderer: (params: ICellRendererParams<LocationRow>) => (
          <span className="text-mw-neutral-700 dark:text-mw-neutral-300">
            {params.value ?? "-"}
          </span>
        ),
      },
      {
        colId: "radius",
        headerName: t("viewFileLocationDrawer.columns.radius"),
        minWidth: 80,
        valueGetter: (params: ValueGetterParams<LocationRow>) =>
          params.data?.radius ?? "-",
        cellRenderer: (params: ICellRendererParams<LocationRow>) => (
          <span className="text-mw-neutral-700 dark:text-mw-neutral-300">
            {params.value ?? "-"}
          </span>
        ),
      },
      {
        colId: "siteType",
        headerName: t("viewFileLocationDrawer.columns.siteType"),
        minWidth: 100,
        valueGetter: (params: ValueGetterParams<LocationRow>) =>
          params.data?.siteType ?? "-",
        cellRenderer: (params: ICellRendererParams<LocationRow>) => (
          <span className="text-mw-neutral-700 dark:text-mw-neutral-300">
            {params.value ?? "-"}
          </span>
        ),
      },
    ],
    [t],
  );

  const handleBack = () => {
    if (onBack) {
      onBack();
    } else {
      onClose();
    }
  };

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
          {mapLocationItems.length > 0 && mapCenter[0] !== 0 && (
            <div className="shrink-0">
              <div className="h-[350px] w-full">
                <MapBoxWrapper
                  defaultCenter={mapCenter}
                  defaultZoom={12}
                  controlsConfig={mapConfig}
                  locationsList={mapLocationItems}
                  PopupComponent={LocationMapPopupComponent}
                />
              </div>
            </div>
          )}

          {/* Table Section - Scrollable */}
          <div className="flex-1 overflow-hidden flex flex-col">
            <div className="flex-1 rounded-lg">
              <AgGridTable<LocationRow>
                rowData={tableData}
                columnDefs={columnDefs}
                getRowId={(row) => String(row.srId)}
                loading={isLoading || isFetching}
                emptyMessage={t("viewFileLocationDrawer.emptyMessage")}
                className="min-h-[200px]"
                height="100%"
                domLayout="autoHeight"
              />
            </div>
          </div>
        </div>
      </ModalDrawer>
    </>
  );
};

export default ViewFileLocationDrawer;
