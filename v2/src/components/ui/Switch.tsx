import { clsx } from "clsx";
import React from "react";

export interface SwitchProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  size?: "sm" | "md" | "lg";
  className?: string;
  id?: string;
}

const switchSizes = {
  sm: {
    track: "w-8 h-4",
    thumb: "h-3 w-3 after:h-3 after:w-3",
    translate: "peer-checked:after:translate-x-4",
  },
  md: {
    track: "w-11 h-6",
    thumb: "h-5 w-5 after:h-5 after:w-5",
    translate: "peer-checked:after:translate-x-full",
  },
  lg: {
    track: "w-14 h-7",
    thumb: "h-6 w-6 after:h-6 after:w-6",
    translate: "peer-checked:after:translate-x-7",
  },
};

export const Switch: React.FC<SwitchProps> = ({
  checked,
  onChange,
  disabled = false,
  size = "md",
  className,
  id,
}) => {
  const sizeClasses = switchSizes[size];

  return (
    <label
      className={clsx(
        "relative inline-flex items-center cursor-pointer",
        disabled && "cursor-not-allowed opacity-50",
        className,
      )}
    >
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        disabled={disabled}
        className="sr-only peer"
        id={id}
      />
      <div
        className={clsx(
          "bg-mw-neutral-100 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[1.5px] after:left-[1.5px] after:bg-white after:border-mw-neutral-100 after:border after:rounded-full after:transition-all peer-checked:bg-mw-primary-500",
          sizeClasses.track,
          sizeClasses.thumb,
          sizeClasses.translate,
        )}
      />
    </label>
  );
};

export default Switch;
