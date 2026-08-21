import { useDropdownPortalSimple } from "@hooks/useDropdownPortal";
import { useTranslate } from "@tolgee/react";
import { clsx } from "clsx";
import { ChevronDown, ChevronUp, Loader2, AlertCircle, X } from "lucide-react";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

// Types
export interface DropdownOption {
  id: string | number;
  label: string;
  value: string | number;
  disabled?: boolean;
  description?: string;
  avatar?: string;
}

export interface RemoteDataResponse<T = DropdownOption> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
  numberOfElements: number;
}

export interface SearchParams {
  page?: number;
  size?: number;
  [key: string]: unknown;
}

export type RemoteDataFetcher<T = DropdownOption> = (
  params: SearchParams,
) => Promise<RemoteDataResponse<T>>;

export interface RemoteDropdownProps<T = DropdownOption> {
  fetcher: RemoteDataFetcher<T>;
  value?: T | T[] | null;
  placeholder?: string;
  disabled?: boolean;
  multiple?: boolean;
  pageSize?: number;
  onSelectionChange?: (selected: T | T[] | null) => void;
  onError?: (error: Error) => void;
  name?: string;
  id?: string;
  className?: string;
  maxHeight?: string;
  renderOption?: (option: T, isSelected: boolean) => React.ReactNode;
  renderEmpty?: () => React.ReactNode;
  renderError?: (error: Error, retry: () => void) => React.ReactNode;
  renderFooter?: (
    setIsOpen: React.Dispatch<React.SetStateAction<boolean>>,
  ) => React.ReactNode;
  searchable?: boolean;
  searchPlaceholder?: string;
  debounceMs?: number;
  clearable?: boolean;
  onClear?: () => void;
}

export function RemoteDropdown<T extends DropdownOption = DropdownOption>({
  fetcher,
  value,
  placeholder = "Select an option...",
  disabled = false,
  multiple = false,
  pageSize = 20,
  onSelectionChange,
  onError,
  name,
  id,
  className,
  maxHeight = "300px",
  renderOption,
  renderEmpty,
  renderError,
  renderFooter,
  searchable = false,
  searchPlaceholder = "Search...",
  debounceMs = 300,
  clearable,
  onClear,
}: RemoteDropdownProps<T>) {
  const { t } = useTranslate(["common"]);
  // State
  const [isOpen, setIsOpen] = useState(false);
  const [options, setOptions] = useState<T[]>([]);
  const [selectedOptions, setSelectedOptions] = useState<T | T[] | null>(
    value || null,
  );
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [hasNextPage, setHasNextPage] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");
  const [debouncedSearchTerm, setDebouncedSearchTerm] = useState("");
  const [hasInitialLoad, setHasInitialLoad] = useState(false);

  const containerRef = useRef<HTMLDivElement>(null);
  const abortControllerRef = useRef<AbortController | null>(null);

  const handleClose = useCallback(() => setIsOpen(false), []);

  const { contentRef, portalPosition, isPositionReady } =
    useDropdownPortalSimple({
      triggerRef: containerRef,
      isOpen,
      onClose: handleClose,
      maxHeight,
      minWidth: 0,
    });

  // Debounce search term
  useEffect(() => {
    const timeoutId = setTimeout(() => {
      setDebouncedSearchTerm(searchTerm);
    }, debounceMs);

    return () => clearTimeout(timeoutId);
  }, [searchTerm, debounceMs]);

  // Fetch data function
  const fetchData = useCallback(
    async (page: number, append: boolean = false, search?: string) => {
      // Cancel previous request
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }

      abortControllerRef.current = new AbortController();

      try {
        if (page === 0) {
          setLoading(true);
        } else {
          setLoadingMore(true);
        }
        setError(null);

        const params: SearchParams = {
          page,
          size: pageSize,
          ...(search && { search }),
        };

        const response = await fetcher(params);

        // Check if request was cancelled
        if (abortControllerRef.current?.signal.aborted) {
          return;
        }

        if (append) {
          setOptions((prev) => [...prev, ...response.content]);
        } else {
          setOptions(response.content);
        }

        setCurrentPage(response.number);
        setHasNextPage(!response.last);
        setLoading(false);
        setLoadingMore(false);
        setError(null);
      } catch (err) {
        if (abortControllerRef.current?.signal.aborted) {
          return;
        }

        const error =
          err instanceof Error ? err : new Error("An error occurred");
        setError(error);
        setLoading(false);
        setLoadingMore(false);
        onError?.(error);
      }
    },
    [fetcher, pageSize, onError],
  );

  // Handle search when debounced search term changes (only for searchable dropdowns after initial load)
  useEffect(() => {
    if (searchable && isOpen && hasInitialLoad) {
      setOptions([]);
      setCurrentPage(0);
      setHasNextPage(true);
      fetchData(0, false, debouncedSearchTerm);
      // Note: When search is cleared, we let the clearSearch function handle it
    }
  }, [debouncedSearchTerm, searchable, isOpen, hasInitialLoad, fetchData]);

  // Initial data fetch when dropdown opens
  useEffect(() => {
    if (isOpen && !hasInitialLoad && !loading) {
      fetchData(0, false).then(() => {
        setHasInitialLoad(true);
      });
    }
  }, [isOpen, hasInitialLoad, loading, fetchData]);

  // Load more data
  const loadMore = useCallback(() => {
    if (hasNextPage && !loading && !loadingMore) {
      fetchData(
        currentPage + 1,
        true,
        searchable ? debouncedSearchTerm : undefined,
      );
    }
  }, [
    hasNextPage,
    loading,
    loadingMore,
    currentPage,
    fetchData,
    searchable,
    debouncedSearchTerm,
  ]);

  // Retry function
  const retry = useCallback(() => {
    if (options.length === 0) {
      fetchData(0, false, searchable ? debouncedSearchTerm : undefined);
    } else {
      fetchData(
        currentPage + 1,
        true,
        searchable ? debouncedSearchTerm : undefined,
      );
    }
  }, [options.length, currentPage, fetchData, searchable, debouncedSearchTerm]);

  // Reset search state when dropdown closes
  useEffect(() => {
    if (!isOpen) {
      setSearchTerm("");
      setDebouncedSearchTerm("");
      setHasInitialLoad(false);
    }
  }, [isOpen]);

  // Update selected options when value prop changes
  useEffect(() => {
    setSelectedOptions(value || null);
  }, [value]);

  // Clear search function
  const clearSearch = useCallback(() => {
    setSearchTerm("");
    setDebouncedSearchTerm("");
    // Reset options and fetch all data
    setOptions([]);
    setCurrentPage(0);
    setHasNextPage(true);
    fetchData(0, false);
  }, [fetchData]);

  const handleOptionSelect = useCallback(
    (option: T) => {
      if (multiple) {
        const current = (selectedOptions as T[]) || [];
        const isSelected = current.some((item) => item.id === option.id);
        const newSelection = isSelected
          ? current.filter((item) => item.id !== option.id)
          : [...current, option];
        const next = newSelection.length > 0 ? newSelection : null;
        setSelectedOptions(next);
        onSelectionChange?.(next);
      } else {
        setSelectedOptions(option);
        onSelectionChange?.(option);
        setIsOpen(false);
      }
    },
    [multiple, selectedOptions, onSelectionChange],
  );

  const isSelected = useCallback(
    (option: T): boolean => {
      if (!selectedOptions) return false;
      if (multiple) {
        return (
          (selectedOptions as T[])?.some((s) => s.id === option.id) ?? false
        );
      }
      return (selectedOptions as T)?.id === option.id;
    },
    [multiple, selectedOptions],
  );

  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const { scrollTop, scrollHeight, clientHeight } = e.currentTarget;
      if (
        scrollHeight - scrollTop - clientHeight < 50 &&
        hasNextPage &&
        !loadingMore
      ) {
        loadMore();
      }
    },
    [hasNextPage, loadingMore, loadMore],
  );

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, []);

  // Render selection display
  const renderSelectionDisplay = () => {
    if (!selectedOptions) {
      return (
        <span className="text-mw-neutral-500 dark:text-mw-neutral-400">
          {placeholder}
        </span>
      );
    }

    if (multiple) {
      const options = selectedOptions as T[];
      if (options.length === 1) {
        return options[0].label;
      }
      return `${options.length} items selected`;
    } else {
      const option = selectedOptions as T;
      return (
        <div className="flex items-center gap-2">
          {option.avatar && (
            <img src={option.avatar} alt="" className="w-5 h-5 rounded-full" />
          )}
          <span>{option.label}</span>
        </div>
      );
    }
  };

  // Render option item
  const renderOptionItem = (option: T) => {
    const selected = isSelected(option);

    if (renderOption) {
      return renderOption(option, selected);
    }

    return (
      <div
        className={clsx(
          "flex items-center gap-3 px-3 py-2 cursor-pointer",
          "hover:bg-mw-neutral-50",
          selected && "bg-mw-primary-50 dark:bg-mw-primary-900/30",
          option.disabled && "opacity-50 cursor-not-allowed",
        )}
      >
        {/* Avatar */}
        {option.avatar && (
          <img src={option.avatar} alt="" className="w-6 h-6 rounded-full" />
        )}

        {/* Content */}
        <div className="flex-1 min-w-0">
          <div className="font-medium text-mw-neutral-900 dark:text-mw-neutral-100">
            {option.label}
          </div>
          {option.description && (
            <p className="text-sm text-mw-neutral-500 dark:text-mw-neutral-400 truncate">
              {option.description}
            </p>
          )}
        </div>

        {/* Selection indicator */}
        {selected && <div className="text-mw-primary-600">✓</div>}
      </div>
    );
  };

  const handleClear = (e: React.MouseEvent) => {
    e.stopPropagation();
    onClear?.();
  };

  return (
    <div ref={containerRef} className={clsx("relative", className)}>
      {/* Hidden input for form integration */}
      {name && (
        <input
          type="hidden"
          name={name}
          value={
            selectedOptions
              ? multiple
                ? (selectedOptions as T[]).map((opt) => opt.value).join(",")
                : (selectedOptions as T).value
              : ""
          }
        />
      )}

      {/* Trigger Button */}
      <button
        type="button"
        onClick={() => !disabled && setIsOpen(!isOpen)}
        disabled={disabled}
        className={clsx(
          "relative w-full h-10 min-h-10 flex items-center justify-between gap-2 px-3 py-2",
          "border rounded-md text-sm font-medium text-left",
          "focus:outline-none focus:ring-1 focus:ring-mw-primary-500 focus:border-transparent",
          "transition-colors duration-150",
          disabled
            ? "bg-mw-neutral-50 text-mw-neutral-400 cursor-not-allowed border-mw-neutral-100"
            : "bg-white dark:bg-mw-neutral-800 text-mw-neutral-500 dark:text-mw-neutral-300 border-mw-neutral-100 dark:border-mw-neutral-600",
          isOpen && "ring-1 ring-mw-primary-500 border-mw-primary-500",
        )}
        aria-expanded={isOpen}
        aria-haspopup="listbox"
        id={id}
      >
        <div className="flex-1 min-w-0">{renderSelectionDisplay()}</div>

        <div className="flex items-center gap-1 ml-2">
          {clearable && value && onClear && (
            <div
              id={`${id}-clear`}
              onClick={handleClear}
              className="p-1 hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-600 rounded-sm transition-colors"
              title={t("aria.clearSelection")}
            >
              <X className="w-3 h-3" />
            </div>
          )}
          {/* Dropdown arrow */}
          {isOpen ? (
            <ChevronUp className="h-4 w-4 text-mw-neutral-400" />
          ) : (
            <ChevronDown className="h-4 w-4 text-mw-neutral-400" />
          )}
        </div>
      </button>

      {/* Dropdown Content - Portal */}
      {isOpen &&
        isPositionReady &&
        portalPosition &&
        createPortal(
          <>
            {/* Overlay */}
            <div
              className="dropdown-overlay"
              onClick={() => setIsOpen(false)}
              aria-hidden="true"
            />
            {/* Dropdown Content */}
            <div
              ref={contentRef}
              className={clsx(
                "fixed dropdown-content w-full bg-white dark:bg-mw-neutral-800",
                "border border-mw-neutral-100 dark:border-mw-neutral-700 rounded-md shadow-lg",
                "overflow-hidden",
              )}
              style={{
                top: `${portalPosition.top}px`,
                left: `${portalPosition.left}px`,
                width: `${portalPosition.width}px`,
                minWidth: `${portalPosition.width}px`,
              }}
            >
              {/* Search Input */}
              {searchable && (
                <div className="p-2">
                  <div className="relative">
                    <input
                      type="text"
                      value={searchTerm}
                      onChange={(e) => setSearchTerm(e.target.value)}
                      placeholder={searchPlaceholder}
                      className={clsx(
                        "w-full px-3 py-2 pr-8 text-sm",
                        "border border-mw-neutral-300 dark:border-mw-neutral-600 rounded-md",
                        "bg-white dark:bg-mw-neutral-800",
                        "text-mw-neutral-900 dark:text-mw-neutral-100",
                        "placeholder-mw-neutral-500 dark:placeholder-mw-neutral-400",
                        "focus:outline-none focus:ring-1 focus:ring-mw-primary-500 focus:border-transparent",
                      )}
                      autoFocus
                    />
                    {searchTerm && (
                      <button
                        type="button"
                        onClick={clearSearch}
                        className={clsx(
                          "absolute right-2 top-1/2 -translate-y-1/2",
                          "w-5 h-5 flex items-center justify-center",
                          "text-mw-neutral-400",
                          "rounded-full hover:bg-mw-neutral-100",
                          "transition-colors duration-150",
                        )}
                        aria-label={t("aria.clearSearch")}
                      >
                        <X className="w-3 h-3" />
                      </button>
                    )}
                  </div>
                </div>
              )}

              <div
                className="overflow-y-auto scrollbar-thin"
                style={{ maxHeight }}
                onScroll={handleScroll}
              >
                {/* Loading State */}
                {loading && (
                  <div className="flex items-center justify-center py-8">
                    <Loader2 className="h-5 w-5 animate-spin text-mw-neutral-500" />
                    <span className="ml-2 text-sm text-mw-neutral-500">
                      Loading...
                    </span>
                  </div>
                )}

                {/* Error State */}
                {error && !loading && (
                  <div className="p-4">
                    {renderError ? (
                      renderError(error, retry)
                    ) : (
                      <div className="flex flex-col items-center py-4 text-center">
                        <AlertCircle className="h-8 w-8 text-mw-error-500 mb-2" />
                        <p className="text-sm text-mw-neutral-600 mb-3">
                          {error.message}
                        </p>
                        <button
                          onClick={retry}
                          className="px-3 py-1 text-sm bg-mw-primary-600 text-white rounded hover:bg-mw-primary-700"
                        >
                          Try Again
                        </button>
                      </div>
                    )}
                  </div>
                )}

                {/* Empty State */}
                {options.length === 0 && !loading && !error && (
                  <div className="p-4">
                    {renderEmpty ? (
                      renderEmpty()
                    ) : (
                      <div className="text-center py-4">
                        <p className="text-sm text-mw-neutral-500">
                          No options available
                        </p>
                      </div>
                    )}
                  </div>
                )}

                {/* Options List */}
                {options.length > 0 && !loading && (
                  <div>
                    {options.map((option) => (
                      <div
                        key={option.id}
                        onClick={() =>
                          !option.disabled && handleOptionSelect(option)
                        }
                        role="option"
                        aria-selected={isSelected(option)}
                        className="hover:bg-mw-neutral-50 cursor-pointer"
                      >
                        {renderOptionItem(option)}
                      </div>
                    ))}

                    {/* Load More Indicator */}
                    {loadingMore && (
                      <div className="flex items-center justify-center py-3 border-t border-mw-neutral-100 dark:border-mw-neutral-700">
                        <Loader2 className="h-4 w-4 animate-spin text-mw-neutral-500" />
                        <span className="ml-2 text-sm text-mw-neutral-500">
                          Loading more...
                        </span>
                      </div>
                    )}

                    {/* End of results indicator */}
                    {!hasNextPage && options.length > pageSize && (
                      <div className="text-center py-2 text-xs text-mw-neutral-400 border-t border-mw-neutral-100 dark:border-mw-neutral-700">
                        All items loaded
                      </div>
                    )}
                  </div>
                )}
              </div>

              {/* Footer - Outside scrollable area */}
              {renderFooter && (
                <div className="border-t border-mw-neutral-200 dark:border-mw-neutral-700 bg-white dark:bg-mw-neutral-800">
                  {renderFooter(setIsOpen)}
                </div>
              )}
            </div>
          </>,
          document.body,
        )}
    </div>
  );
}

export default RemoteDropdown;
