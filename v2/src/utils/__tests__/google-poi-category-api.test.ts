import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  __resetPOIPhotoCacheForTests,
  getPOIPhotoUrl,
} from "../google-poi-category-api";

type GoogleMock = {
  maps: {
    LatLng: ReturnType<typeof vi.fn>;
    places: {
      Place: {
        searchByText: ReturnType<typeof vi.fn>;
      };
    };
  };
};

declare global {
  interface Window {
    // Allow tests to override the SDK shim.
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    google: any;
  }
}

const installGoogle = (searchByText: ReturnType<typeof vi.fn>): GoogleMock => {
  const google: GoogleMock = {
    maps: {
      LatLng: vi.fn((lat: number, lng: number) => ({ lat, lng })),
      places: {
        Place: { searchByText },
      },
    },
  };
  (window as unknown as { google: GoogleMock }).google = google;
  return google;
};

describe("getPOIPhotoUrl", () => {
  const basePoi = {
    displayName: "Toll Plaza Subang",
    locationLat: 3.14,
    locationLng: 101.69,
  };

  beforeEach(() => {
    __resetPOIPhotoCacheForTests();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (window as any).google = undefined;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("returns null when window.google is unavailable", async () => {
    const url = await getPOIPhotoUrl(basePoi);
    expect(url).toBeNull();
  });

  it("returns the photo URI from getURI with 240/160 sizing", async () => {
    const getURI = vi.fn(() => "https://photos.example.com/p.jpg");
    const searchByText = vi.fn(async () => ({
      places: [{ photos: [{ getURI }] }],
    }));
    installGoogle(searchByText);

    const url = await getPOIPhotoUrl(basePoi);

    expect(url).toBe("https://photos.example.com/p.jpg");
    expect(getURI).toHaveBeenCalledWith({ maxWidth: 240, maxHeight: 160 });
  });

  it("caches by displayName + lat + lng (second call reuses the first promise)", async () => {
    const getURI = vi.fn(() => "https://photos.example.com/p.jpg");
    const searchByText = vi.fn(async () => ({
      places: [{ photos: [{ getURI }] }],
    }));
    installGoogle(searchByText);

    const [a, b] = await Promise.all([
      getPOIPhotoUrl(basePoi),
      getPOIPhotoUrl(basePoi),
    ]);
    expect(a).toBe("https://photos.example.com/p.jpg");
    expect(b).toBe("https://photos.example.com/p.jpg");
    expect(searchByText).toHaveBeenCalledTimes(1);
  });

  it("resolves to null (does not throw) when searchByText rejects", async () => {
    const searchByText = vi.fn(async () => {
      throw new Error("boom");
    });
    installGoogle(searchByText);

    const url = await getPOIPhotoUrl(basePoi);
    expect(url).toBeNull();
  });

  it("resolves to null when the result has no photos", async () => {
    const searchByText = vi.fn(async () => ({ places: [{ photos: [] }] }));
    installGoogle(searchByText);

    const url = await getPOIPhotoUrl(basePoi);
    expect(url).toBeNull();
  });
});
