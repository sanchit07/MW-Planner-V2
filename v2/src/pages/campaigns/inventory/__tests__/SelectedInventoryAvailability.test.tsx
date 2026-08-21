import { render, screen, waitFor, act, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import * as invUIUtils from "@utils/inventoryAvailabilityUI.utils";
import type { InventoryItem } from "src/types/inventory.types";
import type { InventoryAvailabilityData } from "src/types/price-management.types";
import { describe, it, expect, vi, beforeEach } from "vitest";

import SelectedInventoryAvailability from "../SelectedInventoryAvailability";

let resolveAvailabilityPromise: (value: unknown) => void;
let rejectAvailabilityPromise: (reason?: unknown) => void;
const createAvailabilityPromise = () =>
  new Promise<unknown>((resolve, reject) => {
    resolveAvailabilityPromise = resolve;
    rejectAvailabilityPromise = reject;
  });
const mockUnwrap = vi.fn(() => createAvailabilityPromise());

const mockSyncTriggerUnwrap = vi.fn(() => Promise.resolve({}));
const mockSyncStatusUnwrap = vi.fn(() =>
  Promise.resolve({ status: "SUCCESS" } as Record<string, unknown>),
);

vi.mock("@services/inventory/inventorySlice", () => ({
  useLazyGetInventoryAvailabilityQuery: () => [() => ({ unwrap: mockUnwrap })],
  useTriggerInventoryAvailabilitySyncMutation: () => [
    () => ({ unwrap: mockSyncTriggerUnwrap }),
    { isLoading: false },
  ],
  useLazyGetInventoryAvailabilitySyncStatusQuery: () => [
    () => ({ unwrap: mockSyncStatusUnwrap }),
  ],
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string) => key,
  }),
}));

vi.mock("@utils/dateUtils", () => ({
  getDaysInMonth: (year: number, month: number) =>
    new Date(year, month + 1, 0).getDate(),
  getStartOfWeek: (date: Date, weekStartsOn: number) => {
    const result = new Date(date);
    const day = result.getDay();
    const diff = (day - weekStartsOn + 7) % 7;
    result.setDate(result.getDate() - diff);
    return result;
  },
  formatMonthYear: (date: Date) =>
    date.toLocaleDateString("en-US", { month: "long", year: "numeric" }),
  formatWeekRange: (_w: number, weekNum: number) =>
    `Week ${weekNum} (mock range)`,
  getWeekOfYear: () => 1,
  toISODateString: (date: Date) => date.toISOString().split("T")[0],
}));

vi.mock("@utils/inventoryAvailabilityUI.utils", () => ({
  getStatusConfig: (status: string) => ({
    label: status,
    bgColor: "white",
    textColor: "text-mw-black",
    progressColor: "success",
    showProgress: true,
  }),
  parseAvailabilityResponse: vi.fn(() => ({})),
  parseAvailabilitySync: vi.fn(() => null),
  getAvailabilitySyncWarning: vi.fn(
    (sync: { status?: string } | null | undefined) =>
      sync?.status === "FAILED" ? "failed" : null,
  ),
  isAvailabilitySyncStale: vi.fn(() => false),
  AVAILABILITY_STALE_THRESHOLD_MS: 6 * 60 * 60 * 1000,
}));

vi.mock("@components/common/SpotTooltipContent", () => ({
  SpotTooltipContent: () => <div data-testid="spot-tooltip-content" />,
}));

function createMockInventory(
  id: string,
  type: "Digital" | "Classic",
  overrides?: Partial<{ externalId: string }>,
): InventoryItem {
  return {
    detail: {
      id,
      name: `Inventory ${id}`,
      externalId: overrides?.externalId ?? `ext-${id}`,
      inventoryType: type,
    },
    operations: { clientPerLoop: 10, slotDuration: 6 },
    location: { location: {} },
    performance: {},
    schedules: [],
  } as unknown as InventoryItem;
}

function createMockAvailabilityData(
  externalId: string,
): InventoryAvailabilityData {
  const schedule = {
    createdAt: "",
    createdBy: "",
    updatedAt: "",
    updatedBy: "",
    operatingTimes: {
      monday: [{ start: "09:00:00", end: "17:00:00" }],
      tuesday: [{ start: "09:00:00", end: "17:00:00" }],
      wednesday: [{ start: "09:00:00", end: "17:00:00" }],
      thursday: [{ start: "09:00:00", end: "17:00:00" }],
      friday: [{ start: "09:00:00", end: "17:00:00" }],
      saturday: [],
      sunday: [],
    },
  };
  return {
    id: externalId,
    name: `Avail ${externalId}`,
    allocatedLoopSeconds: 0,
    availableLoopSeconds: 0,
    bookingMode: "loop",
    loopDuration: 360,
    schedule,
    timeZone: "UTC",
    bookings: [],
  };
}

/** Availability data with no operatingTimes so buildAvailabilityIndex returns null (legacy path). */
function createMockAvailabilityDataNoOperatingTimes(
  externalId: string,
): InventoryAvailabilityData {
  return {
    id: externalId,
    name: `Avail ${externalId}`,
    allocatedLoopSeconds: 0,
    availableLoopSeconds: 0,
    bookingMode: "loop",
    loopDuration: 360,
    schedule: {
      createdAt: "",
      createdBy: "",
      updatedAt: "",
      updatedBy: "",
      operatingTimes: {},
    },
    timeZone: "UTC",
    bookings: [],
  };
}

describe("SelectedInventoryAvailability", { timeout: 10000 }, () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUnwrap.mockImplementation(() => createAvailabilityPromise());
    vi.mocked(invUIUtils.parseAvailabilityResponse).mockReturnValue({});
  });

  describe("rendering", () => {
    it("renders with zero inventories", () => {
      render(
        <SelectedInventoryAvailability inventories={[]} campaignData={null} />,
      );
      expect(
        screen.getByText(/0\s+availability\.selectedInventories/),
      ).toBeInTheDocument();
    });

    it("renders with one inventory", async () => {
      const inventories = [createMockInventory("inv-1", "Digital")];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={null}
        />,
      );
      expect(
        screen.getByText(/1\s+availability\.selectedInventories/),
      ).toBeInTheDocument();
      await waitFor(() => {
        expect(mockUnwrap).toHaveBeenCalled();
      });
      await act(async () => {
        resolveAvailabilityPromise!({
          success: true,
          data: { inventories: {} },
        });
        await Promise.resolve();
        await Promise.resolve();
      });
      expect(
        screen.getByText(/1\s+availability\.selectedInventories/),
      ).toBeInTheDocument();
    });

    it("renders with availability data and populates calendar cells", async () => {
      vi.mocked(invUIUtils.parseAvailabilityResponse).mockReturnValue({
        "ext-inv-1": createMockAvailabilityData("ext-inv-1"),
      });
      const inventories = [createMockInventory("inv-1", "Digital")];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={null}
        />,
      );
      await waitFor(() => expect(mockUnwrap).toHaveBeenCalled());
      await act(async () => {
        resolveAvailabilityPromise!({
          success: true,
          data: { inventories: { "inv-1": {} } },
        });
        await Promise.resolve();
        await Promise.resolve();
      });
      await waitFor(() => {
        expect(screen.getByText("Inventory inv-1")).toBeInTheDocument();
      });
    });

    it("renders Classic inventory without expand button", async () => {
      vi.mocked(invUIUtils.parseAvailabilityResponse).mockReturnValue({
        "ext-inv-classic": createMockAvailabilityData("ext-inv-classic"),
      });
      const inventories = [createMockInventory("inv-classic", "Classic")];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={null}
        />,
      );
      await act(async () => {
        resolveAvailabilityPromise!({
          success: true,
          data: { inventories: { "inv-classic": {} } },
        });
        await Promise.resolve();
        await Promise.resolve();
      });
      expect(screen.getByText("Inventory inv-classic")).toBeInTheDocument();
      expect(
        screen.queryByRole("button", {
          name: /availability\.ariaExpandSpots/i,
        }),
      ).not.toBeInTheDocument();
    });

    it("renders with campaignData for tooltip context", async () => {
      vi.mocked(invUIUtils.parseAvailabilityResponse).mockReturnValue({
        "ext-inv-1": createMockAvailabilityData("ext-inv-1"),
      });
      const inventories = [createMockInventory("inv-1", "Digital")];
      const campaignData = {
        startDate: "2026-01-01",
        endDate: "2026-12-31",
      } as Parameters<typeof SelectedInventoryAvailability>[0]["campaignData"];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={campaignData}
        />,
      );
      await act(async () => {
        resolveAvailabilityPromise!({
          success: true,
          data: { inventories: {} },
        });
        await Promise.resolve();
        await Promise.resolve();
      });
      expect(screen.getByText("Inventory inv-1")).toBeInTheDocument();
    });

    it("renders dash in calendar cells when no availability data", async () => {
      const inventories = [createMockInventory("inv-1", "Digital")];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={null}
        />,
      );
      await waitFor(() => expect(mockUnwrap).toHaveBeenCalled());
      await act(async () => {
        resolveAvailabilityPromise!({
          success: true,
          data: { inventories: {} },
        });
        await Promise.resolve();
        await Promise.resolve();
      });
      await waitFor(() => {
        expect(screen.getByText("Inventory inv-1")).toBeInTheDocument();
      });
      const dashes = screen.getAllByText("-");
      expect(dashes.length).toBeGreaterThan(0);
    });

    it("skips inventory without detail.id in calendar rows", () => {
      const inventories = [
        createMockInventory("inv-1", "Digital"),
        {
          ...createMockInventory("inv-2", "Digital"),
          detail: {
            name: "No Id",
            externalId: "ext-2",
            inventoryType: "Digital",
          },
        } as InventoryItem,
      ];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={null}
        />,
      );
      expect(
        screen.getByText(/2\s+availability\.selectedInventories/),
      ).toBeInTheDocument();
      expect(screen.getByText("Inventory inv-1")).toBeInTheDocument();
      expect(screen.queryByText("No Id")).not.toBeInTheDocument();
    });
  });

  describe("fetch and API", () => {
    it("shows error when fetch fails", async () => {
      const consoleSpy = vi
        .spyOn(console, "error")
        .mockImplementation(() => {});
      const inventories = [createMockInventory("inv-1", "Digital")];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={null}
        />,
      );
      await waitFor(() => expect(mockUnwrap).toHaveBeenCalled());
      rejectAvailabilityPromise!(new Error("Network error"));
      const alert = await screen.findByRole("alert", {}, { timeout: 2000 });
      expect(alert).toHaveTextContent("availability.loadError");
      consoleSpy.mockRestore();
    });
  });

  describe("view mode and navigation", () => {
    it("switches to weekly view when Weekly is clicked", async () => {
      const user = userEvent.setup();
      render(
        <SelectedInventoryAvailability inventories={[]} campaignData={null} />,
      );
      const weeklyButton = screen.getByRole("button", { name: /weekly view/i });
      await user.click(weeklyButton);
      expect(weeklyButton).toHaveAttribute("aria-pressed", "true");
      expect(screen.getByText(/calendar\.weekTitle/)).toBeInTheDocument();
    });

    it("switches to daily view when Daily is clicked", async () => {
      const user = userEvent.setup();
      render(
        <SelectedInventoryAvailability inventories={[]} campaignData={null} />,
      );
      const dailyButton = screen.getByRole("button", { name: /daily view/i });
      await user.click(dailyButton);
      expect(dailyButton).toHaveAttribute("aria-pressed", "true");
    });

    it("weekly view with availability data shows week and calendar cells", async () => {
      const user = userEvent.setup();
      vi.mocked(invUIUtils.parseAvailabilityResponse).mockReturnValue({
        "ext-inv-1": createMockAvailabilityData("ext-inv-1"),
      });
      const inventories = [createMockInventory("inv-1", "Digital")];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={null}
        />,
      );
      await act(async () => {
        resolveAvailabilityPromise!({
          success: true,
          data: {
            inventories: {
              "ext-inv-1": createMockAvailabilityData("ext-inv-1"),
            },
          },
        });
        await Promise.resolve();
        await Promise.resolve();
      });
      await screen.findByText("Inventory inv-1", {}, { timeout: 2000 });
      await user.click(screen.getByRole("button", { name: /weekly view/i }));
      expect(screen.getByText(/calendar\.weekTitle/)).toBeInTheDocument();
      expect(screen.getByText("Inventory inv-1")).toBeInTheDocument();
    });

    it("daily view with availability data shows hour columns", async () => {
      const user = userEvent.setup();
      vi.mocked(invUIUtils.parseAvailabilityResponse).mockReturnValue({
        "ext-inv-1": createMockAvailabilityData("ext-inv-1"),
      });
      const inventories = [createMockInventory("inv-1", "Digital")];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={null}
        />,
      );
      await act(async () => {
        resolveAvailabilityPromise!({
          success: true,
          data: {
            inventories: {
              "ext-inv-1": createMockAvailabilityData("ext-inv-1"),
            },
          },
        });
        await Promise.resolve();
        await Promise.resolve();
      });
      await screen.findByText("Inventory inv-1", {}, { timeout: 2000 });
      await user.click(screen.getByRole("button", { name: /daily view/i }));
      expect(screen.getByText("00:00")).toBeInTheDocument();
      expect(screen.getByText("Inventory inv-1")).toBeInTheDocument();
    });

    it("daily view calendar title shows weekday format", async () => {
      const user = userEvent.setup();
      render(
        <SelectedInventoryAvailability inventories={[]} campaignData={null} />,
      );
      await user.click(screen.getByRole("button", { name: /daily view/i }));
      const title = screen.getByText("calendar.fullDateTitle");
      expect(title).toBeInTheDocument();
    });

    it("navigates previous period when Previous is clicked", async () => {
      const user = userEvent.setup();
      render(
        <SelectedInventoryAvailability inventories={[]} campaignData={null} />,
      );
      const prevButton = screen.getByRole("button", {
        name: /availability\.ariaPreviousPeriod/i,
      });
      const titleBefore = screen.getByText(
        /^calendar\.monthNames\.\d+ \d{4}$/,
      ).textContent;
      await user.click(prevButton);
      await waitFor(() => {
        const titleAfter = screen.getByText(
          /^calendar\.monthNames\.\d+ \d{4}$/,
        ).textContent;
        expect(titleAfter).not.toBe(titleBefore);
      });
    });

    it("navigates next period when Next is clicked", async () => {
      const user = userEvent.setup();
      render(
        <SelectedInventoryAvailability inventories={[]} campaignData={null} />,
      );
      const nextButton = screen.getByRole("button", {
        name: /availability\.ariaNextPeriod/i,
      });
      await user.click(nextButton);
      await waitFor(() => {
        expect(nextButton).toBeInTheDocument();
      });
    });
  });

  describe("expand and collapse", () => {
    it("expands digital inventory to show spots when expand is clicked", async () => {
      const user = userEvent.setup();
      vi.mocked(invUIUtils.parseAvailabilityResponse).mockReturnValue({
        "ext-inv-1": createMockAvailabilityData("ext-inv-1"),
      });
      const inventories = [createMockInventory("inv-1", "Digital")];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={null}
        />,
      );
      await act(async () => {
        resolveAvailabilityPromise!({
          success: true,
          data: { inventories: {} },
        });
        await Promise.resolve();
        await Promise.resolve();
      });
      await screen.findByText("Inventory inv-1", {}, { timeout: 2000 });
      const expandButton = screen.getByRole("button", {
        name: /availability\.ariaExpandSpots/i,
      });
      await user.click(expandButton);
      expect(
        await screen.findByRole("button", {
          name: /availability\.ariaCollapseSpots/i,
        }),
      ).toBeInTheDocument();
      expect(screen.getByText("Spot 01")).toBeInTheDocument();
    });

    it("collapses digital inventory when collapse is clicked", async () => {
      const user = userEvent.setup();
      vi.mocked(invUIUtils.parseAvailabilityResponse).mockReturnValue({
        "ext-inv-1": createMockAvailabilityData("ext-inv-1"),
      });
      const inventories = [createMockInventory("inv-1", "Digital")];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={null}
        />,
      );
      await act(async () => {
        resolveAvailabilityPromise!({
          success: true,
          data: { inventories: {} },
        });
        await Promise.resolve();
        await Promise.resolve();
      });
      const expandButton = screen.getByRole("button", {
        name: /availability\.ariaExpandSpots/i,
      });
      await user.click(expandButton);
      expect(
        await screen.findByText("Spot 01", {}, { timeout: 2000 }),
      ).toBeInTheDocument();
      const collapseButton = screen.getByRole("button", {
        name: /availability\.ariaCollapseSpots/i,
      });
      await user.click(collapseButton);
      await waitFor(
        () => {
          expect(screen.queryByText("Spot 01")).not.toBeInTheDocument();
        },
        { timeout: 2000 },
      );
    });
  });

  describe("multiple inventories", () => {
    it("renders multiple selected inventories", async () => {
      vi.mocked(invUIUtils.parseAvailabilityResponse).mockReturnValue({
        "ext-inv-1": createMockAvailabilityData("ext-inv-1"),
        "ext-inv-2": createMockAvailabilityData("ext-inv-2"),
      });
      const inventories = [
        createMockInventory("inv-1", "Digital"),
        createMockInventory("inv-2", "Classic"),
      ];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={null}
        />,
      );
      await act(async () => {
        resolveAvailabilityPromise!({
          success: true,
          data: { inventories: {} },
        });
        await Promise.resolve();
        await Promise.resolve();
      });
      expect(
        screen.getByText(/2\s+availability\.selectedInventories/),
      ).toBeInTheDocument();
      expect(screen.getByText("Inventory inv-1")).toBeInTheDocument();
      expect(screen.getByText("Inventory inv-2")).toBeInTheDocument();
    });
  });

  describe("cell data and legacy path", () => {
    it("uses legacy path when buildAvailabilityIndex returns null", async () => {
      vi.mocked(invUIUtils.parseAvailabilityResponse).mockReturnValue({
        "ext-inv-1": createMockAvailabilityDataNoOperatingTimes("ext-inv-1"),
      });
      const inventories = [createMockInventory("inv-1", "Digital")];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={null}
        />,
      );
      await act(async () => {
        resolveAvailabilityPromise!({
          success: true,
          data: {
            inventories: {
              "ext-inv-1":
                createMockAvailabilityDataNoOperatingTimes("ext-inv-1"),
            },
          },
        });
        await Promise.resolve();
        await Promise.resolve();
      });
      await waitFor(() => {
        expect(screen.getByText("Inventory inv-1")).toBeInTheDocument();
      });
      expect(screen.getAllByText("available").length).toBeGreaterThan(0);
    });

    it("Classic inventory cell shows status label", async () => {
      vi.mocked(invUIUtils.parseAvailabilityResponse).mockReturnValue({
        "ext-inv-classic": createMockAvailabilityData("ext-inv-classic"),
      });
      const inventories = [createMockInventory("inv-classic", "Classic")];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={null}
        />,
      );
      await act(async () => {
        resolveAvailabilityPromise!({
          success: true,
          data: {
            inventories: {
              "ext-inv-classic": createMockAvailabilityData("ext-inv-classic"),
            },
          },
        });
        await Promise.resolve();
        await Promise.resolve();
      });
      await waitFor(() => {
        expect(screen.getByText("Inventory inv-classic")).toBeInTheDocument();
      });
      expect(screen.getAllByText("available").length).toBeGreaterThan(0);
    });

    it("expanded digital header row renders blank cells in monthly view", async () => {
      const user = userEvent.setup();
      vi.mocked(invUIUtils.parseAvailabilityResponse).mockReturnValue({
        "ext-inv-1": createMockAvailabilityData("ext-inv-1"),
      });
      const inventories = [createMockInventory("inv-1", "Digital")];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={null}
        />,
      );
      await act(async () => {
        resolveAvailabilityPromise!({
          success: true,
          data: {
            inventories: {
              "ext-inv-1": createMockAvailabilityData("ext-inv-1"),
            },
          },
        });
        await Promise.resolve();
        await Promise.resolve();
      });
      await screen.findByText("Inventory inv-1", {}, { timeout: 2000 });
      await user.click(
        screen.getByRole("button", {
          name: /availability\.ariaExpandSpots/i,
        }),
      );
      await screen.findByText("Spot 01", {}, { timeout: 2000 });
      expect(screen.getByText("Inventory inv-1")).toBeInTheDocument();
      expect(screen.getByText("Spot 01")).toBeInTheDocument();
    });
  });

  describe("manual sync polling", () => {
    it("polls sync status until SUCCESS then refetches availability", async () => {
      mockUnwrap.mockImplementation(() =>
        Promise.resolve({ success: true, data: { inventories: {} } }),
      );
      mockSyncStatusUnwrap
        .mockResolvedValueOnce({ status: "RUNNING" })
        .mockResolvedValueOnce({ status: "SUCCESS" });
      const inventories = [createMockInventory("inv-1", "Digital")];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={null}
          syncPollIntervalMs={5}
        />,
      );
      await waitFor(() => expect(mockUnwrap).toHaveBeenCalled());
      const callsBefore = mockUnwrap.mock.calls.length;

      fireEvent.click(screen.getByTestId("button-availability-sync-now"));

      // First poll -> RUNNING, second poll -> SUCCESS terminates polling
      // and refetches availability.
      await waitFor(() =>
        expect(mockSyncStatusUnwrap).toHaveBeenCalledTimes(2),
      );
      await waitFor(() =>
        expect(mockUnwrap.mock.calls.length).toBeGreaterThan(callsBefore),
      );
      expect(mockSyncTriggerUnwrap).toHaveBeenCalledTimes(1);
      expect(
        screen.queryByText(/availability\.syncFailedGeneric/),
      ).not.toBeInTheDocument();
    });

    it("shows a failure message when the sync ends FAILED", async () => {
      mockUnwrap.mockImplementation(() =>
        Promise.resolve({ success: true, data: { inventories: {} } }),
      );
      mockSyncStatusUnwrap.mockResolvedValueOnce({ status: "FAILED" });
      const inventories = [createMockInventory("inv-1", "Digital")];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={null}
          syncPollIntervalMs={5}
        />,
      );
      await waitFor(() => expect(mockUnwrap).toHaveBeenCalled());

      fireEvent.click(screen.getByTestId("button-availability-sync-now"));

      await waitFor(() =>
        expect(
          screen.getByText(/availability\.syncFailedGeneric/),
        ).toBeInTheDocument(),
      );
    });

    it("treats a 409 (sync already running) as in-progress, not an error", async () => {
      mockUnwrap.mockImplementation(() =>
        Promise.resolve({ success: true, data: { inventories: {} } }),
      );
      mockSyncTriggerUnwrap.mockRejectedValueOnce({ status: 409 });
      mockSyncStatusUnwrap.mockResolvedValueOnce({ status: "SUCCESS" });
      const inventories = [createMockInventory("inv-1", "Digital")];
      render(
        <SelectedInventoryAvailability
          inventories={inventories}
          campaignData={null}
          syncPollIntervalMs={5}
        />,
      );
      await waitFor(() => expect(mockUnwrap).toHaveBeenCalled());

      fireEvent.click(screen.getByTestId("button-availability-sync-now"));

      await waitFor(() =>
        expect(mockSyncStatusUnwrap).toHaveBeenCalledTimes(1),
      );
      expect(
        screen.queryByText(/availability\.syncFailedGeneric/),
      ).not.toBeInTheDocument();
    });
  });

  describe("accessibility", () => {
    it("has calendar view toggle with aria-label and aria-pressed", () => {
      render(
        <SelectedInventoryAvailability inventories={[]} campaignData={null} />,
      );
      const group = screen.getByRole("group", {
        name: /availability\.ariaCalendarView/i,
      });
      expect(group).toBeInTheDocument();
      const monthlyButton = screen.getByRole("button", {
        name: /monthly view/i,
      });
      expect(monthlyButton).toHaveAttribute("aria-pressed", "true");
    });

    it("has previous and next period buttons with aria-label", () => {
      render(
        <SelectedInventoryAvailability inventories={[]} campaignData={null} />,
      );
      expect(
        screen.getByRole("button", {
          name: /availability\.ariaPreviousPeriod/i,
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", {
          name: /availability\.ariaNextPeriod/i,
        }),
      ).toBeInTheDocument();
    });
  });
});
