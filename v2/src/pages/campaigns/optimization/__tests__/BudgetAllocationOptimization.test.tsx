import type { OptimizationFormData } from "@schemas/campaigns/optimzation.schema";
import { render, screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FormProvider, useForm } from "react-hook-form";
import { describe, expect, it, vi, beforeEach } from "vitest";

import { defaultBudgetAllocation as mockBudgetAllocation } from "./mocks";
import BudgetAllocationComponent from "../BudgetAllocationOptimization";

const mockTransformOptimizationAdjustHundredPercentShare = vi.fn();

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@utils/optimization.utils", () => ({
  transformOptimizationAdjustHundredPercentShare: (...args: unknown[]) =>
    mockTransformOptimizationAdjustHundredPercentShare(...args),
}));

const defaultBudgetAllocation: OptimizationFormData["budgetAllocation"] =
  mockBudgetAllocation;

const defaultScheduleTargeting: OptimizationFormData["scheduleTargeting"] = {
  weekdayDistribution: {
    MONDAY: 14.29,
    TUESDAY: 14.29,
    WEDNESDAY: 14.29,
    THURSDAY: 14.29,
    FRIDAY: 14.29,
    SATURDAY: 14.29,
    SUNDAY: 14.29,
  },
  daypartDistribution: {
    "06-10": 20,
    "10-14": 20,
    "14-18": 20,
    "18-22": 20,
    "22-06": 20,
  },
};

function TestWrapper({
  onFieldChange,
  handleBudgetSchedulingFieldMouseUp,
  budgetFormData = defaultBudgetAllocation,
}: {
  onFieldChange?: (value: Record<string, unknown>) => Promise<void>;
  handleBudgetSchedulingFieldMouseUp?: (
    key: keyof OptimizationFormData,
  ) => Promise<void>;
  budgetFormData?: OptimizationFormData["budgetAllocation"];
}) {
  const methods = useForm<OptimizationFormData>({
    defaultValues: {
      budgetAllocation: defaultBudgetAllocation,
      scheduleTargeting: defaultScheduleTargeting,
    },
  });

  return (
    <FormProvider {...methods}>
      <BudgetAllocationComponent
        control={methods.control}
        onFieldChange={onFieldChange ?? (async () => {})}
        budgetFormData={budgetFormData}
        handleBudgetSchedulingFieldMouseUp={
          handleBudgetSchedulingFieldMouseUp ?? (async () => {})
        }
      />
    </FormProvider>
  );
}

describe("BudgetAllocationOptimization", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockTransformOptimizationAdjustHundredPercentShare.mockImplementation(
      (currentValue: number, _fieldValue: unknown, budgetFormData: unknown) => {
        const data = budgetFormData as Record<string, number>;
        if (!data) return 100;
        const others = { ...data };
        delete others.digital;
        return { ...others, digital: currentValue };
      },
    );
  });

  it("renders card with title and subtitle", () => {
    render(<TestWrapper />);

    expect(
      screen.getByText("optimization.budgetAllocation.title"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("optimization.budgetAllocation.subtitle"),
    ).toBeInTheDocument();
  });

  it("renders description and info tooltip area", () => {
    render(<TestWrapper />);

    expect(
      screen.getByText("optimization.budgetAllocation.description"),
    ).toBeInTheDocument();
  });

  it("renders four budget allocation sliders with labels", () => {
    render(<TestWrapper />);

    expect(
      screen.getByText("optimization.budgetAllocation.digital"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("optimization.budgetAllocation.transit"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("optimization.budgetAllocation.classic"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("optimization.budgetAllocation.retail"),
    ).toBeInTheDocument();
  });

  it("calls onFieldChange when slider value changes", async () => {
    const onFieldChange = vi.fn().mockResolvedValue(undefined);

    render(<TestWrapper onFieldChange={onFieldChange} />);

    const sliders = screen.getAllByRole("slider");
    expect(sliders.length).toBeGreaterThanOrEqual(4);

    fireEvent.change(sliders[0], { target: { value: "30" } });
    expect(onFieldChange).toHaveBeenCalled();
  });

  it("calls handleBudgetSchedulingFieldMouseUp on mouse up", async () => {
    const user = userEvent.setup();
    const handleMouseUp = vi.fn().mockResolvedValue(undefined);

    render(<TestWrapper handleBudgetSchedulingFieldMouseUp={handleMouseUp} />);

    const sliders = screen.getAllByRole("slider");
    await user.click(sliders[0]);
    const firstSlider = sliders[0];
    firstSlider.dispatchEvent(
      new MouseEvent("mouseup", { bubbles: true, cancelable: true }),
    );

    expect(handleMouseUp).toHaveBeenCalledWith("budgetAllocation");
  });

  it("displays slider inputs with values", () => {
    render(<TestWrapper />);

    const sliders = screen.getAllByRole("slider");
    expect(sliders.length).toBe(4);
    sliders.forEach((slider) => {
      expect(slider).toHaveAttribute("value");
    });
  });

  it("renders with custom budgetFormData", () => {
    const customData = {
      digital: 40,
      transit: 30,
      classic: 20,
      retail: 10,
    };

    render(<TestWrapper budgetFormData={customData} />);

    expect(
      screen.getByText("optimization.budgetAllocation.digital"),
    ).toBeInTheDocument();
    const sliders = screen.getAllByRole("slider");
    expect(sliders.length).toBeGreaterThanOrEqual(4);
  });

  it("handleSliderChange merges valueToSave when transform returns object", async () => {
    mockTransformOptimizationAdjustHundredPercentShare.mockReturnValue({
      digital: 30,
      transit: 25,
      classic: 25,
      retail: 20,
    });
    const onFieldChange = vi.fn().mockResolvedValue(undefined);
    render(<TestWrapper onFieldChange={onFieldChange} />);

    const sliders = screen.getAllByRole("slider");
    fireEvent.change(sliders[0], { target: { value: "30" } });

    expect(
      mockTransformOptimizationAdjustHundredPercentShare,
    ).toHaveBeenCalled();
    expect(onFieldChange).toHaveBeenCalledWith(
      expect.objectContaining({
        digital: 30,
        transit: 25,
        classic: 25,
        retail: 20,
      }),
    );
  });

  it("handleSliderChange runs when transform returns 100 (single value branch)", async () => {
    mockTransformOptimizationAdjustHundredPercentShare.mockReturnValue(100);
    const onFieldChange = vi.fn().mockResolvedValue(undefined);
    render(<TestWrapper onFieldChange={onFieldChange} />);

    const sliders = screen.getAllByRole("slider");
    expect(sliders.length).toBeGreaterThanOrEqual(4);
    // Use a value different from default (25) so the input change fires
    fireEvent.change(sliders[0], { target: { value: "40" } });

    expect(
      mockTransformOptimizationAdjustHundredPercentShare,
    ).toHaveBeenCalled();
  });
});
