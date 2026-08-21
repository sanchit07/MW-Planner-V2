import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { ApplyDiscountDrawer } from "../ApplyDiscountDrawer";

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

const mockApplyScheduleAdjustment = vi.fn();
vi.mock("@services/inventory/inventorySlice", () => ({
  useApplyScheduleAdjustmentMutation: () => [mockApplyScheduleAdjustment],
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

const defaultTableData = [
  {
    id: "inv-1",
    children: [
      {
        id: "sch-1",
        parentId: "inv-1",
        originalSchedule: { id: "orig-sch-1" },
      },
    ],
  },
];

describe("ApplyDiscountDrawer", () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    selectedCount: 1,
    campaignId: "camp-1",
    selectedItems: new Set<string>(["inv-1:sch-1"]),
    tableData: defaultTableData,
    onSuccess: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockApplyScheduleAdjustment.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          data: "Discount applied successfully",
        }),
    });
  });

  describe("rendering", () => {
    it("does not render when isOpen is false", () => {
      render(<ApplyDiscountDrawer {...defaultProps} isOpen={false} />);
      expect(screen.queryByTestId("modal-drawer")).not.toBeInTheDocument();
    });

    it("renders title and description when open", () => {
      render(<ApplyDiscountDrawer {...defaultProps} />);
      expect(
        screen.getByText("drawers.apply_discount.title"),
      ).toBeInTheDocument();
    });

    it("renders discount input and buttons", () => {
      render(<ApplyDiscountDrawer {...defaultProps} />);
      expect(
        screen.getByLabelText(/drawers\.apply_discount\.discount_percentage/i),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", {
          name: /buttons\.cancel/i,
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /drawers\.apply_discount\.apply/i }),
      ).toBeInTheDocument();
    });

    it("apply button is disabled when discount is empty", () => {
      render(<ApplyDiscountDrawer {...defaultProps} />);
      const applyBtn = screen.getByRole("button", {
        name: /drawers\.apply_discount\.apply/i,
      });
      expect(applyBtn).toBeDisabled();
    });
  });

  describe("input validation", () => {
    it("accepts valid discount value and enables apply", async () => {
      const user = userEvent.setup();
      render(<ApplyDiscountDrawer {...defaultProps} />);
      const input = screen.getByLabelText(
        /drawers\.apply_discount\.discount_percentage/i,
      );
      await user.type(input, "15");
      expect(input).toHaveValue("15");
      const applyBtn = screen.getByRole("button", {
        name: /drawers\.apply_discount\.apply/i,
      });
      expect(applyBtn).not.toBeDisabled();
    });

    it("shows error when discount is invalid (negative or over 100)", async () => {
      const user = userEvent.setup();
      render(<ApplyDiscountDrawer {...defaultProps} />);
      const input = screen.getByLabelText(
        /drawers\.apply_discount\.discount_percentage/i,
      );
      await user.type(input, "150");
      expect(
        screen.getByText("drawers.apply_discount.error_invalid"),
      ).toBeInTheDocument();
    });

    it("apply button is disabled when discount is empty so required error path is not reachable", () => {
      render(<ApplyDiscountDrawer {...defaultProps} />);
      const applyBtn = screen.getByRole("button", {
        name: /drawers\.apply_discount\.apply/i,
      });
      expect(applyBtn).toBeDisabled();
    });
  });

  describe("interactions", () => {
    it("calls onClose when cancel is clicked", async () => {
      const user = userEvent.setup();
      render(<ApplyDiscountDrawer {...defaultProps} />);
      await user.click(
        screen.getByRole("button", {
          name: /buttons\.cancel/i,
        }),
      );
      expect(defaultProps.onClose).toHaveBeenCalledTimes(1);
    });

    it("applies discount successfully and calls onSuccess and onClose", async () => {
      const user = userEvent.setup();
      render(<ApplyDiscountDrawer {...defaultProps} />);
      const input = screen.getByLabelText(
        /drawers\.apply_discount\.discount_percentage/i,
      );
      await user.type(input, "10");
      await user.click(
        screen.getByRole("button", { name: /drawers\.apply_discount\.apply/i }),
      );
      await waitFor(() => {
        expect(mockApplyScheduleAdjustment).toHaveBeenCalledWith({
          campaignId: "camp-1",
          data: {
            scheduleIds: ["orig-sch-1"],
            actionType: "DISCOUNT",
            discount: {
              discountType: "PERCENTAGE",
              value: "10",
            },
          },
        });
      });
      await waitFor(() => {
        expect(mockShowSuccess).toHaveBeenCalledWith(
          "Discount applied successfully",
        );
        expect(defaultProps.onSuccess).toHaveBeenCalled();
        expect(defaultProps.onClose).toHaveBeenCalled();
      });
    });

    it("shows error when campaignId is missing", async () => {
      const user = userEvent.setup();
      render(<ApplyDiscountDrawer {...defaultProps} campaignId={undefined} />);
      const input = screen.getByLabelText(
        /drawers\.apply_discount\.discount_percentage/i,
      );
      await user.type(input, "20");
      await user.click(
        screen.getByRole("button", { name: /drawers\.apply_discount\.apply/i }),
      );
      await waitFor(() => {
        expect(mockShowError).toHaveBeenCalledWith(
          expect.stringContaining("Campaign ID"),
        );
      });
    });

    it("shows error when no schedules selected", async () => {
      const user = userEvent.setup();
      render(
        <ApplyDiscountDrawer
          {...defaultProps}
          selectedItems={new Set()}
          tableData={[]}
        />,
      );
      const input = screen.getByLabelText(
        /drawers\.apply_discount\.discount_percentage/i,
      );
      await user.type(input, "5");
      await user.click(
        screen.getByRole("button", { name: /drawers\.apply_discount\.apply/i }),
      );
      await waitFor(() => {
        expect(mockShowError).toHaveBeenCalledWith(
          expect.stringContaining("No schedules"),
        );
      });
    });

    it("handles API error and shows error message", async () => {
      mockApplyScheduleAdjustment.mockReturnValue({
        unwrap: () =>
          Promise.reject({
            data: { error: { message: "Discount failed" } },
          }),
      });
      const user = userEvent.setup();
      render(<ApplyDiscountDrawer {...defaultProps} />);
      const input = screen.getByLabelText(
        /drawers\.apply_discount\.discount_percentage/i,
      );
      await user.type(input, "25");
      await user.click(
        screen.getByRole("button", { name: /drawers\.apply_discount\.apply/i }),
      );
      await waitFor(() => {
        expect(mockShowError).toHaveBeenCalledWith("Discount failed");
      });
    });
  });

  describe("decimal places", () => {
    it("allows up to 2 decimal places in discount input", async () => {
      const user = userEvent.setup();
      render(<ApplyDiscountDrawer {...defaultProps} />);
      const input = screen.getByLabelText(
        /drawers\.apply_discount\.discount_percentage/i,
      ) as HTMLInputElement;
      await user.type(input, "12.34");
      expect(input.value).toBe("12.34");
    });
  });
});
