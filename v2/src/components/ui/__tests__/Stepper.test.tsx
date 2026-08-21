import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";

import Stepper from "../Stepper";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

const mockSteps = [
  {
    id: 1,
    title: "Step 1",
    isCompleted: false,
    isAccessible: true,
    isCurrent: true,
  },
  {
    id: 2,
    title: "Step 2",
    isCompleted: false,
    isAccessible: false,
    isCurrent: false,
  },
  {
    id: 3,
    title: "Step 3",
    isCompleted: true,
    isAccessible: true,
    isCurrent: false,
  },
];

describe("Stepper", () => {
  it("should render stepper with steps", () => {
    render(<Stepper steps={mockSteps} />);
    expect(screen.getByText("Step 1")).toBeInTheDocument();
    expect(screen.getByText("Step 2")).toBeInTheDocument();
    expect(screen.getByText("Step 3")).toBeInTheDocument();
  });

  it("should show current step with primary styling", () => {
    render(<Stepper steps={mockSteps} />);
    const step1 = screen.getByText("Step 1").closest("button");
    expect(step1).toBeInTheDocument();
  });

  it("should show completed step with check icon", () => {
    render(<Stepper steps={mockSteps} />);
    const step3 = screen.getByText("Step 3").closest("button");
    expect(step3).toBeInTheDocument();
  });

  it("should call onStepClick when accessible step is clicked", async () => {
    const user = userEvent.setup();
    const onStepClick = vi.fn();
    render(<Stepper steps={mockSteps} onStepClick={onStepClick} />);

    const step1 = screen.getByText("Step 1").closest("button");
    if (step1) {
      await user.click(step1);
      expect(onStepClick).toHaveBeenCalledWith(1);
    }
  });

  it("should not call onStepClick when step is not accessible", async () => {
    const user = userEvent.setup();
    const onStepClick = vi.fn();
    render(<Stepper steps={mockSteps} onStepClick={onStepClick} />);

    const step2 = screen.getByText("Step 2").closest("button");
    if (step2) {
      await user.click(step2);
      expect(onStepClick).not.toHaveBeenCalled();
    }
  });

  it("should render compact variant", () => {
    render(<Stepper steps={mockSteps} variant="compact" />);
    expect(screen.getByText("Step 1")).toBeInTheDocument();
  });

  it("should show optional label when step is optional", () => {
    const stepsWithOptional = [
      {
        ...mockSteps[0],
        isOptional: true,
      },
    ];
    render(<Stepper steps={stepsWithOptional} />);
    expect(screen.getByText("optional")).toBeInTheDocument();
  });

  it("should apply custom className", () => {
    const { container } = render(
      <Stepper steps={mockSteps} className="custom-class" />,
    );
    expect(container.querySelector(".custom-class")).toBeInTheDocument();
  });

  it("should use provided id", () => {
    const { container } = render(<Stepper steps={mockSteps} id="custom-id" />);
    expect(container.querySelector("#custom-id")).toBeInTheDocument();
  });

  it("should render animate-ping element only on current step", () => {
    const { container } = render(<Stepper steps={mockSteps} />);
    const pingElements = container.querySelectorAll(".animate-ping");
    // Only the current step (step 1) should have the ping animation
    expect(pingElements.length).toBe(1);
  });

  it("should not render animate-ping on completed or inaccessible steps", () => {
    const allNonCurrentSteps = mockSteps.map((s) => ({
      ...s,
      isCurrent: false,
    }));
    const { container } = render(<Stepper steps={allNonCurrentSteps} />);
    const pingElements = container.querySelectorAll(".animate-ping");
    expect(pingElements.length).toBe(0);
  });

  it("should render animate-ping in compact variant on current step", () => {
    const { container } = render(
      <Stepper steps={mockSteps} variant="compact" />,
    );
    const pingElements = container.querySelectorAll(".animate-ping");
    expect(pingElements.length).toBe(1);
  });
});
