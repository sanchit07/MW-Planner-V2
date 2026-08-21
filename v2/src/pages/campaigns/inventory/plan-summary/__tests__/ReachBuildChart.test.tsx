import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import ReachBuildChart from "../ReachBuildChart";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string) =>
      key === "inventories.planSummary.reachBuild.subtitle"
        ? "Across %COUNT% inventories"
        : key,
  }),
}));

// chart.js cannot render to a real canvas in jsdom — stub the Line component.
vi.mock("react-chartjs-2", () => ({
  Line: ({ data }: { data: { datasets: { data: number[] }[] } }) => (
    <div data-testid="line-chart">{data.datasets[0].data.join(",")}</div>
  ),
}));

describe("ReachBuildChart", () => {
  it("renders the title, subtitle with count, and the data", () => {
    render(
      <ReachBuildChart
        data={[0, 50, 100]}
        labels={["Jan 01", "Jan 02", "Jan 03"]}
        inventoryCount={5}
      />,
    );
    expect(
      screen.getByText("inventories.planSummary.reachBuild.title"),
    ).toBeInTheDocument();
    expect(screen.getByText("Across 5 inventories")).toBeInTheDocument();
    expect(screen.getByTestId("line-chart")).toHaveTextContent("0,50,100");
  });
});
