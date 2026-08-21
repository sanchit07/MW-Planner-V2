import { Button } from "@components/ui/Button";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@components/ui/Tabs";
import { CONFIG } from "@config/index";
import { COORDINATE_LIMITS } from "@constants/campaign.constants";
import { useAnnounce } from "@hooks/useAnnounce";
import {
  useGetGeoImportFilesQuery,
  useGetGeoImportLocationsQuery,
  useDeleteGeoImportFileMutation,
  useImportGeoCoordinatesMutation,
  useDownloadGeoImportFileMutation,
} from "@services/inventory/inventorySlice";
import { useTranslate } from "@tolgee/react";
import React, { useMemo, useState, useEffect } from "react";

import { InventoryCsvVerifyResult } from "../../../types/inventory.types";
import { ViewLogDrawer } from "../inventory/ViewLogDrawer";
import { DeleteFileModal } from "./components/DeleteFileModal";
import { ExistingFilesTab } from "./components/ExistingFilesTab";
import { TemplateDownloadSection } from "./components/TemplateDownloadSection";
import { UploadTab } from "./components/UploadTab";
import {
  LocationCsvFile,
  LocationCsvVerifyResult,
} from "./types/location-csv.types";
import { ViewFileLocationDrawer } from "./ViewFileLocationDrawer";

export interface LocationData {
  lat: number;
  lng: number;
  name: string;
  metaData?: Record<string, string>;
  address?: string;
  isValid: boolean;
  errors: string[];
  included: boolean;
}

export interface LocationCsvUploadDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  countryName?: string;
  onImport?: (locations: LocationCsvFile[]) => Promise<{
    success: boolean;
    errors?: string[];
  }>;
  onUseFile?: (locations: LocationData[]) => void;
}

export const LocationCsvUploadDrawer: React.FC<
  LocationCsvUploadDrawerProps
> = ({ isOpen, onClose, countryName = "", onImport: _onImport, onUseFile }) => {
  const { t: tCommon } = useTranslate(["common"]);
  const { t: tCampaigns } = useTranslate("campaigns");
  const { showError, showSuccess } = useAnnounce();

  // API mutations
  const [deleteGeoImportFile] = useDeleteGeoImportFileMutation();
  const [downloadGeoImportFile] = useDownloadGeoImportFileMutation();
  const [importGeoCoordinates] = useImportGeoCoordinatesMutation();

  const [activeTab, setActiveTab] = useState("upload");
  const [isViewLogDrawerOpen, setIsViewLogDrawerOpen] = useState(false);
  const [isViewFileLocationDrawerOpen, setIsViewFileLocationDrawerOpen] =
    useState(false);
  const [selectedFileId, setSelectedFileId] = useState<string>("");
  const [selectedFileName, setSelectedFileName] = useState<string>("");
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [fileToDelete, setFileToDelete] = useState<{
    id: string;
    fileName: string;
  } | null>(null);

  // Upload state
  const [uploadedFile, setUploadedFile] = useState<File | null>(null);
  const [uploadResponse, setUploadResponse] = useState<{
    totalRow: number;
    validLocation: number;
    invalidLocation: number;
    duplicateLocation: number;
    logs: LocationCsvVerifyResult[];
  } | null>(null);
  const [uploadSuccess, setUploadSuccess] = useState<{
    totalValidLocation: number;
  } | null>(null);
  const [uploadedFileGeoDetails, setUploadedFileGeoDetails] = useState<
    Array<{
      locationName: string;
      radius: string;
      latitude: string;
      longitude: string;
      siteType: string;
    }>
  >([]);
  const [uploadedFileAddressMap, setUploadedFileAddressMap] = useState<
    Map<number, string>
  >(new Map());

  // Pagination state
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [selectedFileIdForUse, setSelectedFileIdForUse] = useState<string>("");

  // Fetch existing files from API
  const {
    data: geoImportFilesData,
    isLoading: isLoadingGeoImportFiles,
    refetch: refetchGeoImportFiles,
  } = useGetGeoImportFilesQuery(
    {
      params: {
        countryName: countryName || "",
        page: currentPage - 1,
        size: pageSize,
        sortBy: "createdAt",
        sortDir: "desc",
      },
    },
    {
      skip: !countryName || !isOpen || activeTab !== "existing",
    },
  );

  // Map API response to LocationCsvFile format
  const existingFiles = useMemo<LocationCsvFile[]>(() => {
    if (!geoImportFilesData?.data?.content) return [];
    return geoImportFilesData.data.content.map((file) => ({
      id: file.id,
      fileName: file.fileName,
      locationCount: file.countOfCoordinates || 0,
      createdBy: file.createdBy,
      createdAt: file.createdAt,
      countryName: file.countryName,
      companyId: file.companyId,
      lastModifiedBy: file.lastModifiedBy,
      updatedAt: file.updatedAt,
    }));
  }, [geoImportFilesData]);

  // Reset all state when drawer closes
  useEffect(() => {
    if (!isOpen) {
      setActiveTab("upload");
      setUploadResponse(null);
      setUploadedFile(null);
      setUploadSuccess(null);
      setUploadedFileGeoDetails([]);
      setUploadedFileAddressMap(new Map());
      setCurrentPage(1);
      setPageSize(10);
      setIsDeleteModalOpen(false);
      setFileToDelete(null);
      setSelectedFileIdForUse("");
      setIsViewFileLocationDrawerOpen(false);
      setIsViewLogDrawerOpen(false);
      setSelectedFileId("");
      setSelectedFileName("");
    }
  }, [isOpen]);

  const handleClose = () => {
    setActiveTab("upload");
    setUploadResponse(null);
    setUploadedFile(null);
    setUploadSuccess(null);
    setUploadedFileGeoDetails([]);
    setUploadedFileAddressMap(new Map());
    setCurrentPage(1);
    setPageSize(10);
    setIsDeleteModalOpen(false);
    setFileToDelete(null);
    setSelectedFileIdForUse("");
    setIsViewFileLocationDrawerOpen(false);
    setSelectedFileId("");
    setSelectedFileName("");
    onClose();
  };

  const handleOpenViewLog = () => {
    setIsViewLogDrawerOpen(true);
  };

  const handleCloseViewLog = () => {
    setIsViewLogDrawerOpen(false);
  };

  const handleBackFromViewLog = () => {
    setIsViewLogDrawerOpen(false);
  };

  // Helper function to validate if a string is a complete valid number
  // This is needed because parseFloat("125v.126117") returns 125, which would pass range validation
  // but the string itself is invalid and will fail when sent to Mapbox API
  // Using a safe regex pattern that avoids ReDoS vulnerabilities
  const isValidNumber = (str: string): boolean => {
    if (!str || str.trim() === "") return false;
    const trimmed = str.trim();
    // Use a simple, safe regex pattern without nested quantifiers to avoid ReDoS
    // Pattern: optional minus, digits with optional decimal part, optional scientific notation
    // This pattern is safe because it doesn't have catastrophic backtracking scenarios
    const numberRegex = /^-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?$/;
    if (!numberRegex.test(trimmed)) return false;
    // Also verify it can be parsed and is finite
    const parsed = parseFloat(trimmed);
    return !isNaN(parsed) && isFinite(parsed);
  };

  // Batch reverse geocode multiple lat/long pairs to get location names using Mapbox API
  const batchReverseGeocode = async (
    coordinates: Array<{ latitude: number; longitude: number; index: number }>,
  ): Promise<{
    locationNameMap: Map<number, string>;
    addressMap: Map<number, string>;
  }> => {
    const mapboxAccessToken = CONFIG.MAPBOX_ACCESS_TOKEN;
    const locationNameMap = new Map<number, string>();
    const addressMap = new Map<number, string>();

    if (coordinates.length === 0) {
      return { locationNameMap, addressMap };
    }

    try {
      // Build batch query string: {lon},{lat};{lon},{lat};...
      const batchQuery = coordinates
        .map((coord) => `${coord.longitude},${coord.latitude}`)
        .join(";");

      const response = await fetch(
        `https://api.mapbox.com/geocoding/v5/mapbox.places-permanent/${batchQuery}.json?access_token=${mapboxAccessToken}&limit=1`,
      );

      if (!response.ok) {
        console.error("Batch reverse geocoding failed:", response.statusText);
        return { locationNameMap, addressMap };
      }

      const data = await response.json();

      // Mapbox batch API returns an array of results, one for each coordinate
      if (Array.isArray(data)) {
        data.forEach((result, idx) => {
          const coord = coordinates[idx];
          if (coord && result.features && result.features.length > 0) {
            const feature = result.features[0];

            // Extract address from place_name
            const address = feature.place_name || null;
            if (address) {
              addressMap.set(coord.index, address);
            }

            // Extract locationName from context array - find object where id includes "place"
            let locationName: string | null = null;
            if (feature.context && Array.isArray(feature.context)) {
              for (const contextItem of feature.context) {
                if (contextItem.id && contextItem.id.includes("place")) {
                  locationName = contextItem.text || null;
                  break;
                }
              }
            }

            // Fallback to text if no place found in context
            if (!locationName) {
              locationName = feature.text || null;
            }

            if (locationName) {
              locationNameMap.set(coord.index, locationName);
            }
          }
        });
      } else {
        // Handle single result (non-array response)
        if (data.features && data.features.length > 0) {
          const feature = data.features[0];
          const coord = coordinates[0];

          // Extract address from place_name
          const address = feature.place_name || null;
          if (address) {
            addressMap.set(coord.index, address);
          }

          // Extract locationName from context array - find object where id includes "place"
          let locationName: string | null = null;
          if (feature.context && Array.isArray(feature.context)) {
            for (const contextItem of feature.context) {
              if (contextItem.id && contextItem.id.includes("place")) {
                locationName = contextItem.text || null;
                break;
              }
            }
          }

          // Fallback to text if no place found in context
          if (!locationName) {
            locationName = feature.text || null;
          }

          if (locationName) {
            locationNameMap.set(coord.index, locationName);
          }
        }
      }
      return { locationNameMap, addressMap };
    } catch (error) {
      console.error("Batch reverse geocoding error:", error);
      return { locationNameMap, addressMap };
    }
  };

  // Parse and validate CSV file
  const parseAndValidateCSV = async (
    file: File,
  ): Promise<{
    totalRow: number;
    validLocation: number;
    invalidLocation: number;
    duplicateLocation: number;
    logs: LocationCsvVerifyResult[];
    geoDetails: Array<{
      locationName: string;
      radius: string;
      latitude: string;
      longitude: string;
      siteType: string;
    }>;
    addressMap: Map<number, string>;
  }> => {
    const text = await file.text();
    const lines = text.split("\n").filter((line) => line.trim());

    if (lines.length < 2) {
      return {
        totalRow: 0,
        validLocation: 0,
        invalidLocation: 0,
        duplicateLocation: 0,
        logs: [
          {
            row: 1,
            message: "CSV file must contain header and at least one data row",
            type: "INVALID",
          },
        ],
        geoDetails: [],
        addressMap: new Map<number, string>(),
      };
    }

    // Parse header
    const headerLine = lines[0].split(",").map((h) => h.trim());
    const locationIndex = headerLine.findIndex(
      (h) => h.toLowerCase() === "location",
    );
    const latitudeIndex = headerLine.findIndex(
      (h) => h.toLowerCase() === "latitude",
    );
    const longitudeIndex = headerLine.findIndex(
      (h) => h.toLowerCase() === "longitude",
    );
    const radiusIndex = headerLine.findIndex(
      (h) => h.toLowerCase() === "radius",
    );
    const siteTypeIndex = headerLine.findIndex(
      (h) => h.toLowerCase() === "site type" || h.toLowerCase() === "sitetype",
    );

    // Validate required columns (Location and Radius are optional, but Latitude, Longitude, and Site Type are required)
    const missingColumns: string[] = [];
    if (latitudeIndex === -1) missingColumns.push("Latitude");
    if (longitudeIndex === -1) missingColumns.push("Longitude");
    if (siteTypeIndex === -1) missingColumns.push("Site Type");

    if (missingColumns.length > 0) {
      return {
        totalRow: 0,
        validLocation: 0,
        invalidLocation: 0,
        duplicateLocation: 0,
        logs: [
          {
            row: 1,
            message: `Missing required columns: ${missingColumns.join(", ")}`,
            type: "INVALID",
          },
        ],
        geoDetails: [],
        addressMap: new Map<number, string>(),
      };
    }

    // First pass: Validate all rows and collect coordinates that need location names
    const logs: LocationCsvVerifyResult[] = [];
    const locationKeys: Set<string> = new Set();
    const rowsData: Array<{
      row: number;
      location: string;
      latitude: number;
      longitude: number;
      latitudeStr: string;
      longitudeStr: string;
      radiusStr: string;
      siteType: string;
      isValid: boolean;
      errors: string[];
    }> = [];
    let validCount = 0;
    let invalidCount = 0;
    let duplicateCount = 0;

    // First pass: Validate all rows
    for (let i = 1; i < lines.length; i++) {
      const row = i + 1; // Row number (1-indexed, including header)
      const values = lines[i].split(",").map((v) => v.trim());
      const errors: string[] = [];

      // Get values
      const location = locationIndex !== -1 ? values[locationIndex] || "" : "";
      const latitudeStr = values[latitudeIndex] || "";
      const longitudeStr = values[longitudeIndex] || "";
      const radiusStr = radiusIndex !== -1 ? values[radiusIndex] || "" : "";
      const siteType = values[siteTypeIndex] || "";

      // Validate Latitude
      let latitude: number | null = null;
      if (!latitudeStr || !isValidNumber(latitudeStr)) {
        errors.push("Latitude must be a valid number");
      } else {
        latitude = parseFloat(latitudeStr);
        if (
          latitude < COORDINATE_LIMITS.MIN_LATITUDE ||
          latitude > COORDINATE_LIMITS.MAX_LATITUDE
        ) {
          errors.push(
            `Latitude must be between ${COORDINATE_LIMITS.MIN_LATITUDE} and ${COORDINATE_LIMITS.MAX_LATITUDE}`,
          );
        }
      }

      // Validate Longitude
      let longitude: number | null = null;
      if (!longitudeStr || !isValidNumber(longitudeStr)) {
        errors.push("Longitude must be a valid number");
      } else {
        longitude = parseFloat(longitudeStr);
        if (
          longitude < COORDINATE_LIMITS.MIN_LONGITUDE ||
          longitude > COORDINATE_LIMITS.MAX_LONGITUDE
        ) {
          errors.push(
            `Longitude must be between ${COORDINATE_LIMITS.MIN_LONGITUDE} and ${COORDINATE_LIMITS.MAX_LONGITUDE}`,
          );
        }
      }

      // Validate Radius (optional field)
      if (radiusStr) {
        if (!isValidNumber(radiusStr)) {
          errors.push("Radius must be a valid number");
        } else {
          const radius = parseFloat(radiusStr);
          if (radius <= 0) {
            errors.push("Radius must be greater than 0");
          }
        }
      }

      // Validate Site Type
      if (!siteType) {
        errors.push("Site Type is required");
      }

      // If there are validation errors, mark as invalid
      if (errors.length > 0) {
        invalidCount++;
        logs.push({
          row,
          message: errors.join("; "),
          type: "INVALID",
        });
        continue;
      }

      // At this point, latitude and longitude are guaranteed to be valid numbers (not null)
      // TypeScript assertion: we know they're numbers because validation passed
      const validLatitude = latitude as number;
      const validLongitude = longitude as number;

      // Check for duplicates
      const locationKey = `${validLatitude}_${validLongitude}`;
      const isDuplicate = locationKeys.has(locationKey);

      if (isDuplicate) {
        duplicateCount++;
        logs.push({
          row,
          message: `Duplicate location at (${validLatitude}, ${validLongitude})`,
          type: "DUPLICATE",
        });
        continue;
      }

      locationKeys.add(locationKey);

      // Store row data for processing
      rowsData.push({
        row,
        location,
        latitude: validLatitude,
        longitude: validLongitude,
        latitudeStr,
        longitudeStr,
        radiusStr,
        siteType,
        isValid: true,
        errors: [],
      });

      validCount++;
    }

    // Second pass: Batch fetch location names for rows that need them
    const coordinatesToFetch: Array<{
      latitude: number;
      longitude: number;
      index: number;
    }> = [];
    rowsData.forEach((rowData, index) => {
      //fetch address for all the valid coordinates
      coordinatesToFetch.push({
        latitude: rowData.latitude,
        longitude: rowData.longitude,
        index,
      });
    });

    // Batch fetch location names and addresses
    let locationNameMap = new Map<number, string>();
    let addressMap = new Map<number, string>();
    if (coordinatesToFetch.length > 0) {
      try {
        const result = await batchReverseGeocode(coordinatesToFetch);
        locationNameMap = result.locationNameMap;
        addressMap = result.addressMap;
      } catch (error) {
        console.error("Error in batch reverse geocoding:", error);
      }
    }

    // Third pass: Build geoDetails array
    const geoDetails: Array<{
      locationName: string;
      radius: string;
      latitude: string;
      longitude: string;
      siteType: string;
    }> = [];

    rowsData.forEach((rowData, index) => {
      let locationName = rowData.location;

      // If location name is missing, use fetched name or coordinates as fallback
      if (!locationName) {
        const fetchedName = locationNameMap.get(index);
        if (fetchedName) {
          locationName = fetchedName;
        } else {
          // If reverse geocoding fails, use coordinates as fallbacks
          locationName = "";
          logs.push({
            row: rowData.row,
            message: "Location name not found, using coordinates",
            type: "INVALID",
          });
          invalidCount++;
          validCount--;
        }
      }

      // Add to geoDetails for backend
      geoDetails.push({
        locationName,
        radius: rowData.radiusStr || "",
        latitude: rowData.latitudeStr,
        longitude: rowData.longitudeStr,
        siteType: rowData.siteType,
      });
    });

    return {
      totalRow: lines.length - 1, // Exclude header
      validLocation: validCount,
      invalidLocation: invalidCount,
      duplicateLocation: duplicateCount,
      logs,
      geoDetails,
      addressMap,
    };
  };

  const handleFileChange = async (file: File | null) => {
    setUploadedFile(file);

    // Reset uploadResponse and related state when file is removed
    if (!file) {
      setUploadResponse(null);
      setUploadSuccess(null);
      setUploadedFileGeoDetails([]);
      setUploadedFileAddressMap(new Map());
      return;
    }

    // File exists, process it
    setUploadSuccess(null);
    try {
      // Parse and validate CSV (this will also fetch location names)
      const result = await parseAndValidateCSV(file);
      setUploadResponse({
        totalRow: result.totalRow,
        validLocation: result.validLocation,
        invalidLocation: result.invalidLocation,
        duplicateLocation: result.duplicateLocation,
        logs: result.logs,
      });
      // Store geoDetails and addressMap for import
      setUploadedFileGeoDetails(result.geoDetails);
      setUploadedFileAddressMap(result.addressMap);
    } catch (error) {
      console.error("Error parsing CSV:", error);
      setUploadResponse({
        totalRow: 0,
        validLocation: 0,
        invalidLocation: 0,
        duplicateLocation: 0,
        logs: [
          {
            row: 1,
            message: "Error parsing CSV file. Please check the file format.",
            type: "INVALID",
          },
        ],
      });
      setUploadedFileGeoDetails([]);
      setUploadedFileAddressMap(new Map());
    }
  };

  const handleImport = async () => {
    if (
      !uploadedFile ||
      !uploadResponse ||
      uploadResponse.validLocation === 0 ||
      !countryName ||
      uploadedFileGeoDetails.length === 0
    ) {
      if (!countryName) {
        showError(tCampaigns("locationCsvUpload.errors.countryNameRequired"));
      }
      return;
    }

    try {
      // Prepare request body in new format
      const requestBody = {
        fileName: uploadedFile.name,
        countryName,
        geoDetails: uploadedFileGeoDetails,
      };

      const result = await importGeoCoordinates({
        requestBody,
      }).unwrap();

      if (result.success) {
        setUploadSuccess({
          totalValidLocation: uploadResponse.validLocation,
        });
        showSuccess(tCampaigns("locationCsvUpload.success.imported"));

        // Map uploadedFileGeoDetails to LocationData format and pass to onUseFile
        if (onUseFile) {
          const locations: LocationData[] = uploadedFileGeoDetails.map(
            (loc, index) => {
              const latitude = parseFloat(loc.latitude);
              const longitude = parseFloat(loc.longitude);
              const address = uploadedFileAddressMap.get(index);

              return {
                lat: latitude,
                lng: longitude,
                name: loc.locationName,
                metaData: { type: loc.siteType || "" },
                address: address || undefined,
                isValid: true,
                errors: [],
                included: true,
              };
            },
          );

          // Call the callback to add locations to map (this will trigger autosave)
          onUseFile(locations);
        }
        onClose();
      }
    } catch (error) {
      console.error("Error importing CSV:", error);
      showError(tCampaigns("locationCsvUpload.errors.failedToImport"));
    }
  };

  // Fetch locations from selected file (only for use file functionality)
  const { refetch: refetchLocations } = useGetGeoImportLocationsQuery(
    {
      geoImportId: selectedFileIdForUse,
    },
    {
      skip: !selectedFileIdForUse || !isOpen,
    },
  );

  const handleUseFile = async () => {
    if (!selectedFileIdForUse) {
      showError(tCampaigns("locationCsvUpload.errors.selectFile"));
      return;
    }

    if (!onUseFile) {
      showError(
        tCampaigns("locationCsvUpload.errors.importCallbackNotAvailable"),
      );
      return;
    }

    try {
      // Fetch locations using refetch
      const locationsResult = await refetchLocations();

      if (
        !locationsResult.data?.data ||
        locationsResult.data.data.length === 0
      ) {
        showError(tCampaigns("locationCsvUpload.errors.noLocationsFound"));
        return;
      }

      // Prepare coordinates for batch reverse geocoding
      const coordinatesToFetch: Array<{
        latitude: number;
        longitude: number;
        index: number;
      }> = [];

      locationsResult.data.data.forEach((loc, index) => {
        const latitude = parseFloat(loc.latitude);
        const longitude = parseFloat(loc.longitude);
        if (!isNaN(latitude) && !isNaN(longitude)) {
          coordinatesToFetch.push({
            latitude,
            longitude,
            index,
          });
        }
      });

      // Fetch addresses using batch reverse geocoding
      let addressMap = new Map<number, string>();
      if (coordinatesToFetch.length > 0) {
        try {
          const geocodeResult = await batchReverseGeocode(coordinatesToFetch);
          addressMap = geocodeResult.addressMap;
        } catch (error) {
          console.error("Error fetching addresses:", error);
          // Continue even if geocoding fails
        }
      }

      // Map API response to LocationData format with addresses
      const locations: LocationData[] = [];
      for (let i = 0; i < locationsResult.data.data.length; i++) {
        const loc = locationsResult.data.data[i];
        const latitude = parseFloat(loc.latitude);
        const longitude = parseFloat(loc.longitude);

        if (isNaN(latitude) || isNaN(longitude)) {
          continue; // Skip invalid coordinates
        }

        const address = addressMap.get(i);
        locations.push({
          lat: latitude,
          lng: longitude,
          name: loc.locationName,
          metaData: { type: loc.siteType || "" },
          address: address || undefined,
          isValid: true,
          errors: [],
          included: true,
        });
      }

      if (locations.length === 0) {
        showError(tCampaigns("locationCsvUpload.errors.noValidLocations"));
        return;
      }
      // Call the callback to add locations to map (this will trigger autosave)
      onUseFile(locations);
      showSuccess(`${locations.length} locations loaded successfully`);
      handleClose();
    } catch (error) {
      console.error("Error reading file:", error);
      showError(tCampaigns("locationCsvUpload.errors.failedToReadFile"));
    }
  };

  const handleDeleteFile = (fileId: string) => {
    const file = existingFiles.find((f) => f.id === fileId);
    if (file) {
      setFileToDelete({ id: fileId, fileName: file.fileName });
      setIsDeleteModalOpen(true);
    }
  };

  const handleConfirmDelete = async () => {
    if (!fileToDelete) return;

    try {
      const result = await deleteGeoImportFile({
        geoImportId: fileToDelete.id,
      }).unwrap();

      if (result.success) {
        showSuccess(tCampaigns("locationCsvUpload.success.deleted"));
        refetchGeoImportFiles();
      }
    } catch (error) {
      console.error("Error deleting file:", error);
      showError(tCampaigns("locationCsvUpload.errors.failedToDelete"));
    }

    setIsDeleteModalOpen(false);
    setFileToDelete(null);
  };

  const handleDownloadFile = async (fileId: string) => {
    try {
      // Find the file to get its name
      const file = existingFiles.find((f) => f.id === fileId);
      const fileName = file?.fileName || `geo_import_${fileId}.csv`;

      // Download the file
      const blob = await downloadGeoImportFile({
        geoImportId: fileId,
      }).unwrap();

      // Create download link and trigger download
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = fileName;
      link.style.display = "none";
      document.body.appendChild(link);
      link.click();

      // Cleanup synchronously — click() starts the download immediately, so no
      // delay is needed. A deferred setTimeout could fire after unmount (e.g.
      // after a test tears down jsdom) and throw on the missing `document`.
      link.remove();
      window.URL.revokeObjectURL(url);

      showSuccess(tCampaigns("locationCsvUpload.success.downloaded"));
    } catch (error) {
      console.error("Error downloading file:", error);
      showError(tCampaigns("locationCsvUpload.errors.failedToDownload"));
    }
  };

  const handleCancelDelete = () => {
    setIsDeleteModalOpen(false);
    setFileToDelete(null);
  };

  // Handle view file
  const handleViewFile = (fileId: string) => {
    const file = existingFiles.find((f) => f.id === fileId);
    if (file) {
      setSelectedFileId(fileId);
      setSelectedFileName(file.fileName);
      setIsViewFileLocationDrawerOpen(true);
    }
  };

  const handleCloseViewFileLocation = () => {
    setIsViewFileLocationDrawerOpen(false);
    setSelectedFileId("");
    setSelectedFileName("");
  };

  const handleBackFromViewFileLocation = () => {
    setIsViewFileLocationDrawerOpen(false);
    setSelectedFileId("");
    setSelectedFileName("");
  };

  const handleDownloadTemplate = () => {
    const csvContent = `Location,Latitude,Longitude,Radius,Site Type`;

    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const link = document.createElement("a");
    const url = URL.createObjectURL(blob);
    link.setAttribute("href", url);
    link.setAttribute("download", "location_import_template.csv");
    link.style.visibility = "hidden";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <>
      <ModalDrawer
        isOpen={isOpen && !isViewLogDrawerOpen && !isViewFileLocationDrawerOpen}
        onClose={handleClose}
        title={tCampaigns("targeting.geofencing.import_locations")}
        size="custom"
        customWidth="60vw"
        footer={
          <div className="flex gap-3 justify-end">
            <Button variant="outline" onClick={handleClose}>
              {tCommon("buttons.cancel")}
            </Button>
            {activeTab === "upload" && (
              <Button
                variant="primary"
                disabled={
                  !uploadedFile ||
                  !uploadResponse ||
                  uploadResponse.validLocation === 0
                }
                onClick={handleImport}
              >
                {tCampaigns("targeting.geofencing.import_button")}
              </Button>
            )}
            {activeTab === "existing" && (
              <Button
                variant="primary"
                onClick={handleUseFile}
                disabled={!selectedFileIdForUse}
              >
                Use File
              </Button>
            )}
          </div>
        }
      >
        <TemplateDownloadSection onDownloadTemplate={handleDownloadTemplate} />

        <Tabs value={activeTab} onValueChange={setActiveTab}>
          <TabsList>
            <TabsTrigger value="existing">
              {tCampaigns("inventoryCsvUpload.tabs.existingFiles")}
            </TabsTrigger>
            <TabsTrigger value="upload">
              {tCampaigns("inventoryCsvUpload.tabs.uploadNew")}
            </TabsTrigger>
          </TabsList>

          <TabsContent value="existing">
            <ExistingFilesTab
              files={existingFiles}
              selectedFileId={selectedFileIdForUse}
              onSelectFile={setSelectedFileIdForUse}
              onDeleteFile={handleDeleteFile}
              onDownloadFile={handleDownloadFile}
              onViewFile={handleViewFile}
              currentPage={currentPage}
              pageSize={pageSize}
              onPageChange={setCurrentPage}
              onPageSizeChange={setPageSize}
              isLoading={isLoadingGeoImportFiles}
              setActiveTab={setActiveTab}
              totalItems={geoImportFilesData?.data?.totalElements || 0}
            />
          </TabsContent>

          <TabsContent value="upload">
            <UploadTab
              uploadedFile={uploadedFile}
              onFileChange={handleFileChange}
              uploadResponse={uploadResponse}
              uploadSuccess={uploadSuccess}
              onViewLog={handleOpenViewLog}
            />
          </TabsContent>
        </Tabs>
      </ModalDrawer>

      {/* View Log Drawer - Reusing from inventory */}
      <ViewLogDrawer
        isOpen={isViewLogDrawerOpen}
        onClose={handleCloseViewLog}
        onBack={handleBackFromViewLog}
        logs={
          (uploadResponse?.logs.map((log) => ({
            id: "",
            row: log.row,
            message: log.message,
            type: log.type,
          })) as InventoryCsvVerifyResult[]) || []
        }
      />

      {/* View File Location Drawer */}
      {selectedFileId && (
        <ViewFileLocationDrawer
          isOpen={isViewFileLocationDrawerOpen}
          onClose={handleCloseViewFileLocation}
          onBack={handleBackFromViewFileLocation}
          fileId={selectedFileId}
          fileName={selectedFileName}
        />
      )}

      {/* Delete File Modal */}
      <DeleteFileModal
        isOpen={isDeleteModalOpen}
        fileName={fileToDelete?.fileName || ""}
        onConfirm={handleConfirmDelete}
        onCancel={handleCancelDelete}
      />
    </>
  );
};

export default LocationCsvUploadDrawer;
