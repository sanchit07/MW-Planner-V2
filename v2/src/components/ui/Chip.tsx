import { useTranslate } from "@tolgee/react";
import { clsx } from "clsx";
import { X } from "lucide-react";
import React from "react";

interface ChipProps {
  children: React.ReactNode;
  variant?:
    | "default"
    | "primary"
    | "secondary"
    | "success"
    | "warning"
    | "error"
    | "outline";
  size?: "sm" | "md" | "lg";
  onRemove?: () => void;
  disabled?: boolean;
  clickable?: boolean;
  className?: string;
  onClick?: () => void;
  closeClassNames?: string;
}

const chipVariants = {
  default:
    "bg-mw-neutral-100 text-mw-neutral-800 dark:bg-mw-neutral-800 dark:text-mw-neutral-200",
  primary:
    "bg-mw-primary-100 text-mw-primary-800 dark:bg-mw-primary-900/30 dark:text-mw-primary-300",
  secondary:
    "bg-mw-secondary-100 text-mw-secondary-800 dark:bg-mw-secondary-900/30 dark:text-mw-secondary-300",
  success:
    "bg-success-100 text-success-800 dark:bg-success-900/30 dark:text-success-300",
  warning:
    "bg-warning-100 text-warning-800 dark:bg-warning-900/30 dark:text-warning-300",
  error: "bg-error-100 text-error-800 dark:bg-error-900/30 dark:text-error-300",
  outline: "border border-mw-neutral-300 text-black",
};

const chipSizes = {
  sm: "px-2 py-1 text-xs",
  md: "px-3 py-1.5 text-sm",
  lg: "px-4 py-2 text-base",
};

export function Chip({
  children,
  variant = "default",
  size = "md",
  onRemove,
  disabled = false,
  clickable = false,
  className,
  closeClassNames,
  onClick,
}: ChipProps) {
  const { t } = useTranslate(["common"]);
  const isInteractive = clickable || onClick;

  return (
    <span
      className={clsx(
        "inline-flex items-center gap-1 rounded-full font-medium transition-colors duration-200",
        chipVariants[variant],
        chipSizes[size],
        isInteractive && !disabled && "cursor-pointer hover:opacity-80",
        disabled && "opacity-50 cursor-not-allowed",
        className,
      )}
      onClick={!disabled && onClick ? onClick : undefined}
    >
      {children}
      {onRemove && (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            if (!disabled) {
              onRemove();
            }
          }}
          disabled={disabled}
          className={clsx(
            "ml-1 rounded-full p-0.5 hover:bg-black/10 dark:hover:bg-white/10 transition-colors duration-200",
            disabled &&
              "cursor-not-allowed hover:bg-transparent dark:hover:bg-transparent",
          )}
          aria-label={t("aria.remove")}
        >
          <X
            className={clsx(
              size === "sm"
                ? "w-3 h-3"
                : size === "md"
                  ? "w-3.5 h-3.5"
                  : "w-4 h-4",
              closeClassNames,
            )}
          />
        </button>
      )}
    </span>
  );
}

// Alternative name for consistency with some design systems
export const Tag = Chip;
