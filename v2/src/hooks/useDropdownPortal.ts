import { useCallback, useEffect, useRef, useState } from "react";

const PADDING = 8;
const GAP = 4;
const MIN_CONTENT_WIDTH = 250;
const MOBILE_BREAKPOINT = 640;

export type DropdownAlign = "left" | "right" | "center";

export interface UseDropdownPortalOptions {
  triggerRef: React.RefObject<HTMLElement | null>;
  isOpen: boolean;
  onClose: () => void;
  align?: DropdownAlign;
  maxHeight?: string;
  /** Minimum content width in px. Use 0 to match trigger width exactly. Default 250. */
  minWidth?: number;
}

export interface PortalPosition {
  top: number;
  left: number;
  width: number;
}

function clampHorizontal(
  left: number,
  contentWidth: number,
  viewportWidth: number,
): number {
  return Math.max(
    PADDING,
    Math.min(left, viewportWidth - contentWidth - PADDING),
  );
}

function computeInitialPosition(
  triggerRect: DOMRect,
  align: DropdownAlign,
  minWidth: number = MIN_CONTENT_WIDTH,
): PortalPosition {
  const viewportWidth = window.innerWidth;
  const contentWidth = Math.max(triggerRect.width, minWidth);
  const isMobile = viewportWidth < MOBILE_BREAKPOINT;

  let left: number;
  if (isMobile) {
    const triggerCenter = triggerRect.left + triggerRect.width / 2;
    left = clampHorizontal(
      triggerCenter - contentWidth / 2,
      contentWidth,
      viewportWidth,
    );
  } else {
    switch (align) {
      case "right":
        left = triggerRect.right - contentWidth;
        break;
      case "center":
        left = triggerRect.left + triggerRect.width / 2 - contentWidth / 2;
        break;
      default:
        left = triggerRect.left;
    }
    left = clampHorizontal(left, contentWidth, viewportWidth);
  }

  const viewportHeight = window.innerHeight;
  const spaceBelow = viewportHeight - triggerRect.bottom - PADDING;
  const estimatedHeight = 200;
  // Default: open below trigger. Only open above when there's not enough space below.
  const top =
    spaceBelow < estimatedHeight
      ? Math.max(PADDING, triggerRect.top - estimatedHeight - GAP)
      : triggerRect.bottom + GAP;

  return { top, left, width: contentWidth };
}

function computeRefinedPosition(
  trigger: HTMLElement,
  content: HTMLElement,
  align: DropdownAlign,
  maxHeightStr: string,
  minWidth: number = MIN_CONTENT_WIDTH,
): PortalPosition | null {
  const triggerRect = trigger.getBoundingClientRect();
  const viewportHeight = window.innerHeight;
  const viewportWidth = window.innerWidth;
  const contentRect = content.getBoundingClientRect();
  const contentHeight =
    contentRect.height > 0 ? contentRect.height : content.scrollHeight;
  const maxAllowedHeight = parseInt(maxHeightStr, 10) || 300;
  const actualHeight = Math.min(contentHeight, maxAllowedHeight);

  if (actualHeight <= 0) return null;

  const spaceBelow = viewportHeight - triggerRect.bottom - PADDING;
  const spaceAbove = triggerRect.top - PADDING;
  // Prefer opening below; only open upward when content doesn't fit below but fits above.
  const shouldOpenUpward =
    spaceBelow < actualHeight + GAP && spaceAbove >= actualHeight + GAP;

  const top = shouldOpenUpward
    ? triggerRect.top - actualHeight - GAP
    : triggerRect.bottom + GAP;
  const minTop = PADDING;
  const maxTop = viewportHeight - actualHeight - PADDING;
  let finalTop = Math.max(minTop, Math.min(top, maxTop));

  if (actualHeight > viewportHeight - 2 * PADDING) {
    finalTop = PADDING;
  } else if (finalTop + actualHeight > viewportHeight - PADDING) {
    finalTop = viewportHeight - actualHeight - PADDING;
  }

  const contentWidth = Math.max(
    contentRect.width > 0 ? contentRect.width : triggerRect.width,
    minWidth,
  );
  const isMobile = viewportWidth < MOBILE_BREAKPOINT;

  let left: number;
  if (isMobile) {
    const triggerCenter = triggerRect.left + triggerRect.width / 2;
    left = clampHorizontal(
      triggerCenter - contentWidth / 2,
      contentWidth,
      viewportWidth,
    );
  } else {
    switch (align) {
      case "right":
        left = triggerRect.right - contentWidth;
        break;
      case "center":
        left = triggerRect.left + triggerRect.width / 2 - contentWidth / 2;
        break;
      default:
        left = triggerRect.left;
    }
    left = clampHorizontal(left, contentWidth, viewportWidth);
  }

  return { top: finalTop, left, width: contentWidth };
}

export function useDropdownPortal({
  triggerRef,
  isOpen,
  onClose,
  align = "left",
  maxHeight = "300px",
  minWidth = MIN_CONTENT_WIDTH,
}: UseDropdownPortalOptions) {
  const contentRef = useRef<HTMLDivElement>(null);
  const [portalPosition, setPortalPosition] = useState<PortalPosition | null>(
    null,
  );
  const [isPositionReady, setIsPositionReady] = useState(false);

  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  const effectiveMinWidth = minWidth ?? MIN_CONTENT_WIDTH;

  useEffect(() => {
    if (!isOpen) {
      setPortalPosition(null);
      setIsPositionReady(false);
      return;
    }
    const trigger = triggerRef.current;
    if (!trigger) return;

    setPortalPosition(
      computeInitialPosition(
        trigger.getBoundingClientRect(),
        align,
        effectiveMinWidth,
      ),
    );
    setIsPositionReady(true);
  }, [isOpen, align, triggerRef, effectiveMinWidth]);

  useEffect(() => {
    if (!isOpen) return;

    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === "Escape") onCloseRef.current();
    };
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node;
      if (
        !triggerRef.current?.contains(target) &&
        !contentRef.current?.contains(target)
      ) {
        onCloseRef.current();
      }
    };

    document.addEventListener("keydown", handleEscape);
    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("keydown", handleEscape);
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [isOpen, triggerRef]);

  useEffect(() => {
    if (
      !isOpen ||
      !isPositionReady ||
      !triggerRef.current ||
      !contentRef.current
    ) {
      return;
    }

    const trigger = triggerRef.current;
    const content = contentRef.current;

    const run = () => {
      const next = computeRefinedPosition(
        trigger,
        content,
        align,
        maxHeight,
        effectiveMinWidth,
      );
      if (next) setPortalPosition(next);
    };

    const rafId = requestAnimationFrame(() => {
      requestAnimationFrame(run);
    });

    const handleResize = () => requestAnimationFrame(run);
    const handleScroll = () => requestAnimationFrame(run);

    window.addEventListener("resize", handleResize);
    window.addEventListener("scroll", handleScroll, true);

    return () => {
      cancelAnimationFrame(rafId);
      window.removeEventListener("resize", handleResize);
      window.removeEventListener("scroll", handleScroll, true);
    };
  }, [
    isOpen,
    isPositionReady,
    align,
    maxHeight,
    triggerRef,
    effectiveMinWidth,
  ]);

  return { contentRef, portalPosition, isPositionReady };
}

export interface UseDropdownPortalSimpleOptions
  extends Omit<UseDropdownPortalOptions, "align"> {
  /** Minimum content width in px. Use 0 to match trigger width exactly. Default 250. */
  minWidth?: number;
}

export function useDropdownPortalSimple({
  triggerRef,
  isOpen,
  onClose,
  maxHeight = "300px",
  minWidth = MIN_CONTENT_WIDTH,
}: UseDropdownPortalSimpleOptions) {
  const contentRef = useRef<HTMLDivElement>(null);
  const [portalPosition, setPortalPosition] = useState<PortalPosition | null>(
    null,
  );
  const [isPositionReady, setIsPositionReady] = useState(false);

  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  const effectiveMinWidth = minWidth ?? MIN_CONTENT_WIDTH;

  useEffect(() => {
    if (!isOpen || !triggerRef.current) {
      setPortalPosition(null);
      setIsPositionReady(false);
      return;
    }
    const rect = triggerRef.current.getBoundingClientRect();
    const width = Math.max(rect.width, effectiveMinWidth);
    setPortalPosition({
      top: rect.bottom + GAP,
      left: rect.left,
      width,
    });
    setIsPositionReady(true);
  }, [isOpen, triggerRef, effectiveMinWidth]);

  useEffect(() => {
    if (!isOpen) return;
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === "Escape") onCloseRef.current();
    };
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node;
      if (
        !triggerRef.current?.contains(target) &&
        !contentRef.current?.contains(target)
      ) {
        onCloseRef.current();
      }
    };
    document.addEventListener("keydown", handleEscape);
    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("keydown", handleEscape);
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [isOpen, triggerRef]);

  const runRefined = useCallback(() => {
    const trigger = triggerRef.current;
    const content = contentRef.current;
    if (!isOpen || !trigger || !content) return;

    const viewportHeight = window.innerHeight;
    const viewportWidth = window.innerWidth;
    const triggerRect = trigger.getBoundingClientRect();
    const contentHeight = content.scrollHeight;
    const maxAllowed = parseInt(maxHeight, 10) || 300;
    const actualHeight = Math.min(contentHeight, maxAllowed);
    if (actualHeight <= 0) return;

    const spaceBelow = viewportHeight - triggerRect.bottom - PADDING;
    const spaceAbove = triggerRect.top - PADDING;
    // Prefer opening below; only open upward when content doesn't fit below but fits above.
    const shouldOpenUpward =
      spaceBelow < actualHeight + GAP && spaceAbove >= actualHeight + GAP;
    const top = shouldOpenUpward
      ? triggerRect.top - actualHeight - GAP
      : triggerRect.bottom + GAP;
    const minTop = PADDING;
    const maxTop = viewportHeight - actualHeight - PADDING;
    const finalTop = Math.max(minTop, Math.min(top, maxTop));
    const contentWidth = Math.max(triggerRect.width, effectiveMinWidth);
    const left = Math.max(
      PADDING,
      Math.min(triggerRect.left, viewportWidth - contentWidth - PADDING),
    );

    setPortalPosition({ top: finalTop, left, width: contentWidth });
  }, [isOpen, maxHeight, triggerRef, effectiveMinWidth]);

  useEffect(() => {
    if (!isOpen || !isPositionReady || !contentRef.current) return;

    const timeoutId = setTimeout(runRefined, 0);
    const handleResize = () => runRefined();
    const handleScroll = () => runRefined();

    window.addEventListener("resize", handleResize);
    window.addEventListener("scroll", handleScroll, true);

    return () => {
      clearTimeout(timeoutId);
      window.removeEventListener("resize", handleResize);
      window.removeEventListener("scroll", handleScroll, true);
    };
  }, [isOpen, isPositionReady, runRefined]);

  return { contentRef, portalPosition, isPositionReady };
}
