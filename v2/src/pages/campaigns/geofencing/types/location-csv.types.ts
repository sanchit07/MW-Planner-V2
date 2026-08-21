// Location CSV Types

export interface LocationCsvVerifyResult {
  row: number;
  message: string;
  type: "VALID" | "INVALID" | "DUPLICATE";
}

export interface LocationCsvFile {
  id: string;
  fileName: string;
  locationCount: number;
  createdBy: string;
  createdAt: string;
  countryName?: string;
  companyId?: string;
  lastModifiedBy?: string;
  updatedAt?: string;
}

export interface LocationCsvFileListParams {
  countryName: string;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: "asc" | "desc";
}

export interface GeoImportLocation {
  locationName: string;
  radius: string;
  latitude: string;
  longitude: string;
  siteType: string;
}

export interface GeoImportLocationResponse {
  success: boolean;
  data: GeoImportLocation[];
}

export interface GeoImportRequest {
  fileName: string;
  countryName: string;
  geoDetails: Array<{
    locationName: string;
    radius: string;
    latitude: string;
    longitude: string;
    siteType: string;
  }>;
}

// API Response type (matches actual API response structure)
export interface GeoImportFileApiResponse {
  id: string;
  fileName: string;
  countryName: string;
  companyId: string;
  createdBy: string;
  lastModifiedBy: string;
  createdAt: string;
  updatedAt: string;
  countOfCoordinates: number;
}

export interface LocationCsvFileListResponse {
  totalElements: number;
  totalPages: number;
  size: number;
  content: GeoImportFileApiResponse[];
  number: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  empty: boolean;
}
