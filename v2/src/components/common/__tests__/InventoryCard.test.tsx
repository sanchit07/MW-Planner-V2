import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import {
  InventoryClassification,
  Environment,
} from "../../../constants/inventory.constants";
import { InventoryItem } from "../../../types/inventory.types";
import { InventoryCard } from "../InventoryCard";

const mockFormatTime = vi.fn((time: string) => time);
const mockExtractOperationTimes = vi.fn();
const mockFormatSize = vi.fn(() => ({
  name: "Large",
  colorClass: "text-blue-500",
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("@utils/optimization.utils", () => ({
  formatTime: (time: string) => mockFormatTime(time),
}));

vi.mock("@utils/schedule.utils", () => ({
  extractOperationTimes: (arg: unknown) => mockExtractOperationTimes(arg),
  formatSize: () => mockFormatSize(),
}));

const baseItem: InventoryItem = {
  id: "1",
  detail: {
    name: "Test Inventory",
    inventoryType: InventoryClassification.DIGITAL,
    format: "Digital Screen",
    environment: Environment.OUTDOOR,
    panels: [],
    id: "d1",
    externalId: "",
    referenceId: "ref-1",
    mediaOwnerId: "",
    mediaOwnerName: "",
    category: "",
    venueType: [],
    thumbnail: "",
    images: [],
    size: "",
    operationMode: "",
    execution: "",
    screens: 0,
    sov: 0,
    isSelected: false,
    isCompliant: false,
    bookingMode: "",
  },
  location: {
    location: {
      address: "",
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
  operations: {
    startTime: "09:00",
    endTime: "17:00",
    operationDays: [],
    maintenanceWindow: "",
    loopSize: 0,
    slotDuration: 0,
    clientPerLoop: 0,
    cycleTime: 0,
  },
  schedules: [],
} as InventoryItem;

describe("InventoryCard", () => {
  beforeEach(() => {
    mockExtractOperationTimes.mockReturnValue(null);
  });

  it("renders inventory card with item details", () => {
    render(<InventoryCard item={baseItem} />);
    expect(screen.getByText("Test Inventory")).toBeInTheDocument();
    expect(screen.getByText("Digital Screen")).toBeInTheDocument();
  });

  it("renders empty string when item.detail.name is missing", () => {
    const item = { ...baseItem, detail: { ...baseItem.detail, name: "" } };
    render(<InventoryCard item={item} />);
    const heading = screen.getByRole("heading", { level: 3 });
    expect(heading).toHaveTextContent("");
  });

  it("calls onClick when card is clicked and showCheckbox is false", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<InventoryCard item={baseItem} onClick={onClick} />);
    const card = screen.getByText("Test Inventory").closest("div");
    await user.click(card!);
    expect(onClick).toHaveBeenCalled();
  });

  it("does not call onClick when showCheckbox is true", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <InventoryCard
        item={baseItem}
        showCheckbox
        onCheckboxChange={vi.fn()}
        onClick={onClick}
      />,
    );
    const card = screen.getByText("Test Inventory").closest("div");
    await user.click(card!);
    expect(onClick).not.toHaveBeenCalled();
  });

  it("shows checkbox with correct id when showCheckbox and checkboxId are set", () => {
    render(
      <InventoryCard
        item={baseItem}
        showCheckbox
        checkboxId="inv-check-1"
        onCheckboxChange={vi.fn()}
      />,
    );
    const checkbox = screen.getByRole("checkbox", { name: "" });
    expect(checkbox).toHaveAttribute("id", "inv-check-1");
  });

  it("calls onCheckboxChange when checkbox is clicked", async () => {
    const user = userEvent.setup();
    const onCheckboxChange = vi.fn();
    render(
      <InventoryCard
        item={baseItem}
        showCheckbox
        checkboxChecked={false}
        onCheckboxChange={onCheckboxChange}
      />,
    );
    const checkbox = screen.getByRole("checkbox");
    await user.click(checkbox);
    expect(onCheckboxChange).toHaveBeenCalledWith(true);
  });

  it("does not throw when checkbox is clicked and onCheckboxChange is undefined", async () => {
    const user = userEvent.setup();
    render(
      <InventoryCard item={baseItem} showCheckbox checkboxChecked={false} />,
    );
    const checkbox = screen.getByRole("checkbox");
    await user.click(checkbox);
  });

  it("shows alert icon with tooltip when showAlertIcon is true", () => {
    render(
      <InventoryCard
        item={baseItem}
        showAlertIcon
        alertTooltipContent="Alert message"
      />,
    );
    expect(screen.getByText("Test Inventory")).toBeInTheDocument();
  });

  it("applies alertIconClassName to alert icon when provided", () => {
    const { container } = render(
      <InventoryCard
        item={baseItem}
        showAlertIcon
        alertTooltipContent="Tip"
        alertIconClassName="custom-alert-icon"
      />,
    );
    const icon = container.querySelector(".custom-alert-icon");
    expect(icon).toBeInTheDocument();
  });

  it("applies selected styling when isSelected is true and showCheckbox is false", () => {
    const { container } = render(
      <InventoryCard item={baseItem} isSelected showCheckbox={false} />,
    );
    const card = container.querySelector('[class*="bg-mw-primary-50"]');
    expect(card).toBeInTheDocument();
  });

  it("displays operation times from startTime/endTime when extractOperationTimes returns null", () => {
    render(<InventoryCard item={baseItem} />);
    expect(screen.getByText(/09:00/)).toBeInTheDocument();
    expect(screen.getByText(/17:00/)).toBeInTheDocument();
  });

  it("displays operation times from extractOperationTimes when operatingTimes is provided", () => {
    mockExtractOperationTimes.mockReturnValue({
      startTime: "08:00",
      endTime: "18:00",
    });
    const itemWithOps = {
      ...baseItem,
      operations: {
        ...baseItem.operations,
        operatingTimes: { MONDAY: [{ start: "08:00", end: "18:00" }] },
      },
    } as InventoryItem;
    render(<InventoryCard item={itemWithOps} />);
    expect(screen.getByText(/08:00/)).toBeInTheDocument();
    expect(screen.getByText(/18:00/)).toBeInTheDocument();
  });

  it("displays no operation times when operations has neither operatingTimes nor start/end", () => {
    const itemNoTimes = {
      ...baseItem,
      operations: {
        ...baseItem.operations,
        startTime: undefined,
        endTime: undefined,
      },
    } as InventoryItem;
    render(<InventoryCard item={itemNoTimes} />);
    expect(
      screen.queryByText(/\d{1,2}:\d{2}\s+to\s+\d{1,2}:\d{2}/),
    ).not.toBeInTheDocument();
  });

  it("renders size badge when detail.size is set", () => {
    const itemWithSize = {
      ...baseItem,
      detail: {
        ...baseItem.detail,
        size: "Large",
      },
    };
    render(<InventoryCard item={itemWithSize} />);
    expect(mockFormatSize).toHaveBeenCalled();
    expect(screen.getByText("inventorySize.large.label")).toBeInTheDocument();
  });

  it("does not render size badge when detail.size is absent", () => {
    render(<InventoryCard item={baseItem} />);
    expect(
      screen.queryByText("inventorySize.large.label"),
    ).not.toBeInTheDocument();
  });

  it("applies non-DIGITAL badge class for inventoryType", () => {
    const itemClassic = {
      ...baseItem,
      detail: {
        ...baseItem.detail,
        inventoryType: InventoryClassification.CLASSIC,
      },
    };
    const { container } = render(<InventoryCard item={itemClassic} />);
    expect(
      container.querySelector(".outline-mw-neutral-500"),
    ).toBeInTheDocument();
  });

  it("applies INDOOR environment badge class", () => {
    const itemIndoor = {
      ...baseItem,
      detail: { ...baseItem.detail, environment: Environment.INDOOR },
    };
    const { container } = render(<InventoryCard item={itemIndoor} />);
    expect(
      container.querySelector('[class*="mw-amaranth-500"]'),
    ).toBeInTheDocument();
  });

  it("applies custom className and headerClassName", () => {
    const { container } = render(
      <InventoryCard
        item={baseItem}
        className="custom-class"
        headerClassName="header-class"
      />,
    );
    expect(container.querySelector(".custom-class")).toBeInTheDocument();
    expect(container.querySelector(".header-class")).toBeInTheDocument();
  });

  it("renders card content without checkbox wrapper when showCheckbox is false", () => {
    render(<InventoryCard item={baseItem} />);
    expect(screen.getByText("Test Inventory")).toBeInTheDocument();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("does not render the remove button by default", () => {
    render(<InventoryCard item={baseItem} removeButtonTitle="Remove" />);
    expect(
      screen.queryByRole("button", { name: "Remove" }),
    ).not.toBeInTheDocument();
  });

  it("renders the remove button and calls onRemove when clicked", async () => {
    const user = userEvent.setup();
    const onRemove = vi.fn();
    render(
      <InventoryCard
        item={baseItem}
        showRemoveButton
        onRemove={onRemove}
        removeButtonTitle="Remove"
      />,
    );
    await user.click(screen.getByRole("button", { name: "Remove" }));
    expect(onRemove).toHaveBeenCalledTimes(1);
  });

  it("does not trigger card onClick when the remove button is clicked", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    const onRemove = vi.fn();
    render(
      <InventoryCard
        item={baseItem}
        onClick={onClick}
        showRemoveButton
        onRemove={onRemove}
        removeButtonTitle="Remove"
      />,
    );
    await user.click(screen.getByRole("button", { name: "Remove" }));
    expect(onRemove).toHaveBeenCalledTimes(1);
    expect(onClick).not.toHaveBeenCalled();
  });
});
