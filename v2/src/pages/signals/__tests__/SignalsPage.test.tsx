import { render, screen } from "@testing-library/react";
import React from "react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import SignalsPage from "../SignalsPage";

vi.mock("@components/Tolgee/RouteNamespaceManager", () => ({
  useNamespace: () => ({ namespace: "signals" }),
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => {
    const keys: Record<string, string> = {
      title: "Signals",
      description: "Manage your signals.",
    };
    return { t: (key: string) => keys[key] ?? key };
  },
  T: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
}));

function renderSignalsPage() {
  return render(
    <MemoryRouter>
      <SignalsPage />
    </MemoryRouter>,
  );
}

describe("SignalsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders page with id signals-page", () => {
    renderSignalsPage();
    expect(document.getElementById("signals-page")).toBeInTheDocument();
  });

  it("renders PageHeader with translated title and description", () => {
    renderSignalsPage();
    expect(document.getElementById("page-header-signals")).toBeInTheDocument();
    expect(screen.getByText("Signals")).toBeInTheDocument();
    expect(screen.getByText("Manage your signals.")).toBeInTheDocument();
  });
});
