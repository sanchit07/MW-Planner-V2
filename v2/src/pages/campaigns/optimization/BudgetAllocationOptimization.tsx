import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import { Slider } from "@components/ui/Slider";
import { Tooltip } from "@components/ui/Tooltip";
import { OptimizationFormData } from "@schemas/campaigns/optimzation.schema";
import { useTranslate } from "@tolgee/react";
import { transformOptimizationAdjustHundredPercentShare } from "@utils/optimization.utils";
import { Calculator, Info } from "lucide-react";
import { useCallback } from "react";
import { Control, Controller } from "react-hook-form";

import { BudgetAllocation } from "../../../types/campaign.types";

const BUDGET_ALLOCATION_KEYS = [
  "digital",
  "transit",
  "classic",
  "retail",
] as const;

interface BudgetAllocationComponentProps {
  control: Control<OptimizationFormData>;
  onFieldChange: (value: Record<string, unknown>) => Promise<void>;
  budgetFormData: OptimizationFormData["budgetAllocation"];
  handleBudgetSchedulingFieldMouseUp: (
    saveFormKey: keyof OptimizationFormData,
  ) => Promise<void>;
}

const BudgetAllocationComponent = ({
  control,
  onFieldChange,
  budgetFormData,
  handleBudgetSchedulingFieldMouseUp,
}: BudgetAllocationComponentProps) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);

  const handleSliderChange = (
    value: number,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    field: any,
    currentKey: string,
  ) => {
    let currentValue = value;
    const otherValues = transformOptimizationAdjustHundredPercentShare(
      currentValue,
      field.value,
      budgetFormData,
      currentKey,
    );
    let valueToSave: Partial<BudgetAllocation> = {
      [currentKey]: currentValue,
    };
    if (otherValues && otherValues !== 100 && typeof otherValues === "object") {
      valueToSave = {
        ...valueToSave,
        ...otherValues,
      };
    } else if (otherValues === 100) {
      currentValue = otherValues;
    }
    field.onChange(currentValue);
    onFieldChange(valueToSave);
  };

  const onFieldMouseUp = useCallback(() => {
    handleBudgetSchedulingFieldMouseUp("budgetAllocation");
  }, [handleBudgetSchedulingFieldMouseUp]);

  return (
    <Card className="p-4 gap-6">
      <CardHeader>
        <div className="flex items-center gap-2 border-b border-mw-neutral-100 pb-2">
          <div className="w-10 h-10 bg-mw-brown-50 rounded-lg flex items-center justify-center">
            <Calculator className="w-5 h-5 text-mw-brown-600" />
          </div>
          <div className="flex-1 inline-flex flex-col justify-start items-start gap-1">
            <div className="self-stretch inline-flex justify-start items-start gap-2">
              <CardTitle className="text-sm font-medium leading-none">
                {tCampaigns("optimization.budgetAllocation.title")}
              </CardTitle>
            </div>
            <p className="text-sm font-normal text-mw-neutral-500 leading-none">
              {tCampaigns("optimization.budgetAllocation.subtitle")}
            </p>
          </div>
        </div>
      </CardHeader>
      <CardContent className="p-0!">
        <div className="rounded-xs h-full">
          <div className="mt-4 flex items-center gap-2 rounded-lg p-4 bg-mw-neutral-50">
            <Tooltip
              className="border border-mw-neutral-100 text-mw-neutral-500 dark:border-mw-neutral-600 rounded-full bg-white dark:bg-mw-neutral-800"
              content={tCampaigns(
                "optimization.budgetAllocation.companyTooltip",
              )}
              position="top"
            >
              <Info className="w-4 h-4 relative overflow-hidden" />
            </Tooltip>
            <div className="flex-1 inline-flex flex-col justify-start items-start gap-1">
              <p className="text-sm font-normal text-mw-neutral-500 leading-none">
                {tCampaigns("optimization.budgetAllocation.description")}
              </p>
            </div>
          </div>
          <div className="pt-4 grid grid-cols-2 gap-4">
            {BUDGET_ALLOCATION_KEYS.map((key) => (
              <Controller
                key={key}
                name={`budgetAllocation.${key}`}
                control={control}
                render={({ field }) => (
                  <Slider
                    label={tCampaigns(`optimization.budgetAllocation.${key}`)}
                    value={field.value}
                    onChange={async (value) => {
                      handleSliderChange(value, field, key);
                    }}
                    onMouseUp={onFieldMouseUp}
                    showValue
                    formatValue={(value) => `${value.toFixed(2)}%`}
                  />
                )}
              />
            ))}
          </div>
        </div>
      </CardContent>
    </Card>
  );
};

export default BudgetAllocationComponent;
