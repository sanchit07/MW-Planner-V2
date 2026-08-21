import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";

import { Switch } from "../Switch";

describe("Switch", () => {
  it("should render switch", () => {
    const onChange = vi.fn();
    render(<Switch checked={false} onChange={onChange} />);
    const checkbox = screen.getByRole("checkbox");
    expect(checkbox).toBeInTheDocument();
  });

  it("should be checked when checked prop is true", () => {
    const onChange = vi.fn();
    render(<Switch checked={true} onChange={onChange} />);
    const checkbox = screen.getByRole("checkbox");
    expect(checkbox).toBeChecked();
  });

  it("should call onChange when toggled", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Switch checked={false} onChange={onChange} />);

    const checkbox = screen.getByRole("checkbox");
    await user.click(checkbox);

    expect(onChange).toHaveBeenCalledWith(true);
  });

  it("should be disabled when disabled prop is set", () => {
    const onChange = vi.fn();
    render(<Switch checked={false} onChange={onChange} disabled />);
    const checkbox = screen.getByRole("checkbox");
    expect(checkbox).toBeDisabled();
  });

  it("should apply different sizes", () => {
    const onChange = vi.fn();
    const { container: smContainer } = render(
      <Switch checked={false} onChange={onChange} size="sm" />,
    );
    const { container: mdContainer } = render(
      <Switch checked={false} onChange={onChange} size="md" />,
    );
    const { container: lgContainer } = render(
      <Switch checked={false} onChange={onChange} size="lg" />,
    );

    expect(smContainer.querySelector(".w-8")).toBeInTheDocument();
    expect(mdContainer.querySelector(".w-11")).toBeInTheDocument();
    expect(lgContainer.querySelector(".w-14")).toBeInTheDocument();
  });

  it("should apply custom className", () => {
    const onChange = vi.fn();
    const { container } = render(
      <Switch checked={false} onChange={onChange} className="custom-class" />,
    );
    expect(container.querySelector(".custom-class")).toBeInTheDocument();
  });

  it("should use provided id", () => {
    const onChange = vi.fn();
    render(<Switch checked={false} onChange={onChange} id="custom-id" />);
    const checkbox = screen.getByRole("checkbox");
    expect(checkbox.id).toBe("custom-id");
  });
});
