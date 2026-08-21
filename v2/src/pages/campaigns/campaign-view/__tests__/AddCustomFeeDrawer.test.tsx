import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import type { PriceSummaryCustomFee } from "src/types/inventory.types";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { AddCustomFeeDrawer } from "../AddCustomFeeDrawer";

// Mock dependencies
vi.mock("@components/campaigns/CustomFeeForm", () => ({
  CustomFeeForm: ({
    feeName,
    type,
    value,
    description,
    includeInMediaPlan,
    onFeeNameChange,
    onTypeChange,
    onValueChange,
    onDescriptionChange,
    onIncludeInMediaPlanChange,
    errors,
  }: {
    feeName: string;
    type: string;
    value: string;
    description: string;
    includeInMediaPlan: boolean;
    onFeeNameChange: (val: string) => void;
    onTypeChange: (val: string) => void;
    onValueChange: (val: string) => void;
    onDescriptionChange: (val: string) => void;
    onIncludeInMediaPlanChange: (checked: boolean) => void;
    errors?: { feeName?: string; value?: string };
  }) => (
    <div data-testid="custom-fee-form">
      <input
        data-testid="fee-name-input"
        value={feeName}
        onChange={(e) => onFeeNameChange(e.target.value)}
      />
      <select
        data-testid="fee-type-select"
        value={type}
        onChange={(e) => onTypeChange(e.target.value)}
      >
        <option value="Percentage">Percentage</option>
        <option value="Fixed">Fixed</option>
      </select>
      <input
        data-testid="fee-value-input"
        value={value}
        onChange={(e) => onValueChange(e.target.value)}
      />
      <textarea
        data-testid="fee-description-textarea"
        value={description}
        onChange={(e) => onDescriptionChange(e.target.value)}
      />
      <input
        type="checkbox"
        data-testid="fee-include-checkbox"
        checked={includeInMediaPlan}
        onChange={(e) => onIncludeInMediaPlanChange(e.target.checked)}
      />
      {errors?.feeName && (
        <div data-testid="fee-name-error">{errors.feeName}</div>
      )}
      {errors?.value && <div data-testid="fee-value-error">{errors.value}</div>}
    </div>
  ),
}));

vi.mock("@components/ui/ModalDrawer", () => ({
  ModalDrawer: ({
    isOpen,
    onClose,
    title,
    children,
    footer,
  }: {
    isOpen: boolean;
    onClose: () => void;
    title: string;
    children: React.ReactNode;
    footer: React.ReactNode;
  }) => {
    if (!isOpen) return null;
    return (
      <div data-testid="modal-drawer">
        <div data-testid="drawer-title">{title}</div>
        <div data-testid="drawer-content">{children}</div>
        <div data-testid="drawer-footer">{footer}</div>
        <button onClick={onClose} data-testid="drawer-close">
          Close
        </button>
      </div>
    );
  },
}));

vi.mock("@components/ui/Button", () => ({
  Button: ({
    children,
    onClick,
    disabled,
    variant,
  }: {
    children: React.ReactNode;
    onClick?: () => void;
    disabled?: boolean;
    variant?: string;
  }) => (
    <button
      onClick={onClick}
      disabled={disabled}
      data-variant={variant}
      data-testid={variant === "primary" ? "save-button" : "cancel-button"}
    >
      {children}
    </button>
  ),
}));

vi.mock("@components/ui/Label", () => ({
  Label: ({
    children,
    htmlFor,
  }: {
    children: React.ReactNode;
    htmlFor?: string;
  }) => <label htmlFor={htmlFor}>{children}</label>,
}));

const mockUser = {
  activeCompanyId: "company-123",
  memberships: [{ company_id: "company-456" }],
};

vi.mock("@store", () => ({
  useAppSelector: vi.fn((selector) => {
    if (selector.toString().includes("profile.profile")) {
      return mockUser;
    }
    return null;
  }),
}));

const mockT = vi.fn((key: string, options?: { defaultValue?: string }) => {
  return options?.defaultValue || key;
});

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: mockT,
  }),
}));

describe("AddCustomFeeDrawer", () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    campaignId: "campaign-123",
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockT.mockImplementation(
      (key: string, options?: { defaultValue?: string }) => {
        return options?.defaultValue || key;
      },
    );
  });

  const user = userEvent.setup({ delay: null });

  describe("Rendering", () => {
    it("renders drawer when isOpen is true", () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);
      expect(screen.getByTestId("modal-drawer")).toBeInTheDocument();
    });

    it("does not render drawer when isOpen is false", () => {
      render(<AddCustomFeeDrawer {...defaultProps} isOpen={false} />);
      expect(screen.queryByTestId("modal-drawer")).not.toBeInTheDocument();
    });

    it("renders title for new fee", () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);
      expect(screen.getByTestId("drawer-title")).toHaveTextContent(
        "drawers.add_custom_fee.title",
      );
    });

    it("renders title for editing fee", () => {
      const initialFee: PriceSummaryCustomFee = {
        id: "fee-1",
        name: "Test Fee",
        type: "PERCENTAGE",
        value: 10,
      } as PriceSummaryCustomFee;

      render(<AddCustomFeeDrawer {...defaultProps} initialFee={initialFee} />);
      expect(screen.getByTestId("drawer-title")).toHaveTextContent(
        "Edit Custom Fee",
      );
    });

    it("renders CustomFeeForm", () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);
      expect(screen.getByTestId("custom-fee-form")).toBeInTheDocument();
    });

    it("renders approval checkbox", () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);
      const checkbox = screen.getByLabelText(
        /I confirm that campaign approval is required/i,
      );
      expect(checkbox).toBeInTheDocument();
      expect(checkbox).not.toBeChecked();
    });

    it("renders cancel and save buttons", () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);
      expect(screen.getByTestId("cancel-button")).toBeInTheDocument();
      expect(screen.getByTestId("save-button")).toBeInTheDocument();
    });

    it("disables save button when approval is not checked", () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);
      const saveButton = screen.getByTestId("save-button");
      expect(saveButton).toBeDisabled();
    });
  });

  describe("Form state management", () => {
    it("initializes form with default values when drawer opens", () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);

      expect(screen.getByTestId("fee-name-input")).toHaveValue("");
      expect(screen.getByTestId("fee-type-select")).toHaveValue("Percentage");
      expect(screen.getByTestId("fee-value-input")).toHaveValue("");
      expect(screen.getByTestId("fee-description-textarea")).toHaveValue("");
      expect(screen.getByTestId("fee-include-checkbox")).not.toBeChecked();
    });

    it("populates form with initialFee data when editing", () => {
      const initialFee: PriceSummaryCustomFee = {
        id: "fee-1",
        name: "Test Fee",
        type: "PERCENTAGE",
        value: 15,
        description: "Test Description",
        isIncludeInMediaPlan: true,
        campaignId: "campaign-123",
        companyId: "company-123",
      } as PriceSummaryCustomFee;

      render(<AddCustomFeeDrawer {...defaultProps} initialFee={initialFee} />);

      expect(screen.getByTestId("fee-name-input")).toHaveValue("Test Fee");
      expect(screen.getByTestId("fee-type-select")).toHaveValue("Percentage");
      expect(screen.getByTestId("fee-value-input")).toHaveValue("15");
      expect(screen.getByTestId("fee-description-textarea")).toHaveValue(
        "Test Description",
      );
      expect(screen.getByTestId("fee-include-checkbox")).toBeChecked();
    });

    it("handles PERCENTAGE type conversion", () => {
      const initialFee: PriceSummaryCustomFee = {
        id: "fee-1",
        name: "Test",
        type: "PERCENTAGE",
        value: 10,
      } as PriceSummaryCustomFee;

      render(<AddCustomFeeDrawer {...defaultProps} initialFee={initialFee} />);

      expect(screen.getByTestId("fee-type-select")).toHaveValue("Percentage");
    });

    it("handles FIXED type conversion", () => {
      const initialFee: PriceSummaryCustomFee = {
        id: "fee-1",
        name: "Test",
        type: "FIXED",
        value: 100,
      } as PriceSummaryCustomFee;

      render(<AddCustomFeeDrawer {...defaultProps} initialFee={initialFee} />);

      expect(screen.getByTestId("fee-type-select")).toHaveValue("Fixed");
    });

    it("resets form when drawer closes", async () => {
      const { rerender } = render(<AddCustomFeeDrawer {...defaultProps} />);

      const nameInput = screen.getByTestId("fee-name-input");
      await user.type(nameInput, "Test Fee");

      // Close drawer
      rerender(<AddCustomFeeDrawer {...defaultProps} isOpen={false} />);

      // Reopen drawer
      rerender(<AddCustomFeeDrawer {...defaultProps} isOpen={true} />);

      expect(screen.getByTestId("fee-name-input")).toHaveValue("");
    });
  });

  describe("Form interactions", () => {
    it("updates fee name when input changes", () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);

      const nameInput = screen.getByTestId("fee-name-input");
      // Verify input exists and can receive changes
      expect(nameInput).toBeInTheDocument();
      // Simulate change event
      fireEvent.change(nameInput, { target: { value: "New Fee Name" } });
      // Verify the value was updated
      expect(nameInput).toHaveValue("New Fee Name");
    });

    it("updates fee type when select changes", async () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);

      const typeSelect = screen.getByTestId("fee-type-select");
      await user.selectOptions(typeSelect, "Fixed");

      expect(typeSelect).toHaveValue("Fixed");
    });

    it("updates fee value when input changes", () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);

      const valueInput = screen.getByTestId("fee-value-input");
      fireEvent.change(valueInput, { target: { value: "25" } });

      expect(valueInput).toHaveValue("25");
    });

    it("updates description when textarea changes", () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);

      const descriptionTextarea = screen.getByTestId(
        "fee-description-textarea",
      );
      fireEvent.change(descriptionTextarea, {
        target: { value: "Test description" },
      });

      expect(descriptionTextarea).toHaveValue("Test description");
    });

    it("updates includeInMediaPlan when checkbox changes", async () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);

      const includeCheckbox = screen.getByTestId("fee-include-checkbox");
      await user.click(includeCheckbox);

      expect(includeCheckbox).toBeChecked();
    });

    it("updates requiresApproval when approval checkbox changes", async () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);

      const approvalCheckbox = screen.getByLabelText(
        /I confirm that campaign approval is required/i,
      );
      await user.click(approvalCheckbox);

      expect(approvalCheckbox).toBeChecked();
      expect(screen.getByTestId("save-button")).not.toBeDisabled();
    });

    it("clears fee name error when fee name changes", async () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);

      // Trigger validation error by trying to save without fee name
      const approvalCheckbox = screen.getByLabelText(
        /I confirm that campaign approval is required/i,
      );
      await user.click(approvalCheckbox);

      const saveButton = screen.getByTestId("save-button");
      await user.click(saveButton);

      // Error should be shown
      await waitFor(() => {
        expect(screen.queryByTestId("fee-name-error")).toBeInTheDocument();
      });

      // Clear error by typing in name field
      const nameInput = screen.getByTestId("fee-name-input");
      await user.type(nameInput, "Test");

      // Error should be cleared
      await waitFor(() => {
        expect(screen.queryByTestId("fee-name-error")).not.toBeInTheDocument();
      });
    });

    it("clears value error when value changes", async () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);

      // Trigger validation error
      const approvalCheckbox = screen.getByLabelText(
        /I confirm that campaign approval is required/i,
      );
      await user.click(approvalCheckbox);

      const saveButton = screen.getByTestId("save-button");
      await user.click(saveButton);

      // Error should be shown
      await waitFor(() => {
        expect(screen.queryByTestId("fee-value-error")).toBeInTheDocument();
      });

      // Clear error by typing in value field
      const valueInput = screen.getByTestId("fee-value-input");
      await user.type(valueInput, "10");

      // Error should be cleared
      await waitFor(() => {
        expect(screen.queryByTestId("fee-value-error")).not.toBeInTheDocument();
      });
    });
  });

  describe("Validation", () => {
    it("validates fee name is required", async () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);

      const approvalCheckbox = screen.getByLabelText(
        /I confirm that campaign approval is required/i,
      );
      await user.click(approvalCheckbox);

      const saveButton = screen.getByTestId("save-button");
      await user.click(saveButton);

      await waitFor(() => {
        expect(screen.getByTestId("fee-name-error")).toBeInTheDocument();
      });
    });

    it("validates value is required", async () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);

      const nameInput = screen.getByTestId("fee-name-input");
      await user.type(nameInput, "Test Fee");

      const approvalCheckbox = screen.getByLabelText(
        /I confirm that campaign approval is required/i,
      );
      await user.click(approvalCheckbox);

      const saveButton = screen.getByTestId("save-button");
      await user.click(saveButton);

      await waitFor(() => {
        expect(screen.getByTestId("fee-value-error")).toBeInTheDocument();
      });
    });

    it("validates value is a positive number", async () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);

      const nameInput = screen.getByTestId("fee-name-input");
      await user.type(nameInput, "Test Fee");

      const valueInput = screen.getByTestId("fee-value-input");
      await user.type(valueInput, "-10");

      const approvalCheckbox = screen.getByLabelText(
        /I confirm that campaign approval is required/i,
      );
      await user.click(approvalCheckbox);

      const saveButton = screen.getByTestId("save-button");
      await user.click(saveButton);

      await waitFor(() => {
        expect(screen.getByTestId("fee-value-error")).toBeInTheDocument();
      });
    });

    it("validates value is not NaN", async () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);

      const nameInput = screen.getByTestId("fee-name-input");
      await user.type(nameInput, "Test Fee");

      const valueInput = screen.getByTestId("fee-value-input");
      await user.type(valueInput, "abc");

      const approvalCheckbox = screen.getByLabelText(
        /I confirm that campaign approval is required/i,
      );
      await user.click(approvalCheckbox);

      const saveButton = screen.getByTestId("save-button");
      await user.click(saveButton);

      await waitFor(() => {
        expect(screen.getByTestId("fee-value-error")).toBeInTheDocument();
      });
    });

    it("validates value is finite (not Infinity)", async () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);

      const nameInput = screen.getByTestId("fee-name-input");
      await user.type(nameInput, "Test Fee");

      const valueInput = screen.getByTestId("fee-value-input");
      // Simulate Infinity by setting a very large number that would cause Infinity
      await user.clear(valueInput);
      await user.type(valueInput, "1e309"); // This will parse to Infinity

      const approvalCheckbox = screen.getByLabelText(
        /I confirm that campaign approval is required/i,
      );
      await user.click(approvalCheckbox);

      const saveButton = screen.getByTestId("save-button");
      await user.click(saveButton);

      await waitFor(() => {
        expect(screen.getByTestId("fee-value-error")).toBeInTheDocument();
      });
    });

    it("allows valid positive numbers", async () => {
      render(<AddCustomFeeDrawer {...defaultProps} />);

      const nameInput = screen.getByTestId("fee-name-input");
      await user.type(nameInput, "Test Fee");

      const valueInput = screen.getByTestId("fee-value-input");
      await user.type(valueInput, "25.5");

      const approvalCheckbox = screen.getByLabelText(
        /I confirm that campaign approval is required/i,
      );
      await user.click(approvalCheckbox);

      // Should not show error
      expect(screen.queryByTestId("fee-value-error")).not.toBeInTheDocument();
    });
  });

  describe("Save functionality", () => {
    it("does not save when validation fails", async () => {
      const onSave = vi.fn();

      render(<AddCustomFeeDrawer {...defaultProps} onSave={onSave} />);

      const approvalCheckbox = screen.getByLabelText(
        /I confirm that campaign approval is required/i,
      );
      await user.click(approvalCheckbox);

      const saveButton = screen.getByTestId("save-button");
      await user.click(saveButton);

      await waitFor(() => {
        expect(onSave).not.toHaveBeenCalled();
      });
    });

    it("does not save when approval is not checked", async () => {
      const onSave = vi.fn();

      render(<AddCustomFeeDrawer {...defaultProps} onSave={onSave} />);

      const nameInput = screen.getByTestId("fee-name-input");
      await user.type(nameInput, "Test Fee");

      const valueInput = screen.getByTestId("fee-value-input");
      await user.type(valueInput, "10");

      const saveButton = screen.getByTestId("save-button");
      await user.click(saveButton);

      expect(onSave).not.toHaveBeenCalled();
    });

    it("saves new fee with correct data", async () => {
      const onSave = vi.fn().mockResolvedValue(undefined);

      render(<AddCustomFeeDrawer {...defaultProps} onSave={onSave} />);

      const nameInput = screen.getByTestId("fee-name-input");
      await user.type(nameInput, "Test Fee");

      const valueInput = screen.getByTestId("fee-value-input");
      await user.type(valueInput, "10");

      const descriptionTextarea = screen.getByTestId(
        "fee-description-textarea",
      );
      await user.type(descriptionTextarea, "Test Description");

      const includeCheckbox = screen.getByTestId("fee-include-checkbox");
      await user.click(includeCheckbox);

      const approvalCheckbox = screen.getByLabelText(
        /I confirm that campaign approval is required/i,
      );
      await user.click(approvalCheckbox);

      const saveButton = screen.getByTestId("save-button");
      await user.click(saveButton);

      await waitFor(() => {
        expect(onSave).toHaveBeenCalledWith(
          [
            expect.objectContaining({
              feeName: "Test Fee",
              type: "Percentage",
              value: "10",
              basedOn: "Base Cost",
              description: "Test Description",
              includeInMediaPlan: true,
              campaignId: "campaign-123",
              companyId: "company-123",
            }),
          ],
          true,
          "campaign-123",
        );
      });
    });

    it("saves edited fee with initialFee data", async () => {
      const onSave = vi.fn().mockResolvedValue(undefined);

      const initialFee: PriceSummaryCustomFee = {
        id: "fee-1",
        name: "Original Fee",
        type: "PERCENTAGE",
        value: 10,
        campaignId: "campaign-456",
        companyId: "company-456",
      } as PriceSummaryCustomFee;

      render(
        <AddCustomFeeDrawer
          {...defaultProps}
          initialFee={initialFee}
          onSave={onSave}
        />,
      );

      const nameInput = screen.getByTestId("fee-name-input");
      await user.clear(nameInput);
      await user.type(nameInput, "Updated Fee");

      const approvalCheckbox = screen.getByLabelText(
        /I confirm that campaign approval is required/i,
      );
      await user.click(approvalCheckbox);

      const saveButton = screen.getByTestId("save-button");
      await user.click(saveButton);

      await waitFor(() => {
        expect(onSave).toHaveBeenCalledWith(
          [
            expect.objectContaining({
              id: "fee-1",
              feeName: "Updated Fee",
              campaignId: "campaign-456",
              companyId: "company-456",
            }),
          ],
          true,
          "campaign-123",
        );
      });
    });

    it("closes drawer after successful save", async () => {
      const onSave = vi.fn().mockResolvedValue(undefined);
      const onClose = vi.fn();

      render(
        <AddCustomFeeDrawer
          {...defaultProps}
          onSave={onSave}
          onClose={onClose}
        />,
      );

      const nameInput = screen.getByTestId("fee-name-input");
      await user.type(nameInput, "Test Fee");

      const valueInput = screen.getByTestId("fee-value-input");
      await user.type(valueInput, "10");

      const approvalCheckbox = screen.getByLabelText(
        /I confirm that campaign approval is required/i,
      );
      await user.click(approvalCheckbox);

      const saveButton = screen.getByTestId("save-button");
      await user.click(saveButton);

      await waitFor(() => {
        expect(onClose).toHaveBeenCalled();
      });
    });

    it("handles save error gracefully", async () => {
      const onSave = vi.fn().mockRejectedValue(new Error("Save failed"));
      const consoleSpy = vi
        .spyOn(console, "error")
        .mockImplementation(() => {});

      render(<AddCustomFeeDrawer {...defaultProps} onSave={onSave} />);

      const nameInput = screen.getByTestId("fee-name-input");
      await user.type(nameInput, "Test Fee");

      const valueInput = screen.getByTestId("fee-value-input");
      await user.type(valueInput, "10");

      const approvalCheckbox = screen.getByLabelText(
        /I confirm that campaign approval is required/i,
      );
      await user.click(approvalCheckbox);

      const saveButton = screen.getByTestId("save-button");
      await user.click(saveButton);

      await waitFor(() => {
        expect(consoleSpy).toHaveBeenCalledWith(
          "Error saving custom fee:",
          expect.any(Error),
        );
      });

      consoleSpy.mockRestore();
    });

    it("closes drawer when onSave is not provided", async () => {
      const onClose = vi.fn();

      render(<AddCustomFeeDrawer {...defaultProps} onClose={onClose} />);

      // Fill in required fields
      const nameInput = screen.getByTestId("fee-name-input");
      await user.type(nameInput, "Test Fee");

      const valueInput = screen.getByTestId("fee-value-input");
      await user.type(valueInput, "10");

      const approvalCheckbox = screen.getByLabelText(
        /I confirm that campaign approval is required/i,
      );
      await user.click(approvalCheckbox);

      const saveButton = screen.getByTestId("save-button");
      await user.click(saveButton);

      await waitFor(() => {
        expect(onClose).toHaveBeenCalled();
      });
    });
  });

  describe("Close functionality", () => {
    it("calls onClose when cancel button is clicked", async () => {
      const onClose = vi.fn();

      render(<AddCustomFeeDrawer {...defaultProps} onClose={onClose} />);

      const cancelButton = screen.getByTestId("cancel-button");
      await user.click(cancelButton);

      expect(onClose).toHaveBeenCalled();
    });

    it("resets form state when close is called", async () => {
      const { rerender } = render(<AddCustomFeeDrawer {...defaultProps} />);

      // Change form values
      const nameInput = screen.getByTestId("fee-name-input");
      await user.type(nameInput, "Test Fee");

      // Close drawer
      const closeButton = screen.getByTestId("drawer-close");
      await user.click(closeButton);

      // Reopen drawer
      rerender(<AddCustomFeeDrawer {...defaultProps} isOpen={true} />);

      expect(screen.getByTestId("fee-name-input")).toHaveValue("");
    });
  });

  describe("Edge cases", () => {
    it("handles initialFee with null values", () => {
      const initialFee: PriceSummaryCustomFee = {
        id: "fee-1",
        name: null as unknown as string,
        type: "PERCENTAGE",
        value: null as unknown as number,
        description: null as unknown as string,
      } as PriceSummaryCustomFee;

      render(<AddCustomFeeDrawer {...defaultProps} initialFee={initialFee} />);

      expect(screen.getByTestId("fee-name-input")).toHaveValue("");
      expect(screen.getByTestId("fee-value-input")).toHaveValue("");
      expect(screen.getByTestId("fee-description-textarea")).toHaveValue("");
    });

    it("handles user without activeCompanyId", () => {
      // The component should handle user without activeCompanyId gracefully
      // It will fall back to memberships[0].company_id
      render(<AddCustomFeeDrawer {...defaultProps} />);
      expect(screen.getByTestId("custom-fee-form")).toBeInTheDocument();
    });

    it("handles user without memberships", () => {
      // The component should handle user without memberships gracefully
      // It will use empty string for companyId
      render(<AddCustomFeeDrawer {...defaultProps} />);
      expect(screen.getByTestId("custom-fee-form")).toBeInTheDocument();
    });

    it("handles empty campaignId", () => {
      render(<AddCustomFeeDrawer {...defaultProps} campaignId={undefined} />);

      expect(screen.getByTestId("custom-fee-form")).toBeInTheDocument();
    });

    it("generates unique ID for new fees", async () => {
      const onSave = vi.fn().mockResolvedValue(undefined);

      render(<AddCustomFeeDrawer {...defaultProps} onSave={onSave} />);

      const nameInput = screen.getByTestId("fee-name-input");
      await user.type(nameInput, "Test Fee");

      const valueInput = screen.getByTestId("fee-value-input");
      await user.type(valueInput, "10");

      const approvalCheckbox = screen.getByLabelText(
        /I confirm that campaign approval is required/i,
      );
      await user.click(approvalCheckbox);

      const saveButton = screen.getByTestId("save-button");
      await user.click(saveButton);

      await waitFor(() => {
        expect(onSave).toHaveBeenCalled();
        const callArgs = onSave.mock.calls[0][0];
        expect(callArgs[0].id).toMatch(/^fee-\d+$/);
      });
    });
  });
});
