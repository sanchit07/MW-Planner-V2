import { setUserProfile } from "@services/user/userSlice";
import type { UserProfile } from "@services/user/userSlice";
import { store } from "@store";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { Provider } from "react-redux";
import type {
  PriceSummaryResponse,
  PriceSummaryCustomFee,
} from "src/types/inventory.types";
import { describe, it, expect, vi, beforeEach } from "vitest";

import CostBreakdown from "../CostBreakdown";

const editableUserProfile = {
  activeCompanyId: "company-1",
} as UserProfile;

function setupEditableUser() {
  store.dispatch(setUserProfile({ success: true, data: editableUserProfile }));
}

function renderWithStore(ui: React.ReactElement) {
  return render(ui, {
    wrapper: ({ children }) => <Provider store={store}>{children}</Provider>,
  });
}

vi.mock("@utils/currency", () => ({
  formatCurrencyWithLocale: (amount: number | undefined, currency: string) => {
    if (amount === undefined || amount === null) return "--";
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: currency || "USD",
    }).format(amount);
  },
}));

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
    t: (key: string, options?: { defaultValue?: string }) =>
      options?.defaultValue ?? key,
  }),
}));

const mockBulkUpdateCustomFees = vi.fn().mockReturnValue({
  unwrap: () => Promise.resolve(),
});
const mockFetchPriceSummary = vi.fn().mockReturnValue({
  unwrap: () => Promise.resolve(),
});
const mockUpdateCustomFee = vi.fn().mockReturnValue({
  unwrap: () => Promise.resolve(),
});

vi.mock("@services/inventory/inventorySlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/inventory/inventorySlice")>();
  return {
    ...actual,
    useBulkUpdateCustomFeesMutation: () => [
      mockBulkUpdateCustomFees,
      { isLoading: false },
    ],
    useLazyGetPriceSummaryQuery: () => [
      mockFetchPriceSummary,
      { isLoading: false },
    ],
    useUpdateCustomFeeMutation: () => [
      mockUpdateCustomFee,
      { isLoading: false },
    ],
  };
});

vi.mock("../AddCustomFeeDrawer", () => ({
  AddCustomFeeDrawer: ({
    isOpen,
    onClose,
    onSave,
    campaignId,
    initialFee,
  }: {
    isOpen: boolean;
    onClose: () => void;
    onSave?: (
      customFees: unknown[],
      requiresApproval: boolean,
      campaignIdParam?: string,
    ) => Promise<void>;
    campaignId?: string;
    initialFee: {
      id: string;
      name: string;
      type: string;
      value: number;
      description: string;
      isIncludeInMediaPlan: boolean;
      entityName: string;
      entityReferenceId: string;
    } | null;
  }) =>
    isOpen ? (
      <div data-testid="add-custom-fee-drawer">
        <span>Add Custom Fee Drawer</span>
        <button type="button" onClick={onClose}>
          Close drawer
        </button>
        <button
          type="button"
          data-testid="drawer-save"
          onClick={() =>
            onSave?.(
              initialFee
                ? [
                    {
                      id: initialFee.id,
                      feeName: initialFee.name,
                      type:
                        initialFee.type === "PERCENTAGE"
                          ? "Percentage"
                          : "Fixed",
                      value: String(initialFee.value),
                      basedOn: "Base Cost",
                      description: initialFee.description,
                      includeInMediaPlan: initialFee.isIncludeInMediaPlan,
                      entityName: initialFee.entityName,
                      entityReferenceId: initialFee.entityReferenceId,
                    },
                  ]
                : [
                    {
                      id: "fee-new",
                      feeName: "New Fee",
                      type: "Percentage",
                      value: "5",
                      basedOn: "Base Cost",
                      description: "",
                      includeInMediaPlan: false,
                      entityName: "CAMPAIGN",
                      entityReferenceId: campaignId,
                      campaignId: campaignId ?? "campaign-1",
                      companyId: "company-1",
                    },
                  ],
              false,
              campaignId,
            )
          }
        >
          Save
        </button>
      </div>
    ) : null,
}));

function createMockPriceSummaryResponse(
  overrides: Partial<PriceSummaryResponse> = {},
): PriceSummaryResponse {
  return {
    currentPrice: 0,
    proposedPrice: 107000,
    changeInPrice: 0,
    changeInPercentage: 0,
    mediaCost: 100000,
    discountedMediaCost: 100000,
    standardFees: 0,
    customFees: [],
    isAllApproved: true,
    ...overrides,
  };
}

function createMockCustomFee(
  overrides: Partial<PriceSummaryCustomFee> = {},
): PriceSummaryCustomFee {
  return {
    id: "fee-1",
    name: "fee-1",
    description: "One-time setup",
    type: "PERCENTAGE",
    value: 2,
    basedOn: "BASE_COST",
    isIncludeInMediaPlan: false,
    isActive: true,
    companyId: "company-1",
    campaignId: "campaign-1",
    createdAt: "2023-01-01T00:00:00Z",
    updatedAt: "2023-01-01T00:00:00Z",
    effectiveCustomFee: 2000,
    ...overrides,
  };
}

describe("CostBreakdown", () => {
  const mockCostBreakdownData = createMockPriceSummaryResponse();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("Rendering", () => {
    it("should render component with media cost section", () => {
      renderWithStore(
        <CostBreakdown
          costBreakDownData={mockCostBreakdownData}
          currency="USD"
        />,
      );

      expect(
        screen.getAllByText("costBreakdown.mediaCost").length,
      ).toBeGreaterThan(0);
    });

    it("should render custom fees section", () => {
      renderWithStore(
        <CostBreakdown
          costBreakDownData={mockCostBreakdownData}
          currency="USD"
        />,
      );

      expect(
        screen.getAllByText("costBreakdown.customFees").length,
      ).toBeGreaterThan(0);
    });

    it("should render total cost summary section", () => {
      renderWithStore(
        <CostBreakdown
          costBreakDownData={mockCostBreakdownData}
          currency="USD"
        />,
      );

      expect(
        screen.getByText("costBreakdown.totalCostSummary"),
      ).toBeInTheDocument();
    });
  });

  describe("Media Cost Section", () => {
    it("should display campaign forecast", () => {
      renderWithStore(
        <CostBreakdown
          costBreakDownData={mockCostBreakdownData}
          currency="USD"
        />,
      );

      expect(
        screen.getByText("costBreakdown.campaignForecast"),
      ).toBeInTheDocument();
    });

    it("should display media cost value from discountedMediaCost", () => {
      renderWithStore(
        <CostBreakdown
          costBreakDownData={mockCostBreakdownData}
          currency="USD"
        />,
      );

      expect(
        screen.getAllByText("costBreakdown.mediaCost").length,
      ).toBeGreaterThan(0);
      expect(screen.getAllByText("$100,000.00").length).toBeGreaterThan(0);
    });

    it("should display platform fee as zero", () => {
      renderWithStore(
        <CostBreakdown
          costBreakDownData={mockCostBreakdownData}
          currency="USD"
        />,
      );

      expect(
        screen.getAllByText("costBreakdown.platformFee").length,
      ).toBeGreaterThan(0);
      expect(screen.getAllByText("$0.00").length).toBeGreaterThan(0);
    });

    it("should display net cost from discountedMediaCost", () => {
      renderWithStore(
        <CostBreakdown
          costBreakDownData={mockCostBreakdownData}
          currency="USD"
        />,
      );

      expect(screen.getByText("costBreakdown.netCost")).toBeInTheDocument();
      expect(screen.getAllByText("$100,000.00").length).toBeGreaterThan(0);
    });
  });

  describe("Custom Fees Section", () => {
    it("should display add fee button", () => {
      renderWithStore(
        <CostBreakdown
          costBreakDownData={mockCostBreakdownData}
          currency="USD"
        />,
      );

      expect(
        screen.getByRole("button", { name: "costBreakdown.addFee" }),
      ).toBeInTheDocument();
    });

    it("should display no custom fees message when customFees is empty", () => {
      renderWithStore(
        <CostBreakdown
          costBreakDownData={mockCostBreakdownData}
          currency="USD"
        />,
      );

      expect(
        screen.getByText("costBreakdown.noCustomFeesAdded"),
      ).toBeInTheDocument();
    });

    it("should display custom fees list with name and effective amount", () => {
      const dataWithFees = createMockPriceSummaryResponse({
        customFees: [
          createMockCustomFee({
            id: "fee-1",
            name: "Setup Fee",
            type: "PERCENTAGE",
            value: 2,
            basedOn: "BASE_COST",
            effectiveCustomFee: 2000,
          }),
        ],
      });

      renderWithStore(
        <CostBreakdown costBreakDownData={dataWithFees} currency="USD" />,
      );

      expect(screen.getByText("Setup Fee")).toBeInTheDocument();
      expect(
        screen.getByText("2costBreakdown.percentOf costBreakdown.baseCost"),
      ).toBeInTheDocument();
      expect(screen.getAllByText("$2,000.00").length).toBeGreaterThan(0);
    });

    it("should display VALUE type fee as fixed amount", () => {
      const dataWithValueFee = createMockPriceSummaryResponse({
        customFees: [
          createMockCustomFee({
            id: "fee-2",
            name: "Fixed Fee",
            type: "VALUE",
            value: 500,
            effectiveCustomFee: 500,
          }),
        ],
      });

      renderWithStore(
        <CostBreakdown costBreakDownData={dataWithValueFee} currency="USD" />,
      );

      expect(screen.getByText("Fixed Fee")).toBeInTheDocument();
      expect(
        screen.getByText("$500.00 costBreakdown.fixed"),
      ).toBeInTheDocument();
      expect(screen.getAllByText("$500.00").length).toBeGreaterThan(0);
    });

    it("should render Edit fee button with accessible label", () => {
      setupEditableUser();
      const dataWithFees = createMockPriceSummaryResponse({
        customFees: [createMockCustomFee()],
      });

      renderWithStore(
        <CostBreakdown
          costBreakDownData={dataWithFees}
          currency="USD"
          campaignId="campaign-1"
        />,
      );

      expect(
        screen.getByRole("button", { name: "costBreakdown.ariaEditFee" }),
      ).toBeInTheDocument();
    });

    it("should render Delete fee button with accessible label", () => {
      setupEditableUser();
      const dataWithFees = createMockPriceSummaryResponse({
        customFees: [createMockCustomFee()],
      });

      renderWithStore(
        <CostBreakdown
          costBreakDownData={dataWithFees}
          currency="USD"
          campaignId="campaign-1"
        />,
      );

      expect(
        screen.getByRole("button", { name: "costBreakdown.ariaDeleteFee" }),
      ).toBeInTheDocument();
    });
  });

  describe("Total Cost Summary", () => {
    it("should display all cost summary cards", () => {
      renderWithStore(
        <CostBreakdown
          costBreakDownData={mockCostBreakdownData}
          currency="USD"
        />,
      );

      expect(
        screen.getAllByText("costBreakdown.mediaCost").length,
      ).toBeGreaterThan(0);
      expect(
        screen.getAllByText("costBreakdown.platformFee").length,
      ).toBeGreaterThan(0);
      expect(
        screen.getAllByText("costBreakdown.customFees").length,
      ).toBeGreaterThan(0);
      expect(screen.getByText("costBreakdown.totalCost")).toBeInTheDocument();
    });

    it("should display correct values in summary cards using proposedPrice and discountedMediaCost", () => {
      renderWithStore(
        <CostBreakdown
          costBreakDownData={mockCostBreakdownData}
          currency="USD"
        />,
      );

      expect(screen.getAllByText("$100,000.00").length).toBeGreaterThan(0);
      expect(screen.getAllByText("$0.00").length).toBeGreaterThan(0);
      expect(screen.getByText("$107,000.00")).toBeInTheDocument();
    });

    it("should display sum of custom fees effectiveCustomFee in Custom Fees summary card", () => {
      const dataWithFees = createMockPriceSummaryResponse({
        customFees: [
          createMockCustomFee({ effectiveCustomFee: 1500 }),
          createMockCustomFee({
            id: "fee-2",
            name: "Other",
            effectiveCustomFee: 500,
          }),
        ],
      });

      renderWithStore(
        <CostBreakdown costBreakDownData={dataWithFees} currency="USD" />,
      );

      expect(screen.getByText("$2,000.00")).toBeInTheDocument();
    });
  });

  describe("Interactions", () => {
    it("should open Add Custom Fee drawer when Add Fee is clicked", async () => {
      const user = userEvent.setup();
      renderWithStore(
        <CostBreakdown
          costBreakDownData={mockCostBreakdownData}
          currency="USD"
        />,
      );

      await user.click(
        screen.getByRole("button", { name: "costBreakdown.addFee" }),
      );

      expect(screen.getByTestId("add-custom-fee-drawer")).toBeInTheDocument();
      expect(screen.getByText("Add Custom Fee Drawer")).toBeInTheDocument();
    });

    it("should open drawer when Edit fee is clicked", async () => {
      setupEditableUser();
      const user = userEvent.setup();
      const dataWithFees = createMockPriceSummaryResponse({
        customFees: [createMockCustomFee({ name: "Editable Fee" })],
      });

      renderWithStore(
        <CostBreakdown
          costBreakDownData={dataWithFees}
          currency="USD"
          campaignId="campaign-1"
        />,
      );

      await user.click(
        screen.getByRole("button", { name: "costBreakdown.ariaEditFee" }),
      );

      expect(screen.getByTestId("add-custom-fee-drawer")).toBeInTheDocument();
      expect(screen.getByText("Add Custom Fee Drawer")).toBeInTheDocument();
    });

    it("should open delete confirmation modal when Delete fee is clicked", async () => {
      setupEditableUser();
      const user = userEvent.setup();
      const dataWithFees = createMockPriceSummaryResponse({
        customFees: [createMockCustomFee({ name: "Fee to delete" })],
      });

      renderWithStore(
        <CostBreakdown
          costBreakDownData={dataWithFees}
          currency="USD"
          campaignId="campaign-1"
        />,
      );

      await user.click(
        screen.getByRole("button", { name: "costBreakdown.ariaDeleteFee" }),
      );

      expect(screen.getByText("costBreakdown.deleteFee")).toBeInTheDocument();
      expect(
        screen.getByText(/costBreakdown\.deleteFeeConfirm/),
      ).toBeInTheDocument();
      expect(screen.getByText("Fee to delete")).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: "costBreakdown.yesDelete" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: "costBreakdown.dontDelete" }),
      ).toBeInTheDocument();
    });

    it("should close delete modal when Don't Delete is clicked", async () => {
      setupEditableUser();
      const user = userEvent.setup();
      const dataWithFees = createMockPriceSummaryResponse({
        customFees: [createMockCustomFee()],
      });

      renderWithStore(
        <CostBreakdown
          costBreakDownData={dataWithFees}
          currency="USD"
          campaignId="campaign-1"
        />,
      );

      await user.click(
        screen.getByRole("button", { name: "costBreakdown.ariaDeleteFee" }),
      );
      expect(screen.getByText("costBreakdown.deleteFee")).toBeInTheDocument();

      await user.click(
        screen.getByRole("button", { name: "costBreakdown.dontDelete" }),
      );
      await waitFor(() => {
        expect(
          screen.queryByText("costBreakdown.deleteFee"),
        ).not.toBeInTheDocument();
      });
    });

    it("should call updateCustomFee and showSuccess when confirming delete", async () => {
      setupEditableUser();
      const user = userEvent.setup();
      const dataWithFees = createMockPriceSummaryResponse({
        customFees: [createMockCustomFee({ id: "fee-1", name: "Setup Fee" })],
      });

      renderWithStore(
        <CostBreakdown
          costBreakDownData={dataWithFees}
          currency="USD"
          campaignId="campaign-1"
        />,
      );

      await user.click(
        screen.getByRole("button", { name: "costBreakdown.ariaDeleteFee" }),
      );
      await user.click(
        screen.getByRole("button", { name: "costBreakdown.yesDelete" }),
      );

      await waitFor(
        () => {
          expect(mockUpdateCustomFee).toHaveBeenCalledWith(
            expect.objectContaining({
              id: "fee-1",
              data: expect.objectContaining({
                name: "Setup Fee",
                isActive: false,
              }),
            }),
          );
        },
        { timeout: 2000 },
      );
      await waitFor(() => {
        expect(mockShowSuccess).toHaveBeenCalled();
      });
      expect(mockFetchPriceSummary).toHaveBeenCalledWith({
        campaignId: "campaign-1",
      });
    });

    it("should close drawer when Close drawer is clicked", async () => {
      const user = userEvent.setup();
      renderWithStore(
        <CostBreakdown
          costBreakDownData={mockCostBreakdownData}
          currency="USD"
        />,
      );

      await user.click(
        screen.getByRole("button", { name: "costBreakdown.addFee" }),
      );
      expect(screen.getByTestId("add-custom-fee-drawer")).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "Close drawer" }));
      await waitFor(() => {
        expect(
          screen.queryByTestId("add-custom-fee-drawer"),
        ).not.toBeInTheDocument();
      });
    });
  });

  describe("Save Custom Fees", () => {
    it("should call bulkUpdateCustomFees and fetchPriceSummary when saving from drawer with campaignId", async () => {
      setupEditableUser();
      const user = userEvent.setup();
      renderWithStore(
        <CostBreakdown
          costBreakDownData={mockCostBreakdownData}
          currency="USD"
          campaignId="campaign-1"
        />,
      );

      await user.click(
        screen.getByRole("button", { name: "costBreakdown.addFee" }),
      );
      await user.click(screen.getByTestId("drawer-save"));

      await waitFor(
        () => {
          expect(mockBulkUpdateCustomFees).toHaveBeenCalled();
        },
        { timeout: 2000 },
      );
      await waitFor(() => {
        expect(mockShowSuccess).toHaveBeenCalled();
      });
      expect(mockFetchPriceSummary).toHaveBeenCalledWith({
        campaignId: "campaign-1",
      });
    });

    it("should call onRefresh when provided after successful save", async () => {
      setupEditableUser();
      const user = userEvent.setup();
      const onRefresh = vi.fn();
      renderWithStore(
        <CostBreakdown
          costBreakDownData={mockCostBreakdownData}
          currency="USD"
          campaignId="campaign-1"
          onRefresh={onRefresh}
        />,
      );

      await user.click(
        screen.getByRole("button", { name: "costBreakdown.addFee" }),
      );
      await user.click(screen.getByTestId("drawer-save"));

      await waitFor(
        () => {
          expect(mockBulkUpdateCustomFees).toHaveBeenCalled();
        },
        { timeout: 2000 },
      );
      await waitFor(() => {
        expect(onRefresh).toHaveBeenCalled();
      });
    });
  });

  describe("Edge Cases", () => {
    it("should handle undefined costBreakDownData", () => {
      renderWithStore(
        <CostBreakdown costBreakDownData={undefined} currency="USD" />,
      );

      expect(
        screen.getAllByText("costBreakdown.mediaCost").length,
      ).toBeGreaterThan(0);
      expect(screen.getAllByText("--").length).toBeGreaterThan(0);
    });

    it("should handle missing values in costBreakDownData", () => {
      const incompleteData = createMockPriceSummaryResponse({
        discountedMediaCost: 100000,
        proposedPrice: 100000,
        customFees: [],
      });

      renderWithStore(
        <CostBreakdown costBreakDownData={incompleteData} currency="USD" />,
      );

      expect(
        screen.getAllByText("costBreakdown.mediaCost").length,
      ).toBeGreaterThan(0);
    });

    it("should handle zero values", () => {
      const zeroData = createMockPriceSummaryResponse({
        discountedMediaCost: 0,
        proposedPrice: 0,
        customFees: [],
      });

      renderWithStore(
        <CostBreakdown costBreakDownData={zeroData} currency="USD" />,
      );

      expect(screen.getAllByText("$0.00").length).toBeGreaterThan(0);
    });

    it("should handle different currencies", () => {
      renderWithStore(
        <CostBreakdown
          costBreakDownData={mockCostBreakdownData}
          currency="EUR"
        />,
      );

      expect(screen.getAllByText(/€/).length).toBeGreaterThan(0);
    });

    it("should handle empty currency string with default", () => {
      renderWithStore(
        <CostBreakdown costBreakDownData={mockCostBreakdownData} currency="" />,
      );

      expect(
        screen.getAllByText("costBreakdown.mediaCost").length,
      ).toBeGreaterThan(0);
    });

    it("should handle very large numbers", () => {
      const largeData = createMockPriceSummaryResponse({
        discountedMediaCost: 999999999,
        proposedPrice: 1050999998,
        customFees: [],
      });

      renderWithStore(
        <CostBreakdown costBreakDownData={largeData} currency="USD" />,
      );

      expect(
        screen.getAllByText("costBreakdown.mediaCost").length,
      ).toBeGreaterThan(0);
    });
  });
});
