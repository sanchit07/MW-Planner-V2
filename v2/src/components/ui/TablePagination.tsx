import { useTranslate } from "@tolgee/react";
import {
  ChevronFirst,
  ChevronLast,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import React from "react";

import { Button } from "./Button";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownTrigger,
} from "./Dropdown";
import { Label } from "./Label";

interface TablePaginationProps {
  currentPage: number;
  totalPages: number;
  pageSize: number;
  totalItems: number;
  onPageChange: (page: number) => void;
  onPageSizeChange?: (size: number) => void;
  id?: string;
}

export const TablePagination: React.FC<TablePaginationProps> = ({
  currentPage,
  totalPages,
  pageSize,
  totalItems,
  onPageChange,
  onPageSizeChange,
  id,
}) => {
  const { t } = useTranslate("common");
  const startItem = (currentPage - 1) * pageSize + 1;
  const endItem = Math.min(currentPage * pageSize, totalItems);

  const pageSizeOptions = [10, 25, 50, 100];
  const paginationId = id || "table-pagination";

  return (
    <div
      id={paginationId}
      className="bg-white dark:bg-mw-neutral-900 flex items-center justify-between"
    >
      <div className="flex-1 flex justify-between sm:hidden">
        <button
          onClick={() => onPageChange(currentPage - 1)}
          disabled={currentPage === 1}
          className="relative inline-flex items-center px-4 py-2 border border-mw-neutral-100 dark:border-mw-neutral-600 text-sm font-medium rounded-md text-mw-neutral-700 dark:text-mw-neutral-300 bg-white dark:bg-mw-neutral-800 hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {t("pagination.previous")}
        </button>
        <button
          onClick={() => onPageChange(currentPage + 1)}
          disabled={currentPage === totalPages}
          className="ml-3 relative inline-flex items-center px-4 py-2 border border-mw-neutral-100 dark:border-mw-neutral-600 text-sm font-medium rounded-md text-mw-neutral-700 dark:text-mw-neutral-300 bg-white dark:bg-mw-neutral-800 hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {t("pagination.next")}
        </button>
      </div>
      <div className="hidden sm:flex-1 sm:flex sm:items-center sm:justify-between gap-12">
        <div className="flex items-center gap-12">
          {onPageSizeChange && (
            <div className="flex items-center gap-3">
              <Label>{t("pagination.pageItems")}</Label>
              <Dropdown>
                <DropdownTrigger>
                  <span>{pageSize}</span>
                </DropdownTrigger>
                <DropdownContent align="left">
                  {pageSizeOptions.map((value) => (
                    <DropdownItem
                      key={value}
                      onClick={() => onPageSizeChange(value)}
                    >
                      <div className="text-sm">{value}</div>
                    </DropdownItem>
                  ))}
                </DropdownContent>
              </Dropdown>
            </div>
          )}
          <p className="text-sm text-mw-neutral-700 dark:text-mw-neutral-300">
            {t("pagination.itemRange", {
              start: startItem,
              end: endItem,
              total: totalItems,
            })}
          </p>
        </div>
        <div>
          <nav className="inline-flex justify-start items-center gap-1">
            {/* First page button */}
            <Button
              variant="outline"
              size="iconMd"
              disabled={currentPage === 1}
              onClick={() => onPageChange(1)}
            >
              <ChevronFirst className="w-4 h-4 text-primary" />
            </Button>

            {/* Previous page button */}
            <Button
              variant="outline"
              size="iconMd"
              disabled={currentPage === 1}
              onClick={() => onPageChange(currentPage - 1)}
            >
              <ChevronLeft className="w-4 h-4 text-primary" />
            </Button>

            {/* Page info */}
            <div className="p-2 justify-start text-black text-xs font-medium leading-4">
              {t("pagination.pageInfo", {
                current: currentPage,
                total: totalPages,
              })}
            </div>

            {/* Next page button */}
            <Button
              variant="outline"
              size="iconMd"
              disabled={currentPage === totalPages}
              onClick={() => onPageChange(currentPage + 1)}
            >
              <ChevronRight className="w-4 h-4 text-primary" />
            </Button>

            {/* Last page button */}
            <Button
              variant="outline"
              size="iconMd"
              disabled={currentPage === totalPages}
              onClick={() => onPageChange(totalPages)}
            >
              <ChevronLast className="w-4 h-4 text-primary" />
            </Button>
          </nav>
        </div>
      </div>
    </div>
  );
};
