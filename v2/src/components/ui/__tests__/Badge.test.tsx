import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import { Badge } from "../Badge";

describe("Badge", () => {
  it("should render badge with children", () => {
    render(<Badge>Test Badge</Badge>);
    expect(screen.getByText("Test Badge")).toBeInTheDocument();
  });

  it("should apply outline variant by default", () => {
    const { container } = render(<Badge>Badge</Badge>);
    expect(container.querySelector(".outline")).toBeInTheDocument();
  });

  it("should apply different variants", () => {
    const { container: defaultContainer } = render(
      <Badge variant="default">Default</Badge>,
    );
    const { container: secondaryContainer } = render(
      <Badge variant="secondary">Secondary</Badge>,
    );
    const { container: destructiveContainer } = render(
      <Badge variant="destructive">Destructive</Badge>,
    );
    const { container: successContainer } = render(
      <Badge variant="success">Success</Badge>,
    );
    const { container: warningContainer } = render(
      <Badge variant="warning">Warning</Badge>,
    );

    expect(
      defaultContainer.querySelector(".bg-mw-primary-500"),
    ).toBeInTheDocument();
    expect(
      secondaryContainer.querySelector(".bg-mw-primary-100"),
    ).toBeInTheDocument();
    expect(
      destructiveContainer.querySelector(".bg-mw-error-100"),
    ).toBeInTheDocument();
    expect(
      successContainer.querySelector(".bg-mw-success-100"),
    ).toBeInTheDocument();
    expect(
      warningContainer.querySelector(".bg-mw-warning-100"),
    ).toBeInTheDocument();
  });

  it("should apply different sizes", () => {
    const { container: smContainer } = render(<Badge size="sm">Small</Badge>);
    const { container: mdContainer } = render(<Badge size="md">Medium</Badge>);

    expect(smContainer.querySelector(".text-xs")).toBeInTheDocument();
    expect(mdContainer.querySelector(".text-sm")).toBeInTheDocument();
  });

  it("should apply custom className", () => {
    const { container } = render(<Badge className="custom-class">Badge</Badge>);
    expect(container.querySelector(".custom-class")).toBeInTheDocument();
  });

  it("should pass through other props", () => {
    render(<Badge data-testid="test">Badge</Badge>);
    expect(screen.getByTestId("test")).toBeInTheDocument();
  });
});
