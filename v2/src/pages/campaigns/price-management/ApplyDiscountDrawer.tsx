import { Button } from "@components/ui/Button";
import { Input } from "@components/ui/Input";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { useAnnounce } from "@hooks/useAnnounce";
import { useApplyScheduleAdjustmentMutation } from "@services/inventory/inventorySlice";
import { useTranslate } from "@tolgee/react";
import React, { useState, useCallback } from "react";

interface TableDataItem {
  id: string;
  children?: Array<{
    id: string;
    parentId?: string;
    originalSchedule?: { id?: string };
  }>;
}

interface ApplyDiscountDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  selectedCount: number;
  campaignId?: string;
  selectedItems: Set<string>;
  tableData: TableDataItem[];
  onSuccess?: () => void;
}

export const ApplyDiscountDrawer: React.FC<ApplyDiscountDrawerProps> = ({
  isOpen,
  onClose,
  selectedCount,
  campaignId,
  selectedItems,
  tableData,
  onSuccess,
}) => {
  const { t } = useTranslate(["price"]);
  const { t: tCommon } = useTranslate(["common"]);
  const { showSuccess, showError } = useAnnounce();
  const [applyScheduleAdjustment] = useApplyScheduleAdjustmentMutation();
  const [discount, setDiscount] = useState<string>("");
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

  const handleDiscountChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;

    // Check if value has more than 2 decimal places
    if (value.includes(".")) {
      const decimalPart = value.split(".")[1];
      if (decimalPart && decimalPart.length > 2) {
        // Prevent input if more than 2 decimal places
        return;
      }
    }

    setDiscount(value);
    setError("");

    // Validate discount value
    if (value) {
      const numValue = parseFloat(value);
      if (isNaN(numValue) || numValue < 0 || numValue > 100) {
        setError(t("drawers.apply_discount.error_invalid"));
      }
    }
  };

  const handleApply = async () => {
    if (!discount || discount.trim() === "") {
      setError(t("drawers.apply_discount.error_required"));
      return;
    }

    const numValue = parseFloat(discount);
    if (isNaN(numValue) || numValue < 0 || numValue > 100) {
      setError(t("drawers.apply_discount.error_invalid"));
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
          actionType: "DISCOUNT",
          discount: {
            discountType: "PERCENTAGE",
            value: numValue.toString(),
          },
        },
      }).unwrap();

      // Show success message from API response
      const message =
        typeof response.data === "string"
          ? response.data
          : t("success.discount_applied", {
              defaultValue: "Discount applied successfully",
            });
      showSuccess(message);

      // Call success callback to refresh data
      onSuccess?.();
      handleClose();
    } catch (error) {
      const errorMessage =
        error && typeof error === "object" && "data" in error
          ? (error.data as { error?: { message?: string } })?.error?.message
          : t("errors.failed_to_apply_discount", {
              defaultValue: "Failed to apply discount",
            });
      showError(
        errorMessage ||
          t("errors.failed_to_apply_discount", {
            defaultValue: "Failed to apply discount",
          }),
      );
    }
  };

  const handleClose = () => {
    setDiscount("");
    setError("");
    onClose();
  };

  // Check if discount is valid and not empty
  const isApplyDisabled =
    !discount ||
    discount.trim() === "" ||
    isNaN(parseFloat(discount)) ||
    parseFloat(discount) < 0 ||
    parseFloat(discount) > 100;

  return (
    <ModalDrawer
      isOpen={isOpen}
      onClose={handleClose}
      title={t("drawers.apply_discount.title")}
      size="md"
      position="right"
      id="apply-discount-drawer"
      footer={
        <div className="flex justify-end gap-3">
          <Button
            id="apply-discount-drawer-reset-btn"
            variant="outline"
            className="text-mw-primary-500 outline-mw-primary-500"
            size="md"
            onClick={handleClose}
          >
            {tCommon("buttons.cancel")}
          </Button>
          <Button
            id="apply-discount-drawer-apply-btn"
            variant="primary"
            size="md"
            onClick={handleApply}
            disabled={isApplyDisabled}
          >
            {t("drawers.apply_discount.apply")}
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <p className="text-sm text-mw-neutral-600 dark:text-mw-neutral-300">
          {t("drawers.apply_discount.description", {
            count: selectedCount,
            item:
              selectedCount === 1
                ? t("drawers.apply_discount.item_singular")
                : t("drawers.apply_discount.item_plural"),
          })}
        </p>

        <div>
          <Input
            id="discount-input"
            label={t("drawers.apply_discount.discount_percentage")}
            placeholder={t("drawers.apply_discount.discount_placeholder")}
            value={discount}
            onChange={handleDiscountChange}
            error={error}
            required
            min={0}
            max={100}
            step="0.01"
            helpText={t("drawers.apply_discount.help_text")}
            onKeyDown={(e) => {
              // Prevent 'e', 'E', '+', '-' keys
              if (["e", "E", "+", "-"].includes(e.key)) {
                e.preventDefault();
              }
            }}
          />
        </div>
      </div>
    </ModalDrawer>
  );
};
