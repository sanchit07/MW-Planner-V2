import "@testing-library/jest-dom";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { PriceHistoryDrawer } from "../PriceHistoryDrawer";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string, opts?: { defaultValue?: string }) =>
      opts?.defaultValue ?? key,
  }),
}));

const mockFetchSchedulePrices = vi.fn();
const mockFetchPriceSummary = vi.fn();
const mockFetchPriceHistory = vi.fn();

vi.mock("@services/inventory/inventorySlice", () => ({
  useLazyGetCampaignSchedulePricesQuery: () => [
    mockFetchSchedulePrices,
    { data: mockPriceData, isFetching: false },
  ],
  useLazyGetPriceSummaryQuery: () => [
    mockFetchPriceSummary,
    { data: mockSummaryData },
  ],
  useLazyGetPriceHistoryQuery: () => [mockFetchPriceHistory],
}));

vi.mock("@components/ui/ModalDrawer", () => ({
  ModalDrawer: ({
    children,
    isOpen,
    footer,
    title,
  }: {
    children: React.ReactNode;
    isOpen: boolean;
    footer: React.ReactNode;
    title?: string;
  }) =>
    isOpen ? (
      <div data-testid="modal-drawer">
        <h2 data-testid="drawer-title">{title}</h2>
        <div>{children}</div>
        <div data-testid="footer">{footer}</div>
      </div>
    ) : null,
}));

let mockPriceData: unknown;
let mockSummaryData: unknown;

const historyRows = [
  {
    oldPrice: 1860014.88,
    newPrice: 1799.99,
    action: "COUNTERED",
    userId: "u-1",
    companyId: "c-1",
    createdBy: "Suraj Prakash",
    role: "Agency",
    createdAt: "2026-07-29 04:03:55",
  },
  {
    oldPrice: 1860014.88,
    newPrice: 1860014.88,
    action: "RATE_CARD",
    userId: "u-1",
    companyId: "c-1",
    createdBy: "Suraj Prakash",
    role: "Agency",
    createdAt: "2026-07-28 09:23:27",
  },
];

const defaultProps = {
  isOpen: true,
  onClose: vi.fn(),
  campaignId: "camp-1",
  currency: "USD",
};

describe("PriceHistoryDrawer", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPriceData = {
      data: {
        content: [
          {
            inventoryId: "inv-1",
            id: "cis-1",
            inventoryName: "Digital Billboard Alpha",
            currentRate: 2200,
            proposedRate: 1800,
          },
          {
            inventoryId: "inv-2",
            id: "cis-2",
            inventoryName: "Static Billboard Beta",
            currentRate: 1100,
            proposedRate: 1100,
          },
        ],
        totalElements: 2,
        totalPages: 1,
      },
    };
    mockSummaryData = { data: { currentPrice: 3300, proposedPrice: 2900 } };
    mockFetchSchedulePrices.mockReturnValue({
      unwrap: () => Promise.resolve(),
    });
    mockFetchPriceSummary.mockReturnValue({ unwrap: () => Promise.resolve() });
    mockFetchPriceHistory.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: {
            content: historyRows,
            totalElements: 2,
            totalPages: 1,
          },
        }),
    });
  });

  it("does not render when closed", () => {
    render(<PriceHistoryDrawer {...defaultProps} isOpen={false} />);
    expect(screen.queryByTestId("modal-drawer")).not.toBeInTheDocument();
  });

  it("renders the Price History title", () => {
    render(<PriceHistoryDrawer {...defaultProps} />);
    expect(screen.getByTestId("drawer-title")).toHaveTextContent(
      "drawers.price_history.title",
    );
  });

  it("shows the current and proposed totals without a change line", () => {
    render(<PriceHistoryDrawer {...defaultProps} />);

    expect(screen.getByText(/3,300/)).toBeInTheDocument();
    expect(screen.getByText(/2,900/)).toBeInTheDocument();
    expect(
      screen.queryByText("drawers.pricing_summary.change_in_price"),
    ).not.toBeInTheDocument();
  });

  it("lists every inventory returned for the campaign", () => {
    render(<PriceHistoryDrawer {...defaultProps} />);

    expect(screen.getByText("Digital Billboard Alpha")).toBeInTheDocument();
    expect(screen.getByText("Static Billboard Beta")).toBeInTheDocument();
  });

  it("has only a Cancel action in the footer", () => {
    render(<PriceHistoryDrawer {...defaultProps} />);

    const footer = screen.getByTestId("footer");
    expect(footer).toHaveTextContent("buttons.cancel");
    expect(footer.querySelectorAll("button")).toHaveLength(1);
  });

  it("closes when Cancel is clicked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<PriceHistoryDrawer {...defaultProps} onClose={onClose} />);

    await user.click(screen.getByRole("button", { name: /buttons\.cancel/i }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("does not request any history until a row is expanded", () => {
    render(<PriceHistoryDrawer {...defaultProps} />);

    expect(mockFetchPriceHistory).not.toHaveBeenCalled();
  });

  it("loads that inventory's history on expand, keyed on its composite id", async () => {
    const user = userEvent.setup();
    render(<PriceHistoryDrawer {...defaultProps} />);

    await user.click(screen.getByTestId("button-expand-inv-1"));

    await waitFor(() => {
      expect(mockFetchPriceHistory).toHaveBeenCalledWith({
        campaignInventoryScheduleId: "cis-1",
        params: {
          campaignInventoryScheduleId: "cis-1",
          page: 0,
          size: 10,
        },
      });
    });
  });

  it("renders the history rows with user, role and both prices", async () => {
    const user = userEvent.setup();
    render(<PriceHistoryDrawer {...defaultProps} />);

    await user.click(screen.getByTestId("button-expand-inv-1"));

    await waitFor(() => {
      expect(screen.getAllByText("Suraj Prakash")).toHaveLength(2);
    });
    expect(screen.getAllByText("Agency")).toHaveLength(2);
    expect(
      screen.getByText("drawers.add_proposal_price.actions.counter"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("drawers.add_proposal_price.actions.rate_card"),
    ).toBeInTheDocument();
  });

  it("numbers history newest-first, counting down", async () => {
    const user = userEvent.setup();
    render(<PriceHistoryDrawer {...defaultProps} />);

    await user.click(screen.getByTestId("button-expand-inv-1"));

    await waitFor(() => {
      expect(screen.getByText("02")).toBeInTheDocument();
    });
    expect(screen.getByText("01")).toBeInTheDocument();
  });

  it("does not refetch history when a row is collapsed and reopened", async () => {
    const user = userEvent.setup();
    render(<PriceHistoryDrawer {...defaultProps} />);

    await user.click(screen.getByTestId("button-expand-inv-1"));
    await waitFor(() => {
      expect(mockFetchPriceHistory).toHaveBeenCalledTimes(1);
    });

    await user.click(screen.getByTestId("button-expand-inv-1"));
    await user.click(screen.getByTestId("button-expand-inv-1"));

    expect(mockFetchPriceHistory).toHaveBeenCalledTimes(1);
  });

  it("keeps only one inventory expanded at a time", async () => {
    const user = userEvent.setup();
    render(<PriceHistoryDrawer {...defaultProps} />);

    await user.click(screen.getByTestId("button-expand-inv-1"));
    await waitFor(() => {
      expect(screen.getAllByText("Suraj Prakash")).toHaveLength(2);
    });

    // Opening the second inventory collapses the first
    await user.click(screen.getByTestId("button-expand-inv-2"));

    await waitFor(() => {
      expect(mockFetchPriceHistory).toHaveBeenCalledWith(
        expect.objectContaining({ campaignInventoryScheduleId: "cis-2" }),
      );
    });

    // The outgoing panel stays mounted while it animates out, so wait for it
    // to leave - then only one history table remains (2 rows, not 4).
    await waitFor(() => {
      expect(screen.getAllByText("Suraj Prakash")).toHaveLength(2);
    });
  });

  it("collapses the open inventory when its own chevron is clicked", async () => {
    const user = userEvent.setup();
    render(<PriceHistoryDrawer {...defaultProps} />);

    await user.click(screen.getByTestId("button-expand-inv-1"));
    await waitFor(() => {
      expect(screen.getAllByText("Suraj Prakash")).toHaveLength(2);
    });

    await user.click(screen.getByTestId("button-expand-inv-1"));

    // Unmounts once the collapse animation finishes
    await waitFor(() => {
      expect(screen.queryByText("Suraj Prakash")).not.toBeInTheDocument();
    });
  });

  it("fetches each inventory's history separately", async () => {
    const user = userEvent.setup();
    render(<PriceHistoryDrawer {...defaultProps} />);

    await user.click(screen.getByTestId("button-expand-inv-1"));
    await waitFor(() => {
      expect(mockFetchPriceHistory).toHaveBeenCalledTimes(1);
    });
    await user.click(screen.getByTestId("button-expand-inv-2"));

    await waitFor(() => {
      expect(mockFetchPriceHistory).toHaveBeenCalledWith(
        expect.objectContaining({ campaignInventoryScheduleId: "cis-2" }),
      );
    });
  });

  it("shows an empty message when an inventory has no history", async () => {
    const user = userEvent.setup();
    mockFetchPriceHistory.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: { content: [], totalElements: 0, totalPages: 0 },
        }),
    });
    render(<PriceHistoryDrawer {...defaultProps} />);

    await user.click(screen.getByTestId("button-expand-inv-1"));

    await waitFor(() => {
      expect(
        screen.getByText("drawers.add_proposal_price.no_history"),
      ).toBeInTheDocument();
    });
  });

  it("shows the empty message when the history request fails", async () => {
    const user = userEvent.setup();
    mockFetchPriceHistory.mockReturnValue({
      unwrap: () => Promise.reject(new Error("boom")),
    });
    render(<PriceHistoryDrawer {...defaultProps} />);

    await user.click(screen.getByTestId("button-expand-inv-1"));

    await waitFor(() => {
      expect(
        screen.getByText("drawers.add_proposal_price.no_history"),
      ).toBeInTheDocument();
    });
  });
});
