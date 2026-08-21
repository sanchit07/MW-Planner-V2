import { clsx } from "clsx";
import { Loader2 } from "lucide-react";
import React from "react";

interface LoadMoreProps {
  currentPage: number;
  totalPages: number;
  pageSize: number;
  totalItems: number;
  loading?: boolean;
  className?: string;
  id?: string;
}

export const LoadMore: React.FC<LoadMoreProps> = ({
  currentPage,
  totalPages,
  pageSize,
  totalItems,
  loading = false,
  className,
  id,
}) => {
  const hasMore = currentPage < totalPages;
  const currentlyShowing = currentPage * pageSize;

  const loadMoreId = id || "load-more";
  if (!hasMore && !loading) {
    return (
      <div id={loadMoreId} className={clsx("text-center py-6", className)}>
        <p className="text-sm text-mw-neutral-500 dark:text-mw-neutral-400">
          Showing all {totalItems} results
        </p>
      </div>
    );
  }

  if (loading) {
    return (
      <div id={loadMoreId} className={clsx("text-center py-6", className)}>
        <div className="flex justify-center items-center gap-2">
          <Loader2 className="h-5 w-5 animate-spin text-mw-primary-500" />
          <p className="text-sm text-mw-neutral-500 dark:text-mw-neutral-400">
            Loading more results...
          </p>
        </div>
      </div>
    );
  }

  return (
    <div id={loadMoreId} className={clsx("text-center py-6", className)}>
      <p className="text-sm text-mw-neutral-500 dark:text-mw-neutral-400">
        Showing {currentlyShowing} of {totalItems} results
      </p>
    </div>
  );
};

export default LoadMore;
