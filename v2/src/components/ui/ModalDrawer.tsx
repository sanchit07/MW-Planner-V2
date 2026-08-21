import { clsx } from "clsx";
import { ArrowLeft, X } from "lucide-react";
import React, { useEffect, useState } from "react";

export interface ModalDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  onBack?: () => void;
  title?: React.ReactNode;
  children: React.ReactNode;
  footer?: React.ReactNode;
  position?: "left" | "right";
  size?: "sm" | "md" | "lg" | "xl" | "2xl" | "3xl" | "4xl" | "custom";
  customWidth?: string; // Custom width when size is "custom"
  showCloseButton?: boolean;
  showBackButton?: boolean;
  id?: string;
}

const drawerSizes = {
  sm: "max-w-sm",
  md: "max-w-md",
  lg: "max-w-lg",
  xl: "max-w-xl",
  "2xl": "max-w-2xl",
  "3xl": "max-w-3xl",
  "4xl": "max-w-4xl",
};

export const ModalDrawer: React.FC<ModalDrawerProps> = ({
  isOpen,
  onClose,
  onBack,
  title,
  children,
  footer,
  position = "right",
  size = "md",
  customWidth,
  showCloseButton = true,
  showBackButton = true,
  id,
}) => {
  // mounted: keeps DOM alive during slide-out; visible: drives the CSS transition
  const [mounted, setMounted] = useState(isOpen);
  const [visible, setVisible] = useState(isOpen);

  useEffect(() => {
    if (isOpen) {
      setMounted(true);
      // Two rAFs ensure the element is in the DOM before the transition starts
      let raf2: number;
      const raf1 = requestAnimationFrame(() => {
        raf2 = requestAnimationFrame(() => setVisible(true));
      });
      return () => {
        cancelAnimationFrame(raf1);
        cancelAnimationFrame(raf2);
      };
    } else {
      setVisible(false);
      const timer = setTimeout(() => setMounted(false), 300);
      return () => clearTimeout(timer);
    }
  }, [isOpen]);

  useEffect(() => {
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape" && isOpen) onClose();
    };
    if (mounted) {
      document.addEventListener("keydown", handleEscape);
      document.body.style.overflow = isOpen ? "hidden" : "";
    }
    return () => {
      document.removeEventListener("keydown", handleEscape);
      if (!isOpen) document.body.style.overflow = "";
    };
  }, [isOpen, onClose, mounted]);

  if (!mounted) return null;

  const drawerId = id || "modal-drawer";

  return (
    <div id={drawerId} className="fixed inset-0 z-50 overflow-hidden">
      {/* Backdrop */}
      <div
        id={`${drawerId}-backdrop`}
        className={clsx(
          "fixed inset-0 transition-all duration-300 cursor-pointer",
          visible
            ? "backdrop-brightness-50 bg-mw-neutral-100/10"
            : "opacity-0 pointer-events-none",
        )}
        onClick={onClose}
      />

      {/* Drawer container — pointer-events-none so outside clicks reach backdrop */}
      <div
        id={`${drawerId}-container`}
        className="fixed inset-0 overflow-hidden pointer-events-none"
      >
        <div
          className={clsx(
            "absolute inset-y-0 flex max-w-full pointer-events-none",
            position === "right" ? "right-0" : "left-0",
          )}
        >
          <div
            id={`${drawerId}-panel`}
            className={clsx(
              "relative w-screen transform transition-all duration-300 ease-out pointer-events-auto",
              size === "custom" && customWidth
                ? ""
                : drawerSizes[size as keyof typeof drawerSizes],
              visible
                ? "translate-x-0 opacity-100"
                : position === "right"
                  ? "translate-x-full opacity-0"
                  : "-translate-x-full opacity-0",
            )}
            style={
              size === "custom" && customWidth
                ? { maxWidth: customWidth }
                : undefined
            }
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex h-full flex-col bg-white dark:bg-mw-neutral-800 shadow-2xl">
              {/* Header */}
              <div
                id={`${drawerId}-header`}
                className="self-stretch inline-flex justify-start items-center px-4 py-4 border-b border-mw-neutral-100 dark:border-mw-neutral-700"
              >
                <div className="flex-1 inline-flex justify-start gap-2">
                  {showBackButton && (
                    <button
                      onClick={onBack ?? onClose}
                      className="text-black transition-colors cursor-pointer"
                    >
                      <ArrowLeft className="w-5 h-5" />
                    </button>
                  )}
                  {title && (
                    <h4
                      id={`${drawerId}-title`}
                      className="text-xl font-medium text-mw-neutral-800 dark:text-white leading-8"
                    >
                      {title}
                    </h4>
                  )}
                </div>
                <div className="text-center justify-start">
                  {showCloseButton && (
                    <button
                      id={`${drawerId}-close`}
                      onClick={onClose}
                      className="text-black dark:text-mw-neutral-300 hover:text-mw-neutral-600 dark:hover:text-white transition-colors cursor-pointer justify-end"
                    >
                      <X className="w-5 h-5" />
                    </button>
                  )}
                </div>
              </div>

              {/* Content */}
              <div
                id={`${drawerId}-content`}
                className="flex-1 overflow-y-auto scrollbar-thin"
              >
                <div className="p-4 h-full">{children}</div>
              </div>

              {/* Footer */}
              {footer && (
                <div
                  id={`${drawerId}-footer`}
                  className="border-t border-mw-neutral-100 dark:border-mw-neutral-700 px-4 py-4 bg-white dark:bg-mw-neutral-800"
                >
                  {footer}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ModalDrawer;
