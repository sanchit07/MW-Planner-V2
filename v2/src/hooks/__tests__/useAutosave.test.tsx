import { configureStore } from "@reduxjs/toolkit";
import campaignSlice, {
  useAutosaveCampaignMutation,
} from "@services/campaign/campaignSlice";
import { renderHook, act, waitFor } from "@testing-library/react";
import React from "react";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import type { CampaignCreateResponse } from "../../types/campaign.types";
import { useAutosave } from "../useAutosave";

const mockMutate = vi.fn();
vi.mock("@services/campaign/campaignSlice", async (importOriginal) => {
  const mod =
    await importOriginal<typeof import("@services/campaign/campaignSlice")>();
  return {
    ...mod,
    useAutosaveCampaignMutation: vi.fn(),
  };
});

const createStore = (campaignId: string | null) =>
  configureStore({
    reducer: { campaign: campaignSlice },
    preloadedState: {
      campaign: {
        campaignId,
        campaignData: campaignId
          ? ({ id: campaignId, name: "Test" } as CampaignCreateResponse)
          : null,
        currentCampaignName: "",
        isCreating: false,
        createError: null,
        isEditMode: false,
        forecastData: null,
        recommendationRun: null,
      },
    },
  });

const wrapper =
  (campaignId: string | null) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(Provider, { store: createStore(campaignId), children });

describe("useAutosave", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAutosaveCampaignMutation).mockReturnValue([
      mockMutate as ReturnType<typeof useAutosaveCampaignMutation>[0],
      { reset: vi.fn() } as ReturnType<typeof useAutosaveCampaignMutation>[1],
    ] as ReturnType<typeof useAutosaveCampaignMutation>);
    mockMutate.mockReturnValue({
      unwrap: vi.fn().mockResolvedValue({ id: "1" }),
    });
  });

  it("returns autosave, autosaveBatch, and isAutosaving", () => {
    const { result } = renderHook(() => useAutosave(), {
      wrapper: wrapper("campaign-1"),
    });
    expect(result.current.autosave).toBeDefined();
    expect(typeof result.current.autosave).toBe("function");
    expect(result.current.autosaveBatch).toBeDefined();
    expect(typeof result.current.autosaveBatch).toBe("function");
    expect(typeof result.current.isAutosaving).toBe("boolean");
  });

  it("does not call mutation when campaignId is missing for autosave", async () => {
    const consoleSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
    const { result } = renderHook(() => useAutosave(), {
      wrapper: wrapper(null),
    });
    await act(async () => {
      await result.current.autosave("name", "value");
    });
    expect(mockMutate).not.toHaveBeenCalled();
    consoleSpy.mockRestore();
  });

  it("does not call mutation when value is empty and canSkipValueNullValidation is false", async () => {
    const consoleSpy = vi.spyOn(console, "log").mockImplementation(() => {});
    const { result } = renderHook(() => useAutosave(), {
      wrapper: wrapper("campaign-1"),
    });
    await act(async () => {
      await result.current.autosave("name", "");
    });
    expect(mockMutate).not.toHaveBeenCalled();
    consoleSpy.mockRestore();
  });

  it("calls mutation when campaignId and value are valid", async () => {
    const { result } = renderHook(() => useAutosave({ debounceMs: 0 }), {
      wrapper: wrapper("campaign-1"),
    });
    await act(async () => {
      await result.current.autosave("name", "value");
    });
    await waitFor(() => {
      expect(mockMutate).toHaveBeenCalledWith({
        id: "campaign-1",
        data: { name: "value" },
      });
    });
  });

  it("calls onSuccess when autosave succeeds", async () => {
    const onSuccess = vi.fn();
    const { result } = renderHook(
      () => useAutosave({ debounceMs: 0, onSuccess }),
      { wrapper: wrapper("campaign-1") },
    );
    await act(async () => {
      await result.current.autosave("name", "value");
    });
    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalled();
    });
  });

  it("calls onError when autosave fails", async () => {
    const onError = vi.fn();
    const err = new Error("Save failed");
    mockMutate.mockReturnValueOnce({
      unwrap: vi.fn().mockRejectedValue(err),
    });
    const { result } = renderHook(
      () => useAutosave({ debounceMs: 0, onError }),
      { wrapper: wrapper("campaign-1") },
    );
    await act(async () => {
      await result.current.autosave("name", "value");
    });
    await waitFor(() => {
      expect(onError).toHaveBeenCalledWith(err);
    });
  });

  it("does not call mutation when campaignId is missing for autosaveBatch", async () => {
    const consoleSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
    const { result } = renderHook(() => useAutosave(), {
      wrapper: wrapper(null),
    });
    await act(async () => {
      await result.current.autosaveBatch({ a: "1" });
    });
    expect(mockMutate).not.toHaveBeenCalled();
    consoleSpy.mockRestore();
  });

  it("does not call mutation when autosaveBatch has no valid data", async () => {
    const consoleSpy = vi.spyOn(console, "log").mockImplementation(() => {});
    const { result } = renderHook(() => useAutosave(), {
      wrapper: wrapper("campaign-1"),
    });
    await act(async () => {
      await result.current.autosaveBatch({ a: "", b: null, c: undefined });
    });
    expect(mockMutate).not.toHaveBeenCalled();
    consoleSpy.mockRestore();
  });

  it("calls mutation with filtered data for autosaveBatch", async () => {
    const { result } = renderHook(() => useAutosave({ debounceMs: 0 }), {
      wrapper: wrapper("campaign-1"),
    });
    await act(async () => {
      await result.current.autosaveBatch({
        name: "Campaign",
        empty: "",
        count: 10,
      });
    });
    await waitFor(() => {
      expect(mockMutate).toHaveBeenCalledWith({
        id: "campaign-1",
        data: { name: "Campaign", count: 10 },
      });
    });
  });

  it("allows empty value when canSkipValueNullValidation is true", async () => {
    const { result } = renderHook(() => useAutosave({ debounceMs: 0 }), {
      wrapper: wrapper("campaign-1"),
    });
    await act(async () => {
      await result.current.autosave("optional", null, true);
    });
    await waitFor(() => {
      expect(mockMutate).toHaveBeenCalledWith({
        id: "campaign-1",
        data: { optional: null },
      });
    });
  });
});
