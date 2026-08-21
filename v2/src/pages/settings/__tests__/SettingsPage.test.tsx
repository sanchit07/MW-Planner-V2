import { render, screen } from "@testing-library/react";
import React from "react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import SettingsPage from "../SettingsPage";

vi.mock("@components/Tolgee/RouteNamespaceManager", () => ({
  useNamespace: () => ({ namespace: "settings" }),
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => {
    const keys: Record<string, string> = {
      title: "Settings",
      description: "Manage your settings.",
    };
    return { t: (key: string) => keys[key] ?? key };
  },
  T: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
}));

// SettingsPage reads the profile directly via useAppSelector and calls two RTK Query slices —
// mocking the hooks directly (rather than wrapping with a real store) matches the pattern used
// elsewhere in this codebase (e.g. CampaignHistory.test.tsx) and avoids needing every other
// reducer/middleware a full Provider would require.
vi.mock("@store", () => ({
  useAppSelector: (selector: (state: unknown) => unknown) =>
    selector({ profile: { profile: undefined } }),
}));

vi.mock("@services/plannerConfiguration/plannerConfigurationSlice", () => ({
  useGetPlannerConfigurationQuery: () => ({ data: undefined, isLoading: false }),
  useUpdatePlannerConfigurationMutation: () => [vi.fn(), { isLoading: false }],
}));

vi.mock("@services/companyBranding/companyBrandingSlice", () => ({
  useGetCompanyBrandingQuery: () => ({ data: undefined, isLoading: false }),
  useUpdateCompanyBrandingMutation: () => [vi.fn(), { isLoading: false }],
}));

function renderSettingsPage() {
  return render(
    <MemoryRouter>
      <SettingsPage />
    </MemoryRouter>,
  );
}

describe("SettingsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders page with id settings-page", () => {
    renderSettingsPage();
    expect(document.getElementById("settings-page")).toBeInTheDocument();
  });

  it("renders PageHeader with translated title and description", () => {
    renderSettingsPage();
    expect(document.getElementById("page-header-settings")).toBeInTheDocument();
    expect(screen.getByText("Settings")).toBeInTheDocument();
    expect(screen.getByText("Manage your settings.")).toBeInTheDocument();
  });
});
