import { cn } from "@utils/tailwindMerge";
import { clsx } from "clsx";
import { useState, useRef, useEffect, useCallback } from "react";
import { createPortal } from "react-dom";

export interface TooltipProps {
  content: React.ReactNode;
  children: React.ReactNode;
  position?: "top" | "bottom" | "left" | "right";
  className?: string;
  triggerClassName?: string;
  offset?: number;
}

const GAP = 8; // Default gap between trigger and tooltip

export function Tooltip({
  content,
  children,
  position = "top",
  className,
  triggerClassName,
  offset = GAP,
}: TooltipProps) {
  const [isVisible, setIsVisible] = useState(false);
  const [tooltipPosition, setTooltipPosition] = useState<{
    top: number;
    left: number;
    actualPosition: "top" | "bottom" | "left" | "right";
  } | null>(null);
  const triggerRef = useRef<HTMLDivElement>(null);
  const tooltipRef = useRef<HTMLDivElement>(null);

  const calculatePosition = useCallback(() => {
    if (!triggerRef.current || !tooltipRef.current) return;

    const triggerRect = triggerRef.current.getBoundingClientRect();
    const tooltipRect = tooltipRef.current.getBoundingClientRect();
    const viewport = {
      width: window.innerWidth,
      height: window.innerHeight,
    };

    // Try positions in order of preference
    const positionsToTry: Array<"top" | "bottom" | "left" | "right"> = [
      position,
      ...(["top", "bottom", "left", "right"] as const).filter(
        (p) => p !== position,
      ),
    ];

    for (const pos of positionsToTry) {
      let top = 0;
      let left = 0;
      let fits = true;

      switch (pos) {
        case "top": {
          top = triggerRect.top - tooltipRect.height - offset;
          const centerX =
            triggerRect.left + triggerRect.width / 2 - tooltipRect.width / 2;
          left = centerX;
          // Check if tooltip fits above
          if (triggerRect.top - tooltipRect.height - offset < 0) {
            fits = false;
          }
          break;
        }
        case "bottom": {
          top = triggerRect.bottom + offset;
          const centerX =
            triggerRect.left + triggerRect.width / 2 - tooltipRect.width / 2;
          left = centerX;
          // Check if tooltip fits below
          if (
            triggerRect.bottom + tooltipRect.height + offset >
            viewport.height
          ) {
            fits = false;
          }
          break;
        }
        case "left": {
          const centerY =
            triggerRect.top + triggerRect.height / 2 - tooltipRect.height / 2;
          top = centerY;
          left = triggerRect.left - tooltipRect.width - offset;
          // Check if tooltip fits to the left
          if (triggerRect.left - tooltipRect.width - offset < 0) {
            fits = false;
          }
          break;
        }
        case "right": {
          const centerY =
            triggerRect.top + triggerRect.height / 2 - tooltipRect.height / 2;
          top = centerY;
          left = triggerRect.right + offset;
          // Check if tooltip fits to the right
          if (triggerRect.right + tooltipRect.width + offset > viewport.width) {
            fits = false;
          }
          break;
        }
      }

      // Adjust horizontal position to prevent overflow
      if (left < offset) {
        left = offset;
      } else if (left + tooltipRect.width > viewport.width - offset) {
        left = viewport.width - tooltipRect.width - offset;
      }

      // Adjust vertical position to prevent overflow
      if (top < offset) {
        top = offset;
      } else if (top + tooltipRect.height > viewport.height - offset) {
        top = viewport.height - tooltipRect.height - offset;
      }

      // If this position fits (or is the last option), use it
      if (fits || pos === positionsToTry[positionsToTry.length - 1]) {
        setTooltipPosition({
          top,
          left,
          actualPosition: pos,
        });
        return;
      }
    }
  }, [position, offset]);

  useEffect(() => {
    if (isVisible) {
      // First render tooltip off-screen to measure it
      if (!tooltipPosition) {
        // Set initial position off-screen to measure
        setTooltipPosition({
          top: -9999,
          left: -9999,
          actualPosition: position,
        });
      }

      // Calculate position after tooltip is rendered and measured
      const timeoutId = setTimeout(() => {
        calculatePosition();
      }, 0);

      // Recalculate on scroll and resize
      const handleReposition = () => {
        calculatePosition();
      };

      window.addEventListener("scroll", handleReposition, true);
      window.addEventListener("resize", handleReposition);

      return () => {
        clearTimeout(timeoutId);
        window.removeEventListener("scroll", handleReposition, true);
        window.removeEventListener("resize", handleReposition);
      };
    } else {
      setTooltipPosition(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isVisible, content, position, offset, calculatePosition]);

  return (
    <>
      <div
        ref={triggerRef}
        className={clsx("inline-flex cursor-help", triggerClassName)}
        onMouseEnter={() => setIsVisible(true)}
        onMouseLeave={() => setIsVisible(false)}
        onFocus={() => setIsVisible(true)}
        onBlur={() => setIsVisible(false)}
      >
        {children}
      </div>
      {isVisible &&
        createPortal(
          <div
            ref={tooltipRef}
            className={cn(
              "fixed z-9999 max-w-xs px-2 py-1 text-sm text-mw-neutral-500 bg-white border border-mw-neutral-100 rounded-md shadow-lg pointer-events-none whitespace-normal break-words",
              className,
            )}
            style={{
              top: tooltipPosition ? `${tooltipPosition.top}px` : "-9999px",
              left: tooltipPosition ? `${tooltipPosition.left}px` : "-9999px",
            }}
          >
            {content}
          </div>,
          document.body,
        )}
    </>
  );
}
