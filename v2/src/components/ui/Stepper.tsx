import { useTranslate } from "@tolgee/react";
import { Check } from "lucide-react";
import React from "react";

export interface StepperStep {
  id: number;
  title: string;
  subtitle?: string;
  isCompleted: boolean;
  isAccessible: boolean;
  isCurrent: boolean;
  isOptional?: boolean;
}

export interface StepperProps {
  steps: StepperStep[];
  onStepClick?: (stepId: number) => void;
  className?: string;
  variant?: "default" | "compact";
  showProgress?: boolean;
  id?: string;
}

const Stepper: React.FC<StepperProps> = ({
  steps,
  onStepClick,
  className = "",
  variant = "default",
  id,
}) => {
  const { t: tCommon } = useTranslate(["common"]);
  const handleStepClick = (step: StepperStep) => {
    if (step.isAccessible && onStepClick) {
      onStepClick(step.id);
    }
  };

  const getStepIcon = (step: StepperStep) => {
    if (step.isCurrent) {
      return (
        <div className="relative flex items-center justify-center">
          <div className="absolute w-8 h-8 rounded-full bg-mw-primary-500 opacity-30 animate-ping" />
          <div className="w-8 h-8 p-2.5 bg-mw-primary-50 outline outline-mw-primary-500 rounded-full flex items-center justify-center relative">
            <span className="text-sm font-medium text-mw-primary-500">
              {step.id}
            </span>
          </div>
        </div>
      );
    }

    if (step.isCompleted) {
      return (
        <div className="w-8 h-8 bg-mw-success-600 rounded-full flex items-center justify-center">
          <Check className="w-4 h-4 text-white" />
        </div>
      );
    }

    return (
      <div
        className={`w-8 h-8 rounded-full flex items-center justify-center outline -outline-offset-1 bg-white ${
          step.isAccessible
            ? "outline-mw-neutral-500 text-mw-neutral-500"
            : "outline-mw-neutral-300 text-mw-neutral-300"
        }`}
      >
        <span className="text-sm font-medium text-mw-neutral-500">
          {step.id}
        </span>
      </div>
    );
  };

  const getConnectorLine = (index: number) => {
    if (index === steps.length - 1) return null;

    return (
      <div className="flex-1 mx-4 flex items-center">
        <div
          className="h-0.5 w-full"
          style={{
            background:
              "repeating-linear-gradient(to right, #7F7F7F 0px, #7F7F7F 4px, transparent 4px, transparent 8px)",
          }}
        />
      </div>
    );
  };

  if (variant === "compact") {
    return (
      <div id={id || "stepper"} className={`${className}`}>
        <div className="flex items-center gap-2">
          {steps.map((step, index) => (
            <React.Fragment key={step.id}>
              <button
                id={`stepper-step-${step.id}`}
                onClick={() => handleStepClick(step)}
                disabled={!step.isAccessible}
                className={`flex items-center gap-2 px-2 py-2 rounded-full text-sm transition-colors  ${
                  step.isCurrent
                    ? " text-mw-primary-500 hover:bg-mw-primary-50 cursor-pointer "
                    : step.isCompleted
                      ? "text-mw-success-800 hover:bg-mw-success-50 cursor-pointer"
                      : step.isAccessible
                        ? " text-mw-neutral-600 hover:bg-mw-neutral-50 cursor-pointer"
                        : " text-mw-neutral-500 cursor-not-allowed"
                }`}
              >
                {getStepIcon(step)}
                <div className="inline-flex flex-col gap-1 items-start">
                  <span
                    className={`${step.isCurrent || step.isCompleted ? "font-semibold" : "font-medium"}`}
                  >
                    {step.title}
                  </span>
                  {step.isOptional && (
                    <span className="text-xs">{tCommon("optional")}</span>
                  )}
                </div>
              </button>
              {getConnectorLine(index)}
            </React.Fragment>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div id={id || "stepper"} className={`${className}`}>
      <div className="flex items-center">
        {steps.map((step, index) => (
          <React.Fragment key={step.id}>
            <div className="flex flex-col items-center">
              <button
                id={`stepper-step-${step.id}`}
                onClick={() => handleStepClick(step)}
                disabled={!step.isAccessible}
                className={`flex flex-col items-center transition-colors ${
                  step.isAccessible ? "cursor-pointer" : "cursor-not-allowed"
                }`}
              >
                <div className="flex flex-col items-center">
                  {getStepIcon(step)}
                  <div className="mt-2 text-center">
                    <div
                      className={`text-sm font-medium whitespace-nowrap ${
                        step.isCurrent
                          ? "text-mw-primary-600"
                          : step.isCompleted
                            ? "text-mw-success-600"
                            : "text-mw-neutral-500"
                      }`}
                    >
                      {step.title}
                    </div>
                    {step.isOptional && (
                      <div className="text-xs text-mw-neutral-400 mt-1">
                        {tCommon("optional")}
                      </div>
                    )}
                  </div>
                </div>
              </button>
            </div>
            {getConnectorLine(index)}
          </React.Fragment>
        ))}
      </div>
    </div>
  );
};

export default Stepper;
