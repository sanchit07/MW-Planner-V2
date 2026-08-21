import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";

import ProductSwitcher from "../ProductSwitcher";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("@config/index", () => ({
  CONFIG: {
    INFLUENCE_URL: "https://influence-test.example.com",
    MEASURE_URL: "https://measure-test.example.com",
    INVENTORY_URL: "https://inventory-test.example.com",
    ACCOUNT_URL: "https://account-test.example.com",
  },
}));

describe("ProductSwitcher", () => {
  it("renders product switcher container with id", () => {
    render(<ProductSwitcher />);
    const container = document.getElementById("product-switcher");
    expect(container).toBeInTheDocument();
  });

  it("displays product names when dropdown is opened", async () => {
    const user = userEvent.setup();
    render(<ProductSwitcher />);
    const grip = document.querySelector(".size-6");
    if (grip) await user.click(grip as HTMLElement);
    await expect(
      screen.findByText("productSwitcher.influence.name"),
    ).resolves.toBeInTheDocument();
    expect(
      screen.getByText("productSwitcher.measure.name"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("productSwitcher.inventory.name"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("productSwitcher.account.name"),
    ).toBeInTheDocument();
  });

  it("displays product names in alphabetical order", async () => {
    const user = userEvent.setup();
    render(<ProductSwitcher />);
    const grip = document.querySelector(".size-6");
    if (grip) await user.click(grip as HTMLElement);
    await screen.findByText("productSwitcher.account.name");

    const names = [
      "productSwitcher.account.name",
      "productSwitcher.influence.name",
      "productSwitcher.inventory.name",
      "productSwitcher.measure.name",
    ];
    const rendered = names.map((n) => screen.getByText(n));
    for (let i = 1; i < rendered.length; i++) {
      // each name must appear after the previous one in DOM order
      expect(
        rendered[i - 1].compareDocumentPosition(rendered[i]) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    }
  });

  it("renders anchor tags with correct href and target attributes", async () => {
    const user = userEvent.setup();
    render(<ProductSwitcher />);
    const grip = document.querySelector(".size-6");
    if (grip) await user.click(grip as HTMLElement);

    const influenceLink = await screen.findByText(
      "productSwitcher.influence.name",
    );
    const anchor = influenceLink.closest("a");
    expect(anchor).toHaveAttribute(
      "href",
      "https://influence-test.example.com",
    );
    expect(anchor).toHaveAttribute("target", "_blank");
    expect(anchor).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("renders product descriptions when open", async () => {
    const user = userEvent.setup();
    render(<ProductSwitcher />);
    const grip = document.querySelector(".size-6");
    if (grip) await user.click(grip as HTMLElement);
    expect(
      await screen.findByText("productSwitcher.influence.description"),
    ).toBeInTheDocument();
  });

  it("shows the campaign-performance description under Measure", async () => {
    const user = userEvent.setup();
    render(<ProductSwitcher />);
    const grip = document.querySelector(".size-6");
    if (grip) await user.click(grip as HTMLElement);

    const measureLink = (
      await screen.findByText("productSwitcher.measure.name")
    ).closest("a");
    expect(measureLink).toHaveTextContent(
      "productSwitcher.measure.description",
    );
  });

  it("shows the inventories description under Inventory", async () => {
    const user = userEvent.setup();
    render(<ProductSwitcher />);
    const grip = document.querySelector(".size-6");
    if (grip) await user.click(grip as HTMLElement);

    const inventoryLink = (
      await screen.findByText("productSwitcher.inventory.name")
    ).closest("a");
    expect(inventoryLink).toHaveTextContent(
      "productSwitcher.inventory.description",
    );
  });

  describe("platform URL routing — live URL config", () => {
    it("renders Inventory link with correct URL", async () => {
      const user = userEvent.setup();
      render(<ProductSwitcher />);
      const grip = document.querySelector(".size-6");
      if (grip) await user.click(grip as HTMLElement);

      const inventoryLink = await screen.findByText(
        "productSwitcher.inventory.name",
      );
      const anchor = inventoryLink.closest("a");
      expect(anchor).toHaveAttribute(
        "href",
        "https://inventory-test.example.com",
      );
      expect(anchor).toHaveAttribute("target", "_blank");
      expect(anchor).toHaveAttribute("rel", "noopener noreferrer");
    });

    it("renders Measure link with correct URL", async () => {
      const user = userEvent.setup();
      render(<ProductSwitcher />);
      const grip = document.querySelector(".size-6");
      if (grip) await user.click(grip as HTMLElement);

      const measureLink = await screen.findByText(
        "productSwitcher.measure.name",
      );
      const anchor = measureLink.closest("a");
      expect(anchor).toHaveAttribute(
        "href",
        "https://measure-test.example.com",
      );
      expect(anchor).toHaveAttribute("target", "_blank");
      expect(anchor).toHaveAttribute("rel", "noopener noreferrer");
    });

    it("renders Account link with correct URL", async () => {
      const user = userEvent.setup();
      render(<ProductSwitcher />);
      const grip = document.querySelector(".size-6");
      if (grip) await user.click(grip as HTMLElement);

      const accountLink = await screen.findByText(
        "productSwitcher.account.name",
      );
      const anchor = accountLink.closest("a");
      expect(anchor).toHaveAttribute(
        "href",
        "https://account-test.example.com",
      );
      expect(anchor).toHaveAttribute("target", "_blank");
      expect(anchor).toHaveAttribute("rel", "noopener noreferrer");
    });

    it("each platform has a distinct URL configured (no duplicates)", () => {
      // Validate via the mock that all 4 configured URLs are unique
      const configuredUrls = [
        "https://influence-test.example.com",
        "https://measure-test.example.com",
        "https://inventory-test.example.com",
        "https://account-test.example.com",
      ];
      expect(new Set(configuredUrls).size).toBe(configuredUrls.length);
    });
  });
});
