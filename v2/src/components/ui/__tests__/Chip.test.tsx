import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";

import { Chip } from "../Chip";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

describe("Chip", () => {
  it("should render chip with children", () => {
    render(<Chip>Test Chip</Chip>);
    expect(screen.getByText("Test Chip")).toBeInTheDocument();
  });

  it("should apply default variant", () => {
    const { container } = render(<Chip>Default</Chip>);
    expect(container.querySelector(".bg-mw-neutral-100")).toBeInTheDocument();
  });

  it("should apply different variants", () => {
    const { container: primaryContainer } = render(
      <Chip variant="primary">Primary</Chip>,
    );
    const { container: successContainer } = render(
      <Chip variant="success">Success</Chip>,
    );
    const { container: errorContainer } = render(
      <Chip variant="error">Error</Chip>,
    );

    expect(
      primaryContainer.querySelector(".bg-mw-primary-100"),
    ).toBeInTheDocument();
    expect(
      successContainer.querySelector(".bg-success-100"),
    ).toBeInTheDocument();
    expect(errorContainer.querySelector(".bg-error-100")).toBeInTheDocument();
  });

  it("should apply different sizes", () => {
    const { container: smContainer } = render(<Chip size="sm">Small</Chip>);
    const { container: mdContainer } = render(<Chip size="md">Medium</Chip>);
    const { container: lgContainer } = render(<Chip size="lg">Large</Chip>);

    expect(smContainer.querySelector(".text-xs")).toBeInTheDocument();
    expect(mdContainer.querySelector(".text-sm")).toBeInTheDocument();
    expect(lgContainer.querySelector(".text-base")).toBeInTheDocument();
  });

  it("should call onRemove when remove button is clicked", async () => {
    const user = userEvent.setup();
    const onRemove = vi.fn();
    render(<Chip onRemove={onRemove}>Removable</Chip>);

    const removeButton = screen.getByLabelText("aria.remove");
    await user.click(removeButton);

    expect(onRemove).toHaveBeenCalledTimes(1);
  });

  it("should not show remove button when onRemove is not provided", () => {
    render(<Chip>No Remove</Chip>);
    expect(screen.queryByLabelText("aria.remove")).not.toBeInTheDocument();
  });

  it("should call onClick when clicked and clickable", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <Chip onClick={onClick} clickable>
        Clickable
      </Chip>,
    );

    const chip = screen.getByText("Clickable");
    await user.click(chip);

    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it("should be disabled when disabled prop is set", () => {
    const { container } = render(<Chip disabled>Disabled</Chip>);
    expect(container.querySelector(".opacity-50")).toBeInTheDocument();
  });

  it("should not call onRemove when disabled", async () => {
    const user = userEvent.setup();
    const onRemove = vi.fn();
    render(
      <Chip onRemove={onRemove} disabled>
        Disabled
      </Chip>,
    );

    const removeButton = screen.getByLabelText("aria.remove");
    await user.click(removeButton);

    expect(onRemove).not.toHaveBeenCalled();
  });

  it("should apply custom className", () => {
    const { container } = render(<Chip className="custom-class">Chip</Chip>);
    expect(container.querySelector(".custom-class")).toBeInTheDocument();
  });
});
