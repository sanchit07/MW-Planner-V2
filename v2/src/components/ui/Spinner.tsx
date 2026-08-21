import { clsx } from "clsx";
import React from "react";

export interface SpinnerProps extends React.HTMLAttributes<HTMLDivElement> {
  size?: "sm" | "md" | "lg" | "xl";
  variant?: "default" | "primary" | "white";
}

const spinnerSizes = {
  sm: "w-4 h-4",
  md: "w-6 h-6",
  lg: "w-8 h-8",
  xl: "w-12 h-12",
};

const spinnerVariants = {
  default: "text-mw-neutral-600 dark:text-white",
  primary: "text-mw-primary-600 dark:text-white",
  white: "text-white dark:text-mw-black",
};

export function Spinner({
  className,
  size = "md",
  variant = "default",
  ...props
}: SpinnerProps) {
  return (
    <div
      className={clsx(
        "inline-block animate-spin rounded-full border-2 border-solid border-current border-r-transparent align-[-0.125em] motion-reduce:animate-[spin_1.5s_linear_infinite]",
        spinnerSizes[size],
        spinnerVariants[variant],
        className,
      )}
      role="status"
      {...props}
    >
      <span className="sr-only">Loading...</span>
    </div>
  );
}

export interface LoadingProps extends React.HTMLAttributes<HTMLDivElement> {
  size?: "sm" | "md" | "lg" | "xl";
  variant?: "default" | "primary" | "white";
  text?: string;
  overlay?: boolean;
}

export function Loading({
  className,
  size = "md",
  variant = "default",
  text = "Loading...",
  overlay = false,
  ...props
}: LoadingProps) {
  const content = (
    <div
      className={clsx("flex flex-col items-center justify-center space-y-2")}
    >
      <Spinner size={size} variant={variant} />
      {text && (
        <p className={clsx("text-sm font-medium", spinnerVariants[variant])}>
          {text}
        </p>
      )}
    </div>
  );

  if (overlay) {
    return (
      <div
        className={clsx(
          "absolute inset-0 z-50 flex items-center justify-center",
          variant === "default" &&
            "bg-mw-neutral-50/50 dark:bg-mw-neutral-900/50",
          variant === "primary" && "bg-white/80 dark:bg-mw-primary-900/50",
          variant === "white" &&
            "bg-mw-neutral-50/50 dark:bg-mw-primary-900/50",
          className,
        )}
        {...props}
      >
        {content}
      </div>
    );
  }

  return (
    <div
      className={clsx("flex items-center justify-center p-4", className)}
      {...props}
    >
      {content}
    </div>
  );
}
