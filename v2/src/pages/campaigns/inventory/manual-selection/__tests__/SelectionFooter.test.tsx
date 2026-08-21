import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import SelectionFooter from "../SelectionFooter";

// This codebase's convention (see ManualSelectionPage.test.tsx,
// CostingTab.test.tsx, etc.) is to mock @tolgee/react so t() returns the raw
// key rather than rendering via a real TolgeeProvider/test instance. The
// i18n keys themselves are added in Task 6, so asserting on translated copy
// isn't possible yet — we assert on stable rendered values instead (the
// zero-padded count, compact-formatted impressions, currency code) and match
// buttons by a case-insensitive substring of their (untranslated) key, which
// still contains "save"/"cancel".
vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const totals = {
  count: 3,
  impressions: 2_300_000,
  cost: 130000,
  overBudget: false,
  overBy: 0,
};

const renderFooter = (props = {}) =>
  render(
    <SelectionFooter
      totals={totals}
      budget={200000}
      currency="MYR"
      channelRows={[]}
      isSaving={false}
      onCancel={vi.fn()}
      onSave={vi.fn()}
      onToggleChannels={vi.fn()}
      onCloseChannels={vi.fn()}
      channelsOpen={false}
      {...props}
    />,
  );

describe("SelectionFooter", () => {
  it("renders zero-padded count, compact impressions, budget of total", () => {
    renderFooter();
    expect(screen.getByText("03")).toBeInTheDocument();
    expect(screen.getByText(/2\.30\s*M/)).toBeInTheDocument();
    expect(screen.getAllByText(/MYR/)[0]).toBeInTheDocument();
  });

  it("fires onSave and onCancel", async () => {
    const onSave = vi.fn();
    const onCancel = vi.fn();
    renderFooter({ onSave, onCancel });
    await userEvent.click(screen.getByRole("button", { name: /save/i }));
    await userEvent.click(screen.getByRole("button", { name: /cancel/i }));
    expect(onSave).toHaveBeenCalled();
    expect(onCancel).toHaveBeenCalled();
  });

  it("shows the over-budget warning icon when over", () => {
    renderFooter({ totals: { ...totals, overBudget: true, overBy: 170000 } });
    expect(screen.getByTestId("budget-warning")).toBeInTheDocument();
  });

  it("does not show the over-budget warning icon when within budget", () => {
    renderFooter();
    expect(screen.queryByTestId("budget-warning")).not.toBeInTheDocument();
  });

  it("disables Save and Cancel while isSaving", () => {
    renderFooter({ isSaving: true });
    expect(screen.getByRole("button", { name: /save/i })).toBeDisabled();
    expect(screen.getByRole("button", { name: /cancel/i })).toBeDisabled();
  });

  it("fires onToggleChannels when the budget-by-channel toggle is clicked", async () => {
    const onToggleChannels = vi.fn();
    renderFooter({ onToggleChannels });
    await userEvent.click(
      screen.getByText("inventories.manual.footer.budgetByChannel"),
    );
    expect(onToggleChannels).toHaveBeenCalled();
  });
});
