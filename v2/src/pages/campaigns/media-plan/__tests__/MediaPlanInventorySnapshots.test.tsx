import { render } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import MediaPlanInventorySnapshots from "../MediaPlanInventorySnapshots";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@utils/campaign.utils", () => ({
  formatCurrency: (amount: number, currency?: string) =>
    `${currency ?? "USD"} ${amount.toFixed(2)}`,
}));

vi.mock("@utils/dashboard.utils", () => ({
  formatCompactNumber: (n: number) => `${n}`,
}));

vi.mock("@components/common/InventoryThumbnail", () => ({
  default: ({ alt }: { alt: string }) => <img alt={alt} />,
}));

const theme = {
  id: "primary",
  name: "Test",
  colors: {
    primary: "--color-mw-primary-500",
    secondary: "--color-mw-primary-400",
    accent: "--color-mw-primary-300",
  },
};

const makeItem = (name: string, impressions: number, city: string) =>
  ({
    detail: {
      name,
      referenceId: name,
      inventoryType: "DIGITAL",
      thumbnail: "",
      mediaOwnerName: "MW Planner Internal",
    },
    location: { location: { city } },
    performance: {
      estimatedImpression: impressions,
      estimatedCost: 500,
      cpmRate: 7,
    },
  }) as never;

const selectedInventory = {
  summaryStatistics: { totalAssets: 2, totalCities: 2, totalFormatTypes: 1 },
  locations: [
    makeItem("Low Panel", 100, "Bishan"),
    makeItem("High Panel", 900, "Orchard"),
  ],
} as never;

describe("MediaPlanInventorySnapshots", () => {
  it("renders banner with theme background", () => {
    render(
      <MediaPlanInventorySnapshots
        selectedInventory={selectedInventory}
        theme={theme}
      />,
    );
    expect(
      document.getElementById("media-plan-inventory-snapshots-header"),
    ).toHaveStyle({ backgroundColor: "var(--color-mw-primary-500)" });
  });

  it("renders one card per inventory, sorted by impressions desc", () => {
    render(
      <MediaPlanInventorySnapshots
        selectedInventory={selectedInventory}
        theme={theme}
      />,
    );
    const grid = document.getElementById("media-plan-inventory-snapshots-grid");
    expect(grid?.children.length).toBe(2);
    // first card = highest impressions
    expect(grid?.firstElementChild?.textContent).toContain("High Panel");
    expect(grid?.firstElementChild?.textContent).toContain("Digital · Orchard");
  });

  it("uses performance.estimatedImpression for the card metric when present", () => {
    const inv = {
      summaryStatistics: {},
      locations: [
        {
          detail: { name: "Spot Panel", referenceId: "s", thumbnail: "" },
          location: { location: { city: "Orchard" } },
          performance: {
            estimatedImpression: 1000,
            estimatedCost: 500,
            cpmRate: 7,
          },
        },
      ],
    } as never;

    render(
      <MediaPlanInventorySnapshots selectedInventory={inv} theme={theme} />,
    );

    const grid = document.getElementById("media-plan-inventory-snapshots-grid");
    expect(grid?.textContent).toContain("1000");
  });

  it("shows CPS (spot rate) instead of CPM for a SOV/ADPLAYS goal", () => {
    const inv = {
      summaryStatistics: {},
      locations: [
        {
          detail: { name: "Spot Panel", referenceId: "s", thumbnail: "" },
          location: { location: { city: "Orchard" } },
          performance: { estimatedCost: 500, cpmRate: 7, spotRate: 0.58 },
          schedules: [{ impressions: 100 }],
        },
      ],
    } as never;
    render(
      <MediaPlanInventorySnapshots
        selectedInventory={inv}
        goalType="ADPLAYS"
        theme={theme}
      />,
    );
    const grid = document.getElementById("media-plan-inventory-snapshots-grid");
    // CPS label shown, CPM label absent; spot rate rendered.
    expect(grid?.textContent).toContain("inventory_snapshots.cps");
    expect(grid?.textContent).not.toContain("inventory_snapshots.cpm");
    expect(grid?.textContent).toContain("0.58");
  });

  it("paginates into blocks of 6 (one card grid per block)", () => {
    const many = {
      summaryStatistics: {},
      locations: Array.from({ length: 7 }, (_, i) =>
        makeItem(`Panel ${i}`, 100 - i, "City"),
      ),
    } as never;
    const { container } = render(
      <MediaPlanInventorySnapshots selectedInventory={many} theme={theme} />,
    );
    // 7 items → 2 blocks (6 + 1) → 2 Card sections
    const grids = container.querySelectorAll(".md\\:grid-cols-2");
    expect(grids.length).toBe(2);
    expect(grids[0].children.length).toBe(6);
    expect(grids[1].children.length).toBe(1);
  });

  it("shows empty state when no inventory", () => {
    render(
      <MediaPlanInventorySnapshots
        selectedInventory={{ summaryStatistics: {}, locations: [] } as never}
        theme={theme}
      />,
    );
    expect(
      document.getElementById("media-plan-inventory-snapshots-empty"),
    ).toBeInTheDocument();
    expect(
      document.getElementById("media-plan-inventory-snapshots-grid"),
    ).not.toBeInTheDocument();
  });
});
