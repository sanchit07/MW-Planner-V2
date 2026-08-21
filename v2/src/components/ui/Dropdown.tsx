import { useDropdownPortal } from "@hooks/useDropdownPortal";
import { useTranslate } from "@tolgee/react";
import { clsx } from "clsx";
import { ChevronDown, ChevronUp, X, Search } from "lucide-react";
import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  useRef,
  useEffect,
  useLayoutEffect,
  ReactNode,
} from "react";
import { createPortal } from "react-dom";

interface DropdownContextType {
  isOpen: boolean;
  setIsOpen: (open: boolean) => void;
  toggle: () => void;
  value?: string;
  onChange?: (value: string) => void;
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  searchable?: boolean;
  dropdownRef: React.RefObject<HTMLDivElement | null>;
}

const DropdownContext = createContext<DropdownContextType | undefined>(
  undefined,
);

export interface DropdownProps {
  children: ReactNode;
  className?: string;
  name?: string;
  value?: string;
  onChange?: (value: string) => void;
  searchable?: boolean;
}

export const Dropdown = ({
  children,
  className,
  name,
  value,
  onChange,
  searchable = false,
}: DropdownProps) => {
  const [isOpen, setIsOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const dropdownRef = useRef<HTMLDivElement>(null);

  const toggle = useCallback(() => {
    setIsOpen((prev) => !prev);
  }, []);

  useEffect(() => {
    if (!isOpen) setSearchQuery("");
  }, [isOpen]);

  const contextValue = useMemo<DropdownContextType>(
    () => ({
      isOpen,
      setIsOpen,
      toggle,
      value,
      onChange,
      searchQuery,
      setSearchQuery,
      searchable,
      dropdownRef,
    }),
    [isOpen, toggle, value, onChange, searchQuery, searchable],
  );

  const dropdownId = name ? `dropdown-${name}` : "dropdown";
  return (
    <DropdownContext.Provider value={contextValue}>
      <div
        id={dropdownId}
        ref={dropdownRef}
        className={clsx("relative", className)}
      >
        {children}
        {/* Hidden input for form integration */}
        {name && (
          <input
            type="hidden"
            name={name}
            value={value || ""}
            onChange={() => {}} // Controlled by dropdown selections
          />
        )}
      </div>
    </DropdownContext.Provider>
  );
};

export interface DropdownTriggerProps {
  children: ReactNode;
  asChild?: boolean;
  className?: string;
  clearable?: boolean;
  onClear?: () => void;
  hasValue?: boolean;
  position?: "left" | "right" | "center" | "bottom";
}

export const DropdownTrigger = ({
  children,
  asChild,
  className,
  clearable = false,
  onClear,
  hasValue = false,
  position = "bottom",
}: DropdownTriggerProps) => {
  const { t } = useTranslate(["common"]);
  const context = useContext(DropdownContext);

  if (!context) {
    throw new Error("DropdownTrigger must be used within a Dropdown");
  }

  const handleClear = (e: React.MouseEvent) => {
    e.stopPropagation();
    onClear?.();
  };

  if (asChild) {
    // Clone the child element and add click handler
    return (
      <div onClick={context.toggle} className={className}>
        {children}
      </div>
    );
  }

  const triggerId = `dropdown-trigger-${context.value || "default"}`;

  // Determine content alignment based on position prop
  let contentAlignment: string;
  if (position === "center") {
    contentAlignment = "justify-center";
  } else if (position === "right") {
    contentAlignment = "justify-end";
  } else {
    contentAlignment = "justify-start";
  }

  return (
    <button
      id={triggerId}
      type="button"
      onClick={context.toggle}
      className={clsx(
        "w-full h-10 min-h-10 inline-flex items-center rounded-md px-3 py-2 text-sm font-medium",
        "bg-white dark:bg-mw-neutral-800 border border-mw-neutral-100 dark:border-mw-neutral-600",
        "text-mw-neutral-500 dark:text-mw-neutral-300 cursor-pointer",
        "hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700",
        // "focus:outline-none focus:ring-1 focus:ring-mw-primary-500 focus:border-transparent",
        className,
      )}
      aria-expanded={context.isOpen}
      aria-haspopup="true"
    >
      <div className={clsx("flex-1 flex items-center", contentAlignment)}>
        {children}
      </div>
      <div className="inline-flex items-center gap-1 ml-2 shrink-0">
        {clearable && hasValue && onClear && (
          <div
            id={`${triggerId}-clear`}
            onClick={handleClear}
            className="p-1 hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-600 rounded-sm transition-colors"
            title={t("aria.clearSelection")}
          >
            <X className="w-3 h-3" />
          </div>
        )}
        {context.isOpen ? (
          <ChevronUp className="h-4 w-4 transition-transform" />
        ) : (
          <ChevronDown className="h-4 w-4 transition-transform" />
        )}
      </div>
    </button>
  );
};

export interface DropdownContentProps {
  children: ReactNode;
  className?: string;
  align?: "left" | "right" | "center";
  maxHeight?: string;
  /** Minimum width of the dropdown panel (e.g. "200px", "20rem"). When not set, uses the portal-calculated width (trigger width). */
  minWidth?: string;
  /** Maximum width of the dropdown panel (e.g. "400px", "50vw"). When not set, uses default responsive max-width. */
  maxWidth?: string;
  noResultsText?: string;
}

export interface DropdownScrollableContentProps {
  children: ReactNode;
  className?: string;
  maxHeight?: string;
  noResultsText?: string;
}

export interface DropdownFooterProps {
  children: ReactNode;
  className?: string;
}

export const DropdownContent = ({
  children,
  className,
  align = "left",
  maxHeight = "300px",
  minWidth: minWidthProp,
  maxWidth,
  noResultsText = "No result found",
}: DropdownContentProps) => {
  const context = useContext(DropdownContext);

  if (!context) {
    throw new Error("DropdownContent must be used within a Dropdown");
  }

  const { contentRef, portalPosition, isPositionReady } = useDropdownPortal({
    triggerRef: context.dropdownRef as React.RefObject<HTMLElement | null>,
    isOpen: context.isOpen,
    onClose: () => context.setIsOpen(false),
    align,
    maxHeight,
    minWidth: 0,
  });

  if (!context.isOpen || !isPositionReady || !portalPosition) return null;

  const hasContent =
    children != null && (Array.isArray(children) ? children.length > 0 : true);

  const portalContent = (
    <>
      <div
        className="dropdown-overlay"
        onClick={() => context.setIsOpen(false)}
        aria-hidden="true"
      />
      <div
        id={`${context.dropdownRef.current?.id ?? "dropdown"}-content`}
        ref={contentRef}
        className={clsx(
          "p-2 rounded-lg outline outline-offset-1 outline-neutral-100",
          "inline-flex flex-col justify-start items-start gap-1",
          "fixed dropdown-content rounded-md shadow-lg",
          "bg-white dark:bg-mw-neutral-800 border border-mw-neutral-100 dark:border-mw-neutral-700",
          "transition-all duration-200 ease-out",
          "max-w-[calc(100vw-2rem)] sm:max-w-none mx-4 sm:mx-0",
          className,
        )}
        role="menu"
        aria-orientation="vertical"
        style={{
          top: `${portalPosition.top}px`,
          left: `${portalPosition.left}px`,
          minWidth: minWidthProp ?? `${portalPosition.width}px`,
          maxHeight,
          ...(maxWidth !== undefined && { maxWidth }),
        }}
      >
        {hasContent ? (
          children
        ) : (
          <div className="w-full px-3 py-4 text-center text-sm text-mw-neutral-500 dark:text-mw-neutral-400">
            {noResultsText}
          </div>
        )}
      </div>
    </>
  );

  return createPortal(portalContent, document.body);
};

export const DropdownScrollableContent = ({
  children,
  className,
  maxHeight = "200px",
  noResultsText = "No result found",
}: DropdownScrollableContentProps) => {
  const context = useContext(DropdownContext);
  const contentRef = useRef<HTMLDivElement>(null);
  const [hasVisibleItems, setHasVisibleItems] = useState(true);

  const searchable = context?.searchable ?? false;
  const searchQuery = context?.searchQuery ?? "";
  const hasSearch = searchQuery.trim() !== "";

  useLayoutEffect(() => {
    if (!searchable || !hasSearch) {
      setHasVisibleItems(true);
      return;
    }
    if (!contentRef.current) return;

    const visibleButtons = contentRef.current.querySelectorAll(
      'button[role="menuitem"]',
    );
    setHasVisibleItems(visibleButtons.length > 0);
  }, [searchable, hasSearch, searchQuery, children]);

  const showNoResults = searchable && hasSearch && !hasVisibleItems;

  return (
    <div
      ref={contentRef}
      className={clsx(
        "w-full overflow-y-auto overflow-x-hidden py-2 scrollbar",
        "scrollbar-thin scrollbar-thumb-mw-neutral-300 scrollbar-track-mw-neutral-100",
        "dark:scrollbar-thumb-mw-neutral-600 dark:scrollbar-track-mw-neutral-800",
        className,
      )}
      style={{ maxHeight }}
    >
      <div className={clsx(showNoResults && "hidden")}>{children}</div>
      {showNoResults && (
        <div className="w-full px-3 py-4 text-center text-sm text-mw-neutral-500 dark:text-mw-neutral-400">
          {noResultsText}
        </div>
      )}
    </div>
  );
};

export const DropdownFooter = ({
  children,
  className,
}: DropdownFooterProps) => {
  return (
    <div
      className={clsx(
        "w-full border-t border-mw-neutral-100 dark:border-mw-neutral-700",
        "bg-mw-neutral-50 dark:bg-mw-neutral-750",
        "px-3 py-2 rounded-b-md",
        "shrink-0",
        className,
      )}
    >
      {children}
    </div>
  );
};

export interface DropdownItemProps {
  children: ReactNode;
  onClick?: () => void;
  disabled?: boolean;
  className?: string;
  value?: string; // Value to set when this item is selected
  searchableText?: string; // Text to match against search query
  closeOnSelect?: boolean;
}

export const DropdownItem = ({
  children,
  onClick,
  disabled,
  className,
  value,
  searchableText,
  closeOnSelect = true,
}: DropdownItemProps) => {
  const context = useContext(DropdownContext);

  if (!context) {
    throw new Error("DropdownItem must be used within a Dropdown");
  }

  const { searchable, searchQuery, onChange, setIsOpen } = context;

  const handleClick = useCallback(() => {
    if (disabled) return;
    if (value != null && onChange) onChange(value);
    onClick?.();
    if (closeOnSelect) setIsOpen(false);
  }, [disabled, value, onChange, onClick, setIsOpen, closeOnSelect]);

  // Filter item based on search query if searchable
  const shouldShow =
    !searchable ||
    !searchQuery ||
    (searchableText &&
      searchableText.toLowerCase().includes(searchQuery.toLowerCase())) ||
    (typeof children === "string" &&
      children.toLowerCase().includes(searchQuery.toLowerCase()));

  if (!shouldShow) {
    return null;
  }

  const itemId = value
    ? `dropdown-item-${value}`
    : `dropdown-item-${typeof children === "string" ? children.toLowerCase().replace(/\s+/g, "-") : "option"}`;

  return (
    <button
      id={itemId}
      type="button"
      onClick={handleClick}
      disabled={disabled}
      className={clsx(
        "self-stretch w-full px-2 py-1 rounded-sm text-sm inline-flex justify-start items-center gap-2",
        "text-mw-neutral-500 dark:text-mw-neutral-300",
        "hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700",
        "focus:bg-mw-neutral-50 dark:focus:bg-mw-neutral-700 focus:outline-none cursor-pointer",
        disabled && "opacity-50 cursor-not-allowed",
        className,
      )}
      role="menuitem"
    >
      {children}
    </button>
  );
};

export interface DropdownSeparatorProps {
  className?: string;
  style?: React.CSSProperties;
}

export const DropdownSeparator = ({
  className,
  style,
}: DropdownSeparatorProps) => {
  return (
    <div
      className={clsx(
        "w-full my-1 h-px bg-mw-neutral-100 dark:bg-mw-neutral-700",
        className,
      )}
      role="separator"
      style={style}
    />
  );
};

export interface DropdownSearchProps {
  placeholder?: string;
  className?: string;
}

export const DropdownSearch = ({
  placeholder = "Search...",
  className,
}: DropdownSearchProps) => {
  const context = useContext(DropdownContext);

  if (!context) {
    throw new Error("DropdownSearch must be used within a Dropdown");
  }

  if (!context.searchable) {
    return null;
  }

  return (
    <div
      id="dropdown-search"
      className={clsx(
        "w-full px-2 py-2 border-b border-mw-neutral-100 dark:border-mw-neutral-700",
        className,
      )}
    >
      <div className="relative">
        <Search className="absolute left-2 top-1/2 transform -translate-y-1/2 w-4 h-4 text-mw-neutral-400 dark:text-mw-neutral-500" />
        <input
          id="dropdown-search-input"
          type="text"
          value={context.searchQuery}
          onChange={(e) => {
            e.stopPropagation();
            context.setSearchQuery(e.target.value);
          }}
          onClick={(e) => e.stopPropagation()}
          className={clsx(
            "w-full pl-8 pr-3 py-1.5 text-sm rounded-md",
            "bg-white dark:bg-mw-neutral-800",
            "border border-mw-neutral-200 dark:border-mw-neutral-600",
            "text-mw-neutral-700 dark:text-mw-neutral-200",
            "placeholder:text-mw-neutral-400 dark:placeholder:text-mw-neutral-500",
            "focus:outline-none focus:ring-1 focus:ring-mw-primary-500 focus:border-transparent",
            className,
          )}
          placeholder={placeholder}
          autoFocus
        />
      </div>
    </div>
  );
};
