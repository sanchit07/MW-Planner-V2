import { render, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import MediaPlanAudienceMap from "../MediaPlanAudienceMap";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string) => key,
  }),
}));

vi.mock("@components/ui/Mapbox", () => ({
  default: () => <div data-testid="mapbox" />,
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

const baseProps = {
  mapView: { center: [0, 0] as [number, number], zoom: 2 },
  mapConfig: {} as never,
  locations: [],
  summary: {
    sitesPinned: 13,
    marketCount: 11,
    totalInventory: 13,
    densestMarket: "Downtown",
  },
  onMapReady: vi.fn(),
  onInteractiveClick: vi.fn(),
  theme,
};

describe("MediaPlanAudienceMap", () => {
  it("renders banner with theme background and the map", () => {
    render(<MediaPlanAudienceMap {...baseProps} />);
    expect(
      document.getElementById("media-plan-audience-map-header"),
    ).toHaveStyle({ backgroundColor: "var(--color-mw-primary-500)" });
    expect(
      document.getElementById("media-plan-audience-map-canvas"),
    ).toBeInTheDocument();
  });

  it("renders three summary boxes and the note", () => {
    render(<MediaPlanAudienceMap {...baseProps} />);
    const summary = document.getElementById("media-plan-audience-map-summary");
    expect(summary?.children.length).toBe(3);
    expect(summary?.textContent).toContain("13");
    expect(summary?.textContent).toContain("Downtown");
    expect(
      document.getElementById("media-plan-audience-map-note"),
    ).toBeInTheDocument();
  });

  it("fires onInteractiveClick when the button is pressed", () => {
    const onInteractiveClick = vi.fn();
    render(
      <MediaPlanAudienceMap
        {...baseProps}
        onInteractiveClick={onInteractiveClick}
      />,
    );
    fireEvent.click(
      document.getElementById("media-plan-audience-map-button") as Element,
    );
    expect(onInteractiveClick).toHaveBeenCalledOnce();
  });
});
