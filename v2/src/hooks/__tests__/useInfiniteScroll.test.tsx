import { renderHook, waitFor } from "@testing-library/react";
import { act } from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

import { useInfiniteScroll } from "../useInfiniteScroll";

async function triggerEffectRerender(
  rerender: (props: {
    hasMore: boolean;
    loading: boolean;
    threshold: number;
  }) => void,
  threshold: number,
) {
  await act(async () => {
    rerender({ hasMore: true, loading: false, threshold: threshold + 1 });
    await new Promise((resolve) => setTimeout(resolve, 50));
  });
  await act(async () => {
    rerender({ hasMore: true, loading: false, threshold });
    await new Promise((resolve) => setTimeout(resolve, 50));
  });
}

function getScrollHandlerFromSpy(
  addEventListenerSpy: ReturnType<typeof vi.spyOn>,
): EventListener {
  const handlerCalls = addEventListenerSpy.mock.calls.filter(
    (call) => call[0] === "scroll",
  );
  expect(handlerCalls.length).toBeGreaterThan(0);
  return handlerCalls[handlerCalls.length - 1]?.[1] as EventListener;
}

describe("useInfiniteScroll", () => {
  let mockContainer: HTMLDivElement;
  let mockOnLoadMore: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    mockOnLoadMore = vi.fn();
    mockContainer = document.createElement("div");
    mockContainer.scrollTop = 0;
    // Use Object.defineProperty for read-only properties
    Object.defineProperty(mockContainer, "scrollHeight", {
      value: 1000,
      writable: false,
      configurable: true,
    });
    Object.defineProperty(mockContainer, "clientHeight", {
      value: 500,
      writable: false,
      configurable: true,
    });
    document.body.appendChild(mockContainer);
  });

  afterEach(() => {
    if (mockContainer && mockContainer.parentNode) {
      mockContainer.parentNode.removeChild(mockContainer);
    }
  });

  it("should return container ref", () => {
    const { result } = renderHook(() =>
      useInfiniteScroll({
        hasMore: true,
        loading: false,
        onLoadMore: mockOnLoadMore,
      }),
    );
    expect(result.current).toBeDefined();
    expect(result.current.current).toBeNull();
  });

  it("should call onLoadMore when scrolled near bottom", async () => {
    const addEventListenerSpy = vi.spyOn(mockContainer, "addEventListener");

    const { result, rerender } = renderHook(
      (props) =>
        useInfiniteScroll({
          hasMore: props.hasMore ?? true,
          loading: props.loading ?? false,
          onLoadMore: mockOnLoadMore,
          threshold: props.threshold ?? 100,
        }),
      {
        initialProps: { hasMore: true, loading: false, threshold: 100 },
      },
    );

    result.current.current = mockContainer;
    await triggerEffectRerender(rerender, 100);
    await waitFor(() => expect(addEventListenerSpy).toHaveBeenCalled(), {
      timeout: 2000,
    });
    const handler = getScrollHandlerFromSpy(addEventListenerSpy);
    await act(async () => {
      Object.defineProperty(mockContainer, "scrollTop", {
        value: 400,
        writable: true,
        configurable: true,
      });
      handler(new Event("scroll") as Event);
    });
    expect(mockOnLoadMore).toHaveBeenCalled();
  });

  it("should not call onLoadMore when loading", () => {
    const { result } = renderHook(() =>
      useInfiniteScroll({
        hasMore: true,
        loading: true,
        onLoadMore: mockOnLoadMore,
      }),
    );

    result.current.current = mockContainer;
    mockContainer.scrollTop = 400;

    const scrollEvent = new Event("scroll");
    mockContainer.dispatchEvent(scrollEvent);

    expect(mockOnLoadMore).not.toHaveBeenCalled();
  });

  it("should not call onLoadMore when hasMore is false", () => {
    const { result } = renderHook(() =>
      useInfiniteScroll({
        hasMore: false,
        loading: false,
        onLoadMore: mockOnLoadMore,
      }),
    );

    result.current.current = mockContainer;
    mockContainer.scrollTop = 400;

    const scrollEvent = new Event("scroll");
    mockContainer.dispatchEvent(scrollEvent);

    expect(mockOnLoadMore).not.toHaveBeenCalled();
  });

  it("should use custom threshold", async () => {
    const addEventListenerSpy = vi.spyOn(mockContainer, "addEventListener");

    const { result, rerender } = renderHook(
      (props) =>
        useInfiniteScroll({
          hasMore: props.hasMore ?? true,
          loading: props.loading ?? false,
          onLoadMore: mockOnLoadMore,
          threshold: props.threshold ?? 200,
        }),
      {
        initialProps: { hasMore: true, loading: false, threshold: 200 },
      },
    );

    result.current.current = mockContainer;
    await triggerEffectRerender(rerender, 200);
    await waitFor(() => expect(addEventListenerSpy).toHaveBeenCalled(), {
      timeout: 2000,
    });
    const handler = getScrollHandlerFromSpy(addEventListenerSpy);
    await act(async () => {
      Object.defineProperty(mockContainer, "scrollTop", {
        value: 300,
        writable: true,
        configurable: true,
      });
      handler(new Event("scroll") as Event);
    });
    expect(mockOnLoadMore).toHaveBeenCalled();
  });

  it("should cleanup event listener on unmount", async () => {
    const addEventListenerSpy = vi.spyOn(mockContainer, "addEventListener");
    const removeEventListenerSpy = vi.spyOn(
      mockContainer,
      "removeEventListener",
    );

    const { result, rerender, unmount } = renderHook(
      (props) =>
        useInfiniteScroll({
          hasMore: props.hasMore ?? true,
          loading: props.loading ?? false,
          onLoadMore: mockOnLoadMore,
          threshold: props.threshold ?? 100,
        }),
      {
        initialProps: { hasMore: true, loading: false, threshold: 100 },
      },
    );

    result.current.current = mockContainer;
    await triggerEffectRerender(rerender, 100);
    await waitFor(() => expect(addEventListenerSpy).toHaveBeenCalled(), {
      timeout: 2000,
    });
    const scrollHandler = getScrollHandlerFromSpy(addEventListenerSpy);
    expect(scrollHandler).toBeDefined();

    await act(async () => {
      unmount();
    });

    // After unmount, cleanup should be called
    expect(removeEventListenerSpy).toHaveBeenCalled();
    const removeCalls = removeEventListenerSpy.mock.calls.filter(
      (call) => call[0] === "scroll",
    );
    expect(removeCalls.length).toBeGreaterThan(0);
  });

  it("should call onLoadMore when scroll ratio meets thresholdRatio", async () => {
    const addEventListenerSpy = vi.spyOn(mockContainer, "addEventListener");

    const { result, rerender } = renderHook(
      (props: {
        hasMore?: boolean;
        loading?: boolean;
        thresholdRatio?: number;
      }) =>
        useInfiniteScroll({
          hasMore: props.hasMore ?? true,
          loading: props.loading ?? false,
          onLoadMore: mockOnLoadMore,
          thresholdRatio: props.thresholdRatio ?? 0.8,
        }),
      {
        initialProps: { hasMore: true, loading: false, thresholdRatio: 0.8 },
      },
    );

    result.current.current = mockContainer;
    await act(async () => {
      rerender({ hasMore: true, loading: false, thresholdRatio: 0.9 });
      await new Promise((resolve) => setTimeout(resolve, 50));
    });
    await act(async () => {
      rerender({ hasMore: true, loading: false, thresholdRatio: 0.8 });
      await new Promise((resolve) => setTimeout(resolve, 50));
    });
    await waitFor(() => expect(addEventListenerSpy).toHaveBeenCalled(), {
      timeout: 2000,
    });
    const handler = getScrollHandlerFromSpy(addEventListenerSpy);
    await act(async () => {
      Object.defineProperty(mockContainer, "scrollTop", {
        value: 400,
        writable: true,
        configurable: true,
      });
      handler(new Event("scroll") as Event);
    });
    expect(mockOnLoadMore).toHaveBeenCalled();
  });

  it("should not call onLoadMore when scroll ratio is below thresholdRatio", async () => {
    const addEventListenerSpy = vi.spyOn(mockContainer, "addEventListener");

    const { result, rerender } = renderHook(
      (props: {
        hasMore?: boolean;
        loading?: boolean;
        thresholdRatio?: number;
      }) =>
        useInfiniteScroll({
          hasMore: props.hasMore ?? true,
          loading: props.loading ?? false,
          onLoadMore: mockOnLoadMore,
          thresholdRatio: props.thresholdRatio ?? 0.8,
        }),
      {
        initialProps: { hasMore: true, loading: false, thresholdRatio: 0.8 },
      },
    );

    result.current.current = mockContainer;
    await act(async () => {
      rerender({ hasMore: true, loading: false, thresholdRatio: 0.9 });
      await new Promise((resolve) => setTimeout(resolve, 50));
    });
    await act(async () => {
      rerender({ hasMore: true, loading: false, thresholdRatio: 0.8 });
      await new Promise((resolve) => setTimeout(resolve, 50));
    });
    await waitFor(() => expect(addEventListenerSpy).toHaveBeenCalled(), {
      timeout: 2000,
    });
    const handler = getScrollHandlerFromSpy(addEventListenerSpy);
    await act(async () => {
      Object.defineProperty(mockContainer, "scrollTop", {
        value: 100,
        writable: true,
        configurable: true,
      });
      handler(new Event("scroll") as Event);
    });
    expect(mockOnLoadMore).not.toHaveBeenCalled();
  });

  it("should cleanup throttle timeout on unmount when throttleMs is set", async () => {
    const clearTimeoutSpy = vi.spyOn(globalThis, "clearTimeout");
    const addEventListenerSpy = vi.spyOn(mockContainer, "addEventListener");

    const { result, rerender, unmount } = renderHook(
      (props: { throttleMs?: number }) =>
        useInfiniteScroll({
          hasMore: true,
          loading: false,
          onLoadMore: mockOnLoadMore,
          thresholdRatio: 0.8,
          throttleMs: props.throttleMs ?? 200,
        }),
      { initialProps: { throttleMs: 200 } },
    );

    result.current.current = mockContainer;
    await act(async () => {
      rerender({ throttleMs: 201 });
      await new Promise((resolve) => setTimeout(resolve, 50));
    });
    await act(async () => {
      rerender({ throttleMs: 200 });
      await new Promise((resolve) => setTimeout(resolve, 50));
    });
    await waitFor(() => expect(addEventListenerSpy).toHaveBeenCalled(), {
      timeout: 2000,
    });
    const handler = getScrollHandlerFromSpy(addEventListenerSpy);
    await act(async () => {
      Object.defineProperty(mockContainer, "scrollTop", {
        value: 400,
        writable: true,
        configurable: true,
      });
      handler(new Event("scroll") as Event);
    });

    await act(async () => {
      unmount();
    });

    expect(clearTimeoutSpy).toHaveBeenCalled();
    clearTimeoutSpy.mockRestore();
  });
});
