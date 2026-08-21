import {
  Dropdown,
  DropdownTrigger,
  DropdownContent,
  DropdownItem,
} from "@components/ui/Dropdown";
import { Input } from "@components/ui/Input";
import { Label } from "@components/ui/Label";
import { Switch } from "@components/ui/Switch";
import { Textarea } from "@components/ui/Textarea";
import { useTranslate } from "@tolgee/react";
import React from "react";

const FEE_TYPES = ["Percentage", "Fixed"] as const;
const BASED_ON_OPTIONS = ["Base Cost"] as const;

export interface CustomFeeFormData {
  feeName: string;
  type: "Percentage" | "Fixed";
  value: string;
  basedOn: "Base Cost";
  description: string;
  includeInMediaPlan: boolean;
}

export interface CustomFeeFormErrors {
  feeName?: string;
  value?: string;
}

interface CustomFeeFormProps {
  // Form data
  feeName: string;
  type: "Percentage" | "Fixed";
  value: string;
  basedOn: "Base Cost";
  description: string;
  includeInMediaPlan: boolean;

  // Change handlers
  onFeeNameChange: (value: string) => void;
  onTypeChange: (value: "Percentage" | "Fixed") => void;
  onValueChange: (value: string) => void;
  onBasedOnChange: (value: "Base Cost") => void;
  onDescriptionChange: (value: string) => void;
  onIncludeInMediaPlanChange: (checked: boolean) => void;

  // Optional props
  disabled?: boolean;
  readOnly?: boolean;
  errors?: CustomFeeFormErrors;
  showRequired?: boolean;
  translationNamespace?: string;
  idPrefix?: string;
}

export const CustomFeeForm: React.FC<CustomFeeFormProps> = ({
  feeName,
  type,
  value,
  basedOn,
  description,
  includeInMediaPlan,
  onFeeNameChange,
  onTypeChange,
  onValueChange,
  onBasedOnChange,
  onDescriptionChange,
  onIncludeInMediaPlanChange,
  disabled = false,
  readOnly = false,
  errors,
  showRequired = false,
  translationNamespace = "price",
  idPrefix = "fee",
}) => {
  const { t } = useTranslate([translationNamespace]);
  const isDisabled = disabled || readOnly;
  const getTranslationKey = (key: string): string => {
    if (translationNamespace === "price") {
      return `drawers.pricing_summary.${key}`;
    }
    return `drawers.add_custom_fee.${key}`;
  };

  return (
    <div className="grid grid-cols-1 gap-4">
      {/* Fee Name */}
      <div>
        <Label htmlFor={`${idPrefix}-name`} required={showRequired}>
          {t(getTranslationKey("fee_name"), {
            defaultValue: "Fee Name",
          })}
        </Label>
        <Input
          id={`${idPrefix}-name`}
          type="text"
          placeholder={t(getTranslationKey("fee_name_placeholder"), {
            defaultValue: "Enter Fee Name",
          })}
          value={feeName}
          onChange={(e) => onFeeNameChange(e.target.value)}
          disabled={isDisabled}
          readOnly={readOnly}
          className={errors?.feeName ? "border-red-500" : ""}
        />
        {errors?.feeName && (
          <p className="text-xs text-red-500 mt-1">{errors.feeName}</p>
        )}
      </div>

      {/* Type and Value */}
      <div className="grid grid-cols-2 gap-4">
        <div>
          <Label htmlFor={`${idPrefix}-type`} required={false}>
            {t(getTranslationKey("type"), {
              defaultValue: "Type",
            })}
          </Label>
          <Dropdown
            value={type}
            onChange={(selectedType) => {
              if (!isDisabled) {
                onTypeChange(selectedType as "Percentage" | "Fixed");
              }
            }}
            className={isDisabled ? "pointer-events-none opacity-60" : ""}
          >
            <DropdownTrigger className="w-full">
              <div className="flex items-center justify-between w-full">
                <span className="text-sm font-normal text-mw-neutral-900 dark:text-white">
                  {t(
                    `${getTranslationKey("fee_types")}.${type.toLowerCase()}`,
                    { defaultValue: type },
                  )}
                </span>
              </div>
            </DropdownTrigger>
            <DropdownContent align="left">
              {FEE_TYPES.map((feeType) => (
                <DropdownItem key={feeType} value={feeType}>
                  {t(
                    `${getTranslationKey("fee_types")}.${feeType.toLowerCase()}`,
                    { defaultValue: feeType },
                  )}
                </DropdownItem>
              ))}
            </DropdownContent>
          </Dropdown>
        </div>
        <div>
          <Label htmlFor={`${idPrefix}-value`} required={showRequired}>
            {t(getTranslationKey("value"), {
              defaultValue: "Value",
            })}
          </Label>
          <Input
            id={`${idPrefix}-value`}
            type="number"
            placeholder={t(getTranslationKey("value_placeholder"), {
              defaultValue: "Enter Value",
            })}
            value={value}
            onChange={(e) => {
              const raw = e.target.value;
              if (
                type === "Percentage" &&
                raw !== "" &&
                !e.target.validity.badInput
              ) {
                const num = parseFloat(raw);
                if (!Number.isNaN(num) && num > 100) {
                  return;
                }
              }
              onValueChange(raw);
            }}
            disabled={isDisabled}
            readOnly={readOnly}
            className={errors?.value ? "border-red-500" : ""}
            min={0}
            max={type === "Percentage" ? 100 : undefined}
            step="0.01"
            onKeyDown={(e) => {
              // Prevent 'e', 'E', '+', '-' keys
              if (["e", "E", "+", "-"].includes(e.key)) {
                e.preventDefault();
              }
            }}
          />
          {errors?.value && (
            <p className="text-xs text-red-500 mt-1">{errors.value}</p>
          )}
        </div>
      </div>

      {/* Based On */}
      <div>
        <Label htmlFor={`${idPrefix}-based-on`} required={false}>
          {t(getTranslationKey("based_on"), {
            defaultValue: "Based On",
          })}
        </Label>
        <Dropdown
          value={basedOn}
          onChange={(val) => !isDisabled && onBasedOnChange(val as "Base Cost")}
          className={isDisabled ? "pointer-events-none opacity-60" : ""}
        >
          <DropdownTrigger className="w-full">
            <div className="flex items-center justify-between w-full">
              <span className="text-sm font-normal text-mw-neutral-900 dark:text-white">
                {t(
                  `${getTranslationKey("based_on_options")}.${basedOn.toLowerCase().split(" ").join("_")}`,
                  { defaultValue: basedOn },
                )}
              </span>
            </div>
          </DropdownTrigger>
          <DropdownContent align="left">
            {BASED_ON_OPTIONS.map((option) => {
              const key = option.toLowerCase().split(" ").join("_");
              return (
                <DropdownItem key={option} value={option}>
                  {t(`${getTranslationKey("based_on_options")}.${key}`, {
                    defaultValue: option,
                  })}
                </DropdownItem>
              );
            })}
          </DropdownContent>
        </Dropdown>
      </div>

      {/* Description */}
      <div>
        <Label htmlFor={`${idPrefix}-description`} required={false}>
          {t(getTranslationKey("fee_description"), {
            defaultValue: "Description",
          })}
        </Label>
        <Textarea
          id={`${idPrefix}-description`}
          placeholder={t(getTranslationKey("fee_description_placeholder"), {
            defaultValue: "Enter description (optional)",
          })}
          value={description}
          onChange={(e) => onDescriptionChange(e.target.value)}
          rows={3}
          disabled={isDisabled}
          readOnly={readOnly}
        />
      </div>

      {/* Include in Media Plan */}
      <div className="flex items-center justify-between">
        <Label htmlFor={`${idPrefix}-include`} required={false}>
          {t(getTranslationKey("include_in_media_plan"), {
            defaultValue: "Include in Media Plan",
          })}
        </Label>
        <Switch
          id={`${idPrefix}-include`}
          checked={includeInMediaPlan}
          onChange={onIncludeInMediaPlanChange}
          disabled={isDisabled}
        />
      </div>
    </div>
  );
};
