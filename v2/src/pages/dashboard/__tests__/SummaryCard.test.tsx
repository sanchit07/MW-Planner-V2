import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import SummaryCard from "../SummaryCard";

describe("SummaryCard", () => {
  it("renders title and value", () => {
    render(
      <SummaryCard
        icon={<span data-testid="icon">Icon</span>}
        iconBgColor="bg-blue-100"
        title="Total Revenue"
        value="MYR 1,234"
      />,
    );
    expect(screen.getByText("Total Revenue")).toBeInTheDocument();
    expect(screen.getByText("MYR 1,234")).toBeInTheDocument();
    expect(screen.getByTestId("icon")).toBeInTheDocument();
  });

  it("renders numeric value", () => {
    render(
      <SummaryCard
        icon={<span />}
        iconBgColor="bg-gray-100"
        title="Count"
        value={42}
      />,
    );
    expect(screen.getByText("42")).toBeInTheDocument();
  });

  it("applies iconBgColor to icon container", () => {
    const { container } = render(
      <SummaryCard
        icon={<span />}
        iconBgColor="bg-mw-primary-50"
        title="Test"
        value="0"
      />,
    );
    const iconContainer = container.querySelector(".bg-mw-primary-50");
    expect(iconContainer).toBeInTheDocument();
    expect(iconContainer).toHaveClass("bg-mw-primary-50");
  });

  it("does not render subtitle block when subtitle is undefined", () => {
    const { container } = render(
      <SummaryCard
        icon={<span />}
        iconBgColor="bg-gray-100"
        title="Test"
        value="0"
      />,
    );
    expect(
      container.querySelector(".text-mw-neutral-500"),
    ).not.toBeInTheDocument();
  });

  it("renders subtitle when provided", () => {
    render(
      <SummaryCard
        icon={<span />}
        iconBgColor="bg-gray-100"
        title="Test"
        value="0"
        subtitle="vs last period"
      />,
    );
    expect(screen.getByText("vs last period")).toBeInTheDocument();
  });

  it("renders positive trend with percentage and success styling", () => {
    render(
      <SummaryCard
        icon={<span />}
        iconBgColor="bg-gray-100"
        title="Test"
        value="100"
        subtitle="vs last period"
        trend={{ value: 12.5, isPositive: true }}
      />,
    );
    expect(screen.getByText("12.5%")).toBeInTheDocument();
    const trendEl = screen.getByText("12.5%");
    expect(trendEl).toHaveClass("text-mw-success-500");
  });

  it("renders negative trend with percentage and error styling", () => {
    render(
      <SummaryCard
        icon={<span />}
        iconBgColor="bg-gray-100"
        title="Test"
        value="100"
        subtitle="vs last period"
        trend={{ value: 5, isPositive: false }}
      />,
    );
    expect(screen.getByText("5%")).toBeInTheDocument();
    const trendEl = screen.getByText("5%");
    expect(trendEl).toHaveClass("text-mw-error-500");
  });

  it("renders subtitle without trend when trend is undefined", () => {
    render(
      <SummaryCard
        icon={<span />}
        iconBgColor="bg-gray-100"
        title="Test"
        value="0"
        subtitle="Only subtitle"
      />,
    );
    expect(screen.getByText("Only subtitle")).toBeInTheDocument();
    expect(screen.queryByText(/%/)).not.toBeInTheDocument();
  });
});
