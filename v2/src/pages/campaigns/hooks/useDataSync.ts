import { DEFAULT_SORT, DEFAULT_PAGE_SIZE } from "@constants/campaign.constants";
import { useGetCampaignsQuery } from "@services/campaign/campaignSlice";
import {
  setPaginationPage,
  setPaginationPageSize,
  setVirtualScrollOffset,
  selectCampaignsUI,
} from "@services/campaign/campaignsUISlice";
import { useAppSelector } from "@store";
import { transformCampaignData } from "@utils/campaign.utils";
import { toAPIDateString } from "@utils/dateUtils";
import { useMemo, useCallback, useEffect, useRef } from "react";
import { useDispatch, useSelector } from "react-redux";

import { CampaignDisplay } from "../../../types/campaign-display.types";
import { GetCampaignsQueryParams } from "../../../types/campaign.types";

/**
 * Custom hook that synchronizes RTK Query with Redux slice state
 * Handles data fetching, parameter building, and state synchronization
 */
export const useDataSync = () => {
  const dispatch = useDispatch();
  const uiState = useSelector(selectCampaignsUI);
  const lastQuerySignatureRef = useRef<string>("");
  const previousPageSizeRef = useRef<number>(uiState.pagination.pageSize);
  const user = useAppSelector((state) => state.profile.profile);
  const companyId = user?.activeCompanyId || user?.current_company?.id || "";
  // Build query parameters from UI state
  const queryParams = useMemo((): GetCampaignsQueryParams => {
    const params: GetCampaignsQueryParams = {
      // Pagination - use page for list view, calculate from offset for grid view
      companyId,
      page:
        uiState.viewType === "list"
          ? Math.max(0, uiState.pagination.page - 1) // API uses 0-based, ensure non-negative
          : Math.max(
              0,
              Math.floor(
                uiState.virtualScroll.offset / uiState.virtualScroll.limit,
              ),
            ),
      size:
        uiState.viewType === "list"
          ? uiState.pagination.pageSize
          : uiState.virtualScroll.limit,
      // Sorting
      sortBy:
        uiState.sortState.length > 0
          ? uiState.sortState[0].key
          : DEFAULT_SORT.sortBy,
      sortDir:
        uiState.sortState.length > 0
          ? uiState.sortState[0].direction
          : DEFAULT_SORT.sortDir,
      // Search - only include if not empty
      ...(uiState.searchQuery &&
        uiState.searchQuery.trim().length >= 3 && {
          nameContains: uiState.searchQuery.trim(),
        }),
      // Filters
      ...(uiState.filters.status.length > 0 && {
        statuses: uiState.filters.status.join(","),
      }),
      ...(uiState.filters.campaignGoal.length > 0 && {
        goalTypes: uiState.filters.campaignGoal.join(","),
      }),
      ...(uiState.filters.period?.from &&
        uiState.filters.period?.to && {
          startDateFrom: toAPIDateString(uiState.filters.period.from),
          startDateTo: toAPIDateString(uiState.filters.period.to),
        }),
      ...(uiState.filters.userName.length > 0 && {
        userIds: uiState.filters.userName.join(","),
      }),
    };

    return params;
  }, [
    companyId,
    uiState.viewType,
    uiState.pagination.page,
    uiState.pagination.pageSize,
    uiState.virtualScroll.offset,
    uiState.virtualScroll.limit,
    uiState.sortState,
    uiState.searchQuery,
    uiState.filters,
  ]);

  // Create query signature for change detection (excludes pagination for grid view)
  const querySignature = useMemo(() => {
    const { ...filterParams } = queryParams;
    return JSON.stringify(filterParams);
  }, [uiState.sortState, uiState.searchQuery, uiState.filters]);

  // Use RTK Query hook - automatically handles caching and request cancellation
  const {
    data: campaignsResponse,
    isLoading,
    isFetching,
    error,
    refetch,
  } = useGetCampaignsQuery(queryParams, {
    // Skip if query hasn't changed (RTK Query handles this automatically)
    skip: false,
  });

  // Transform data
  const transformedData = useMemo((): CampaignDisplay[] => {
    if (!campaignsResponse?.data?.content) {
      return [];
    }
    return campaignsResponse.data.content.map(transformCampaignData);
  }, [campaignsResponse]);

  // Pagination info
  const paginationInfo = useMemo(() => {
    if (!campaignsResponse?.data) {
      return {
        totalItems: 0,
        totalPages: 0,
        currentPage: uiState.viewType === "list" ? uiState.pagination.page : 1,
        pageSize:
          uiState.viewType === "list"
            ? uiState.pagination.pageSize
            : uiState.virtualScroll.limit,
      };
    }

    return {
      totalItems: campaignsResponse.data.totalElements,
      totalPages: campaignsResponse.data.totalPages,
      currentPage: (campaignsResponse.data.number || 0) + 1, // Convert from 0-based to 1-based
      pageSize: campaignsResponse.data.size,
    };
  }, [
    campaignsResponse,
    uiState.viewType,
    uiState.pagination,
    uiState.virtualScroll.limit,
  ]);

  // Reset pagination to page 1 when the active company changes
  const previousCompanyIdRef = useRef<string>(companyId);
  useEffect(() => {
    if (
      previousCompanyIdRef.current &&
      previousCompanyIdRef.current !== companyId
    ) {
      dispatch(setPaginationPage(1));
    }
    previousCompanyIdRef.current = companyId;
  }, [companyId, dispatch]);

  // Note: Pagination reset is handled in the slice reducers (setSortState, setSearchQuery, setFilters)
  // This effect just tracks query signature for debugging/monitoring
  useEffect(() => {
    lastQuerySignatureRef.current = querySignature;
  }, [querySignature]);

  // Reset page size to default if API fails after page size change
  useEffect(() => {
    const currentPageSize = uiState.pagination.pageSize;
    const previousPageSize = previousPageSizeRef.current;

    // If page size changed and API failed, reset to default
    if (
      uiState.viewType === "list" &&
      error &&
      currentPageSize !== previousPageSize &&
      currentPageSize !== DEFAULT_PAGE_SIZE
    ) {
      dispatch(setPaginationPageSize(DEFAULT_PAGE_SIZE));
    }

    // Update previous page size on successful response
    if (!error && campaignsResponse) {
      previousPageSizeRef.current = currentPageSize;
    }
  }, [
    error,
    campaignsResponse,
    uiState.pagination.pageSize,
    uiState.viewType,
    dispatch,
  ]);

  // Handlers
  const handlePageChange = useCallback(
    (page: number) => {
      dispatch(setPaginationPage(page));
    },
    [dispatch],
  );

  const handlePageSizeChange = useCallback(
    (size: number) => {
      dispatch(setPaginationPageSize(size));
    },
    [dispatch],
  );

  const handleVirtualScrollLoadMore = useCallback(
    (newOffset: number) => {
      dispatch(setVirtualScrollOffset(newOffset));
    },
    [dispatch],
  );

  // Full query signature including pagination (for debugging)
  const fullQuerySignature = useMemo(
    () => JSON.stringify(queryParams),
    [queryParams],
  );

  return {
    // Data
    data: transformedData,
    // Loading states
    isLoading,
    isFetching,
    error,
    // Pagination info
    paginationInfo,
    // Actions
    refetch,
    handlePageChange,
    handlePageSizeChange,
    handleVirtualScrollLoadMore,
    // Query signature for change detection (excludes pagination)
    querySignature,
    // Full query signature (includes pagination)
    fullQuerySignature,
  };
};
