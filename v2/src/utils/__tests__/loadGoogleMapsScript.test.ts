import { afterEach, describe, expect, it, vi } from "vitest";

import { loadGoogleMapsScript } from "../loadGoogleMapsScript";

describe("loadGoogleMapsScript", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("does not append a script when one already exists", () => {
    const existingScript = document.createElement("script");
    vi.spyOn(document, "querySelector").mockReturnValue(existingScript);
    const appendChildSpy = vi.spyOn(document.body, "appendChild");

    loadGoogleMapsScript("test-api-key");

    expect(document.querySelector).toHaveBeenCalledWith(
      'script[src*="maps.googleapis.com"]',
    );
    expect(appendChildSpy).not.toHaveBeenCalled();
  });

  it("creates and appends script with correct src when no existing script", () => {
    vi.spyOn(document, "querySelector").mockReturnValue(null);
    const appendChildSpy = vi.spyOn(document.body, "appendChild");
    const createElementSpy = vi.spyOn(document, "createElement");

    loadGoogleMapsScript("my-key-123");

    expect(createElementSpy).toHaveBeenCalledWith("script");
    expect(appendChildSpy).toHaveBeenCalledTimes(1);
    const appended = appendChildSpy.mock.calls[0][0] as HTMLScriptElement;
    expect(appended.src).toBe(
      "https://maps.googleapis.com/maps/api/js?key=my-key-123&libraries=places&loading=async",
    );
    expect(appended.async).toBe(true);
    expect(appended.defer).toBe(true);
  });

  it("uses empty key in src when apiKey is empty string", () => {
    vi.spyOn(document, "querySelector").mockReturnValue(null);
    const appendChildSpy = vi.spyOn(document.body, "appendChild");

    loadGoogleMapsScript("");

    const appended = appendChildSpy.mock.calls[0][0] as HTMLScriptElement;
    expect(appended.src).toContain("key=&libraries=places");
  });
});
