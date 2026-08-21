import { render, screen } from "@testing-library/react";
import React from "react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import InventoriesPage from "../InventoriesPage";

vi.mock("@components/Tolgee/RouteNamespaceManager", () => ({
  useNamespace: () => ({ namespace: "inventories" }),
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => {
    const keys: Record<string, string> = {
      title: "Inventories",
      description: "Manage your inventories.",
    };
    return { t: (key: string) => keys[key] ?? key };
  },
  T: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
}));

function renderInventoriesPage() {
  return render(
    <MemoryRouter>
      <InventoriesPage />
    </MemoryRouter>,
  );
}

describe("InventoriesPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders page with id inventories-page", () => {
    renderInventoriesPage();
    expect(document.getElementById("inventories-page")).toBeInTheDocument();
  });

  it("renders PageHeader with translated title and description", () => {
    renderInventoriesPage();
    expect(
      document.getElementById("page-header-inventories"),
    ).toBeInTheDocument();
    expect(screen.getByText("Inventories")).toBeInTheDocument();
    expect(screen.getByText("Manage your inventories.")).toBeInTheDocument();
  });
});
