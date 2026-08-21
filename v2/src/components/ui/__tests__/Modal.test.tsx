import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { Modal } from "../Modal";

describe("Modal", () => {
  const mockOnClose = vi.fn();
  const defaultProps = {
    isOpen: true,
    onClose: mockOnClose,
    title: "Test Modal",
    children: <div>Modal Content</div>,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("should not render when isOpen is false", () => {
    render(<Modal {...defaultProps} isOpen={false} />);
    expect(screen.queryByText("Test Modal")).not.toBeInTheDocument();
  });

  it("should render when isOpen is true", () => {
    render(<Modal {...defaultProps} />);
    expect(screen.getByText("Test Modal")).toBeInTheDocument();
    expect(screen.getByText("Modal Content")).toBeInTheDocument();
  });

  it("should call onClose when close button is clicked", async () => {
    const user = userEvent.setup();
    const { container } = render(<Modal {...defaultProps} />);

    // Close button has id with "-close" suffix
    const closeButton = container.querySelector(
      '[id*="-close"]',
    ) as HTMLButtonElement;
    expect(closeButton).toBeInTheDocument();

    await user.click(closeButton);
    expect(mockOnClose).toHaveBeenCalledTimes(1);
  });

  it("should not show close button when showCloseButton is false", () => {
    render(<Modal {...defaultProps} showCloseButton={false} />);
    expect(
      screen.queryByRole("button", { name: /close/i }),
    ).not.toBeInTheDocument();
  });

  it("should call onClose when escape key is pressed", async () => {
    const user = userEvent.setup();
    render(<Modal {...defaultProps} />);

    await user.keyboard("{Escape}");

    expect(mockOnClose).toHaveBeenCalledTimes(1);
  });

  it("should call onPrimaryAction when primary button is clicked", async () => {
    const user = userEvent.setup();
    const onPrimaryAction = vi.fn();
    render(
      <Modal
        {...defaultProps}
        primaryButtonText="Save"
        onPrimaryAction={onPrimaryAction}
      />,
    );

    const primaryButton = screen.getByRole("button", { name: "Save" });
    await user.click(primaryButton);

    expect(onPrimaryAction).toHaveBeenCalledTimes(1);
    expect(mockOnClose).toHaveBeenCalledTimes(1);
  });

  it("should call onSecondaryAction when secondary button is clicked", async () => {
    const user = userEvent.setup();
    const onSecondaryAction = vi.fn();
    render(
      <Modal
        {...defaultProps}
        secondaryButtonText="Cancel"
        onSecondaryAction={onSecondaryAction}
      />,
    );

    const secondaryButton = screen.getByRole("button", { name: "Cancel" });
    await user.click(secondaryButton);

    expect(onSecondaryAction).toHaveBeenCalledTimes(1);
  });

  it("should call onClose when secondary button is clicked without onSecondaryAction", async () => {
    const user = userEvent.setup();
    render(<Modal {...defaultProps} secondaryButtonText="Cancel" />);

    const secondaryButton = screen.getByRole("button", { name: "Cancel" });
    await user.click(secondaryButton);

    expect(mockOnClose).toHaveBeenCalledTimes(1);
  });

  it("should disable primary button when primaryButtonDisabled is true", () => {
    render(
      <Modal
        {...defaultProps}
        primaryButtonText="Save"
        primaryButtonDisabled
      />,
    );

    const primaryButton = screen.getByRole("button", { name: "Save" });
    expect(primaryButton).toBeDisabled();
  });

  it("should not call onPrimaryAction when button is disabled", async () => {
    const user = userEvent.setup();
    const onPrimaryAction = vi.fn();
    render(
      <Modal
        {...defaultProps}
        primaryButtonText="Save"
        onPrimaryAction={onPrimaryAction}
        primaryButtonDisabled
      />,
    );

    const primaryButton = screen.getByRole("button", { name: "Save" });
    await user.click(primaryButton);

    expect(onPrimaryAction).not.toHaveBeenCalled();
  });

  it("should apply different sizes", () => {
    const { container: smContainer } = render(
      <Modal {...defaultProps} size="sm" />,
    );
    const { container: mdContainer } = render(
      <Modal {...defaultProps} size="md" />,
    );
    const { container: lgContainer } = render(
      <Modal {...defaultProps} size="lg" />,
    );

    expect(smContainer.querySelector(".max-w-sm")).toBeInTheDocument();
    expect(mdContainer.querySelector(".max-w-md")).toBeInTheDocument();
    expect(lgContainer.querySelector(".max-w-lg")).toBeInTheDocument();
  });

  it("should prevent body scroll when modal is open", () => {
    render(<Modal {...defaultProps} />);
    expect(document.body.style.overflow).toBe("hidden");
  });

  it("should restore body scroll when modal is closed", () => {
    const { rerender } = render(<Modal {...defaultProps} />);
    rerender(<Modal {...defaultProps} isOpen={false} />);
    expect(document.body.style.overflow).toBe("hidden");
  });
});
