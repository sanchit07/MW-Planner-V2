import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import { Input } from "@components/ui/Input";
import { useSelectedInventoryList } from "@hooks/useSelectedInventoryList";
import { useTranslate } from "@tolgee/react";
import { clsx } from "clsx";
import { Search } from "lucide-react";
import {
  ReactNode,
  useEffect,
  useImperativeHandle,
  useMemo,
  useState,
  forwardRef,
} from "react";
import { InventoryItem } from "src/types/inventory.types";

import InventorySkeleton from "../../pages/campaigns/inventory/InventorySkeleton";

export interface SelectedInventoryListContainerProps {
  campaignId: string | undefined;
  enabled?: boolean;
  pageSize?: number;
  sortBy?: string;
  sortDir?: "asc" | "desc";
  // Header configuration
  showHeader?: boolean;
  headerTitle?: string;
  headerSubtitle?: string;
  headerIcon?: ReactNode;
  headerActions?: ReactNode;
  headerClassName?: string;
  // Simple header (h4 style, not CardHeader)
  showSimpleHeader?: boolean;
  simpleHeaderContent?: ReactNode;
  simpleHeaderClassName?: string;
  // Search configuration
  showSearch?: boolean;
  searchPlaceholder?: string;
  searchValue?: string;
  onSearchChange?: (value: string) => void;
  searchBeforeContent?: boolean; // true = before CardContent, false = inside CardContent
  searchClassName?: string;
  // Count/Info display
  showCount?: boolean;
  countLabel?: (count: number) => string;
  countClassName?: string;
  countBeforeContent?: boolean; // true = before CardContent, false = inside CardContent
  contentClassName?: string;
  listClassName?: string;
  // Custom content before scrollable list (e.g., dropdowns, checkboxes)
  contentBeforeList?: ReactNode;
  // Item rendering
  renderItem: (item: InventoryItem, index: number) => ReactNode;
  // Filter function
  filterItems?: (items: InventoryItem[], query: string) => InventoryItem[];
  // Loading configuration
  skeletonCount?: number;
  showLoadingMore?: boolean;
  loadingMoreText?: string;
  // Empty state
  emptyMessage?: string;
  emptySubMessage?: string | ((query: string) => string);
  // Callbacks
  onInitialLoad?: (items: InventoryItem[]) => void;
  onLoadMore?: (items: InventoryItem[]) => void;
  // Fires when the total count of selected inventories changes
  onTotalElementsChange?: (total: number) => void;
  // Container wrapper
  containerClassName?: string;
  // Scroll container - can be CardContent itself or a div inside
  useNestedScrollContainer?: boolean; // true = div inside CardContent, false = CardContent itself
  nestedScrollClassName?: string;
  countClasses?: string;
}

export interface SelectedInventoryListContainerRef {
  refetch: () => Promise<void>;
}

/**
 * Reusable container component for displaying selected inventory lists
 * Supports various UI patterns while maintaining exact CSS and positioning
 */
export const SelectedInventoryListContainer = forwardRef<
  SelectedInventoryListContainerRef,
  SelectedInventoryListContainerProps
>(
  (
    {
      campaignId,
      enabled = true,
      pageSize = 10,
      sortBy = "name",
      sortDir = "asc",
      showHeader = true,
      headerTitle,
      headerSubtitle,
      headerIcon,
      headerActions,
      headerClassName,
      showSearch = false,
      searchPlaceholder,
      searchValue: controlledSearchValue,
      onSearchChange,
      searchBeforeContent = true,
      searchClassName,
      showCount = false,
      countLabel,
      countClassName,
      countBeforeContent = true,
      contentClassName,
      listClassName,
      contentBeforeList,
      renderItem,
      filterItems,
      skeletonCount = 5,
      showLoadingMore = true,
      loadingMoreText,
      emptyMessage,
      emptySubMessage,
      onInitialLoad,
      onLoadMore,
      onTotalElementsChange,
      containerClassName,
      useNestedScrollContainer = false,
      nestedScrollClassName,
      showSimpleHeader = false,
      simpleHeaderContent,
      simpleHeaderClassName,
      countClasses,
    },
    ref,
  ) => {
    const { t: tCampaigns } = useTranslate(["campaigns"]);
    // Internal search state if not controlled
    const [internalSearchQuery, setInternalSearchQuery] = useState("");
    const searchQuery =
      controlledSearchValue !== undefined
        ? controlledSearchValue
        : internalSearchQuery;

    const handleSearchChange = (value: string) => {
      if (onSearchChange) {
        onSearchChange(value);
      } else {
        setInternalSearchQuery(value);
      }
    };

    // Use the reusable hook for data fetching
    const {
      selectedItems,
      isLoading,
      isLoadingMore,
      totalElements,
      scrollContainerRef,
      refetch,
    } = useSelectedInventoryList({
      campaignId,
      enabled,
      pageSize,
      sortBy,
      sortDir,
      onInitialLoad,
      onLoadMore,
    });

    // Expose refetch function to parent components via ref
    useImperativeHandle(ref, () => ({
      refetch,
    }));

    // Notify parent when the total count changes
    useEffect(() => {
      onTotalElementsChange?.(totalElements);
    }, [totalElements, onTotalElementsChange]);

    // Filter items by search query
    const filteredItems = useMemo(() => {
      if (!searchQuery && !filterItems) {
        return selectedItems;
      }

      if (filterItems) {
        return filterItems(selectedItems, searchQuery);
      }

      // Default filter: name and address
      const query = searchQuery.toLowerCase();
      return selectedItems.filter(
        (item) =>
          item.detail.name?.toLowerCase().includes(query) ||
          item.location.location.address?.toLowerCase().includes(query),
      );
    }, [selectedItems, searchQuery, filterItems]);

    // Default count label
    const defaultCountLabel = (count: number) =>
      `${count} ${count === 1 ? tCampaigns("selectedInventoryListContainer.inventory") : tCampaigns("selectedInventoryListContainer.inventories")}`;

    const searchInput = showSearch ? (
      <div className={clsx("relative", searchClassName)}>
        <Input
          type="text"
          placeholder={searchPlaceholder}
          value={searchQuery}
          onChange={(e) => handleSearchChange(e.target.value)}
          className="pr-8"
        />
        <Search className="absolute right-2 top-1/2 transform -translate-y-1/2 w-4 h-4 text-gray-400 pointer-events-none" />
      </div>
    ) : null;

    const countDisplay = showCount ? (
      <div className={countClassName}>
        <p className="text-sm font-medium text-mw-neutral-900">
          {countLabel
            ? countLabel(totalElements)
            : defaultCountLabel(totalElements)}
        </p>
      </div>
    ) : null;

    const listContent = (
      <>
        {/* Skeleton loader for initial loading */}
        {isLoading && <InventorySkeleton count={skeletonCount} />}

        {/* Inventory list */}
        {!isLoading && (
          <>
            {filteredItems.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-64 text-center">
                <p className="text-sm font-medium text-mw-neutral-600">
                  {emptyMessage ||
                    tCampaigns(
                      "selectedInventoryListContainer.noInventoriesFound",
                    )}
                </p>
                {emptySubMessage && (
                  <p className="text-xs text-mw-neutral-500 mt-1">
                    {typeof emptySubMessage === "function"
                      ? emptySubMessage(searchQuery)
                      : emptySubMessage}
                  </p>
                )}
              </div>
            ) : (
              <>
                {filteredItems.map((item, index) => renderItem(item, index))}

                {/* Loading more indicator */}
                {showLoadingMore && isLoadingMore && (
                  <div className="flex justify-center py-4">
                    <p className="text-sm text-mw-neutral-500">
                      {loadingMoreText ||
                        tCampaigns(
                          "selectedInventoryListContainer.loadingMore",
                        )}
                    </p>
                  </div>
                )}
              </>
            )}
          </>
        )}
      </>
    );

    return (
      <Card className={containerClassName}>
        {/* Simple Header (h4 style) */}
        {showSimpleHeader && (
          <div className={clsx("p-4", simpleHeaderClassName)}>
            {simpleHeaderContent || (
              <div className="inline-flex justify-start items-center gap-2">
                <h4 className="font-medium text-sm">{headerTitle}</h4>
              </div>
            )}
          </div>
        )}

        {/* CardHeader */}
        {showHeader && !showSimpleHeader && (
          <CardHeader className={headerClassName}>
            <div className="flex items-center justify-between">
              {(headerIcon || headerTitle || headerSubtitle) && (
                <div className="flex items-center gap-2">
                  {headerIcon}
                  {(headerTitle || headerSubtitle) && (
                    <CardTitle className="flex flex-col">
                      {headerTitle && <span>{headerTitle}</span>}
                      {headerSubtitle && (
                        <span className="text-sm text-mw-neutral-500">
                          {headerSubtitle}
                        </span>
                      )}
                    </CardTitle>
                  )}
                </div>
              )}
              {headerActions && <div>{headerActions}</div>}
            </div>
          </CardHeader>
        )}

        {/* Search before CardContent */}
        {showSearch && searchBeforeContent && (
          <div className={clsx("p-4", searchClassName)}>{searchInput}</div>
        )}

        {/* Count before CardContent */}
        {showCount && countBeforeContent && (
          <div className={clsx("p-4", countClasses)}>{countDisplay}</div>
        )}

        {/* List Content */}
        <CardContent
          ref={useNestedScrollContainer ? undefined : scrollContainerRef}
          className={clsx(
            contentClassName,
            !useNestedScrollContainer &&
              clsx(listClassName, "overflow-y-auto scrollbar-thin"),
          )}
        >
          {/* Search inside CardContent */}
          {showSearch && !searchBeforeContent && (
            <div className={clsx("space-y-3 p-4", searchClassName)}>
              {searchInput}
            </div>
          )}

          {/* Count inside CardContent */}
          {showCount && !countBeforeContent && (
            <div className={clsx("p-4", countClassName)}>{countDisplay}</div>
          )}

          {/* Custom content before list (dropdowns, checkboxes, etc.) */}
          {contentBeforeList}

          {/* Scrollable list container */}
          {useNestedScrollContainer ? (
            <div
              ref={scrollContainerRef}
              className={clsx(
                nestedScrollClassName,
                listClassName,
                "overflow-y-auto scrollbar-thin",
              )}
            >
              {listContent}
            </div>
          ) : (
            listContent
          )}
        </CardContent>
      </Card>
    );
  },
);

SelectedInventoryListContainer.displayName = "SelectedInventoryListContainer";
