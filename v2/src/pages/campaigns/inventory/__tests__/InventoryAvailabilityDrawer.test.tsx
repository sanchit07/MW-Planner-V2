import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { CampaignCreateResponse } from "../../../../types/campaign.types";
import { InventoryItem } from "../../../../types/inventory.types";
import InventoryAvailabilityDrawer from "../InventoryAvailabilityDrawer";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

// Mock the SelectedInventoryAvailability component
vi.mock("../SelectedInventoryAvailability", () => ({
  default: ({ inventories }: { inventories: InventoryItem[] }) => (
    <div data-testid="selected-inventory-availability">
      <span data-testid="inventory-count">{inventories.length}</span>
    </div>
  ),
}));

// Mock the useSelectedInventoryList hook
const mockReset = vi.fn();
const mockLoadSelectedInventory = vi.fn();

vi.mock("@hooks/useSelectedInventoryList", () => ({
  useSelectedInventoryList: ({
    campaignId,
    enabled,
  }: {
    campaignId: string;
    enabled: boolean;
  }) => ({
    selectedItems: enabled && campaignId ? mockSelectedItems : [],
    isLoading: mockIsLoading,
    reset: mockReset,
    loadSelectedInventory: mockLoadSelectedInventory,
  }),
}));

// Mock ModalDrawer
vi.mock("@components/ui/ModalDrawer", () => ({
  ModalDrawer: ({
    isOpen,
    onClose,
    title,
    children,
  }: {
    isOpen: boolean;
    onClose: () => void;
    title: string;
    children: React.ReactNode;
  }) =>
    isOpen ? (
      <div data-testid="modal-drawer">
        <div data-testid="drawer-title">{title}</div>
        <button onClick={onClose} data-testid="close-button">
          Close
        </button>
        <div data-testid="drawer-content">{children}</div>
      </div>
    ) : null,
}));

// Test state variables
let mockSelectedItems: InventoryItem[] = [];
let mockIsLoading = false;

const createMockInventoryItem = (id: string): InventoryItem =>
  ({
    detail: {
      id,
      name: `Inventory ${id}`,
      externalId: `ext-${id}`,
      inventoryType: "Digital",
    },
    operations: {
      clientPerLoop: 10,
      slotDuration: 6,
    },
    location: {
      location: {},
    },
    performance: {},
    schedules: [],
  }) as unknown as InventoryItem;

const createMockCampaignData = (): CampaignCreateResponse =>
  ({
    id: "campaign-1",
    name: "Test Campaign",
    startDate: "2026-02-01",
    endDate: "2026-02-28",
  }) as CampaignCreateResponse;

describe("InventoryAvailabilityDrawer", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSelectedItems = [];
    mockIsLoading = false;
  });

  describe("Drawer State", () => {
    it("should not render when closed", () => {
      render(
        <InventoryAvailabilityDrawer
          isOpen={false}
          onClose={vi.fn()}
          campaignId="campaign-1"
          campaignData={createMockCampaignData()}
        />,
      );

      expect(screen.queryByTestId("modal-drawer")).not.toBeInTheDocument();
    });

    it("should render when open", () => {
      mockSelectedItems = [createMockInventoryItem("1")];

      render(
        <InventoryAvailabilityDrawer
          isOpen={true}
          onClose={vi.fn()}
          campaignId="campaign-1"
          campaignData={createMockCampaignData()}
        />,
      );

      expect(screen.getByTestId("modal-drawer")).toBeInTheDocument();
    });

    it("should display correct title", () => {
      mockSelectedItems = [createMockInventoryItem("1")];

      render(
        <InventoryAvailabilityDrawer
          isOpen={true}
          onClose={vi.fn()}
          campaignId="campaign-1"
          campaignData={createMockCampaignData()}
        />,
      );

      expect(screen.getByTestId("drawer-title")).toHaveTextContent(
        "inventoryAvailability.title",
      );
    });
  });

  describe("Loading State", () => {
    it("should show loading message when loading", () => {
      mockIsLoading = true;

      render(
        <InventoryAvailabilityDrawer
          isOpen={true}
          onClose={vi.fn()}
          campaignId="campaign-1"
          campaignData={createMockCampaignData()}
        />,
      );

      expect(
        screen.getByText("inventoryAvailability.loadingInventories"),
      ).toBeInTheDocument();
    });
  });

  describe("Empty State", () => {
    it("should show empty message when no inventories", () => {
      mockSelectedItems = [];
      mockIsLoading = false;

      render(
        <InventoryAvailabilityDrawer
          isOpen={true}
          onClose={vi.fn()}
          campaignId="campaign-1"
          campaignData={createMockCampaignData()}
        />,
      );

      expect(
        screen.getByText("inventoryAvailability.noSelectedInventories"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("inventoryAvailability.pleaseSelectInventories"),
      ).toBeInTheDocument();
    });
  });

  describe("With Inventories", () => {
    it("should render SelectedInventoryAvailability with inventories", () => {
      mockSelectedItems = [
        createMockInventoryItem("1"),
        createMockInventoryItem("2"),
      ];

      render(
        <InventoryAvailabilityDrawer
          isOpen={true}
          onClose={vi.fn()}
          campaignId="campaign-1"
          campaignData={createMockCampaignData()}
        />,
      );

      expect(
        screen.getByTestId("selected-inventory-availability"),
      ).toBeInTheDocument();
      expect(screen.getByTestId("inventory-count")).toHaveTextContent("2");
    });
  });

  describe("Reset and Refetch", () => {
    it("should call reset when drawer opens", async () => {
      mockSelectedItems = [createMockInventoryItem("1")];

      const { rerender } = render(
        <InventoryAvailabilityDrawer
          isOpen={false}
          onClose={vi.fn()}
          campaignId="campaign-1"
          campaignData={createMockCampaignData()}
        />,
      );

      // Open the drawer
      rerender(
        <InventoryAvailabilityDrawer
          isOpen={true}
          onClose={vi.fn()}
          campaignId="campaign-1"
          campaignData={createMockCampaignData()}
        />,
      );

      await waitFor(() => {
        expect(mockReset).toHaveBeenCalled();
      });
    });

    it("should call loadSelectedInventory when drawer opens", async () => {
      mockSelectedItems = [createMockInventoryItem("1")];

      const { rerender } = render(
        <InventoryAvailabilityDrawer
          isOpen={false}
          onClose={vi.fn()}
          campaignId="campaign-1"
          campaignData={createMockCampaignData()}
        />,
      );

      rerender(
        <InventoryAvailabilityDrawer
          isOpen={true}
          onClose={vi.fn()}
          campaignId="campaign-1"
          campaignData={createMockCampaignData()}
        />,
      );

      await waitFor(() => {
        expect(mockLoadSelectedInventory).toHaveBeenCalledWith(0, false);
      });
    });
  });

  describe("Close Handler", () => {
    it("should call onClose when close button is clicked", async () => {
      mockSelectedItems = [createMockInventoryItem("1")];
      const onClose = vi.fn();

      render(
        <InventoryAvailabilityDrawer
          isOpen={true}
          onClose={onClose}
          campaignId="campaign-1"
          campaignData={createMockCampaignData()}
        />,
      );

      const closeButton = screen.getByTestId("close-button");
      closeButton.click();

      expect(onClose).toHaveBeenCalled();
    });
  });

  describe("Props Handling", () => {
    it("should handle undefined campaignId", () => {
      render(
        <InventoryAvailabilityDrawer
          isOpen={true}
          onClose={vi.fn()}
          campaignId=""
          campaignData={createMockCampaignData()}
        />,
      );

      // Should still render, showing empty state
      expect(screen.getByTestId("modal-drawer")).toBeInTheDocument();
    });

    it("should handle null campaignData", () => {
      mockSelectedItems = [createMockInventoryItem("1")];

      render(
        <InventoryAvailabilityDrawer
          isOpen={true}
          onClose={vi.fn()}
          campaignId="campaign-1"
          campaignData={null}
        />,
      );

      expect(
        screen.getByTestId("selected-inventory-availability"),
      ).toBeInTheDocument();
    });
  });
});
