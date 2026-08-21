import { render, screen } from "@testing-library/react";
import React from "react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import CreativesPage from "../CreativesPage";

vi.mock("@components/Tolgee/RouteNamespaceManager", () => ({
  useNamespace: () => ({ namespace: "creatives" }),
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => {
    const keys: Record<string, string> = {
      title: "Creatives",
      description: "Manage your creative assets.",
    };
    return { t: (key: string) => keys[key] ?? key };
  },
  T: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
}));

// CreativesPage's RTK Query hooks need a Redux Provider they don't otherwise get in this test —
// mocking the slice directly (as CampaignHistory.test.tsx does) avoids standing up a full store.
vi.mock("@services/creative/creativeSlice", () => ({
  useListCreativesQuery: () => ({ data: undefined, isLoading: false }),
  useUploadCreativeMutation: () => [vi.fn(), { isLoading: false }],
  useDeactivateCreativeMutation: () => [vi.fn(), { isLoading: false }],
  useBindCreativeMutation: () => [vi.fn(), { isLoading: false }],
  useUpdateCreativeTier1StatusMutation: () => [vi.fn(), { isLoading: false }],
  useListAssignmentsForCampaignQuery: () => ({
    data: undefined,
    isLoading: false,
    refetch: vi.fn(),
  }),
}));

function renderCreativesPage() {
  return render(
    <MemoryRouter>
      <CreativesPage />
    </MemoryRouter>,
  );
}

describe("CreativesPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders page with id creatives-page", () => {
    renderCreativesPage();
    expect(document.getElementById("creatives-page")).toBeInTheDocument();
  });

  it("renders PageHeader with translated title and description", () => {
    renderCreativesPage();
    expect(
      document.getElementById("page-header-creatives"),
    ).toBeInTheDocument();
    expect(screen.getByText("Creatives")).toBeInTheDocument();
    expect(
      screen.getByText("Manage your creative assets."),
    ).toBeInTheDocument();
  });
});
