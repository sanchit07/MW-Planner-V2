import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import {
  PriceManagementToolbar,
  PriceManagementViewType,
} from "../PriceManagementToolbar";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const defaultProps = {
  searchValue: "",
  onSearchChange: vi.fn(),
  onSearchSubmit: vi.fn(),
  viewType: "grid" as PriceManagementViewType,
  onViewChange: vi.fn(),
  activeFilterCount: 0,
  onOpenFilters: vi.fn(),
  onOpenColumns: vi.fn(),
  onOpenSummary: vi.fn(),
  onOpenHistory: vi.fn(),
  disabled: false,
};

const renderToolbar = (props: Partial<typeof defaultProps> = {}) =>
  render(<PriceManagementToolbar {...defaultProps} {...props} />);

describe("PriceManagementToolbar", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders search, filters, view toggle, columns and summary controls", () => {
    renderToolbar();

    expect(document.getElementById("campaigns-search-input")).toBeVisible();
    expect(document.getElementById("campaigns-filter-btn")).toBeVisible();
    expect(document.getElementById("campaigns-view-list-btn")).toBeVisible();
    expect(document.getElementById("campaigns-view-map-btn")).toBeVisible();
    expect(
      document.getElementById("campaigns-view-calendar-btn"),
    ).toBeVisible();
    expect(document.getElementById("campaigns-columns-btn")).toBeVisible();
    expect(document.getElementById("campaigns-summary-btn")).toBeVisible();
  });

  it("calls onSearchChange as the user types", async () => {
    const user = userEvent.setup();
    const onSearchChange = vi.fn();
    renderToolbar({ onSearchChange });

    await user.type(screen.getByRole("textbox"), "a");

    expect(onSearchChange).toHaveBeenCalledWith("a");
  });

  it("calls onSearchSubmit only when Enter is pressed", async () => {
    const user = userEvent.setup();
    const onSearchSubmit = vi.fn();
    renderToolbar({ onSearchSubmit });

    await user.type(screen.getByRole("textbox"), "abc");
    expect(onSearchSubmit).not.toHaveBeenCalled();

    await user.keyboard("{Enter}");
    expect(onSearchSubmit).toHaveBeenCalledTimes(1);
  });

  it("calls the drawer openers for filters, columns and summary", async () => {
    const user = userEvent.setup();
    const onOpenFilters = vi.fn();
    const onOpenColumns = vi.fn();
    const onOpenSummary = vi.fn();
    renderToolbar({ onOpenFilters, onOpenColumns, onOpenSummary });

    await user.click(document.getElementById("campaigns-filter-btn")!);
    await user.click(document.getElementById("campaigns-columns-btn")!);
    await user.click(document.getElementById("campaigns-summary-btn")!);

    expect(onOpenFilters).toHaveBeenCalledTimes(1);
    expect(onOpenColumns).toHaveBeenCalledTimes(1);
    expect(onOpenSummary).toHaveBeenCalledTimes(1);
  });

  it("renders the price history button next to the view toggle", () => {
    renderToolbar();

    expect(screen.getByTestId("button-price-history")).toBeVisible();
  });

  it("opens the price history drawer when its button is clicked", async () => {
    const user = userEvent.setup();
    const onOpenHistory = vi.fn();
    renderToolbar({ onOpenHistory });

    await user.click(screen.getByTestId("button-price-history"));

    expect(onOpenHistory).toHaveBeenCalledTimes(1);
  });

  it("switches views through onViewChange", async () => {
    const user = userEvent.setup();
    const onViewChange = vi.fn();
    renderToolbar({ onViewChange });

    await user.click(document.getElementById("campaigns-view-map-btn")!);
    expect(onViewChange).toHaveBeenCalledWith("mapView");

    await user.click(document.getElementById("campaigns-view-calendar-btn")!);
    expect(onViewChange).toHaveBeenCalledWith("calender");
  });

  it("highlights the active view button", () => {
    renderToolbar({ viewType: "mapView" });

    expect(document.getElementById("campaigns-view-map-btn")).toHaveClass(
      "bg-mw-neutral-200",
    );
    expect(document.getElementById("campaigns-view-list-btn")).not.toHaveClass(
      "bg-mw-neutral-200",
    );
  });

  it("shows the active filter count badge only when filters are applied", () => {
    const { unmount } = renderToolbar({ activeFilterCount: 0 });
    expect(document.getElementById("campaigns-filter-badge")).toBeNull();
    unmount();

    renderToolbar({ activeFilterCount: 3 });
    expect(document.getElementById("campaigns-filter-badge")).toHaveTextContent(
      "3",
    );
  });

  it("enables search, filters and view toggle by default", () => {
    renderToolbar();

    expect(screen.getByRole("textbox")).toBeEnabled();
    expect(document.getElementById("campaigns-filter-btn")).toBeEnabled();
    expect(document.getElementById("campaigns-view-map-btn")).toBeEnabled();
  });

  it("disables search, filters and view toggle while a fetch is in flight", () => {
    renderToolbar({ disabled: true });

    expect(screen.getByRole("textbox")).toBeDisabled();
    expect(document.getElementById("campaigns-filter-btn")).toBeDisabled();
    expect(document.getElementById("campaigns-view-list-btn")).toBeDisabled();
    expect(document.getElementById("campaigns-view-map-btn")).toBeDisabled();
    expect(
      document.getElementById("campaigns-view-calendar-btn"),
    ).toBeDisabled();
  });

  it("keeps columns and summary enabled while a fetch is in flight", () => {
    renderToolbar({ disabled: true });

    // These only open a drawer - they don't trigger another table fetch,
    // so there's no overlapping-request risk to guard against.
    expect(document.getElementById("campaigns-columns-btn")).toBeEnabled();
    expect(document.getElementById("campaigns-summary-btn")).toBeEnabled();
  });
});
