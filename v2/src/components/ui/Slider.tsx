import clsx from "clsx";
import { useState } from "react";

import { Label } from "./Label";

export interface SliderProps
  extends Omit<
    React.InputHTMLAttributes<HTMLInputElement>,
    "type" | "onChange" | "onMouseUp"
  > {
  label?: string;
  min?: number;
  max?: number;
  step?: number;
  value?: number;
  defaultValue?: number;
  onChange?: (value: number) => void;
  onMouseUp?: (value: number) => void;
  showValue?: boolean;
  labelClassName?: string;
  valueClassName?: string;
  formatValue?: (value: number) => string;
}

export function Slider({
  className,
  labelClassName,
  valueClassName,
  label,
  min = 0,
  max = 100,
  step = 1,
  value,
  defaultValue = min,
  onChange,
  onMouseUp,
  showValue = false,
  formatValue = (val) => val.toString(),
  disabled,
  ...props
}: SliderProps) {
  const [internalValue, setInternalValue] = useState(
    value ? (defaultValue ? defaultValue : min) : min,
  );
  const currentValue = value !== undefined ? value : internalValue;

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = Number(e.target.value);
    if (value === undefined) {
      setInternalValue(newValue);
    }
    onChange?.(newValue);
  };

  const handleMouseUp = () => {
    onMouseUp?.(internalValue);
  };

  const percentage = ((currentValue - min) / (max - min)) * 100;

  return (
    <div className="space-y-1">
      {(label || showValue) && (
        <div className="flex justify-between items-center">
          {label && <Label className={clsx(labelClassName)}>{label}</Label>}
          {showValue && (
            <Label className={clsx(valueClassName)}>
              {formatValue ? formatValue(currentValue) : currentValue}
            </Label>
          )}
        </div>
      )}
      <div className="relative">
        <input
          type="range"
          min={min}
          max={max}
          step={step}
          value={currentValue}
          onChange={handleChange}
          onMouseUp={handleMouseUp}
          disabled={disabled}
          className={clsx(
            "w-full h-2.25 bg-mw-neutral-50 dark:bg-mw-gray-700 rounded-lg appearance-none cursor-pointer",
            "disabled:opacity-50 disabled:cursor-not-allowed",
            "[&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-4 [&::-webkit-slider-thumb]:h-4",
            "[&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-white",
            "[&::-webkit-slider-thumb]:cursor-pointer",
            "[&::-webkit-slider-thumb]:border-1 [&::-webkit-slider-thumb]:border-mw-primary-500",
            "[&::-moz-range-thumb]:w-4 [&::-moz-range-thumb]:h-4 [&::-moz-range-thumb]:rounded-full",
            "[&::-moz-range-thumb]:bg-white [&::-moz-range-thumb]:cursor-pointer",
            "[&::-moz-range-thumb]:border-1 [&::-moz-range-thumb]:border-mw-primary-500",
            className,
          )}
          style={{
            background: `linear-gradient(to right, #1d65af 0%, #1d65af ${percentage}%, #f3f3f3 ${percentage}%, #f3f3f3 100%)`,
          }}
          {...props}
        />
      </div>
    </div>
  );
}
