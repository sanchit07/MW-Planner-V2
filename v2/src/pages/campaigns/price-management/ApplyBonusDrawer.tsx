import { Button } from "@components/ui/Button";
import {
  Dropdown,
  DropdownTrigger,
  DropdownContent,
  DropdownItem,
} from "@components/ui/Dropdown";
import { Label } from "@components/ui/Label";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { useAnnounce } from "@hooks/useAnnounce";
import { useApplyScheduleAdjustmentMutation } from "@services/inventory/inventorySlice";
import { useTranslate } from "@tolgee/react";
import { clsx } from "clsx";
import { Info, AlertCircle } from "lucide-react";
import React, { useState, useCallback } from "react";

interface TableDataItem {
  id: string;
  children?: Array<{
    id: string;
    parentId?: string;
    originalSchedule?: { id?: string };
  }>;
}

interface ApplyBonusDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  selectedCount: number;
  campaignId?: string;
  selectedItems: Set<string>;
  tableData: TableDataItem[];
  onSuccess?: () => void;
  bonusTypeOptions: { value: string; label: string }[];
}

export const ApplyBonusDrawer: React.FC<ApplyBonusDrawerProps> = ({
  isOpen,
  onClose,
  selectedCount,
  campaignId,
  selectedItems,
  tableData,
  onSuccess,
  bonusTypeOptions,
}) => {
  const { t } = useTranslate(["price"]);
  const { t: tCommon } = useTranslate(["common"]);
  const { showSuccess, showError } = useAnnounce();
  const [applyScheduleAdjustment] = useApplyScheduleAdjustmentMutation();
  const [selectedBonusType, setSelectedBonusType] = useState<string>("");
  const [error, setError] = useState<string>("");

  // Extract schedule IDs from selected items
  const getSelectedScheduleIds = useCallback((): string[] => {
    const scheduleIdsSet = new Set<string>();
    selectedItems.forEach((itemId) => {
      // If itemId contains ":", it's a schedule (format: "inventoryId:scheduleId")
      if (itemId.includes(":")) {
        const parts = itemId.split(":");
        const parentId = parts[0];
        const childId = parts[1];
        const inventoryItem = tableData.find((item) => item.id === parentId);
        const child = inventoryItem?.children?.find((c) => c.id === childId);
        // Use originalSchedule.id from API response
        if (
          child &&
          "originalSchedule" in child &&
          child.originalSchedule?.id
        ) {
          scheduleIdsSet.add(child.originalSchedule.id);
        }
      } else {
        // If it's a parent inventory, get all schedule IDs from that inventory
        const inventoryItem = tableData.find((item) => item.id === itemId);
        if (inventoryItem?.children) {
          inventoryItem.children.forEach((child) => {
            // Use originalSchedule.id from API response
            if ("originalSchedule" in child && child.originalSchedule?.id) {
              scheduleIdsSet.add(child.originalSchedule.id);
            }
          });
        }
      }
    });
    return Array.from(scheduleIdsSet);
  }, [selectedItems, tableData]);

  const handleBonusTypeChange = (value: string) => {
    setSelectedBonusType(value);
    setError("");
  };

  const handleApply = async () => {
    if (!selectedBonusType || selectedBonusType.trim() === "") {
      setError(t("drawers.apply_bonus.error_required"));
      return;
    }

    if (!campaignId) {
      showError(
        t("errors.campaign_id_required", {
          defaultValue: "Campaign ID is required",
        }),
      );
      return;
    }

    const scheduleIds = getSelectedScheduleIds();
    if (scheduleIds.length === 0) {
      showError(
        t("errors.no_schedules_selected", {
          defaultValue: "No schedules selected",
        }),
      );
      return;
    }

    try {
      const response = await applyScheduleAdjustment({
        campaignId,
        data: {
          scheduleIds,
          actionType: "BONUS",
          bonus: selectedBonusType,
        },
      }).unwrap();

      // Show success message from API response
      const message =
        typeof response.data === "string"
          ? response.data
          : t("success.bonus_applied", {
              defaultValue: "Bonus applied successfully",
            });
      showSuccess(message);

      // Call success callback to refresh data
      onSuccess?.();
      handleClose();
    } catch (error) {
      const errorMessage =
        error && typeof error === "object" && "data" in error
          ? (error.data as { error?: { message?: string } })?.error?.message
          : t("errors.failed_to_apply_bonus", {
              defaultValue: "Failed to apply bonus",
            });
      showError(
        errorMessage ||
          t("errors.failed_to_apply_bonus", {
            defaultValue: "Failed to apply bonus",
          }),
      );
    }
  };

  const handleClose = () => {
    setSelectedBonusType("");
    setError("");
    onClose();
  };

  const selectedLabel =
    bonusTypeOptions?.find((opt) => opt.value === selectedBonusType)?.label ||
    "";

  const isApplyDisabled = !selectedBonusType || selectedBonusType.trim() === "";

  return (
    <ModalDrawer
      isOpen={isOpen}
      onClose={handleClose}
      title={t("drawers.apply_bonus.title")}
      size="md"
      position="right"
      id="apply-bonus-drawer"
      footer={
        <div className="flex justify-end gap-3">
          <Button
            id="apply-bonus-drawer-reset-btn"
            variant="outline"
            className="text-mw-primary-500 outline-mw-primary-500"
            size="md"
            onClick={handleClose}
          >
            {tCommon("buttons.cancel")}
          </Button>
          <Button
            id="apply-bonus-drawer-apply-btn"
            variant="primary"
            size="md"
            onClick={handleApply}
            disabled={isApplyDisabled}
          >
            {t("drawers.apply_bonus.apply")}
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <p className="text-sm text-mw-neutral-600 dark:text-mw-neutral-300">
          {t("drawers.apply_bonus.description", {
            count: selectedCount,
            item:
              selectedCount === 1
                ? t("drawers.apply_bonus.item_singular")
                : t("drawers.apply_bonus.item_plural"),
          })}
        </p>

        <div className="space-y-2">
          <div className="inline-flex justify-start items-center gap-1">
            <Label htmlFor="bonus-type-dropdown" required>
              {t("drawers.apply_bonus.bonus_type")}
            </Label>
            <Info className="w-3.5 h-3.5 text-mw-neutral-400" />
          </div>

          <Dropdown
            value={selectedBonusType}
            onChange={handleBonusTypeChange}
            name="bonusType"
          >
            <DropdownTrigger
              className={clsx(
                "w-full justify-between",
                error &&
                  "border-mw-error-300 text-mw-error-500 placeholder-mw-error-300 focus:ring-mw-error-500",
              )}
              hasValue={!!selectedBonusType}
              clearable
              onClear={() => {
                setSelectedBonusType("");
                setError("");
              }}
            >
              {selectedLabel || t("drawers.apply_bonus.select_option")}
            </DropdownTrigger>
            <DropdownContent>
              {bonusTypeOptions.map((option) => (
                <DropdownItem
                  key={option.value}
                  value={option.value}
                  searchableText={option.label}
                >
                  {option.label}
                </DropdownItem>
              ))}
            </DropdownContent>
          </Dropdown>

          {error && (
            <div
              id="bonus-type-error"
              className="flex items-center gap-2 text-sm text-mw-error-500 dark:text-mw-error-400"
            >
              <AlertCircle className="w-4 h-4" />
              <span>{error}</span>
            </div>
          )}
        </div>
      </div>
    </ModalDrawer>
  );
};
