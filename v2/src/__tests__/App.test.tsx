import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";

import { App } from "../App";
import { loadGoogleMapsScript } from "../utils/loadGoogleMapsScript";

vi.mock("@components/Tolgee/Tolgee", () => ({
  default: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="tolgee-provider">{children}</div>
  ),
}));

vi.mock("../routes/AppRoutes", () => ({
  default: () => <div data-testid="app-routes">AppRoutes</div>,
}));

vi.mock("@services/intercom", () => ({
  IntercomProvider: ({ children }: { children: React.ReactNode }) => (
    <>{children}</>
  ),
}));

vi.mock("../utils/loadGoogleMapsScript", () => ({
  loadGoogleMapsScript: vi.fn(),
}));

vi.mock("../config", () => ({
  CONFIG: { GM_KEY: "mock-gm-key" },
}));

describe("App", () => {
  beforeEach(() => {
    vi.mocked(loadGoogleMapsScript).mockClear();
  });

  it("renders Provider, TolgeeContextProvider and AppRoutes", () => {
    render(<App />);

    expect(screen.getByTestId("tolgee-provider")).toBeInTheDocument();
    expect(screen.getByTestId("app-routes")).toBeInTheDocument();
    expect(screen.getByText("AppRoutes")).toBeInTheDocument();
  });

  it("calls loadGoogleMapsScript on mount with CONFIG.GM_KEY", () => {
    render(<App />);

    expect(loadGoogleMapsScript).toHaveBeenCalledTimes(1);
    expect(loadGoogleMapsScript).toHaveBeenCalledWith("mock-gm-key");
  });

  it("renders AppRoutes inside TolgeeContextProvider", () => {
    render(<App />);

    const tolgee = screen.getByTestId("tolgee-provider");
    const routes = screen.getByTestId("app-routes");
    expect(tolgee).toContainElement(routes);
  });
});
