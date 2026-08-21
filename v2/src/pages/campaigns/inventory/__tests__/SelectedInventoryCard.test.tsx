import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { InventoryItem } from "../../../../types/inventory.types";
import SelectedInventoryCard from "../SelectedInventoryCard";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (k: string) => k }),
}));

const makeItem = (location: unknown): InventoryItem =>
  ({
    detail: { id: "id-1", referenceId: "REF-1", name: "Billboard 1" },
    location,
    performance: { cpmRate: 0, totalAdPlays: 0 },
  }) as unknown as InventoryItem;

function renderCard(item: InventoryItem) {
  return render(
    <SelectedInventoryCard
      item={item}
      index={0}
      isSelected={false}
      isExpanded={false}
      onCardClick={() => {}}
      onToggleExpand={() => {}}
    />,
  );
}

describe("SelectedInventoryCard address fallback", () => {
  it("shows the address when present", () => {
    renderCard(makeItem({ location: { address: "123 Main St" } }));
    expect(screen.getByText("123 Main St")).toBeInTheDocument();
  });

  it("joins country + state when address is missing", () => {
    renderCard(makeItem({ location: { country: "Japan", state: "Tokyo" } }));
    expect(screen.getByText("Japan, Tokyo")).toBeInTheDocument();
  });

  it("never renders the literal 'undefined' when address/country/state are missing", () => {
    renderCard(makeItem({ location: {} }));
    expect(screen.queryByText(/undefined/)).not.toBeInTheDocument();
    expect(screen.getByText("--")).toBeInTheDocument();
  });

  it("does not crash and shows no 'undefined' when location itself is missing", () => {
    renderCard(makeItem(undefined));
    expect(screen.queryByText(/undefined/)).not.toBeInTheDocument();
  });
});

describe("SelectedInventoryCard limited-availability badge", () => {
  const baseScore = {
    score: 76.9,
    components: {
      geoFit: 100,
      availability: 45,
      budgetFit: 50,
      audienceFit: 50,
      brandFit: 100,
      qualityFit: 70,
      timeFit: 50,
      measureFit: 50,
    },
  };

  const renderWithScore = (availability?: unknown) =>
    render(
      <SelectedInventoryCard
        item={makeItem({ location: { address: "123 Main St" } })}
        index={0}
        isSelected={false}
        isExpanded={false}
        score={
          {
            ...baseScore,
            availability,
          } as never
        }
        onCardClick={() => {}}
        onToggleExpand={() => {}}
      />,
    );

  it("shows the badge with X/Y days when partially available", () => {
    renderWithScore({
      availableDays: 14,
      totalDays: 31,
      availabilityPercentage: 45.2,
      summary: "Limited availability for your dates: 14/31 days available",
      allAvailable: false,
    });
    const badge = screen.getByTestId("badge-limited-availability-REF-1");
    expect(badge).toBeInTheDocument();
    expect(badge.textContent).toContain(
      "inventoryDetailCard.limitedAvailability",
    );
    expect(badge.textContent).toContain("14/31");
  });

  it("hides the badge when fully available", () => {
    renderWithScore({
      availableDays: 31,
      totalDays: 31,
      availabilityPercentage: 100,
      summary: "31/31 days available",
      allAvailable: true,
    });
    expect(
      screen.queryByTestId("badge-limited-availability-REF-1"),
    ).not.toBeInTheDocument();
  });

  it("hides the badge when no availability annotation exists", () => {
    renderWithScore(undefined);
    expect(
      screen.queryByTestId("badge-limited-availability-REF-1"),
    ).not.toBeInTheDocument();
  });
});

describe("SelectedInventoryCard size badge", () => {
  const makeSizeItem = (size: string | undefined): InventoryItem =>
    ({
      detail: {
        id: "id-1",
        referenceId: "REF-1",
        name: "Billboard 1",
        size,
      },
      location: { location: { address: "123 Main St" } },
      performance: { cpmRate: 0, totalAdPlays: 0 },
    }) as unknown as InventoryItem;

  it("renders the size badge from detail.size, uppercased", () => {
    renderCard(makeSizeItem("m"));
    expect(screen.getByText("M")).toBeInTheDocument();
  });

  it("renders no size badge when detail.size is absent", () => {
    renderCard(makeSizeItem(undefined));
    expect(screen.queryByText("M")).not.toBeInTheDocument();
  });
});

describe("SelectedInventoryCard venue type chip", () => {
  const makeVenueItem = (venueType: string[]): InventoryItem =>
    ({
      detail: {
        id: "id-1",
        referenceId: "REF-1",
        name: "Billboard 1",
        venueType,
      },
      location: { location: { address: "123 Main St" } },
      performance: { cpmRate: 0, totalAdPlays: 0 },
    }) as unknown as InventoryItem;

  it("renders a single venue type with no +N", () => {
    renderCard(makeVenueItem(["Outdoor"]));
    expect(screen.getByText("Outdoor")).toBeInTheDocument();
  });

  it("shows the first two venue types inline and collapses the rest to +N", () => {
    renderCard(makeVenueItem(["Outdoor", "Billboards", "Highway"]));
    expect(screen.getByText("Outdoor, Billboards +1")).toBeInTheDocument();
  });

  it("renders no venue chip when venueType is empty", () => {
    renderCard(makeVenueItem([]));
    expect(screen.queryByText("Outdoor")).not.toBeInTheDocument();
  });
});

describe("SelectedInventoryCard impression fallback", () => {
  const makePerfItem = (performance: Record<string, number>): InventoryItem =>
    ({
      detail: { id: "id-1", referenceId: "REF-1", name: "Billboard 1" },
      location: { location: { address: "123 Main St" } },
      performance,
    }) as unknown as InventoryItem;

  // Metrics only render when the card is expanded.
  function renderExpanded(item: InventoryItem) {
    return render(
      <SelectedInventoryCard
        item={item}
        index={0}
        isSelected={false}
        isExpanded={true}
        onCardClick={() => {}}
        onToggleExpand={() => {}}
      />,
    );
  }

  it("shows estimatedImpressions when present (ignores singular + totalAdPlays)", () => {
    renderExpanded(
      makePerfItem({
        cpmRate: 0,
        estimatedImpressions: 12345,
        estimatedImpression: 999,
        totalAdPlays: 111,
      }),
    );
    expect(screen.getByText("12,345")).toBeInTheDocument();
  });

  it("falls back to singular estimatedImpression when plural is absent", () => {
    renderExpanded(
      makePerfItem({
        cpmRate: 0,
        estimatedImpression: 6789,
        totalAdPlays: 111,
      }),
    );
    expect(screen.getByText("6,789")).toBeInTheDocument();
  });

  it("falls back to 0 when neither impression field is present", () => {
    renderExpanded(makePerfItem({ cpmRate: 0, totalAdPlays: 111 }));
    expect(screen.getByText("0")).toBeInTheDocument();
  });
});

describe("SelectedInventoryCard availability", () => {
  const item = (): InventoryItem =>
    ({
      detail: { id: "id-1", referenceId: "REF-1", name: "Billboard 1" },
      location: { location: { address: "123 Main St" } },
      performance: { cpmRate: 0, totalAdPlays: 0 },
    }) as unknown as InventoryItem;

  function renderWithAvailability(availability?: {
    loading: boolean;
    percent: number | null;
  }) {
    return render(
      <SelectedInventoryCard
        item={item()}
        index={0}
        isSelected={false}
        isExpanded={true}
        availability={availability}
        onCardClick={() => {}}
        onToggleExpand={() => {}}
      />,
    );
  }

  it("shows the percentage when resolved", () => {
    renderWithAvailability({ loading: false, percent: 83 });
    expect(screen.getByText("83%")).toBeInTheDocument();
  });

  it("shows a spinner while loading", () => {
    renderWithAvailability({ loading: true, percent: null });
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("shows '--' when percent is null and not loading", () => {
    renderWithAvailability({ loading: false, percent: null });
    // The availability value cell falls back to the placeholder.
    expect(screen.getAllByText("--").length).toBeGreaterThan(0);
  });

  it("shows '--' when no availability entry is provided", () => {
    renderWithAvailability(undefined);
    expect(screen.getAllByText("--").length).toBeGreaterThan(0);
  });
});
