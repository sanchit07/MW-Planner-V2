import { configureStore } from "@reduxjs/toolkit";
import sidebarSlice from "@services/sidebar-toggle/sidebar-toggle.slice";
import { render, screen } from "@testing-library/react";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { Layout } from "../Layout";

// Mock child components
vi.mock("../Header", () => ({
  Header: () => <div data-testid="header">Header</div>,
}));

vi.mock("../Footer", () => ({
  Footer: () => <div data-testid="footer">Footer</div>,
}));

vi.mock("../Sidebar", () => ({
  Sidebar: () => <div data-testid="sidebar">Sidebar</div>,
}));

vi.mock("../InactivityTimer", () => ({
  InactivityTimer: () => (
    <div data-testid="inactivity-timer">InactivityTimer</div>
  ),
}));

const createMockStore = (isCollapsed: boolean) => {
  return configureStore({
    reducer: {
      sidebar: sidebarSlice,
    },
    preloadedState: {
      sidebar: {
        isSidebarCollapsed: isCollapsed,
      },
    },
  });
};

const TestWrapper = ({
  children,
  isCollapsed = false,
  initialEntries = ["/"],
}: {
  children: React.ReactNode;
  isCollapsed?: boolean;
  initialEntries?: string[];
}) => {
  const store = createMockStore(isCollapsed);
  return (
    <Provider store={store}>
      <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
    </Provider>
  );
};

describe("Layout", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders Header and Sidebar for any route", () => {
    render(
      <TestWrapper initialEntries={["/login"]}>
        <Layout />
      </TestWrapper>,
    );

    expect(screen.getByTestId("header")).toBeInTheDocument();
    const sidebars = screen.getAllByTestId("sidebar");
    expect(sidebars.length).toBeGreaterThan(0);
  });

  it("renders Footer when pathname is /login", () => {
    render(
      <TestWrapper initialEntries={["/login"]}>
        <Layout />
      </TestWrapper>,
    );

    expect(screen.getByTestId("footer")).toBeInTheDocument();
  });

  it("should render full layout for non-auth pages", () => {
    render(
      <TestWrapper initialEntries={["/dashboard"]}>
        <Layout />
      </TestWrapper>,
    );

    const header = screen.getByTestId("header");
    const sidebars = screen.getAllByTestId("sidebar");
    // There might be multiple sidebars (mobile overlay + desktop)
    expect(sidebars.length).toBeGreaterThan(0);
    expect(header).toBeInTheDocument();
  });

  it("should render footer for non-campaign create/edit pages", () => {
    render(
      <TestWrapper initialEntries={["/dashboard"]}>
        <Layout />
      </TestWrapper>,
    );

    const footer = screen.getByTestId("footer");
    expect(footer).toBeInTheDocument();
  });

  it("should not render footer for campaign create page", () => {
    render(
      <TestWrapper initialEntries={["/campaigns/create"]}>
        <Layout />
      </TestWrapper>,
    );

    const footer = screen.queryByTestId("footer");
    expect(footer).not.toBeInTheDocument();
  });

  it("should not render footer for campaign edit page", () => {
    render(
      <TestWrapper initialEntries={["/campaigns/edit/123"]}>
        <Layout />
      </TestWrapper>,
    );

    const footer = screen.queryByTestId("footer");
    expect(footer).not.toBeInTheDocument();
  });

  it("shows mobile overlay when sidebar is not collapsed", () => {
    const { container } = render(
      <TestWrapper initialEntries={["/dashboard"]} isCollapsed={false}>
        <Layout />
      </TestWrapper>,
    );

    const overlay = container.querySelector('[class*="md:hidden"]');
    expect(overlay).toBeInTheDocument();
    expect(overlay).toHaveClass("block");
  });

  it("hides mobile overlay when sidebar is collapsed", () => {
    const { container } = render(
      <TestWrapper initialEntries={["/dashboard"]} isCollapsed={true}>
        <Layout />
      </TestWrapper>,
    );

    const overlay = container.querySelector('[class*="md:hidden"]');
    expect(overlay).toBeInTheDocument();
    expect(overlay).toHaveClass("hidden");
  });

  it("should have correct layout structure", () => {
    render(
      <TestWrapper initialEntries={["/dashboard"]}>
        <Layout />
      </TestWrapper>,
    );

    const layoutWrapper = document.querySelector(".layout-content-wrapper");
    expect(layoutWrapper).toBeInTheDocument();
  });

  it("should render main content area", () => {
    render(
      <TestWrapper initialEntries={["/dashboard"]}>
        <Layout />
      </TestWrapper>,
    );

    const main = document.querySelector("main");
    expect(main).toBeInTheDocument();
    expect(main).toHaveClass("flex-1", "flex", "flex-col");
  });

  it("should handle different routes correctly", () => {
    render(
      <TestWrapper initialEntries={["/campaigns"]}>
        <Layout />
      </TestWrapper>,
    );

    const header = screen.getByTestId("header");
    // Layout renders sidebar multiple times (mobile overlay + desktop)
    const sidebars = screen.getAllByTestId("sidebar");
    const footer = screen.getByTestId("footer");

    expect(header).toBeInTheDocument();
    expect(sidebars.length).toBeGreaterThan(0);
    expect(footer).toBeInTheDocument();
  });
});
