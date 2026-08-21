import { render } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import MediaPlanTitleSlide from "../MediaPlanTitleSlide";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string, params?: Record<string, string | number>) =>
      params?.count !== undefined ? `${params.count} days` : key,
  }),
}));

vi.mock("@utils/dateUtils", () => ({
  formatDisplayDate: (d: string) => d || "",
}));

const defaultTheme = {
  id: "primary",
  name: "Test",
  colors: {
    primary: "--color-mw-primary-500",
    secondary: "--color-mw-primary-400",
    accent: "--color-mw-primary-300",
  },
};

describe("MediaPlanTitleSlide", () => {
  it("renders title slide card and container", () => {
    render(<MediaPlanTitleSlide theme={defaultTheme} />);
    expect(
      document.getElementById("media-plan-title-slide-card"),
    ).toBeInTheDocument();
    expect(
      document.getElementById("media-plan-title-slide-container"),
    ).toBeInTheDocument();
  });

  it("displays campaign name from headerInfo, left-aligned", () => {
    render(
      <MediaPlanTitleSlide
        headerInfo={{ name: "Summer Campaign" }}
        theme={defaultTheme}
      />,
    );
    expect(
      document.getElementById("media-plan-title-slide-campaign-name"),
    ).toHaveTextContent("Summer Campaign");
  });

  it("displays plan dates and computed duration when both dates provided", () => {
    render(
      <MediaPlanTitleSlide
        headerInfo={{ startDate: "2026-05-01", endDate: "2026-05-31" }}
        theme={defaultTheme}
      />,
    );
    expect(
      document.getElementById("media-plan-title-slide-campaign-period-value"),
    ).toHaveTextContent("2026-05-01 – 2026-05-31");
    // 31 days inclusive
    expect(
      document.getElementById("media-plan-title-slide-duration"),
    ).toHaveTextContent("31 days");
  });

  it("hides plan dates block values when dates missing", () => {
    render(<MediaPlanTitleSlide headerInfo={{}} theme={defaultTheme} />);
    expect(
      document.getElementById("media-plan-title-slide-campaign-period-value"),
    ).not.toBeInTheDocument();
    expect(
      document.getElementById("media-plan-title-slide-duration"),
    ).not.toBeInTheDocument();
  });

  it("displays prepared by from headerInfo", () => {
    render(
      <MediaPlanTitleSlide
        headerInfo={{ preparedBy: "John Doe" }}
        theme={defaultTheme}
      />,
    );
    expect(
      document.getElementById("media-plan-title-slide-prepared-by-value"),
    ).toHaveTextContent("John Doe");
  });

  it("renders status badge when status provided", () => {
    render(
      <MediaPlanTitleSlide
        headerInfo={{ status: "PLANNED" }}
        theme={defaultTheme}
      />,
    );
    expect(
      document.getElementById("media-plan-title-slide-status"),
    ).toBeInTheDocument();
  });

  it("renders brand section when brandDetails has name", () => {
    render(
      <MediaPlanTitleSlide
        theme={defaultTheme}
        brandDetails={{ name: "Acme" }}
      />,
    );
    expect(
      document.getElementById("media-plan-title-slide-brand"),
    ).toBeInTheDocument();
    expect(
      document.getElementById("media-plan-title-slide-brand-name"),
    ).toHaveTextContent("Acme");
  });

  it("does not render metric cards (moved to performance metrics section)", () => {
    render(
      <MediaPlanTitleSlide
        headerInfo={{ budget: 10000, totalCost: 9500, impressions: 500000 }}
        theme={defaultTheme}
      />,
    );
    expect(
      document.getElementById("media-plan-title-slide-total-budget"),
    ).not.toBeInTheDocument();
    expect(
      document.getElementById("media-plan-title-slide-impressions"),
    ).not.toBeInTheDocument();
  });
});
