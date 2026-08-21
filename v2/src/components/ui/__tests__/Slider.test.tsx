import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

import { Slider } from "../Slider";

describe("Slider", () => {
  it("should render slider", () => {
    render(<Slider />);
    const slider = screen.getByRole("slider");
    expect(slider).toBeInTheDocument();
  });

  it("should render with label", () => {
    render(<Slider label="Volume" />);
    expect(screen.getByText("Volume")).toBeInTheDocument();
  });

  it("should show value when showValue is true", () => {
    render(<Slider value={50} showValue />);
    expect(screen.getByText("50")).toBeInTheDocument();
  });

  it("should use formatValue when provided", () => {
    const formatValue = (val: number) => `${val}%`;
    render(<Slider value={50} showValue formatValue={formatValue} />);
    expect(screen.getByText("50%")).toBeInTheDocument();
  });

  it("should call onChange when value changes", async () => {
    const onChange = vi.fn();
    render(<Slider value={0} onChange={onChange} />);

    const slider = screen.getByRole("slider") as HTMLInputElement;
    // For range inputs, use fireEvent.change directly
    fireEvent.change(slider, { target: { value: "50" } });

    expect(onChange).toHaveBeenCalledWith(50);
  });

  it("should use default min and max", () => {
    render(<Slider />);
    const slider = screen.getByRole("slider");
    expect(slider).toHaveAttribute("min", "0");
    expect(slider).toHaveAttribute("max", "100");
  });

  it("should use custom min and max", () => {
    render(<Slider min={10} max={90} />);
    const slider = screen.getByRole("slider");
    expect(slider).toHaveAttribute("min", "10");
    expect(slider).toHaveAttribute("max", "90");
  });

  it("should be disabled when disabled prop is set", () => {
    render(<Slider disabled />);
    const slider = screen.getByRole("slider");
    expect(slider).toBeDisabled();
  });

  it("should apply custom className", () => {
    const { container } = render(<Slider className="custom-class" />);
    const slider = container.querySelector('input[type="range"]');
    expect(slider?.className).toContain("custom-class");
  });
});
