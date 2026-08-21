import { clsx } from "clsx";
import { forwardRef } from "react";

import { Label } from "./Label";

export interface InputProps
  extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helpText?: string;
  required?: boolean;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  (
    { className, label, error, helpText, required = false, id, ...props },
    ref,
  ) => {
    const inputId =
      id ||
      (props.name
        ? `input-${props.name}`
        : `input-${label?.toLowerCase().replace(/\s+/g, "-") || "field"}`);
    return (
      <div className="space-y-2">
        {label && (
          <Label htmlFor={inputId} required={required}>
            {label}
          </Label>
        )}
        <input
          ref={ref}
          id={inputId}
          className={clsx(
            "block w-full h-10 min-h-10 px-3 py-2 border rounded-md text-sm font-normal leading-none",
            "focus:outline-none focus:ring-1 focus:ring-mw-primary-500 focus:border-transparent",
            "hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700",
            "disabled:bg-mw-neutral-50 disabled:cursor-not-allowed",
            error
              ? "border-mw-error-300 text-mw-error-500 placeholder-mw-error-300 focus:ring-mw-error-500"
              : "border-mw-neutral-100 dark:border-mw-neutral-600 text-mw-neutral-500 placeholder-mw-neutral-500",
            "dark:bg-mw-neutral-800",
            className,
          )}
          {...props}
        />
        {error && (
          <p
            id={`${inputId}-error`}
            className="text-sm text-mw-error-500 dark:text-mw-error-400"
          >
            {error}
          </p>
        )}
        {helpText && !error && (
          <p
            id={`${inputId}-help`}
            className="text-sm text-mw-neutral-500 dark:text-mw-neutral-400"
          >
            {helpText}
          </p>
        )}
      </div>
    );
  },
);

Input.displayName = "Input";
