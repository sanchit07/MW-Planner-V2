import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi } from "vitest";

import { Input } from "../Input";

describe("Input", () => {
  it("should render input without label", () => {
    render(<Input />);
    const input = screen.getByRole("textbox");
    expect(input).toBeInTheDocument();
  });

  it("should render input with label", () => {
    render(<Input label="Username" />);
    expect(screen.getByLabelText("Username")).toBeInTheDocument();
  });

  it("should show error message", () => {
    render(<Input label="Email" error="Invalid email" />);
    expect(screen.getByText("Invalid email")).toBeInTheDocument();
    const input = screen.getByLabelText("Email");
    expect(input.className).toContain("border-mw-error-300");
  });

  it("should show help text when no error", () => {
    render(<Input label="Password" helpText="Must be 8 characters" />);
    expect(screen.getByText("Must be 8 characters")).toBeInTheDocument();
  });

  it("should not show help text when error is present", () => {
    render(
      <Input
        label="Password"
        helpText="Must be 8 characters"
        error="Password required"
      />,
    );
    expect(screen.getByText("Password required")).toBeInTheDocument();
    expect(screen.queryByText("Must be 8 characters")).not.toBeInTheDocument();
  });

  it("should mark as required", () => {
    render(<Input label="Email" required />);
    const label = screen.getByText("Email").closest("label");
    expect(label).toBeInTheDocument();
  });

  it("should handle input change", async () => {
    const handleChange = vi.fn();
    const user = userEvent.setup();
    render(<Input onChange={handleChange} />);

    const input = screen.getByRole("textbox");
    await user.type(input, "test");
    expect(handleChange).toHaveBeenCalled();
  });

  it("should forward ref", () => {
    const ref = React.createRef<HTMLInputElement>();
    render(<Input ref={ref} />);
    expect(ref.current).toBeInstanceOf(HTMLInputElement);
  });

  it("should generate id from name when provided", () => {
    render(<Input name="username" />);
    const input = screen.getByRole("textbox");
    expect(input.id).toBe("input-username");
  });

  it("should generate id from label when name not provided", () => {
    render(<Input label="Email Address" />);
    const input = screen.getByLabelText("Email Address");
    expect(input.id).toContain("input-");
  });

  it("should use provided id", () => {
    render(<Input id="custom-id" />);
    const input = screen.getByRole("textbox");
    expect(input.id).toBe("custom-id");
  });

  it("should pass through other input props", () => {
    render(<Input type="email" placeholder="Enter email" />);
    const input = screen.getByRole("textbox");
    expect(input).toHaveAttribute("type", "email");
    expect(input).toHaveAttribute("placeholder", "Enter email");
  });

  it("should be disabled when disabled prop is set", () => {
    render(<Input disabled />);
    const input = screen.getByRole("textbox");
    expect(input).toBeDisabled();
  });

  it("should apply custom className", () => {
    const { container } = render(<Input className="custom-class" />);
    const input = container.querySelector("input");
    expect(input?.className).toContain("custom-class");
  });
});
