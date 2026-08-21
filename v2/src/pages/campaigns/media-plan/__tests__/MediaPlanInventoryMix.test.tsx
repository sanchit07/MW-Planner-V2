import { render } from "@testing-library/react";
import { CampaignForecastData } from "src/types/inventory.types";
import { describe, it, expect, vi } from "vitest";

import MediaPlanInventoryMix from "../MediaPlanInventoryMix";

const CHANNEL_LABELS: Record<string, string> = {
  "media_plan.inventory_mix.channel_digital_ooh": "Digital",
  "media_plan.inventory_mix.channel_classic_ooh": "Classic",
};
vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => CHANNEL_LABELS[key] ?? key }),
}));

vi.mock("@utils/campaign.utils", () => ({
  formatCurrency: (amount: number, currency?: string) =>
    `${currency ?? "USD"} ${amount.toFixed(2)}`,
}));

vi.mock("@utils/dashboard.utils", () => ({
  formatCompactNumber: (n: number) => `${n}`,
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

const channels = [
  {
    name: "Digital",
    totalInventories: 1,
    impressions: 3600000,
    totalAmount: 25000,
    avgCpm: 7.01,
    totalAmountInPercentage: 100,
    frequency: 1,
    reach: 0,
  },
];

describe("MediaPlanInventoryMix", () => {
  it("renders banner with theme background", () => {
    render(<MediaPlanInventoryMix costSplitData={channels} theme={theme} />);
    expect(
      document.getElementById("media-plan-inventory-mix-card"),
    ).toBeInTheDocument();
    expect(
      document.getElementById("media-plan-inventory-mix-header"),
    ).toHaveStyle({ backgroundColor: "var(--color-mw-primary-500)" });
  });

  it("renders a row per channel plus a total row", () => {
    render(<MediaPlanInventoryMix costSplitData={channels} />);
    const table = document.getElementById("media-plan-inventory-mix-table");
    // 1 header row + 1 channel row + 1 total row
    expect(table?.querySelectorAll("tr").length).toBe(3);
    expect(
      document.getElementById("media-plan-inventory-mix-total-row"),
    ).toBeInTheDocument();
  });

  it("renders the three summary boxes and the lead-channel note", () => {
    render(<MediaPlanInventoryMix costSplitData={channels} />);
    const summary = document.getElementById("media-plan-inventory-mix-summary");
    expect(summary?.children.length).toBe(3);
    expect(
      document.getElementById("media-plan-inventory-mix-note"),
    ).toBeInTheDocument();
  });

  it("shows empty state when no channel data", () => {
    render(<MediaPlanInventoryMix costSplitData={[]} />);
    expect(
      document.getElementById("media-plan-inventory-mix-empty"),
    ).toBeInTheDocument();
    expect(
      document.getElementById("media-plan-inventory-mix-table"),
    ).not.toBeInTheDocument();
  });

  it("uses the performance metrics value for the CPM/CPS column", () => {
    render(
      <MediaPlanInventoryMix
        costSplitData={channels}
        performanceMetrics={{ avgCpm: 42.5 } as CampaignForecastData}
      />,
    );
    expect(document.body.textContent).toContain("USD 42.50");
    expect(document.body.textContent).not.toContain("USD 7.01");
  });

  it("shows a dash for classic channel CPM/CPS values", () => {
    render(
      <MediaPlanInventoryMix
        costSplitData={[{ ...channels[0], name: "Classic" }]}
        performanceMetrics={{ avgCpm: 42.5 } as CampaignForecastData}
      />,
    );
    expect(document.body.textContent).toContain("-");
  });

  it("adds targeted-but-unbooked channels as zero rows", () => {
    render(
      <MediaPlanInventoryMix
        costSplitData={channels}
        mediaChannels={["DIGITAL_OOH", "CLASSIC_OOH"]}
      />,
    );
    const table = document.getElementById("media-plan-inventory-mix-table");
    // header + 2 channel rows (Digital booked + Classic zero) + total = 4
    expect(table?.querySelectorAll("tr").length).toBe(4);
    expect(table?.textContent).toContain("Classic");
  });

  it("does not duplicate a channel already present in the cost split", () => {
    render(
      <MediaPlanInventoryMix
        costSplitData={channels}
        mediaChannels={["DIGITAL_OOH"]}
      />,
    );
    const table = document.getElementById("media-plan-inventory-mix-table");
    // Digital already booked → no extra row. header + 1 + total = 3
    expect(table?.querySelectorAll("tr").length).toBe(3);
  });
});
