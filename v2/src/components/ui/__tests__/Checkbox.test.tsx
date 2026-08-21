import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";

import { Checkbox } from "../Checkbox";

describe("Checkbox", () => {
  it("should render checkbox", () => {
    render(<Checkbox />);
    const checkbox = screen.getByRole("checkbox");
    expect(checkbox).toBeInTheDocument();
  });

  it("should render with label", () => {
    render(<Checkbox label="Accept terms" />);
    expect(screen.getByLabelText("Accept terms")).toBeInTheDocument();
  });

  it("should render with description", () => {
    render(<Checkbox label="Subscribe" description="Receive email updates" />);
    expect(screen.getByText("Receive email updates")).toBeInTheDocument();
  });

  it("should handle checked state", async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(<Checkbox onChange={handleChange} />);

    const checkbox = screen.getByRole("checkbox");
    await user.click(checkbox);

    expect(handleChange).toHaveBeenCalled();
  });

  it("should be checked when checked prop is true", () => {
    render(<Checkbox checked />);
    const checkbox = screen.getByRole("checkbox");
    expect(checkbox).toBeChecked();
  });

  it("should be disabled when disabled prop is set", () => {
    render(<Checkbox disabled />);
    const checkbox = screen.getByRole("checkbox");
    expect(checkbox).toBeDisabled();
  });

  it("should display error message", () => {
    render(<Checkbox error="This field is required" />);
    expect(screen.getByText("This field is required")).toBeInTheDocument();
  });

  it("should set indeterminate state", () => {
    const { container } = render(<Checkbox isIndeterminate />);
    const checkbox = container.querySelector(
      'input[type="checkbox"]',
    ) as HTMLInputElement;
    expect(checkbox.indeterminate).toBe(true);
  });

  it("should generate id from name when provided", () => {
    render(<Checkbox name="terms" />);
    const checkbox = screen.getByRole("checkbox");
    expect(checkbox.id).toBe("checkbox-terms");
  });

  it("should generate id from label when name not provided", () => {
    render(<Checkbox label="Accept Terms" />);
    const checkbox = screen.getByRole("checkbox");
    expect(checkbox.id).toContain("checkbox-");
  });

  it("should use provided id", () => {
    render(<Checkbox id="custom-id" />);
    const checkbox = screen.getByRole("checkbox");
    expect(checkbox.id).toBe("custom-id");
  });

  it("should apply custom className", () => {
    const { container } = render(<Checkbox className="custom-class" />);
    const checkbox = container.querySelector('input[type="checkbox"]');
    expect(checkbox?.className).toContain("custom-class");
  });
});
