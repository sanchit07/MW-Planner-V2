import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { ApplyBonusDrawer } from "../ApplyBonusDrawer";

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

vi.mock("@components/ui/Dropdown", () => ({
  Dropdown: ({
    children,
    onChange,
  }: {
    children: React.ReactNode;
    value: string;
    onChange: (v: string) => void;
  }) => (
    <div data-testid="dropdown">
      {children}
      <button
        type="button"
        onClick={() => onChange("value_added")}
        aria-label="Select value_added"
      >
        Value Added
      </button>
      <button
        type="button"
        onClick={() => onChange("volume_bonus")}
        aria-label="Select volume_bonus"
      >
        Volume Bonus
      </button>
    </div>
  ),
  DropdownTrigger: ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  ),
  DropdownContent: ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  ),
  DropdownItem: () => null,
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

const defaultBonusTypeOptions = [
  { value: "value_added", label: "Value Added" },
  { value: "volume_bonus", label: "Volume Bonus" },
];

describe("ApplyBonusDrawer", () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    selectedCount: 1,
    campaignId: "camp-1",
    selectedItems: new Set<string>(["inv-1:sch-1"]),
    tableData: defaultTableData,
    onSuccess: vi.fn(),
    bonusTypeOptions: defaultBonusTypeOptions,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockApplyScheduleAdjustment.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          data: "Bonus applied successfully",
        }),
    });
  });

  describe("rendering", () => {
    it("does not render when isOpen is false", () => {
      render(<ApplyBonusDrawer {...defaultProps} isOpen={false} />);
      expect(screen.queryByTestId("modal-drawer")).not.toBeInTheDocument();
    });

    it("renders title and description when open", () => {
      render(<ApplyBonusDrawer {...defaultProps} />);
      expect(screen.getByText("drawers.apply_bonus.title")).toBeInTheDocument();
      expect(
        screen.getByText(/drawers\.apply_bonus\.description/),
      ).toBeInTheDocument();
    });

    it("renders cancel and apply buttons", () => {
      render(<ApplyBonusDrawer {...defaultProps} />);
      expect(
        screen.getByRole("button", { name: /buttons\.cancel/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /drawers\.apply_bonus\.apply/i }),
      ).toBeInTheDocument();
    });

    it("apply button is disabled when no bonus type selected", () => {
      render(<ApplyBonusDrawer {...defaultProps} />);
      const applyBtn = screen.getByRole("button", {
        name: /drawers\.apply_bonus\.apply/i,
      });
      expect(applyBtn).toBeDisabled();
    });
  });

  describe("interactions", () => {
    it("calls onClose when cancel is clicked", async () => {
      const user = userEvent.setup();
      render(<ApplyBonusDrawer {...defaultProps} />);
      await user.click(
        screen.getByRole("button", { name: /buttons\.cancel/i }),
      );
      expect(defaultProps.onClose).toHaveBeenCalledTimes(1);
    });

    it("enables apply when bonus type is selected and applies successfully", async () => {
      const user = userEvent.setup();
      render(<ApplyBonusDrawer {...defaultProps} />);
      await user.click(
        screen.getByRole("button", { name: /Select value_added/i }),
      );
      const applyBtn = screen.getByRole("button", {
        name: /drawers\.apply_bonus\.apply/i,
      });
      expect(applyBtn).not.toBeDisabled();
      await user.click(applyBtn);
      await waitFor(() => {
        expect(mockApplyScheduleAdjustment).toHaveBeenCalledWith({
          campaignId: "camp-1",
          data: {
            scheduleIds: ["orig-sch-1"],
            actionType: "BONUS",
            bonus: "value_added",
          },
        });
      });
      await waitFor(() => {
        expect(mockShowSuccess).toHaveBeenCalledWith(
          "Bonus applied successfully",
        );
        expect(defaultProps.onSuccess).toHaveBeenCalled();
        expect(defaultProps.onClose).toHaveBeenCalled();
      });
    });

    it("shows error when campaignId is missing and apply is clicked", async () => {
      const user = userEvent.setup();
      render(<ApplyBonusDrawer {...defaultProps} campaignId={undefined} />);
      await user.click(
        screen.getByRole("button", { name: /Select value_added/i }),
      );
      const applyBtn = screen.getByRole("button", {
        name: /drawers\.apply_bonus\.apply/i,
      });
      await user.click(applyBtn);
      await waitFor(() => {
        expect(mockShowError).toHaveBeenCalledWith(
          expect.stringContaining("Campaign ID"),
        );
      });
      expect(mockApplyScheduleAdjustment).not.toHaveBeenCalled();
    });

    it("shows error when no schedules selected and apply is clicked", async () => {
      const user = userEvent.setup();
      render(
        <ApplyBonusDrawer
          {...defaultProps}
          selectedItems={new Set()}
          tableData={[]}
        />,
      );
      await user.click(
        screen.getByRole("button", { name: /Select value_added/i }),
      );
      await user.click(
        screen.getByRole("button", { name: /drawers\.apply_bonus\.apply/i }),
      );
      await waitFor(() => {
        expect(mockShowError).toHaveBeenCalledWith(
          expect.stringContaining("No schedules"),
        );
      });
      expect(mockApplyScheduleAdjustment).not.toHaveBeenCalled();
    });

    it("apply button is disabled when no bonus type selected so required error path is not reachable", () => {
      render(<ApplyBonusDrawer {...defaultProps} />);
      const applyBtn = screen.getByRole("button", {
        name: /drawers\.apply_bonus\.apply/i,
      });
      expect(applyBtn).toBeDisabled();
    });
  });

  describe("getSelectedScheduleIds", () => {
    it("extracts schedule IDs from parent selection (no colon)", async () => {
      mockApplyScheduleAdjustment.mockReturnValue({
        unwrap: () => Promise.resolve({ data: "OK" }),
      });
      const user = userEvent.setup();
      render(
        <ApplyBonusDrawer
          {...defaultProps}
          selectedItems={new Set(["inv-1"])}
          tableData={defaultTableData}
        />,
      );
      await user.click(
        screen.getByRole("button", { name: /Select value_added/i }),
      );
      await user.click(
        screen.getByRole("button", { name: /drawers\.apply_bonus\.apply/i }),
      );
      await waitFor(() => {
        expect(mockApplyScheduleAdjustment).toHaveBeenCalledWith(
          expect.objectContaining({
            data: expect.objectContaining({
              scheduleIds: ["orig-sch-1"],
            }),
          }),
        );
      });
    });

    it("handles API error and shows error message", async () => {
      mockApplyScheduleAdjustment.mockReturnValue({
        unwrap: () =>
          Promise.reject({
            data: { error: { message: "Server error" } },
          }),
      });
      const user = userEvent.setup();
      render(<ApplyBonusDrawer {...defaultProps} />);
      await user.click(
        screen.getByRole("button", { name: /Select value_added/i }),
      );
      await user.click(
        screen.getByRole("button", { name: /drawers\.apply_bonus\.apply/i }),
      );
      await waitFor(() => {
        expect(mockShowError).toHaveBeenCalledWith("Server error");
      });
    });

    it("applies volume_bonus when that option is selected", async () => {
      mockApplyScheduleAdjustment.mockReturnValue({
        unwrap: () =>
          Promise.resolve({
            data: "Bonus applied successfully",
          }),
      });
      const user = userEvent.setup();
      render(<ApplyBonusDrawer {...defaultProps} />);
      await user.click(
        screen.getByRole("button", { name: /Select volume_bonus/i }),
      );
      await user.click(
        screen.getByRole("button", { name: /drawers\.apply_bonus\.apply/i }),
      );
      await waitFor(() => {
        expect(mockApplyScheduleAdjustment).toHaveBeenCalledWith({
          campaignId: "camp-1",
          data: {
            scheduleIds: ["orig-sch-1"],
            actionType: "BONUS",
            bonus: "volume_bonus",
          },
        });
      });
    });

    it("shows default success message when response.data is not a string", async () => {
      mockApplyScheduleAdjustment.mockReturnValue({
        unwrap: () =>
          Promise.resolve({
            data: { success: true },
          }),
      });
      const user = userEvent.setup();
      render(<ApplyBonusDrawer {...defaultProps} />);
      await user.click(
        screen.getByRole("button", { name: /Select value_added/i }),
      );
      await user.click(
        screen.getByRole("button", { name: /drawers\.apply_bonus\.apply/i }),
      );
      await waitFor(() => {
        expect(mockShowSuccess).toHaveBeenCalledWith(
          "Bonus applied successfully",
        );
      });
    });

    it("shows fallback error message when API error has no message", async () => {
      mockApplyScheduleAdjustment.mockReturnValue({
        unwrap: () => Promise.reject(new Error("Network error")),
      });
      const user = userEvent.setup();
      render(<ApplyBonusDrawer {...defaultProps} />);
      await user.click(
        screen.getByRole("button", { name: /Select value_added/i }),
      );
      await user.click(
        screen.getByRole("button", { name: /drawers\.apply_bonus\.apply/i }),
      );
      await waitFor(() => {
        expect(mockShowError).toHaveBeenCalled();
      });
    });
  });
});
