import { MediaChannel } from "@constants/inventory.constants";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import BudgetByChannelPopup from "../BudgetByChannelPopup";
import type { ChannelRow } from "../selection-stats.utils";

// This codebase's convention (see SelectionFooter.test.tsx, Task 4) is to
// mock @tolgee/react so t() returns the raw key rather than rendering via a
// real TolgeeProvider/test instance. The i18n keys are added in Task 6, so we
// assert on stable rendered values (numbers, currency, inventory counts) and
// on the channel label keys as rendered, not on translated copy.
vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const rows: ChannelRow[] = [
  {
    channel: MediaChannel.DIGITAL_OOH,
    key: "digital",
    planned: 50000,
    selected: 28300,
    difference: -21700,
    inventories: 0,
  },
  {
    channel: MediaChannel.CLASSIC_OOH,
    key: "classic",
    planned: 50000,
    selected: 23350,
    difference: -26650,
    inventories: 8,
  },
];

const renderPopup = (props = {}) =>
  render(
    <BudgetByChannelPopup
      isOpen
      onClose={vi.fn()}
      rows={rows}
      currency="MYR"
      {...props}
    />,
  );

describe("BudgetByChannelPopup", () => {
  it("renders nothing when closed", () => {
    const { container } = render(
      <BudgetByChannelPopup
        isOpen={false}
        onClose={vi.fn()}
        rows={rows}
        currency="MYR"
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("renders a row per channel with its translated label key", () => {
    renderPopup();
    expect(screen.getByTestId("channel-row-digital")).toBeInTheDocument();
    expect(screen.getByTestId("channel-row-classic")).toBeInTheDocument();
    expect(
      screen.getByText("optimization.budgetAllocation.digital"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("optimization.budgetAllocation.classic"),
    ).toBeInTheDocument();
  });

  it("shows the inventory count per channel", () => {
    renderPopup();
    expect(screen.getByTestId("channel-inventories-classic")).toHaveTextContent(
      "8",
    );
    expect(screen.getByTestId("channel-inventories-digital")).toHaveTextContent(
      "0",
    );
  });

  it("shows planned and selected currency amounts per row", () => {
    renderPopup();
    const digitalRow = screen.getByTestId("channel-row-digital");
    expect(digitalRow).toHaveTextContent("MYR 50,000");
    expect(digitalRow).toHaveTextContent("MYR 28,300");
  });

  it("renders a Total row that sums planned, selected and difference", () => {
    renderPopup();
    const totalRow = screen.getByTestId("channel-row-total");
    // Total planned = 100,000; total selected = 51,650; total diff = -48,350
    expect(totalRow).toHaveTextContent("MYR 100,000");
    expect(totalRow).toHaveTextContent("MYR 51,650");
    expect(totalRow).toHaveTextContent("MYR -48,350");
  });

  it("renders negative differences without a plus sign and no over-budget note", () => {
    renderPopup();
    const digitalRow = screen.getByTestId("channel-row-digital");
    expect(digitalRow).not.toHaveTextContent("+ MYR");
    expect(screen.queryByTestId("over-budget-note")).not.toBeInTheDocument();
  });

  it("renders a plus-prefixed difference and an over-budget note when a channel is over plan", () => {
    renderPopup({
      rows: [{ ...rows[0], selected: 60000, difference: 10000 }, rows[1]],
    });
    const digitalRow = screen.getByTestId("channel-row-digital");
    expect(digitalRow).toHaveTextContent("+ MYR 10,000");
    expect(screen.getByTestId("over-budget-note")).toBeInTheDocument();
  });
});
