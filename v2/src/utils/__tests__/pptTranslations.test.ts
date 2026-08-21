import * as storageModule from "@utils/storage";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// vi.mock is hoisted before imports, so @utils/storage will be mocked when
// pptTranslations.ts is first evaluated.
vi.mock("@utils/storage", () => ({
  getItem: vi.fn(),
}));

import {
  getCurrentLanguage,
  getPPTTranslations,
  loadTranslations,
} from "../pptTranslations";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const mockGetItem = () => vi.mocked(storageModule.getItem);

// ---------------------------------------------------------------------------
// getCurrentLanguage
// ---------------------------------------------------------------------------

describe("getCurrentLanguage", () => {
  beforeEach(() => {
    mockGetItem().mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("returns 'en' when storage value is null", () => {
    mockGetItem().mockReturnValue(null);
    expect(getCurrentLanguage()).toBe("en");
  });

  it("returns 'en' when storage value is an unsupported language", () => {
    mockGetItem().mockReturnValue("fr");
    expect(getCurrentLanguage()).toBe("en");
  });

  it("returns 'en' when storage explicitly holds 'en'", () => {
    mockGetItem().mockReturnValue("en");
    expect(getCurrentLanguage()).toBe("en");
  });

  it("returns 'ja' when storage holds 'ja'", () => {
    mockGetItem().mockReturnValue("ja");
    expect(getCurrentLanguage()).toBe("ja");
  });

  it("reads from the correct localStorage key", () => {
    mockGetItem().mockReturnValue(null);
    getCurrentLanguage();
    expect(storageModule.getItem).toHaveBeenCalledWith("mw-planner-language");
  });
});

// ---------------------------------------------------------------------------
// loadTranslations
// ---------------------------------------------------------------------------

describe("loadTranslations", () => {
  it("loads English translations and returns an object with a media_plan key", async () => {
    const result = await loadTranslations("en");
    expect(result).toHaveProperty("media_plan");
  });

  it("uses 'en' as the default language when no argument is provided", async () => {
    const result = await loadTranslations();
    expect(result).toHaveProperty("media_plan");
  });

  it("loads Japanese translations and returns an object with a media_plan key", async () => {
    const result = await loadTranslations("ja");
    expect(result).toHaveProperty("media_plan");
  });

  it("returns the cached object (same reference) on a second call with the same language", async () => {
    // Force English into the cache first, then call again — the second call
    // must return the exact same object reference regardless of whether the
    // first call was a cache hit or a fresh load.
    const first = await loadTranslations("en");
    const second = await loadTranslations("en");
    expect(first).toBe(second);
  });

  it("returns the cached object (same reference) on a second call with Japanese", async () => {
    const first = await loadTranslations("ja");
    const second = await loadTranslations("ja");
    expect(first).toBe(second);
  });
});

// ---------------------------------------------------------------------------
// getPPTTranslations
// ---------------------------------------------------------------------------

describe("getPPTTranslations", () => {
  beforeEach(() => {
    mockGetItem().mockReset();
  });

  it("returns the media_plan subtree (has title_slide key)", async () => {
    mockGetItem().mockReturnValue("en");
    const result = await getPPTTranslations();
    expect(result).toHaveProperty("title_slide");
  });

  it("returns an object with the performance_metrics key", async () => {
    mockGetItem().mockReturnValue("en");
    const result = await getPPTTranslations();
    expect(result).toHaveProperty("performance_metrics");
  });

  it("returns an object with the cost_breakdown key", async () => {
    mockGetItem().mockReturnValue("en");
    const result = await getPPTTranslations();
    expect(result).toHaveProperty("cost_breakdown");
  });

  it("works when the stored language is Japanese", async () => {
    mockGetItem().mockReturnValue("ja");
    const result = await getPPTTranslations();
    expect(result).toHaveProperty("title_slide");
    expect(result).toHaveProperty("performance_metrics");
  });

  it("falls back to English when storage returns an unknown language", async () => {
    // getCurrentLanguage returns "en" for unknown languages, so loadTranslations
    // is called with "en" — getPPTTranslations should still succeed.
    mockGetItem().mockReturnValue("zh");
    const result = await getPPTTranslations();
    expect(result).toHaveProperty("title_slide");
  });
});
