import { useLazyGetSelectedInventoryQuery } from "@services/inventory/inventorySlice";
import { useCallback, useEffect, useRef, useState } from "react";
import { InventoryItem } from "src/types/inventory.types";

import { useMediaOwnerIds } from "./useMediaOwnerIds";

interface UseSelectedInventoryListOptions {
  campaignId: string | undefined;
  enabled?: boolean;
  pageSize?: number;
  sortBy?: string;
  sortDir?: "asc" | "desc";
  onInitialLoad?: (items: InventoryItem[]) => void;
  onLoadMore?: (items: InventoryItem[]) => void;
}

interface UseSelectedInventoryListReturn {
  selectedItems: InventoryItem[];
  isLoading: boolean;
  isLoadingMore: boolean;
  isLastPage: boolean;
  totalElements: number;
  loadSelectedInventory: (page: number, append?: boolean) => Promise<void>;
  scrollContainerRef: React.RefObject<HTMLDivElement | null>;
  reset: () => void;
  refetch: () => Promise<void>;
}

/**
 * Reusable hook for fetching and managing selected inventory list with pagination
 * Handles scroll-based load more, auto-load when content fits, and throttling
 */
export const useSelectedInventoryList = (
  options: UseSelectedInventoryListOptions,
): UseSelectedInventoryListReturn => {
  const {
    campaignId,
    enabled = true,
    pageSize = 10,
    sortBy = "name",
    sortDir = "asc",
    onInitialLoad,
    onLoadMore,
  } = options;

  const [fetchSelectedInventory] = useLazyGetSelectedInventoryQuery();
  const mediaOwnerIds = useMediaOwnerIds();
  const mediaOwnerIdsRef = useRef(mediaOwnerIds);
  mediaOwnerIdsRef.current = mediaOwnerIds;

  // State for API-fetched data
  const [selectedItems, setSelectedItems] = useState<InventoryItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [isLastPage, setIsLastPage] = useState(false);
  const [totalElements, setTotalElements] = useState(0);

  // Refs
  const currentPageRef = useRef(0);
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const hasInitialLoadRef = useRef(false);
  const isLoadingRef = useRef(false); // Prevent multiple simultaneous API calls
  const hasCheckedAutoLoadRef = useRef(false); // Track if we've already checked for auto-load

  // Load selected inventory from API
  const loadSelectedInventory = useCallback(
    async (page: number, append = false) => {
      if (!campaignId || !enabled) {
        return;
      }

      // Prevent multiple simultaneous API calls
      if (isLoadingRef.current) {
        return;
      }

      try {
        isLoadingRef.current = true;
        if (append) {
          setIsLoadingMore(true);
        } else {
          setIsLoading(true);
        }

        const result = await fetchSelectedInventory({
          campaignId,
          params: {
            page,
            size: pageSize,
            sortBy,
            sortDir: sortDir.toUpperCase() as "ASC" | "DESC",
          },
          ...(mediaOwnerIdsRef.current.length > 0
            ? { mediaOwnerIds: mediaOwnerIdsRef.current }
            : {}),
        }).unwrap();

        if (result.success && result.data) {
          const { content, totalElements: total, last } = result.data;

          if (append) {
            setSelectedItems((prev) => [...prev, ...content]);
            // Reset auto-load check when new data is appended (for next potential auto-load)
            if (content.length > 0) {
              hasCheckedAutoLoadRef.current = false;
            }
            onLoadMore?.(content);
          } else {
            setSelectedItems(content);
            // Reset auto-load check on initial load
            hasCheckedAutoLoadRef.current = false;
            onInitialLoad?.(content);
          }

          setTotalElements(total);
          setIsLastPage(last);
          currentPageRef.current = page;
        }
      } catch (error) {
        console.error("Error loading selected inventory:", error);
      } finally {
        setIsLoading(false);
        setIsLoadingMore(false);
        isLoadingRef.current = false;
      }
    },
    [
      campaignId,
      enabled,
      pageSize,
      sortBy,
      sortDir,
      fetchSelectedInventory,
      onInitialLoad,
      onLoadMore,
    ],
  );

  // Reset function
  const reset = useCallback(() => {
    hasInitialLoadRef.current = false;
    setSelectedItems([]);
    setTotalElements(0);
    currentPageRef.current = 0;
    isLoadingRef.current = false;
    hasCheckedAutoLoadRef.current = false;
  }, []);

  // Refetch function - resets and reloads from first page
  const refetch = useCallback(async () => {
    reset();
    hasInitialLoadRef.current = false;
    currentPageRef.current = 0;
    hasCheckedAutoLoadRef.current = false;
    if (enabled && campaignId) {
      await loadSelectedInventory(0, false);
    }
  }, [reset, enabled, campaignId, loadSelectedInventory]);

  // Check if we're loading all items at once (no pagination)
  const loadAllAtOnce = pageSize === -1;

  // Load data when enabled/campaignId changes
  useEffect(() => {
    if (enabled && campaignId && !hasInitialLoadRef.current) {
      hasInitialLoadRef.current = true;
      currentPageRef.current = 0;
      hasCheckedAutoLoadRef.current = false; // Reset auto-load check
      loadSelectedInventory(0, false);
    } else if (!enabled || !campaignId) {
      // Reset when disabled or campaignId changes
      reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, campaignId]);

  // Check if we need to load more when items are rendered (for cases where content fits in container)
  // Skip this logic when loading all at once (pageSize === -1)
  useEffect(() => {
    if (
      loadAllAtOnce || // Skip pagination logic when loading all at once
      !enabled ||
      !campaignId ||
      isLoadingMore ||
      isLastPage ||
      isLoadingRef.current ||
      selectedItems.length === 0 || // Don't auto-load if we don't have initial data yet
      hasCheckedAutoLoadRef.current // Don't check again if we've already checked
    ) {
      return;
    }

    // Small delay to ensure DOM is updated
    const timeoutId = setTimeout(() => {
      const container = scrollContainerRef.current;
      if (container) {
        const { scrollHeight, clientHeight } = container;
        // Only auto-load if:
        // 1. Content fits in container (no scrollbar)
        // 2. We have more items to load (not on last page)
        // 3. Current items count is less than total
        // 4. We're not already loading
        if (
          scrollHeight <= clientHeight &&
          !isLastPage &&
          selectedItems.length < totalElements &&
          !isLoadingRef.current
        ) {
          hasCheckedAutoLoadRef.current = true; // Mark as checked
          const nextPage = currentPageRef.current + 1;
          loadSelectedInventory(nextPage, true);
        } else {
          // If we don't need to auto-load, mark as checked anyway
          hasCheckedAutoLoadRef.current = true;
        }
      }
    }, 300);

    return () => clearTimeout(timeoutId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    selectedItems.length,
    enabled,
    campaignId,
    isLastPage,
    totalElements,
    loadAllAtOnce,
  ]);

  // Handle scroll for pagination
  const handleScroll = useCallback(() => {
    // Skip scroll handling when loading all at once
    if (loadAllAtOnce) {
      return;
    }

    const container = scrollContainerRef.current;
    if (
      !container ||
      !enabled ||
      isLoadingMore ||
      isLastPage ||
      !campaignId ||
      isLoadingRef.current
    ) {
      return;
    }

    const { scrollTop, scrollHeight, clientHeight } = container;

    // Check if we can scroll (content is taller than container)
    if (scrollHeight <= clientHeight) {
      // If content fits in container, don't trigger load here
      // The auto-load effect will handle this case
      return;
    }

    const scrollPercentage = (scrollTop + clientHeight) / scrollHeight;

    // Load more when scrolled to 80%
    if (scrollPercentage >= 0.8) {
      const nextPage = currentPageRef.current + 1;
      loadSelectedInventory(nextPage, true);
    }
  }, [
    loadAllAtOnce,
    enabled,
    isLoadingMore,
    isLastPage,
    campaignId,
    loadSelectedInventory,
    selectedItems.length,
    totalElements,
  ]);

  // Attach scroll listener with throttling
  // Skip scroll listener when loading all at once
  useEffect(() => {
    if (loadAllAtOnce || !enabled) return;

    const container = scrollContainerRef.current;
    if (!container) return;

    let scrollTimeout: ReturnType<typeof setTimeout> | null = null;
    const throttledHandleScroll = () => {
      if (scrollTimeout) return;
      scrollTimeout = setTimeout(() => {
        handleScroll();
        scrollTimeout = null;
      }, 200);
    };

    container.addEventListener("scroll", throttledHandleScroll);
    return () => {
      container.removeEventListener("scroll", throttledHandleScroll);
      if (scrollTimeout) clearTimeout(scrollTimeout);
    };
  }, [handleScroll, enabled, loadAllAtOnce]);

  return {
    selectedItems,
    isLoading,
    isLoadingMore,
    isLastPage,
    totalElements,
    loadSelectedInventory,
    scrollContainerRef,
    reset,
    refetch,
  };
};
