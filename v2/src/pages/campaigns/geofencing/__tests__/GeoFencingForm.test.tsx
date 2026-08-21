import { configureStore } from "@reduxjs/toolkit";
import type { SerializedError } from "@reduxjs/toolkit";
import type { FetchBaseQueryError } from "@reduxjs/toolkit/query";
import { TargetingFormData } from "@schemas/campaigns/targeting.schema";
import campaignSlice from "@services/campaign/campaignSlice";
import { useLazyGetCountryByNameQuery } from "@services/campaign/campaignSlice";
import mapMarkerLocationsReducer from "@services/map-marker-lists/mapMarkerLocationsSlice";
import { MapMarkerLocationsState } from "@services/map-marker-lists/mapMarkerLocationsSlice";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { Control } from "react-hook-form";
import { Provider } from "react-redux";
import type { RootState } from "src/store";
import {
  MapMarkerLocation,
  MapGeometry,
  CountryTax,
} from "src/types/campaign.types";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

import GeoFencingForm from "../GeoFencingForm";

// The mobility heatmap hook uses an RTK Query hook (campaignApi middleware
// isn't part of this test store); stub the module.
vi.mock("@components/map/MobilityHeatmap", () => ({
  toCountrySlug: (name?: string) =>
    name ? name.toLowerCase().replace(/\s+/g, "-") : undefined,
  useMobilityHeatmapLayer: () => ({
    isLoading: false,
    isError: false,
    pointCount: 0,
    isEmpty: false,
  }),
  MobilityHeatmapControl: () => null,
}));

// Mock RTK Query hook
vi.mock("@services/campaign/campaignSlice", async (importOriginal) => {
  const mod =
    await importOriginal<typeof import("@services/campaign/campaignSlice")>();
  return {
    ...mod,
    useLazyGetCountryByNameQuery: vi.fn(),
  };
});

// Mock useAnnounce
const mockShowWarning = vi.fn();
vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({
    showWarning: mockShowWarning,
  }),
}));

// Mock useTranslate (returns key as-is for t and tCommon)
vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string) => key,
    tCommon: (key: string) => key,
  }),
}));

// Captures every render's map props so tests can assert identity stability
const capturedMapProps = vi.hoisted(() => [] as Array<Record<string, unknown>>);

// Mock MapBoxWrapper
vi.mock("@components/ui/Mapbox", () => ({
  default: (props: Record<string, unknown>) => {
    capturedMapProps.push(props);
    return <MapboxMockView {...(props as MapboxMockViewProps)} />;
  },
}));

type MapboxMockViewProps = {
  defaultCenter?: [number, number];
  defaultZoom?: number;
  onLocationSelected?: (location: MapMarkerLocation) => void;
  onShapeDrawn?: (geometry: MapGeometry) => void;
  onCircleRadiusUpdate?: (id: string, radius: number) => void;
  locationsList?: MapMarkerLocation[];
  selectedItemId?: string | null;
  selectedCountry?: string;
  updateLocationPOIMetadata?: (
    id: string,
    metadata: Record<string, string>,
    poi: string[],
  ) => void;
  deleteAllShapes?: () => void;
  popupExtraProps?: Record<string, unknown>;
};

const MapboxMockView = ({
  defaultCenter,
  defaultZoom,
  onLocationSelected,
  onShapeDrawn,
  onCircleRadiusUpdate,
  locationsList,
  selectedItemId,
  selectedCountry,
  updateLocationPOIMetadata,
  deleteAllShapes,
  popupExtraProps,
}: MapboxMockViewProps) => (
  <div
    data-testid="mapbox-wrapper"
    data-popup-extra-props={JSON.stringify(popupExtraProps ?? {})}
  >
    <div data-testid="map-center">
      {defaultCenter?.[0]}, {defaultCenter?.[1]}
    </div>
    <div data-testid="map-zoom">{defaultZoom}</div>
    <div data-testid="map-selected-country">{selectedCountry ?? "none"}</div>
    <div data-testid="locations-count">{locationsList?.length || 0}</div>
    <div data-testid="selected-item">{selectedItemId || "none"}</div>
    <button
      data-testid="add-location"
      onClick={() =>
        onLocationSelected?.({
          id: "new-location",
          lat: 40.7128,
          lng: -74.006,
          name: "New Location",
          address: "New Address",
          included: true,
          isShape: false,
        })
      }
    >
      Add Location
    </button>
    <button
      data-testid="add-shape"
      onClick={() =>
        onShapeDrawn?.({
          id: "new-geometry",
          type: "Polygon",
          coordinates: [
            [-74.006, 40.7128],
            [-74.005, 40.7129],
          ],
          name: "New Polygon",
          included: true,
          isShape: true,
        })
      }
    >
      Add Shape
    </button>
    <button
      data-testid="update-radius"
      onClick={() => onCircleRadiusUpdate?.("location-1", 2000)}
    >
      Update Radius
    </button>
    <button
      data-testid="update-poi"
      onClick={() =>
        updateLocationPOIMetadata?.("location-1", { poi1: "data" }, ["poi1"])
      }
    >
      Update POI
    </button>
    <button data-testid="delete-all-shapes" onClick={deleteAllShapes}>
      Delete All
    </button>
  </div>
);

// Mock LocationCsvUploadDrawer
vi.mock("../LocationCsvUploadDrawer", () => ({
  LocationCsvUploadDrawer: ({
    isOpen,
    onClose,
    onUseFile,
  }: {
    isOpen: boolean;
    onClose: () => void;
    onUseFile?: (locations: unknown[]) => void;
    countryName: string;
  }) =>
    isOpen ? (
      <div data-testid="csv-upload-drawer">
        <button data-testid="close-csv-drawer" onClick={onClose}>
          Close
        </button>
        <button
          data-testid="import-locations"
          onClick={() =>
            onUseFile?.([
              {
                lat: 40.7128,
                lng: -74.006,
                name: "Imported Location",
                address: "Imported Address",
                included: true,
                metaData: { type: "Billboard" },
              },
            ])
          }
        >
          Import
        </button>
      </div>
    ) : null,
}));

// Mock Modal
vi.mock("@components/ui/Modal", () => ({
  default: ({
    isOpen,
    onClose,
    onPrimaryAction,
    onSecondaryAction,
    title,
    children,
  }: {
    isOpen: boolean;
    onClose: () => void;
    onPrimaryAction: () => void;
    onSecondaryAction: () => void;
    title: string;
    children: React.ReactNode;
  }) =>
    isOpen ? (
      <div data-testid="modal">
        <div data-testid="modal-title">{title}</div>
        {children}
        <button data-testid="modal-primary" onClick={onPrimaryAction}>
          Confirm
        </button>
        <button data-testid="modal-secondary" onClick={onSecondaryAction}>
          Cancel
        </button>
        <button data-testid="modal-close" onClick={onClose}>
          Close
        </button>
      </div>
    ) : null,
}));

// Mock UI components
vi.mock("@components/ui/Input", () => ({
  Input: ({
    id,
    value,
    onChange,
    placeholder,
    className,
  }: {
    id: string;
    value: string;
    onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
    placeholder: string;
    className?: string;
  }) => (
    <input
      data-testid={id}
      value={value}
      onChange={onChange}
      placeholder={placeholder}
      className={className}
    />
  ),
}));

vi.mock("@components/ui/Checkbox", () => ({
  Checkbox: ({
    id,
    checked,
    onChange,
    onClick,
    label,
    className,
  }: {
    id: string;
    checked: boolean;
    onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
    onClick?: (e: React.MouseEvent) => void;
    label?: string;
    className?: string;
  }) => (
    <label>
      <input
        type="checkbox"
        id={id}
        data-testid={id}
        checked={checked}
        onChange={onChange}
        onClick={onClick}
        className={className}
      />
      {label && <span>{label}</span>}
    </label>
  ),
}));

vi.mock("@components/ui/Button", () => ({
  Button: ({
    id,
    children,
    onClick,
    disabled,
    className,
    title,
  }: {
    id?: string;
    children: React.ReactNode;
    onClick?: (e: React.MouseEvent) => void;
    disabled?: boolean;
    variant?: string;
    size?: string;
    className?: string;
    title?: string;
  }) => (
    <button
      id={id}
      data-testid={id}
      onClick={onClick}
      disabled={disabled}
      className={className}
      title={title}
    >
      {children}
    </button>
  ),
}));

vi.mock("@components/ui/Tooltip", () => ({
  Tooltip: ({
    children,
    content,
  }: {
    children: React.ReactNode;
    content: string;
  }) => <div title={content}>{children}</div>,
}));

vi.mock("@components/ui/Chip", () => ({
  Chip: ({
    children,
    onRemove,
    className,
  }: {
    children: React.ReactNode;
    onRemove?: () => void;
    size?: string;
    variant?: string;
    className?: string;
    closeClassNames?: string;
  }) => (
    <div className={className} data-testid="poi-chip">
      {children}
      {onRemove && (
        <button onClick={onRemove} data-testid="remove-poi">
          ×
        </button>
      )}
    </div>
  ),
}));

vi.mock("@components/ui/Badge", () => ({
  Badge: ({
    children,
    className,
  }: {
    children: React.ReactNode;
    variant?: string;
    className?: string;
    size?: string;
  }) => <span className={className}>{children}</span>,
}));

const createMockStore = (
  mapMarkerState?: Partial<MapMarkerLocationsState>,
  campaignState?: { id: string; countryId: string; name: string } | null,
) => {
  return configureStore({
    reducer: {
      mapMarkerLocations: mapMarkerLocationsReducer,
      campaign: campaignSlice,
    },
    preloadedState: {
      mapMarkerLocations: {
        geometries: [],
        locations: [],
        selectedLocation: null,
        isLoading: false,
        error: null,
        ...mapMarkerState,
      },
      campaign: {
        campaignData: campaignState || {
          id: "campaign-1",
          countryId: "US",
          name: "Test Campaign",
        },
        currentCampaignName: "",
        campaignId: "campaign-1",
        isCreating: false,
        createError: null,
        isEditMode: false,
      },
    } as Parameters<typeof configureStore>[0]["preloadedState"],
  });
};

const createMockLocation = (
  overrides?: Partial<MapMarkerLocation>,
): MapMarkerLocation => ({
  id: "location-1",
  lat: 40.7128,
  lng: -74.006,
  name: "Test Location",
  address: "123 Test Street",
  included: true,
  isShape: false,
  ...overrides,
});

const createMockGeometry = (overrides?: Partial<MapGeometry>): MapGeometry => ({
  id: "geometry-1",
  type: "Polygon",
  coordinates: [
    [-74.006, 40.7128],
    [-74.005, 40.7129],
  ],
  name: "Test Polygon",
  included: true,
  isShape: true,
  ...overrides,
});

describe("GeoFencingForm", () => {
  const mockSetValue = vi.fn();
  const mockOnFieldChange = vi.fn();
  const defaultProps = {
    control: {} as Control<TargetingFormData>,
    onFieldChange: mockOnFieldChange,
    geofencingFormData: {
      geometries: [],
      locations: [],
    } as TargetingFormData["geofencing"],
    setValue: mockSetValue,
  };

  const mockGetCountryByName = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    capturedMapProps.length = 0;

    // RTK Query returns SuccessResponse<T> which has { success: boolean, data?: T }
    const mockCountryData = {
      success: true,
      data: {
        id: "us",
        countryId: "US",
        name: "United States",
        latitude: 40.7128,
        longitude: -74.006,
        zoom: 12,
        population: 0,
        iso: "US",
        postalformat: "",
        postalname: "",
        active: true,
        tax: { percent: 0 } as CountryTax,
        updatedAt: "",
      },
    };

    vi.mocked(useLazyGetCountryByNameQuery).mockReturnValue([
      mockGetCountryByName,
      {
        data: mockCountryData,
        isLoading: false,
        isError: false,
        error: undefined,
      },
      mockGetCountryByName,
    ] as unknown as ReturnType<typeof useLazyGetCountryByNameQuery>);

    mockGetCountryByName.mockResolvedValue(mockCountryData);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("country fetch (feedback S.No 11)", () => {
    // API stores country ids as slugs ("sri-lanka"); a raw lowercased name
    // with spaces ("sri lanka") 404s and the map shows "country not available".
    it("slugifies multi-word countryId before fetching country data", () => {
      const store = createMockStore(undefined, {
        id: "campaign-1",
        countryId: "Sri Lanka",
        name: "Test Campaign",
      });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      expect(mockGetCountryByName).toHaveBeenCalledWith("sri-lanka");
    });

    it("renders the map with a fallback zoom when the country record has zoom 0 (UAE)", () => {
      vi.mocked(useLazyGetCountryByNameQuery).mockReturnValue([
        mockGetCountryByName,
        {
          data: {
            success: true,
            data: {
              id: "ae",
              countryId: "united-arab-emirates",
              name: "United Arab Emirates",
              latitude: 24.349886,
              longitude: 52.834754,
              zoom: 0, // real prod/stg data — must not hang on "Loading map..."
              population: 9991000,
              iso: "AE",
              postalformat: "-",
              postalname: "",
              active: true,
              tax: { percent: 5 } as CountryTax,
              updatedAt: "",
            },
          },
          isLoading: false,
          isError: false,
          error: undefined,
        },
        mockGetCountryByName,
      ] as unknown as ReturnType<typeof useLazyGetCountryByNameQuery>);

      const store = createMockStore(undefined, {
        id: "campaign-1",
        countryId: "United Arab Emirates",
        name: "Test Campaign",
      });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      expect(
        screen.queryByText("targeting.geofencing.loading_map"),
      ).not.toBeInTheDocument();
      expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      expect(screen.getByTestId("map-zoom")).toHaveTextContent(/[1-9]/);
    });

    it("keeps single-word countryId working", () => {
      const store = createMockStore(undefined, {
        id: "campaign-1",
        countryId: "US",
        name: "Test Campaign",
      });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      expect(mockGetCountryByName).toHaveBeenCalledWith("us");
    });
  });

  // Feedback SI 48: typing in the selected-locations search must not lose
  // focus between keystrokes (users had to re-click after every letter).
  describe("selected locations search (feedback SI 48)", () => {
    it("keeps focus and accumulates the typed text", async () => {
      const user = userEvent.setup();
      const store = createMockStore({
        locations: [
          createMockLocation({ id: "l1", name: "China Town" }),
          createMockLocation({ id: "l2", name: "Beijing Plaza" }),
        ],
      });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      const search = screen.getByPlaceholderText(
        "targeting.geofencing.search_placeholder",
      );
      await user.click(search);
      await user.keyboard("china");

      expect(search).toHaveValue("china");
      expect(search).toHaveFocus();
    });

    it("keeps map props referentially stable while typing (no marker/popup churn)", async () => {
      const user = userEvent.setup();
      const store = createMockStore({
        locations: [createMockLocation({ id: "l1", name: "China Town" })],
      });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      const search = screen.getByPlaceholderText(
        "targeting.geofencing.search_placeholder",
      );
      await user.click(search);
      await user.keyboard("ch");

      // Re-renders caused by typing must NOT hand the real map new object
      // identities — in the browser that tears down markers/popups on every
      // keystroke and steals focus from this input (SI 48).
      const identityOf = (key: string) =>
        new Set(capturedMapProps.map((p) => p[key])).size;
      expect(capturedMapProps.length).toBeGreaterThan(1);
      expect(identityOf("defaultCenter")).toBe(1);
      expect(identityOf("controlsConfig")).toBe(1);
      expect(identityOf("locationsList")).toBe(1);
      expect(identityOf("popupExtraProps")).toBe(1);
    });
  });

  describe("rendering", () => {
    it("should render loading state when campaign data is not available", () => {
      // When campaignData is null, also return undefined data from the query
      vi.mocked(useLazyGetCountryByNameQuery).mockReturnValue([
        mockGetCountryByName,
        {
          data: undefined,
          isLoading: false,
          isError: false,
          error: undefined,
        },
        mockGetCountryByName,
      ] as unknown as ReturnType<typeof useLazyGetCountryByNameQuery>);

      const store = createMockStore(undefined, null);

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      // The translation function returns the key as-is, so search for the translation key
      expect(
        screen.getByText("targeting.geofencing.loading_map"),
      ).toBeInTheDocument();
    });

    it("should render loading state when country data is loading", () => {
      vi.mocked(useLazyGetCountryByNameQuery).mockReturnValue([
        mockGetCountryByName,
        {
          data: undefined,
          isLoading: true,
          isError: false,
          error: undefined,
        },
        mockGetCountryByName,
      ] as unknown as ReturnType<typeof useLazyGetCountryByNameQuery>);

      const store = createMockStore();

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      // The translation function returns the key as-is, so search for the translation key
      expect(
        screen.getByText("targeting.geofencing.loading_map"),
      ).toBeInTheDocument();
    });

    it("should render error state when country data fetch fails", () => {
      // The component checks loading before error. For error to show, we need:
      // - campaignData exists
      // - isCountryLoading is false
      // - mapDefaultCenter and mapDefaultZoom are defined (so loading check passes)
      // - isCountryError is true
      // So we provide data but mark as error to test the error state
      const mockCountryDataWithError = {
        success: true,
        data: {
          id: "us",
          countryId: "US",
          name: "United States",
          latitude: 40.7128,
          longitude: -74.006,
          zoom: 12,
          population: 0,
          iso: "US",
          postalformat: "",
          postalname: "",
          active: true,
          tax: { percent: 0 } as CountryTax,
          updatedAt: "",
        },
      };

      vi.mocked(useLazyGetCountryByNameQuery).mockReturnValue([
        mockGetCountryByName,
        {
          data: mockCountryDataWithError,
          isLoading: false,
          isError: true, // Mark as error even though we have data
          error: { message: "Failed to fetch" } as
            | SerializedError
            | FetchBaseQueryError,
        },
        mockGetCountryByName,
      ] as unknown as ReturnType<typeof useLazyGetCountryByNameQuery>);

      const store = createMockStore();

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      // Now the error check should be reached since loading conditions are false
      expect(
        screen.getByText("targeting.geofencing.map_error"),
      ).toBeInTheDocument();
    });

    it("should render map and sidebar when data is loaded", async () => {
      const store = createMockStore();

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      expect(screen.getByText(/selected_locations/i)).toBeInTheDocument();
    });
  });

  describe("location management", () => {
    it("should add location from map", async () => {
      const user = userEvent.setup();
      const store = createMockStore();

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      const addLocationButton = screen.getByTestId("add-location");
      await user.click(addLocationButton);

      await waitFor(() => {
        const state = store.getState() as RootState;
        expect(state.mapMarkerLocations.locations.length).toBeGreaterThan(0);
      });
    });

    it("should add geometry from map", async () => {
      const user = userEvent.setup();
      const store = createMockStore();

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      const addShapeButton = screen.getByTestId("add-shape");
      await user.click(addShapeButton);

      await waitFor(() => {
        const state = store.getState() as RootState;
        expect(state.mapMarkerLocations.geometries.length).toBeGreaterThan(0);
      });
    });

    it("should prevent duplicate geometries", async () => {
      const user = userEvent.setup();
      // Create geometry with same coordinates as the mock will add
      const existingGeometry = createMockGeometry({
        type: "Polygon",
        coordinates: [
          [-74.006, 40.7128],
          [-74.005, 40.7129],
        ],
      });
      const store = createMockStore({
        geometries: [existingGeometry],
      });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      const addShapeButton = screen.getByTestId("add-shape");
      await user.click(addShapeButton);

      await waitFor(
        () => {
          expect(mockShowWarning).toHaveBeenCalled();
        },
        { timeout: 3000 },
      );
    });

    it("should update circle radius", async () => {
      const user = userEvent.setup();
      const location = createMockLocation({ id: "location-1", radius: 1000 });
      const store = createMockStore({
        locations: [location],
      });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      const updateRadiusButton = screen.getByTestId("update-radius");
      await user.click(updateRadiusButton);

      await waitFor(() => {
        const state = store.getState() as RootState;
        const updatedLocation = state.mapMarkerLocations.locations.find(
          (loc: MapMarkerLocation) => loc.id === "location-1",
        );
        expect(updatedLocation?.radius).toBe(2000);
      });
    });
  });

  describe("location list", () => {
    it("should display locations in sidebar", async () => {
      const locations = [
        createMockLocation({ id: "location-1", name: "Location 1" }),
        createMockLocation({ id: "location-2", name: "Location 2" }),
      ];
      const store = createMockStore({ locations });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      // Wait for map to load first
      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      // Then wait for locations to be displayed
      await waitFor(
        () => {
          expect(screen.getByText("Location 1")).toBeInTheDocument();
          expect(screen.getByText("Location 2")).toBeInTheDocument();
        },
        { timeout: 3000 },
      );
    });

    it("should filter locations by search query", async () => {
      const user = userEvent.setup();
      const locations = [
        createMockLocation({ id: "location-1", name: "Central Park" }),
        createMockLocation({ id: "location-2", name: "Times Square" }),
      ];
      const store = createMockStore({ locations });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      // Wait for map to load first
      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      // Wait for locations to be displayed
      await waitFor(
        () => {
          expect(screen.getByText("Central Park")).toBeInTheDocument();
        },
        { timeout: 3000 },
      );

      const searchInput = screen.getByPlaceholderText(/search_placeholder/i);
      await user.clear(searchInput);
      await user.type(searchInput, "Central");

      await waitFor(
        () => {
          expect(screen.getByText("Central Park")).toBeInTheDocument();
          expect(screen.queryByText("Times Square")).not.toBeInTheDocument();
        },
        { timeout: 3000 },
      );
    });

    it("should toggle location included state", async () => {
      const user = userEvent.setup();
      const location = createMockLocation({
        id: "location-1",
        included: true,
      });
      const store = createMockStore({ locations: [location] });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      // Wait for map to load first
      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      // Wait for location to be displayed
      await waitFor(
        () => {
          expect(screen.getByText("Test Location")).toBeInTheDocument();
        },
        { timeout: 3000 },
      );

      const checkbox = screen.getByTestId("saved-location-checkbox-1");
      await user.click(checkbox);

      await waitFor(
        () => {
          const state = store.getState() as RootState;
          const updatedLocation = state.mapMarkerLocations.locations.find(
            (loc: MapMarkerLocation) => loc.id === "location-1",
          );
          expect(updatedLocation?.included).toBe(false);
        },
        { timeout: 3000 },
      );
    });

    it("should toggle all locations", async () => {
      const user = userEvent.setup();
      const locations = [
        createMockLocation({ id: "location-1", included: true }),
        createMockLocation({ id: "location-2", included: true }),
      ];
      const store = createMockStore({ locations });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      // Wait for map to load first
      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      // Wait for enable all checkbox to be displayed
      await waitFor(
        () => {
          expect(screen.getByTestId("enable-all-checkbox")).toBeInTheDocument();
        },
        { timeout: 3000 },
      );

      const enableAllCheckbox = screen.getByTestId("enable-all-checkbox");
      await user.click(enableAllCheckbox);

      await waitFor(
        () => {
          const state = store.getState() as RootState;
          state.mapMarkerLocations.locations.forEach(
            (loc: MapMarkerLocation) => {
              expect(loc.included).toBe(false);
            },
          );
        },
        { timeout: 3000 },
      );
    });

    it("should delete location", async () => {
      const user = userEvent.setup();
      const location = createMockLocation({ id: "location-1" });
      const store = createMockStore({ locations: [location] });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      // Wait for map to load first
      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      // Wait for location to be displayed
      await waitFor(
        () => {
          expect(screen.getByText("Test Location")).toBeInTheDocument();
        },
        { timeout: 3000 },
      );

      const deleteButton = screen.getByTestId("delete-location-button-1");
      await user.click(deleteButton);

      await waitFor(
        () => {
          expect(screen.getByTestId("modal")).toBeInTheDocument();
        },
        { timeout: 3000 },
      );

      const confirmButton = screen.getByTestId("modal-primary");
      await user.click(confirmButton);

      await waitFor(
        () => {
          const state = store.getState() as RootState;
          expect(state.mapMarkerLocations.locations.length).toBe(0);
        },
        { timeout: 3000 },
      );
    });
  });

  describe("CSV import", () => {
    it("should open CSV upload drawer", async () => {
      const user = userEvent.setup();
      const store = createMockStore();

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      const importButton = screen.getByRole("button", {
        name: /import_locations/i,
      });
      await user.click(importButton);

      await waitFor(() => {
        expect(screen.getByTestId("csv-upload-drawer")).toBeInTheDocument();
      });
    });

    it("should import locations from CSV", async () => {
      const user = userEvent.setup();
      const store = createMockStore();

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      const importButton = screen.getByRole("button", {
        name: /import_locations/i,
      });
      await user.click(importButton);

      await waitFor(() => {
        expect(screen.getByTestId("csv-upload-drawer")).toBeInTheDocument();
      });

      const importLocationsButton = screen.getByTestId("import-locations");
      await user.click(importLocationsButton);

      await waitFor(() => {
        const state = store.getState() as RootState;
        expect(state.mapMarkerLocations.locations.length).toBeGreaterThan(0);
      });
    });

    it("should prevent duplicate locations on import", async () => {
      const user = userEvent.setup();
      const existingLocation = createMockLocation({
        lat: 40.7128,
        lng: -74.006,
      });
      const store = createMockStore({ locations: [existingLocation] });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      const importButton = screen.getByRole("button", {
        name: /import_locations/i,
      });
      await user.click(importButton);

      await waitFor(() => {
        expect(screen.getByTestId("csv-upload-drawer")).toBeInTheDocument();
      });

      const importLocationsButton = screen.getByTestId("import-locations");
      await user.click(importLocationsButton);

      await waitFor(() => {
        expect(mockShowWarning).toHaveBeenCalled();
      });
    });
  });

  describe("POI management", () => {
    it("should display POI chips", async () => {
      const location = createMockLocation({
        id: "location-1",
        poi: ["poi1", "poi2"],
        metadata: {
          poi1: JSON.stringify({ displayName: "POI 1" }),
          poi2: JSON.stringify({ displayName: "POI 2" }),
        },
      });
      const store = createMockStore({ locations: [location] });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      // Wait for map to load first
      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      // Wait for POI chips to be displayed
      await waitFor(
        () => {
          expect(screen.getByText("POI 1")).toBeInTheDocument();
          expect(screen.getByText("POI 2")).toBeInTheDocument();
        },
        { timeout: 3000 },
      );
    });

    it("should remove POI", async () => {
      const user = userEvent.setup();
      const location = createMockLocation({
        id: "location-1",
        poi: ["poi1"],
        metadata: {
          poi1: JSON.stringify({ displayName: "POI 1" }),
        },
      });
      const store = createMockStore({ locations: [location] });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      // Wait for map to load first
      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      // Wait for POI chip to be displayed
      await waitFor(
        () => {
          expect(screen.getByText("POI 1")).toBeInTheDocument();
        },
        { timeout: 3000 },
      );

      // Find and click the remove button on the POI chip
      const removeButton = screen.getByTestId("remove-poi");
      await user.click(removeButton);

      await waitFor(
        () => {
          const state = store.getState() as RootState;
          const updatedLocation = state.mapMarkerLocations.locations.find(
            (loc: MapMarkerLocation) => loc.id === "location-1",
          );
          expect(updatedLocation?.poi?.length).toBe(0);
        },
        { timeout: 3000 },
      );
    });

    it("should disable add POI button when limit is reached", async () => {
      const location = createMockLocation({
        id: "location-1",
        poi: ["poi1", "poi2", "poi3", "poi4", "poi5"],
      });
      const store = createMockStore({ locations: [location] });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      // Wait for map to load first
      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      // Wait for add POI button to be displayed
      await waitFor(
        () => {
          const addPoiButton = screen.getByTestId("add-poi-button-1");
          expect(addPoiButton).toBeDisabled();
        },
        { timeout: 3000 },
      );
    });
  });

  describe("data persistence", () => {
    it("should load saved geofencing data on mount", async () => {
      const savedData = {
        geometries: [
          {
            type: "Polygon" as const,
            coordinates: [[-74.006, 40.7128]],
            included: true,
            name: "Saved Polygon",
            poi: [] as string[],
            metadata: {} as Record<string, string>,
          },
        ],
        locations: [
          {
            lat: 40.7128,
            lng: -74.006,
            address: "Saved Address",
            included: true,
            name: "Saved Location",
            poi: [] as string[],
            metadata: {} as Record<string, string>,
          },
        ],
      };

      const props = {
        ...defaultProps,
        geofencingFormData: savedData,
      };

      const store = createMockStore();

      render(
        <Provider store={store}>
          <GeoFencingForm {...props} />
        </Provider>,
      );

      // Wait for map to load first
      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      // Wait for data to be loaded into Redux store
      await waitFor(
        () => {
          const state = store.getState() as RootState;
          expect(state.mapMarkerLocations.geometries.length).toBeGreaterThan(0);
          expect(state.mapMarkerLocations.locations.length).toBeGreaterThan(0);
        },
        { timeout: 3000 },
      );
    });

    it("should sync state changes to form", async () => {
      const user = userEvent.setup();
      const store = createMockStore();

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      // Add a location
      const addLocationButton = screen.getByTestId("add-location");
      await user.click(addLocationButton);

      await waitFor(
        () => {
          expect(mockSetValue).toHaveBeenCalled();
          expect(mockOnFieldChange).toHaveBeenCalled();
        },
        { timeout: 3000 },
      );
    });
  });

  describe("edge cases", () => {
    it("should handle empty geofencing data", async () => {
      const props = {
        ...defaultProps,
        geofencingFormData: {
          geometries: [],
          locations: [],
        } as TargetingFormData["geofencing"],
      };

      const store = createMockStore();

      render(
        <Provider store={store}>
          <GeoFencingForm {...props} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });
    });

    it("should handle geometry center calculation", async () => {
      const user = userEvent.setup();
      const geometry = createMockGeometry({
        coordinates: [
          [-74.006, 40.7128],
          [-74.005, 40.7129],
          [-74.004, 40.713],
        ],
      });
      const store = createMockStore({ geometries: [geometry] });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      // Wait for map to load first
      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      // Wait for geometry to be displayed
      await waitFor(
        () => {
          expect(screen.getByText("Test Polygon")).toBeInTheDocument();
        },
        { timeout: 3000 },
      );

      // Click on geometry to navigate
      const geometryItem = screen.getByText("Test Polygon");
      await user.click(geometryItem);

      await waitFor(
        () => {
          const state = store.getState() as RootState;
          expect(state.mapMarkerLocations.selectedLocation).not.toBeNull();
        },
        { timeout: 3000 },
      );
    });

    it("should handle delete all shapes", async () => {
      const user = userEvent.setup();
      const geometries = [
        createMockGeometry({ id: "geometry-1" }),
        createMockGeometry({ id: "geometry-2" }),
      ];
      const store = createMockStore({ geometries });

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      const deleteAllButton = screen.getByTestId("delete-all-shapes");
      await user.click(deleteAllButton);

      await waitFor(() => {
        const state = store.getState() as RootState;
        expect(state.mapMarkerLocations.geometries.length).toBe(0);
      });
    });
  });

  describe("popup extra props (identity-only, no budget metrics)", () => {
    const readPopupExtraProps = (): Record<string, unknown> => {
      const el = screen.getByTestId("mapbox-wrapper");
      return JSON.parse(el.getAttribute("data-popup-extra-props") ?? "{}");
    };

    it("passes currency without campaign-level budget metrics", async () => {
      const campaignState = {
        id: "campaign-1",
        countryId: "US",
        name: "Test Campaign",
        budget: 59900,
        currency: "MYR",
        goals: { goalType: "IMPRESSIONS", targetValue: 3400680 },
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } as any;
      const store = createMockStore(undefined, campaignState);

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      const extra = readPopupExtraProps();
      // Campaign-level budget metrics are no longer passed (they were misleading)
      expect("impressions" in extra).toBe(false);
      expect("spots" in extra).toBe(false);
      expect("price" in extra).toBe(false);
      expect(extra.currency).toBe("MYR");
    });

    it("maps selected parent location's state to popupExtraProps.stateLabel", async () => {
      const campaignState = {
        id: "campaign-1",
        countryId: "US",
        name: "Test Campaign",
        budget: 1000,
        currency: "USD",
        goals: { goalType: "IMPRESSIONS", targetValue: 10000 },
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } as any;
      const selectedLocation: MapMarkerLocation = {
        id: "loc-1",
        lat: 3.14,
        lng: 101.69,
        name: "Kuala Lumpur",
        address: "Kuala Lumpur",
        included: true,
        isShape: false,
        metadata: { localName: "Selangor", localNameType: "State" },
      };
      const store = createMockStore(
        { selectedLocation, locations: [selectedLocation] },
        campaignState,
      );

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      const extra = readPopupExtraProps();
      expect(extra.stateLabel).toBe("Selangor");
    });

    it("leaves stateLabel undefined when parent has no state-like localNameType", async () => {
      const campaignState = {
        id: "campaign-1",
        countryId: "US",
        name: "Test Campaign",
        budget: 1000,
        currency: "USD",
        goals: { goalType: "IMPRESSIONS", targetValue: 10000 },
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } as any;
      const selectedLocation: MapMarkerLocation = {
        id: "loc-1",
        lat: 3.14,
        lng: 101.69,
        name: "Some circle",
        address: "",
        included: true,
        isShape: false,
        metadata: { type: "circle" },
      };
      const store = createMockStore(
        { selectedLocation, locations: [selectedLocation] },
        campaignState,
      );

      render(
        <Provider store={store}>
          <GeoFencingForm {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      const extra = readPopupExtraProps();
      expect("stateLabel" in extra).toBe(false);
    });
  });
});
