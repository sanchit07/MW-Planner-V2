import { render, screen } from "@testing-library/react";
import React from "react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import StatementsPage from "../StatementsPage";

vi.mock("@components/Tolgee/RouteNamespaceManager", () => ({
  useNamespace: () => ({ namespace: "statements" }),
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => {
    const keys: Record<string, string> = {
      title: "Statements",
      description: "View and manage financial statements.",
    };
    return { t: (key: string) => keys[key] ?? key };
  },
  T: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
}));

// StatementsPage's RTK Query hooks need a Redux Provider they don't otherwise get in this test —
// mocking the slice directly (as CampaignHistory.test.tsx does) avoids standing up a full store.
vi.mock("@services/statement/statementSlice", () => ({
  useListStatementsQuery: () => ({ data: undefined, isLoading: false }),
  useListStatementCandidatesQuery: () => ({ data: undefined, isLoading: false }),
  useCalculateStatementMutation: () => [vi.fn(), { data: undefined, isLoading: false }],
  useCreateStatementMutation: () => [vi.fn(), { isLoading: false }],
}));

function renderStatementsPage() {
  return render(
    <MemoryRouter>
      <StatementsPage />
    </MemoryRouter>,
  );
}

describe("StatementsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders page with id statements-page", () => {
    renderStatementsPage();
    expect(document.getElementById("statements-page")).toBeInTheDocument();
  });

  it("renders PageHeader with translated title and description", () => {
    renderStatementsPage();
    expect(
      document.getElementById("page-header-statements"),
    ).toBeInTheDocument();
    expect(screen.getByText("Statements")).toBeInTheDocument();
    expect(
      screen.getByText("View and manage financial statements."),
    ).toBeInTheDocument();
  });
});
