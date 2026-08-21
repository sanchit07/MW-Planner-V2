import { fireEvent, render, screen } from "@testing-library/react";
import { MapMarkerLocation } from "src/types/campaign.types";
import { describe, it, expect, vi } from "vitest";

import GeoFencingLocationPopup from "../GeoFencingLocationPopup";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const createMockLocation = (
  overrides?: Partial<MapMarkerLocation>,
): MapMarkerLocation => ({
  id: "location-1",
  lat: 40.7128,
  lng: -74.006,
  name: "Test Location",
  address: "123 Test Street, New York, NY",
  included: true,
  isShape: false,
  ...overrides,
});

describe("GeoFencingLocationPopup", () => {
  it("renders the location name", () => {
    const location = createMockLocation({ name: "TOLL PLAZA SUBANG" });
    render(<GeoFencingLocationPopup item={location} />);
    expect(screen.getByTestId("popup-name")).toHaveTextContent(
      "TOLL PLAZA SUBANG",
    );
  });

  it("renders the address when provided", () => {
    const location = createMockLocation({
      address: "123 Test Street, New York, NY",
    });
    render(<GeoFencingLocationPopup item={location} />);
    expect(screen.getByTestId("popup-address")).toHaveTextContent(
      "123 Test Street, New York, NY",
    );
  });

  it("does not render the address block when address is missing", () => {
    const location = createMockLocation({ address: undefined });
    render(<GeoFencingLocationPopup item={location} />);
    expect(screen.queryByTestId("popup-address")).not.toBeInTheDocument();
  });

  it("advances through the fallback chain on image load error, then hides the <img>", () => {
    const location = createMockLocation({
      // No `type` → no POI icon candidate, so the only source is the Google photo.
      metadata: { photoUrl: "https://example.com/broken.jpg" },
    });
    render(<GeoFencingLocationPopup item={location} />);
    const img = screen.getByRole("img") as HTMLImageElement;
    fireEvent.error(img);
    // Source chain exhausted → <img> is unmounted and the Lucide fallback shows.
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("renders the MapPin icon (no photo <img>) for circle markers", () => {
    const location = createMockLocation({
      metadata: { type: "circle", photoUrl: "https://example.com/x.jpg" },
    });
    render(<GeoFencingLocationPopup item={location} />);
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });
});
