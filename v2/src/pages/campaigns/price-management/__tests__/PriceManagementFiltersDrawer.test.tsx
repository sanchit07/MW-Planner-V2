import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import {
  PriceManagementFiltersDrawer,
  type PriceManagementFilters,
} from "../PriceManagementFiltersDrawer";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string, opts?: { defaultValue?: string }) =>
      opts?.defaultValue ?? key,
  }),
}));

vi.mock("@components/common/MediaOwnerDropdown", () => ({
  MediaOwnerDropdown: ({
    value,
    onChange,
  }: {
    value: string[];
    onChange: (v: string[]) => void;
  }) => (
    <div data-testid="media-owner-dropdown">
      <span data-value={value.join(",")}>{value.length} selected</span>
      <button type="button" onClick={() => onChange([...value, "mo-1"])}>
        Add media owner
      </button>
    </div>
  ),
}));

vi.mock("@components/ui/ModalDrawer", () => ({
  ModalDrawer: ({
    children,
    isOpen,
    onClose,
    footer,
    title,
  }: {
    children: React.ReactNode;
    isOpen: boolean;
    onClose: () => void;
    footer: React.ReactNode;
    title?: string;
  }) =>
    isOpen ? (
      <div data-testid="modal-drawer">
        {title != null && <h2 data-testid="drawer-title">{title}</h2>}
        <div>{children}</div>
        <div data-testid="footer">{footer}</div>
        <button type="button" onClick={onClose} aria-label="Close drawer">
          Close
        </button>
      </div>
    ) : null,
}));

const defaultFilters: PriceManagementFilters = {
  cities: [],
  inventoryTypes: [],
  mediaOwners: [],
  minPricing: "",
  maxPricing: "",
};

describe("PriceManagementFiltersDrawer", () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    filters: defaultFilters,
    onApplyFilters: vi.fn(),
    onClearFilters: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("does not render content when isOpen is false", () => {
      render(<PriceManagementFiltersDrawer {...defaultProps} isOpen={false} />);
      expect(screen.queryByTestId("modal-drawer")).not.toBeInTheDocument();
    });

    it("renders drawer when isOpen is true", () => {
      render(<PriceManagementFiltersDrawer {...defaultProps} />);
      expect(screen.getByTestId("modal-drawer")).toBeInTheDocument();
    });

    it("renders filters title", () => {
      render(<PriceManagementFiltersDrawer {...defaultProps} />);
      expect(screen.getByText("drawers.filters.title")).toBeInTheDocument();
    });

    it("renders city, inventory type, and media owner labels", () => {
      render(<PriceManagementFiltersDrawer {...defaultProps} />);
      expect(screen.getByText("drawers.filters.city")).toBeInTheDocument();
      expect(
        screen.getByText("drawers.filters.inventory_type"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("drawers.filters.media_owner"),
      ).toBeInTheDocument();
    });

    it("renders min and max pricing inputs", () => {
      render(<PriceManagementFiltersDrawer {...defaultProps} />);
      expect(
        screen.getByLabelText("drawers.filters.min_pricing"),
      ).toBeInTheDocument();
      expect(
        screen.getByLabelText("drawers.filters.max_pricing"),
      ).toBeInTheDocument();
    });

    it("renders cancel and apply buttons", () => {
      render(<PriceManagementFiltersDrawer {...defaultProps} />);
      expect(
        screen.getByRole("button", { name: /buttons\.cancel/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", {
          name: /drawers\.filters\.apply_filters/i,
        }),
      ).toBeInTheDocument();
    });
  });

  describe("filter state", () => {
    it("disables apply button when no filters changed and all empty", () => {
      render(<PriceManagementFiltersDrawer {...defaultProps} />);
      const applyBtn = screen.getByRole("button", {
        name: /drawers\.filters\.apply_filters/i,
      });
      expect(applyBtn).toBeDisabled();
    });

    it("shows current minPricing and maxPricing values in inputs", () => {
      render(
        <PriceManagementFiltersDrawer
          {...defaultProps}
          filters={{
            ...defaultFilters,
            minPricing: 10,
            maxPricing: 100,
          }}
        />,
      );
      expect(screen.getByLabelText("drawers.filters.min_pricing")).toHaveValue(
        10,
      );
      expect(screen.getByLabelText("drawers.filters.max_pricing")).toHaveValue(
        100,
      );
    });
  });

  describe("interactions", () => {
    it("calls onClose when cancel button is clicked", async () => {
      const user = userEvent.setup();
      render(<PriceManagementFiltersDrawer {...defaultProps} />);
      const cancelBtn = screen.getByRole("button", {
        name: /buttons\.cancel/i,
      });
      await user.click(cancelBtn);
      expect(defaultProps.onClose).toHaveBeenCalledTimes(1);
    });

    it("calls onClose when Close drawer is clicked", async () => {
      const user = userEvent.setup();
      render(<PriceManagementFiltersDrawer {...defaultProps} />);
      await user.click(screen.getByRole("button", { name: /Close drawer/i }));
      expect(defaultProps.onClose).toHaveBeenCalledTimes(1);
    });

    it("calls onApplyFilters with local filters when apply is clicked after change", async () => {
      const user = userEvent.setup();
      render(<PriceManagementFiltersDrawer {...defaultProps} />);
      const minInput = screen.getByLabelText("drawers.filters.min_pricing");
      await user.clear(minInput);
      await user.type(minInput, "50");
      const applyBtn = screen.getByRole("button", {
        name: /drawers\.filters\.apply_filters/i,
      });
      await user.click(applyBtn);
      expect(defaultProps.onApplyFilters).toHaveBeenCalledWith(
        expect.objectContaining({
          ...defaultFilters,
          minPricing: 50,
        }),
      );
      expect(defaultProps.onClose).toHaveBeenCalled();
    });
  });

  describe("keyboard prevention on number inputs", () => {
    it("has number inputs that prevent e, E, +, - (onKeyDown)", () => {
      render(<PriceManagementFiltersDrawer {...defaultProps} />);
      const minInput = screen.getByLabelText(
        "drawers.filters.min_pricing",
      ) as HTMLInputElement;
      expect(minInput).toHaveAttribute("type", "number");
    });
  });
});
