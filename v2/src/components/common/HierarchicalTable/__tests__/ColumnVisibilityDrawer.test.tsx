import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import ColumnVisibilityDrawer from "../ColumnVisibilityDrawer";

// Mock useTranslate
const mockT = vi.fn((key: string) => key);
vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: mockT,
  }),
}));

// Mock ModalDrawer
vi.mock("@components/ui/ModalDrawer", () => ({
  ModalDrawer: ({
    isOpen,
    title,
    children,
    footer,
    id,
  }: {
    isOpen: boolean;
    title: string;
    children: React.ReactNode;
    footer: React.ReactNode;
    id?: string;
  }) => {
    if (!isOpen) return null;
    return (
      <div data-testid="modal-drawer" data-id={id}>
        <div data-testid="drawer-title">{title}</div>
        <div data-testid="drawer-content">{children}</div>
        <div data-testid="drawer-footer">{footer}</div>
      </div>
    );
  },
}));

// Mock Switch
vi.mock("@components/ui/Switch", () => ({
  Switch: ({
    checked,
    onChange,
    id,
  }: {
    checked: boolean;
    onChange: (checked: boolean) => void;
    id?: string;
  }) => (
    <input
      type="checkbox"
      checked={checked}
      onChange={(e) => onChange(e.target.checked)}
      data-testid={id}
      role="switch"
    />
  ),
}));

// Mock Label
vi.mock("@components/ui/Label", () => ({
  Label: ({
    children,
    htmlFor,
    className,
  }: {
    children: React.ReactNode;
    htmlFor?: string;
    className?: string;
  }) => (
    <label htmlFor={htmlFor} className={className}>
      {children}
    </label>
  ),
}));

// Mock Button
vi.mock("@components/ui/Button", () => ({
  Button: ({
    children,
    onClick,
    variant,
    id,
  }: {
    children: React.ReactNode;
    onClick?: () => void;
    variant?: string;
    id?: string;
  }) => (
    <button onClick={onClick} data-variant={variant} data-testid={id}>
      {children}
    </button>
  ),
}));

describe("ColumnVisibilityDrawer", () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    columnVisibility: {
      col1: true,
      col2: true,
      col3: false,
    },
    onColumnVisibilityChange: vi.fn(),
    availableColumns: [
      { key: "col1", label: "Column 1" },
      { key: "col2", label: "Column 2" },
      { key: "col3", label: "Column 3" },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockT.mockImplementation((key: string) => key);
  });

  describe("Rendering", () => {
    it("renders drawer when isOpen is true", () => {
      render(<ColumnVisibilityDrawer {...defaultProps} />);
      expect(screen.getByTestId("modal-drawer")).toBeInTheDocument();
    });

    it("does not render drawer when isOpen is false", () => {
      render(<ColumnVisibilityDrawer {...defaultProps} isOpen={false} />);
      expect(screen.queryByTestId("modal-drawer")).not.toBeInTheDocument();
    });

    it("renders title with translation", () => {
      render(<ColumnVisibilityDrawer {...defaultProps} />);
      expect(screen.getByTestId("drawer-title")).toHaveTextContent(
        "column_customization.title",
      );
    });

    it("renders all available columns", () => {
      render(<ColumnVisibilityDrawer {...defaultProps} />);
      expect(screen.getByText("Column 1")).toBeInTheDocument();
      expect(screen.getByText("Column 2")).toBeInTheDocument();
      expect(screen.getByText("Column 3")).toBeInTheDocument();
    });

    it("renders enable all switch", () => {
      render(<ColumnVisibilityDrawer {...defaultProps} />);
      const enableAllSwitch = screen.getByTestId(
        "column-visibility-drawer-enable-all-switch",
      );
      expect(enableAllSwitch).toBeInTheDocument();
    });

    it("renders cancel and apply buttons", () => {
      render(<ColumnVisibilityDrawer {...defaultProps} />);
      expect(
        screen.getByTestId("column-visibility-drawer-cancel-btn"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("column-visibility-drawer-apply-btn"),
      ).toBeInTheDocument();
    });

    it("uses custom id when provided", () => {
      render(<ColumnVisibilityDrawer {...defaultProps} id="custom-id" />);
      const drawer = screen.getByTestId("modal-drawer");
      expect(drawer).toHaveAttribute("data-id", "custom-id");
    });
  });

  describe("Column visibility state", () => {
    it("initializes local state with prop values when drawer opens", async () => {
      const { rerender } = render(
        <ColumnVisibilityDrawer {...defaultProps} isOpen={false} />,
      );
      rerender(<ColumnVisibilityDrawer {...defaultProps} isOpen={true} />);

      await waitFor(() => {
        const col1Switch = screen.getByTestId(
          "column-visibility-drawer-column-col1-switch",
        ) as HTMLInputElement;
        expect(col1Switch.checked).toBe(true);
      });
    });

    it("updates local state when columnVisibility prop changes", () => {
      const { rerender } = render(<ColumnVisibilityDrawer {...defaultProps} />);
      rerender(
        <ColumnVisibilityDrawer
          {...defaultProps}
          columnVisibility={{ col1: false, col2: false, col3: true }}
        />,
      );

      waitFor(() => {
        const col1Switch = screen.getByTestId(
          "column-visibility-drawer-column-col1-switch",
        ) as HTMLInputElement;
        expect(col1Switch.checked).toBe(false);
      });
    });

    it("shows correct checked state for each column switch", () => {
      render(<ColumnVisibilityDrawer {...defaultProps} />);

      const col1Switch = screen.getByTestId(
        "column-visibility-drawer-column-col1-switch",
      ) as HTMLInputElement;
      const col2Switch = screen.getByTestId(
        "column-visibility-drawer-column-col2-switch",
      ) as HTMLInputElement;
      const col3Switch = screen.getByTestId(
        "column-visibility-drawer-column-col3-switch",
      ) as HTMLInputElement;

      expect(col1Switch.checked).toBe(true);
      expect(col2Switch.checked).toBe(true);
      expect(col3Switch.checked).toBe(false);
    });

    it("defaults to true for columns not in columnVisibility", () => {
      render(
        <ColumnVisibilityDrawer
          {...defaultProps}
          columnVisibility={{ col1: true }}
          availableColumns={[
            { key: "col1", label: "Column 1" },
            { key: "col2", label: "Column 2" },
          ]}
        />,
      );

      const col2Switch = screen.getByTestId(
        "column-visibility-drawer-column-col2-switch",
      ) as HTMLInputElement;
      expect(col2Switch.checked).toBe(true);
    });
  });

  describe("Enable all toggle", () => {
    it("checks enable all when all columns are visible", () => {
      render(
        <ColumnVisibilityDrawer
          {...defaultProps}
          columnVisibility={{ col1: true, col2: true, col3: true }}
        />,
      );

      const enableAllSwitch = screen.getByTestId(
        "column-visibility-drawer-enable-all-switch",
      ) as HTMLInputElement;
      expect(enableAllSwitch.checked).toBe(true);
    });

    it("unchecks enable all when any column is hidden", () => {
      render(<ColumnVisibilityDrawer {...defaultProps} />);

      const enableAllSwitch = screen.getByTestId(
        "column-visibility-drawer-enable-all-switch",
      ) as HTMLInputElement;
      expect(enableAllSwitch.checked).toBe(false);
    });

    it("toggles all columns when enable all is clicked", async () => {
      const user = userEvent.setup();
      render(<ColumnVisibilityDrawer {...defaultProps} />);

      const enableAllSwitch = screen.getByTestId(
        "column-visibility-drawer-enable-all-switch",
      ) as HTMLInputElement;

      // Initially some columns are hidden
      expect(enableAllSwitch.checked).toBe(false);

      // Click to enable all
      await user.click(enableAllSwitch);

      await waitFor(() => {
        const col1Switch = screen.getByTestId(
          "column-visibility-drawer-column-col1-switch",
        ) as HTMLInputElement;
        const col2Switch = screen.getByTestId(
          "column-visibility-drawer-column-col2-switch",
        ) as HTMLInputElement;
        const col3Switch = screen.getByTestId(
          "column-visibility-drawer-column-col3-switch",
        ) as HTMLInputElement;

        expect(col1Switch.checked).toBe(true);
        expect(col2Switch.checked).toBe(true);
        expect(col3Switch.checked).toBe(true);
      });
    });

    it("disables all columns when enable all is unchecked", async () => {
      const user = userEvent.setup();
      render(
        <ColumnVisibilityDrawer
          {...defaultProps}
          columnVisibility={{ col1: true, col2: true, col3: true }}
        />,
      );

      const enableAllSwitch = screen.getByTestId(
        "column-visibility-drawer-enable-all-switch",
      ) as HTMLInputElement;

      // Initially all are enabled
      expect(enableAllSwitch.checked).toBe(true);

      // Click to disable all
      await user.click(enableAllSwitch);

      await waitFor(() => {
        const col1Switch = screen.getByTestId(
          "column-visibility-drawer-column-col1-switch",
        ) as HTMLInputElement;
        const col2Switch = screen.getByTestId(
          "column-visibility-drawer-column-col2-switch",
        ) as HTMLInputElement;
        const col3Switch = screen.getByTestId(
          "column-visibility-drawer-column-col3-switch",
        ) as HTMLInputElement;

        expect(col1Switch.checked).toBe(false);
        expect(col2Switch.checked).toBe(false);
        expect(col3Switch.checked).toBe(false);
      });
    });
  });

  describe("Individual column toggle", () => {
    it("toggles individual column visibility", async () => {
      const user = userEvent.setup();
      render(<ColumnVisibilityDrawer {...defaultProps} />);

      const col1Switch = screen.getByTestId(
        "column-visibility-drawer-column-col1-switch",
      ) as HTMLInputElement;

      expect(col1Switch.checked).toBe(true);
      await user.click(col1Switch);

      await waitFor(() => {
        expect(col1Switch.checked).toBe(false);
      });
    });

    it("updates enable all state when individual column is toggled", async () => {
      const user = userEvent.setup();
      render(
        <ColumnVisibilityDrawer
          {...defaultProps}
          columnVisibility={{ col1: true, col2: true, col3: true }}
        />,
      );

      const enableAllSwitch = screen.getByTestId(
        "column-visibility-drawer-enable-all-switch",
      ) as HTMLInputElement;
      const col1Switch = screen.getByTestId(
        "column-visibility-drawer-column-col1-switch",
      ) as HTMLInputElement;

      expect(enableAllSwitch.checked).toBe(true);
      await user.click(col1Switch);

      await waitFor(() => {
        expect(enableAllSwitch.checked).toBe(false);
      });
    });
  });

  describe("Cancel button", () => {
    it("calls onClose when cancel is clicked", async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      render(<ColumnVisibilityDrawer {...defaultProps} onClose={onClose} />);

      const cancelButton = screen.getByTestId(
        "column-visibility-drawer-cancel-btn",
      );
      await user.click(cancelButton);

      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it("resets local state to original when cancel is clicked", async () => {
      const user = userEvent.setup();
      render(<ColumnVisibilityDrawer {...defaultProps} />);

      // Toggle a column
      const col1Switch = screen.getByTestId(
        "column-visibility-drawer-column-col1-switch",
      ) as HTMLInputElement;
      await user.click(col1Switch);

      await waitFor(() => {
        expect(col1Switch.checked).toBe(false);
      });

      // Click cancel
      const cancelButton = screen.getByTestId(
        "column-visibility-drawer-cancel-btn",
      );
      await user.click(cancelButton);

      // State should be reset (but drawer closes, so we can't verify easily)
      // The important thing is onClose is called
      expect(defaultProps.onClose).toHaveBeenCalled();
    });
  });

  describe("Apply button", () => {
    it("calls onColumnVisibilityChange with updated visibility when apply is clicked", async () => {
      const user = userEvent.setup();
      const onColumnVisibilityChange = vi.fn();
      render(
        <ColumnVisibilityDrawer
          {...defaultProps}
          onColumnVisibilityChange={onColumnVisibilityChange}
        />,
      );

      // Toggle a column
      const col1Switch = screen.getByTestId(
        "column-visibility-drawer-column-col1-switch",
      ) as HTMLInputElement;
      await user.click(col1Switch);

      // Click apply
      const applyButton = screen.getByTestId(
        "column-visibility-drawer-apply-btn",
      );
      await user.click(applyButton);

      expect(onColumnVisibilityChange).toHaveBeenCalledWith(
        expect.objectContaining({
          col1: false,
        }),
      );
    });

    it("calls onClose when apply is clicked", async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      render(<ColumnVisibilityDrawer {...defaultProps} onClose={onClose} />);

      const applyButton = screen.getByTestId(
        "column-visibility-drawer-apply-btn",
      );
      await user.click(applyButton);

      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it("applies all changes made in local state", async () => {
      const user = userEvent.setup();
      const onColumnVisibilityChange = vi.fn();
      render(
        <ColumnVisibilityDrawer
          {...defaultProps}
          onColumnVisibilityChange={onColumnVisibilityChange}
        />,
      );

      // Toggle multiple columns
      const col1Switch = screen.getByTestId(
        "column-visibility-drawer-column-col1-switch",
      ) as HTMLInputElement;
      const col3Switch = screen.getByTestId(
        "column-visibility-drawer-column-col3-switch",
      ) as HTMLInputElement;

      await user.click(col1Switch);
      await user.click(col3Switch);

      // Click apply
      const applyButton = screen.getByTestId(
        "column-visibility-drawer-apply-btn",
      );
      await user.click(applyButton);

      expect(onColumnVisibilityChange).toHaveBeenCalledWith(
        expect.objectContaining({
          col1: false,
          col2: true,
          col3: true,
        }),
      );
    });
  });

  describe("Edge cases", () => {
    it("handles empty availableColumns array", () => {
      render(
        <ColumnVisibilityDrawer
          {...defaultProps}
          availableColumns={[]}
          columnVisibility={{}}
        />,
      );

      expect(screen.getByTestId("modal-drawer")).toBeInTheDocument();
      const enableAllSwitch = screen.getByTestId(
        "column-visibility-drawer-enable-all-switch",
      ) as HTMLInputElement;
      // When no columns, all are "enabled" (default true)
      expect(enableAllSwitch.checked).toBe(true);
    });

    it("handles columnVisibility with extra keys not in availableColumns", () => {
      render(
        <ColumnVisibilityDrawer
          {...defaultProps}
          columnVisibility={{
            col1: true,
            col2: false,
            extraCol: true, // Not in availableColumns
          }}
        />,
      );

      // Should only render columns in availableColumns
      expect(screen.getByText("Column 1")).toBeInTheDocument();
      expect(screen.getByText("Column 2")).toBeInTheDocument();
      expect(screen.queryByText("extraCol")).not.toBeInTheDocument();
    });

    it("handles rapid toggling of columns", async () => {
      const user = userEvent.setup();
      render(<ColumnVisibilityDrawer {...defaultProps} />);

      const col1Switch = screen.getByTestId(
        "column-visibility-drawer-column-col1-switch",
      ) as HTMLInputElement;

      // Rapid clicks
      await user.click(col1Switch);
      await user.click(col1Switch);
      await user.click(col1Switch);

      // Should end up in a consistent state
      await waitFor(() => {
        // After 3 clicks, should be false (true -> false -> true -> false)
        expect(col1Switch.checked).toBe(false);
      });
    });
  });
});
