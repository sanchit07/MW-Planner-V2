import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

import { CalendarView, CalendarEvent, HourlyEvent } from "../CalendarView";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("../Tooltip", () => ({
  Tooltip: ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  ),
}));

describe("CalendarView", () => {
  const defaultEvents: CalendarEvent[] = [
    {
      date: new Date(2024, 0, 15),
      customContent: <div>Event 1</div>,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("Rendering", () => {
    it("renders calendar in monthly view by default", () => {
      render(<CalendarView />);
      expect(screen.getByText("calendar.dayNamesShort.0")).toBeInTheDocument();
      expect(screen.getByText("calendar.dayNamesShort.1")).toBeInTheDocument();
    });

    it("renders calendar in weekly view", () => {
      const { container } = render(<CalendarView viewMode="weekly" />);
      const headerRow = container.querySelector(".grid.bg-mw-neutral-50");
      const headers = headerRow?.querySelectorAll("div");
      const hasSun = Array.from(headers || []).some((el) =>
        el.textContent?.includes("calendar.dayNamesShort.0"),
      );
      expect(hasSun).toBe(true);
    });

    it("renders calendar in daily view", () => {
      render(<CalendarView viewMode="daily" />);
      expect(screen.getByText(/00:00/)).toBeInTheDocument();
    });

    it("renders view toggle by default", () => {
      render(<CalendarView />);
      expect(
        screen.getByText("calendar.viewModes.monthly"),
      ).toBeInTheDocument();
      expect(screen.getByText("calendar.viewModes.weekly")).toBeInTheDocument();
      expect(screen.getByText("calendar.viewModes.daily")).toBeInTheDocument();
    });

    it("does not render view toggle when showViewToggle is false", () => {
      render(<CalendarView showViewToggle={false} />);
      expect(
        screen.queryByText("calendar.viewModes.monthly"),
      ).not.toBeInTheDocument();
    });

    it("renders calendar title when provided", () => {
      render(<CalendarView calendarTitle="Test Calendar" />);
      expect(screen.getByText("Test Calendar")).toBeInTheDocument();
    });

    it("renders color palette info when provided", () => {
      const colorPalette = [
        { label: "Available", color: "green" },
        { label: "Busy", color: "red" },
      ];
      render(<CalendarView colorPaletteInfo={colorPalette} />);
      expect(screen.getByText("Available")).toBeInTheDocument();
      expect(screen.getByText("Busy")).toBeInTheDocument();
    });
  });

  describe("View Mode Changes", () => {
    it("changes to weekly view when weekly button is clicked", async () => {
      const user = userEvent.setup();
      const onViewModeChange = vi.fn();
      render(
        <CalendarView viewMode="monthly" onViewModeChange={onViewModeChange} />,
      );

      const weeklyButton = screen.getByText("calendar.viewModes.weekly");
      await user.click(weeklyButton);

      expect(onViewModeChange).toHaveBeenCalledWith("weekly");
    });

    it("changes to daily view when daily button is clicked", async () => {
      const user = userEvent.setup();
      const onViewModeChange = vi.fn();
      render(
        <CalendarView viewMode="monthly" onViewModeChange={onViewModeChange} />,
      );

      const dailyButton = screen.getByText("calendar.viewModes.daily");
      await user.click(dailyButton);

      expect(onViewModeChange).toHaveBeenCalledWith("daily");
    });
  });

  describe("Navigation", () => {
    it("navigates to previous month", async () => {
      const user = userEvent.setup();
      const onDateChange = vi.fn();
      render(<CalendarView viewMode="monthly" onDateChange={onDateChange} />);

      const prevButton = screen.getByRole("button", {
        name: "calendar.previousPeriod",
      });
      await user.click(prevButton);

      expect(onDateChange).toHaveBeenCalled();
    });

    it("navigates to next month", async () => {
      const user = userEvent.setup();
      const onDateChange = vi.fn();
      render(<CalendarView viewMode="monthly" onDateChange={onDateChange} />);

      const nextButton = screen.getByRole("button", {
        name: "calendar.nextPeriod",
      });
      await user.click(nextButton);

      expect(onDateChange).toHaveBeenCalled();
    });

    it("disables previous button when minDate is reached", () => {
      const minDate = new Date(2024, 0, 1);
      render(
        <CalendarView
          viewMode="monthly"
          minDate={minDate}
          selectedDate={minDate}
        />,
      );
      const prevButton = screen.getByRole("button", {
        name: "calendar.previousPeriod",
      });
      expect(prevButton).toBeDisabled();
    });

    it("disables next button when maxDate is reached", () => {
      const maxDate = new Date(2024, 0, 31);
      render(
        <CalendarView
          viewMode="monthly"
          maxDate={maxDate}
          selectedDate={maxDate}
        />,
      );
      const nextButton = screen.getByRole("button", {
        name: "calendar.nextPeriod",
      });
      expect(nextButton).toBeDisabled();
    });
  });

  describe("Events", () => {
    it("renders events in monthly view", () => {
      const eventDate = new Date(2024, 0, 15);
      render(
        <CalendarView
          viewMode="monthly"
          events={defaultEvents}
          selectedDate={eventDate}
        />,
      );
      expect(screen.getByText("Event 1")).toBeInTheDocument();
    });

    it("calls onCellClick when cell is clicked", async () => {
      const user = userEvent.setup();
      const onCellClick = vi.fn();
      render(
        <CalendarView
          viewMode="monthly"
          events={defaultEvents}
          onCellClick={onCellClick}
        />,
      );

      const cell = screen.getByText("15").closest("div");
      if (cell) {
        await user.click(cell);
        expect(onCellClick).toHaveBeenCalled();
      }
    });

    it("renders hourly events in daily view", () => {
      const hourlyEvents: HourlyEvent[] = [
        {
          hour: 10,
          customContent: <div>Hour Event</div>,
        },
      ];
      render(<CalendarView viewMode="daily" hourlyEvents={hourlyEvents} />);
      expect(screen.getByText("Hour Event")).toBeInTheDocument();
    });
  });

  describe("Detail Column", () => {
    const rows = [
      { id: "row1", label: "Row 1", count: 10 },
      { id: "row2", label: "Row 2", count: 20 },
    ];

    it("renders detail column in weekly view", () => {
      render(
        <CalendarView
          viewMode="weekly"
          showDetailColumn={true}
          rows={rows}
          detailColumnConfig={{ header: "Slots" }}
        />,
      );
      expect(screen.getByText("Slots")).toBeInTheDocument();
      expect(screen.getByText("Row 1")).toBeInTheDocument();
    });

    it("renders detail column in daily view", () => {
      render(
        <CalendarView
          viewMode="daily"
          showDetailColumn={true}
          rows={rows}
          detailColumnConfig={{ header: "Slots" }}
        />,
      );
      expect(screen.getByText("Slots")).toBeInTheDocument();
    });

    it("calls onGridCellClick when grid cell is clicked", async () => {
      const user = userEvent.setup();
      const onGridCellClick = vi.fn();
      render(
        <CalendarView
          viewMode="weekly"
          showDetailColumn={true}
          rows={rows}
          onGridCellClick={onGridCellClick}
        />,
      );

      const cells = screen.getAllByRole("generic");
      const gridCell = cells.find((cell) =>
        cell.className.includes("cursor-pointer"),
      );
      if (gridCell) {
        await user.click(gridCell);
        expect(onGridCellClick).toHaveBeenCalled();
      }
    });
  });

  describe("Date Selection", () => {
    it("highlights selected date when highlightSelectedDate is true", () => {
      const selectedDate = new Date(2024, 0, 15);
      const { container } = render(
        <CalendarView
          viewMode="monthly"
          selectedDate={selectedDate}
          highlightSelectedDate={true}
        />,
      );
      const cell = container.querySelector('[class*="ring-2"]');
      expect(cell).toBeInTheDocument();
    });

    it("highlights current date when highlightCurrentDate is true", () => {
      const { container } = render(
        <CalendarView viewMode="monthly" highlightCurrentDate={true} />,
      );
      const todayCell = container.querySelector(`[class*="text-blue-600"]`);
      expect(todayCell).toBeInTheDocument();
    });
  });

  describe("Week Start", () => {
    it("starts week on Monday by default", () => {
      const { container } = render(
        <CalendarView viewMode="monthly" weekStartsOn={1} />,
      );
      const headerRow = container.querySelector(
        ".grid.grid-cols-7.bg-mw-neutral-50",
      );
      const headers = headerRow?.querySelectorAll("div");
      expect(headers?.[0]?.textContent?.trim()).toBe(
        "calendar.dayNamesShort.1",
      );
    });

    it("starts week on Sunday when weekStartsOn is 0", () => {
      const { container } = render(
        <CalendarView viewMode="monthly" weekStartsOn={0} />,
      );
      const headerRow = container.querySelector(
        ".grid.grid-cols-7.bg-mw-neutral-50",
      );
      const headers = headerRow?.querySelectorAll("div");
      expect(headers?.[0]?.textContent?.trim()).toBe(
        "calendar.dayNamesShort.0",
      );
    });
  });

  describe("Accessibility", () => {
    it("has accessible navigation buttons", () => {
      render(<CalendarView />);
      const prevButton = screen.getByRole("button", {
        name: "calendar.previousPeriod",
      });
      const nextButton = screen.getByRole("button", {
        name: "calendar.nextPeriod",
      });
      expect(prevButton).toBeInTheDocument();
      expect(nextButton).toBeInTheDocument();
    });

    it("has accessible view toggle buttons", () => {
      render(<CalendarView />);
      const monthlyButton = screen.getByRole("button", {
        name: "calendar.viewModes.monthly",
      });
      expect(monthlyButton).toBeInTheDocument();
    });
  });

  describe("Edge Cases", () => {
    it("handles empty events array", () => {
      render(<CalendarView viewMode="monthly" events={[]} />);
      expect(screen.getByText("calendar.dayNamesShort.0")).toBeInTheDocument();
    });

    it("handles events outside current month", () => {
      const futureEvent: CalendarEvent[] = [
        {
          date: new Date(2025, 0, 1),
          customContent: <div>Future Event</div>,
        },
      ];
      render(<CalendarView viewMode="monthly" events={futureEvent} />);
      expect(screen.getByText("calendar.dayNamesShort.0")).toBeInTheDocument();
    });

    it("handles disabled dates", () => {
      const minDate = new Date(2024, 0, 15);
      const maxDate = new Date(2024, 0, 20);
      const { container } = render(
        <CalendarView viewMode="monthly" minDate={minDate} maxDate={maxDate} />,
      );
      const cells = container.querySelectorAll('[class*="cursor-not-allowed"]');
      expect(cells.length).toBeGreaterThan(0);
    });
  });
});
