import { LoadMore } from "@components/ui/LoadMore";
import { INFINITE_SCROLL_THRESHOLD } from "@constants/campaign.constants";
import { useInfiniteScroll } from "@hooks/useInfiniteScroll";
import {
  toggleItemSelection,
  // setSelectedItems,
  selectCampaignsUI,
} from "@services/campaign/campaignsUISlice";
import { RootState } from "@store";
import { useTranslate } from "@tolgee/react";
import React, {
  useCallback,
  useRef,
  useEffect,
  useState,
  useMemo,
} from "react";
import { useDispatch, useSelector } from "react-redux";

import { CampaignDisplay } from "../../../types/campaign-display.types";
import { CampaignCard } from "../common/CampaignCard";

interface CampaignsGridViewProps {
  data: CampaignDisplay[];
  isLoading: boolean;
  isFetching: boolean;
  paginationInfo: {
    currentPage: number;
    totalPages: number;
    pageSize: number;
    totalItems: number;
  };
  onLoadMore: (newOffset: number) => void;
  querySignature?: string; // To detect query changes
  onRefresh?: () => void;
}

export const CampaignsGridView: React.FC<CampaignsGridViewProps> = ({
  data,
  isLoading,
  isFetching,
  paginationInfo,
  onLoadMore,
  querySignature = "",
  onRefresh,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const dispatch = useDispatch();
  const uiState = useSelector((state: RootState) => selectCampaignsUI(state));
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  // Accumulated data for virtual scrolling
  const [accumulatedData, setAccumulatedData] = useState<CampaignDisplay[]>([]);
  const [loadedPages, setLoadedPages] = useState<Set<number>>(new Set());
  const lastQuerySignatureRef = useRef<string>("");
  const isInitialMountRef = useRef<boolean>(true);

  // Reset accumulated data when query changes (but not on initial mount).
  // The parent bumps `querySignature` with a refresh nonce after any mutation
  // that affects the list (bulk/single archive, delete, etc.), so this same
  // reset path also covers "reload everything" after those actions - no
  // separate reconciliation logic needed.
  useEffect(() => {
    if (isInitialMountRef.current) {
      isInitialMountRef.current = false;
      lastQuerySignatureRef.current = querySignature;
      return;
    }

    if (querySignature !== lastQuerySignatureRef.current) {
      // Query changed (filters/search/sort/refresh) - reset accumulated data
      setAccumulatedData([]);
      setLoadedPages(new Set());
      lastQuerySignatureRef.current = querySignature;
    }
  }, [querySignature]);

  // Accumulate data when new page arrives
  useEffect(() => {
    // Don't process if no data
    if (data.length === 0) {
      // Only clear if query changed and we're not loading
      if (
        !isLoading &&
        querySignature !== lastQuerySignatureRef.current &&
        lastQuerySignatureRef.current !== ""
      ) {
        setAccumulatedData([]);
        setLoadedPages(new Set());
      }
      return;
    }

    const currentPage = paginationInfo.currentPage;

    // Check if query signature changed (reset scenario - filters/search/sort/refresh changed)
    const queryChanged = querySignature !== lastQuerySignatureRef.current;

    if (queryChanged && lastQuerySignatureRef.current !== "") {
      // Query changed - replace all data with new data
      setAccumulatedData(data);
      setLoadedPages(new Set([currentPage]));
      lastQuerySignatureRef.current = querySignature;
    } else if (!loadedPages.has(currentPage)) {
      // Load more - append to accumulated data, avoiding duplicates
      setAccumulatedData((prev) => {
        const existingIds = new Set(prev.map((c) => c.id));
        const uniqueNewCampaigns = data.filter((c) => !existingIds.has(c.id));
        return [...prev, ...uniqueNewCampaigns];
      });
      setLoadedPages((prev) => {
        const newSet = new Set(prev);
        newSet.add(currentPage);
        return newSet;
      });
    }

    // Update query signature ref after processing (if it wasn't a query change)
    if (!queryChanged) {
      lastQuerySignatureRef.current = querySignature;
    }
  }, [
    data,
    loadedPages,
    paginationInfo.currentPage,
    querySignature,
    isLoading,
  ]);

  // Display data (accumulated for grid view)
  const displayData = useMemo(() => {
    return accumulatedData.length > 0 ? accumulatedData : data;
  }, [accumulatedData, data]);

  // Handlers
  const handleCardSelect = useCallback(
    (id: string | number) => {
      const idStr = typeof id === "number" ? id.toString() : id;
      dispatch(toggleItemSelection(idStr));
    },
    [dispatch],
  );

  // const handleSelectAll = useCallback(
  //   (selected: boolean) => {
  //     if (selected) {
  //       dispatch(setSelectedItems(displayData.map((row) => row.id)));
  //     } else {
  //       dispatch(setSelectedItems([]));
  //     }
  //   },
  //   [dispatch, displayData],
  // );

  // Calculate if there are more items to load
  const hasMore = paginationInfo.currentPage < paginationInfo.totalPages;
  // Get the highest loaded page number
  const currentLoadedPage =
    loadedPages.size > 0
      ? Math.max(...Array.from(loadedPages), paginationInfo.currentPage)
      : paginationInfo.currentPage;

  // Load more handler for infinite scroll
  const handleLoadMore = useCallback(() => {
    if (!isFetching && hasMore) {
      // Calculate next page based on current offset
      const currentPage = Math.floor(
        uiState.virtualScroll.offset / uiState.virtualScroll.limit,
      );
      const nextPage = currentPage + 1;
      const nextOffset = nextPage * uiState.virtualScroll.limit;
      onLoadMore(nextOffset);
    }
  }, [
    isFetching,
    hasMore,
    uiState.virtualScroll.offset,
    uiState.virtualScroll.limit,
    onLoadMore,
  ]);

  // Setup infinite scroll
  const infiniteScrollRef = useInfiniteScroll({
    hasMore,
    loading: isFetching,
    onLoadMore: handleLoadMore,
    threshold: INFINITE_SCROLL_THRESHOLD,
  });

  // Merge refs
  useEffect(() => {
    if (scrollContainerRef.current && infiniteScrollRef.current) {
      // Both refs point to the same element
      if (infiniteScrollRef.current !== scrollContainerRef.current) {
        // If they're different, we need to handle this
        // For now, we'll use the infinite scroll ref as primary
      }
    }
  }, [infiniteScrollRef]);

  return (
    <div
      id="campaigns-grid-container"
      ref={infiniteScrollRef}
      className="h-full overflow-y-auto overflow-x-hidden scrollbar-thin scrollbar-thumb-mw-neutral-300 scrollbar-track-transparent"
    >
      {isLoading && displayData.length === 0 ? (
        <div
          id="campaigns-grid-loading"
          className="flex justify-center items-center py-12"
        >
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-mw-primary-500"></div>
        </div>
      ) : displayData.length === 0 ? (
        <div id="campaigns-grid-empty" className="text-center py-12">
          <p className="text-mw-neutral-500 dark:text-mw-neutral-400">
            {tCampaigns("campaignsList.emptyMessage")}
          </p>
        </div>
      ) : (
        <>
          <div
            id="campaigns-grid"
            className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 p-2"
          >
            {displayData.map((campaign) => (
              <CampaignCard
                key={campaign.id}
                id={`campaign-card-${campaign.id}`}
                campaign={campaign}
                selected={uiState.selectedItems.includes(campaign.id)}
                onSelect={handleCardSelect}
                onRefresh={onRefresh}
              />
            ))}
          </div>
          {/* Load more indicator at bottom */}
          <LoadMore
            id="campaigns-load-more"
            currentPage={currentLoadedPage}
            totalPages={paginationInfo.totalPages}
            pageSize={paginationInfo.pageSize}
            totalItems={paginationInfo.totalItems}
            loading={isFetching}
          />
        </>
      )}
    </div>
  );
};
