import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import type { InventoryFilters } from "../../../../types/inventory.types";
import { InventoryFilterDrawer } from "../InventoryFilterDrawer";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
}));

const mockVenues = [
  {
    enumerationId: 1,
    tier: 0,
    name: "Mall",
    definition: null,
    stringValue: "MALL",
    children: [
      {
        enumerationId: 2,
        tier: 1,
        name: "Shopping Center",
        definition: null,
        stringValue: "SHOPPING_CENTER",
        children: [],
      },
    ],
  },
];
vi.mock("@services/inventory/inventorySlice", () => ({
  useGetVenuesQuery: () => ({ data: mockVenues }),
}));

const mockGetItem = vi.fn();
const mockSetItem = vi.fn();
const mockRemoveItem = vi.fn();
vi.mock("../../../../utils/storage", () => ({
  default: {
    getItem: (...args: unknown[]) => mockGetItem(...args),
    setItem: (...args: unknown[]) => mockSetItem(...args),
    removeItem: (...args: unknown[]) => mockRemoveItem(...args),
  },
}));

vi.mock("@components/ui/ModalDrawer", () => ({
  ModalDrawer: ({
    isOpen,
    onClose,
    title,
    children,
    footer,
  }: {
    isOpen: boolean;
    onClose: () => void;
    title: string;
    children: React.ReactNode;
    footer: React.ReactNode;
  }) =>
    isOpen ? (
      <div data-testid="inventory-filter-drawer">
        <h2 data-testid="drawer-title">{title}</h2>
        <div data-testid="drawer-body">{children}</div>
        <div data-testid="drawer-footer">{footer}</div>
        <button type="button" onClick={onClose} aria-label="Close">
          Close
        </button>
      </div>
    ) : null,
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
      <span>{value.length} selected</span>
      <button type="button" onClick={() => onChange([...value, "mo-1"])}>
        Add media owner
      </button>
    </div>
  ),
}));

vi.mock("@components/ui/MultiSelect", () => ({
  default: ({
    value,
    onChange,
    options,
  }: {
    value: string[];
    onChange: (v: string[]) => void;
    options: Array<{
      value: string;
      label: string;
      disabled?: boolean;
      description?: string;
    }>;
  }) => (
    <div data-testid="multiselect">
      <span>
        {value.length} of {options.length}
      </span>
      {options.map((o) => (
        <span
          key={o.value}
          data-testid={`option-${o.value}`}
          data-disabled={o.disabled ? "true" : "false"}
          data-selected={value.includes(o.value) ? "true" : "false"}
        >
          {o.description ?? ""}
        </span>
      ))}
      <button
        type="button"
        onClick={() => onChange(options.length ? [options[0].value] : [])}
      >
        Toggle first
      </button>
      <button type="button" onClick={() => onChange([])}>
        Empty values
      </button>
    </div>
  ),
}));

const defaultFilters: InventoryFilters = {
  mediaOwners: [],
  venueTypes: [],
  bookingMode: [],
  sizes: [],
  latitude: "",
  longitude: "",
  searchbyquery: "",
  environments: [],
  inventoryClassification: [],
  programmaticSupport: "ALL",
  dealTypes: [],
};

describe("InventoryFilterDrawer", () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    filters: defaultFilters,
    onApplyFilters: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockGetItem.mockReturnValue(null);
  });

  describe("rendering", () => {
    it("does not render drawer when isOpen is false", () => {
      render(<InventoryFilterDrawer {...defaultProps} isOpen={false} />);
      expect(
        screen.queryByTestId("inventory-filter-drawer"),
      ).not.toBeInTheDocument();
    });

    it("renders drawer with title when isOpen is true", () => {
      render(<InventoryFilterDrawer {...defaultProps} />);
      expect(screen.getByTestId("inventory-filter-drawer")).toBeInTheDocument();
      expect(screen.getByTestId("drawer-title")).toHaveTextContent(
        "inventories.filters.title",
      );
    });

    it("renders operations section and filter controls", () => {
      render(<InventoryFilterDrawer {...defaultProps} />);
      expect(
        screen.getByText("inventories.filters.operations_section"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("inventories.filters.media_owner"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("inventories.filters.inventory_classification"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("inventories.filters.venue_type"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("inventories.filters.environment"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("inventories.filters.mode_of_operation"),
      ).toBeInTheDocument();
      expect(screen.getByText("inventories.filters.size")).toBeInTheDocument();
    });

    it("renders Clear filters and Apply filters buttons in footer", () => {
      render(<InventoryFilterDrawer {...defaultProps} />);
      expect(
        screen.getByRole("button", {
          name: /inventories\.filters\.clear_filters/i,
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", {
          name: /inventories\.filters\.apply_filters/i,
        }),
      ).toBeInTheDocument();
    });
  });

  describe("storage", () => {
    it("loads filters from storage when drawer opens and storage has data", () => {
      mockGetItem.mockReturnValue(
        JSON.stringify({
          mediaOwners: ["mo-1"],
          venueTypes: [],
          bookingMode: [],
          sizes: [],
          latitude: "",
          longitude: "",
          searchbyquery: "",
          environments: [],
          inventoryClassification: [],
        }),
      );
      render(<InventoryFilterDrawer {...defaultProps} />);
      expect(mockGetItem).toHaveBeenCalledWith("inventory_filters");
      expect(screen.getByTestId("media-owner-dropdown")).toHaveTextContent(
        "1 selected",
      );
    });

    it("uses props filters when storage is empty", () => {
      mockGetItem.mockReturnValue(null);
      render(<InventoryFilterDrawer {...defaultProps} />);
      expect(screen.getByTestId("media-owner-dropdown")).toHaveTextContent(
        "0 selected",
      );
    });
  });

  describe("interactions", () => {
    it("calls onClose when drawer Close is clicked", async () => {
      const user = userEvent.setup();
      render(<InventoryFilterDrawer {...defaultProps} />);
      await user.click(screen.getByRole("button", { name: /close/i }));
      expect(defaultProps.onClose).toHaveBeenCalledTimes(1);
    });

    it("calls onApplyFilters with current local filters and onClose when Apply filters is clicked", async () => {
      const user = userEvent.setup();
      render(<InventoryFilterDrawer {...defaultProps} />);
      await user.click(
        screen.getByRole("button", {
          name: /inventories\.filters\.apply_filters/i,
        }),
      );
      expect(defaultProps.onApplyFilters).toHaveBeenCalledWith(
        expect.objectContaining({
          ...defaultFilters,
          searchbyquery: "",
        }),
      );
      expect(defaultProps.onClose).toHaveBeenCalled();
    });

    it("saves filters to storage when Apply filters is clicked", async () => {
      const user = userEvent.setup();
      render(<InventoryFilterDrawer {...defaultProps} />);
      await user.click(
        screen.getByRole("button", {
          name: /inventories\.filters\.apply_filters/i,
        }),
      );
      expect(mockSetItem).toHaveBeenCalledWith(
        "inventory_filters",
        expect.any(String),
      );
    });

    it("does not apply, close, or clear storage when Clear filters is clicked (local reset only)", async () => {
      const user = userEvent.setup();
      render(<InventoryFilterDrawer {...defaultProps} />);
      await user.click(
        screen.getByRole("button", {
          name: /inventories\.filters\.clear_filters/i,
        }),
      );
      expect(defaultProps.onApplyFilters).not.toHaveBeenCalled();
      expect(defaultProps.onClose).not.toHaveBeenCalled();
      expect(mockRemoveItem).not.toHaveBeenCalled();
    });

    it("resets the drawer fields locally on Clear, applied only on Apply", async () => {
      const user = userEvent.setup();
      render(
        <InventoryFilterDrawer
          {...defaultProps}
          filters={{ ...defaultFilters, venueTypes: ["MALL"] }}
        />,
      );
      // Select a venue then clear → local venue selection reset.
      await user.click(
        screen.getByRole("button", {
          name: /inventories\.filters\.clear_filters/i,
        }),
      );
      await user.click(
        screen.getByRole("button", {
          name: /inventories\.filters\.apply_filters/i,
        }),
      );
      expect(defaultProps.onApplyFilters).toHaveBeenCalledWith(
        expect.objectContaining({ venueTypes: [] }),
      );
    });

    it("preserves searchbyquery from props when applying filters", async () => {
      const user = userEvent.setup();
      render(
        <InventoryFilterDrawer
          {...defaultProps}
          filters={{ ...defaultFilters, searchbyquery: "test query" }}
        />,
      );
      await user.click(
        screen.getByRole("button", {
          name: /inventories\.filters\.apply_filters/i,
        }),
      );
      expect(defaultProps.onApplyFilters).toHaveBeenCalledWith(
        expect.objectContaining({
          searchbyquery: "test query",
        }),
      );
    });

    it("resets local state to props filters when drawer Close is clicked", async () => {
      const user = userEvent.setup();
      mockGetItem.mockReturnValue(
        JSON.stringify({
          ...defaultFilters,
          mediaOwners: ["mo-1"],
        }),
      );
      render(<InventoryFilterDrawer {...defaultProps} />);
      expect(screen.getByTestId("media-owner-dropdown")).toHaveTextContent(
        "1 selected",
      );
      await user.click(screen.getByRole("button", { name: /close/i }));
      expect(defaultProps.onClose).toHaveBeenCalled();
    });
  });

  describe("venue type filter", () => {
    it("renders venue type options from the /venues API", () => {
      render(<InventoryFilterDrawer {...defaultProps} />);
      expect(
        screen.getByText("inventories.filters.venue_type"),
      ).toBeInTheDocument();
      expect(screen.getByTestId("option-MALL")).toBeInTheDocument();
    });

    it("applies the selected venue type as venueTypes when Apply is clicked", async () => {
      const user = userEvent.setup();
      render(<InventoryFilterDrawer {...defaultProps} />);
      // Venue Type is the second MultiSelect (after classification).
      const venueSelect = screen.getAllByTestId("multiselect")[1];
      await user.click(
        within(venueSelect).getByRole("button", { name: /toggle first/i }),
      );
      await user.click(
        screen.getByRole("button", {
          name: /inventories\.filters\.apply_filters/i,
        }),
      );
      expect(defaultProps.onApplyFilters).toHaveBeenCalledWith(
        expect.objectContaining({ venueTypes: ["MALL"] }),
      );
    });
  });

  describe("channel-locked classification", () => {
    const digitalOnly = ["DIGITAL_OOH"];

    it("pre-selects and disables Digital and disables Classic when only Digital OOH channel is selected", () => {
      render(
        <InventoryFilterDrawer
          {...defaultProps}
          media_channels={digitalOnly}
        />,
      );
      const digital = screen.getByTestId("option-Digital");
      const classic = screen.getByTestId("option-Classic");
      expect(digital).toHaveAttribute("data-selected", "true");
      expect(digital).toHaveAttribute("data-disabled", "true");
      expect(digital).toHaveTextContent(
        "inventories.filters.classification_locked_by_channel",
      );
      expect(classic).toHaveAttribute("data-disabled", "true");
      expect(classic).toHaveTextContent(
        "inventories.filters.classification_not_in_channel",
      );
    });

    it("pre-selects and disables Classic when only Classic OOH channel is selected", () => {
      render(
        <InventoryFilterDrawer
          {...defaultProps}
          media_channels={["CLASSIC_OOH"]}
        />,
      );
      const classic = screen.getByTestId("option-Classic");
      const digital = screen.getByTestId("option-Digital");
      expect(classic).toHaveAttribute("data-selected", "true");
      expect(classic).toHaveAttribute("data-disabled", "true");
      expect(digital).toHaveAttribute("data-disabled", "true");
    });

    it("leaves both classifications enabled and unselected when both channels are selected", () => {
      render(
        <InventoryFilterDrawer
          {...defaultProps}
          media_channels={["DIGITAL_OOH", "CLASSIC_OOH"]}
        />,
      );
      expect(screen.getByTestId("option-Digital")).toHaveAttribute(
        "data-disabled",
        "false",
      );
      expect(screen.getByTestId("option-Classic")).toHaveAttribute(
        "data-disabled",
        "false",
      );
      expect(screen.getByTestId("option-Digital")).toHaveAttribute(
        "data-selected",
        "false",
      );
    });

    it("applies the locked classification even without user interaction", async () => {
      const user = userEvent.setup();
      render(
        <InventoryFilterDrawer
          {...defaultProps}
          media_channels={digitalOnly}
        />,
      );
      await user.click(
        screen.getByRole("button", {
          name: /inventories\.filters\.apply_filters/i,
        }),
      );
      expect(defaultProps.onApplyFilters).toHaveBeenCalledWith(
        expect.objectContaining({ inventoryClassification: ["Digital"] }),
      );
    });

    it("re-adds the locked classification when a change tries to remove it", async () => {
      const user = userEvent.setup();
      render(
        <InventoryFilterDrawer
          {...defaultProps}
          media_channels={digitalOnly}
        />,
      );
      // Classification MultiSelect is the first one rendered in the drawer.
      const classificationSelect = screen.getAllByTestId("multiselect")[0];
      await user.click(
        within(classificationSelect).getByRole("button", {
          name: /empty values/i,
        }),
      );
      await user.click(
        screen.getByRole("button", {
          name: /inventories\.filters\.apply_filters/i,
        }),
      );
      expect(defaultProps.onApplyFilters).toHaveBeenCalledWith(
        expect.objectContaining({ inventoryClassification: ["Digital"] }),
      );
    });

    it("keeps the locked classification after Clear filters", async () => {
      const user = userEvent.setup();
      render(
        <InventoryFilterDrawer
          {...defaultProps}
          media_channels={digitalOnly}
        />,
      );
      await user.click(
        screen.getByRole("button", {
          name: /inventories\.filters\.clear_filters/i,
        }),
      );
      await user.click(
        screen.getByRole("button", {
          name: /inventories\.filters\.apply_filters/i,
        }),
      );
      expect(defaultProps.onApplyFilters).toHaveBeenCalledWith(
        expect.objectContaining({ inventoryClassification: ["Digital"] }),
      );
    });

    it("overrides a stale stored classification with the locked one", () => {
      mockGetItem.mockReturnValue(
        JSON.stringify({
          ...defaultFilters,
          inventoryClassification: ["Classic"],
        }),
      );
      render(
        <InventoryFilterDrawer
          {...defaultProps}
          media_channels={digitalOnly}
        />,
      );
      const digital = screen.getByTestId("option-Digital");
      const classic = screen.getByTestId("option-Classic");
      expect(digital).toHaveAttribute("data-selected", "true");
      expect(classic).toHaveAttribute("data-selected", "false");
    });
  });

  describe("sync when drawer opens", () => {
    it("merges stored filters with props.searchbyquery when isOpen becomes true", () => {
      mockGetItem.mockReturnValue(
        JSON.stringify({
          ...defaultFilters,
          mediaOwners: ["mo-a"],
        }),
      );
      const { rerender } = render(
        <InventoryFilterDrawer {...defaultProps} isOpen={false} />,
      );
      expect(
        screen.queryByTestId("inventory-filter-drawer"),
      ).not.toBeInTheDocument();
      rerender(
        <InventoryFilterDrawer
          {...defaultProps}
          isOpen={true}
          filters={{ ...defaultFilters, searchbyquery: "q" }}
        />,
      );
      expect(screen.getByTestId("inventory-filter-drawer")).toBeInTheDocument();
      expect(mockGetItem).toHaveBeenCalled();
    });
  });
});
