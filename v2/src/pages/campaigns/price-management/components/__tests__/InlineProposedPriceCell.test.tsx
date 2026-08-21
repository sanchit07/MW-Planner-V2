import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import {
  InlineProposedPriceCell,
  InlineProposedPriceCellProps,
} from "../InlineProposedPriceCell";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const priceButton = () =>
  screen.getByRole("button", { name: /2,200|--|760|850/ });
const saveButton = () => screen.getByRole("button", { name: "buttons.save" });
const cancelButton = () =>
  screen.getByRole("button", { name: "buttons.cancel" });

const onSave = vi.fn();

const defaultProps: InlineProposedPriceCellProps = {
  value: 2200,
  currency: "USD",
  rowKey: "inv-1",
  isInventoryRow: true,
  // High enough that the staging tests below (up to 3000) never trip the
  // "cannot exceed original price" cap - that behavior has its own tests.
  originalPrice: 10000,
  onSave,
};

const renderCell = (props: Partial<InlineProposedPriceCellProps> = {}) =>
  render(<InlineProposedPriceCell {...defaultProps} {...props} />);

describe("InlineProposedPriceCell", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows the formatted price for inventory rows", () => {
    renderCell();

    expect(priceButton()).toHaveTextContent("2,200");
  });

  it("shows no source line for schedule rows", () => {
    renderCell({ isInventoryRow: false });

    expect(
      screen.queryByText("common.internal_system"),
    ).not.toBeInTheDocument();
  });

  it("renders a placeholder when there is no price", () => {
    renderCell({ value: 0 });

    expect(priceButton()).toHaveTextContent("--");
  });

  it("shows no unsaved indicator when the value is not a draft", () => {
    renderCell({ isDraft: false });

    expect(
      priceButton().querySelector(".bg-mw-primary-500"),
    ).not.toBeInTheDocument();
  });

  it("shows an unsaved indicator when the value is a draft", () => {
    renderCell({ isDraft: true });

    expect(
      priceButton().querySelector(".bg-mw-primary-500"),
    ).toBeInTheDocument();
  });

  it("switches to an input when the price is clicked", async () => {
    const user = userEvent.setup();
    renderCell();

    await user.click(priceButton());

    expect(screen.getByRole("textbox")).toHaveValue("2,200");
  });

  it("seeds the input at display precision, not the raw float", async () => {
    const user = userEvent.setup();
    renderCell({ value: 5296.349999999999 });

    await user.click(screen.getByRole("button", { name: /5,296\.35/ }));

    expect(screen.getByRole("textbox")).toHaveValue("5,296.35");
  });

  it("treats an untouched float price as unchanged", async () => {
    const user = userEvent.setup();
    renderCell({ value: 5296.349999999999 });

    await user.click(screen.getByRole("button", { name: /5,296\.35/ }));
    await user.type(screen.getByRole("textbox"), "{Enter}");

    expect(onSave).not.toHaveBeenCalled();
  });

  it("stages the new price on Enter without calling any API", async () => {
    const user = userEvent.setup();
    renderCell();

    await user.click(priceButton());
    await user.clear(screen.getByRole("textbox"));
    await user.type(screen.getByRole("textbox"), "2500{Enter}");

    expect(onSave).toHaveBeenCalledWith(2500);
    // Back to the read-only display, no async save state to wait on
    expect(priceButton()).toBeInTheDocument();
  });

  it("stages the new price when the save button is clicked", async () => {
    const user = userEvent.setup();
    renderCell();

    await user.click(priceButton());
    await user.clear(screen.getByRole("textbox"));
    await user.type(screen.getByRole("textbox"), "3000");
    expect(screen.getByRole("textbox")).toHaveValue("3,000");
    await user.click(saveButton());

    expect(onSave).toHaveBeenCalledWith(3000);
  });

  it("does not stage anything when blurring the input", async () => {
    const user = userEvent.setup();
    renderCell();

    await user.click(priceButton());
    await user.clear(screen.getByRole("textbox"));
    await user.type(screen.getByRole("textbox"), "3000");
    await user.tab();

    expect(onSave).not.toHaveBeenCalled();
    expect(screen.getByRole("textbox")).toBeInTheDocument();
  });

  it("discards the edit when the cancel button is clicked", async () => {
    const user = userEvent.setup();
    renderCell();

    await user.click(priceButton());
    await user.clear(screen.getByRole("textbox"));
    await user.type(screen.getByRole("textbox"), "9999");
    await user.click(cancelButton());

    expect(onSave).not.toHaveBeenCalled();
    expect(priceButton()).toHaveTextContent("2,200");
  });

  it("cancels on Escape without staging anything", async () => {
    const user = userEvent.setup();
    renderCell();

    await user.click(priceButton());
    await user.clear(screen.getByRole("textbox"));
    await user.type(screen.getByRole("textbox"), "9999{Escape}");

    expect(onSave).not.toHaveBeenCalled();
    expect(priceButton()).toHaveTextContent("2,200");
  });

  it("does not stage anything when the price is unchanged", async () => {
    const user = userEvent.setup();
    renderCell();

    await user.click(priceButton());
    await user.type(screen.getByRole("textbox"), "{Enter}");

    expect(onSave).not.toHaveBeenCalled();
    expect(priceButton()).toBeInTheDocument();
  });

  it("strips characters that are not digits or a decimal point", async () => {
    const user = userEvent.setup();
    renderCell();

    await user.click(priceButton());
    await user.clear(screen.getByRole("textbox"));
    await user.type(screen.getByRole("textbox"), "-5");

    expect(screen.getByRole("textbox")).toHaveValue("5");
  });

  it("rejects more than two decimal places", async () => {
    const user = userEvent.setup();
    renderCell();

    await user.click(priceButton());
    await user.clear(screen.getByRole("textbox"));
    await user.type(screen.getByRole("textbox"), "12.345");

    expect(screen.getByRole("textbox")).toHaveValue("12.34");
  });

  it("reports an error when the price is cleared", async () => {
    const user = userEvent.setup();
    renderCell();

    await user.click(priceButton());
    await user.clear(screen.getByRole("textbox"));
    await user.type(screen.getByRole("textbox"), "{Enter}");

    expect(onSave).not.toHaveBeenCalled();
    expect(
      screen.getByText("drawers.add_proposal_price.error_required"),
    ).toBeInTheDocument();
  });

  it("rejects a price above the original price", async () => {
    const user = userEvent.setup();
    renderCell({ value: 2200, originalPrice: 2200 });

    await user.click(priceButton());
    await user.clear(screen.getByRole("textbox"));
    await user.type(screen.getByRole("textbox"), "2500{Enter}");

    expect(onSave).not.toHaveBeenCalled();
    expect(
      screen.getByText("drawers.add_proposal_price.error_max_price"),
    ).toBeInTheDocument();
  });

  it("allows a price equal to the original price", async () => {
    const user = userEvent.setup();
    renderCell({ value: 2000, originalPrice: 2200 });

    await user.click(screen.getByRole("button", { name: /2,000/ }));
    await user.clear(screen.getByRole("textbox"));
    await user.type(screen.getByRole("textbox"), "2200{Enter}");

    expect(onSave).toHaveBeenCalledWith(2200);
  });
});
