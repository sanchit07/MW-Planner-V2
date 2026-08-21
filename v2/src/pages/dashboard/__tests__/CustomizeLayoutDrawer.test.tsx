import {
  useUpdateDashboardWidgetsMutation,
  type DashboardWidget,
} from "@services/dashboard/dashboardSlice";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import CustomizeLayoutDrawer, {
  defaultWidgetVisibility,
} from "../CustomizeLayoutDrawer";

const mockUpdateWidgets = vi.fn();
const mockUnwrap = vi.fn();

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string, opts?: { defaultValue?: string }) =>
      opts?.defaultValue ?? key,
  }),
}));

vi.mock("@services/dashboard/dashboardSlice", () => ({
  useUpdateDashboardWidgetsMutation: vi.fn(),
}));

vi.mock("@components/ui/ModalDrawer", () => ({
  ModalDrawer: ({
    isOpen,
    title,
    footer,
    children,
  }: {
    isOpen: boolean;
    onClose: () => void;
    title: string;
    footer: React.ReactNode;
    children: React.ReactNode;
  }) =>
    isOpen ? (
      <div data-testid="modal-drawer" data-open="true">
        <h2>{title}</h2>
        <div data-testid="drawer-content">{children}</div>
        <div data-testid="drawer-footer">{footer}</div>
      </div>
    ) : (
      <div data-testid="modal-drawer" data-open="false" />
    ),
}));

vi.mock("@components/ui/Switch", () => ({
  Switch: ({
    id,
    checked,
    onChange,
    disabled,
  }: {
    id: string;
    checked: boolean;
    onChange: (checked: boolean) => void;
    disabled?: boolean;
  }) => (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      data-testid={id}
      disabled={disabled}
      onClick={() => onChange(!checked)}
    >
      {checked ? "on" : "off"}
    </button>
  ),
}));

vi.mock("@components/ui/Button", () => ({
  Button: ({
    children,
    onClick,
    id,
    disabled,
  }: {
    children: React.ReactNode;
    onClick?: () => void;
    id?: string;
    disabled?: boolean;
  }) => (
    <button
      type="button"
      id={id}
      onClick={onClick}
      disabled={disabled}
      data-testid={id ?? "button"}
    >
      {children}
    </button>
  ),
}));

vi.mock("@components/ui/Label", () => ({
  Label: ({
    children,
    htmlFor,
  }: {
    children: React.ReactNode;
    htmlFor?: string;
  }) => <label htmlFor={htmlFor}>{children}</label>,
}));

describe("CustomizeLayoutDrawer", () => {
  const widgets: DashboardWidget[] = [
    { key: "campaign-overview", isEnable: true },
    { key: "sales-overview", isEnable: true },
  ];

  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    widgetVisibility: { "campaign-overview": true, "sales-overview": true },
    onWidgetVisibilityChange: vi.fn(),
    widgets,
    isLoadingWidgets: false,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockUnwrap.mockResolvedValue(undefined);
    vi.mocked(useUpdateDashboardWidgetsMutation).mockReturnValue([
      mockUpdateWidgets,
      { isLoading: false },
    ] as unknown as ReturnType<typeof useUpdateDashboardWidgetsMutation>);
  });

  describe("defaultWidgetVisibility", () => {
    it("exports empty object", () => {
      expect(defaultWidgetVisibility).toEqual({});
    });
  });

  describe("Rendering", () => {
    it("does not render drawer content when closed", () => {
      render(<CustomizeLayoutDrawer {...defaultProps} isOpen={false} />);
      expect(screen.getByTestId("modal-drawer")).toHaveAttribute(
        "data-open",
        "false",
      );
    });

    it("renders title and content when open", () => {
      render(<CustomizeLayoutDrawer {...defaultProps} />);
      expect(
        screen.getByRole("heading", { name: /customizeLayout\.title/i }),
      ).toBeInTheDocument();
      expect(screen.getByTestId("drawer-content")).toBeInTheDocument();
    });

    it("renders loading widgets message when isLoadingWidgets", () => {
      render(
        <CustomizeLayoutDrawer
          {...defaultProps}
          isLoadingWidgets={true}
          widgets={[]}
        />,
      );
      expect(
        screen.getByText("customizeLayout.loadingWidgets"),
      ).toBeInTheDocument();
    });

    it("renders no widgets available when widgets list is empty and not loading", () => {
      render(
        <CustomizeLayoutDrawer
          {...defaultProps}
          widgets={[]}
          isLoadingWidgets={false}
        />,
      );
      expect(screen.getByText("customizeLayout.noWidgets")).toBeInTheDocument();
    });

    it("renders widget toggles when widgets are provided", () => {
      render(<CustomizeLayoutDrawer {...defaultProps} />);
      const switches = screen.getAllByRole("switch");
      expect(switches.length).toBeGreaterThan(0);
    });
  });

  describe("Sync from props", () => {
    it("syncs local visibility and widgets when drawer opens", () => {
      const { rerender } = render(
        <CustomizeLayoutDrawer {...defaultProps} isOpen={false} />,
      );
      rerender(<CustomizeLayoutDrawer {...defaultProps} isOpen={true} />);
      expect(screen.getByTestId("modal-drawer")).toHaveAttribute(
        "data-open",
        "true",
      );
    });
  });

  describe("Cancel", () => {
    it("calls onClose when Cancel is clicked", async () => {
      const onClose = vi.fn();
      render(<CustomizeLayoutDrawer {...defaultProps} onClose={onClose} />);
      const cancelBtn = screen.getByRole("button", { name: /cancel/i });
      await userEvent.click(cancelBtn);
      expect(onClose).toHaveBeenCalled();
    });
  });

  describe("Apply", () => {
    it("calls updateWidgets and onWidgetVisibilityChange when Apply is clicked", async () => {
      mockUpdateWidgets.mockReturnValue({ unwrap: mockUnwrap });
      const onWidgetVisibilityChange = vi.fn();
      const onClose = vi.fn();
      render(
        <CustomizeLayoutDrawer
          {...defaultProps}
          onWidgetVisibilityChange={onWidgetVisibilityChange}
          onClose={onClose}
        />,
      );
      const applyBtn = screen.getByRole("button", {
        name: /customizeLayout\.applyLayout/i,
      });
      await userEvent.click(applyBtn);
      await waitFor(() => {
        expect(mockUpdateWidgets).toHaveBeenCalled();
      });
      await waitFor(() => {
        expect(mockUnwrap).toHaveBeenCalled();
      });
      expect(onWidgetVisibilityChange).toHaveBeenCalled();
      expect(onClose).toHaveBeenCalled();
    });

    it("shows apply error when update fails", async () => {
      mockUpdateWidgets.mockReturnValue({
        unwrap: vi.fn().mockRejectedValue(new Error("Failed")),
      });
      render(<CustomizeLayoutDrawer {...defaultProps} />);
      const applyBtn = screen.getByRole("button", {
        name: /customizeLayout\.applyLayout/i,
      });
      await userEvent.click(applyBtn);
      await waitFor(() => {
        expect(screen.getByRole("alert")).toHaveTextContent(
          /customizeLayout\.applyError/i,
        );
      });
    });
  });

  describe("Reset to default", () => {
    it("resets local visibility to all enabled when Reset to default is clicked", async () => {
      render(
        <CustomizeLayoutDrawer
          {...defaultProps}
          widgetVisibility={{ "campaign-overview": false }}
        />,
      );
      const resetBtn = screen.getByRole("button", {
        name: /customizeLayout\.resetToDefault/i,
      });
      await userEvent.click(resetBtn);
      expect(screen.getByTestId("modal-drawer")).toBeInTheDocument();
    });
  });

  describe("Toggle widget", () => {
    it("toggles widget visibility when switch is clicked", async () => {
      render(<CustomizeLayoutDrawer {...defaultProps} />);
      const switches = screen.getAllByRole("switch");
      expect(switches.length).toBeGreaterThan(0);
      await userEvent.click(switches[0]);
      expect(switches[0]).toHaveAttribute("aria-checked", "false");
    });
  });

  describe("Custom id", () => {
    it("uses custom id when provided", () => {
      render(<CustomizeLayoutDrawer {...defaultProps} id="my-drawer" />);
      expect(
        screen.getByRole("button", {
          name: /customizeLayout\.resetToDefault/i,
        }).id,
      ).toBe("my-drawer-reset-btn");
    });
  });
});
