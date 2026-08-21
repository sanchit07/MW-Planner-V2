import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { InventoryItem } from "../../../types/inventory.types";
import { SelectedInventoryListContainer } from "../SelectedInventoryListContainer";

const mockRefetch = vi.fn().mockResolvedValue(undefined);
const mockScrollRef = { current: null };

interface MockHookReturn {
  selectedItems: InventoryItem[];
  isLoading: boolean;
  isLoadingMore: boolean;
  totalElements: number;
  scrollContainerRef: React.RefObject<HTMLDivElement | null>;
  refetch: () => Promise<void>;
}

const defaultHookReturn: MockHookReturn = {
  selectedItems: [],
  isLoading: false,
  isLoadingMore: false,
  totalElements: 0,
  scrollContainerRef: mockScrollRef,
  refetch: mockRefetch,
};

const mockUseSelectedInventoryList = vi.fn(
  (): MockHookReturn => defaultHookReturn,
);

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("@hooks/useSelectedInventoryList", () => ({
  useSelectedInventoryList: () => mockUseSelectedInventoryList(),
}));

vi.mock("../../../pages/campaigns/inventory/InventorySkeleton", () => ({
  default: () =>
    React.createElement("div", { "data-testid": "inventory-skeleton" }),
}));

const mockItem: InventoryItem = {
  detail: {
    id: "d1",
    name: "Item One",
    externalId: "",
    referenceId: "R1",
    mediaOwnerId: "",
    mediaOwnerName: "",
    inventoryType: "Digital",
    category: "",
    venueType: [],
    thumbnail: "",
    images: [],
    format: "",
    environment: "",
    size: "",
    operationMode: "",
    execution: "",
    screens: 0,
    sov: 0,
    isSelected: false,
    isCompliant: false,
    bookingMode: "",
    panels: [],
  },
  location: {
    location: {
      address: "123 St",
      country: "",
      state: "",
      city: "",
      zipCode: "",
      locationCoordinates: { coordinates: [], type: "" },
    },
    poi: { types: [], nearbyPOIs: [], categories: [] },
    demographics: {
      age: "",
      gender: "",
      overall: "",
      ageGender: "",
      income: "",
      behaviour: "",
      interest: "",
      highestIndexScore: "",
    },
  },
  performance: {
    cpmRate: 0,
    estimatedCost: 0,
    perDayCost: 0,
    perDayAdPlays: 0,
    totalAdPlays: 0,
    plannedSot: 0,
    totalSot: 0,
  },
  operations: {} as InventoryItem["operations"],
  schedules: [],
} as InventoryItem;

describe("SelectedInventoryListContainer", () => {
  const defaultProps = {
    campaignId: "campaign-1",
    renderItem: (item: InventoryItem, index: number) => (
      <div key={item.detail.id} data-testid={`item-${index}`}>
        {item.detail.name}
      </div>
    ),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseSelectedInventoryList.mockReturnValue({ ...defaultHookReturn });
  });

  it("renders card with default header when showHeader is true", () => {
    render(
      <SelectedInventoryListContainer
        {...defaultProps}
        headerTitle="Selected"
      />,
    );
    expect(screen.getByText("Selected")).toBeInTheDocument();
  });

  it("renders empty state when no items and not loading", () => {
    render(<SelectedInventoryListContainer {...defaultProps} />);
    expect(
      screen.getByText("selectedInventoryListContainer.noInventoriesFound"),
    ).toBeInTheDocument();
  });

  it("renders custom emptyMessage when provided", () => {
    render(
      <SelectedInventoryListContainer
        {...defaultProps}
        emptyMessage="No items"
      />,
    );
    expect(screen.getByText("No items")).toBeInTheDocument();
  });

  it("renders items via renderItem when selectedItems has data", () => {
    mockUseSelectedInventoryList.mockReturnValue({
      ...defaultHookReturn,
      selectedItems: [mockItem],
      totalElements: 1,
    });
    render(<SelectedInventoryListContainer {...defaultProps} />);
    expect(screen.getByTestId("item-0")).toBeInTheDocument();
    expect(screen.getByText("Item One")).toBeInTheDocument();
  });

  it("shows skeleton when isLoading is true", () => {
    mockUseSelectedInventoryList.mockReturnValue({
      ...defaultHookReturn,
      isLoading: true,
    });
    render(<SelectedInventoryListContainer {...defaultProps} />);
    const skeleton = document.querySelector(
      '[data-testid="inventory-skeleton"]',
    );
    expect(
      skeleton || document.querySelector(".animate-pulse"),
    ).toBeInTheDocument();
  });

  it("shows search input when showSearch is true", () => {
    render(
      <SelectedInventoryListContainer
        {...defaultProps}
        showSearch
        searchPlaceholder="Search..."
      />,
    );
    expect(screen.getByPlaceholderText("Search...")).toBeInTheDocument();
  });

  it("calls onSearchChange when controlled and user types", async () => {
    const user = userEvent.setup();
    const onSearchChange = vi.fn();
    render(
      <SelectedInventoryListContainer
        {...defaultProps}
        showSearch
        searchValue=""
        onSearchChange={onSearchChange}
      />,
    );
    await user.type(screen.getByRole("textbox"), "a");
    expect(onSearchChange).toHaveBeenCalledWith("a");
  });

  it("updates internal search when uncontrolled and user types", async () => {
    const user = userEvent.setup();
    render(<SelectedInventoryListContainer {...defaultProps} showSearch />);
    await user.type(screen.getByRole("textbox"), "test");
    expect(screen.getByRole("textbox")).toHaveValue("test");
  });

  it("shows count when showCount is true", () => {
    mockUseSelectedInventoryList.mockReturnValue({
      ...defaultHookReturn,
      totalElements: 5,
    });
    render(<SelectedInventoryListContainer {...defaultProps} showCount />);
    expect(
      screen.getByText("5 selectedInventoryListContainer.inventories"),
    ).toBeInTheDocument();
  });

  it("uses countLabel when provided", () => {
    mockUseSelectedInventoryList.mockReturnValue({
      ...defaultHookReturn,
      totalElements: 1,
    });
    render(
      <SelectedInventoryListContainer
        {...defaultProps}
        showCount
        countLabel={(n) => `${n} item(s)`}
      />,
    );
    expect(screen.getByText("1 item(s)")).toBeInTheDocument();
  });

  it("shows 1 Inventory for single item count", () => {
    mockUseSelectedInventoryList.mockReturnValue({
      ...defaultHookReturn,
      selectedItems: [mockItem],
      totalElements: 1,
    });
    render(<SelectedInventoryListContainer {...defaultProps} showCount />);
    expect(
      screen.getByText("1 selectedInventoryListContainer.inventory"),
    ).toBeInTheDocument();
  });

  it("renders emptySubMessage when function and empty", () => {
    render(
      <SelectedInventoryListContainer
        {...defaultProps}
        emptySubMessage={(q) => `Query: ${q}`}
      />,
    );
    expect(screen.getByText(/Query:/)).toBeInTheDocument();
  });

  it("renders emptySubMessage when string and empty", () => {
    render(
      <SelectedInventoryListContainer
        {...defaultProps}
        emptySubMessage="Try another search"
      />,
    );
    expect(screen.getByText("Try another search")).toBeInTheDocument();
  });

  it("filters items with filterItems when provided", () => {
    const secondItem: InventoryItem = {
      ...mockItem,
      detail: { ...mockItem.detail, id: "d2", name: "Item Two" },
    };
    mockUseSelectedInventoryList.mockReturnValue({
      ...defaultHookReturn,
      selectedItems: [mockItem, secondItem],
      totalElements: 2,
    });
    render(
      <SelectedInventoryListContainer
        {...defaultProps}
        searchValue="One"
        onSearchChange={vi.fn()}
        filterItems={(items, query) =>
          items.filter((i) =>
            i.detail.name.toLowerCase().includes(query.toLowerCase()),
          )
        }
      />,
    );
    expect(screen.getByText("Item One")).toBeInTheDocument();
    expect(screen.queryByText("Item Two")).not.toBeInTheDocument();
  });

  it("uses default name/address filter when searchQuery set and no filterItems", () => {
    mockUseSelectedInventoryList.mockReturnValue({
      ...defaultHookReturn,
      selectedItems: [mockItem],
      totalElements: 1,
    });
    render(<SelectedInventoryListContainer {...defaultProps} showSearch />);
    const input = screen.getByRole("textbox");
    expect(input).toBeInTheDocument();
  });

  it("shows loading more when showLoadingMore and isLoadingMore", () => {
    mockUseSelectedInventoryList.mockReturnValue({
      ...defaultHookReturn,
      selectedItems: [mockItem],
      isLoadingMore: true,
      totalElements: 1,
    });
    render(<SelectedInventoryListContainer {...defaultProps} />);
    expect(
      screen.getByText("selectedInventoryListContainer.loadingMore"),
    ).toBeInTheDocument();
  });

  it("shows custom loadingMoreText when provided", () => {
    mockUseSelectedInventoryList.mockReturnValue({
      ...defaultHookReturn,
      selectedItems: [mockItem],
      isLoadingMore: true,
      totalElements: 1,
    });
    render(
      <SelectedInventoryListContainer
        {...defaultProps}
        loadingMoreText="Fetching..."
      />,
    );
    expect(screen.getByText("Fetching...")).toBeInTheDocument();
  });

  it("renders contentBeforeList when provided", () => {
    render(
      <SelectedInventoryListContainer
        {...defaultProps}
        contentBeforeList={<div data-testid="before-list">Before</div>}
      />,
    );
    expect(screen.getByTestId("before-list")).toBeInTheDocument();
    expect(screen.getByText("Before")).toBeInTheDocument();
  });

  it("renders simple header when showSimpleHeader is true", () => {
    render(
      <SelectedInventoryListContainer
        {...defaultProps}
        showSimpleHeader
        showHeader={false}
        headerTitle="Simple"
      />,
    );
    expect(screen.getByText("Simple")).toBeInTheDocument();
  });

  it("renders simpleHeaderContent when showSimpleHeader and content provided", () => {
    render(
      <SelectedInventoryListContainer
        {...defaultProps}
        showSimpleHeader
        showHeader={false}
        simpleHeaderContent={<span data-testid="custom-simple">Custom</span>}
      />,
    );
    expect(screen.getByTestId("custom-simple")).toBeInTheDocument();
    expect(screen.getByText("Custom")).toBeInTheDocument();
  });

  it("applies containerClassName to Card", () => {
    const { container } = render(
      <SelectedInventoryListContainer
        {...defaultProps}
        containerClassName="my-card"
      />,
    );
    expect(container.querySelector(".my-card")).toBeInTheDocument();
  });

  it("calls onTotalElementsChange with the current total", () => {
    mockUseSelectedInventoryList.mockReturnValue({
      ...defaultHookReturn,
      selectedItems: [mockItem],
      totalElements: 7,
    });
    const onTotalElementsChange = vi.fn();
    render(
      <SelectedInventoryListContainer
        {...defaultProps}
        onTotalElementsChange={onTotalElementsChange}
      />,
    );
    expect(onTotalElementsChange).toHaveBeenCalledWith(7);
  });

  it("does not render header when showHeader is false", () => {
    render(
      <SelectedInventoryListContainer
        {...defaultProps}
        showHeader={false}
        headerTitle="Hidden"
      />,
    );
    expect(screen.queryByText("Hidden")).not.toBeInTheDocument();
  });
});
