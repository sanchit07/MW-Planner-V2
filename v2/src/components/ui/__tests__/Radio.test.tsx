import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";

import { Radio } from "../Radio";

describe("Radio", () => {
  it("should render radio button", () => {
    render(<Radio />);
    const radio = screen.getByRole("radio");
    expect(radio).toBeInTheDocument();
  });

  it("should render with label", () => {
    render(<Radio label="Option 1" />);
    expect(screen.getByLabelText("Option 1")).toBeInTheDocument();
  });

  it("should render with description", () => {
    render(<Radio label="Option 1" description="Description text" />);
    expect(screen.getByText("Description text")).toBeInTheDocument();
  });

  it("should handle change event", async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(<Radio onChange={handleChange} />);

    const radio = screen.getByRole("radio");
    await user.click(radio);

    expect(handleChange).toHaveBeenCalled();
  });

  it("should be checked when checked prop is true", () => {
    render(<Radio checked />);
    const radio = screen.getByRole("radio");
    expect(radio).toBeChecked();
  });

  it("should be disabled when disabled prop is set", () => {
    render(<Radio disabled />);
    const radio = screen.getByRole("radio");
    expect(radio).toBeDisabled();
  });

  it("should display error message", () => {
    render(<Radio error="This field is required" />);
    expect(screen.getByText("This field is required")).toBeInTheDocument();
  });

  it("should generate id from name and value", () => {
    render(<Radio name="option" value="1" />);
    const radio = screen.getByRole("radio");
    expect(radio.id).toBe("radio-option-1");
  });

  it("should use provided id", () => {
    render(<Radio id="custom-id" />);
    const radio = screen.getByRole("radio");
    expect(radio.id).toBe("custom-id");
  });

  it("should apply custom className", () => {
    const { container } = render(<Radio className="custom-class" />);
    const radio = container.querySelector('input[type="radio"]');
    expect(radio?.className).toContain("custom-class");
  });
});
