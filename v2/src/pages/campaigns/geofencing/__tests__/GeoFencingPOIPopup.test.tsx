import { fireEvent, render, screen } from "@testing-library/react";
import { POIPlaceData } from "src/types/campaign.types";
import { describe, it, expect, vi } from "vitest";

import GeoFencingPOIPopup from "../GeoFencingPOIPopup";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const createMockPOI = (overrides?: Partial<POIPlaceData>): POIPlaceData => ({
  locationLat: 3.139,
  locationLng: 101.6869,
  displayName: "Toll Plaza Subang",
  primaryType: "transit_station",
  primaryTypeDisplayName: "Transit Station",
  address: "Lebuhraya Persekutuan, Subang Jaya, Selangor, Malaysia",
  ...overrides,
});

describe("GeoFencingPOIPopup", () => {
  it("renders the POI displayName as the title", () => {
    render(<GeoFencingPOIPopup poi={createMockPOI()} />);
    expect(screen.getByTestId("poi-popup-name")).toHaveTextContent(
      "Toll Plaza Subang",
    );
  });

  it("renders the category from primaryTypeDisplayName", () => {
    render(<GeoFencingPOIPopup poi={createMockPOI()} />);
    expect(screen.getByTestId("poi-popup-category")).toHaveTextContent(
      "Transit Station",
    );
  });

  it("renders the address when provided", () => {
    render(<GeoFencingPOIPopup poi={createMockPOI()} />);
    expect(screen.getByTestId("poi-popup-address")).toHaveTextContent(
      "Lebuhraya Persekutuan, Subang Jaya, Selangor, Malaysia",
    );
  });

  it("does not render address block when address is missing", () => {
    render(<GeoFencingPOIPopup poi={createMockPOI({ address: undefined })} />);
    expect(screen.queryByTestId("poi-popup-address")).not.toBeInTheDocument();
  });

  it("falls back to primaryTypeDisplayName as title when displayName is empty", () => {
    render(<GeoFencingPOIPopup poi={createMockPOI({ displayName: "" })} />);
    expect(screen.getByTestId("poi-popup-name")).toHaveTextContent(
      "Transit Station",
    );
  });

  it("renders the photo overlay when photoUrl is provided", () => {
    render(
      <GeoFencingPOIPopup
        poi={createMockPOI()}
        photoUrl="https://example.com/photo.jpg"
      />,
    );
    const img = screen.getByRole("img") as HTMLImageElement;
    expect(img.src).toBe("https://example.com/photo.jpg");
  });

  it("renders the numeric rating with stars", () => {
    render(<GeoFencingPOIPopup poi={createMockPOI({ rating: 4.5 })} />);
    const rating = screen.getByTestId("poi-popup-rating");
    expect(rating).toHaveTextContent("4.5");
    // 5 star glyphs rendered (lucide svgs)
    expect(rating.querySelectorAll("svg").length).toBe(5);
  });

  it("shows 'N/A' when rating is missing", () => {
    render(<GeoFencingPOIPopup poi={createMockPOI({ rating: undefined })} />);
    const rating = screen.getByTestId("poi-popup-rating");
    expect(rating).toHaveTextContent("geofencingPopup.notAvailable");
    expect(rating.querySelectorAll("svg").length).toBe(0);
  });

  it("renders no <img> when photoUrl is null", () => {
    render(<GeoFencingPOIPopup poi={createMockPOI()} photoUrl={null} />);
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("hides the image when it errors", () => {
    render(
      <GeoFencingPOIPopup
        poi={createMockPOI()}
        photoUrl="https://example.com/broken.jpg"
      />,
    );
    const img = screen.getByRole("img") as HTMLImageElement;
    fireEvent.error(img);
    expect(img.style.display).toBe("none");
  });

  it("unwraps { text } objects for displayName and address", () => {
    render(
      <GeoFencingPOIPopup
        poi={createMockPOI({
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          displayName: { text: "Wrapped Name" } as any,
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          address: { text: "Wrapped Address 123" } as any,
        })}
      />,
    );
    expect(screen.getByTestId("poi-popup-name")).toHaveTextContent(
      "Wrapped Name",
    );
    expect(screen.getByTestId("poi-popup-address")).toHaveTextContent(
      "Wrapped Address 123",
    );
  });
});
