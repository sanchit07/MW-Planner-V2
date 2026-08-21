import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { InlinePriceEditProvider } from "../InlinePriceEditContext";
import { InlineProposedPriceCell } from "../InlineProposedPriceCell";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const renderTwoCells = () =>
  render(
    <InlinePriceEditProvider>
      <InlineProposedPriceCell
        value={2200}
        currency="USD"
        rowKey="inv-1"
        isInventoryRow
        originalPrice={2200}
        onSave={vi.fn()}
      />
      <InlineProposedPriceCell
        value={1100}
        currency="USD"
        rowKey="inv-2"
        isInventoryRow
        originalPrice={1100}
        onSave={vi.fn()}
      />
    </InlinePriceEditProvider>,
  );

const priceButton = (text: string | RegExp) =>
  screen.getByRole("button", { name: text });

describe("single-cell inline price editing", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("opens only one editor at a time", async () => {
    const user = userEvent.setup();
    renderTwoCells();

    await user.click(priceButton(/2,200/));
    expect(screen.getAllByRole("textbox")).toHaveLength(1);

    await user.click(priceButton(/1,100/));
    expect(screen.getAllByRole("textbox")).toHaveLength(1);
    // The first cell is back to its read-only display
    expect(priceButton(/2,200/)).toBeInTheDocument();
  });

  it("discards the unsaved draft of the cell that gets closed", async () => {
    const user = userEvent.setup();
    renderTwoCells();

    await user.click(priceButton(/2,200/));
    await user.clear(screen.getByRole("textbox"));
    await user.type(screen.getByRole("textbox"), "9999");

    // Switch to the other cell, then come back
    await user.click(priceButton(/1,100/));
    await user.click(priceButton(/2,200/));

    // Seeded from the current value, not the abandoned 9999
    expect(screen.getByRole("textbox")).toHaveValue("2,200");
  });

  it("falls back to local state when no provider is present", async () => {
    const user = userEvent.setup();
    render(
      <InlineProposedPriceCell
        value={2200}
        currency="USD"
        rowKey="inv-1"
        isInventoryRow
        originalPrice={2200}
        onSave={vi.fn()}
      />,
    );

    await user.click(priceButton(/2,200/));

    expect(screen.getByRole("textbox")).toBeInTheDocument();
  });
});
