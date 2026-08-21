import { useCallback, useEffect, useRef } from "react";

export interface UseInfiniteScrollOptions {
  hasMore: boolean;
  loading: boolean;
  onLoadMore: () => void;
  /** Distance from bottom in pixels to trigger loading (used when thresholdRatio is not set) */
  threshold?: number;
  /** Scroll ratio 0–1: trigger when (scrollTop + clientHeight) / scrollHeight >= value (e.g. 0.8 = 80%) */
  thresholdRatio?: number;
  /** Throttle scroll handler in milliseconds */
  throttleMs?: number;
}

export const useInfiniteScroll = ({
  hasMore,
  loading,
  onLoadMore,
  threshold = 100,
  thresholdRatio,
  throttleMs = 0,
}: UseInfiniteScrollOptions) => {
  const containerRef = useRef<HTMLDivElement>(null);

  const throttleRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const checkAndLoad = useCallback(() => {
    const container = containerRef.current;
    if (!container || loading || !hasMore) return;

    const { scrollTop, scrollHeight, clientHeight } = container;
    if (scrollHeight <= 0) return;

    const shouldLoad =
      thresholdRatio !== undefined
        ? (scrollTop + clientHeight) / scrollHeight >= thresholdRatio
        : scrollHeight - scrollTop - clientHeight <= threshold;

    if (shouldLoad) {
      onLoadMore();
    }
  }, [loading, hasMore, onLoadMore, threshold, thresholdRatio]);

  const handleScroll = useCallback(() => {
    if (throttleMs <= 0) {
      checkAndLoad();
      return;
    }
    if (throttleRef.current != null) return;
    throttleRef.current = setTimeout(() => {
      throttleRef.current = null;
      checkAndLoad();
    }, throttleMs);
  }, [throttleMs, checkAndLoad]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    container.addEventListener("scroll", handleScroll);
    return () => {
      container.removeEventListener("scroll", handleScroll);
      if (throttleRef.current) {
        clearTimeout(throttleRef.current);
        throttleRef.current = null;
      }
    };
  }, [handleScroll]);

  return containerRef;
};
