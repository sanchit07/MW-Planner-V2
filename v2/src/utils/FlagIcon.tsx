import * as Flags from "country-flag-icons/react/3x2";
import React from "react";

interface FlagIconProps {
  code: string;
  className?: string;
}

export const FlagIcon: React.FC<FlagIconProps> = ({
  code,
  className = "h-3 w-5",
}) => {
  const FlagComponent = Flags[code.toUpperCase() as keyof typeof Flags];
  if (!FlagComponent) return null;
  return <FlagComponent className={className} />;
};
