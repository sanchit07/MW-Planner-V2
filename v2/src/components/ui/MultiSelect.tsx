import { useDropdownPortalSimple } from "@hooks/useDropdownPortal";
import { useTranslate } from "@tolgee/react";
import clsx from "clsx";
import {
  ChevronDown,
  ChevronRight,
  Plus,
  Search,
  X,
  XCircle,
} from "lucide-react";
import React, {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useState,
  useRef,
} from "react";
import { createPortal } from "react-dom";

import { Badge } from "./Badge";
import { Checkbox } from "./Checkbox";
import { computeVisibleChipCount } from "./chipOverflow.utils";
import { DropdownSeparator } from "./Dropdown";
import { Input } from "./Input";
import { Tooltip } from "./Tooltip";

export interface TreeNode {
  id?: string;
  label: string;
  value: string;
  children?: TreeNode[];
  disabled?: boolean;
  description?: string;
}

export interface MultiSelectProps {
  options: TreeNode[];
  value: string[];
  onChange: (fieldValues: string[]) => void;
  placeholder?: string;
  maxVisibleChips?: number;
  responsiveChips?: boolean; // Fit as many chips as the width allows, rest go into "+N" (overrides maxVisibleChips)
  disabled?: boolean;
  className?: string;
  searchable?: boolean;
  clearable?: boolean;
  closeOnSelect?: boolean;
  maxHeight?: string;
  expandable?: boolean; // Configurable expandable/collapsible behavior
  showSelectAll?: boolean; // Show "Select All" option
  selectAllLabel?: string; // Custom label for "Select All" option
  maxSelections?: number;
  noOptionFoundLabel?: string;
  showAddOptionIfNoOptionFound?: boolean;
  renderFooter?: (
    setIsOpen: React.Dispatch<React.SetStateAction<boolean>>,
  ) => React.ReactNode;
  onSearchChange?: (searchTerm: string) => void;
  id?: string;
  showPlaceHolderWithSearchOption?: string;
  showPlaceHolderWithSearchOptionWithMaxSelectionCount?: boolean;
  onBlur?: (fieldValues: string[]) => void; // Callback triggered when the multiselect loses focus
  onRemoteLoad?: () => void; // Fired when dropdown opens so parent can load options from API
  onRemoteSearch?: (searchTerm: string) => void; // Fired on search input change so parent can call search API (when set, client-side filtering is skipped)
  onRemoteLoadMore?: () => void; // Fired when user scrolls near bottom of options list so parent can load next page
}

interface ChipProps {
  label: string;
  onRemove: () => void;
  disabled?: boolean;
}

const Chip: React.FC<ChipProps> = ({ label, onRemove, disabled }) => (
  <Tooltip content={label}>
    <span className="inline-flex items-center gap-1 px-2 py-1 text-sm rounded-md border border-mw-neutral-100">
      <span className="truncate w-16">{label}</span>
      {!disabled && (
        <button
          type="button"
          onClick={onRemove}
          className="ml-1 rounded-full p-0.5 transition-colors"
        >
          <X className="h-3 w-3 text-mw-neutral-500" />
        </button>
      )}
    </span>
  </Tooltip>
);

interface OptionProps {
  node: TreeNode;
  level: number;
  onToggle: (node: TreeNode) => void;
  searchTerm?: string;
  value: string[];
  isNodeSelected: (node: TreeNode) => boolean;
  isNodeIndeterminate: (node: TreeNode) => boolean;
  getAllNodes: (nodes: TreeNode[]) => TreeNode[];
  expandable: boolean;
  isLimitReached: boolean;
}

const Option: React.FC<OptionProps> = ({
  node,
  level,
  onToggle,
  searchTerm,
  value,
  isNodeSelected,
  isNodeIndeterminate,
  getAllNodes,
  expandable,
  isLimitReached,
}) => {
  const { t } = useTranslate("common");
  const [isExpanded, setIsExpanded] = useState(false);
  const hasChildren = node.children && node.children.length > 0;
  const paddingLeft = level * 20 + 8;

  const isSelected = isNodeSelected(node);
  const isIndeterminate = isNodeIndeterminate(node);
  const shouldDisable = node.disabled || (isLimitReached && !isSelected);

  // Count badge + selection status for parent nodes
  const directChildCount = node.children?.length ?? 0;
  const allDescendants = hasChildren ? getAllNodes(node.children!) : [];
  const selectedDescendantCount = allDescendants.filter((n) =>
    value.includes(n.value),
  ).length;
  const statusText = hasChildren
    ? selectedDescendantCount > 0
      ? t("nSelected", { count: selectedDescendantCount })
      : t("noneSelected")
    : null;

  const handleToggle = (e: React.MouseEvent) => {
    e.stopPropagation();
    onToggle(node);
  };

  const handleExpandToggle = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (hasChildren) setIsExpanded((p) => !p);
  };

  return (
    <>
      <div
        className={`flex items-start py-2 px-3 hover:bg-mw-neutral-50 cursor-pointer min-w-0 ${
          shouldDisable ? "opacity-50 cursor-not-allowed" : ""
        }`}
        style={{ paddingLeft }}
        onClick={!node.disabled ? handleToggle : undefined}
      >
        {/* Expand/Collapse Button */}
        {expandable && hasChildren && (
          <button
            type="button"
            onClick={handleExpandToggle}
            className="flex items-center justify-center w-4 h-4 mr-2 mt-0.5 shrink-0 rounded"
          >
            {isExpanded ? (
              <ChevronDown className="h-3 w-3 text-mw-neutral-500" />
            ) : (
              <ChevronRight className="h-3 w-3 text-mw-neutral-500" />
            )}
          </button>
        )}
        {/* Spacer when no expand button */}
        {expandable && !hasChildren && (
          <div className="w-4 h-4 mr-2 shrink-0" />
        )}

        {/* Checkbox — id derived from node to keep htmlFor association unique */}
        <Checkbox
          checked={isSelected}
          isIndeterminate={isIndeterminate}
          onChange={() => {}}
          label=""
          id={node.id ?? node.value}
          className="mr-2 mt-0.5 shrink-0"
        />

        {/* Label + count badge + status */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="text-sm font-medium text-mw-neutral-800 leading-tight">
              {node.label}
            </span>
            {hasChildren && directChildCount > 0 && (
              <span className="text-[10px] font-medium text-mw-neutral-500 bg-mw-neutral-100 rounded px-1.5 py-0.5 leading-none">
                {directChildCount}
              </span>
            )}
          </div>
          {statusText ? (
            <p className="text-xs text-mw-neutral-400 mt-0.5 leading-tight">
              {statusText}
            </p>
          ) : node.description ? (
            <p className="text-xs text-mw-neutral-400 mt-0.5 leading-tight">
              {node.description}
            </p>
          ) : null}
        </div>
      </div>
      <DropdownSeparator style={{ marginLeft: `${paddingLeft}px` }} />

      {/* Children rendered outside the row */}
      {hasChildren && isExpanded && (
        <>
          {node.children!.map((child) => (
            <Option
              key={child.id ?? child.value}
              node={child}
              level={level + 1}
              onToggle={onToggle}
              searchTerm={searchTerm}
              value={value}
              isNodeSelected={isNodeSelected}
              isNodeIndeterminate={isNodeIndeterminate}
              getAllNodes={getAllNodes}
              expandable={expandable}
              isLimitReached={isLimitReached}
            />
          ))}
        </>
      )}
    </>
  );
};

const MultiSelect: React.FC<MultiSelectProps> = ({
  options,
  value = [],
  onChange,
  placeholder,
  maxVisibleChips = 3,
  responsiveChips = false,
  disabled = false,
  className = "",
  searchable = true,
  clearable = true,
  closeOnSelect = false,
  maxHeight = "min(60vh, 400px)",
  expandable = true,
  showSelectAll = true,
  selectAllLabel,
  noOptionFoundLabel,
  maxSelections,
  showAddOptionIfNoOptionFound = false,
  renderFooter,
  onSearchChange,
  id,
  showPlaceHolderWithSearchOption,
  showPlaceHolderWithSearchOptionWithMaxSelectionCount,
  onBlur,
  onRemoteLoad,
  onRemoteSearch,
  onRemoteLoadMore,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");
  const valueRef = useRef(value);
  valueRef.current = value;
  const dropdownRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const optionsScrollRef = useRef<HTMLDivElement>(null);
  const prevOpenRef = useRef(false);

  useEffect(() => {
    const justOpened = isOpen && !prevOpenRef.current;
    prevOpenRef.current = isOpen;
    if (justOpened && onRemoteLoad) {
      onRemoteLoad();
    }
  }, [isOpen, onRemoteLoad]);

  const handleClose = useCallback(() => {
    setIsOpen(false);
    setSearchTerm("");
    onBlur?.(valueRef.current);
  }, [onBlur]);

  const { contentRef, portalPosition, isPositionReady } =
    useDropdownPortalSimple({
      triggerRef: dropdownRef,
      isOpen,
      onClose: handleClose,
      maxHeight,
      minWidth: 0,
    });

  const getAllNodes = useCallback((nodes: TreeNode[]): TreeNode[] => {
    const result: TreeNode[] = [];
    const traverse = (nodeList: TreeNode[]) => {
      nodeList.forEach((node) => {
        result.push(node);
        if (node.children) traverse(node.children);
      });
    };
    traverse(nodes);
    return result;
  }, []);

  // Render tree nodes recursively or flattened based on expandable setting
  const renderTreeNodes = (
    treeNodes: TreeNode[],
    currentLevel = 0,
  ): React.ReactNode => {
    return treeNodes.map((node) => {
      const hasChildren = node.children && node.children.length > 0;

      return (
        <React.Fragment key={node.id || node.value}>
          <Option
            node={node}
            level={currentLevel}
            onToggle={handleNodeToggle}
            searchTerm={searchTerm}
            value={value}
            isNodeSelected={isNodeSelected}
            isNodeIndeterminate={isNodeIndeterminate}
            getAllNodes={getAllNodes}
            expandable={expandable}
            isLimitReached={isSelectionLimitReached}
          />
          {/* If not expandable, render children immediately */}
          {hasChildren && !expandable && (
            <div>{renderTreeNodes(node.children!, currentLevel + 1)}</div>
          )}
        </React.Fragment>
      );
    });
  };
  // Check if selection limit has been reached
  const isSelectionLimitReached =
    maxSelections !== undefined && value.length >= maxSelections;

  const allNodes = useMemo(() => getAllNodes(options), [options, getAllNodes]);
  const selectedNodes = useMemo(
    () => allNodes.filter((node) => value.includes(node.value)),
    [allNodes, value],
  );

  const areAllOptionsSelected = useMemo(
    () =>
      allNodes.length > 0 &&
      allNodes.every((node) => value.includes(node.value)),
    [allNodes, value],
  );

  const isNodeSelected = useCallback(
    (node: TreeNode) => value.includes(node.value),
    [value],
  );

  const isNodeIndeterminate = useCallback(
    (node: TreeNode) => {
      if (!node.children?.length) return false;
      const childValues = getAllNodes(node.children).map((c) => c.value);
      const selectedCount = childValues.filter((v) => value.includes(v)).length;
      return selectedCount > 0 && selectedCount < childValues.length;
    },
    [value, getAllNodes],
  );

  const handleSelectAllToggle = useCallback(() => {
    if (
      allNodes.length > 0 &&
      allNodes.every((node) => value.includes(node.value))
    ) {
      onChange([]);
    } else {
      const allValues = allNodes.map((node) => node.value);
      onChange(
        maxSelections != null ? allValues.slice(0, maxSelections) : allValues,
      );
    }
  }, [allNodes, value, maxSelections, onChange]);

  const handleNodeToggle = useCallback(
    (node: TreeNode) => {
      if (node.disabled) return;
      const selected = value.includes(node.value);
      const childValues = getAllNodes(node.children || []).map((c) => c.value);
      let newValue: string[];
      if (selected) {
        newValue = value.filter(
          (v) => v !== node.value && !childValues.includes(v),
        );
      } else {
        const toAdd = [node.value, ...childValues];
        if (
          maxSelections != null &&
          value.length + toAdd.length > maxSelections
        )
          return;
        newValue = [...value, ...toAdd];
      }
      onChange(newValue);
      if (closeOnSelect) handleClose();
    },
    [value, maxSelections, closeOnSelect, getAllNodes, onChange, handleClose],
  );

  const handleChipRemove = useCallback(
    (valueToRemove: string) => {
      const node = allNodes.find((n) => n.value === valueToRemove);
      if (!node) return;
      const childValues = getAllNodes(node.children || []).map((c) => c.value);
      onChange(
        value.filter((v) => v !== valueToRemove && !childValues.includes(v)),
      );
    },
    [allNodes, value, getAllNodes, onChange],
  );

  const handleSearchTermOption = useCallback(() => {
    onChange([...value, searchTerm]);
    setSearchTerm("");
  }, [value, searchTerm, onChange]);

  const handleClearAll = useCallback(() => {
    onChange([]);
    onBlur?.([]);
  }, [onChange, onBlur]);

  const filterOptionsWithHierarchy = useCallback(
    (nodes: TreeNode[], term: string): TreeNode[] => {
      if (!term) return nodes;
      const result: TreeNode[] = [];
      const lower = term.toLowerCase();
      for (const node of nodes) {
        const nodeMatches = node.label.toLowerCase().includes(lower);
        const filteredChildren = node.children
          ? filterOptionsWithHierarchy(node.children, term)
          : [];
        if (nodeMatches || filteredChildren.length > 0) {
          result.push({
            ...node,
            children:
              filteredChildren.length > 0 ? filteredChildren : node.children,
          });
        }
      }
      return result;
    },
    [],
  );

  const filteredOptions = useMemo(() => {
    if (onRemoteSearch) {
      return options;
    }
    return searchTerm
      ? filterOptionsWithHierarchy(options, searchTerm.toLowerCase())
      : options;
  }, [options, searchTerm, filterOptionsWithHierarchy, onRemoteSearch]);

  // Responsive chips (SI 47): measure a hidden copy of all chips and fit as
  // many as the row width allows, reserving room for the "+N" badge.
  const chipsAreaRef = useRef<HTMLDivElement>(null);
  const measureRowRef = useRef<HTMLDivElement>(null);
  const [fitCount, setFitCount] = useState<number | null>(null);
  const CHIP_GAP = 4; // matches gap-1 on the chips row

  useLayoutEffect(() => {
    if (!responsiveChips) return;

    const compute = () => {
      const area = chipsAreaRef.current;
      const measureRow = measureRowRef.current;
      if (!area || !measureRow) return;
      const containerWidth = area.clientWidth;
      // jsdom / hidden containers report 0 — keep the maxVisibleChips fallback
      if (!containerWidth) return;
      const children = Array.from(measureRow.children) as HTMLElement[];
      if (children.length < 2) return;
      const badgeWidth = children[children.length - 1].offsetWidth;
      const chipWidths = children
        .slice(0, -1)
        .map((child) => child.offsetWidth);
      setFitCount(
        computeVisibleChipCount(
          containerWidth,
          chipWidths,
          badgeWidth,
          CHIP_GAP,
        ),
      );
    };

    compute();
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(compute);
    if (chipsAreaRef.current) observer.observe(chipsAreaRef.current);
    return () => observer.disconnect();
  }, [responsiveChips, selectedNodes]);

  // Get visible chips and remaining count
  const effectiveMaxChips =
    responsiveChips && fitCount !== null ? fitCount : maxVisibleChips;
  const visibleChips = selectedNodes.slice(0, effectiveMaxChips);
  const remainingCount = selectedNodes.length - effectiveMaxChips;
  const { t } = useTranslate("common");

  const [isHovered, setIsHovered] = useState(false);

  const handleOptionsScroll = useCallback(() => {
    if (!onRemoteLoadMore) return;
    const el = optionsScrollRef.current;
    if (!el) return;
    const { scrollTop, scrollHeight, clientHeight } = el;
    const threshold = 80;
    if (scrollHeight - scrollTop - clientHeight < threshold) {
      onRemoteLoadMore();
    }
  }, [onRemoteLoadMore]);

  const multiSelectId = id || "multiselect";
  return (
    <div
      id={multiSelectId}
      className={`relative ${className}`}
      ref={dropdownRef}
    >
      {/* Input Field */}
      <div
        className={clsx(
          "relative w-full h-10 min-h-10 flex items-center border border-mw-neutral-100 rounded-md bg-white",
          disabled ? "bg-mw-neutral-50 cursor-not-allowed" : "cursor-pointer",
          isOpen && "ring-1 ring-mw-primary-500 border-mw-primary-500",
        )}
        onClick={() => {
          if (disabled) return;
          if (isOpen) handleClose();
          else setIsOpen(true);
        }}
      >
        <div className="flex w-full items-center justify-between gap-2 px-2 py-2">
          <div
            ref={chipsAreaRef}
            className={clsx(
              "flex min-w-0 flex-1 items-center gap-1",
              responsiveChips
                ? "relative flex-nowrap overflow-hidden"
                : "flex-wrap",
            )}
          >
            {/* Hidden measurement row: all chips + widest possible badge */}
            {responsiveChips && selectedNodes.length > 0 && (
              <div
                ref={measureRowRef}
                aria-hidden="true"
                className="invisible pointer-events-none absolute left-0 top-0 flex items-center gap-1 whitespace-nowrap"
              >
                {selectedNodes.map((node) => (
                  <Chip
                    key={node.id}
                    label={node.label}
                    onRemove={() => {}}
                    disabled
                  />
                ))}
                <div className="inline-flex items-center px-2 py-1 text-sm rounded-md border border-mw-neutral-100">
                  +{selectedNodes.length}
                </div>
              </div>
            )}

            {/* Selected Chips */}
            {visibleChips.map((node) => (
              <Chip
                key={node.id}
                label={node.label}
                onRemove={() => handleChipRemove(node.value)}
                disabled={disabled}
              />
            ))}

            {/* Remaining Count */}
            {remainingCount > 0 && (
              <div
                className="relative inline-flex items-center px-2 py-1 text-mw-neutral-500 text-sm rounded-md border border-mw-neutral-100"
                onMouseEnter={() => setIsHovered(true)}
                onMouseLeave={() => setIsHovered(false)}
              >
                <span>+{remainingCount}</span>
                {isHovered && (
                  <div className="absolute left-0 top-full z-10 mt-1 w-auto max-w-xs max-h-48 overflow-y-auto scrollbar whitespace-nowrap rounded-md border border-mw-neutral-100 bg-white p-2 shadow-lg">
                    <div className="text-sm text-mw-neutral-500">
                      {selectedNodes.slice(effectiveMaxChips).map((node) => (
                        <div key={node.value} className="py-1">
                          {node.label}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Placeholder */}
            {selectedNodes.length === 0 && (
              <span
                className={clsx(
                  "text-sm",
                  disabled ? "text-mw-neutral-100" : "text-mw-neutral-500",
                )}
              >
                {placeholder ?? t("multiSelect.placeholder")}
              </span>
            )}
          </div>
          <div className="inline-flex shrink-0 items-center gap-1">
            {/* Clear Button */}
            {clearable && selectedNodes.length > 0 && !disabled && (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  handleClearAll();
                }}
                className="p-1 cursor-pointer rounded-full transition-colors"
              >
                <XCircle className="h-4 w-4 text-mw-neutral-400" />
              </button>
            )}

            {/* Dropdown Arrow */}
            <button
              type="button"
              className="p-1 rounded-full transition-colors"
              onClick={(e) => {
                e.stopPropagation();
                if (disabled) return;
                if (isOpen) handleClose();
                else setIsOpen(true);
              }}
            >
              <ChevronDown
                className={`h-4 w-4 transition-transform ${
                  isOpen ? "rotate-180" : ""
                } ${disabled ? "text-mw-neutral-100" : "text-mw-neutral-400"}`}
              />
            </button>
          </div>
        </div>
      </div>

      {/* Portal Dropdown */}
      {isOpen &&
        isPositionReady &&
        portalPosition &&
        createPortal(
          <>
            {/* Overlay */}
            <div
              className="dropdown-overlay"
              onClick={handleClose}
              aria-hidden="true"
            />
            {/* Dropdown Content */}
            <div
              ref={contentRef}
              className="fixed dropdown-content flex flex-col rounded-md shadow-lg overflow-hidden bg-white border border-mw-neutral-100"
              style={{
                top: `${portalPosition.top}px`,
                left: `${portalPosition.left}px`,
                width: `${portalPosition.width}px`,
                minWidth: `${portalPosition.width}px`,
                maxHeight: maxHeight,
              }}
              role="menu"
              aria-orientation="vertical"
            >
              {" "}
              {/* Search Input */}
              {searchable && (
                <>
                  <div className="p-2 relative">
                    <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                      <Search className="h-4 w-4 text-mw-neutral-400" />
                    </div>
                    <Input
                      ref={inputRef}
                      type="text"
                      placeholder={t("multiSelect.searchPlaceholder")}
                      value={searchTerm}
                      onChange={(e) => {
                        const term = e.target.value;
                        setSearchTerm(term);
                        onSearchChange?.(term);
                        onRemoteSearch?.(term);
                      }}
                      onClick={(e) => e.stopPropagation()}
                      className="pl-7"
                    />
                  </div>
                  {showPlaceHolderWithSearchOption && (
                    <div className="mx-3 text-sm text-mw-neutral-500 flex items-center gap-2 border-b border-mw-neutral-100 pt-2 pb-4">
                      {showPlaceHolderWithSearchOption}
                      {showPlaceHolderWithSearchOptionWithMaxSelectionCount && (
                        <Badge variant="secondary" size="sm">
                          {value.length} / {maxSelections}
                        </Badge>
                      )}
                    </div>
                  )}
                </>
              )}
              <div
                ref={optionsScrollRef}
                className="overflow-y-auto flex-1 scrollbar overflow-x-hidden scrollbar-thin scrollbar-thumb-mw-neutral-300 scrollbar-track-mw-neutral-100"
                onScroll={onRemoteLoadMore ? handleOptionsScroll : undefined}
              >
                {/* Options */}
                <div className="py-1">
                  {/* Select All Option */}
                  {showSelectAll && filteredOptions.length > 0 && (
                    <>
                      <div
                        className="flex items-start py-2 px-2 hover:bg-mw-neutral-50 cursor-pointer min-w-0"
                        onClick={handleSelectAllToggle}
                      >
                        <Checkbox
                          checked={areAllOptionsSelected}
                          isIndeterminate={false}
                          onChange={() => {}}
                          label={selectAllLabel ?? t("selectAll")}
                          className="mr-2"
                        />
                      </div>
                      <DropdownSeparator />
                    </>
                  )}

                  {filteredOptions.length === 0 ? (
                    <>
                      <div className="mx-3 pt-2 pb-4 text-sm text-mw-neutral-500 border-b border-mw-neutral-100">
                        {noOptionFoundLabel
                          ? noOptionFoundLabel
                          : t("noOptionFound")}
                      </div>
                      {showAddOptionIfNoOptionFound &&
                        (isSelectionLimitReached ? (
                          ""
                        ) : (
                          <button
                            type="button"
                            onClick={handleSearchTermOption}
                            className="w-full flex items-start justify-start gap-2 px-2 py-2 text-mw-primary-500 hover:text-mw-primary-600 hover:bg-mw-primary-50 text-sm"
                          >
                            <Plus className="size-4" />
                            {searchTerm}
                          </button>
                        ))}
                    </>
                  ) : (
                    renderTreeNodes(filteredOptions)
                  )}
                </div>
              </div>
              {/* Footer - Outside scrollable area */}
              {renderFooter && (
                <div className="border-t border-mw-neutral-200 w-full dark:border-mw-neutral-700 bg-white dark:bg-mw-neutral-800">
                  {renderFooter(setIsOpen)}
                </div>
              )}
            </div>
          </>,
          document.body,
        )}
    </div>
  );
};

export default MultiSelect;
