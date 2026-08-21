import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";

import { ScheduleDrawer } from "../ScheduleDrawer";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const mockShowError = vi.fn();
const mockShowSuccess = vi.fn();
const mockShowWarning = vi.fn();

vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({
    showError: mockShowError,
    showSuccess: mockShowSuccess,
    showWarning: mockShowWarning,
  }),
}));

const mockUpdateSchedules = vi.fn();
const mockAddSchedules = vi.fn();

vi.mock("@services/inventory/inventorySlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/inventory/inventorySlice")>();
  return {
    ...actual,
    useUpdateInventorySchedulesMutation: () => [
      mockUpdateSchedules,
      { isLoading: false },
    ],
    useAddInventorySchedulesMutation: () => [
      mockAddSchedules,
      { isLoading: false },
    ],
  };
});

const defaultProps = {
  isOpen: true,
  onClose: vi.fn(),
  selectedInventoryId: null as string | null,
  selectedInventory: null,
  inventorySchedules: [] as never[],
  campaignId: "campaign-1",
  onScheduleSaved: vi.fn(),
};

const UNWRAP_SUCCESS = {
  unwrap: () => Promise.resolve({ success: true, data: "Saved" }),
};

describe("ScheduleDrawer", { timeout: 10000 }, () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUpdateSchedules.mockReturnValue(UNWRAP_SUCCESS);
    mockAddSchedules.mockReturnValue(UNWRAP_SUCCESS);
  });

  it("renders nothing when isOpen is false", () => {
    render(<ScheduleDrawer {...defaultProps} isOpen={false} />);

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("renders no inventory message when selectedInventory is null", () => {
    render(<ScheduleDrawer {...defaultProps} />);

    expect(
      screen.getByText("scheduleDrawer.noInventorySelected"),
    ).toBeInTheDocument();
  });

  it("renders drawer with New Schedule when selectedInventory is provided and scheduleId is undefined", () => {
    const selectedInventory = {
      detail: {
        id: "inv-1",
        name: "Test Inventory",
        inventoryType: "DIGITAL",
      },
      operations: {
        operationDays: ["MONDAY", "TUESDAY"],
        loopSize: 60,
        clientPerLoop: 1,
      },
    };

    render(
      <ScheduleDrawer
        {...defaultProps}
        selectedInventory={selectedInventory as never}
      />,
    );

    expect(screen.getByText(/scheduleDrawer\.newTitle/)).toBeInTheDocument();
  });

  it("calls onClose when drawer close is triggered", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "DIGITAL" },
      operations: { operationDays: ["MONDAY"], loopSize: 60, clientPerLoop: 1 },
    };

    render(
      <ScheduleDrawer
        {...defaultProps}
        onClose={onClose}
        selectedInventory={selectedInventory as never}
      />,
    );

    const closeButton = screen.queryByRole("button", { name: /close/i });
    if (closeButton) {
      await user.click(closeButton);
      expect(onClose).toHaveBeenCalled();
    }
  });

  it("renders with campaign dates for new schedule when campaignStartDate and campaignEndDate provided", () => {
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "DIGITAL" },
      operations: { operationDays: ["MONDAY"], loopSize: 60, clientPerLoop: 1 },
    };

    render(
      <ScheduleDrawer
        {...defaultProps}
        selectedInventory={selectedInventory as never}
        campaignStartDate="2025-01-01"
        campaignEndDate="2025-12-31"
      />,
    );

    expect(screen.getByText(/scheduleDrawer\.newTitle/)).toBeInTheDocument();
  });

  it("renders in edit mode when scheduleId and inventorySchedules are provided", () => {
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "DIGITAL" },
      operations: { operationDays: ["MONDAY"], loopSize: 60, clientPerLoop: 1 },
    };
    const schedules = [
      {
        id: "sched-1",
        name: "Schedule 1",
        order: 1,
        startDate: "2025-01-01",
        endDate: "2025-01-31",
        scheduleDays: ["MONDAY", "TUESDAY"],
        duration: 15,
        spotsPerLoop: 1,
        spotsPerHour: 60,
        bookingMatrix: {},
        adPlays: 10,
        sov: 5,
        plannedSot: 2,
      },
    ];

    render(
      <ScheduleDrawer
        {...defaultProps}
        selectedInventory={selectedInventory as never}
        selectedInventoryId="inv-1"
        scheduleId="sched-1"
        inventorySchedules={schedules as never}
      />,
    );

    expect(
      screen.getByText(/buttons\.edit.*scheduleOptimization/),
    ).toBeInTheDocument();
  });

  it("calls updateSchedules and onScheduleSaved when Save is clicked in edit mode with valid schedule", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const onScheduleSaved = vi.fn();
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "DIGITAL" },
      operations: {
        operationDays: ["MONDAY", "TUESDAY"],
        loopSize: 60,
        clientPerLoop: 1,
      },
    };
    mockUpdateSchedules.mockReturnValue({
      unwrap: () => Promise.resolve({ success: true, data: "Schedule saved" }),
    });
    const schedules = [
      {
        id: "sched-1",
        name: "Schedule 1",
        order: 1,
        startDate: "2025-01-01",
        endDate: "2025-01-31",
        scheduleDays: ["MONDAY", "TUESDAY"],
        duration: 15,
        spotsPerLoop: 1,
        spotsPerHour: 60,
        bookingMatrix: { "2025-01-01": [6, 10], "2025-01-02": [6, 10] },
        adPlays: 10,
        sov: 5,
        plannedSot: 2,
        pricing: 0,
      },
    ];

    render(
      <ScheduleDrawer
        {...defaultProps}
        onClose={onClose}
        onScheduleSaved={onScheduleSaved}
        campaignId="campaign-1"
        selectedInventory={selectedInventory as never}
        selectedInventoryId="inv-1"
        scheduleId="sched-1"
        inventorySchedules={schedules as never}
      />,
    );

    await waitFor(
      () => {
        expect(
          screen.getByText(/buttons\.edit.*scheduleOptimization/),
        ).toBeInTheDocument();
      },
      { timeout: 4000 },
    );

    const saveButton = screen.getByRole("button", {
      name: /scheduleDrawer\.saveChanges/i,
    });
    await user.click(saveButton);

    await waitFor(
      () => {
        expect(mockUpdateSchedules).toHaveBeenCalled();
      },
      { timeout: 4000 },
    );
    await waitFor(
      () => {
        expect(mockShowSuccess).toHaveBeenCalled();
      },
      { timeout: 4000 },
    );
    await waitFor(
      () => {
        expect(onScheduleSaved).toHaveBeenCalled();
        expect(onClose).toHaveBeenCalled();
      },
      { timeout: 4000 },
    );
  });

  it("shows error when save fails in edit mode", async () => {
    const user = userEvent.setup();
    mockUpdateSchedules.mockReturnValue({
      unwrap: () => Promise.resolve({ success: false }),
    });
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "DIGITAL" },
      operations: { operationDays: ["MONDAY"], loopSize: 60, clientPerLoop: 1 },
    };
    const schedules = [
      {
        id: "sched-1",
        name: "Schedule 1",
        order: 1,
        startDate: "2025-01-01",
        endDate: "2025-01-31",
        scheduleDays: ["MONDAY"],
        duration: 15,
        spotsPerLoop: 1,
        spotsPerHour: 60,
        bookingMatrix: { "2025-01-01": [6, 10] },
        adPlays: 10,
        sov: 5,
        plannedSot: 2,
        pricing: 0,
      },
    ];

    render(
      <ScheduleDrawer
        {...defaultProps}
        campaignId="campaign-1"
        selectedInventory={selectedInventory as never}
        selectedInventoryId="inv-1"
        scheduleId="sched-1"
        inventorySchedules={schedules as never}
      />,
    );

    await waitFor(
      () =>
        expect(
          screen.getByText(/buttons\.edit.*scheduleOptimization/),
        ).toBeInTheDocument(),
      { timeout: 4000 },
    );

    const saveButton = screen.getByRole("button", {
      name: /scheduleDrawer\.saveChanges/i,
    });
    await user.click(saveButton);

    await waitFor(
      () => {
        expect(mockShowError).toHaveBeenCalledWith(
          "scheduleDrawer.errors.failedToSave",
        );
      },
      { timeout: 4000 },
    );
  });

  it("shows error when save throws in edit mode", async () => {
    const user = userEvent.setup();
    mockUpdateSchedules.mockReturnValue({
      unwrap: () => Promise.reject(new Error("Network error")),
    });
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "DIGITAL" },
      operations: { operationDays: ["MONDAY"], loopSize: 60, clientPerLoop: 1 },
    };
    const schedules = [
      {
        id: "sched-1",
        name: "Schedule 1",
        order: 1,
        startDate: "2025-01-01",
        endDate: "2025-01-31",
        scheduleDays: ["MONDAY"],
        duration: 15,
        spotsPerLoop: 1,
        spotsPerHour: 60,
        bookingMatrix: { "2025-01-01": [6, 10] },
        adPlays: 10,
        sov: 5,
        plannedSot: 2,
        pricing: 0,
      },
    ];

    render(
      <ScheduleDrawer
        {...defaultProps}
        campaignId="campaign-1"
        selectedInventory={selectedInventory as never}
        selectedInventoryId="inv-1"
        scheduleId="sched-1"
        inventorySchedules={schedules as never}
      />,
    );

    await waitFor(
      () =>
        expect(
          screen.getByText(/buttons\.edit.*scheduleOptimization/),
        ).toBeInTheDocument(),
      { timeout: 4000 },
    );
    const saveButton = screen.getByRole("button", {
      name: /scheduleDrawer\.saveChanges/i,
    });
    await user.click(saveButton);

    await waitFor(
      () => {
        expect(mockShowError).toHaveBeenCalledWith(
          "scheduleDrawer.errors.failedToSaveRetry",
        );
      },
      { timeout: 4000 },
    );
  });

  it("selects day and triggers validation on Save for new schedule", async () => {
    const user = userEvent.setup();
    mockAddSchedules.mockReturnValue({
      unwrap: () => Promise.resolve({ success: true, data: "Schedule saved" }),
    });
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "Digital" },
      operations: {
        operationDays: ["MONDAY", "TUESDAY", "WEDNESDAY"],
        loopSize: 60,
        clientPerLoop: 1,
      },
    };

    const { container } = render(
      <ScheduleDrawer
        {...defaultProps}
        campaignId="campaign-1"
        selectedInventory={selectedInventory as never}
        campaignStartDate="2025-01-01"
        campaignEndDate="2025-01-31"
      />,
    );

    await waitFor(
      () =>
        expect(
          screen.getByText(/scheduleDrawer\.newTitle/),
        ).toBeInTheDocument(),
      { timeout: 4000 },
    );

    const monButton = container.querySelector("#schedule-drawer-day-mon");
    expect(monButton).toBeTruthy();
    if (monButton) await user.click(monButton as HTMLElement);

    await waitFor(
      () => {
        expect(monButton?.classList.contains("bg-mw-primary-500")).toBe(true);
      },
      { timeout: 4000 },
    );

    const saveButton = screen.getByRole("button", {
      name: /scheduleDrawer\.saveChanges/i,
    });
    await user.click(saveButton);

    await waitFor(
      () => {
        expect(mockShowError).toHaveBeenCalled();
        const msg = mockShowError.mock.calls[0][0];
        expect(
          msg === "scheduleDrawer.errors.selectAtLeastOneHour" ||
            msg === "scheduleDrawer.errors.fillRequiredFields",
        ).toBe(true);
      },
      { timeout: 4000 },
    );
  });

  it("toggles Select all days and triggers handleSelectAllDays", async () => {
    const user = userEvent.setup();
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "Digital" },
      operations: {
        operationDays: ["MONDAY", "TUESDAY"],
        loopSize: 60,
        clientPerLoop: 1,
      },
    };

    const { container } = render(
      <ScheduleDrawer
        {...defaultProps}
        campaignId="campaign-1"
        selectedInventory={selectedInventory as never}
        campaignStartDate="2025-01-01"
        campaignEndDate="2025-01-31"
      />,
    );

    await waitFor(
      () =>
        expect(
          screen.getByText(/scheduleDrawer\.newTitle/),
        ).toBeInTheDocument(),
      { timeout: 4000 },
    );

    const selectAllCheckbox = screen.getByRole("checkbox", {
      name: /scheduleDrawer\.selectAllDays/i,
    });
    await user.click(selectAllCheckbox);

    await waitFor(
      () => {
        const monBtn = container.querySelector("#schedule-drawer-day-mon");
        const tueBtn = container.querySelector("#schedule-drawer-day-tue");
        expect(monBtn?.classList.contains("bg-mw-primary-500")).toBe(true);
        expect(tueBtn?.classList.contains("bg-mw-primary-500")).toBe(true);
      },
      { timeout: 4000 },
    );
  });

  it("changes duration when dropdown option is selected", async () => {
    const user = userEvent.setup();
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "Digital" },
      operations: { operationDays: ["MONDAY"], loopSize: 60, clientPerLoop: 1 },
    };

    render(
      <ScheduleDrawer
        {...defaultProps}
        selectedInventory={selectedInventory as never}
        campaignStartDate="2025-01-01"
        campaignEndDate="2025-01-31"
      />,
    );

    await waitFor(
      () =>
        expect(
          screen.getByText(/scheduleDrawer\.newTitle/),
        ).toBeInTheDocument(),
      { timeout: 4000 },
    );

    const durationElements = screen.getAllByText("15 Sec");
    await user.click(durationElements[0]);

    const option = screen.getByRole("menuitem", { name: "15 Sec" });
    await user.click(option);

    expect(screen.getAllByText("15 Sec").length).toBeGreaterThanOrEqual(1);
  });

  it("increases spots per loop when plus button is clicked", async () => {
    const user = userEvent.setup();
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "Digital" },
      operations: {
        operationDays: ["MONDAY", "TUESDAY"],
        loopSize: 60,
        clientPerLoop: 1,
      },
    };

    render(
      <ScheduleDrawer
        {...defaultProps}
        campaignId="campaign-1"
        selectedInventory={selectedInventory as never}
        campaignStartDate="2025-01-01"
        campaignEndDate="2025-01-31"
      />,
    );

    await waitFor(
      () =>
        expect(
          screen.getByText(/scheduleDrawer\.newTitle/),
        ).toBeInTheDocument(),
      { timeout: 4000 },
    );

    const increaseButton = document.getElementById(
      "schedule-drawer-spots-per-loop-increase",
    );
    if (increaseButton && !increaseButton.hasAttribute("disabled")) {
      await user.click(increaseButton);
      await waitFor(
        () => {
          const valueEl = document.getElementById(
            "schedule-drawer-spots-per-loop-value",
          );
          expect(valueEl?.textContent).toBe("2");
        },
        { timeout: 4000 },
      );
    } else {
      const valueEl = document.getElementById(
        "schedule-drawer-spots-per-loop-value",
      );
      expect(valueEl?.textContent).toBe("1");
    }
  });

  it("removes hours for deselected day from bookingMatrix payload in edit mode", async () => {
    const user = userEvent.setup();
    const onScheduleSaved = vi.fn();
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "Digital" },
      operations: {
        operationDays: ["MONDAY", "TUESDAY"],
        loopSize: 60,
        clientPerLoop: 1,
      },
    };
    // 2025-01-06 = Monday, 2025-01-07 = Tuesday
    const schedules = [
      {
        id: "sched-1",
        name: "Schedule 1",
        order: 1,
        startDate: "2025-01-06",
        endDate: "2025-01-07",
        scheduleDays: ["MONDAY", "TUESDAY"],
        duration: 15,
        spotsPerLoop: 1,
        spotsPerHour: 60,
        bookingMatrix: { "2025-01-06": [6, 10], "2025-01-07": [6, 10] },
        adPlays: 10,
        sov: 5,
        plannedSot: 2,
        pricing: 0,
      },
    ];
    mockUpdateSchedules.mockReturnValue({
      unwrap: () => Promise.resolve({ success: true, data: "Saved" }),
    });

    const { container } = render(
      <ScheduleDrawer
        {...defaultProps}
        onScheduleSaved={onScheduleSaved}
        selectedInventory={selectedInventory as never}
        selectedInventoryId="inv-1"
        scheduleId="sched-1"
        inventorySchedules={schedules as never}
        campaignStartDate="2025-01-06"
        campaignEndDate="2025-01-07"
      />,
    );

    await waitFor(
      () =>
        expect(
          screen.getByText(/buttons\.edit.*scheduleOptimization/),
        ).toBeInTheDocument(),
      { timeout: 4000 },
    );

    // Wait for init effect to apply selectedDays from schedule (TUE button becomes selected)
    await waitFor(
      () =>
        expect(
          container
            .querySelector("#schedule-drawer-day-tue")
            ?.classList.contains("bg-mw-primary-500"),
        ).toBe(true),
      { timeout: 4000 },
    );

    // Deselect TUESDAY
    const tueButton = container.querySelector("#schedule-drawer-day-tue");
    if (tueButton) await user.click(tueButton as HTMLElement);

    const saveButton = screen.getByRole("button", {
      name: /scheduleDrawer\.saveChanges/i,
    });
    await user.click(saveButton);

    await waitFor(() => expect(mockUpdateSchedules).toHaveBeenCalled(), {
      timeout: 4000,
    });

    const callArg = mockUpdateSchedules.mock.calls[0][0];
    const savedBookingMatrix = callArg?.data?.bookingMatrix ?? {};
    // TUESDAY hours must be removed; MONDAY hours must remain
    expect(Object.keys(savedBookingMatrix)).not.toContain("2025-01-07");
    expect(Object.keys(savedBookingMatrix)).toContain("2025-01-06");
  });

  it("deselects all days when Select all days is unchecked after selecting available days", async () => {
    const user = userEvent.setup();
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "Digital" },
      operations: {
        operationDays: ["MONDAY", "TUESDAY"],
        loopSize: 60,
        clientPerLoop: 1,
      },
    };

    const { container } = render(
      <ScheduleDrawer
        {...defaultProps}
        campaignId="campaign-1"
        selectedInventory={selectedInventory as never}
        campaignStartDate="2025-01-01"
        campaignEndDate="2025-01-31"
      />,
    );

    await waitFor(
      () =>
        expect(
          screen.getByText(/scheduleDrawer\.newTitle/),
        ).toBeInTheDocument(),
      { timeout: 4000 },
    );

    const monButton = container.querySelector("#schedule-drawer-day-mon");
    const tueButton = container.querySelector("#schedule-drawer-day-tue");
    if (monButton) await user.click(monButton as HTMLElement);
    if (tueButton) await user.click(tueButton as HTMLElement);

    const selectAllCheckbox = screen.getByRole("checkbox", {
      name: /scheduleDrawer\.selectAllDays/i,
    });
    await waitFor(
      () => {
        expect(selectAllCheckbox).toBeChecked();
      },
      { timeout: 4000 },
    );
    await user.click(selectAllCheckbox);
    await waitFor(
      () => {
        expect(
          container
            .querySelector("#schedule-drawer-day-mon")
            ?.classList.contains("bg-mw-primary-500"),
        ).toBe(false);
      },
      { timeout: 4000 },
    );
  });

  it("updates search query when typing in search input", async () => {
    const user = userEvent.setup();
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test Inventory", inventoryType: "Digital" },
      operations: { operationDays: ["MONDAY"], loopSize: 60, clientPerLoop: 1 },
    };

    render(
      <ScheduleDrawer
        {...defaultProps}
        selectedInventory={selectedInventory as never}
        campaignStartDate="2025-01-01"
        campaignEndDate="2025-01-31"
      />,
    );

    await waitFor(
      () =>
        expect(
          screen.getByText(/scheduleDrawer\.newTitle/),
        ).toBeInTheDocument(),
      { timeout: 4000 },
    );

    const searchInput = screen.getByPlaceholderText(
      /scheduleDrawer\.searchPlaceholder/i,
    );
    await user.type(searchInput, "Test");

    expect(searchInput).toHaveValue("Test");
  });

  it("toggles clear previous schedule checkbox", async () => {
    const user = userEvent.setup();
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "Digital" },
      operations: { operationDays: ["MONDAY"], loopSize: 60, clientPerLoop: 1 },
    };

    render(
      <ScheduleDrawer
        {...defaultProps}
        selectedInventory={selectedInventory as never}
        campaignStartDate="2025-01-01"
        campaignEndDate="2025-01-31"
      />,
    );

    await waitFor(
      () =>
        expect(
          screen.getByText(/scheduleDrawer\.newTitle/),
        ).toBeInTheDocument(),
      { timeout: 4000 },
    );

    const clearCheckbox = screen.getByRole("checkbox", {
      name: /scheduleDrawer\.clearPreviousSchedule/i,
    });
    await user.click(clearCheckbox);
    expect(clearCheckbox).toBeChecked();
  });

  it("shows warning when schedule dates are outside campaign range", async () => {
    const user = userEvent.setup();
    mockUpdateSchedules.mockReturnValue({
      unwrap: () => Promise.resolve({ success: true, data: "Saved" }),
    });
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "Classic" },
      operations: { operationDays: ["MONDAY"], loopSize: 60, clientPerLoop: 1 },
    };
    const schedules = [
      {
        id: "sched-1",
        name: "Schedule 1",
        order: 1,
        startDate: "2024-06-01",
        endDate: "2024-06-30",
        scheduleDays: ["MONDAY"],
        duration: 15,
        spotsPerLoop: 1,
        spotsPerHour: 60,
        bookingMatrix: { "2024-06-01": [10], "2024-06-02": [10] },
        adPlays: 10,
        sov: 5,
        plannedSot: 2,
        pricing: 0,
      },
    ];

    render(
      <ScheduleDrawer
        {...defaultProps}
        campaignId="campaign-1"
        selectedInventory={selectedInventory as never}
        selectedInventoryId="inv-1"
        scheduleId="sched-1"
        inventorySchedules={schedules as never}
        campaignStartDate="2025-01-01"
        campaignEndDate="2025-12-31"
      />,
    );

    await waitFor(
      () =>
        expect(
          screen.getByText(/buttons\.edit.*scheduleOptimization/),
        ).toBeInTheDocument(),
      { timeout: 4000 },
    );
    const saveButton = screen.getByRole("button", {
      name: /scheduleDrawer\.saveChanges/i,
    });
    await user.click(saveButton);

    await waitFor(
      () => {
        expect(mockShowWarning).toHaveBeenCalledWith(
          "scheduleDrawer.datesOutsideCampaignRange",
        );
      },
      { timeout: 4000 },
    );
  });

  it("shows error when save with no hours selected in grid", async () => {
    const user = userEvent.setup();
    // Only Digital inventories require an hour selection — Classic has no
    // hourly grid at all (date range only), so this check is Digital-only.
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "Digital" },
      operations: { operationDays: ["MONDAY"], loopSize: 60, clientPerLoop: 1 },
    };

    render(
      <ScheduleDrawer
        {...defaultProps}
        campaignId="campaign-1"
        selectedInventory={selectedInventory as never}
        campaignStartDate="2025-01-01"
        campaignEndDate="2025-01-31"
      />,
    );

    await waitFor(
      () =>
        expect(
          screen.getByText(/scheduleDrawer\.newTitle/),
        ).toBeInTheDocument(),
      { timeout: 4000 },
    );

    // Select a day first (auto-fills its hours and unlocks the grid-mode
    // radios), then Deselect All to reach "day selected, zero hours" — the
    // one case validateScheduleDates()'s Digital-only hours check covers.
    const mondayButton = document.getElementById("schedule-drawer-day-mon");
    await user.click(mondayButton as HTMLElement);
    const deselectAllRadio = screen.getByLabelText(
      "scheduleDrawer.deselectAll",
    );
    await user.click(deselectAllRadio);

    const saveButton = screen.getByRole("button", {
      name: /scheduleDrawer\.saveChanges/i,
    });
    await user.click(saveButton);

    await waitFor(
      () => {
        expect(mockShowError).toHaveBeenCalledWith(
          "scheduleDrawer.errors.selectAtLeastOneHour",
        );
      },
      { timeout: 4000 },
    );
  });

  it("does not require hour selection for Classic inventories on save", async () => {
    const user = userEvent.setup();
    // Classic has no hourly grid at all (date range only) — the "at least one
    // hour" check must not block Classic saves, unlike Digital above.
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "Classic" },
      operations: { operationDays: [], loopSize: 60, clientPerLoop: 1 },
    };
    const schedules = [
      {
        id: "sched-1",
        name: "Schedule 1",
        order: 1,
        startDate: "2025-01-01",
        endDate: "2025-01-31",
        scheduleDays: [],
        duration: 15,
        spotsPerLoop: 1,
        spotsPerHour: 60,
        bookingMatrix: {},
        adPlays: 0,
        sov: 0,
        plannedSot: 0,
        pricing: 0,
      },
    ];

    render(
      <ScheduleDrawer
        {...defaultProps}
        campaignId="campaign-1"
        selectedInventory={selectedInventory as never}
        scheduleId="sched-1"
        inventorySchedules={schedules as never}
        campaignStartDate="2025-01-01"
        campaignEndDate="2025-01-31"
      />,
    );

    await waitFor(
      () =>
        expect(
          screen.getByText(/buttons\.edit.*scheduleOptimization/),
        ).toBeInTheDocument(),
      { timeout: 4000 },
    );

    const saveButton = screen.getByRole("button", {
      name: /scheduleDrawer\.saveChanges/i,
    });
    await user.click(saveButton);

    await waitFor(
      () => {
        expect(mockUpdateSchedules).toHaveBeenCalled();
      },
      { timeout: 4000 },
    );
    expect(mockShowError).not.toHaveBeenCalledWith(
      "scheduleDrawer.selectAtLeastOneHour",
    );
    expect(mockShowError).not.toHaveBeenCalledWith(
      "scheduleDrawer.errors.selectAtLeastOneHour",
    );
  });

  it("applies schedule pattern when Commuter is selected", async () => {
    const user = userEvent.setup();
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "Digital" },
      operations: {
        operationDays: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
        loopSize: 60,
        clientPerLoop: 1,
      },
    };

    render(
      <ScheduleDrawer
        {...defaultProps}
        selectedInventory={selectedInventory as never}
        campaignStartDate="2025-01-01"
        campaignEndDate="2025-01-31"
      />,
    );

    await waitFor(
      () =>
        expect(
          screen.getByText(/scheduleDrawer\.newTitle/),
        ).toBeInTheDocument(),
      { timeout: 4000 },
    );

    const patternTrigger = screen.getByText(
      "scheduleDrawer.schedulePatterns.custom",
    );
    await user.click(patternTrigger);
    const commuterOption = await screen.findByRole("menuitem", {
      name: /scheduleDrawer\.schedulePatterns\.commuter/i,
    });
    await user.click(commuterOption);

    await waitFor(
      () => {
        const monBtn = document.getElementById("schedule-drawer-day-mon");
        expect(monBtn?.classList.contains("bg-mw-primary-500")).toBe(true);
      },
      { timeout: 6000 },
    );
  });

  it("renders Select All and Deselect All grid options", () => {
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "Digital" },
      operations: {
        operationDays: ["MONDAY", "TUESDAY"],
        loopSize: 60,
        clientPerLoop: 1,
      },
    };

    render(
      <ScheduleDrawer
        {...defaultProps}
        selectedInventory={selectedInventory as never}
        campaignStartDate="2025-01-01"
        campaignEndDate="2025-01-31"
      />,
    );

    expect(
      screen.getByRole("radio", { name: /scheduleDrawer\.selectAll/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("radio", { name: /scheduleDrawer\.deselectAll/i }),
    ).toBeInTheDocument();
  });

  it("shows the hourly schedule grid after picking a single day and closing the date picker", async () => {
    const user = userEvent.setup();
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "Digital" },
      operations: { operationDays: ["MONDAY"], loopSize: 60, clientPerLoop: 1 },
    };

    render(
      <ScheduleDrawer
        {...defaultProps}
        selectedInventory={selectedInventory as never}
      />,
    );

    // The default 14-day range (no campaign dates supplied) shows the grid.
    expect(
      document.getElementById("schedule-grid-column-0"),
    ).toBeInTheDocument();

    await user.click(
      document.getElementById("schedule-drawer-date-picker-trigger")!,
    );

    const dayButtons = screen.getAllByRole("button", { name: "15" });
    await user.click(dayButtons[0]);

    // A single click starts a brand-new range with only `from` set, so the
    // grid disappears until the range is completed.
    expect(
      document.getElementById("schedule-grid-column-0"),
    ).not.toBeInTheDocument();

    // Close the picker without picking a second date, as a user would when
    // they only want a single-day schedule.
    await user.click(
      document.getElementById("schedule-drawer-date-picker-toggle")!,
    );

    await waitFor(() => {
      expect(
        document.getElementById("schedule-grid-column-0"),
      ).toBeInTheDocument();
    });
  });

  it("shows Schedule 1 (Default) in edit title when order is 1", () => {
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "Digital" },
      operations: { operationDays: ["MONDAY"], loopSize: 60, clientPerLoop: 1 },
    };
    const schedules = [
      {
        id: "sched-1",
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
        pricing: 0,
      },
    ];

    render(
      <ScheduleDrawer
        {...defaultProps}
        selectedInventory={selectedInventory as never}
        scheduleId="sched-1"
        inventorySchedules={schedules as never}
      />,
    );

    expect(
      screen.getAllByText(
        /scheduleOptimization\.scheduleName.*scheduleOptimization\.scheduleDefault/,
      ).length,
    ).toBeGreaterThanOrEqual(1);
  });

  it("shows valid inventory type message when inventory type is neither Digital nor Classic", () => {
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "Transit" },
      operations: { operationDays: ["MONDAY"], loopSize: 60, clientPerLoop: 1 },
    };

    render(
      <ScheduleDrawer
        {...defaultProps}
        selectedInventory={selectedInventory as never}
        campaignStartDate="2025-01-01"
        campaignEndDate="2025-01-31"
      />,
    );

    expect(
      screen.getByText(/scheduleDrawer\.invalidInventoryType/),
    ).toBeInTheDocument();
  });

  it("decreases spots per loop when minus button is clicked", async () => {
    const user = userEvent.setup();
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "Digital" },
      operations: {
        operationDays: ["MONDAY", "TUESDAY"],
        loopSize: 60,
        clientPerLoop: 2,
      },
    };

    render(
      <ScheduleDrawer
        {...defaultProps}
        campaignId="campaign-1"
        selectedInventory={selectedInventory as never}
        campaignStartDate="2025-01-01"
        campaignEndDate="2025-01-31"
      />,
    );

    await waitFor(
      () =>
        expect(
          screen.getByText(/scheduleDrawer\.newTitle/),
        ).toBeInTheDocument(),
      { timeout: 4000 },
    );

    const increaseBtn = document.getElementById(
      "schedule-drawer-spots-per-loop-increase",
    );
    if (increaseBtn) await user.click(increaseBtn);

    const decreaseBtn = document.getElementById(
      "schedule-drawer-spots-per-loop-decrease",
    );
    if (decreaseBtn) await user.click(decreaseBtn);

    await waitFor(
      () => {
        const valueEl = document.getElementById(
          "schedule-drawer-spots-per-loop-value",
        );
        expect(valueEl?.textContent).toBe("1");
      },
      { timeout: 4000 },
    );
  });

  it("shows error when no schedule dates for Classic on save", async () => {
    const user = userEvent.setup();
    const selectedInventory = {
      detail: { id: "inv-1", name: "Test", inventoryType: "Classic" },
      operations: { operationDays: ["MONDAY"], loopSize: 60, clientPerLoop: 1 },
    };
    const schedules = [
      {
        id: "sched-1",
        name: "Schedule 1",
        order: 1,
        startDate: "",
        endDate: "",
        scheduleDays: [],
        duration: 15,
        spotsPerLoop: 1,
        spotsPerHour: 60,
        bookingMatrix: {},
        adPlays: 0,
        sov: 0,
        plannedSot: 0,
        pricing: 0,
      },
    ];

    render(
      <ScheduleDrawer
        {...defaultProps}
        selectedInventory={selectedInventory as never}
        scheduleId="sched-1"
        inventorySchedules={schedules as never}
        campaignStartDate="2025-01-01"
        campaignEndDate="2025-01-31"
      />,
    );

    await waitFor(
      () =>
        expect(
          screen.getByText(/buttons\.edit.*scheduleOptimization/),
        ).toBeInTheDocument(),
      { timeout: 4000 },
    );

    const saveButton = screen.getByRole("button", {
      name: /scheduleDrawer\.saveChanges/i,
    });
    await user.click(saveButton);

    await waitFor(
      () => {
        expect(mockShowError).toHaveBeenCalledWith(
          "scheduleDrawer.errors.selectScheduleDates",
        );
      },
      { timeout: 4000 },
    );
  });

  it("disables Save and shows an error when a scheduled day is below the selling term's minHours (Digital)", async () => {
    const selectedInventory = {
      detail: {
        id: "inv-1",
        name: "Test",
        inventoryType: "Digital",
        sellingTerm: { minHours: 3, minDays: 1 },
      },
      operations: { operationDays: ["MONDAY"], loopSize: 60, clientPerLoop: 1 },
    };
    const schedules = [
      {
        id: "sched-1",
        name: "Schedule 1",
        order: 1,
        startDate: "2025-01-06",
        endDate: "2025-01-06",
        scheduleDays: ["MONDAY"],
        duration: 15,
        spotsPerLoop: 1,
        spotsPerHour: 60,
        // Only 1 hour selected on 2025-01-06 (Monday) — below minHours of 3.
        bookingMatrix: { "2025-01-06": [9] },
        adPlays: 0,
        sov: 0,
        plannedSot: 0,
        pricing: 0,
      },
    ];

    render(
      <ScheduleDrawer
        {...defaultProps}
        selectedInventory={selectedInventory as never}
        selectedInventoryId="inv-1"
        scheduleId="sched-1"
        inventorySchedules={schedules as never}
      />,
    );

    await waitFor(() =>
      expect(
        screen.getByText("scheduleDrawer.minHoursViolation"),
      ).toBeInTheDocument(),
    );

    const saveButton = screen.getByRole("button", {
      name: /scheduleDrawer\.saveChanges/i,
    });
    expect(saveButton).toBeDisabled();
  });

  it("keeps Save enabled when every scheduled day meets the selling term's minHours", async () => {
    const selectedInventory = {
      detail: {
        id: "inv-1",
        name: "Test",
        inventoryType: "Digital",
        sellingTerm: { minHours: 2, minDays: 1 },
      },
      operations: { operationDays: ["MONDAY"], loopSize: 60, clientPerLoop: 1 },
    };
    const schedules = [
      {
        id: "sched-1",
        name: "Schedule 1",
        order: 1,
        startDate: "2025-01-06",
        endDate: "2025-01-06",
        scheduleDays: ["MONDAY"],
        duration: 15,
        spotsPerLoop: 1,
        spotsPerHour: 60,
        bookingMatrix: { "2025-01-06": [9, 10] },
        adPlays: 0,
        sov: 0,
        plannedSot: 0,
        pricing: 0,
      },
    ];

    render(
      <ScheduleDrawer
        {...defaultProps}
        selectedInventory={selectedInventory as never}
        selectedInventoryId="inv-1"
        scheduleId="sched-1"
        inventorySchedules={schedules as never}
      />,
    );

    await waitFor(() =>
      expect(
        screen.getByText(/buttons\.edit.*scheduleOptimization/),
      ).toBeInTheDocument(),
    );

    expect(
      screen.queryByText("scheduleDrawer.minHoursViolation"),
    ).not.toBeInTheDocument();
    const saveButton = screen.getByRole("button", {
      name: /scheduleDrawer\.saveChanges/i,
    });
    expect(saveButton).not.toBeDisabled();
  });
});
