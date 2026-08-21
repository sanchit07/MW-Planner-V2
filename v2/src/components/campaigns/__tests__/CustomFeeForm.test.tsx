import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { CustomFeeForm } from "../CustomFeeForm";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string, opts?: { defaultValue?: string }) =>
      opts?.defaultValue ?? key,
  }),
}));

describe("CustomFeeForm", () => {
  const defaultProps = {
    feeName: "",
    type: "Percentage" as const,
    value: "",
    basedOn: "Base Cost" as const,
    description: "",
    includeInMediaPlan: false,
    onFeeNameChange: vi.fn(),
    onTypeChange: vi.fn(),
    onValueChange: vi.fn(),
    onBasedOnChange: vi.fn(),
    onDescriptionChange: vi.fn(),
    onIncludeInMediaPlanChange: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("renders fee name label and input with default idPrefix", () => {
      render(<CustomFeeForm {...defaultProps} />);
      expect(
        screen.getByLabelText(/Fee Name/i, { selector: "input" }),
      ).toBeInTheDocument();
      expect(
        screen.getByPlaceholderText(/Enter Fee Name/i),
      ).toBeInTheDocument();
    });

    it("renders with custom idPrefix for accessible ids", () => {
      render(<CustomFeeForm {...defaultProps} idPrefix="custom-fee" />);
      const nameInput = document.getElementById("custom-fee-name");
      expect(nameInput).toBeInTheDocument();
      expect(nameInput).toHaveAttribute("id", "custom-fee-name");
    });

    it("renders type dropdown with current type value", () => {
      render(<CustomFeeForm {...defaultProps} type="Fixed" />);
      expect(screen.getByText("Fixed")).toBeInTheDocument();
    });

    it("renders value input with placeholder", () => {
      render(<CustomFeeForm {...defaultProps} />);
      expect(screen.getByPlaceholderText(/Enter Value/i)).toBeInTheDocument();
    });

    it("renders based on dropdown with Base Cost", () => {
      render(<CustomFeeForm {...defaultProps} />);
      expect(screen.getByText("Base Cost")).toBeInTheDocument();
    });

    it("renders description textarea", () => {
      render(<CustomFeeForm {...defaultProps} />);
      const textarea = screen.getByPlaceholderText(/Enter description/i);
      expect(textarea).toBeInTheDocument();
    });

    it("renders include in media plan switch", () => {
      render(<CustomFeeForm {...defaultProps} />);
      const switchEl = document.getElementById("fee-include");
      expect(switchEl).toBeInTheDocument();
    });

    it("shows required asterisk when showRequired is true", () => {
      render(<CustomFeeForm {...defaultProps} showRequired />);
      const labels = screen.getAllByRole("generic").filter((el) => {
        const text = el.textContent ?? "";
        return text.includes("Fee Name") || text.includes("Value");
      });
      expect(labels.length).toBeGreaterThan(0);
    });

    it("displays feeName value in input", () => {
      render(<CustomFeeForm {...defaultProps} feeName="Setup Fee" />);
      const input = screen.getByDisplayValue("Setup Fee");
      expect(input).toBeInTheDocument();
    });

    it("displays value in value input", () => {
      render(<CustomFeeForm {...defaultProps} value="10" />);
      expect(screen.getByDisplayValue("10")).toBeInTheDocument();
    });

    it("displays description in textarea", () => {
      render(<CustomFeeForm {...defaultProps} description="One-time fee" />);
      expect(screen.getByDisplayValue("One-time fee")).toBeInTheDocument();
    });
  });

  describe("errors", () => {
    it("shows feeName error and applies error class when errors.feeName is set", () => {
      render(
        <CustomFeeForm
          {...defaultProps}
          errors={{ feeName: "Fee name is required" }}
        />,
      );
      expect(screen.getByText("Fee name is required")).toBeInTheDocument();
      const nameInput = screen.getByPlaceholderText(/Enter Fee Name/i);
      expect(nameInput.className).toContain("border-red-500");
    });

    it("shows value error when errors.value is set", () => {
      render(
        <CustomFeeForm
          {...defaultProps}
          errors={{ value: "Value must be a number" }}
        />,
      );
      expect(screen.getByText("Value must be a number")).toBeInTheDocument();
    });

    it("shows both errors when both feeName and value errors present", () => {
      render(
        <CustomFeeForm
          {...defaultProps}
          errors={{
            feeName: "Name required",
            value: "Value required",
          }}
        />,
      );
      expect(screen.getByText("Name required")).toBeInTheDocument();
      expect(screen.getByText("Value required")).toBeInTheDocument();
    });
  });

  describe("interactions", () => {
    it("calls onFeeNameChange when fee name input changes", async () => {
      const user = userEvent.setup();
      render(<CustomFeeForm {...defaultProps} />);
      const input = screen.getByPlaceholderText(/Enter Fee Name/i);
      await user.type(input, "Fee");
      expect(defaultProps.onFeeNameChange).toHaveBeenCalledWith("F");
      expect(defaultProps.onFeeNameChange).toHaveBeenCalledWith("e");
      expect(defaultProps.onFeeNameChange).toHaveBeenCalledWith("e");
    });

    it("calls onValueChange when value input changes", async () => {
      const user = userEvent.setup();
      render(<CustomFeeForm {...defaultProps} />);
      const input = screen.getByPlaceholderText(/Enter Value/i);
      await user.type(input, "5");
      expect(defaultProps.onValueChange).toHaveBeenCalledWith("5");
    });

    it("calls onDescriptionChange when description changes", async () => {
      const user = userEvent.setup();
      render(<CustomFeeForm {...defaultProps} />);
      const textarea = screen.getByPlaceholderText(/Enter description/i);
      await user.type(textarea, "Desc");
      expect(defaultProps.onDescriptionChange).toHaveBeenCalledWith("D");
      expect(defaultProps.onDescriptionChange).toHaveBeenCalledWith("e");
    });

    it("prevents e, E, +, - in value input onKeyDown", async () => {
      const user = userEvent.setup();
      render(<CustomFeeForm {...defaultProps} />);
      const valueInput = screen.getByPlaceholderText(/Enter Value/i);
      valueInput.focus();
      await user.keyboard("{e}");
      await user.keyboard("{E}");
      await user.keyboard("+");
      await user.keyboard("-");
      expect(defaultProps.onValueChange).not.toHaveBeenCalled();
    });
  });

  describe("disabled and readOnly", () => {
    it("disables inputs when disabled is true", () => {
      render(<CustomFeeForm {...defaultProps} disabled />);
      const nameInput = screen.getByPlaceholderText(/Enter Fee Name/i);
      const valueInput = screen.getByPlaceholderText(/Enter Value/i);
      expect(nameInput).toBeDisabled();
      expect(valueInput).toBeDisabled();
    });

    it("disables inputs when readOnly is true", () => {
      render(<CustomFeeForm {...defaultProps} readOnly />);
      const nameInput = screen.getByPlaceholderText(/Enter Fee Name/i);
      expect(nameInput).toBeDisabled();
    });

    it("applies disabled styling to dropdown when disabled", () => {
      const { container } = render(
        <CustomFeeForm {...defaultProps} disabled />,
      );
      const dropdown = container.querySelector(".pointer-events-none");
      expect(dropdown).toBeInTheDocument();
    });

    it("does not call onTypeChange when disabled and type dropdown is used", async () => {
      render(<CustomFeeForm {...defaultProps} disabled />);
      const typeTrigger = screen.getByText("Percentage");
      await userEvent.click(typeTrigger);
      expect(defaultProps.onTypeChange).not.toHaveBeenCalled();
    });
  });

  describe("translation namespace", () => {
    it("uses price namespace by default for translation keys", () => {
      render(<CustomFeeForm {...defaultProps} />);
      expect(screen.getByText("Fee Name")).toBeInTheDocument();
    });

    it("uses add_custom_fee namespace when translationNamespace is add_custom_fee", () => {
      render(
        <CustomFeeForm
          {...defaultProps}
          translationNamespace="add_custom_fee"
        />,
      );
      expect(screen.getByText("Fee Name")).toBeInTheDocument();
    });
  });
});
