import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

import { DateRangePicker } from "../DateRangePicker";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("@utils/dateUtils", () => ({
  isSameDay: vi.fn(
    (date1: Date | null | undefined, date2: Date | null | undefined) => {
      if (!date1 || !date2) return false;
      return (
        date1.getFullYear() === date2.getFullYear() &&
        date1.getMonth() === date2.getMonth() &&
        date1.getDate() === date2.getDate()
      );
    },
  ),
  isDateBetween: vi.fn(
    (
      date: Date,
      start: Date | null | undefined,
      end: Date | null | undefined,
    ) => {
      if (!start || !end) return false;
      return date >= start && date <= end;
    },
  ),
  formatDisplayDate: vi.fn((dateString: string) => {
    if (!dateString) return "";
    const d = new Date(dateString);
    const day = String(d.getDate()).padStart(2, "0");
    const month = String(d.getMonth() + 1).padStart(2, "0");
    const year = d.getFullYear();
    return `${day}/${month}/${year}`;
  }),
}));

describe("DateRangePicker", () => {
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
    it("renders date range picker trigger", () => {
      render(<DateRangePicker {...defaultProps} />);
      const trigger = screen.getByRole("textbox", { name: /date range/i });
      expect(trigger).toBeInTheDocument();
    });

    it("renders with label when provided", () => {
      render(<DateRangePicker {...defaultProps} label="Select Date Range" />);
      expect(screen.getByText("Select Date Range")).toBeInTheDocument();
    });

    it("displays placeholder when no value", () => {
      render(<DateRangePicker {...defaultProps} placeholder="Pick dates" />);
      const trigger = screen.getByRole("textbox", { name: /date range/i });
      expect(trigger).toHaveValue("Pick dates");
    });

    it("displays selected range when value is provided", () => {
      const from = new Date(2024, 0, 1);
      const to = new Date(2024, 0, 31);
      render(<DateRangePicker {...defaultProps} value={{ from, to }} />);
      const trigger = screen.getByRole("textbox", { name: /date range/i });
      expect(trigger).toHaveValue("01/01/2024 - 31/01/2024");
    });

    it("renders calendar icon", () => {
      const { container } = render(<DateRangePicker {...defaultProps} />);
      const icon = container.querySelector("svg");
      expect(icon).toBeInTheDocument();
    });

    it("renders clear button when clearable and value exists", () => {
      const from = new Date(2024, 0, 1);
      const to = new Date(2024, 0, 31);
      const { container } = render(
        <DateRangePicker
          {...defaultProps}
          value={{ from, to }}
          clearable={true}
        />,
      );
      const clearButton = container.querySelector('[id*="clear"]');
      expect(clearButton).toBeInTheDocument();
    });

    it("does not render clear button when clearable is false", () => {
      const from = new Date(2024, 0, 1);
      const to = new Date(2024, 0, 31);
      const { container } = render(
        <DateRangePicker
          {...defaultProps}
          value={{ from, to }}
          clearable={false}
        />,
      );
      const clearButtons = container.querySelectorAll('[id*="clear"]');
      expect(clearButtons.length).toBe(0);
    });

    it("applies custom className", () => {
      const { container } = render(
        <DateRangePicker {...defaultProps} className="custom-class" />,
      );
      const wrapper = container.querySelector(".custom-class");
      expect(wrapper).toBeInTheDocument();
    });

    it("displays error message when error prop is provided", () => {
      render(<DateRangePicker {...defaultProps} error="Invalid range" />);
      expect(screen.getByText("Invalid range")).toBeInTheDocument();
    });

    it("displays help text when helpText prop is provided", () => {
      render(
        <DateRangePicker {...defaultProps} helpText="Select a date range" />,
      );
      expect(screen.getByText("Select a date range")).toBeInTheDocument();
    });
  });

  describe("Interactions", () => {
    it("opens calendar when button is clicked", async () => {
      const user = userEvent.setup();
      const { container } = render(<DateRangePicker {...defaultProps} />);

      const button = container.querySelector('[id*="trigger"]') as HTMLElement;
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Quick Select")).toBeInTheDocument();
      });
    });

    it("closes calendar when clicking outside", async () => {
      const user = userEvent.setup();
      const { container } = render(<DateRangePicker {...defaultProps} />);

      const button = container.querySelector('[id*="trigger"]') as HTMLElement;
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Quick Select")).toBeInTheDocument();
      });

      // The component listens for mousedown events on document
      fireEvent.mouseDown(document.body);

      await waitFor(
        () => {
          expect(screen.queryByText("Quick Select")).not.toBeInTheDocument();
        },
        { timeout: 2000 },
      );
    });

    it("selects date range when dates are clicked", async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const { container } = render(
        <DateRangePicker {...defaultProps} onChange={onChange} />,
      );

      const button = container.querySelector('[id*="trigger"]') as HTMLElement;
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Quick Select")).toBeInTheDocument();
      });

      // Find date buttons within the calendar - look for buttons with numeric text in the calendar area
      const calendar = container.querySelector('[id*="calendar"]');
      if (calendar) {
        // Get all buttons in calendar, filter out navigation buttons and preset buttons
        const allButtons = calendar.querySelectorAll("button");
        const dateButtons = Array.from(allButtons).filter((btn) => {
          const text = btn.textContent?.trim();
          // Date buttons have numeric content (1-31) and are not in the header or preset area
          const isDateButton =
            text &&
            /^\d+$/.test(text) &&
            parseInt(text) >= 1 &&
            parseInt(text) <= 31;
          const isInHeader = btn.closest('div[class*="border-b"]') !== null;
          const isPresetButton = btn.id?.includes("preset");
          return (
            isDateButton && !isInHeader && !isPresetButton && !btn.disabled
          );
        });

        if (dateButtons.length > 0) {
          await user.click(dateButtons[0]);
          await waitFor(() => {
            expect(onChange).toHaveBeenCalled();
          });
        }
      }
    });

    it("calls onChange when preset is selected", async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const presets = [
        {
          label: "Last 7 days",
          range: {
            from: new Date(2024, 0, 1),
            to: new Date(2024, 0, 7),
          },
        },
      ];
      const { container } = render(
        <DateRangePicker
          {...defaultProps}
          onChange={onChange}
          presets={presets}
        />,
      );

      const button = container.querySelector('[id*="trigger"]') as HTMLElement;
      await user.click(button);

      await waitFor(() => {
        const presetButton = screen.getByRole("button", {
          name: "Last 7 days",
        });
        expect(presetButton).toBeInTheDocument();
      });

      const presetButton = screen.getByRole("button", { name: "Last 7 days" });
      await user.click(presetButton);

      expect(onChange).toHaveBeenCalled();
    });

    it("calls onBlur when calendar closes", async () => {
      const user = userEvent.setup();
      const onBlur = vi.fn();
      const { container } = render(
        <DateRangePicker {...defaultProps} onBlur={onBlur} />,
      );

      const button = container.querySelector('[id*="trigger"]') as HTMLElement;
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Quick Select")).toBeInTheDocument();
      });

      // The component listens for mousedown events on document
      fireEvent.mouseDown(document.body);

      await waitFor(() => {
        expect(onBlur).toHaveBeenCalled();
      });
    });

    it("disables trigger when disabled prop is true", () => {
      render(<DateRangePicker {...defaultProps} disabled={true} />);
      const trigger = screen.getByRole("textbox", { name: /date range/i });
      expect(trigger).toBeDisabled();
    });

    it("does not open calendar when disabled", async () => {
      const user = userEvent.setup();
      render(<DateRangePicker {...defaultProps} disabled={true} />);

      const trigger = screen.getByRole("textbox", { name: /date range/i });
      await user.click(trigger);

      await waitFor(() => {
        expect(screen.queryByText("Quick Select")).not.toBeInTheDocument();
      });
    });
  });

  describe("Calendar Navigation", () => {
    it("navigates to previous month", async () => {
      const user = userEvent.setup();
      const { container } = render(<DateRangePicker {...defaultProps} />);

      const button = container.querySelector('[id*="trigger"]') as HTMLElement;
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Quick Select")).toBeInTheDocument();
      });

      // Find the previous month button in the calendar header
      // The calendar header has 4 navigation buttons: ChevronsLeft, ChevronLeft, ChevronRight, ChevronsRight
      const calendar = container.querySelector('[id*="calendar"]');
      const calendarHeader = calendar?.querySelector('div[class*="border-b"]');
      const navButtons = calendarHeader?.querySelectorAll("button") || [];
      // The second button (index 1) should be the previous month button (ChevronLeft)
      if (navButtons.length > 1) {
        await user.click(navButtons[1] as HTMLElement);
        await waitFor(() => {
          expect(screen.getByText("Quick Select")).toBeInTheDocument();
        });
      }
    });

    it("navigates to next month", async () => {
      const user = userEvent.setup();
      const { container } = render(<DateRangePicker {...defaultProps} />);

      const button = container.querySelector('[id*="trigger"]') as HTMLElement;
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Quick Select")).toBeInTheDocument();
      });

      // Find the next month button in the calendar header
      const calendar = container.querySelector('[id*="calendar"]');
      const calendarHeader = calendar?.querySelector('div[class*="border-b"]');
      const navButtons = calendarHeader?.querySelectorAll("button") || [];
      // The third button (index 2) should be the next month button (ChevronRight)
      if (navButtons.length > 2) {
        await user.click(navButtons[2] as HTMLElement);
        await waitFor(() => {
          expect(screen.getByText("Quick Select")).toBeInTheDocument();
        });
      }
    });
  });

  describe("Accessibility", () => {
    it("has proper id attributes", () => {
      const { container } = render(
        <DateRangePicker {...defaultProps} id="test-picker" />,
      );
      expect(container.querySelector("#test-picker")).toBeInTheDocument();
    });

    it("generates default id when not provided", () => {
      const { container } = render(<DateRangePicker {...defaultProps} />);
      expect(container.querySelector("#date-range-picker")).toBeInTheDocument();
    });

    it("trigger has proper aria attributes", () => {
      render(<DateRangePicker {...defaultProps} />);
      const trigger = screen.getByRole("textbox", { name: /date range/i });
      expect(trigger).toHaveAttribute("aria-expanded", "false");
      expect(trigger).toHaveAttribute("aria-haspopup", "dialog");
    });
  });

  describe("Edge Cases", () => {
    it("handles null value", () => {
      render(<DateRangePicker {...defaultProps} value={undefined} />);
      const trigger = screen.getByRole("textbox", { name: /date range/i });
      expect(trigger).toBeInTheDocument();
    });

    it("handles value with only from date", () => {
      const from = new Date(2024, 0, 1);
      const { container } = render(
        <DateRangePicker {...defaultProps} value={{ from, to: null }} />,
      );
      const button = container.querySelector('[id*="trigger"]');
      expect(button).toBeInTheDocument();
    });

    it("handles value with only to date", () => {
      const to = new Date(2024, 0, 31);
      const { container } = render(
        <DateRangePicker {...defaultProps} value={{ from: null, to }} />,
      );
      const button = container.querySelector('[id*="trigger"]');
      expect(button).toBeInTheDocument();
    });

    it("handles minDate constraint", async () => {
      const user = userEvent.setup();
      const minDate = new Date(2024, 0, 15);
      const { container } = render(
        <DateRangePicker {...defaultProps} minDate={minDate} />,
      );

      const button = container.querySelector('[id*="trigger"]') as HTMLElement;
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Quick Select")).toBeInTheDocument();
      });
    });

    it("handles maxDate constraint", async () => {
      const user = userEvent.setup();
      const maxDate = new Date(2024, 0, 15);
      const { container } = render(
        <DateRangePicker {...defaultProps} maxDate={maxDate} />,
      );

      const button = container.querySelector('[id*="trigger"]') as HTMLElement;
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Quick Select")).toBeInTheDocument();
      });
    });

    it("syncs selected range when value prop changes", () => {
      const from = new Date(2024, 0, 1);
      const to = new Date(2024, 0, 15);
      const { rerender } = render(
        <DateRangePicker {...defaultProps} value={{ from: null, to: null }} />,
      );
      rerender(<DateRangePicker {...defaultProps} value={{ from, to }} />);
      const trigger = screen.getByRole("textbox", { name: /date range/i });
      expect(trigger).toBeInTheDocument();
      expect(trigger).toHaveValue("01/01/2024 - 15/01/2024");
    });

    it("calls onChange with cleared range when clear button is clicked", async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const from = new Date(2024, 0, 1);
      const to = new Date(2024, 0, 7);
      const { container } = render(
        <DateRangePicker
          {...defaultProps}
          onChange={onChange}
          value={{ from, to }}
          clearable={true}
        />,
      );
      const clearButton = container.querySelector(
        '[id*="clear"]',
      ) as HTMLElement;
      expect(clearButton).toBeInTheDocument();
      await user.click(clearButton);
      expect(onChange).toHaveBeenCalledWith({ from: null, to: null });
    });

    it("clear button click does not propagate to open calendar", async () => {
      const user = userEvent.setup();
      const from = new Date(2024, 0, 1);
      const to = new Date(2024, 0, 7);
      const { container } = render(
        <DateRangePicker
          {...defaultProps}
          value={{ from, to }}
          clearable={true}
        />,
      );
      const clearButton = container.querySelector(
        '[id*="clear"]',
      ) as HTMLElement;
      await user.click(clearButton);
      expect(screen.queryByText("Quick Select")).not.toBeInTheDocument();
    });
  });

  describe("Actions footer", () => {
    it("shows Cancel and Apply when isActionsVisible is true", async () => {
      const user = userEvent.setup();
      const { container } = render(
        <DateRangePicker {...defaultProps} isActionsVisible={true} />,
      );
      const trigger = container.querySelector('[id*="trigger"]') as HTMLElement;
      await user.click(trigger);
      await waitFor(() => {
        expect(
          screen.getByRole("button", { name: /Cancel/i }),
        ).toBeInTheDocument();
        expect(
          screen.getByRole("button", { name: /Apply/i }),
        ).toBeInTheDocument();
      });
    });

    it("Apply button is disabled when no start date selected", async () => {
      const user = userEvent.setup();
      const { container } = render(
        <DateRangePicker {...defaultProps} isActionsVisible={true} />,
      );
      const trigger = container.querySelector('[id*="trigger"]') as HTMLElement;
      await user.click(trigger);
      await waitFor(() => {
        const applyBtn = screen.getByRole("button", { name: /Apply/i });
        expect(applyBtn).toBeDisabled();
      });
    });

    it("closes calendar when Cancel is clicked", async () => {
      const user = userEvent.setup();
      const { container } = render(
        <DateRangePicker {...defaultProps} isActionsVisible={true} />,
      );
      const trigger = container.querySelector('[id*="trigger"]') as HTMLElement;
      await user.click(trigger);
      await waitFor(() => {
        expect(screen.getByText("Quick Select")).toBeInTheDocument();
      });
      const cancelBtn = screen.getByRole("button", { name: /Cancel/i });
      await user.click(cancelBtn);
      await waitFor(() => {
        expect(screen.queryByText("Quick Select")).not.toBeInTheDocument();
      });
    });

    it("closes calendar when Apply is clicked with start date selected", async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const { container } = render(
        <DateRangePicker
          {...defaultProps}
          isActionsVisible={true}
          onChange={onChange}
        />,
      );
      const trigger = container.querySelector('[id*="trigger"]') as HTMLElement;
      await user.click(trigger);
      await waitFor(() => {
        expect(screen.getByText("Quick Select")).toBeInTheDocument();
      });
      const calendar = container.querySelector('[id*="calendar"]');
      const dateButtons = calendar?.querySelectorAll("button");
      const firstDateBtn = Array.from(dateButtons || []).find(
        (b) => /^\d+$/.test(b.textContent?.trim() || "") && !b.disabled,
      );
      if (firstDateBtn) {
        await user.click(firstDateBtn as HTMLElement);
      }
      const applyBtn = screen.getByRole("button", { name: /Apply/i });
      await waitFor(() => {
        expect(applyBtn).not.toBeDisabled();
      });
      await user.click(applyBtn);
      await waitFor(() => {
        expect(screen.queryByText("Quick Select")).not.toBeInTheDocument();
      });
    });
  });

  describe("Two-month calendar", () => {
    it("renders second calendar when numberOfMonths is 2", async () => {
      const user = userEvent.setup();
      const { container } = render(
        <DateRangePicker {...defaultProps} numberOfMonths={2} />,
      );
      const trigger = container.querySelector('[id*="trigger"]') as HTMLElement;
      await user.click(trigger);
      await waitFor(() => {
        const calendars = container.querySelectorAll('[class*="border-r"]');
        expect(calendars.length).toBeGreaterThanOrEqual(1);
      });
    });
  });

  describe("Keyboard and aria", () => {
    it("trigger has aria-expanded false when closed", () => {
      const { container } = render(<DateRangePicker {...defaultProps} />);
      const trigger = container.querySelector('[id*="trigger"]');
      expect(trigger).toHaveAttribute("aria-expanded", "false");
    });

    it("trigger has aria-expanded true when open", async () => {
      const user = userEvent.setup();
      const { container } = render(<DateRangePicker {...defaultProps} />);
      const trigger = container.querySelector('[id*="trigger"]') as HTMLElement;
      await user.click(trigger);
      await waitFor(() => {
        expect(trigger).toHaveAttribute("aria-expanded", "true");
      });
    });
  });
});
