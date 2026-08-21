import { Badge } from "@components/ui/Badge";
import { Card, CardHeader } from "@components/ui/card";
import { Checkbox } from "@components/ui/Checkbox";
import { Tooltip } from "@components/ui/Tooltip";
import { useTranslate } from "@tolgee/react";
import { formatTime } from "@utils/optimization.utils";
import { extractOperationTimes, formatSize } from "@utils/schedule.utils";
import clsx from "clsx";
import { Clock, Trash2 as RemoveIcon, TriangleAlert } from "lucide-react";
import { ReactNode } from "react";
import {
  InventoryClassification,
  Environment,
} from "src/constants/inventory.constants";
import { InventoryItem } from "src/types/inventory.types";

export interface InventoryCardProps {
  item: InventoryItem;
  className?: string;
  onClick?: () => void;
  isSelected?: boolean;
  showCheckbox?: boolean;
  checkboxId?: string;
  checkboxChecked?: boolean;
  onCheckboxChange?: (checked: boolean) => void;
  headerClassName?: string;
  showAlertIcon?: boolean;
  alertTooltipContent?: ReactNode;
  alertIconClassName?: string;
  // Opt-in red remove button at the top-right corner of the card
  showRemoveButton?: boolean;
  onRemove?: () => void;
  removeButtonTitle?: string;
}

export const InventoryCard: React.FC<InventoryCardProps> = ({
  item,
  className,
  onClick,
  isSelected = false,
  showCheckbox = false,
  checkboxId,
  checkboxChecked = false,
  onCheckboxChange,
  headerClassName,
  showAlertIcon = false,
  alertTooltipContent,
  alertIconClassName,
  showRemoveButton = false,
  onRemove,
  removeButtonTitle,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { t: tCommon } = useTranslate(["common"]);
  const operationTimes = item.operations?.operatingTimes
    ? extractOperationTimes(item.operations.operatingTimes)
    : item.operations?.startTime && item.operations?.endTime
      ? {
          startTime: item.operations.startTime,
          endTime: item.operations.endTime,
        }
      : null;

  const cardContent = (
    <div className={clsx("space-y-2", showCheckbox && "flex-1")}>
      <div className="flex justify-between">
        <h3 className="text-xs font-semibold leading-4 truncate max-w-[250px]">
          {item.detail.name || ""}
        </h3>
        {showAlertIcon && (
          <Tooltip content={alertTooltipContent}>
            <TriangleAlert className={clsx("w-4 h-4", alertIconClassName)} />
          </Tooltip>
        )}
      </div>
      <div className="flex items-center gap-2">
        <div className="flex-1 flex flex-wrap gap-1.5">
          <Badge
            variant="outline"
            size="sm"
            className={`${item.detail.inventoryType === InventoryClassification.DIGITAL ? "outline-mw-secondary-500! text-mw-secondary-500!" : "outline-mw-neutral-500! text-mw-neutral-500!"}`}
          >
            {tCommon(
              `inventoryClassification.${item.detail.inventoryType.toLowerCase()}`,
            )}
          </Badge>
          {item.detail.format && (
            <Badge
              className="outline-mw-rose-warning-400! text-mw-rose-warning-400!"
              variant="outline"
              size="sm"
            >
              {item.detail.format}
            </Badge>
          )}
          {item.detail.environment && (
            <Badge
              variant="outline"
              size="sm"
              className={`${item.detail.environment === Environment.OUTDOOR ? "outline-mw-bluish-grey-500! text-mw-bluish-grey-500!" : "outline-mw-amaranth-500! text-mw-amaranth-500!"}`}
            >
              {tCommon(`inventoryEnvironment.${item.detail.environment}`)}
            </Badge>
          )}
          {item.detail.size &&
            (() => {
              const size = formatSize(item.detail.size);
              return (
                <Badge className={size.colorClass} variant="outline" size="sm">
                  {tCommon(
                    `inventorySize.${item.detail.size.toLowerCase()}.label`,
                  )}
                </Badge>
              );
            })()}
        </div>
        {operationTimes && (
          <div className="inline-flex items-center gap-1">
            <div className="text-xs text-mw-neutral-500 flex items-center gap-1">
              {formatTime(operationTimes.startTime)}{" "}
              {tCampaigns("inventoryCard.to")}{" "}
              {formatTime(operationTimes.endTime)}
            </div>
            <Clock className="w-4 h-4 text-mw-neutral-400" />
          </div>
        )}
      </div>
    </div>
  );

  return (
    <Card
      className={clsx(
        !showCheckbox &&
          "hover:bg-mw-primary-50 transition-colors cursor-pointer p-3",
        isSelected && !showCheckbox ? "bg-mw-primary-50!" : "bg-white",
        showRemoveButton && "relative",
        className,
      )}
      onClick={!showCheckbox ? onClick : undefined}
    >
      {showRemoveButton && (
        <button
          type="button"
          title={removeButtonTitle}
          aria-label={removeButtonTitle}
          onClick={(e) => {
            e.stopPropagation();
            onRemove?.();
          }}
          className="absolute top-1 right-1 z-10 rounded p-1 text-mw-error-500 hover:bg-mw-error-50"
        >
          <RemoveIcon className="h-4 w-4" />
        </button>
      )}
      <CardHeader className={headerClassName}>
        {showCheckbox ? (
          <div className="flex items-start gap-3">
            <Checkbox
              checked={checkboxChecked}
              onChange={(e) => onCheckboxChange?.(e.target.checked)}
              id={checkboxId}
            />
            {cardContent}
          </div>
        ) : (
          cardContent
        )}
      </CardHeader>
    </Card>
  );
};
