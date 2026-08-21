import { render, screen } from "@testing-library/react";
import React from "react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import ProposalThemePage from "../ProposalThemePage";

vi.mock("@components/Tolgee/RouteNamespaceManager", () => ({
  useNamespace: () => ({ namespace: "proposals" }),
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => {
    const keys: Record<string, string> = {
      title: "Proposal Theme",
      description: "Configure proposal themes and templates.",
    };
    return { t: (key: string) => keys[key] ?? key };
  },
  T: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
}));

function renderProposalThemePage() {
  return render(
    <MemoryRouter>
      <ProposalThemePage />
    </MemoryRouter>,
  );
}

describe("ProposalThemePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders page with id proposal-theme-page", () => {
    renderProposalThemePage();
    expect(document.getElementById("proposal-theme-page")).toBeInTheDocument();
  });

  it("renders PageHeader with translated title and description", () => {
    renderProposalThemePage();
    expect(
      document.getElementById("page-header-proposal-theme"),
    ).toBeInTheDocument();
    expect(screen.getByText("Proposal Theme")).toBeInTheDocument();
    expect(
      screen.getByText("Configure proposal themes and templates."),
    ).toBeInTheDocument();
  });
});
