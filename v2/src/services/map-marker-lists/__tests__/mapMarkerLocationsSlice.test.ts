import { MapGeometry, MapMarkerLocation } from "src/types/campaign.types";
import { describe, it, expect } from "vitest";

import {
  mapMarkerLocationsSlice,
  MapMarkerLocationsState,
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
  updateLocationPOIMetadata,
  setSelectedLocation,
  setIsLoading,
  setError,
  clearError,
  resetMapMarkerLocationsState,
  deleteAllShapes,
} from "../mapMarkerLocationsSlice";

const reducer = mapMarkerLocationsSlice.reducer;

const initialState: MapMarkerLocationsState = {
  geometries: [],
  locations: [],
  selectedLocation: null,
  isLoading: false,
  error: null,
};

const makeGeometry = (overrides: Partial<MapGeometry> = {}): MapGeometry => ({
  id: "geo-1",
  type: "Polygon",
  name: "Test Polygon",
  coordinates: [
    [0, 0],
    [1, 0],
    [1, 1],
    [0, 0],
  ],
  included: true,
  isShape: true,
  ...overrides,
});

const makeLocation = (
  overrides: Partial<MapMarkerLocation> = {},
): MapMarkerLocation => ({
  id: "loc-1",
  lat: 1.234,
  lng: 103.456,
  name: "Test Location",
  address: "123 Test St",
  included: true,
  isShape: false,
  ...overrides,
});

describe("mapMarkerLocationsSlice", () => {
  describe("geometry reducers", () => {
    it("setGeometries replaces the geometries array", () => {
      const geo1 = makeGeometry({ id: "geo-1" });
      const geo2 = makeGeometry({ id: "geo-2" });
      const state = reducer(initialState, setGeometries([geo1, geo2]));
      expect(state.geometries).toHaveLength(2);
      expect(state.geometries[0].id).toBe("geo-1");
    });

    it("addGeometry appends a geometry", () => {
      const geo = makeGeometry({ id: "geo-new" });
      const state = reducer(initialState, addGeometry(geo));
      expect(state.geometries).toHaveLength(1);
      expect(state.geometries[0].id).toBe("geo-new");
    });

    it("updateGeometry updates matching geometry by id", () => {
      const start = reducer(initialState, setGeometries([makeGeometry()]));
      const updated = makeGeometry({ name: "Updated Name" });
      const state = reducer(start, updateGeometry(updated));
      expect(state.geometries[0].name).toBe("Updated Name");
    });

    it("updateGeometry is a no-op when id not found", () => {
      const start = reducer(initialState, setGeometries([makeGeometry()]));
      const state = reducer(
        start,
        updateGeometry(makeGeometry({ id: "nonexistent" })),
      );
      expect(state.geometries[0].name).toBe("Test Polygon");
    });

    it("removeGeometry filters out the geometry by id", () => {
      const start = reducer(
        initialState,
        setGeometries([
          makeGeometry({ id: "geo-a" }),
          makeGeometry({ id: "geo-b" }),
        ]),
      );
      const state = reducer(start, removeGeometry("geo-a"));
      expect(state.geometries).toHaveLength(1);
      expect(state.geometries[0].id).toBe("geo-b");
    });

    it("setGeometryIncluded updates included flag when id matches", () => {
      const start = reducer(
        initialState,
        setGeometries([makeGeometry({ included: true })]),
      );
      const state = reducer(
        start,
        setGeometryIncluded({ id: "geo-1", included: false }),
      );
      expect(state.geometries[0].included).toBe(false);
    });

    it("setGeometryIncluded is a no-op when id not found", () => {
      const start = reducer(
        initialState,
        setGeometries([makeGeometry({ included: true })]),
      );
      const state = reducer(
        start,
        setGeometryIncluded({ id: "nonexistent", included: false }),
      );
      expect(state.geometries[0].included).toBe(true);
    });
  });

  describe("location reducers", () => {
    it("setLocations replaces the locations array", () => {
      const loc1 = makeLocation({ id: "loc-1" });
      const loc2 = makeLocation({ id: "loc-2" });
      const state = reducer(initialState, setLocations([loc1, loc2]));
      expect(state.locations).toHaveLength(2);
    });

    it("addLocation appends a location", () => {
      const loc = makeLocation({ id: "loc-new" });
      const state = reducer(initialState, addLocation(loc));
      expect(state.locations).toHaveLength(1);
      expect(state.locations[0].id).toBe("loc-new");
    });

    it("updateLocation updates matching location by id", () => {
      const start = reducer(initialState, setLocations([makeLocation()]));
      const state = reducer(
        start,
        updateLocation(makeLocation({ name: "Updated" })),
      );
      expect(state.locations[0].name).toBe("Updated");
    });

    it("updateLocation is a no-op when id not found", () => {
      const start = reducer(initialState, setLocations([makeLocation()]));
      const state = reducer(
        start,
        updateLocation(makeLocation({ id: "other", name: "Other" })),
      );
      expect(state.locations[0].name).toBe("Test Location");
    });

    it("removeLocation filters out location by id", () => {
      const start = reducer(
        initialState,
        setLocations([
          makeLocation({ id: "loc-a" }),
          makeLocation({ id: "loc-b" }),
        ]),
      );
      const state = reducer(start, removeLocation("loc-a"));
      expect(state.locations).toHaveLength(1);
      expect(state.locations[0].id).toBe("loc-b");
    });

    it("setLocationIncluded updates included flag when id matches", () => {
      const start = reducer(
        initialState,
        setLocations([makeLocation({ included: true })]),
      );
      const state = reducer(
        start,
        setLocationIncluded({ id: "loc-1", included: false }),
      );
      expect(state.locations[0].included).toBe(false);
    });

    it("setLocationEnabled updates included flag when id matches", () => {
      const start = reducer(
        initialState,
        setLocations([makeLocation({ included: false })]),
      );
      const state = reducer(
        start,
        setLocationEnabled({ id: "loc-1", enabled: true }),
      );
      expect(state.locations[0].included).toBe(true);
    });

    it("setAllLocationsEnabled sets included on all locations", () => {
      const start = reducer(
        initialState,
        setLocations([
          makeLocation({ id: "l1", included: true }),
          makeLocation({ id: "l2", included: true }),
        ]),
      );
      const state = reducer(start, setAllLocationsEnabled(false));
      expect(state.locations.every((l) => !l.included)).toBe(true);
    });

    it("setAllItemsIncluded sets included on all locations and geometries", () => {
      const start = {
        ...initialState,
        locations: [makeLocation({ included: true })],
        geometries: [makeGeometry({ included: true })],
      };
      const state = reducer(start, setAllItemsIncluded(false));
      expect(state.locations[0].included).toBe(false);
      expect(state.geometries[0].included).toBe(false);
    });

    it("updateLocationRadius updates radius when id matches", () => {
      const start = reducer(
        initialState,
        setLocations([makeLocation({ radius: 100 })]),
      );
      const state = reducer(
        start,
        updateLocationRadius({ id: "loc-1", radius: 500 }),
      );
      expect(state.locations[0].radius).toBe(500);
    });

    it("updateLocationPOIMetadata updates poi and metadata on location", () => {
      const start = reducer(initialState, setLocations([makeLocation()]));
      const state = reducer(
        start,
        updateLocationPOIMetadata({
          id: "loc-1",
          poi: ["poi-1"],
          metadata: { key: "value" },
        }),
      );
      expect(state.locations[0].poi).toEqual(["poi-1"]);
      expect(state.locations[0].metadata).toEqual({ key: "value" });
    });

    it("updateLocationPOIMetadata updates poi on geometry when id matches", () => {
      const start = {
        ...initialState,
        geometries: [makeGeometry()],
      };
      const state = reducer(
        start,
        updateLocationPOIMetadata({ id: "geo-1", poi: ["poi-2"] }),
      );
      expect(state.geometries[0].poi).toEqual(["poi-2"]);
    });
  });

  describe("selected location", () => {
    it("setSelectedLocation stores a location", () => {
      const loc = makeLocation();
      const state = reducer(initialState, setSelectedLocation(loc));
      expect(state.selectedLocation).toEqual(loc);
    });

    it("setSelectedLocation accepts null", () => {
      const start = {
        ...initialState,
        selectedLocation: makeLocation(),
      };
      const state = reducer(start, setSelectedLocation(null));
      expect(state.selectedLocation).toBeNull();
    });
  });

  describe("general actions", () => {
    it("setIsLoading sets loading flag", () => {
      const state = reducer(initialState, setIsLoading(true));
      expect(state.isLoading).toBe(true);
    });

    it("setError sets error message", () => {
      const state = reducer(initialState, setError("Something failed"));
      expect(state.error).toBe("Something failed");
    });

    it("clearError sets error to null", () => {
      const start = { ...initialState, error: "err" };
      const state = reducer(start, clearError());
      expect(state.error).toBeNull();
    });

    it("resetMapMarkerLocationsState clears all state", () => {
      const dirty = {
        geometries: [makeGeometry()],
        locations: [makeLocation()],
        selectedLocation: makeLocation(),
        isLoading: true,
        error: "some error",
      };
      const state = reducer(dirty, resetMapMarkerLocationsState());
      expect(state.geometries).toHaveLength(0);
      expect(state.locations).toHaveLength(0);
      expect(state.selectedLocation).toBeNull();
      expect(state.isLoading).toBe(false);
      expect(state.error).toBeNull();
    });

    it("deleteAllShapes removes geometries and shape locations", () => {
      const dirty = {
        geometries: [makeGeometry()],
        locations: [
          makeLocation({ id: "shape-loc", isShape: true }),
          makeLocation({ id: "normal-loc", isShape: false }),
        ],
        selectedLocation: makeLocation(),
        isLoading: true,
        error: null,
      };
      const state = reducer(dirty, deleteAllShapes());
      expect(state.geometries).toHaveLength(0);
      expect(state.locations).toHaveLength(1);
      expect(state.locations[0].id).toBe("normal-loc");
      expect(state.selectedLocation).toBeNull();
      expect(state.isLoading).toBe(false);
    });
  });
});
