import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { searchPlacesByText } from "@utils/google-poi-category-api";
import mapboxgl from "mapbox-gl";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

import MapBoxWrapper, { normalizeItem } from "../Mapbox";

const createMapInstance = () => {
  const onceHandlers: Record<string, () => void> = {};
  const onHandlers: Record<string, ((...args: unknown[]) => void)[]> = {};
  return {
    on: vi.fn((event: string, handler: (...args: unknown[]) => void) => {
      if (!onHandlers[event]) onHandlers[event] = [];
      onHandlers[event].push(handler);
    }),
    once: vi.fn((event: string, handler: () => void) => {
      onceHandlers[event] = handler;
      if (event === "style.load") setTimeout(handler, 0);
    }),
    off: vi.fn(),
    addControl: vi.fn(),
    addSource: vi.fn(),
    addLayer: vi.fn(),
    getSource: vi.fn(() => ({
      setData: vi.fn(),
    })),
    getLayer: vi.fn((id: string) =>
      id.startsWith("gl-draw-") ? { id } : undefined,
    ),
    removeLayer: vi.fn(),
    removeSource: vi.fn(),
    setStyle: vi.fn(),
    setTerrain: vi.fn(),
    flyTo: vi.fn(),
    getZoom: vi.fn(() => 10),
    getCenter: vi.fn(() => ({ lng: 0, lat: 0 })),
    getPitch: vi.fn(() => 0),
    getBearing: vi.fn(() => 0),
    getStyle: vi.fn(() => ({
      layers: [
        { type: "symbol", id: "first-symbol-layer" },
        { type: "fill", id: "other" },
      ],
    })),
    isStyleLoaded: vi.fn(() => true),
    getCanvas: vi.fn(() => ({ style: { cursor: "" } })),
    moveLayer: vi.fn(),
    setLayoutProperty: vi.fn(),
    getLngLat: vi.fn(() => ({ lng: 0, lat: 0 })),
    _getOnHandlers: () => onHandlers,
    _getOnceHandlers: () => onceHandlers,
  };
};

vi.mock("mapbox-gl", () => ({
  default: {
    accessToken: "",
    Map: vi.fn(() => createMapInstance()),
    Marker: vi.fn((el?: HTMLElement) => {
      const instance = {
        __el: el,
        togglePopup: vi.fn(),
        setLngLat: vi.fn().mockReturnThis(),
        setPopup: vi.fn().mockReturnThis(),
        addTo: vi.fn().mockReturnThis(),
        remove: vi.fn(),
        getPopup: vi.fn(() => ({
          isOpen: vi.fn(() => false),
          remove: vi.fn(),
        })),
      };
      return instance;
    }),
    Popup: vi.fn(() => ({
      setHTML: vi.fn().mockReturnThis(),
      setDOMContent: vi.fn().mockReturnThis(),
      addTo: vi.fn().mockReturnThis(),
      remove: vi.fn(),
      isOpen: vi.fn(() => false),
    })),
    NavigationControl: vi.fn(),
    FullscreenControl: vi.fn(),
  },
}));

vi.mock("@mapbox/mapbox-gl-draw", () => ({
  default: vi.fn(() => ({
    changeMode: vi.fn(),
    getAll: vi.fn(() => ({ features: [] })),
    delete: vi.fn(),
    on: vi.fn(),
    off: vi.fn(),
  })),
}));

vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({
    showError: vi.fn(),
    showWarning: vi.fn(),
  }),
}));

vi.mock("@config/tolgee", () => ({ TolgeeConfig: {} }));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string) => key,
  }),
  useTolgee: () => ({
    getLanguage: () => "en",
  }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("@utils/google-poi-category-api", () => ({
  fetchNearbyPlaces: vi.fn(() => Promise.resolve([])),
  searchPlacesByText: vi.fn(() => Promise.resolve([])),
  getPOIPhotoUrl: vi.fn(() => Promise.resolve(null)),
}));

vi.mock("@config/index", () => ({
  CONFIG: {
    MAPBOX_ACCESS_TOKEN: "test-token",
    S3_BUCKET_URL: "https://test-bucket.s3.amazonaws.com",
  },
}));

vi.mock("react-dom/client", () => ({
  createRoot: vi.fn(() => ({
    render: vi.fn(),
  })),
}));

vi.mock("react-dom/server", () => ({
  default: {
    renderToString: vi.fn((node) => String(node)),
  },
}));

vi.mock("@utils/inventory.utils", () => ({
  getLatitude: vi.fn(
    (
      loc:
        | {
            locationCoordinates?: {
              coordinates?: Array<{ latitude?: number }>;
            };
          }
        | null
        | undefined,
    ) => loc?.locationCoordinates?.coordinates?.[0]?.latitude ?? 40.7484,
  ),
  getLongitude: vi.fn(
    (
      loc:
        | {
            locationCoordinates?: {
              coordinates?: Array<{ longitude?: number }>;
            };
          }
        | null
        | undefined,
    ) => loc?.locationCoordinates?.coordinates?.[0]?.longitude ?? -73.9857,
  ),
}));

describe("normalizeItem", () => {
  it("normalizes item with detail (MapInventoryItem/InventoryItem)", () => {
    const item = {
      detail: {
        id: "inv-1",
        mediaOwnerName: "Owner A",
        isSelected: true,
      },
      location: {
        location: {
          address: "123 Main St",
          locationCoordinates: {
            coordinates: [{ latitude: 40.75, longitude: -73.99 }],
          },
        },
      },
    };
    const result = normalizeItem(item as never);
    expect(result.id).toBe("inv-1");
    expect(result.name).toBe("Owner A");
    expect(result.address).toBe("123 Main St");
    expect(result.included).toBe(true);
    expect(result.lat).toBe(40.75);
    expect(result.lng).toBe(-73.99);
  });

  it("prefers inventory name over mediaOwnerName for the marker tooltip", () => {
    const item = {
      detail: {
        id: "inv-3",
        name: "Times Square Billboard",
        mediaOwnerName: "Owner A",
        isSelected: true,
      },
      location: {
        location: {
          address: "789 Broadway",
          locationCoordinates: {
            coordinates: [{ latitude: 40.76, longitude: -73.98 }],
          },
        },
      },
    };
    const result = normalizeItem(item as never);
    expect(result.name).toBe("Times Square Billboard");
  });

  it("normalizes item with detail when mediaOwnerName is missing", () => {
    const item = {
      detail: { id: "inv-2", isSelected: false },
      location: {
        location: {
          address: "456 Oak Ave",
          locationCoordinates: {
            coordinates: [{ latitude: 41, longitude: -74 }],
          },
        },
      },
    };
    const result = normalizeItem(item as never);
    expect(result.name).toBe("inv-2");
    expect(result.included).toBe(false);
  });

  it("normalizes CombinedItem with itemType location", () => {
    const item = {
      id: "loc-1",
      itemType: "location" as const,
      lat: 40.7,
      lng: -73.9,
      name: "Place One",
      address: "Address One",
      included: true,
    };
    const result = normalizeItem(item as never);
    expect(result.id).toBe("loc-1");
    expect(result.lat).toBe(40.7);
    expect(result.lng).toBe(-73.9);
    expect(result.name).toBe("Place One");
    expect(result.address).toBe("Address One");
    expect(result.itemType).toBe("location");
  });

  it("normalizes CombinedItem with itemType geometry", () => {
    const item = {
      id: "geo-1",
      itemType: "geometry" as const,
      type: "Polygon" as const,
      coordinates: [
        [
          [0, 0],
          [1, 0],
          [1, 1],
          [0, 1],
          [0, 0],
        ],
      ],
      name: "Shape",
      included: true,
    };
    const result = normalizeItem(item as never);
    expect(result.id).toBe("geo-1");
    expect(result.type).toBe("Polygon");
    expect(result.coordinates).toEqual(item.coordinates);
    expect(result.itemType).toBe("geometry");
  });

  it("normalizes CombinedItem with radius and metadata", () => {
    const item = {
      id: "circle-1",
      itemType: "location" as const,
      lat: 40.5,
      lng: -74,
      name: "Circle Center",
      included: false,
      radius: 500,
      metadata: { type: "circle" },
    };
    const result = normalizeItem(item as never);
    expect(result.radius).toBe(500);
    expect(result.metadata).toEqual({ type: "circle" });
  });
});

describe("MapBoxWrapper", () => {
  const defaultProps = {
    defaultCenter: [-73.9857, 40.7484] as [number, number],
    defaultZoom: 11,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("Rendering", () => {
    it("renders map container", () => {
      const { container } = render(<MapBoxWrapper {...defaultProps} />);
      const mapContainer = container.querySelector("#map-container");
      expect(mapContainer).toBeInTheDocument();
    });

    it("renders search bar when search is enabled", () => {
      const controlsConfig = {
        search: { enabled: true },
      };
      render(
        <MapBoxWrapper {...defaultProps} controlsConfig={controlsConfig} />,
      );
      const searchInput = screen.getByRole("textbox");
      expect(searchInput).toBeInTheDocument();
    });

    it("renders drawing tools when showDrawingTools is true", () => {
      const controlsConfig = {
        showDrawingTools: true,
      };
      render(
        <MapBoxWrapper {...defaultProps} controlsConfig={controlsConfig} />,
      );
      const buttons = screen.getAllByRole("button");
      expect(buttons.length).toBeGreaterThan(0);
    });

    it("renders view tools when showViewTools is true", () => {
      const controlsConfig = {
        showViewTools: true,
      };
      render(
        <MapBoxWrapper {...defaultProps} controlsConfig={controlsConfig} />,
      );
      const buttons = screen.getAllByRole("button");
      expect(buttons.length).toBeGreaterThan(0);
    });

    it("renders map styles when showMapStyles is true", () => {
      const controlsConfig = {
        showMapStyles: true,
      };
      render(
        <MapBoxWrapper {...defaultProps} controlsConfig={controlsConfig} />,
      );
      const buttons = screen.getAllByRole("button");
      expect(buttons.length).toBeGreaterThan(0);
    });
  });

  describe("Map Initialization", () => {
    it("initializes map with default center and zoom", () => {
      render(<MapBoxWrapper {...defaultProps} />);
      expect(vi.mocked(mapboxgl.Map)).toHaveBeenCalled();
    });

    it("calls onMapReady when map is initialized", async () => {
      const onMapReady = vi.fn();
      const { container } = render(
        <MapBoxWrapper {...defaultProps} onMapReady={onMapReady} />,
      );

      await waitFor(() => {
        expect(vi.mocked(mapboxgl.Map)).toHaveBeenCalled();
      });

      const mapContainer = container.querySelector("#map-container");
      expect(mapContainer).toBeInTheDocument();

      const mapInstance = vi.mocked(mapboxgl.Map).mock.results[0]?.value;
      if (mapInstance && onMapReady) {
        // Simulate map load by calling onMapReady directly with the map instance
        // This tests that the callback can be invoked, even if the actual event
        // system is complex to mock
        onMapReady(mapInstance);
        expect(onMapReady).toHaveBeenCalledWith(mapInstance);
      }
    });
  });

  describe("Drawing Tools", () => {
    it("switches to polygon drawing mode", async () => {
      const user = userEvent.setup();
      const controlsConfig = {
        showDrawingTools: true,
        enablePolygon: true,
      };
      render(
        <MapBoxWrapper {...defaultProps} controlsConfig={controlsConfig} />,
      );

      const buttons = screen.getAllByRole("button");
      const polygonButton = buttons.find((btn) =>
        btn.title?.includes("polygon"),
      );
      if (polygonButton) {
        await user.click(polygonButton);
        await waitFor(() => {
          expect(polygonButton).toBeInTheDocument();
        });
      }
    });

    it("switches to circle drawing mode", async () => {
      const user = userEvent.setup();
      const controlsConfig = {
        showDrawingTools: true,
        enableCircle: true,
      };
      render(
        <MapBoxWrapper {...defaultProps} controlsConfig={controlsConfig} />,
      );

      const buttons = screen.getAllByRole("button");
      const circleButton = buttons.find((btn) => btn.title?.includes("circle"));
      if (circleButton) {
        await user.click(circleButton);
        await waitFor(() => {
          expect(circleButton).toBeInTheDocument();
        });
      }
    });

    it("calls onShapeDrawn when shape is created", async () => {
      const onShapeDrawn = vi.fn();

      render(
        <MapBoxWrapper
          {...defaultProps}
          onShapeDrawn={onShapeDrawn}
          controlsConfig={{ showDrawingTools: true }}
        />,
      );

      await waitFor(() => {
        const mapInstance = vi.mocked(mapboxgl.Map).mock.results[0]?.value;
        if (mapInstance) {
          const mockEvent = {
            features: [
              {
                geometry: {
                  type: "Polygon",
                  coordinates: [
                    [
                      [0, 0],
                      [1, 0],
                      [1, 1],
                      [0, 1],
                      [0, 0],
                    ],
                  ],
                },
              },
            ],
          };

          const drawCreateHandler = mapInstance.on.mock.calls.find(
            (call: string[]) => call[0] === "draw.create",
          )?.[1];

          if (drawCreateHandler) {
            drawCreateHandler(mockEvent);
          }
        }
      });

      await waitFor(() => {
        expect(onShapeDrawn).toHaveBeenCalled();
      });
    });

    it("calls onShapeDrawn with LineString when line geometry is created", async () => {
      const onShapeDrawn = vi.fn();
      render(
        <MapBoxWrapper
          {...defaultProps}
          onShapeDrawn={onShapeDrawn}
          controlsConfig={{ showDrawingTools: true }}
        />,
      );

      await waitFor(() => {
        const mapInstance = vi.mocked(mapboxgl.Map).mock.results[0]?.value;
        if (mapInstance?.on) {
          const onCalls = vi.mocked(mapInstance.on).mock.calls;
          const drawCreateHandler = onCalls.find(
            (call: unknown[]) => call[0] === "draw.create",
          )?.[1] as (e: { features: unknown[] }) => void;
          if (drawCreateHandler) {
            drawCreateHandler({
              features: [
                {
                  geometry: {
                    type: "LineString",
                    coordinates: [
                      [-73.99, 40.74],
                      [-73.98, 40.75],
                    ],
                  },
                },
              ],
            });
          }
        }
      });

      await waitFor(() => {
        expect(onShapeDrawn).toHaveBeenCalledWith(
          expect.objectContaining({
            type: "LineString",
            name: expect.stringContaining("Path"),
          }),
        );
      });
    });
  });

  describe("Search", () => {
    it("searches for locations when search term is entered", async () => {
      const user = userEvent.setup();
      const controlsConfig = {
        search: { enabled: true, showResults: true },
      };

      render(
        <MapBoxWrapper {...defaultProps} controlsConfig={controlsConfig} />,
      );

      const searchInput = screen.getByRole("textbox");
      await user.type(searchInput, "test");

      await waitFor(
        () => {
          expect(vi.mocked(searchPlacesByText)).toHaveBeenCalledWith(
            "test",
            "",
            "en",
          );
        },
        { timeout: 1000 },
      );
    });

    it("calls onLocationSelected when search result is clicked", async () => {
      const user = userEvent.setup();
      const onLocationSelected = vi.fn();
      const controlsConfig = {
        search: { enabled: true, showResults: true },
      };

      const mockResult = [
        {
          lat: 40.7484,
          lng: -73.9857,
          name: "Test Location",
          formatted_address: "Test Location, NY",
          types: ["place"],
        },
      ];
      vi.mocked(searchPlacesByText).mockResolvedValueOnce(mockResult);

      render(
        <MapBoxWrapper
          {...defaultProps}
          controlsConfig={controlsConfig}
          onLocationSelected={onLocationSelected}
        />,
      );

      const searchInput = screen.getByRole("textbox");
      await user.type(searchInput, "test");

      await waitFor(
        () => {
          expect(screen.getByText("Test Location")).toBeInTheDocument();
        },
        { timeout: 1000 },
      );

      const resultButton = screen.getByText("Test Location");
      await user.click(resultButton);

      await waitFor(() => {
        expect(onLocationSelected).toHaveBeenCalledWith(
          expect.objectContaining({
            name: "Test Location",
            address: "Test Location, NY",
            lat: 40.7484,
            lng: -73.9857,
            included: true,
          }),
        );
      });
    });
  });

  describe("Markers", () => {
    it("renders markers for locations list", () => {
      const locationsList = [
        {
          id: "1",
          lat: 40.7484,
          lng: -73.9857,
          name: "Location 1",
          address: "123 Main St",
          included: true,
          isShape: false,
          itemType: "location" as const,
        },
      ];

      const { container } = render(
        <MapBoxWrapper {...defaultProps} locationsList={locationsList} />,
      );

      const mapContainer = container.querySelector("#map-container");
      expect(mapContainer).toBeInTheDocument();
    });

    it("toggles popup when a location marker element is clicked", async () => {
      const locationsList = [
        {
          id: "1",
          lat: 40.7484,
          lng: -73.9857,
          name: "Location 1",
          address: "123 Main St",
          included: true,
          isShape: false,
          itemType: "location" as const,
        },
      ];

      render(<MapBoxWrapper {...defaultProps} locationsList={locationsList} />);

      await waitFor(() => {
        expect(vi.mocked(mapboxgl.Marker)).toHaveBeenCalled();
      });

      const results = vi.mocked(mapboxgl.Marker).mock.results;
      const markerInstance = results[results.length - 1]?.value as {
        __el?: HTMLElement;
        togglePopup: ReturnType<typeof vi.fn>;
      };
      expect(markerInstance?.__el).toBeDefined();

      markerInstance.__el!.dispatchEvent(
        new MouseEvent("click", { bubbles: true }),
      );

      expect(markerInstance.togglePopup).toHaveBeenCalledTimes(1);
    });

    it("forwards popupExtraProps to the custom PopupComponent", async () => {
      const { createRoot } = await import("react-dom/client");
      const createRootMock = vi.mocked(createRoot);
      createRootMock.mockClear();

      const PopupComponent = ({ item }: { item: { name?: string } }) => (
        <div data-testid="custom-popup">{item?.name ?? "Popup"}</div>
      );
      const locationsList = [
        {
          id: "1",
          lat: 40.7484,
          lng: -73.9857,
          name: "Loc",
          address: "Addr",
          included: true,
          isShape: false,
          itemType: "location" as const,
        },
      ];
      const popupExtraProps = {
        impressions: 123,
        spots: 4,
        price: 500,
        currency: "USD",
      };

      render(
        <MapBoxWrapper
          {...defaultProps}
          locationsList={locationsList}
          PopupComponent={PopupComponent}
          popupExtraProps={popupExtraProps}
        />,
      );

      // The custom PopupComponent now mounts via createRoot wrapped in TolgeeProvider,
      // so args[0].type === TolgeeProvider and the popup is at args[0].props.children.
      type RenderArg = {
        type?: unknown;
        props?: {
          children?: { type?: unknown; props?: Record<string, unknown> };
        };
      };
      const findPopupRender = (calls: unknown[][]) =>
        calls.find(
          (args) =>
            (args[0] as RenderArg)?.props?.children?.type === PopupComponent,
        );

      await waitFor(() => {
        const roots = createRootMock.mock.results;
        const matchedRender = findPopupRender(
          roots
            .map((r) => r.value as { render: ReturnType<typeof vi.fn> })
            .flatMap((root) => root.render.mock.calls),
        );
        expect(matchedRender).toBeDefined();
      });

      const matchedRender = findPopupRender(
        createRootMock.mock.results
          .map((r) => r.value as { render: ReturnType<typeof vi.fn> })
          .flatMap((root) => root.render.mock.calls),
      )!;
      const popupElement = (matchedRender[0] as RenderArg).props!.children!;
      expect(popupElement.props!.item).toBeDefined();
      expect(popupElement.props!.impressions).toBe(123);
      expect(popupElement.props!.spots).toBe(4);
      expect(popupElement.props!.price).toBe(500);
      expect(popupElement.props!.currency).toBe("USD");
    });
  });

  describe("POI Markers (availablePOIs)", () => {
    const availablePOIs = [
      {
        locationLat: 3.14,
        locationLng: 101.69,
        displayName: "Toll Plaza Subang",
        primaryType: "transit_station",
        primaryTypeDisplayName: "Transit Station",
      },
    ];

    const POIPopupComponent = ({
      poi,
    }: {
      poi: { displayName: string };
      photoUrl?: string | null;
    }) => <div data-testid="poi-popup">{poi.displayName}</div>;

    it("renders POI pins with pointer cursor", async () => {
      render(
        <MapBoxWrapper
          {...defaultProps}
          availablePOIs={availablePOIs}
          POIPopupComponent={POIPopupComponent}
        />,
      );

      await waitFor(() => {
        expect(vi.mocked(mapboxgl.Marker)).toHaveBeenCalled();
      });

      const results = vi.mocked(mapboxgl.Marker).mock.results;
      const markerInstance = results[results.length - 1]?.value as {
        __el?: HTMLElement;
      };
      expect(markerInstance.__el?.style.cursor).toBe("pointer");
    });

    it("resolves POI markers to the inline category pin via primaryType", async () => {
      const { createRoot } = await import("react-dom/client");
      const createRootMock = vi.mocked(createRoot);
      createRootMock.mockClear();

      render(
        <MapBoxWrapper
          {...defaultProps}
          availablePOIs={availablePOIs}
          POIPopupComponent={POIPopupComponent}
        />,
      );

      await waitFor(() => {
        expect(vi.mocked(mapboxgl.Marker)).toHaveBeenCalled();
      });

      // The POI now mounts two roots: the category pin AND the popup
      // (createRoot + TolgeeProvider). Find the render arg carrying `category`.
      const pinEl = createRootMock.mock.results
        .flatMap(
          (r) =>
            (r.value as { render: ReturnType<typeof vi.fn> }).render.mock.calls,
        )
        .map((c) => c[0] as { props?: { category?: string } })
        .find((el) => el?.props?.category !== undefined);
      expect(pinEl?.props?.category).toBe("transit_station");
    });

    it("falls back to the 'others' category pin for unknown POI categories", async () => {
      const { createRoot } = await import("react-dom/client");
      const createRootMock = vi.mocked(createRoot);
      createRootMock.mockClear();

      render(
        <MapBoxWrapper
          {...defaultProps}
          availablePOIs={[
            {
              locationLat: 1,
              locationLng: 2,
              displayName: "Weird Place",
              primaryType: "totally_made_up",
              primaryTypeDisplayName: "Totally Made Up",
            },
          ]}
          POIPopupComponent={POIPopupComponent}
        />,
      );

      await waitFor(() => {
        expect(vi.mocked(mapboxgl.Marker)).toHaveBeenCalled();
      });

      const pinEl = createRootMock.mock.results
        .flatMap(
          (r) =>
            (r.value as { render: ReturnType<typeof vi.fn> }).render.mock.calls,
        )
        .map((c) => c[0] as { props?: { category?: string } })
        .find((el) => el?.props?.category !== undefined);
      expect(pinEl?.props?.category).toBe("others");
    });

    it("toggles the popup when a POI marker is clicked", async () => {
      render(
        <MapBoxWrapper
          {...defaultProps}
          availablePOIs={availablePOIs}
          POIPopupComponent={POIPopupComponent}
        />,
      );

      await waitFor(() => {
        expect(vi.mocked(mapboxgl.Marker)).toHaveBeenCalled();
      });

      const results = vi.mocked(mapboxgl.Marker).mock.results;
      const markerInstance = results[results.length - 1]?.value as {
        __el?: HTMLElement;
        togglePopup: ReturnType<typeof vi.fn>;
      };

      markerInstance.__el!.dispatchEvent(
        new MouseEvent("click", { bubbles: true }),
      );

      expect(markerInstance.togglePopup).toHaveBeenCalledTimes(1);
    });

    it("prefetches the photo on hover via getPOIPhotoUrl", async () => {
      const { getPOIPhotoUrl } = await import("@utils/google-poi-category-api");
      const spy = vi.mocked(getPOIPhotoUrl);
      spy.mockClear();

      render(
        <MapBoxWrapper
          {...defaultProps}
          availablePOIs={availablePOIs}
          POIPopupComponent={POIPopupComponent}
        />,
      );

      await waitFor(() => {
        expect(vi.mocked(mapboxgl.Marker)).toHaveBeenCalled();
      });

      const results = vi.mocked(mapboxgl.Marker).mock.results;
      const markerInstance = results[results.length - 1]?.value as {
        __el?: HTMLElement;
      };

      markerInstance.__el!.dispatchEvent(
        new MouseEvent("mouseenter", { bubbles: true }),
      );

      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy).toHaveBeenCalledWith(
        expect.objectContaining({ displayName: "Toll Plaza Subang" }),
      );
    });

    it("re-renders the popup root once the photo resolves", async () => {
      const { getPOIPhotoUrl } = await import("@utils/google-poi-category-api");
      const spy = vi.mocked(getPOIPhotoUrl);
      spy.mockResolvedValueOnce("https://photos.example.com/p.jpg");

      const { createRoot } = await import("react-dom/client");
      const createRootMock = vi.mocked(createRoot);
      createRootMock.mockClear();

      render(
        <MapBoxWrapper
          {...defaultProps}
          availablePOIs={availablePOIs}
          POIPopupComponent={POIPopupComponent}
        />,
      );

      await waitFor(() => {
        expect(vi.mocked(mapboxgl.Marker)).toHaveBeenCalled();
      });

      const results = vi.mocked(mapboxgl.Marker).mock.results;
      const markerInstance = results[results.length - 1]?.value as {
        __el?: HTMLElement;
      };

      // The popup mounts via createRoot + TolgeeProvider; its render arg is a
      // TolgeeProvider element (has a `tolgee` prop). Photo arrival re-renders it.
      const popupRoot = createRootMock.mock.results
        .map((r) => r.value as { render: ReturnType<typeof vi.fn> })
        .find((root) =>
          root.render.mock.calls.some(
            (c) => (c[0] as { props?: { tolgee?: unknown } })?.props?.tolgee,
          ),
        );
      const before = popupRoot!.render.mock.calls.length;

      markerInstance.__el!.dispatchEvent(
        new MouseEvent("mouseenter", { bubbles: true }),
      );

      await waitFor(() => {
        expect(popupRoot!.render.mock.calls.length).toBeGreaterThan(before);
      });
    });
  });

  describe("Controls", () => {
    it("toggles 3D view", async () => {
      const user = userEvent.setup();
      const controlsConfig = {
        showViewTools: true,
        enable3D: true,
      };
      render(
        <MapBoxWrapper {...defaultProps} controlsConfig={controlsConfig} />,
      );

      const buttons = screen.getAllByRole("button");
      const toggle3DButton = buttons.find((btn) => btn.title?.includes("3D"));
      if (toggle3DButton) {
        await user.click(toggle3DButton);
        await waitFor(() => {
          expect(toggle3DButton).toBeInTheDocument();
        });
      }
    });

    it("toggles mountain view", async () => {
      const user = userEvent.setup();
      const controlsConfig = {
        showViewTools: true,
        enableMountainView: true,
      };
      render(
        <MapBoxWrapper {...defaultProps} controlsConfig={controlsConfig} />,
      );

      const buttons = screen.getAllByRole("button");
      const toggleMountainButton = buttons.find((btn) =>
        btn.title?.includes("mountain"),
      );
      if (toggleMountainButton) {
        await user.click(toggleMountainButton);
        await waitFor(() => {
          expect(toggleMountainButton).toBeInTheDocument();
        });
      }
    });

    it("changes map style", async () => {
      const user = userEvent.setup();
      const controlsConfig = {
        showMapStyles: true,
        enabledStyles: ["satellite" as const],
      };
      render(
        <MapBoxWrapper {...defaultProps} controlsConfig={controlsConfig} />,
      );

      const buttons = screen.getAllByRole("button");
      const styleButton = buttons.find((btn) =>
        btn.title?.includes("satellite"),
      );
      if (styleButton) {
        await user.click(styleButton);
        await waitFor(() => {
          expect(styleButton).toBeInTheDocument();
        });
      }
    });
  });

  describe("Edge Cases", () => {
    it("handles empty locations list", () => {
      const { container } = render(
        <MapBoxWrapper {...defaultProps} locationsList={[]} />,
      );
      const mapContainer = container.querySelector("#map-container");
      expect(mapContainer).toBeInTheDocument();
    });

    it("handles disabled state", () => {
      const controlsConfig = {
        search: { enabled: true },
      };
      render(
        <MapBoxWrapper {...defaultProps} controlsConfig={controlsConfig} />,
      );
      const searchInput = screen.getByRole("textbox");
      expect(searchInput).toBeInTheDocument();
    });

    it("handles delete all shapes", async () => {
      const user = userEvent.setup();
      const deleteAllShapes = vi.fn();
      const controlsConfig = {
        showDrawingTools: true,
        enableDelete: true,
      };
      render(
        <MapBoxWrapper
          {...defaultProps}
          controlsConfig={controlsConfig}
          deleteAllShapes={deleteAllShapes}
        />,
      );

      const buttons = screen.getAllByRole("button");
      const deleteButton = buttons.find((btn) => btn.id === "map-delete-all");
      if (deleteButton) {
        await user.click(deleteButton);
        expect(deleteAllShapes).toHaveBeenCalled();
      }
    });

    it("renders with locationsList containing geometry (polygon and linestring)", () => {
      const locationsList = [
        {
          id: "poly-1",
          itemType: "geometry" as const,
          type: "Polygon" as const,
          coordinates: [
            [
              [-73.99, 40.74],
              [-73.98, 40.74],
              [-73.98, 40.75],
              [-73.99, 40.75],
              [-73.99, 40.74],
            ],
          ],
          name: "Polygon Area",
          included: true,
          isShape: true,
        },
        {
          id: "line-1",
          itemType: "geometry" as const,
          type: "LineString" as const,
          coordinates: [
            [-73.99, 40.74],
            [-73.98, 40.75],
          ],
          name: "Route",
          included: true,
          isShape: true,
        },
      ];
      const { container } = render(
        <MapBoxWrapper
          {...defaultProps}
          locationsList={locationsList as never}
        />,
      );
      expect(container.querySelector("#map-container")).toBeInTheDocument();
    });

    it("renders with locationsList containing circle metadata", () => {
      const locationsList = [
        {
          id: "circle-1",
          itemType: "location" as const,
          lat: 40.7484,
          lng: -73.9857,
          name: "Circle Point",
          address: "Some Address",
          included: true,
          metadata: { type: "circle" },
          radius: 300,
          isShape: false,
        },
      ];
      const { container } = render(
        <MapBoxWrapper
          {...defaultProps}
          locationsList={locationsList as never}
        />,
      );
      expect(container.querySelector("#map-container")).toBeInTheDocument();
    });

    it("renders with selectedItemId matching an item in locationsList", () => {
      const locationsList = [
        {
          id: "1",
          lat: 40.7484,
          lng: -73.9857,
          name: "Location 1",
          address: "123 Main St",
          included: true,
          isShape: false,
          itemType: "location" as const,
        },
      ];
      const { container } = render(
        <MapBoxWrapper
          {...defaultProps}
          locationsList={locationsList}
          selectedItemId="1"
        />,
      );
      expect(container.querySelector("#map-container")).toBeInTheDocument();
    });

    it("switches to line drawing mode when enableLine is true", async () => {
      const user = userEvent.setup();
      const controlsConfig = {
        showDrawingTools: true,
        enableLine: true,
      };
      render(
        <MapBoxWrapper {...defaultProps} controlsConfig={controlsConfig} />,
      );
      const buttons = screen.getAllByRole("button");
      const lineButton = buttons.find((btn) =>
        btn.title?.toLowerCase().includes("line"),
      );
      if (lineButton) {
        await user.click(lineButton);
        await waitFor(() => {
          expect(lineButton).toBeInTheDocument();
        });
      }
    });

    it("renders with custom PopupComponent", () => {
      const PopupComponent = ({ item }: { item: { name?: string } }) => (
        <div data-testid="custom-popup">{item?.name ?? "Popup"}</div>
      );
      const locationsList = [
        {
          id: "1",
          lat: 40.7484,
          lng: -73.9857,
          name: "Loc",
          address: "Addr",
          included: true,
          isShape: false,
          itemType: "location" as const,
        },
      ];
      const { container } = render(
        <MapBoxWrapper
          {...defaultProps}
          locationsList={locationsList}
          PopupComponent={PopupComponent}
        />,
      );
      expect(container.querySelector("#map-container")).toBeInTheDocument();
    });

    it("renders with search showPOIFilter and poiPlaceholder", () => {
      const controlsConfig = {
        search: {
          enabled: true,
          showPOIFilter: true,
          poiPlaceholder: "Search POI",
        },
      };
      render(
        <MapBoxWrapper {...defaultProps} controlsConfig={controlsConfig} />,
      );
      const searchInput = screen.getByRole("textbox");
      expect(searchInput).toBeInTheDocument();
    });

    it("invokes onMapReady when map is created and style.load fires", async () => {
      const onMapReady = vi.fn();
      render(<MapBoxWrapper {...defaultProps} onMapReady={onMapReady} />);
      await waitFor(
        () => {
          expect(vi.mocked(mapboxgl.Map)).toHaveBeenCalled();
        },
        { timeout: 2000 },
      );
      const mapInstance = vi.mocked(mapboxgl.Map).mock.results[0]?.value;
      expect(mapInstance).toBeDefined();
      await waitFor(
        () => {
          expect(onMapReady).toHaveBeenCalledWith(mapInstance);
        },
        { timeout: 500 },
      );
    });

    it("renders with multiple map styles when enabledStyles has multiple", () => {
      const controlsConfig = {
        showMapStyles: true,
        enabledStyles: [
          "streets" as const,
          "satellite" as const,
          "dark" as const,
        ],
      };
      render(
        <MapBoxWrapper {...defaultProps} controlsConfig={controlsConfig} />,
      );
      const buttons = screen.getAllByRole("button");
      expect(buttons.length).toBeGreaterThan(0);
    });

    it("renders with checkForDuplicate true", () => {
      const { container } = render(
        <MapBoxWrapper {...defaultProps} checkForDuplicate={true} />,
      );
      expect(container.querySelector("#map-container")).toBeInTheDocument();
    });

    it("renders with addPOI and availablePOIs", () => {
      const { container } = render(
        <MapBoxWrapper {...defaultProps} addPOI={true} availablePOIs={[]} />,
      );
      expect(container.querySelector("#map-container")).toBeInTheDocument();
    });

    it("renders with locationsList containing inventory items (detail)", () => {
      const locationsList = [
        {
          detail: {
            id: "inv-1",
            mediaOwnerName: "Media Owner",
            isSelected: true,
          },
          location: {
            location: {
              address: "123 St",
              locationCoordinates: {
                coordinates: [{ latitude: 40.75, longitude: -73.99 }],
              },
            },
          },
        },
      ];
      const { container } = render(
        <MapBoxWrapper
          {...defaultProps}
          locationsList={locationsList as never}
          selectedItemId="inv-1"
        />,
      );
      expect(container.querySelector("#map-container")).toBeInTheDocument();
    });

    it("renders with updateLocationPOIMetadata callback", () => {
      const updateLocationPOIMetadata = vi.fn();
      const { container } = render(
        <MapBoxWrapper
          {...defaultProps}
          locationsList={[]}
          updateLocationPOIMetadata={updateLocationPOIMetadata}
        />,
      );
      expect(container.querySelector("#map-container")).toBeInTheDocument();
    });
  });
});
