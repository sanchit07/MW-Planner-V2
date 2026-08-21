import { Button } from "@components/ui/Button";
import { Tooltip } from "@components/ui/Tooltip";
import { ChevronDown, ChevronRight } from "lucide-react";
import React from "react";

/**
 * Reusable cell renderers following Single Responsibility Principle
 */

/**
 * Tooltip header cell renderer
 */
interface TooltipHeaderProps {
  label: string;
  tooltip?: string;
  align?: "left" | "center" | "right";
  className?: string;
}

export const TooltipHeader: React.FC<TooltipHeaderProps> = ({
  label,
  tooltip,
  align = "left",
  className = "",
}) => {
  const content = tooltip ? (
    <Tooltip content={tooltip}>
      <span className="cursor-help">{label}</span>
    </Tooltip>
  ) : (
    label
  );

  return <div className={`text-${align} ${className}`}>{content}</div>;
};

/**
 * Selection checkbox cell renderer
 */
interface SelectionCellProps {
  checked: boolean;
  indeterminate?: boolean;
  disabled?: boolean;
  onChange: (checked: boolean) => void;
  testId?: string;
}

export const SelectionCell: React.FC<SelectionCellProps> = ({
  checked,
  indeterminate = false,
  disabled = false,
  onChange,
  testId,
}) => {
  return (
    <input
      type="checkbox"
      checked={checked}
      ref={(el) => {
        if (el) el.indeterminate = indeterminate;
      }}
      disabled={disabled}
      onChange={(e) => onChange(e.target.checked)}
      data-testid={testId}
      className="cursor-pointer"
    />
  );
};

/**
 * Expand/collapse button cell renderer
 */
interface ExpandCellProps {
  isExpanded: boolean;
  onToggle: () => void;
  testId?: string;
}

export const ExpandCell: React.FC<ExpandCellProps> = ({
  isExpanded,
  onToggle,
  testId,
}) => {
  return (
    <Button
      variant="ghost"
      size="sm"
      className="h-6 w-6 p-0"
      onClick={onToggle}
      data-testid={testId}
    >
      {isExpanded ? (
        <ChevronDown className="h-4 w-4" />
      ) : (
        <ChevronRight className="h-4 w-4" />
      )}
    </Button>
  );
};

// Import missing icons
