import { configureStore } from "@reduxjs/toolkit";
import { campaignApi } from "@services/campaign/campaignSlice";
import { render, screen, fireEvent } from "@testing-library/react";
import React from "react";
import { Provider } from "react-redux";
import { describe, it, expect, vi } from "vitest";

import {
  MobilityHeatmapControl,
  toCountrySlug,
  MOBILITY_TIME_BUCKETS,
} from "../MobilityHeatmap";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const makeStore = () =>
  configureStore({
    reducer: { [campaignApi.reducerPath]: campaignApi.reducer },
    middleware: (getDefault) => getDefault().concat(campaignApi.middleware),
  });

const renderControl = (
  overrides: Partial<React.ComponentProps<typeof MobilityHeatmapControl>> = {},
) => {
  const props = {
    enabled: false,
    onToggle: vi.fn(),
    timeBucket: "ALL" as const,
    onTimeBucketChange: vi.fn(),
    isLoading: false,
    isError: false,
    isEmpty: false,
    ...overrides,
  };
  render(
    <Provider store={makeStore()}>
      <MobilityHeatmapControl {...props} />
    </Provider>,
  );
  return props;
};

describe("toCountrySlug", () => {
  it("slugifies multi-word country names like the geo-fencing lookup", () => {
    expect(toCountrySlug("Sri Lanka")).toBe("sri-lanka");
    expect(toCountrySlug("Malaysia")).toBe("malaysia");
    expect(toCountrySlug(undefined)).toBeUndefined();
    expect(toCountrySlug("")).toBeUndefined();
  });
});

describe("MobilityHeatmapControl", () => {
  it("renders only the toggle when disabled", () => {
    renderControl();
    expect(screen.getByTestId("mobility-heatmap-control")).toBeInTheDocument();
    expect(screen.queryByText("mobilityHeatmap.low")).not.toBeInTheDocument();
  });

  it("shows all time-of-day buckets and the legend when enabled", () => {
    renderControl({ enabled: true });
    MOBILITY_TIME_BUCKETS.forEach((bucket) => {
      expect(
        screen.getByText(`mobilityHeatmap.buckets.${bucket.toLowerCase()}`),
      ).toBeInTheDocument();
    });
    expect(screen.getByText("mobilityHeatmap.low")).toBeInTheDocument();
    expect(screen.getByText("mobilityHeatmap.high")).toBeInTheDocument();
  });

  it("fires onTimeBucketChange when a bucket chip is clicked", () => {
    const props = renderControl({ enabled: true });
    fireEvent.click(screen.getByText("mobilityHeatmap.buckets.night"));
    expect(props.onTimeBucketChange).toHaveBeenCalledWith("NIGHT");
  });

  it("shows the empty state when there is no data for the area", () => {
    renderControl({ enabled: true, isEmpty: true });
    expect(screen.getByTestId("mobility-heatmap-empty")).toBeInTheDocument();
  });
});
