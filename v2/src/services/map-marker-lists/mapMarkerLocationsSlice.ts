import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import { MapGeometry, MapMarkerLocation } from "src/types/campaign.types";

// Combined list of geometries and locations for display
export type CombinedItem =
  | (MapGeometry & { itemType: "geometry" })
  | (MapMarkerLocation & { itemType: "location" });

// Map marker locations state
export interface MapMarkerLocationsState {
  geometries: MapGeometry[];
  locations: MapMarkerLocation[];
  selectedLocation: MapMarkerLocation | MapGeometry | null;
  isLoading: boolean;
  error: string | null;
}

const initialState: MapMarkerLocationsState = {
  geometries: [],
  locations: [],
  selectedLocation: null,
  isLoading: false,
  error: null,
};

// Map marker locations slice
export const mapMarkerLocationsSlice = createSlice({
  name: "mapMarkerLocations",
  initialState,
  reducers: {
    // Geometry actions
    setGeometries: (state, action: PayloadAction<MapGeometry[]>) => {
      state.geometries = action.payload;
    },
    addGeometry: (state, action: PayloadAction<MapGeometry>) => {
      state.geometries.push(action.payload);
    },
    updateGeometry: (state, action: PayloadAction<MapGeometry>) => {
      const index = state.geometries.findIndex(
        (geometry) => geometry.id === action.payload.id,
      );
      if (index !== -1) {
        state.geometries[index] = action.payload;
      }
    },
    removeGeometry: (state, action: PayloadAction<string>) => {
      state.geometries = state.geometries.filter(
        (geometry) => geometry.id !== action.payload,
      );
    },
    setGeometryIncluded: (
      state,
      action: PayloadAction<{ id: string; included: boolean }>,
    ) => {
      const geometry = state.geometries.find(
        (geo) => geo.id === action.payload.id,
      );
      if (geometry) {
        geometry.included = action.payload.included;
      }
    },
    // Location actions
    setLocations: (state, action: PayloadAction<MapMarkerLocation[]>) => {
      state.locations = action.payload;
    },
    addLocation: (state, action: PayloadAction<MapMarkerLocation>) => {
      state.locations.push(action.payload);
    },
    updateLocation: (state, action: PayloadAction<MapMarkerLocation>) => {
      const index = state.locations.findIndex(
        (location) => location.id === action.payload.id,
      );
      if (index !== -1) {
        state.locations[index] = action.payload;
      }
    },
    removeLocation: (state, action: PayloadAction<string>) => {
      state.locations = state.locations.filter(
        (location) => location.id !== action.payload,
      );
    },
    setLocationIncluded: (
      state,
      action: PayloadAction<{ id: string; included: boolean }>,
    ) => {
      const location = state.locations.find(
        (loc) => loc.id === action.payload.id,
      );
      if (location) {
        location.included = action.payload.included;
      }
    },
    setLocationEnabled: (
      state,
      action: PayloadAction<{ id: string; enabled: boolean }>,
    ) => {
      const location = state.locations.find(
        (loc) => loc.id === action.payload.id,
      );
      if (location) {
        location.included = action.payload.enabled;
      }
    },
    setAllLocationsEnabled: (state, action: PayloadAction<boolean>) => {
      state.locations.forEach((location) => {
        location.included = action.payload;
      });
    },
    setAllItemsIncluded: (state, action: PayloadAction<boolean>) => {
      state.locations.forEach((location) => {
        location.included = action.payload;
      });
      state.geometries.forEach((geometry) => {
        geometry.included = action.payload;
      });
    },
    updateLocationRadius: (
      state,
      action: PayloadAction<{ id: string; radius: number }>,
    ) => {
      const location = state.locations.find(
        (loc) => loc.id === action.payload.id,
      );
      if (location) {
        location.radius = action.payload.radius;
      }
    },
    updateLocationPOIMetadata: (
      state,
      action: PayloadAction<{
        id: string;
        poi?: string[];
        metadata?: Record<string, string>;
      }>,
    ) => {
      const location = state.locations.find(
        (loc) => loc.id === action.payload.id,
      );
      if (location) {
        if (action.payload.poi) {
          location.poi = action.payload.poi;
        }
        if (action.payload.metadata) {
          location.metadata = action.payload.metadata;
        }
      }
      const geometries = state.geometries.find(
        (geo) => geo.id === action.payload.id,
      );
      if (geometries) {
        if (action.payload.poi) {
          geometries.poi = action.payload.poi;
        }
        if (action.payload.metadata) {
          geometries.metadata = action.payload.metadata;
        }
      }
    },
    setSelectedLocation: (
      state,
      action: PayloadAction<MapMarkerLocation | null>,
    ) => {
      state.selectedLocation = action.payload;
    },

    // General actions
    setIsLoading: (state, action: PayloadAction<boolean>) => {
      state.isLoading = action.payload;
    },
    setError: (state, action: PayloadAction<string | null>) => {
      state.error = action.payload;
    },
    clearError: (state) => {
      state.error = null;
    },
    resetMapMarkerLocationsState: (state) => {
      state.geometries = [];
      state.locations = [];
      state.selectedLocation = null;
      state.isLoading = false;
      state.error = null;
    },
    deleteAllShapes: (state) => {
      state.geometries = [];
      state.locations = state.locations.filter((location) => !location.isShape);
      state.selectedLocation = null;
      state.isLoading = false;
      state.error = null;
    },
  },
});

export const {
  setGeometries,
  addGeometry,
  updateGeometry,
  removeGeometry,
  setGeometryIncluded,
  setLocations,
  addLocation,
  updateLocation,
  removeLocation,
  setLocationIncluded,
  setLocationEnabled,
  setAllLocationsEnabled,
  setAllItemsIncluded,
  updateLocationRadius,
  setSelectedLocation,
  setIsLoading,
  setError,
  clearError,
  resetMapMarkerLocationsState,
  updateLocationPOIMetadata,
  deleteAllShapes,
} = mapMarkerLocationsSlice.actions;

export default mapMarkerLocationsSlice.reducer;
