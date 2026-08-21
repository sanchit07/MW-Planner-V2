import { Button } from "@components/ui/Button";
import { Card, CardContent, CardHeader } from "@components/ui/card";
import { clsx } from "clsx";
import { ChevronDown, ChevronUp } from "lucide-react";
import React, { useState } from "react";

export interface AccordionProps {
  title: string;
  children: React.ReactNode;
  defaultExpanded?: boolean;
  headerActions?: React.ReactNode;
  className?: string;
  headerClassName?: string;
  contentClassName?: string;
}

export const Accordion: React.FC<AccordionProps> = ({
  title,
  children,
  defaultExpanded = true,
  headerActions,
  className,
  headerClassName,
  contentClassName,
}) => {
  const [isExpanded, setIsExpanded] = useState(defaultExpanded);

  const toggleExpanded = () => {
    setIsExpanded(!isExpanded);
  };

  return (
    <Card className={clsx("overflow-hidden", className)}>
      <CardHeader
        className={clsx(
          "flex flex-row items-center justify-between border-b border-container-border p-4 pb-4 cursor-pointer",
          headerClassName,
        )}
        onClick={toggleExpanded}
      >
        <div className="flex items-center gap-2 flex-1">
          <p className="text-sm font-medium text-mw-neutral-900 dark:text-white">
            {title}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {headerActions && (
            <div onClick={(e) => e.stopPropagation()}>{headerActions}</div>
          )}
          <Button
            variant="ghost"
            size="iconMd"
            onClick={(e) => {
              e.stopPropagation();
              toggleExpanded();
            }}
            className="h-7 w-7"
            aria-label={isExpanded ? "Collapse" : "Expand"}
          >
            {isExpanded ? (
              <ChevronUp className="h-5 w-5 text-mw-neutral-500" />
            ) : (
              <ChevronDown className="h-5 w-5 text-mw-neutral-500" />
            )}
          </Button>
        </div>
      </CardHeader>

      {isExpanded && (
        <CardContent className={clsx("p-4 pt-4", contentClassName)}>
          {children}
        </CardContent>
      )}
    </Card>
  );
};
