import { configureStore } from "@reduxjs/toolkit";
import { useGetGeoImportLocationsQuery } from "@services/inventory/inventorySlice";
import { render, screen, waitFor } from "@testing-library/react";
import React from "react";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { GeoImportLocation } from "../types/location-csv.types";
import { ViewFileLocationDrawer } from "../ViewFileLocationDrawer";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

// Mock the RTK Query hook
vi.mock("@services/inventory/inventorySlice", () => ({
  useGetGeoImportLocationsQuery: vi.fn(),
}));

// Mock MapBoxWrapper
vi.mock("@components/ui/Mapbox", () => ({
  default: ({
    locationsList,
    PopupComponent,
  }: {
    locationsList?: Array<{ lat: number; lng: number; name: string }>;
    PopupComponent?: React.ComponentType<{
      item: { lat: number; lng: number; name: string };
    }>;
  }) => (
    <div data-testid="mapbox-wrapper">
      <div data-testid="locations-count">{locationsList?.length || 0}</div>
      {locationsList && locationsList.length > 0 && PopupComponent && (
        <PopupComponent item={locationsList[0]} />
      )}
    </div>
  ),
}));

// Mock ModalDrawer
vi.mock("@components/ui/ModalDrawer", () => ({
  ModalDrawer: ({
    children,
    isOpen,
    onClose,
    title,
    showBackButton,
  }: {
    children: React.ReactNode;
    isOpen: boolean;
    onClose: () => void;
    title: string;
    showBackButton?: boolean;
  }) =>
    isOpen ? (
      <div data-testid="modal-drawer">
        <div data-testid="modal-title">{title}</div>
        {showBackButton && (
          <button data-testid="back-button" onClick={onClose}>
            Back
          </button>
        )}
        {children}
      </div>
    ) : null,
}));

vi.mock("@components/ui/AgGridTable/AgGridTable", () => ({
  AgGridTable: ({
    rowData,
    loading,
    emptyMessage,
  }: {
    rowData: Array<{
      srId: number;
      locationName?: string;
      latitude?: string;
      longitude?: string;
      radius?: string;
      siteType?: string;
    }>;
    loading: boolean;
    emptyMessage: string;
  }) => (
    <div data-testid="table">
      {loading && <div data-testid="table-loading">Loading...</div>}
      {!loading && (!rowData || rowData.length === 0) && (
        <div data-testid="table-empty">{emptyMessage}</div>
      )}
      {!loading && rowData && rowData.length > 0 && (
        <div data-testid="table-data">
          {rowData.map((row, idx) => (
            <div key={idx} data-testid={`table-row-${idx}`}>
              <div data-testid="cell-srId">{row.srId + 1}</div>
              <div data-testid="cell-locationName">
                {row.locationName && String(row.locationName).trim() !== ""
                  ? row.locationName
                  : "-"}
              </div>
              <div data-testid="cell-latitude">{row.latitude ?? "-"}</div>
              <div data-testid="cell-longitude">{row.longitude ?? "-"}</div>
              <div data-testid="cell-radius">{row.radius ?? "-"}</div>
              <div data-testid="cell-siteType">{row.siteType ?? "-"}</div>
            </div>
          ))}
        </div>
      )}
    </div>
  ),
}));

const createMockStore = () => {
  return configureStore({
    reducer: {
      // Minimal reducer to satisfy Redux
      inventory: (state = {}) => state,
    },
  });
};

const createMockLocation = (
  overrides?: Partial<GeoImportLocation>,
): GeoImportLocation => ({
  locationName: "Test Location",
  latitude: "40.7128",
  longitude: "-74.0060",
  radius: "1000",
  siteType: "Billboard",
  ...overrides,
});

describe("ViewFileLocationDrawer", () => {
  const mockOnClose = vi.fn();
  const mockOnBack = vi.fn();
  const defaultProps = {
    isOpen: true,
    onClose: mockOnClose,
    onBack: mockOnBack,
    fileId: "file-1",
    fileName: "test-file.csv",
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("should not render when isOpen is false", () => {
      const mockData = {
        data: [createMockLocation()],
      };
      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: mockData,
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} isOpen={false} />
        </Provider>,
      );

      expect(screen.queryByTestId("modal-drawer")).not.toBeInTheDocument();
    });

    it("should render drawer with file name as title", () => {
      const mockData = {
        data: [createMockLocation()],
      };
      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: mockData,
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} />
        </Provider>,
      );

      expect(screen.getByTestId("modal-title")).toHaveTextContent(
        "test-file.csv",
      );
    });

    it("should show loading state", () => {
      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: undefined,
        isLoading: true,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} />
        </Provider>,
      );

      expect(screen.getByTestId("table-loading")).toBeInTheDocument();
    });

    it("should render empty message when no locations", () => {
      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: { data: [] },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} />
        </Provider>,
      );

      expect(screen.getByTestId("table-empty")).toHaveTextContent(
        "viewFileLocationDrawer.emptyMessage",
      );
    });

    it("should render locations in table", async () => {
      const locations = [
        createMockLocation({
          locationName: "Location 1",
          latitude: "40.7128",
          longitude: "-74.0060",
          siteType: "Billboard",
        }),
        createMockLocation({
          locationName: "Location 2",
          latitude: "34.0522",
          longitude: "-118.2437",
          siteType: "Digital Screen",
        }),
      ];

      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: { data: locations },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("table-data")).toBeInTheDocument();
      });

      expect(screen.getByTestId("table-row-0")).toBeInTheDocument();
      expect(screen.getByTestId("table-row-1")).toBeInTheDocument();
    });

    it("should render map when locations are available", async () => {
      const locations = [createMockLocation()];

      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: { data: locations },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
      });

      expect(screen.getByTestId("locations-count")).toHaveTextContent("1");
    });

    it("should not render map when no locations", () => {
      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: { data: [] },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} />
        </Provider>,
      );

      expect(screen.queryByTestId("mapbox-wrapper")).not.toBeInTheDocument();
    });
  });

  describe("table columns", () => {
    it("should render serial number column", async () => {
      const locations = [createMockLocation()];

      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: { data: locations },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        const cell = screen.getByTestId("cell-srId");
        expect(cell).toHaveTextContent("1");
      });
    });

    it("should render location name column", async () => {
      const locations = [createMockLocation({ locationName: "Central Park" })];

      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: { data: locations },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        const cell = screen.getByTestId("cell-locationName");
        expect(cell).toHaveTextContent("Central Park");
      });
    });

    it("should render latitude and longitude columns", async () => {
      const locations = [
        createMockLocation({
          latitude: "40.7128",
          longitude: "-74.0060",
        }),
      ];

      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: { data: locations },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("cell-latitude")).toHaveTextContent(
          "40.7128",
        );
        expect(screen.getByTestId("cell-longitude")).toHaveTextContent(
          "-74.0060",
        );
      });
    });

    it("should render radius column", async () => {
      const locations = [createMockLocation({ radius: "5000" })];

      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: { data: locations },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("cell-radius")).toHaveTextContent("5000");
      });
    });

    it("should render site type column", async () => {
      const locations = [createMockLocation({ siteType: "Digital Screen" })];

      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: { data: locations },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("cell-siteType")).toHaveTextContent(
          "Digital Screen",
        );
      });
    });
  });

  describe("map popup", () => {
    it("should render popup with location data", async () => {
      const locations = [
        createMockLocation({
          locationName: "Test Location",
          latitude: "40.7128",
          longitude: "-74.0060",
          radius: "1000",
          siteType: "Billboard",
        }),
      ];

      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: { data: locations },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        // "Test Location" appears in both map popup and table, so use getAllByText
        const locationTexts = screen.getAllByText("Test Location");
        expect(locationTexts.length).toBeGreaterThan(0);
        // Check for popup-specific content (lat/lng/radius use i18n keys via mock)
        expect(
          screen.getByText(
            (content, element) =>
              element?.tagName === "P" &&
              /viewFileLocationDrawer\.lat/.test(content) &&
              content.includes("40.7128"),
          ),
        ).toBeInTheDocument();
        expect(
          screen.getByText(
            (content, element) =>
              element?.tagName === "P" &&
              /viewFileLocationDrawer\.lng/.test(content) &&
              content.includes("-74.0060"),
          ),
        ).toBeInTheDocument();
        expect(
          screen.getByText(
            (content, element) =>
              element?.tagName === "P" &&
              /viewFileLocationDrawer\.radius/.test(content) &&
              content.includes("1000"),
          ),
        ).toBeInTheDocument();
        // Billboard appears in both popup and table, so use getAllByText
        const billboardTexts = screen.getAllByText("Billboard");
        expect(billboardTexts.length).toBeGreaterThan(0);
      });
    });

    it("should handle missing radius in popup", async () => {
      const locations = [
        createMockLocation({
          locationName: "Test Location",
          radius: "",
        }),
      ];

      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: { data: locations },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.queryByText(/Radius:/)).not.toBeInTheDocument();
      });
    });
  });

  describe("edge cases", () => {
    it("should handle invalid coordinates", async () => {
      const locations = [
        createMockLocation({
          latitude: "invalid",
          longitude: "invalid",
        }),
      ];

      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: { data: locations },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        // When coordinates are invalid, parseFloat returns NaN which becomes 0
        // The mapCenter calculation filters out items with 0 coordinates
        // Since mapCenter becomes [0, 0], the map doesn't render (mapCenter[0] !== 0 is false)
        // But the table should still show the invalid data
        expect(screen.queryByTestId("mapbox-wrapper")).not.toBeInTheDocument();
        expect(screen.getByTestId("table-data")).toBeInTheDocument();
        // Verify invalid coordinates are shown in table (appears in both latitude and longitude cells)
        const invalidTexts = screen.getAllByText("invalid");
        expect(invalidTexts.length).toBe(2); // One for latitude, one for longitude
        // Verify specific cells contain invalid values
        const latitudeCell = screen.getByTestId("cell-latitude");
        const longitudeCell = screen.getByTestId("cell-longitude");
        expect(latitudeCell).toHaveTextContent("invalid");
        expect(longitudeCell).toHaveTextContent("invalid");
      });
    });

    it("should handle empty location name", async () => {
      const locations = [createMockLocation({ locationName: "" })];

      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: { data: locations },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        const cell = screen.getByTestId("cell-locationName");
        expect(cell).toHaveTextContent("-");
      });
    });

    it("should handle missing data fields", async () => {
      const locations = [
        {
          locationName: "",
          latitude: "",
          longitude: "",
          radius: "",
          siteType: "",
        } as GeoImportLocation,
      ];

      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: { data: locations },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("table-data")).toBeInTheDocument();
      });
    });
  });

  describe("back button", () => {
    it("should call onBack when back button is clicked", async () => {
      const locations = [createMockLocation()];

      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: { data: locations },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} />
        </Provider>,
      );

      await waitFor(() => {
        const backButton = screen.getByTestId("back-button");
        backButton.click();
        expect(mockOnBack).toHaveBeenCalledTimes(1);
      });
    });

    it("should call onClose when onBack is not provided", async () => {
      const locations = [createMockLocation()];

      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: { data: locations },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer
            {...defaultProps}
            onBack={undefined}
            onClose={mockOnClose}
          />
        </Provider>,
      );

      await waitFor(() => {
        const backButton = screen.getByTestId("back-button");
        backButton.click();
        expect(mockOnClose).toHaveBeenCalledTimes(1);
      });
    });
  });

  describe("query behavior", () => {
    it("should skip query when fileId is empty", () => {
      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: undefined,
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} fileId="" />
        </Provider>,
      );

      expect(useGetGeoImportLocationsQuery).toHaveBeenCalledWith(
        { geoImportId: "" },
        expect.objectContaining({ skip: true }),
      );
    });

    it("should skip query when drawer is closed", () => {
      vi.mocked(useGetGeoImportLocationsQuery).mockReturnValue({
        data: undefined,
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetGeoImportLocationsQuery>);

      render(
        <Provider store={createMockStore()}>
          <ViewFileLocationDrawer {...defaultProps} isOpen={false} />
        </Provider>,
      );

      expect(useGetGeoImportLocationsQuery).toHaveBeenCalledWith(
        { geoImportId: "file-1" },
        expect.objectContaining({ skip: true }),
      );
    });
  });
});
