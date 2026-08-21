import { clsx } from "clsx";
import { forwardRef } from "react";

import { Tooltip } from "./Tooltip";

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?:
    | "primary"
    | "secondary"
    | "ghost"
    | "outline"
    | "destructive"
    | "flow";
  size?: "xsm" | "sm" | "md" | "lg" | "iconMd";
  children: React.ReactNode;
  tooltip?: React.ReactNode;
  tooltipPosition?: "top" | "bottom" | "left" | "right";
}

const buttonVariants = {
  primary:
    "bg-mw-primary-500 text-white hover:bg-mw-primary-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-mw-primary-500 hover:shadow-xl transition-all duration-300 hover:-translate-y-0.5",
  secondary:
    "bg-mw-secondary-600 text-white hover:bg-mw-secondary-700 focus:outline-none focus:ring-2 focus:ring-offset-2  focus:ring-mw-secondary-500 hover:shadow-xl transition-all duration-300 hover:-translate-y-0.5",
  flow: "bg-teal-600 text-white hover:bg-teal-700 focus:outline-none focus:ring-2 focus:ring-offset-2  focus:ring-teal-500 hover:shadow-xl transition-all duration-300 hover:-translate-y-0.5",
  ghost:
    "text-mw-neutral-600  dark:text-mw-neutral-300 hover:bg-mw-primary-50 hover:text-mw-primary-700 dark:hover:bg-mw-neutral-800 transition-all duration-300",
  outline:
    "outline outline-offset-[-1px] outline-mw-neutral-100 text-mw-neutral-500 hover:bg-mw-primary-50 hover:border-mw-primary-500 dark:border-mw-primary-600 dark:text-mw-primary-400 dark:hover:bg-mw-primary-900 focus:ring-mw-primary-500 transition-all duration-300",
  destructive:
    "bg-mw-error-600 text-white hover:bg-mw-error-700 focus:outline-none focus:ring-2 focus:ring-offset-2  focus:ring-mw-error-500 hover:shadow-xl transition-all duration-300 hover:-translate-y-0.5",
};

const buttonSizes = {
  xsm: "p-2",
  sm: "px-3 py-1.5 text-sm",
  md: "px-6 py-2 text-sm",
  lg: "px-6 py-2 text-base",
  iconMd: "size-9 p-2",
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      className,
      variant = "primary",
      size = "md",
      children,
      id,
      tooltip,
      tooltipPosition = "top",
      ...props
    },
    ref,
  ) => {
    const button = (
      <button
        ref={ref}
        id={id || `btn-${variant}-${size}`}
        className={clsx(
          "inline-flex items-center justify-center rounded-md font-normal transition-colors duration-200 leading-tight cursor-pointer",
          "disabled:opacity-50 disabled:cursor-not-allowed",
          buttonVariants[variant],
          buttonSizes[size],
          className,
        )}
        {...props}
      >
        {children}
      </button>
    );

    if (tooltip) {
      return (
        <Tooltip content={tooltip} position={tooltipPosition}>
          {button}
        </Tooltip>
      );
    }

    return button;
  },
);

Button.displayName = "Button";
