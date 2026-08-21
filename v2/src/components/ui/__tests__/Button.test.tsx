import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi } from "vitest";

import { Button } from "../Button";

describe("Button", () => {
  it("should render button with children", () => {
    render(<Button>Click me</Button>);
    expect(
      screen.getByRole("button", { name: "Click me" }),
    ).toBeInTheDocument();
  });

  it("should apply primary variant by default", () => {
    const { container } = render(<Button>Primary</Button>);
    const button = container.querySelector("button");
    expect(button?.className).toContain("bg-mw-primary-500");
  });

  it("should apply different variants", () => {
    const { container: primaryContainer } = render(
      <Button variant="primary">Primary</Button>,
    );
    const { container: secondaryContainer } = render(
      <Button variant="secondary">Secondary</Button>,
    );
    const { container: ghostContainer } = render(
      <Button variant="ghost">Ghost</Button>,
    );
    const { container: outlineContainer } = render(
      <Button variant="outline">Outline</Button>,
    );
    const { container: destructiveContainer } = render(
      <Button variant="destructive">Destructive</Button>,
    );

    expect(primaryContainer.querySelector("button")?.className).toContain(
      "bg-mw-primary-500",
    );
    expect(secondaryContainer.querySelector("button")?.className).toContain(
      "bg-mw-secondary-600",
    );
    expect(ghostContainer.querySelector("button")?.className).toContain(
      "text-mw-neutral-600",
    );
    expect(outlineContainer.querySelector("button")?.className).toContain(
      "outline",
    );
    expect(destructiveContainer.querySelector("button")?.className).toContain(
      "bg-mw-error-600",
    );
  });

  it("should apply different sizes", () => {
    const { container: smContainer } = render(<Button size="sm">Small</Button>);
    const { container: mdContainer } = render(
      <Button size="md">Medium</Button>,
    );
    const { container: lgContainer } = render(<Button size="lg">Large</Button>);

    expect(smContainer.querySelector("button")?.className).toContain("px-3");
    expect(mdContainer.querySelector("button")?.className).toContain("px-6");
    expect(lgContainer.querySelector("button")?.className).toContain("px-6");
  });

  it("should handle click events", async () => {
    const handleClick = vi.fn();
    const user = userEvent.setup();
    render(<Button onClick={handleClick}>Click me</Button>);

    await user.click(screen.getByRole("button"));
    expect(handleClick).toHaveBeenCalledTimes(1);
  });

  it("should be disabled when disabled prop is set", () => {
    render(<Button disabled>Disabled</Button>);
    const button = screen.getByRole("button");
    expect(button).toBeDisabled();
  });

  it("should apply custom className", () => {
    const { container } = render(
      <Button className="custom-class">Custom</Button>,
    );
    expect(container.querySelector("button")?.className).toContain(
      "custom-class",
    );
  });

  it("should forward ref", () => {
    const ref = React.createRef<HTMLButtonElement>();
    render(<Button ref={ref}>Ref</Button>);
    expect(ref.current).toBeInstanceOf(HTMLButtonElement);
  });

  it("should generate id when not provided", () => {
    const { container } = render(<Button>No ID</Button>);
    const button = container.querySelector("button");
    expect(button?.id).toBeTruthy();
    expect(button?.id).toContain("btn-");
  });

  it("should use provided id", () => {
    const { container } = render(<Button id="custom-id">Custom ID</Button>);
    expect(container.querySelector("button")?.id).toBe("custom-id");
  });

  it("should pass through other props", () => {
    render(
      <Button type="submit" data-testid="test">
        Submit
      </Button>,
    );
    const button = screen.getByTestId("test");
    expect(button).toHaveAttribute("type", "submit");
  });
});
