import { clsx } from "clsx";
import { X } from "lucide-react";
import React, { useEffect } from "react";

import { Button } from "./Button";

export interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
  primaryButtonText?: string;
  secondaryButtonText?: string;
  onPrimaryAction?: () => void;
  onSecondaryAction?: () => void;
  showCloseButton?: boolean;
  primaryButtonVariant?: "default" | "danger";
  primaryButtonDisabled?: boolean;
  size?: "sm" | "md" | "lg" | "xl";
}

const modalSizes = {
  sm: "max-w-sm",
  md: "max-w-md",
  lg: "max-w-lg",
  xl: "max-w-xl",
};

export const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  title,
  children,
  primaryButtonText,
  secondaryButtonText,
  onPrimaryAction,
  onSecondaryAction,
  showCloseButton = true,
  primaryButtonVariant = "default",
  primaryButtonDisabled = false,
  size = "sm",
}) => {
  // Close modal on escape key
  useEffect(() => {
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape" && isOpen) {
        onClose();
      }
    };

    if (isOpen) {
      document.addEventListener("keydown", handleEscape);
      // Prevent body scroll when modal is open
      document.body.style.overflow = "hidden";
    }

    return () => {
      document.removeEventListener("keydown", handleEscape);
      document.body.style.overflow = "hidden";
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const handleSecondaryClick = () => {
    if (onSecondaryAction) {
      onSecondaryAction();
    } else {
      onClose();
    }
  };

  const handlePrimaryClick = () => {
    if (primaryButtonDisabled) return;
    if (onPrimaryAction) {
      onPrimaryAction();
    }
    onClose();
  };

  const modalId = `modal-${title.toLowerCase().replace(/\s+/g, "-")}`;

  return (
    <div id={modalId} className="fixed inset-0 z-50 overflow-hidden">
      {/* Backdrop */}
      <div
        id={`${modalId}-backdrop`}
        className={clsx(
          "fixed inset-0 transition-all duration-300 bg-black/50",
        )}
        onClick={onClose}
      />

      {/* Modal container - centered */}
      <div className="fixed inset-0 flex items-center justify-center p-4">
        <div
          id={`${modalId}-container`}
          className={clsx(
            "relative w-full transform transition-all duration-300 ease-in-out",
            modalSizes[size],
            "bg-white dark:bg-mw-neutral-800 rounded-lg shadow-xl",
            isOpen ? "scale-100 opacity-100" : "scale-95 opacity-0",
          )}
          onClick={(e) => e.stopPropagation()}
        >
          {/* Header */}
          <div
            id={`${modalId}-header`}
            className="flex items-center justify-between px-6 py-4 border-b border-mw-neutral-200 dark:border-mw-neutral-700"
          >
            <h3
              id={`${modalId}-title`}
              className="text-lg font-semibold text-mw-neutral-900 dark:text-white"
            >
              {title}
            </h3>
            {showCloseButton && (
              <button
                id={`${modalId}-close`}
                onClick={onClose}
                className="text-mw-neutral-500 hover:text-mw-neutral-700 dark:text-mw-neutral-400 dark:hover:text-mw-neutral-200 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            )}
          </div>

          {/* Content */}
          <div id={`${modalId}-content`} className="px-6 py-4">
            <div className="text-sm text-mw-neutral-600 dark:text-mw-neutral-300">
              {children}
            </div>
          </div>

          {/* Footer with Action Buttons */}
          {(primaryButtonText || secondaryButtonText) && (
            <div
              id={`${modalId}-footer`}
              className="flex items-center justify-end gap-3 px-6 py-4 border-t border-mw-neutral-200 dark:border-mw-neutral-700"
            >
              {secondaryButtonText && (
                <Button
                  id={`${modalId}-secondary-btn`}
                  onClick={handleSecondaryClick}
                  className="px-4 py-2 text-sm font-medium outline-mw-primary-500 text-mw-primary-500"
                  variant="outline"
                >
                  {secondaryButtonText}
                </Button>
              )}
              {primaryButtonText && (
                <Button
                  id={`${modalId}-primary-btn`}
                  onClick={handlePrimaryClick}
                  disabled={primaryButtonDisabled}
                  variant={
                    primaryButtonVariant === "danger"
                      ? "destructive"
                      : "primary"
                  }
                >
                  {primaryButtonText}
                </Button>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Modal;
