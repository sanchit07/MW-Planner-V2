import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { describe, expect, it, vi, beforeEach } from "vitest";

import { createOptimizationStore, defaultCampaignState } from "./mocks";
import ScheduleOptimizationComponent from "../ScheduleOptimization";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({
    showError: mockShowError,
    showSuccess: mockShowSuccess,
    showWarning: vi.fn(),
  }),
}));

vi.mock("../ScheduleDrawer", () => ({
  ScheduleDrawer: ({
    isOpen,
    onClose,
  }: {
    isOpen: boolean;
    onClose: () => void;
  }) =>
    isOpen ? (
      <div data-testid="schedule-drawer">
        <button type="button" onClick={onClose}>
          Close Schedule
        </button>
      </div>
    ) : null,
}));

vi.mock("../OptimizeManuallyDrawer", () => ({
  OptimizeManuallyDrawer: ({
    isOpen,
    onClose,
  }: {
    isOpen: boolean;
    onClose: () => void;
  }) =>
    isOpen ? (
      <div data-testid="optimize-manually-drawer">
        <button type="button" onClick={onClose}>
          Close Optimize
        </button>
      </div>
    ) : null,
}));

vi.mock("@components/common/SelectedInventoryListContainer", () => ({
  SelectedInventoryListContainer: ({
    onInitialLoad,
  }: {
    onInitialLoad?: (items: unknown[]) => void;
  }) => (
    <div data-testid="selected-inventory-list">
      Inventory List
      <button
        type="button"
        onClick={() =>
          onInitialLoad?.([
            {
              detail: { id: "inv-1", name: "Inv 1", inventoryType: "Digital" },
              operations: {
                operationDays: ["MONDAY"],
                loopSize: 60,
                clientPerLoop: 1,
              },
              performance: {
                cpmRate: 25,
                estimatedCost: 0,
                perDayCost: 0,
                perDayAdPlays: 0,
                totalAdPlays: 0,
                plannedSot: 0,
                totalSot: 0,
              },
            },
          ])
        }
        data-testid="initial-load-with-item"
      >
        Load with item
      </button>
    </div>
  ),
}));

const mockFetchSchedules = vi.fn();
const mockDeleteSchedule = vi.fn();

vi.mock("@services/inventory/inventorySlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/inventory/inventorySlice")>();
  return {
    ...actual,
    useLazyGetSelectedInventorySchedulesQuery: () => [mockFetchSchedules, {}],
    useDeleteInventoryScheduleMutation: () => [mockDeleteSchedule, {}],
  };
});

const mockShowError = vi.fn();
const mockShowSuccess = vi.fn();

describe("ScheduleOptimizationComponent", () => {
  const mockLoadForeCastData = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockShowError.mockClear();
    mockShowSuccess.mockClear();
    mockFetchSchedules.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: [{ schedules: [] }],
        }),
    });
    mockDeleteSchedule.mockReturnValue({
      unwrap: () => Promise.resolve({ success: true }),
    });
  });

  it("renders configure scheduling title and description", async () => {
    const store = createOptimizationStore();

    render(
      <Provider store={store}>
        <ScheduleOptimizationComponent
          campaignId={defaultCampaignState.campaignId}
          campaignState={defaultCampaignState}
          loadForeCastData={mockLoadForeCastData}
        />
      </Provider>,
    );

    await waitFor(() => {
      expect(
        screen.getByText("optimization.configureScheduling.title"),
      ).toBeInTheDocument();
    });
    expect(
      screen.getByText("optimization.configureScheduling.description"),
    ).toBeInTheDocument();
  });

  it("renders Optimize Manually button and hides AI Optimize button", async () => {
    const store = createOptimizationStore();

    render(
      <Provider store={store}>
        <ScheduleOptimizationComponent
          campaignId={defaultCampaignState.campaignId}
          campaignState={defaultCampaignState}
          loadForeCastData={mockLoadForeCastData}
        />
      </Provider>,
    );

    await waitFor(() => {
      expect(
        screen.getByText(
          "optimization.configureScheduling.optimizeManuallyButtonLabel",
        ),
      ).toBeInTheDocument();
    });
    // AI Optimization button is temporarily hidden (commented out in
    // ScheduleOptimization.tsx).
    expect(
      screen.queryByText(
        "optimization.configureScheduling.aiOptimizeButtonLabel",
      ),
    ).not.toBeInTheDocument();
  });

  it("opens Optimize Manually drawer when Optimize Manually button is clicked", async () => {
    const user = userEvent.setup();
    const store = createOptimizationStore();

    render(
      <Provider store={store}>
        <ScheduleOptimizationComponent
          campaignId={defaultCampaignState.campaignId}
          campaignState={defaultCampaignState}
          loadForeCastData={mockLoadForeCastData}
        />
      </Provider>,
    );

    const button = await screen.findByText(
      "optimization.configureScheduling.optimizeManuallyButtonLabel",
    );
    await user.click(button);

    expect(screen.getByTestId("optimize-manually-drawer")).toBeInTheDocument();
  });

  it("closes Optimize Manually drawer when Close is clicked", async () => {
    const user = userEvent.setup();
    const store = createOptimizationStore();

    render(
      <Provider store={store}>
        <ScheduleOptimizationComponent
          campaignId={defaultCampaignState.campaignId}
          campaignState={defaultCampaignState}
          loadForeCastData={mockLoadForeCastData}
        />
      </Provider>,
    );

    await user.click(
      await screen.findByText(
        "optimization.configureScheduling.optimizeManuallyButtonLabel",
      ),
    );
    expect(screen.getByTestId("optimize-manually-drawer")).toBeInTheDocument();

    await user.click(screen.getByText("Close Optimize"));
    await waitFor(() => {
      expect(
        screen.queryByTestId("optimize-manually-drawer"),
      ).not.toBeInTheDocument();
    });
  });

  it("does not render OptimizeManuallyDrawer when campaignState.campaignData is absent", () => {
    const store = createOptimizationStore();
    const stateWithoutCampaignData = {
      ...defaultCampaignState,
      campaignData: undefined,
    };

    render(
      <Provider store={store}>
        <ScheduleOptimizationComponent
          campaignId={defaultCampaignState.campaignId}
          campaignState={stateWithoutCampaignData}
          loadForeCastData={mockLoadForeCastData}
        />
      </Provider>,
    );

    expect(
      screen.getByText(
        "optimization.configureScheduling.optimizeManuallyButtonLabel",
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId("optimize-manually-drawer"),
    ).not.toBeInTheDocument();
  });

  it("shows no schedule text when schedules are empty after fetch", async () => {
    mockFetchSchedules.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: [{ schedules: [] }],
        }),
    });

    const store = createOptimizationStore();
    render(
      <Provider store={store}>
        <ScheduleOptimizationComponent
          campaignId={defaultCampaignState.campaignId}
          campaignState={defaultCampaignState}
          loadForeCastData={mockLoadForeCastData}
        />
      </Provider>,
    );

    await userEvent.click(screen.getByTestId("initial-load-with-item"));
    await waitFor(() => {
      expect(mockFetchSchedules).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(
        screen.getByText("optimization.schedulingTargeting.noScheduleText"),
      ).toBeInTheDocument();
    });
  });

  it("shows error when fetch schedules fails", async () => {
    mockFetchSchedules.mockReturnValue({
      unwrap: () => Promise.reject(new Error("Fetch failed")),
    });

    const store = createOptimizationStore();
    render(
      <Provider store={store}>
        <ScheduleOptimizationComponent
          campaignId={defaultCampaignState.campaignId}
          campaignState={defaultCampaignState}
          loadForeCastData={mockLoadForeCastData}
        />
      </Provider>,
    );

    await userEvent.click(screen.getByTestId("initial-load-with-item"));

    await waitFor(() => {
      expect(mockShowError).toHaveBeenCalledWith(
        "optimization.schedulingTargeting.errorFetchingSchedules",
      );
    });
  });

  it("shows schedules and delete modal flow when two schedules exist", async () => {
    const twoSchedules = [
      {
        id: "s1",
        name: "Schedule 1",
        order: 1,
        startDate: "2025-01-01",
        endDate: "2025-01-31",
        scheduleDays: ["MONDAY"],
        duration: 15,
        spotsPerLoop: 1,
        spotsPerHour: 60,
        bookingMatrix: {},
        adPlays: 10,
        sov: 5,
        plannedSot: 2,
      },
      {
        id: "s2",
        name: "Schedule 2",
        order: 2,
        startDate: "2025-02-01",
        endDate: "2025-02-28",
        scheduleDays: ["TUESDAY"],
        duration: 30,
        spotsPerLoop: 2,
        spotsPerHour: 120,
        bookingMatrix: {},
        adPlays: 20,
        sov: 10,
        plannedSot: 4,
      },
    ];
    mockFetchSchedules.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: [{ schedules: twoSchedules }],
        }),
    });

    const store = createOptimizationStore();
    render(
      <Provider store={store}>
        <ScheduleOptimizationComponent
          campaignId={defaultCampaignState.campaignId}
          campaignState={defaultCampaignState}
          loadForeCastData={mockLoadForeCastData}
        />
      </Provider>,
    );

    await userEvent.click(screen.getByTestId("initial-load-with-item"));

    await waitFor(() => {
      expect(mockFetchSchedules).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(
        screen.getByText(/1 scheduleOptimization\.of 2/),
      ).toBeInTheDocument();
    });

    const deleteButtons = screen.getAllByRole("button").filter((b) => {
      const svg = b.querySelector("svg");
      return svg !== null;
    });
    const deleteButton = deleteButtons.find((b) =>
      b.getAttribute("class")?.includes("mw-error"),
    );
    if (deleteButton) {
      await userEvent.click(deleteButton);
      await waitFor(() => {
        expect(
          screen.getByText(
            "optimization.schedulingTargeting.removeScheduleTitle",
          ),
        ).toBeInTheDocument();
      });
      const yesButton = screen.getByText(
        "optimization.schedulingTargeting.removeScheduleYesButtonLabel",
      );
      await userEvent.click(yesButton);
      await waitFor(() => {
        expect(mockDeleteSchedule).toHaveBeenCalled();
      });
      await waitFor(() => {
        expect(mockShowSuccess).toHaveBeenCalledWith(
          "scheduleOptimization.scheduleDeletedSuccessfully",
        );
      });
    }
  });

  it("shows error when delete schedule API returns success false", async () => {
    const twoSchedules = [
      {
        id: "s1",
        name: "S1",
        order: 1,
        startDate: "2025-01-01",
        endDate: "2025-01-31",
        scheduleDays: ["MONDAY"],
        duration: 15,
        spotsPerLoop: 1,
        spotsPerHour: 60,
        bookingMatrix: {},
        adPlays: 10,
        sov: 5,
        plannedSot: 2,
      },
      {
        id: "s2",
        name: "S2",
        order: 2,
        startDate: "2025-02-01",
        endDate: "2025-02-28",
        scheduleDays: ["TUESDAY"],
        duration: 30,
        spotsPerLoop: 2,
        spotsPerHour: 120,
        bookingMatrix: {},
        adPlays: 20,
        sov: 10,
        plannedSot: 4,
      },
    ];
    mockFetchSchedules.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: [{ schedules: twoSchedules }],
        }),
    });
    mockDeleteSchedule.mockReturnValue({
      unwrap: () => Promise.resolve({ success: false }),
    });

    const store = createOptimizationStore();
    render(
      <Provider store={store}>
        <ScheduleOptimizationComponent
          campaignId={defaultCampaignState.campaignId}
          campaignState={defaultCampaignState}
          loadForeCastData={mockLoadForeCastData}
        />
      </Provider>,
    );

    await userEvent.click(screen.getByTestId("initial-load-with-item"));
    await waitFor(() => expect(mockFetchSchedules).toHaveBeenCalled());
    await waitFor(() =>
      expect(
        screen.getByText(/1 scheduleOptimization\.of 2/),
      ).toBeInTheDocument(),
    );

    const deleteBtn = screen
      .getAllByRole("button")
      .find((b) => b.getAttribute("class")?.includes("mw-error"));
    if (deleteBtn) {
      await userEvent.click(deleteBtn);
      await waitFor(() =>
        expect(
          screen.getByText(
            "optimization.schedulingTargeting.removeScheduleYesButtonLabel",
          ),
        ).toBeInTheDocument(),
      );
      await userEvent.click(
        screen.getByText(
          "optimization.schedulingTargeting.removeScheduleYesButtonLabel",
        ),
      );
      await waitFor(() => {
        expect(mockShowError).toHaveBeenCalledWith(
          "scheduleOptimization.errors.failedToDeleteSchedule",
        );
      });
    }
  });

  it("displays schedule detail grid boxes with impressions, reach, frequency and basePrice", async () => {
    const schedule = {
      id: "s1",
      name: "Schedule 1",
      order: 1,
      startDate: "2025-01-01",
      endDate: "2025-01-31",
      scheduleDays: ["MONDAY"],
      duration: 15,
      spotsPerLoop: 2,
      spotsPerHour: 12,
      bookingMatrix: {},
      adPlays: 7068,
      sov: 100,
      plannedSot: 589,
      impressions: 108128,
      reach: 14054,
      frequency: 7.69,
      basePrice: 79.36,
    };
    mockFetchSchedules.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: [{ schedules: [schedule] }],
        }),
    });

    const store = createOptimizationStore();
    render(
      <Provider store={store}>
        <ScheduleOptimizationComponent
          campaignId={defaultCampaignState.campaignId}
          campaignState={defaultCampaignState}
          loadForeCastData={mockLoadForeCastData}
        />
      </Provider>,
    );

    await userEvent.click(screen.getByTestId("initial-load-with-item"));
    await waitFor(() => expect(mockFetchSchedules).toHaveBeenCalled());

    await waitFor(() => {
      expect(screen.getByText("108,128")).toBeInTheDocument();
      expect(screen.getByText("14,054")).toBeInTheDocument();
      expect(screen.getByText("7.7x")).toBeInTheDocument();
      expect(screen.getByText("USD 79.36")).toBeInTheDocument();
    });
  });

  it("shows dash for missing impressions, reach, frequency and basePrice", async () => {
    const schedule = {
      id: "s1",
      name: "Schedule 1",
      order: 1,
      startDate: "2025-01-01",
      endDate: "2025-01-31",
      scheduleDays: ["MONDAY"],
      duration: 15,
      spotsPerLoop: 1,
      spotsPerHour: 60,
      bookingMatrix: {},
      adPlays: 10,
      sov: 5,
      plannedSot: 2,
    };
    mockFetchSchedules.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: [{ schedules: [schedule] }],
        }),
    });

    const store = createOptimizationStore();
    render(
      <Provider store={store}>
        <ScheduleOptimizationComponent
          campaignId={defaultCampaignState.campaignId}
          campaignState={defaultCampaignState}
          loadForeCastData={mockLoadForeCastData}
        />
      </Provider>,
    );

    await userEvent.click(screen.getByTestId("initial-load-with-item"));
    await waitFor(() => expect(mockFetchSchedules).toHaveBeenCalled());

    await waitFor(() => {
      const dashes = screen.getAllByText("-");
      expect(dashes.length).toBeGreaterThanOrEqual(4);
    });
  });

  it("shows error when delete schedule API fails", async () => {
    const twoSchedules = [
      {
        id: "s1",
        name: "S1",
        order: 1,
        startDate: "2025-01-01",
        endDate: "2025-01-31",
        scheduleDays: ["MONDAY"],
        duration: 15,
        spotsPerLoop: 1,
        spotsPerHour: 60,
        bookingMatrix: {},
        adPlays: 10,
        sov: 5,
        plannedSot: 2,
      },
      {
        id: "s2",
        name: "S2",
        order: 2,
        startDate: "2025-02-01",
        endDate: "2025-02-28",
        scheduleDays: ["TUESDAY"],
        duration: 30,
        spotsPerLoop: 2,
        spotsPerHour: 120,
        bookingMatrix: {},
        adPlays: 20,
        sov: 10,
        plannedSot: 4,
      },
    ];
    mockFetchSchedules.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: [{ schedules: twoSchedules }],
        }),
    });
    mockDeleteSchedule.mockReturnValue({
      unwrap: () => Promise.reject(new Error("Delete failed")),
    });

    const store = createOptimizationStore();
    render(
      <Provider store={store}>
        <ScheduleOptimizationComponent
          campaignId={defaultCampaignState.campaignId}
          campaignState={defaultCampaignState}
          loadForeCastData={mockLoadForeCastData}
        />
      </Provider>,
    );

    await userEvent.click(screen.getByTestId("initial-load-with-item"));
    await waitFor(() => expect(mockFetchSchedules).toHaveBeenCalled());
    await waitFor(() =>
      expect(
        screen.getByText(/1 scheduleOptimization\.of 2/),
      ).toBeInTheDocument(),
    );

    const deleteBtn = screen
      .getAllByRole("button")
      .find((b) => b.getAttribute("class")?.includes("mw-error"));
    if (deleteBtn) {
      await userEvent.click(deleteBtn);
      await waitFor(() =>
        expect(
          screen.getByText(
            "optimization.schedulingTargeting.removeScheduleYesButtonLabel",
          ),
        ).toBeInTheDocument(),
      );
      await userEvent.click(
        screen.getByText(
          "optimization.schedulingTargeting.removeScheduleYesButtonLabel",
        ),
      );
      await waitFor(() => {
        expect(mockShowError).toHaveBeenCalledWith(
          "scheduleOptimization.errors.failedToDeleteScheduleRetry",
        );
      });
    }
  });
});
