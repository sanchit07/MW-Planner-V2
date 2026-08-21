import { CustomFeeForm } from "@components/campaigns/CustomFeeForm";
import { Button } from "@components/ui/Button";
import { Label } from "@components/ui/Label";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { useAppSelector } from "@store";
import { useTranslate } from "@tolgee/react";
import React, { useState, useEffect, useCallback } from "react";
import { PriceSummaryCustomFee } from "src/types/inventory.types";

import { CustomFee } from "../price-management/PricingSummaryDrawer";

interface AddCustomFeeDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  campaignId?: string;
  initialFee?: PriceSummaryCustomFee | null;
  onSave?: (
    customFees: CustomFee[],
    requiresApproval: boolean,
    campaignId?: string,
  ) => Promise<void>;
}

export const AddCustomFeeDrawer: React.FC<AddCustomFeeDrawerProps> = ({
  isOpen,
  onClose,
  campaignId,
  initialFee,
  onSave,
}) => {
  const { t: tPrice } = useTranslate(["price"]);
  const { t: tCommon } = useTranslate(["common"]);
  const user = useAppSelector((s) => s.profile.profile);
  const [feeName, setFeeName] = useState("");
  const [type, setType] = useState<"Percentage" | "Fixed">("Percentage");
  const [value, setValue] = useState("");
  const [basedOn, setBasedOn] = useState<"Base Cost">("Base Cost");
  const [description, setDescription] = useState("");
  const [includeInMediaPlan, setIncludeInMediaPlan] = useState(false);
  const [requiresApproval, setRequiresApproval] = useState(false);
  const [errors, setErrors] = useState<{ feeName?: string; value?: string }>(
    {},
  );

  // Get user's companyId
  const userCompanyId =
    user?.activeCompanyId || user?.memberships?.[0]?.company_id;

  // SonarQube: Extract duplicate state reset logic (S1192 - Duplicate code)
  const resetFormState = useCallback(() => {
    setFeeName("");
    setType("Percentage");
    setValue("");
    setBasedOn("Base Cost");
    setDescription("");
    setIncludeInMediaPlan(false);
    setRequiresApproval(false);
    setErrors({});
  }, []);

  // Reset state when drawer opens/closes or when initialFee changes
  useEffect(() => {
    if (!isOpen) {
      resetFormState();
    } else if (initialFee) {
      // Populate form with initial fee data for editing
      setFeeName(initialFee.name || "");
      setType(initialFee.type === "PERCENTAGE" ? "Percentage" : "Fixed");
      setValue(initialFee.value?.toString() || "");
      setBasedOn("Base Cost");
      setDescription(initialFee.description || "");
      setIncludeInMediaPlan(initialFee.isIncludeInMediaPlan || false);
      setRequiresApproval(false); // Reset approval checkbox
      setErrors({});
    } else {
      // Reset to defaults for new fee
      resetFormState();
    }
  }, [isOpen, initialFee, resetFormState]);

  const validate = (): boolean => {
    const newErrors: { feeName?: string; value?: string } = {};

    if (!feeName || feeName.trim() === "") {
      newErrors.feeName = tPrice("errors.fee_name_required", {
        defaultValue: "Fee name is a required field",
      });
    }

    if (!value || value.trim() === "") {
      newErrors.value = tPrice("errors.value_required", {
        defaultValue: "Value is a required field",
      });
    } else {
      const numValue = parseFloat(value);
      // SonarQube: Validate for NaN, negative numbers, and Infinity (S3776 - Cognitive complexity)
      if (isNaN(numValue) || numValue < 0 || !Number.isFinite(numValue)) {
        newErrors.value = tPrice("errors.invalid_value", {
          defaultValue: "Value must be a valid positive number",
        });
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSave = async () => {
    if (!validate()) {
      return;
    }

    if (!requiresApproval) {
      return;
    }

    if (onSave) {
      // For new fees: pass campaignId from prop
      // For edits: pass entire object with campaignId and companyId from initialFee
      const customFee: CustomFee = {
        id: initialFee?.id || `fee-${Date.now()}`,
        feeName,
        type,
        value,
        basedOn,
        description,
        includeInMediaPlan,
        // For new fees, use campaignId from prop; for edits, use from initialFee
        campaignId: initialFee?.campaignId || campaignId || "",
        // For new fees, use userCompanyId; for edits, use from initialFee
        companyId: initialFee?.companyId || userCompanyId || "",
      };

      try {
        await onSave([customFee], requiresApproval, campaignId);
        onClose();
      } catch (error) {
        // Error handling is done in parent component
        console.error("Error saving custom fee:", error);
      }
    } else {
      onClose();
    }
  };

  const handleClose = () => {
    resetFormState();
    onClose();
  };

  return (
    <ModalDrawer
      isOpen={isOpen}
      onClose={handleClose}
      title={
        initialFee
          ? tPrice("drawers.add_custom_fee.edit_title", {
              defaultValue: "Edit Custom Fee",
            })
          : tPrice("drawers.add_custom_fee.title")
      }
      size="lg"
      position="right"
      id="add-custom-fee-drawer"
      showBackButton={false}
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="outline" onClick={handleClose}>
            {tCommon("buttons.cancel")}
          </Button>
          <Button
            variant="primary"
            onClick={handleSave}
            disabled={!requiresApproval}
          >
            {initialFee
              ? tPrice("drawers.add_custom_fee.update_fee", {
                  defaultValue: "Update Fee",
                })
              : tPrice("drawers.add_custom_fee.add_fee", {
                  defaultValue: "Add Fee",
                })}
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <p className="text-sm font-normal text-mw-neutral-500 leading-4">
          {tPrice("drawers.add_custom_fee.description")}
        </p>

        <CustomFeeForm
          feeName={feeName}
          type={type}
          value={value}
          basedOn={basedOn}
          description={description}
          includeInMediaPlan={includeInMediaPlan}
          onFeeNameChange={(val) => {
            setFeeName(val);
            if (errors.feeName) {
              setErrors({ ...errors, feeName: undefined });
            }
          }}
          onTypeChange={setType}
          onValueChange={(val) => {
            setValue(val);
            if (errors.value) {
              setErrors({ ...errors, value: undefined });
            }
          }}
          onBasedOnChange={setBasedOn}
          onDescriptionChange={setDescription}
          onIncludeInMediaPlanChange={setIncludeInMediaPlan}
          errors={errors}
          showRequired={true}
          translationNamespace="price"
          idPrefix="fee"
        />

        {/* Approval Checkbox */}
        <div className="flex items-center gap-2 bg-mw-neutral-50 pl-4 p-2 rounded-sm">
          <input
            type="checkbox"
            id="campaign-approval-checkbox"
            checked={requiresApproval}
            onChange={(e) => setRequiresApproval(e.target.checked)}
            className="w-4 h-4 text-mw-primary-500 border-mw-neutral-300 rounded focus:ring-mw-primary-500"
          />
          <Label
            htmlFor="campaign-approval-checkbox"
            required={false}
            className="text-xs font-normal text-mw-neutral-700 dark:text-mw-neutral-300 cursor-pointer leading-4"
          >
            {tPrice("drawers.add_custom_fee.campaign_approval_required", {
              defaultValue:
                "I confirm that campaign approval is required for these changes",
            })}
          </Label>
        </div>
      </div>
    </ModalDrawer>
  );
};
