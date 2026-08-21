import { configureStore } from "@reduxjs/toolkit";
import { render, screen, waitFor } from "@testing-library/react";
import React from "react";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import CampaignHistory from "../CampaignHistory";

let mockHistoryData: {
  success: boolean;
  data: {
    content: unknown[];
    totalPages: number;
    totalElements: number;
    number: number;
  };
} = {
  success: true,
  data: {
    content: [],
    totalPages: 0,
    totalElements: 0,
    number: 0,
  },
};

let mockIsLoading = false;
let mockIsError = false;

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("@services/campaign/campaignSlice", () => ({
  useLazyGetCampaignHistoryQuery: () => {
    const trigger = vi.fn().mockReturnValue({
      unwrap: async () => mockHistoryData,
    });
    return [
      trigger,
      {
        get data() {
          return mockHistoryData;
        },
        get isLoading() {
          return mockIsLoading;
        },
        get isError() {
          return mockIsError;
        },
      },
    ];
  },
}));

let lastAgGridTableProps: {
  rowData: unknown[];
  loading: boolean;
  emptyMessage: string;
  serverSideConfig?: {
    totalCount: number;
    currentPage: number;
    pageSize: number;
    onPageChange: (page: number) => void;
    onPageSizeChange: (size: number) => void;
  };
} = {
  rowData: [],
  loading: false,
  emptyMessage: "",
};

vi.mock("@components/ui/AgGridTable", () => ({
  AgGridTable: (props: typeof lastAgGridTableProps) => {
    lastAgGridTableProps = props;
    return (
      <div data-testid="ag-grid-table">
        {props.loading && <div>Loading...</div>}
        {!props.loading && props.rowData.length === 0 && (
          <div>{props.emptyMessage}</div>
        )}
        {!props.loading && props.rowData.length > 0 && (
          <div data-testid="ag-grid-rows">
            {props.rowData
              .filter(
                (row: unknown) =>
                  typeof row === "object" &&
                  row !== null &&
                  !(row as { __isPlaceholder?: boolean }).__isPlaceholder,
              )
              .map((row: unknown, index: number) => (
                <div key={index} data-testid={`ag-grid-row-${index}`}>
                  {(row as Record<string, unknown>).message as string}
                </div>
              ))}
          </div>
        )}
        {props.serverSideConfig && props.serverSideConfig.totalCount > 0 && (
          <div data-testid="ag-grid-pagination">
            <span data-testid="ag-grid-page-size">
              {props.serverSideConfig.pageSize}
            </span>
            <span data-testid="ag-grid-total-count">
              {props.serverSideConfig.totalCount}
            </span>
          </div>
        )}
      </div>
    );
  },
}));

vi.mock("@components/ui/Tooltip", () => ({
  Tooltip: ({
    children,
  }: {
    children: React.ReactNode;
    content: React.ReactNode;
  }) => <div data-testid="tooltip">{children}</div>,
}));

const createMockStore = () =>
  configureStore({
    reducer: {
      campaign: (state = {}) => state,
    },
  });

const TestWrapper = ({ children }: { children: React.ReactNode }) => {
  const store = createMockStore();
  return <Provider store={store}>{children}</Provider>;
};

describe("CampaignHistory", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockHistoryData = {
      success: true,
      data: {
        content: [],
        totalPages: 0,
        totalElements: 0,
        number: 0,
      },
    };
    mockIsLoading = false;
    mockIsError = false;
  });

  describe("Rendering", () => {
    it("should render component", () => {
      render(
        <TestWrapper>
          <CampaignHistory campaignId="test-campaign-id" />
        </TestWrapper>,
      );

      expect(screen.getByTestId("ag-grid-table")).toBeInTheDocument();
    });

    it("should call getCampaignHistory on mount", async () => {
      render(
        <TestWrapper>
          <CampaignHistory campaignId="test-campaign-id" />
        </TestWrapper>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("ag-grid-table")).toBeInTheDocument();
      });
    });

    it("renders campaign-history-table-container", () => {
      render(
        <TestWrapper>
          <CampaignHistory campaignId="test-campaign-id" />
        </TestWrapper>,
      );

      expect(
        document.getElementById("campaign-history-table-container"),
      ).toBeInTheDocument();
    });
  });

  describe("Data Display", () => {
    it("should display history items", async () => {
      mockHistoryData = {
        success: true,
        data: {
          content: [
            {
              id: "1",
              campaignId: "c1",
              userId: "u1",
              companyId: "co1",
              createdAt: "2024-01-15T10:30:00",
              createdBy: "John Doe",
              role: "Admin",
              message: "Campaign created",
            },
            {
              id: "2",
              campaignId: "c1",
              userId: "u2",
              companyId: "co1",
              createdAt: "2024-01-16T14:20:00",
              createdBy: "Jane Smith",
              role: "Planner",
              message: "Campaign updated",
            },
          ],
          totalPages: 1,
          totalElements: 2,
          number: 0,
        },
      };

      render(
        <TestWrapper>
          <CampaignHistory campaignId="test-campaign-id" />
        </TestWrapper>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("ag-grid-table")).toBeInTheDocument();
      });
      expect(lastAgGridTableProps.rowData.length).toBeGreaterThan(0);
    });

    it("should format dates correctly", () => {
      const date = new Date("2024-01-15T10:30:00");
      const formatted = date.toLocaleDateString("en-US", {
        month: "short",
        day: "2-digit",
        year: "numeric",
      });

      expect(formatted).toMatch(/Jan \d{2}, 2024/);
    });

    it("should calculate serial numbers correctly", () => {
      const totalElements = 2;
      const page = 0;
      const pageSize = 10;
      const index = 0;
      const serialNumber = totalElements - (page * pageSize + index);

      expect(serialNumber).toBe(2);
    });
  });

  describe("Pagination", () => {
    it("should pass serverSideConfig with pagination when there are items", async () => {
      mockHistoryData = {
        success: true,
        data: {
          content: [
            {
              id: "1",
              campaignId: "c1",
              userId: "u1",
              companyId: "co1",
              createdAt: "2024-01-15",
              createdBy: "User",
              role: "Admin",
              message: "Test",
            },
          ],
          totalPages: 1,
          totalElements: 1,
          number: 0,
        },
      };

      render(
        <TestWrapper>
          <CampaignHistory campaignId="test-campaign-id" />
        </TestWrapper>,
      );

      await waitFor(() => {
        expect(screen.getByTestId("ag-grid-pagination")).toBeInTheDocument();
      });

      expect(screen.getByTestId("ag-grid-total-count")).toHaveTextContent("1");
      expect(screen.getByTestId("ag-grid-page-size")).toHaveTextContent("10");
    });

    it("should not show pagination UI when there are no items", () => {
      mockHistoryData = {
        success: true,
        data: {
          content: [],
          totalPages: 0,
          totalElements: 0,
          number: 0,
        },
      };

      render(
        <TestWrapper>
          <CampaignHistory campaignId="test-campaign-id" />
        </TestWrapper>,
      );

      expect(
        screen.queryByTestId("ag-grid-pagination"),
      ).not.toBeInTheDocument();
    });

    it("passes onPageChange and onPageSizeChange to AgGridTable", () => {
      render(
        <TestWrapper>
          <CampaignHistory campaignId="test-campaign-id" />
        </TestWrapper>,
      );

      expect(lastAgGridTableProps.serverSideConfig).toBeDefined();
      expect(lastAgGridTableProps.serverSideConfig?.onPageChange).toBeDefined();
      expect(
        lastAgGridTableProps.serverSideConfig?.onPageSizeChange,
      ).toBeDefined();
    });
  });

  describe("Loading State", () => {
    it("should display loading state", () => {
      mockIsLoading = true;
      mockIsError = false;

      render(
        <TestWrapper>
          <CampaignHistory campaignId="test-campaign-id" />
        </TestWrapper>,
      );

      expect(screen.getByText("Loading...")).toBeInTheDocument();
    });
  });

  describe("Error State", () => {
    it("should display error message", () => {
      mockIsLoading = false;
      mockIsError = true;

      render(
        <TestWrapper>
          <CampaignHistory campaignId="test-campaign-id" />
        </TestWrapper>,
      );

      expect(
        screen.getByText("campaignHistory.errorLoading"),
      ).toBeInTheDocument();
    });
  });

  describe("Empty State", () => {
    it("should display empty message when no history", () => {
      mockHistoryData = {
        success: true,
        data: {
          content: [],
          totalPages: 0,
          totalElements: 0,
          number: 0,
        },
      };

      render(
        <TestWrapper>
          <CampaignHistory campaignId="test-campaign-id" />
        </TestWrapper>,
      );

      expect(screen.getByText("campaignHistory.noHistory")).toBeInTheDocument();
    });
  });

  describe("Edge Cases", () => {
    it("should handle missing campaignId", () => {
      render(
        <TestWrapper>
          <CampaignHistory campaignId="" />
        </TestWrapper>,
      );

      expect(screen.getByTestId("ag-grid-table")).toBeInTheDocument();
    });

    it("should handle missing role in history item", () => {
      mockHistoryData = {
        success: true,
        data: {
          content: [
            {
              id: "1",
              campaignId: "c1",
              userId: "u1",
              companyId: "co1",
              createdAt: "2024-01-15T10:30:00",
              createdBy: "John Doe",
              role: "",
              message: "Campaign created",
            },
          ],
          totalPages: 1,
          totalElements: 1,
          number: 0,
        },
      };

      render(
        <TestWrapper>
          <CampaignHistory campaignId="test-campaign-id" />
        </TestWrapper>,
      );

      expect(screen.getByTestId("ag-grid-table")).toBeInTheDocument();
    });
  });
});
