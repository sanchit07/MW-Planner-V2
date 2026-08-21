import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { ViewFileInventoryDrawer } from "../ViewFileInventoryDrawer";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const mockUseGetInventoryByFileIdQuery = vi.fn();

vi.mock("@services/inventory/inventorySlice", () => ({
  useGetInventoryByFileIdQuery: (...args: unknown[]) =>
    mockUseGetInventoryByFileIdQuery(...args),
}));

vi.mock("@components/ui/Mapbox", () => ({
  default: function MockMapBox() {
    return <div data-testid="mapbox-wrapper">Map</div>;
  },
}));

vi.mock("@components/ui/AgGridTable/AgGridTable", () => ({
  AgGridTable: (props: Record<string, unknown>) => {
    const rowData = (props.rowData as Array<Record<string, unknown>>) ?? [];
    const loading = props.loading as boolean;
    const emptyMessage = (props.emptyMessage as string) ?? "";
    const columnDefs =
      (props.columnDefs as Array<{ headerName?: string }>) ?? [];
    if (loading) {
      return (
        <div role="table" aria-label="inventory">
          <div>Loading</div>
        </div>
      );
    }
    if (rowData.length === 0) {
      return (
        <div role="table" aria-label="inventory">
          <div>{emptyMessage}</div>
        </div>
      );
    }
    return (
      <div role="table" aria-label="inventory">
        <div role="rowgroup">
          {columnDefs.map((col, i) => (
            <span key={i} role="columnheader">
              {col.headerName}
            </span>
          ))}
        </div>
        {rowData.map((row, idx) => (
          <div key={String(row.id ?? idx)} role="row">
            <span>{idx + 1}</span>
            <span>{String(row.inventoryName ?? "")}</span>
            <span>{String(row.referenceId ?? "")}</span>
            <span>{String(row.type ?? "")}</span>
            <span>
              {row.location &&
              typeof row.location === "object" &&
              "latitude" in row.location
                ? Number(
                    (row.location as { latitude?: number }).latitude,
                  ).toFixed(6)
                : "-"}
            </span>
            <span>
              {row.location &&
              typeof row.location === "object" &&
              "longitude" in row.location
                ? Number(
                    (row.location as { longitude?: number }).longitude,
                  ).toFixed(6)
                : "-"}
            </span>
          </div>
        ))}
      </div>
    );
  },
}));

describe("ViewFileInventoryDrawer", () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    fileId: "file-1",
    fileName: "inventory.csv",
    campaignId: "campaign-1",
  };

  const mockInventoryContent = [
    {
      id: "inv-1",
      inventoryName: "Board A",
      referenceId: "REF-1",
      type: "Digital",
      category: "Retail",
      location: {
        address: "123 Main St",
        country: "US",
        state: "NY",
        latitude: 40.7128,
        longitude: -74.006,
      },
      thumbnail: "",
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseGetInventoryByFileIdQuery.mockReturnValue({
      data: {
        data: {
          content: mockInventoryContent,
          totalPages: 1,
          totalElements: 1,
        },
      },
      isLoading: false,
      isFetching: false,
      refetch: vi.fn(),
    } as ReturnType<typeof mockUseGetInventoryByFileIdQuery>);
  });

  describe("rendering", () => {
    it("renders nothing when isOpen is false", () => {
      render(<ViewFileInventoryDrawer {...defaultProps} isOpen={false} />);
      expect(screen.queryByText("inventory.csv")).not.toBeInTheDocument();
    });

    it("renders drawer with fileName as title when open", () => {
      render(<ViewFileInventoryDrawer {...defaultProps} />);
      expect(screen.getByText("inventory.csv")).toBeInTheDocument();
    });

    it("renders table with inventory data when query returns data", () => {
      render(<ViewFileInventoryDrawer {...defaultProps} />);
      expect(screen.getByText("Board A")).toBeInTheDocument();
      expect(screen.getByText("REF-1")).toBeInTheDocument();
      expect(screen.getByText("Digital")).toBeInTheDocument();
    });

    it("renders empty message when no inventory data", () => {
      mockUseGetInventoryByFileIdQuery.mockReturnValue({
        data: { data: { content: [], totalPages: 0, totalElements: 0 } },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof mockUseGetInventoryByFileIdQuery>);
      render(<ViewFileInventoryDrawer {...defaultProps} />);
      expect(
        screen.getByText("viewFileInventoryDrawer.emptyMessage"),
      ).toBeInTheDocument();
    });

    it("renders map when inventory has valid coordinates", () => {
      render(<ViewFileInventoryDrawer {...defaultProps} />);
      expect(screen.getByTestId("mapbox-wrapper")).toBeInTheDocument();
    });

    it("skips query when fileId or campaignId is missing", () => {
      mockUseGetInventoryByFileIdQuery.mockClear();
      render(
        <ViewFileInventoryDrawer
          {...defaultProps}
          fileId=""
          campaignId="campaign-1"
        />,
      );
      expect(mockUseGetInventoryByFileIdQuery).toHaveBeenCalled();
      const call = mockUseGetInventoryByFileIdQuery.mock.calls[0];
      expect(call[1]).toEqual(expect.objectContaining({ skip: true }));
    });
  });

  describe("pagination", () => {
    it("renders table and pagination when data is loaded", () => {
      render(<ViewFileInventoryDrawer {...defaultProps} />);
      expect(screen.getByRole("table")).toBeInTheDocument();
      expect(screen.getAllByText("1").length).toBeGreaterThanOrEqual(1);
    });
  });

  describe("back button", () => {
    it("calls onBack when back button is clicked when onBack is provided", async () => {
      const onBack = vi.fn();
      const user = userEvent.setup();
      render(<ViewFileInventoryDrawer {...defaultProps} onBack={onBack} />);
      const header = document.getElementById("modal-drawer-header");
      const backButton = header?.querySelector("button");
      expect(backButton).toBeInTheDocument();
      await user.click(backButton!);
      expect(onBack).toHaveBeenCalled();
    });

    it("calls onClose when back button is clicked when onBack is not provided", async () => {
      const user = userEvent.setup();
      render(<ViewFileInventoryDrawer {...defaultProps} />);
      const header = document.getElementById("modal-drawer-header");
      const backButton = header?.querySelector("button");
      expect(backButton).toBeInTheDocument();
      await user.click(backButton!);
      expect(defaultProps.onClose).toHaveBeenCalled();
    });
  });

  describe("loading state", () => {
    it("shows loading state when isLoading is true", () => {
      mockUseGetInventoryByFileIdQuery.mockReturnValue({
        data: undefined,
        isLoading: true,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof mockUseGetInventoryByFileIdQuery>);
      render(<ViewFileInventoryDrawer {...defaultProps} />);
      expect(screen.getByRole("table")).toBeInTheDocument();
    });
  });

  describe("table columns", () => {
    it("renders S.No, Inventory Name, Reference Id, Latitude, Longitude, Inventory Type", () => {
      render(<ViewFileInventoryDrawer {...defaultProps} />);
      expect(
        screen.getByText("viewFileInventoryDrawer.columns.srNo"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("viewFileInventoryDrawer.columns.inventoryName"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("viewFileInventoryDrawer.columns.referenceId"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("viewFileInventoryDrawer.columns.latitude"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("viewFileInventoryDrawer.columns.longitude"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("viewFileInventoryDrawer.columns.inventoryType"),
      ).toBeInTheDocument();
    });

    it("displays latitude and longitude with 6 decimals or dash", () => {
      render(<ViewFileInventoryDrawer {...defaultProps} />);
      expect(screen.getByText("40.712800")).toBeInTheDocument();
      expect(screen.getByText("-74.006000")).toBeInTheDocument();
    });
  });
});
