import { AlertCircle, CheckCircle, Info, XCircle } from "lucide-react";
import React from "react";

export type AlertVariant = "info" | "success" | "warning" | "error";

export interface AlertProps {
  variant?: AlertVariant;
  children: React.ReactNode;
  className?: string;
  icon?: React.ReactNode;
  showIcon?: boolean;
}

const variantStyles: Record<AlertVariant, string> = {
  info: "bg-mw-info-50 border-mw-info-200 -m-4 text-mw-info-700",
  success: "bg-mw-success-50 border-mw-success-200 text-mw-success-700",
  warning: "bg-mw-warning-50 border-mw-warning-200 text-mw-warning-700",
  error: "bg-mw-error-50 border-mw-error-200 text-mw-error-700",
};

const variantIcons: Record<AlertVariant, React.ReactNode> = {
  info: <Info className="w-4 h-4 text-mw-info-500 flex-shrink-0" />,
  success: (
    <CheckCircle className="w-4 h-4 text-mw-success-500 flex-shrink-0" />
  ),
  warning: (
    <AlertCircle className="w-4 h-4 text-mw-warning-500 flex-shrink-0" />
  ),
  error: <XCircle className="w-4 h-4 text-mw-error-500 flex-shrink-0" />,
};

const Alert: React.FC<AlertProps> = ({
  variant = "info",
  children,
  className = "",
  icon,
  showIcon = true,
}) => {
  const iconToShow = icon || variantIcons[variant];

  return (
    <div
      className={`flex items-start gap-2 p-3 border rounded-lg ${variantStyles[variant]} ${className}`}
    >
      {showIcon && iconToShow}
      <div className="text-xs mt-0.5 font-normal leading-tight flex-1">
        {children}
      </div>
    </div>
  );
};

export default Alert;
