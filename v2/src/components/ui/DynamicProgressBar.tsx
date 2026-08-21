import { clsx } from "clsx";
import { Info } from "lucide-react";
import * as React from "react";

import { Tooltip } from "./Tooltip";

export interface DynamicProgressBarProps
  extends React.HTMLAttributes<HTMLDivElement> {
  value: number;
  maxValue?: number;
  variant?:
    | "default"
    | "success"
    | "warning"
    | "error"
    | "teal"
    | "lightGreen"
    | "orange";
  beyondColor?: string;
  label?: string;
  infoText?: string;
  size?: "sm" | "md" | "lg";
}

const progressSizes = {
  sm: "h-1",
  md: "h-1.5",
  lg: "h-2",
};

const progressVariants = {
  default: "bg-mw-primary-500",
  success: "bg-mw-success-500",
  warning: "bg-mw-warning-500",
  error: "bg-mw-error-500",
  teal: "bg-mw-teal-600",
  lightGreen: "bg-mw-light-green-600",
  orange: "bg-mw-orange-600",
};

export const DynamicProgressBar: React.FC<DynamicProgressBarProps> = ({
  className,
  value = 0,
  maxValue = 100,
  variant = "default",
  beyondColor = "bg-mw-success-500",
  label,
  infoText,
  size = "md",
  ...props
}) => {
  // Ensure value is within valid range
  const clampedValue = Math.max(0, Math.min(value, maxValue));
  const isBeyond100 = clampedValue > 100;
  const valueAt100 = Math.min(clampedValue, 100);
  const valueBeyond100 = isBeyond100 ? clampedValue - 100 : 0;

  // Calculate the total range (maxValue if > 100, otherwise 100)
  const totalRange = maxValue > 100 ? maxValue : 100;

  // Calculate the width of each segment relative to the total bar (as percentage)
  const widthAt100Percent = (100 / totalRange) * 100;

  // Calculate progress widths as percentage of total bar
  // Progress up to 100%: show actual value percentage if <= 100, or full 100% segment if > 100
  const progressWidthAt100Percent = isBeyond100
    ? widthAt100Percent // If beyond 100, show full 100% segment
    : (valueAt100 / totalRange) * 100; // If <= 100, show actual progress

  // Progress beyond 100%: only show if value > 100
  const progressWidthBeyond100Percent =
    isBeyond100 && valueBeyond100 > 0 ? (valueBeyond100 / totalRange) * 100 : 0;

  return (
    <div
      className={clsx(
        " inline-flex flex-col justify-center items-start gap-4",
        className,
      )}
      {...props}
    >
      {/* Label Section */}
      {label && (
        <div className="self-stretch flex flex-col justify-start items-start gap-3">
          <div className="self-stretch inline-flex justify-start items-center gap-2">
            <div className="flex-1 flex justify-start items-center gap-2">
              <div className="text-sm font-medium text-mw-neutral-700 leading-4">
                {label}
              </div>
              {infoText && (
                <Tooltip content={infoText} position="top">
                  <div className="w-4 h-4 relative overflow-hidden flex items-center justify-center cursor-pointer">
                    <Info className="w-3.5 h-3.5 text-mw-neutral-500" />
                  </div>
                </Tooltip>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Progress Bar Section */}
      <div className="self-stretch flex flex-col justify-start items-start gap-2">
        <div className="self-stretch inline-flex justify-start items-center gap-2">
          <div
            className={clsx(
              "flex-1 relative rounded-full overflow-hidden",
              progressSizes[size],
              "bg-mw-neutral-100",
            )}
          >
            {/* Progress segment up to 100% (default color) */}
            {valueAt100 > 0 && (
              <div
                className={clsx(
                  "absolute left-0 top-0 rounded-full",
                  progressSizes[size],
                  progressVariants[variant],
                )}
                style={{ width: `${progressWidthAt100Percent}%` }}
              />
            )}

            {/* Progress segment beyond 100% (beyond color) */}
            {isBeyond100 && valueBeyond100 > 0 && (
              <div
                className={clsx(
                  "absolute top-0 rounded-full",
                  progressSizes[size],
                  beyondColor,
                )}
                style={{
                  left: `${widthAt100Percent}%`,
                  width: `${progressWidthBeyond100Percent}%`,
                }}
              />
            )}
          </div>
        </div>

        {/* Percentage Labels */}
        <div className="self-stretch relative">
          <div className="absolute left-0 text-mw-neutral-900 text-sm font-normal leading-4">
            0%
          </div>
          <div
            className="absolute text-mw-neutral-900 text-sm font-normal leading-4"
            style={{
              left: `${widthAt100Percent}%`,
              transform: "translateX(-50%)",
            }}
          >
            100%
          </div>
          {maxValue > 100 && (
            <div className="absolute right-0 text-mw-neutral-900 text-sm font-normal leading-4">
              {maxValue}%
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
