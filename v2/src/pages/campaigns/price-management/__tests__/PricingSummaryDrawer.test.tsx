import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { PricingSummaryDrawer } from "../PricingSummaryDrawer";

const mockShowSuccess = vi.fn();
const mockShowError = vi.fn();
vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({
    showSuccess: mockShowSuccess,
    showError: mockShowError,
  }),
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string, opts?: { defaultValue?: string }) =>
      opts?.defaultValue ?? key,
  }),
}));

vi.mock("@store", () => ({
  useAppSelector: (selector: (s: unknown) => unknown) =>
    selector({
      profile: {
        profile: {
          activeCompanyId: "company-1",
          memberships: [{ company_id: "company-1" }],
        },
      },
    }),
}));

const mockFetchPriceSummary = vi.fn();
const mockBulkUpdateCustomFees = vi.fn();
const mockUpdateCustomFee = vi.fn();
const mockUpdateInventoryDiscount = vi.fn();
const mockAcceptAllPrices = vi.fn();
const mockFetchSchedulePrices = vi.fn();
vi.mock("@services/inventory/inventorySlice", () => ({
  useLazyGetPriceSummaryQuery: () => [mockFetchPriceSummary],
  useBulkUpdateCustomFeesMutation: () => [mockBulkUpdateCustomFees],
  useUpdateCustomFeeMutation: () => [mockUpdateCustomFee],
  useUpdateInventoryDiscountMutation: () => [mockUpdateInventoryDiscount],
  useAcceptAllPricesMutation: () => [mockAcceptAllPrices],
  useLazyGetCampaignSchedulePricesQuery: () => [mockFetchSchedulePrices],
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
    onClose: () => void;
    footer: React.ReactNode;
    title?: string;
  }) =>
    isOpen ? (
      <div data-testid="modal-drawer">
        {title != null && <h2 data-testid="drawer-title">{title}</h2>}
        <div>{children}</div>
        <div data-testid="footer">{footer}</div>
      </div>
    ) : null,
}));

const summaryData = {
  currentPrice: 1000,
  proposedPrice: 1200,
  mediaCost: { current: 800, proposed: 900 },
  standardFees: { current: 100, proposed: 100 },
  customFees: [
    {
      id: "fee-existing",
      feeName: "Existing Fee",
      type: "Percentage" as const,
      value: "10",
      basedOn: "Base Cost" as const,
      description: "Desc",
      includeInMediaPlan: false,
      companyId: "company-1",
      campaignId: "camp-1",
    },
  ],
};

describe("PricingSummaryDrawer", () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    currency: "MYR",
    data: summaryData,
    campaignId: "camp-1",
    onSuccess: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockBulkUpdateCustomFees.mockReturnValue({
      unwrap: () => Promise.resolve(),
    });
    mockUpdateCustomFee.mockReturnValue({
      unwrap: () => Promise.resolve(),
    });
    mockUpdateInventoryDiscount.mockReturnValue({
      unwrap: () => Promise.resolve(),
    });
    mockAcceptAllPrices.mockReturnValue({
      unwrap: () => Promise.resolve({ success: true }),
    });
    // Two pages, so the id collector's paging loop is exercised
    mockFetchSchedulePrices.mockImplementation(
      ({ params }: { params: { page: number } }) => ({
        unwrap: () =>
          Promise.resolve({
            success: true,
            data: {
              content:
                params.page === 0
                  ? [{ id: "cis-1" }, { id: "cis-2" }]
                  : [{ id: "cis-3" }],
              totalPages: 2,
            },
          }),
      }),
    );
    mockFetchPriceSummary.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: {
            currentPrice: 1000,
            proposedPrice: 1200,
            changeInPrice: 200,
            changeInPercentage: 20,
            mediaCost: 800,
            discountedMediaCost: 900,
            standardFees: 100,
            customFees: [],
            isAllApproved: false,
          },
        }),
    });
  });

  describe("rendering", () => {
    it("does not render when isOpen is false", () => {
      render(<PricingSummaryDrawer {...defaultProps} isOpen={false} />);
      expect(screen.queryByTestId("modal-drawer")).not.toBeInTheDocument();
    });

    it("renders title and pricing comparison when open", () => {
      render(<PricingSummaryDrawer {...defaultProps} />);
      expect(
        screen.getByText("drawers.pricing_summary.title"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("drawers.pricing_summary.current_price"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("drawers.pricing_summary.proposed_price"),
      ).toBeInTheDocument();
    });

    it("renders add fee button and custom fees section", () => {
      render(<PricingSummaryDrawer {...defaultProps} />);
      expect(
        screen.getByRole("button", {
          name: /drawers\.pricing_summary\.add_fee/i,
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByText("drawers.pricing_summary.custom_fees"),
      ).toBeInTheDocument();
    });

    it("renders existing custom fees from data", () => {
      render(<PricingSummaryDrawer {...defaultProps} />);
      expect(screen.getByDisplayValue("Existing Fee")).toBeInTheDocument();
    });

    it("renders campaign approval checkbox", () => {
      render(<PricingSummaryDrawer {...defaultProps} />);
      expect(
        screen.getByLabelText(
          /drawers\.pricing_summary\.campaign_approval_required/i,
        ),
      ).toBeInTheDocument();
    });

    it("renders the approval note explaining prices still reach the media plan", () => {
      render(<PricingSummaryDrawer {...defaultProps} />);
      expect(
        screen.getByText("drawers.pricing_summary.campaign_approval_note"),
      ).toBeInTheDocument();
    });

    it("save button is disabled when approval not checked", () => {
      render(<PricingSummaryDrawer {...defaultProps} />);
      const saveBtn = screen.getByRole("button", {
        name: /drawers\.pricing_summary\.save_changes/i,
      });
      expect(saveBtn).toBeDisabled();
    });
  });

  describe("interactions", () => {
    it("calls onClose when cancel is clicked", async () => {
      const user = userEvent.setup();
      render(<PricingSummaryDrawer {...defaultProps} />);
      await user.click(
        screen.getByRole("button", {
          name: /buttons\.cancel/i,
        }),
      );
      expect(defaultProps.onClose).toHaveBeenCalledTimes(1);
    });

    it("adds new fee when add fee button is clicked", async () => {
      const user = userEvent.setup();
      render(<PricingSummaryDrawer {...defaultProps} />);
      const addBtn = screen.getByRole("button", {
        name: /drawers\.pricing_summary\.add_fee/i,
      });
      await user.click(addBtn);
      const feeInputs = screen.getAllByPlaceholderText(/Enter Fee Name/i);
      expect(feeInputs.length).toBe(2);
    });

    it("enables save when approval is checked and fees have changes", async () => {
      const user = userEvent.setup();
      render(<PricingSummaryDrawer {...defaultProps} />);
      const checkbox = screen.getByRole("checkbox", {
        name: /drawers\.pricing_summary\.campaign_approval_required/i,
      });
      await user.click(checkbox);
      const feeNameInput = screen.getByDisplayValue("Existing Fee");
      await user.clear(feeNameInput);
      await user.type(feeNameInput, "Updated Fee");
      const saveBtn = screen.getByRole("button", {
        name: /drawers\.pricing_summary\.save_changes/i,
      });
      expect(saveBtn).not.toBeDisabled();
    });

    it("save button stays disabled when campaignId is undefined because no fees are editable", () => {
      render(<PricingSummaryDrawer {...defaultProps} campaignId={undefined} />);
      const saveBtn = screen.getByRole("button", {
        name: /drawers\.pricing_summary\.save_changes/i,
      });
      expect(saveBtn).toBeDisabled();
    });
  });

  describe("pending price edits", () => {
    const pendingPriceEdits = {
      "inv-1": {
        newPrice: 2500,
        originalPrice: 2200,
        campaignInventoryScheduleId: "cis-1",
        isInventoryRow: true,
        inventoryId: "inv-1",
        label: "Digital Billboard Alpha",
      },
      "inv-2:sch-1": {
        newPrice: 800,
        originalPrice: 900,
        campaignInventoryScheduleId: "cis-2",
        scheduleId: "sch-1",
        isInventoryRow: false,
        inventoryId: "inv-2",
        label: "Schedule #1",
      },
    };

    it("enables save with only an approved price edit, no fee changes", async () => {
      const user = userEvent.setup();
      render(
        <PricingSummaryDrawer
          {...defaultProps}
          pendingPriceEdits={pendingPriceEdits}
        />,
      );

      const checkbox = screen.getByRole("checkbox", {
        name: /drawers\.pricing_summary\.campaign_approval_required/i,
      });
      await user.click(checkbox);

      expect(
        screen.getByRole("button", {
          name: /drawers\.pricing_summary\.save_changes/i,
        }),
      ).not.toBeDisabled();
    });

    it("shows the proposed total including the staged edits, not the saved one", () => {
      // Server says 1200. Staged: inv-1 2200 -> 2500 (+300),
      // inv-2's schedule 900 -> 800 (-100). Net +200 => 1400.
      render(
        <PricingSummaryDrawer
          {...defaultProps}
          pendingPriceEdits={pendingPriceEdits}
        />,
      );

      expect(screen.getByText(/1,400/)).toBeInTheDocument();
      expect(screen.queryByText(/1,200/)).not.toBeInTheDocument();
    });

    it("shows the saved total untouched when nothing is staged", () => {
      render(<PricingSummaryDrawer {...defaultProps} />);

      expect(screen.getByText(/1,200/)).toBeInTheDocument();
    });

    it("shows how many price changes are pending", () => {
      render(
        <PricingSummaryDrawer
          {...defaultProps}
          pendingPriceEdits={pendingPriceEdits}
        />,
      );

      expect(
        screen.getByText(
          (_, element) =>
            element?.tagName.toLowerCase() === "p" &&
            /2\s+drawers\.pricing_summary\.pending_price_changes/.test(
              element.textContent ?? "",
            ),
        ),
      ).toBeInTheDocument();
    });

    it("persists every staged edit with its own request when saved", async () => {
      const user = userEvent.setup();
      const onPriceEditsSaved = vi.fn();
      render(
        <PricingSummaryDrawer
          {...defaultProps}
          pendingPriceEdits={pendingPriceEdits}
          onPriceEditsSaved={onPriceEditsSaved}
        />,
      );

      await user.click(
        screen.getByRole("checkbox", {
          name: /drawers\.pricing_summary\.campaign_approval_required/i,
        }),
      );
      await user.click(
        screen.getByRole("button", {
          name: /drawers\.pricing_summary\.save_changes/i,
        }),
      );

      expect(mockUpdateInventoryDiscount).toHaveBeenCalledWith({
        campaignInventoryScheduleId: "cis-1",
        data: { proposedPrice: 2500 },
      });
      expect(mockUpdateInventoryDiscount).toHaveBeenCalledWith({
        campaignInventoryScheduleId: "cis-2",
        data: { proposedPrice: 800, scheduleId: "sch-1" },
      });
      await waitFor(() => {
        expect(onPriceEditsSaved).toHaveBeenCalledTimes(1);
      });
      expect(defaultProps.onSuccess).toHaveBeenCalledTimes(1);
    });

    it("accepts every schedule in the campaign, not just the edited ones", async () => {
      const user = userEvent.setup();
      render(
        <PricingSummaryDrawer
          {...defaultProps}
          pendingPriceEdits={pendingPriceEdits}
        />,
      );

      await user.click(
        screen.getByRole("checkbox", {
          name: /drawers\.pricing_summary\.campaign_approval_required/i,
        }),
      );
      await user.click(
        screen.getByRole("button", {
          name: /drawers\.pricing_summary\.save_changes/i,
        }),
      );

      await waitFor(() => {
        // Ids come from paging the whole campaign - cis-3 was never edited
        expect(mockAcceptAllPrices).toHaveBeenCalledWith({
          campaignId: "camp-1",
          data: { campaignInventorySchedulesIds: ["cis-1", "cis-2", "cis-3"] },
        });
      });
    });

    it("accepts only after every price has been persisted", async () => {
      const user = userEvent.setup();
      const callOrder: string[] = [];
      mockUpdateInventoryDiscount.mockImplementation(() => ({
        unwrap: () => {
          callOrder.push("update-discount");
          return Promise.resolve();
        },
      }));
      mockAcceptAllPrices.mockImplementation(() => ({
        unwrap: () => {
          callOrder.push("accept");
          return Promise.resolve({ success: true });
        },
      }));

      render(
        <PricingSummaryDrawer
          {...defaultProps}
          pendingPriceEdits={pendingPriceEdits}
        />,
      );

      await user.click(
        screen.getByRole("checkbox", {
          name: /drawers\.pricing_summary\.campaign_approval_required/i,
        }),
      );
      await user.click(
        screen.getByRole("button", {
          name: /drawers\.pricing_summary\.save_changes/i,
        }),
      );

      await waitFor(() => {
        expect(callOrder).toContain("accept");
      });
      // Accept is last - both prices land before anything is agreed
      expect(callOrder).toEqual([
        "update-discount",
        "update-discount",
        "accept",
      ]);
    });

    it("does not accept when only custom fees changed", async () => {
      const user = userEvent.setup();
      render(<PricingSummaryDrawer {...defaultProps} />);

      await user.click(
        screen.getByRole("checkbox", {
          name: /drawers\.pricing_summary\.campaign_approval_required/i,
        }),
      );
      const feeNameInput = screen.getByDisplayValue("Existing Fee");
      await user.clear(feeNameInput);
      await user.type(feeNameInput, "Updated Fee");
      await user.click(
        screen.getByRole("button", {
          name: /drawers\.pricing_summary\.save_changes/i,
        }),
      );

      await waitFor(() => {
        expect(mockBulkUpdateCustomFees).toHaveBeenCalled();
      });
      expect(mockAcceptAllPrices).not.toHaveBeenCalled();
    });

    it("still reports a successful save when only the accept step fails", async () => {
      const user = userEvent.setup();
      const onPriceEditsSaved = vi.fn();
      const consoleSpy = vi
        .spyOn(console, "error")
        .mockImplementation(() => {});
      mockAcceptAllPrices.mockReturnValue({
        unwrap: () => Promise.reject(new Error("accept blew up")),
      });

      render(
        <PricingSummaryDrawer
          {...defaultProps}
          pendingPriceEdits={pendingPriceEdits}
          onPriceEditsSaved={onPriceEditsSaved}
        />,
      );

      await user.click(
        screen.getByRole("checkbox", {
          name: /drawers\.pricing_summary\.campaign_approval_required/i,
        }),
      );
      await user.click(
        screen.getByRole("button", {
          name: /drawers\.pricing_summary\.save_changes/i,
        }),
      );

      // The prices did persist, so this is a success - no error surfaced
      await waitFor(() => {
        expect(onPriceEditsSaved).toHaveBeenCalledTimes(1);
      });
      expect(mockShowSuccess).toHaveBeenCalled();
      expect(mockShowError).not.toHaveBeenCalled();
      expect(defaultProps.onClose).toHaveBeenCalled();

      consoleSpy.mockRestore();
    });

    it("does not clear the staged edits when a save request fails", async () => {
      const user = userEvent.setup();
      const onPriceEditsSaved = vi.fn();
      mockUpdateInventoryDiscount.mockReturnValue({
        unwrap: () => Promise.reject({ data: { error: { message: "Nope" } } }),
      });
      render(
        <PricingSummaryDrawer
          {...defaultProps}
          pendingPriceEdits={pendingPriceEdits}
          onPriceEditsSaved={onPriceEditsSaved}
        />,
      );

      await user.click(
        screen.getByRole("checkbox", {
          name: /drawers\.pricing_summary\.campaign_approval_required/i,
        }),
      );
      await user.click(
        screen.getByRole("button", {
          name: /drawers\.pricing_summary\.save_changes/i,
        }),
      );

      await waitFor(() => {
        expect(mockShowError).toHaveBeenCalledWith("Nope");
      });
      expect(onPriceEditsSaved).not.toHaveBeenCalled();
    });

    it("closes on cancel without asking or discarding the staged edits", async () => {
      const user = userEvent.setup();
      const onPriceEditsSaved = vi.fn();
      render(
        <PricingSummaryDrawer
          {...defaultProps}
          pendingPriceEdits={pendingPriceEdits}
          onPriceEditsSaved={onPriceEditsSaved}
        />,
      );

      await user.click(
        screen.getByRole("button", { name: /buttons\.cancel/i }),
      );

      // The discard-confirmation prompt now lives on the price-management
      // page's leave action, not on the drawer's own close - and closing
      // here must not touch the staged price edits at all, so the table
      // keeps showing the edited value.
      expect(
        screen.queryByText("drawers.pricing_summary.discard_changes_title"),
      ).not.toBeInTheDocument();
      expect(onPriceEditsSaved).not.toHaveBeenCalled();
      expect(defaultProps.onClose).toHaveBeenCalledTimes(1);
      expect(mockUpdateInventoryDiscount).not.toHaveBeenCalled();
    });

    it("closes without asking when there is nothing staged", async () => {
      const user = userEvent.setup();
      render(<PricingSummaryDrawer {...defaultProps} />);

      await user.click(
        screen.getByRole("button", { name: /buttons\.cancel/i }),
      );

      expect(
        screen.queryByText("drawers.pricing_summary.discard_changes_title"),
      ).not.toBeInTheDocument();
      expect(defaultProps.onClose).toHaveBeenCalledTimes(1);
    });
  });

  describe("no custom fees state", () => {
    it("shows no custom fees message when customFees is empty", () => {
      render(
        <PricingSummaryDrawer
          {...defaultProps}
          data={{
            ...summaryData,
            customFees: [],
          }}
        />,
      );
      expect(
        screen.getByText("drawers.pricing_summary.no_custom_fees"),
      ).toBeInTheDocument();
    });
  });
});
