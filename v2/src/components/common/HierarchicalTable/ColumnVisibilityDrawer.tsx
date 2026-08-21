import { Button } from "@components/ui/Button";
import { Label } from "@components/ui/Label";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { Switch } from "@components/ui/Switch";
import { useTranslate } from "@tolgee/react";
import React, { useState, useEffect } from "react";

export interface ColumnVisibility {
  [key: string]: boolean;
}

interface ColumnVisibilityDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  columnVisibility: ColumnVisibility;
  onColumnVisibilityChange: (columnVisibility: ColumnVisibility) => void;
  availableColumns: Array<{
    key: string;
    label: string;
  }>;
  id?: string;
}

const ColumnVisibilityDrawer: React.FC<ColumnVisibilityDrawerProps> = ({
  isOpen,
  onClose,
  columnVisibility,
  onColumnVisibilityChange,
  availableColumns,
  id,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);

  // Local state for column visibility changes (not applied until Apply is clicked)
  const [localColumnVisibility, setLocalColumnVisibility] =
    useState<ColumnVisibility>(columnVisibility);
  const [validationError, setValidationError] = useState<string | null>(null);

  // Initialize local state when drawer opens or columnVisibility prop changes
  useEffect(() => {
    if (isOpen) {
      setLocalColumnVisibility(columnVisibility);
      setValidationError(null);
    }
  }, [isOpen, columnVisibility]);

  const handleToggle = (columnKey: string, checked: boolean) => {
    setValidationError(null);
    setLocalColumnVisibility((prev) => ({
      ...prev,
      [columnKey]: checked,
    }));
  };

  // Check if all columns are enabled (based on local state)
  const areAllColumnsEnabled = () => {
    return availableColumns.every(
      (column) => localColumnVisibility[column.key] ?? true,
    );
  };

  // Handle enable/disable all fields (updates local state)
  const handleEnableAllToggle = (checked: boolean) => {
    setValidationError(null);
    const updatedVisibility = availableColumns.reduce((acc, col) => {
      acc[col.key] = checked;
      return acc;
    }, {} as ColumnVisibility);
    setLocalColumnVisibility(updatedVisibility);
  };

  const handleCancel = () => {
    // Reset to original column visibility
    setLocalColumnVisibility(columnVisibility);
    onClose();
  };

  const handleApplyChanges = () => {
    const visibleCount = availableColumns.filter(
      (column) => localColumnVisibility[column.key] ?? true,
    ).length;
    if (visibleCount === 0) {
      setValidationError(
        tCampaigns("column_customization.at_least_one_visible_required"),
      );
      return;
    }
    setValidationError(null);
    onColumnVisibilityChange(localColumnVisibility);
    onClose();
  };

  const drawerId = id || "column-visibility-drawer";
  return (
    <ModalDrawer
      id={drawerId}
      isOpen={isOpen}
      onClose={handleCancel}
      title={tCampaigns("column_customization.title")}
      position="right"
      size="md"
      footer={
        <div id={`${drawerId}-footer`} className="flex justify-end gap-3">
          <Button
            id={`${drawerId}-cancel-btn`}
            variant="outline"
            className="text-mw-primary-500 outline-mw-primary-500"
            size="md"
            onClick={handleCancel}
          >
            {tCampaigns("column_customization.cancel")}
          </Button>
          <Button
            id={`${drawerId}-apply-btn`}
            variant="primary"
            size="md"
            onClick={handleApplyChanges}
          >
            {tCampaigns("column_customization.apply")}
          </Button>
        </div>
      }
    >
      <div id={`${drawerId}-content`} className="space-y-4">
        {validationError && (
          <div
            id={`${drawerId}-validation-error`}
            role="alert"
            className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-800"
          >
            {validationError}
          </div>
        )}
        <div className="space-y-1">
          <div
            id={`${drawerId}-enable-all`}
            className="flex items-center justify-between py-2"
          >
            <Label
              htmlFor={`${drawerId}-enable-all-switch`}
              className="font-medium text-base leading-tight"
            >
              {tCampaigns("column_customization.enable_all")}
            </Label>
            <Switch
              id={`${drawerId}-enable-all-switch`}
              checked={areAllColumnsEnabled()}
              onChange={handleEnableAllToggle}
              size="md"
            />
          </div>
        </div>
        {/* Column List - Scrollable */}
        <div id={`${drawerId}-column-list`} className="space-y-1">
          {availableColumns.map((column) => (
            <div
              key={column.key}
              id={`${drawerId}-column-${column.key}`}
              className="flex items-center justify-between py-2"
            >
              <Label htmlFor={`${drawerId}-column-${column.key}-switch`}>
                {column.label}
              </Label>
              <Switch
                id={`${drawerId}-column-${column.key}-switch`}
                checked={localColumnVisibility[column.key] ?? true}
                onChange={(checked) => handleToggle(column.key, checked)}
                size="md"
              />
            </div>
          ))}
        </div>
      </div>
    </ModalDrawer>
  );
};

export default ColumnVisibilityDrawer;
