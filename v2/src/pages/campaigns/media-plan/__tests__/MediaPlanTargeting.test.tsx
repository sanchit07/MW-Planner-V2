import { render } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import MediaPlanTargeting from "../MediaPlanTargeting";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
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

const targeting = {
  demographics: {
    age: ["18_24", "25_34"],
    gender: ["male", "female"],
    venues: [],
    behavior: ["Commuters", "Shoppers"],
    income: ["low", "upper_middle"],
    interests: ["Technology", "Travel"],
  },
  geofencing: { geometries: [], locations: [] },
  signals: [],
  venueTypes: {
    digitalOoh: ["transit-airports", "transit-buses"],
    classicOoh: ["retail-mall", "retail-grocery"],
  },
};

describe("MediaPlanTargeting", () => {
  it("renders banner with theme background and four columns", () => {
    render(<MediaPlanTargeting targeting={targeting} theme={theme} />);
    expect(document.getElementById("media-plan-targeting-header")).toHaveStyle({
      backgroundColor: "var(--color-mw-primary-500)",
    });
    ["demographics", "venue-types", "behaviour", "interests"].forEach((c) => {
      expect(
        document.getElementById(`media-plan-targeting-${c}`),
      ).toBeInTheDocument();
    });
  });

  it("formats age and income codes and title-cases gender", () => {
    render(<MediaPlanTargeting targeting={targeting} theme={theme} />);
    const demo = document.getElementById("media-plan-targeting-demographics");
    expect(demo?.textContent).toContain("18-24");
    expect(demo?.textContent).toContain("Upper Middle");
    expect(demo?.textContent).toContain("Female");
  });

  it("collapses venue codes to unique top-level categories", () => {
    render(<MediaPlanTargeting targeting={targeting} theme={theme} />);
    const venue = document.getElementById("media-plan-targeting-venue-types");
    expect(venue?.textContent).toContain("Transit");
    expect(venue?.textContent).toContain("Retail");
    // granular codes collapsed, not shown verbatim
    expect(venue?.textContent).not.toContain("transit-airports");
  });

  it("shows the not-selected label for empty dimensions", () => {
    render(
      <MediaPlanTargeting
        targeting={{
          demographics: {
            age: [],
            gender: [],
            venues: [],
            behavior: [],
            income: [],
            interests: [],
          },
          geofencing: { geometries: [], locations: [] },
          signals: [],
        }}
        theme={theme}
      />,
    );
    const behaviour = document.getElementById("media-plan-targeting-behaviour");
    expect(behaviour?.textContent).toContain(
      "media_plan.targeting_card.not_selected",
    );
  });
});
