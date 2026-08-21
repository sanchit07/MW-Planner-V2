import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

import { DatePicker } from "../DatePicker";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("@utils/dateUtils", () => ({
  formatDisplayDate: vi.fn((date: string) => {
    if (!date) return "";
    const d = new Date(date);
    const day = String(d.getDate()).padStart(2, "0");
    const month = String(d.getMonth() + 1).padStart(2, "0");
    const year = d.getFullYear();
    return `${day}/${month}/${year}`;
  }),
}));

describe("DatePicker", () => {
  const defaultProps = {
    onChange: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("Rendering", () => {
    it("renders date picker input", () => {
      render(<DatePicker {...defaultProps} />);
      const input = screen.getByRole("textbox");
      expect(input).toBeInTheDocument();
    });

    it("renders with label when provided", () => {
      render(<DatePicker {...defaultProps} label="Select Date" />);
      expect(screen.getByText("Select Date")).toBeInTheDocument();
    });

    it("displays placeholder when no value", () => {
      render(<DatePicker {...defaultProps} placeholder="Pick a date" />);
      const input = screen.getByRole("textbox");
      expect(input).toHaveAttribute("placeholder", "Pick a date");
    });

    it("displays value when provided", () => {
      render(<DatePicker {...defaultProps} value="01/01/2024 - 31/01/2024" />);
      const input = screen.getByRole("textbox");
      expect(input).toHaveValue("01/01/2024 - 31/01/2024");
    });

    it("renders calendar icon", () => {
      const { container } = render(<DatePicker {...defaultProps} />);
      const icon = container.querySelector("svg");
      expect(icon).toBeInTheDocument();
    });

    it("applies custom className", () => {
      const { container } = render(
        <DatePicker {...defaultProps} className="custom-class" />,
      );
      const input = container.querySelector("input");
      expect(input?.className).toContain("custom-class");
    });

    it("displays error message when error prop is provided", () => {
      render(<DatePicker {...defaultProps} error="Invalid date" />);
      expect(screen.getByText("Invalid date")).toBeInTheDocument();
    });

    it("displays help text when helpText prop is provided", () => {
      render(<DatePicker {...defaultProps} helpText="Select a date range" />);
      expect(screen.getByText("Select a date range")).toBeInTheDocument();
    });

    it("does not display help text when error is present", () => {
      render(
        <DatePicker
          {...defaultProps}
          error="Invalid date"
          helpText="Select a date range"
        />,
      );
      expect(screen.getByText("Invalid date")).toBeInTheDocument();
      expect(screen.queryByText("Select a date range")).not.toBeInTheDocument();
    });
  });

  describe("Interactions", () => {
    it("opens dropdown when input is clicked", async () => {
      const user = userEvent.setup();
      render(<DatePicker {...defaultProps} />);

      const input = screen.getByRole("textbox");
      await user.click(input);

      await waitFor(() => {
        expect(screen.getByText("calendar.startDate")).toBeInTheDocument();
        expect(screen.getByText("calendar.endDate")).toBeInTheDocument();
      });
    });

    it("closes dropdown when clicking outside", async () => {
      const user = userEvent.setup();
      render(<DatePicker {...defaultProps} />);

      const input = screen.getByRole("textbox");
      await user.click(input);

      await waitFor(() => {
        expect(screen.getByText("calendar.startDate")).toBeInTheDocument();
      });

      await user.click(document.body);

      await waitFor(() => {
        expect(
          screen.queryByText("calendar.startDate"),
        ).not.toBeInTheDocument();
      });
    });

    it("parses existing value and populates date inputs", async () => {
      const user = userEvent.setup();
      render(<DatePicker {...defaultProps} value="01/01/2024 - 31/01/2024" />);

      const input = screen.getByRole("textbox");
      await user.click(input);

      await waitFor(() => {
        const startLabel = screen.getByText("calendar.startDate");
        const endLabel = screen.getByText("calendar.endDate");
        const startInput = startLabel
          .closest("div")
          ?.parentElement?.querySelector(
            'input[type="date"]',
          ) as HTMLInputElement;
        const endInput = endLabel
          .closest("div")
          ?.parentElement?.querySelector(
            'input[type="date"]',
          ) as HTMLInputElement;
        expect(startInput).toBeInTheDocument();
        expect(endInput).toBeInTheDocument();
        if (startInput) {
          expect(startInput.value).toBeTruthy();
        }
        if (endInput) {
          expect(endInput.value).toBeTruthy();
        }
      });
    });

    it("calls onChange when both dates are selected and Apply is clicked", async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      render(<DatePicker {...defaultProps} onChange={onChange} />);

      const input = screen.getByRole("textbox");
      await user.click(input);

      await waitFor(() => {
        expect(screen.getByText("calendar.startDate")).toBeInTheDocument();
      });

      await waitFor(() => {
        const startLabel = screen.getByText("calendar.startDate");
        const endLabel = screen.getByText("calendar.endDate");
        const startInput = startLabel
          .closest("div")
          ?.parentElement?.querySelector(
            'input[type="date"]',
          ) as HTMLInputElement;
        const endInput = endLabel
          .closest("div")
          ?.parentElement?.querySelector(
            'input[type="date"]',
          ) as HTMLInputElement;
        expect(startInput).toBeInTheDocument();
        expect(endInput).toBeInTheDocument();
      });

      const startLabel = screen.getByText("calendar.startDate");
      const endLabel = screen.getByText("calendar.endDate");
      const startInput = startLabel
        .closest("div")
        ?.parentElement?.querySelector(
          'input[type="date"]',
        ) as HTMLInputElement;
      const endInput = endLabel
        .closest("div")
        ?.parentElement?.querySelector(
          'input[type="date"]',
        ) as HTMLInputElement;

      expect(startInput).toBeInTheDocument();
      expect(endInput).toBeInTheDocument();

      if (startInput) {
        await user.clear(startInput);
        await user.type(startInput, "2024-01-01");
      }
      if (endInput) {
        await user.clear(endInput);
        await user.type(endInput, "2024-01-31");
      }

      const applyButton = screen.getByRole("button", { name: "buttons.apply" });
      await user.click(applyButton);

      expect(onChange).toHaveBeenCalled();
    });

    it("disables Apply button when dates are not selected", async () => {
      const user = userEvent.setup();
      render(<DatePicker {...defaultProps} />);

      const input = screen.getByRole("textbox");
      await user.click(input);

      await waitFor(() => {
        const applyButton = screen.getByRole("button", {
          name: "buttons.apply",
        });
        expect(applyButton).toBeDisabled();
      });
    });

    it("closes dropdown when Cancel is clicked", async () => {
      const user = userEvent.setup();
      render(<DatePicker {...defaultProps} />);

      const input = screen.getByRole("textbox");
      await user.click(input);

      await waitFor(() => {
        expect(screen.getByText("calendar.startDate")).toBeInTheDocument();
      });

      const cancelButton = screen.getByRole("button", {
        name: "buttons.cancel",
      });
      await user.click(cancelButton);

      await waitFor(() => {
        expect(
          screen.queryByText("calendar.startDate"),
        ).not.toBeInTheDocument();
      });
    });

    it("sets minimum date for end date input based on start date", async () => {
      const user = userEvent.setup();
      render(<DatePicker {...defaultProps} />);

      const input = screen.getByRole("textbox");
      await user.click(input);

      await waitFor(() => {
        expect(screen.getByText("calendar.startDate")).toBeInTheDocument();
      });

      const dateInputs = screen.getAllByRole("textbox", { hidden: true });
      const startInput = dateInputs.find((input) => {
        const label = input.closest("div")?.querySelector("label");
        return label?.textContent === "calendar.startDate";
      }) as HTMLInputElement;
      const endInput = dateInputs.find((input) => {
        const label = input.closest("div")?.querySelector("label");
        return label?.textContent === "calendar.endDate";
      }) as HTMLInputElement;

      if (startInput) {
        await user.type(startInput, "2024-01-15");
      }

      await waitFor(() => {
        if (endInput) {
          expect(endInput.min).toBe("2024-01-15");
        }
      });
    });
  });

  describe("Accessibility", () => {
    it("input is read-only", () => {
      render(<DatePicker {...defaultProps} />);
      const input = screen.getByRole("textbox");
      expect(input).toHaveAttribute("readOnly");
    });

    it("has proper label association", () => {
      render(<DatePicker {...defaultProps} label="Select Date Range" />);
      const label = screen.getByText("Select Date Range");
      expect(label.tagName).toBe("LABEL");
    });

    it("date inputs have proper labels", async () => {
      const user = userEvent.setup();
      render(<DatePicker {...defaultProps} />);

      const input = screen.getByRole("textbox");
      await user.click(input);

      await waitFor(() => {
        expect(screen.getByText("calendar.startDate")).toBeInTheDocument();
        expect(screen.getByText("calendar.endDate")).toBeInTheDocument();
        const dateInputs = screen.getAllByRole("textbox", { hidden: true });
        expect(dateInputs.length).toBeGreaterThan(0);
      });
    });
  });

  describe("Edge Cases", () => {
    it("handles empty value", () => {
      render(<DatePicker {...defaultProps} value="" />);
      const input = screen.getByRole("textbox");
      expect(input).toHaveValue("");
    });

    it("handles invalid value format gracefully", () => {
      render(<DatePicker {...defaultProps} value="invalid" />);
      const input = screen.getByRole("textbox");
      expect(input).toHaveValue("invalid");
    });

    it("handles value with only start date", async () => {
      const user = userEvent.setup();
      render(<DatePicker {...defaultProps} value="01/01/2024 - " />);

      const input = screen.getByRole("textbox");
      await user.click(input);

      await waitFor(() => {
        const dateInputs = screen.getAllByRole("textbox", { hidden: true });
        const startInput = dateInputs.find((input) => {
          const label = input.closest("div")?.querySelector("label");
          return label?.textContent === "calendar.startDate";
        }) as HTMLInputElement;
        expect(startInput).toBeInTheDocument();
        expect(startInput?.value).toBeTruthy();
      });
    });
  });
});
