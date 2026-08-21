import { render, screen, waitFor } from "@testing-library/react";
import React from "react";
import { Provider } from "react-redux";
import { describe, expect, it, vi, beforeEach } from "vitest";

import { createOptimizationStore } from "./mocks";
import OptimizationForm, { type OptimizationFormRef } from "../Optimization";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const mockShowError = vi.fn();
vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({
    showError: mockShowError,
    showSuccess: vi.fn(),
    showWarning: vi.fn(),
  }),
}));

vi.mock("../BudgetAllocationOptimization", () => ({
  default: () => <div data-testid="budget-allocation">BudgetAllocation</div>,
}));

vi.mock("../ScheduleOptimization", () => ({
  default: () => (
    <div data-testid="schedule-optimization">ScheduleOptimization</div>
  ),
}));

vi.mock("../../common/CampaignForecast", () => ({
  default: () => <div data-testid="campaign-forecast">CampaignForecast</div>,
}));

const mockAutosave = vi.fn().mockResolvedValue({
  data: {
    id: "campaign-1",
    budgetAllocation: { digital: 25, transit: 25, classic: 25, retail: 25 },
  },
});
const mockFetchForecast = vi.fn().mockReturnValue({
  unwrap: () =>
    Promise.resolve({
      success: true,
      data: {
        totalInventories: 0,
        estimatedImpression: 0,
        estimatedReach: 0,
        estimatedFrequency: 0,
        estimatedAdPlays: 0,
        sov: 0,
        avgCpm: 0,
        avgECpm: 0,
        totalCost: 0,
        plannedSot: 0,
        totalSot: 0,
        warnings: [],
      },
    }),
});

const mockFetchSelectedInventory = vi.fn().mockReturnValue({
  unwrap: () =>
    Promise.resolve({
      success: true,
      data: { content: [], totalElements: 0, last: true },
    }),
});
const mockFetchSelectedInventorySchedules = vi.fn().mockReturnValue({
  unwrap: () => Promise.resolve({ success: true, data: [] }),
});

vi.mock("@services/campaign/campaignSlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/campaign/campaignSlice")>();
  return {
    ...actual,
    useAutosaveCampaignMutation: () => [mockAutosave, { isLoading: false }],
  };
});

vi.mock("@services/inventory/inventorySlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/inventory/inventorySlice")>();
  return {
    ...actual,
    useLazyGetCampaignForecastQuery: () => [
      mockFetchForecast,
      { isLoading: false },
    ],
    useLazyGetSelectedInventoryQuery: () => [mockFetchSelectedInventory, {}],
    useLazyGetSelectedInventorySchedulesQuery: () => [
      mockFetchSelectedInventorySchedules,
      {},
    ],
  };
});

describe("OptimizationForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchSelectedInventory.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: { content: [], totalElements: 0, last: true },
        }),
    });
    mockFetchSelectedInventorySchedules.mockReturnValue({
      unwrap: () => Promise.resolve({ success: true, data: [] }),
    });
  });

  it("renders BudgetAllocation and ScheduleOptimization components", async () => {
    const store = createOptimizationStore();

    render(
      <Provider store={store}>
        <OptimizationForm initialData={{}} onSubmit={vi.fn()} />
      </Provider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("schedule-optimization")).toBeInTheDocument();
    });
    expect(screen.getByTestId("campaign-forecast")).toBeInTheDocument();
  });

  it("exposes ref methods when ref is passed", async () => {
    const store = createOptimizationStore();
    const ref = React.createRef<OptimizationFormRef | null>();

    render(
      <Provider store={store}>
        <OptimizationForm ref={ref} initialData={{}} onSubmit={vi.fn()} />
      </Provider>,
    );

    await waitFor(() => {
      expect(ref.current).not.toBeNull();
    });

    expect(typeof ref.current?.getFormData).toBe("function");
    expect(typeof ref.current?.isValid).toBe("function");
    expect(typeof ref.current?.resetForm).toBe("function");
    expect(typeof ref.current?.validateStep).toBe("function");
    expect(typeof ref.current?.submitForm).toBe("function");
  });

  it("getFormData returns form data when form is valid", async () => {
    const store = createOptimizationStore();
    const ref = React.createRef<OptimizationFormRef | null>();

    render(
      <Provider store={store}>
        <OptimizationForm ref={ref} initialData={{}} onSubmit={vi.fn()} />
      </Provider>,
    );

    await waitFor(() => {
      expect(ref.current).not.toBeNull();
    });

    const data = ref.current?.getFormData();
    expect(data).toBeDefined();
    expect(typeof data).toBe("object");
  });

  it("resetForm resets budget and schedule values", async () => {
    const store = createOptimizationStore();
    const ref = React.createRef<OptimizationFormRef | null>();

    render(
      <Provider store={store}>
        <OptimizationForm ref={ref} initialData={{}} onSubmit={vi.fn()} />
      </Provider>,
    );

    await waitFor(() => {
      expect(ref.current).not.toBeNull();
    });

    expect(() => ref.current?.resetForm()).not.toThrow();
  });

  it("calls onValidationChange when form validity changes", async () => {
    const store = createOptimizationStore();
    const onValidationChange = vi.fn();

    render(
      <Provider store={store}>
        <OptimizationForm
          initialData={{}}
          onSubmit={vi.fn()}
          onValidationChange={onValidationChange}
        />
      </Provider>,
    );

    await waitFor(() => {
      expect(onValidationChange).toHaveBeenCalled();
    });
    expect(typeof onValidationChange.mock.calls[0][0]).toBe("boolean");
  });

  it("submitForm returns true", async () => {
    const store = createOptimizationStore();
    const ref = React.createRef<OptimizationFormRef | null>();

    render(
      <Provider store={store}>
        <OptimizationForm ref={ref} initialData={{}} onSubmit={vi.fn()} />
      </Provider>,
    );

    await waitFor(() => {
      expect(ref.current).not.toBeNull();
    });

    const result = await ref.current?.submitForm();
    expect(result).toBe(true);
  });

  it("validateStep returns isValid true and empty errors", async () => {
    const store = createOptimizationStore();
    const ref = React.createRef<OptimizationFormRef | null>();

    render(
      <Provider store={store}>
        <OptimizationForm ref={ref} initialData={{}} onSubmit={vi.fn()} />
      </Provider>,
    );

    await waitFor(() => {
      expect(ref.current).not.toBeNull();
    });

    const result = await ref.current?.validateStep();
    expect(result).toEqual({ isValid: true, errors: [] });
  });

  it("validateStep blocks when a selected inventory is below its selling term's minDays", async () => {
    mockFetchSelectedInventory.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: {
            content: [
              {
                detail: {
                  id: "inv-1",
                  name: "Test Inventory",
                  inventoryType: "Digital",
                  sellingTerm: { minDays: 3, minHours: 1 },
                },
              },
            ],
            totalElements: 1,
            last: true,
          },
        }),
    });
    mockFetchSelectedInventorySchedules.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: [
            {
              inventoryId: "inv-1",
              schedules: [
                { bookingMatrix: { "2025-01-01": [9], "2025-01-02": [9] } },
              ],
            },
          ],
        }),
    });

    const store = createOptimizationStore();
    const ref = React.createRef<OptimizationFormRef | null>();

    render(
      <Provider store={store}>
        <OptimizationForm ref={ref} initialData={{}} onSubmit={vi.fn()} />
      </Provider>,
    );

    await waitFor(() => {
      expect(ref.current).not.toBeNull();
    });

    const result = await ref.current?.validateStep();
    expect(result?.isValid).toBe(false);
    expect(result?.errors.length).toBeGreaterThan(0);
    expect(mockShowError).toHaveBeenCalledWith(
      "campaignWrapper.errors.minDaysViolation",
    );
  });

  it("validateStep passes when the selected inventory meets its selling term's minDays across schedules", async () => {
    mockFetchSelectedInventory.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: {
            content: [
              {
                detail: {
                  id: "inv-1",
                  name: "Test Inventory",
                  inventoryType: "Digital",
                  sellingTerm: { minDays: 2, minHours: 1 },
                },
              },
            ],
            totalElements: 1,
            last: true,
          },
        }),
    });
    mockFetchSelectedInventorySchedules.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: [
            {
              inventoryId: "inv-1",
              schedules: [
                { bookingMatrix: { "2025-01-01": [9] } },
                { bookingMatrix: { "2025-01-02": [9] } },
              ],
            },
          ],
        }),
    });

    const store = createOptimizationStore();
    const ref = React.createRef<OptimizationFormRef | null>();

    render(
      <Provider store={store}>
        <OptimizationForm ref={ref} initialData={{}} onSubmit={vi.fn()} />
      </Provider>,
    );

    await waitFor(() => {
      expect(ref.current).not.toBeNull();
    });

    const result = await ref.current?.validateStep();
    expect(result).toEqual({ isValid: true, errors: [] });
    expect(mockShowError).not.toHaveBeenCalled();
  });

  it("validateStep passes for a Classic inventory whose schedule has no bookingMatrix but covers enough days via scheduleDays + date range", async () => {
    // Regression test: Classic inventories have no hourly booking grid, so
    // bookingMatrix is always empty for them — validateStep must fall back to
    // scheduleDays + startDate/endDate instead of false-positive blocking.
    mockFetchSelectedInventory.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: {
            content: [
              {
                detail: {
                  id: "inv-classic-1",
                  name: "Classic Billboard",
                  inventoryType: "Classic",
                  sellingTerm: { minDays: 5, minHours: 0 },
                },
              },
            ],
            totalElements: 1,
            last: true,
          },
        }),
    });
    mockFetchSelectedInventorySchedules.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: [
            {
              inventoryId: "inv-classic-1",
              schedules: [
                {
                  bookingMatrix: {},
                  scheduleDays: [
                    "MONDAY",
                    "TUESDAY",
                    "WEDNESDAY",
                    "THURSDAY",
                    "FRIDAY",
                    "SATURDAY",
                    "SUNDAY",
                  ],
                  startDate: "2026-07-30",
                  endDate: "2026-08-29",
                },
              ],
            },
          ],
        }),
    });

    const store = createOptimizationStore();
    const ref = React.createRef<OptimizationFormRef | null>();

    render(
      <Provider store={store}>
        <OptimizationForm ref={ref} initialData={{}} onSubmit={vi.fn()} />
      </Provider>,
    );

    await waitFor(() => {
      expect(ref.current).not.toBeNull();
    });

    const result = await ref.current?.validateStep();
    expect(result).toEqual({ isValid: true, errors: [] });
    expect(mockShowError).not.toHaveBeenCalled();
  });

  it("isValid returns boolean", async () => {
    const store = createOptimizationStore();
    const ref = React.createRef<OptimizationFormRef | null>();

    render(
      <Provider store={store}>
        <OptimizationForm ref={ref} initialData={{}} onSubmit={vi.fn()} />
      </Provider>,
    );

    await waitFor(() => {
      expect(ref.current).not.toBeNull();
    });

    const result = ref.current?.isValid();
    expect(typeof result).toBe("boolean");
  });

  it("renders with campaignData in store (initialization branch)", async () => {
    const store = createOptimizationStore({
      campaignData: {
        budgetAllocation: { digital: 30, transit: 25, classic: 25, retail: 20 },
        scheduleTargeting: undefined,
      },
    });

    render(
      <Provider store={store}>
        <OptimizationForm initialData={{}} onSubmit={vi.fn()} />
      </Provider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("schedule-optimization")).toBeInTheDocument();
    });
  });

  it("initializes with default budgetAllocation when campaignData has empty budgetAllocation", async () => {
    const defaultScheduleTargeting = {
      weekdayDistribution: {
        MONDAY: 14.29,
        TUESDAY: 14.29,
        WEDNESDAY: 14.29,
        THURSDAY: 14.29,
        FRIDAY: 14.29,
        SATURDAY: 14.29,
        SUNDAY: 14.29,
      },
      daypartDistribution: {
        "06-10": 20,
        "10-14": 20,
        "14-18": 20,
        "18-22": 20,
        "22-06": 20,
      },
    };
    const store = createOptimizationStore({
      campaignData: {
        budgetAllocation: {},
        scheduleTargeting: defaultScheduleTargeting,
      },
    });

    render(
      <Provider store={store}>
        <OptimizationForm initialData={{}} onSubmit={vi.fn()} />
      </Provider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("schedule-optimization")).toBeInTheDocument();
    });
  });

  it("handles loadForecastData error without throwing", async () => {
    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    mockFetchForecast.mockReturnValue({
      unwrap: () => Promise.reject(new Error("Forecast failed")),
    });

    const store = createOptimizationStore();

    render(
      <Provider store={store}>
        <OptimizationForm initialData={{}} onSubmit={vi.fn()} />
      </Provider>,
    );

    await waitFor(() => {
      expect(mockFetchForecast).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(consoleSpy).toHaveBeenCalled();
    });

    consoleSpy.mockRestore();
  });

  it("loadForecastData loads the forecast (SOT kept in raw hours)", async () => {
    mockFetchForecast.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: {
            totalInventories: 5,
            estimatedImpression: 1000,
            estimatedReach: 500,
            estimatedFrequency: 2,
            estimatedAdPlays: 100,
            sov: 10,
            avgCpm: 5,
            avgECpm: 4,
            totalCost: 1000,
            plannedSot: 25,
            totalSot: 100,
            warnings: [],
          },
        }),
    });

    const store = createOptimizationStore();

    render(
      <Provider store={store}>
        <OptimizationForm initialData={{}} onSubmit={vi.fn()} />
      </Provider>,
    );

    await waitFor(() => {
      expect(mockFetchForecast).toHaveBeenCalled();
    });
  });
});
