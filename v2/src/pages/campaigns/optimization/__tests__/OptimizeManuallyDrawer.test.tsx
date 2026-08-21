import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect } from "react";
import { describe, expect, it, vi, beforeEach } from "vitest";

import { DEFAULT_CAMPAIGN_ID, defaultCampaignState } from "./mocks";
import { OptimizeManuallyDrawer } from "../OptimizeManuallyDrawer";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@components/common/ScheduleGrid", () => {
  return {
    ScheduleGrid: ({
      onCellClick,
      startDate,
    }: {
      onCellClick: (date: Date, hour: number) => void;
      startDate: Date;
    }) => {
      useEffect(() => {
        if (!onCellClick || !startDate) return;
        const id = setTimeout(() => {
          act(() => {
            const d = new Date(startDate);
            d.setHours(0, 0, 0, 0);
            onCellClick(d, 0);
            onCellClick(d, 1);
          });
        }, 1000);
        return () => clearTimeout(id);
      }, [onCellClick, startDate]);
      return <div data-testid="schedule-grid-mock">ScheduleGrid</div>;
    },
  };
});

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

const mockSelectedItems: {
  detail: { id: string; name: string };
  schedules: unknown[];
  location?: unknown;
  performance?: unknown;
  operations?: unknown;
}[] = [];
// Capture the options the drawer passes to the hook so we can assert the
// `enabled` gate (must follow isOpen, else the list is a stale snapshot).
const mockHookOptions: { enabled?: boolean } = {};
vi.mock("@hooks/useSelectedInventoryList", () => ({
  useSelectedInventoryList: (opts: { enabled?: boolean }) => {
    mockHookOptions.enabled = opts?.enabled;
    return {
      get selectedItems() {
        return mockSelectedItems;
      },
      isLoading: false,
    };
  },
}));

const mockFetchSchedules = vi.fn().mockReturnValue({
  unwrap: () => Promise.resolve({ success: true, data: [] }),
});
const mockSaveOptimizedSchedule = vi.fn().mockReturnValue({
  unwrap: () => Promise.resolve({ success: true, data: "Saved" }),
});

vi.mock("@services/inventory/inventorySlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/inventory/inventorySlice")>();
  return {
    ...actual,
    useLazyGetSelectedInventorySchedulesQuery: () => [mockFetchSchedules, {}],
    useOptimizeInventorySchedulesMutation: () => [
      mockSaveOptimizedSchedule,
      {},
    ],
  };
});

const defaultProps = {
  isOpen: true,
  onClose: vi.fn(),
  campaignId: DEFAULT_CAMPAIGN_ID,
  campaignState: defaultCampaignState,
};

const oneInventoryItem = {
  detail: { id: "inv-1", name: "Test Inventory", inventoryType: "Digital" },
  schedules: [],
};

const oneClassicInventoryItem = {
  detail: {
    id: "inv-classic-1",
    name: "Classic Inventory",
    inventoryType: "Classic",
  },
  schedules: [],
};

describe("OptimizeManuallyDrawer", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockShowError.mockClear();
    mockShowSuccess.mockClear();
    mockShowWarning.mockClear();
    mockSelectedItems.length = 0;
    mockFetchSchedules.mockReturnValue({
      unwrap: () => Promise.resolve({ success: true, data: [] }),
    });
    mockSaveOptimizedSchedule.mockReturnValue({
      unwrap: () => Promise.resolve({ success: true, data: "Saved" }),
    });
  });

  it("renders nothing when isOpen is false", () => {
    render(<OptimizeManuallyDrawer {...defaultProps} isOpen={false} />);

    expect(
      screen.queryByText("optimization.optimizeManuallyDrawer.title"),
    ).not.toBeInTheDocument();
  });

  it("disables the selected-inventory fetch while the drawer is closed", () => {
    render(<OptimizeManuallyDrawer {...defaultProps} isOpen={false} />);
    // enabled must follow isOpen so the hook resets on close and refetches on
    // open — otherwise a deleted inventory keeps showing (stale snapshot).
    expect(mockHookOptions.enabled).toBe(false);
  });

  it("enables the selected-inventory fetch when the drawer is open", () => {
    render(<OptimizeManuallyDrawer {...defaultProps} isOpen={true} />);
    expect(mockHookOptions.enabled).toBe(true);
  });

  it("renders drawer when isOpen is true (smoke)", async () => {
    render(<OptimizeManuallyDrawer {...defaultProps} />);

    await waitFor(
      () => {
        expect(
          screen.getByText("optimization.optimizeManuallyDrawer.title"),
        ).toBeInTheDocument();
      },
      { timeout: 15000 },
    );
  }, 25000);

  it("calls onClose when cancel button is clicked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();

    render(<OptimizeManuallyDrawer {...defaultProps} onClose={onClose} />);

    const cancelButton = await screen.findByRole(
      "button",
      { name: "optimization.optimizeManuallyDrawer.cancel" },
      { timeout: 15000 },
    );
    await user.click(cancelButton);

    expect(onClose).toHaveBeenCalledTimes(1);
  }, 30000);

  it("shows error when Save Changes is clicked with no hours selected", async () => {
    const user = userEvent.setup();

    render(<OptimizeManuallyDrawer {...defaultProps} />);

    await waitFor(
      () => {
        expect(
          screen.getByText("optimization.optimizeManuallyDrawer.title"),
        ).toBeInTheDocument();
      },
      { timeout: 15000 },
    );

    const saveButton = await screen.findByRole("button", {
      name: /optimization\.optimizeManuallyDrawer\.saveChanges/i,
    });
    await user.click(saveButton);

    await waitFor(
      () => {
        expect(mockShowError).toHaveBeenCalled();
        const msg = mockShowError.mock.calls[0]?.[0];
        expect(
          msg === "optimizeManually.errors.selectInventory" ||
            msg === "optimizeManually.errors.selectScheduleDates" ||
            msg === "optimization.optimizeManuallyDrawer.noHoursSelected" ||
            msg ===
              "Please select any hour for any day to save schedule for inventories!",
        ).toBe(true);
      },
      { timeout: 2000 },
    );
  }, 25000);

  it("shows error when Save Changes is clicked with hours but no inventory selected", async () => {
    const user = userEvent.setup();
    mockSelectedItems.push(oneInventoryItem as never);

    render(<OptimizeManuallyDrawer {...defaultProps} />);

    await waitFor(
      () => {
        expect(
          screen.getByText("optimization.optimizeManuallyDrawer.title"),
        ).toBeInTheDocument();
      },
      { timeout: 15000 },
    );

    await waitFor(
      () => {
        expect(screen.getByTestId("schedule-grid-mock")).toBeInTheDocument();
      },
      { timeout: 5000 },
    );
    await new Promise((r) => setTimeout(r, 1100));

    const inventoryCheckbox = document.getElementById("checkbox-0");
    expect(inventoryCheckbox).toBeInTheDocument();
    await user.click(inventoryCheckbox!);

    const saveButton = screen.getByRole("button", {
      name: /optimization\.optimizeManuallyDrawer\.saveChanges/i,
    });
    await user.click(saveButton);

    await waitFor(() => {
      expect(mockShowError).toHaveBeenCalledTimes(1);
      const msg = mockShowError.mock.calls[0][0];
      expect(
        msg === "optimizeManually.errors.selectInventory" ||
          msg === "optimizeManually.errors.selectScheduleDates",
      ).toBe(true);
    });
  }, 30000);

  it("opens save modal when Save Changes is clicked with hours and inventory selected", async () => {
    const user = userEvent.setup();
    mockSelectedItems.push(oneInventoryItem as never);

    render(<OptimizeManuallyDrawer {...defaultProps} />);

    await waitFor(
      () => {
        expect(
          screen.getByText("optimization.optimizeManuallyDrawer.title"),
        ).toBeInTheDocument();
      },
      { timeout: 15000 },
    );

    await waitFor(
      () => {
        expect(screen.getByTestId("schedule-grid-mock")).toBeInTheDocument();
      },
      { timeout: 5000 },
    );
    await new Promise((r) => setTimeout(r, 1100));

    const saveButton = await screen.findByRole("button", {
      name: /optimization\.optimizeManuallyDrawer\.saveChanges/i,
    });
    await user.click(saveButton);

    await waitFor(() => {
      expect(
        screen.getByText(
          "optimization.optimizeManuallyDrawer.saveChangesTitle",
        ),
      ).toBeInTheDocument();
    });
  }, 30000);

  it("closes save modal when close button is clicked", async () => {
    const user = userEvent.setup();
    mockSelectedItems.push(oneInventoryItem as never);

    render(<OptimizeManuallyDrawer {...defaultProps} />);

    await waitFor(
      () => {
        expect(
          screen.getByText("optimization.optimizeManuallyDrawer.title"),
        ).toBeInTheDocument();
      },
      { timeout: 15000 },
    );
    await waitFor(
      () =>
        expect(screen.getByTestId("schedule-grid-mock")).toBeInTheDocument(),
      { timeout: 5000 },
    );
    await new Promise((r) => setTimeout(r, 1100));

    const saveButton = await screen.findByRole("button", {
      name: /optimization\.optimizeManuallyDrawer\.saveChanges/i,
    });
    await user.click(saveButton);

    await waitFor(() => {
      expect(
        screen.getByText(
          "optimization.optimizeManuallyDrawer.saveChangesTitle",
        ),
      ).toBeInTheDocument();
    });

    const closeButton = document.getElementById(
      "modal-optimization.optimizeManuallyDrawer.saveChangesTitle-close",
    );
    if (closeButton) {
      await user.click(closeButton);
      await waitFor(() => {
        expect(
          screen.queryByText(
            "optimization.optimizeManuallyDrawer.saveChangesTitle",
          ),
        ).not.toBeInTheDocument();
      });
    }
  }, 30000);

  it("calls showSuccess and onClose when save succeeds", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    mockSelectedItems.push(oneInventoryItem as never);
    mockSaveOptimizedSchedule.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: "Schedules saved successfully",
        }),
    });

    render(<OptimizeManuallyDrawer {...defaultProps} onClose={onClose} />);

    await waitFor(
      () => {
        expect(
          screen.getByText("optimization.optimizeManuallyDrawer.title"),
        ).toBeInTheDocument();
      },
      { timeout: 15000 },
    );
    await waitFor(
      () =>
        expect(screen.getByTestId("schedule-grid-mock")).toBeInTheDocument(),
      { timeout: 5000 },
    );
    await new Promise((r) => setTimeout(r, 1100));

    const saveButton = await screen.findByRole("button", {
      name: /optimization\.optimizeManuallyDrawer\.saveChanges/i,
    });
    await user.click(saveButton);

    await waitFor(() => {
      expect(
        screen.getByText(
          "optimization.optimizeManuallyDrawer.saveChangesTitle",
        ),
      ).toBeInTheDocument();
    });

    const primaryButton = screen.getByRole("button", {
      name: /optimization\.optimizeManuallyDrawer\.saveChangesPrimaryButtonLabel/i,
    });
    await user.click(primaryButton);

    await waitFor(() => {
      expect(mockShowSuccess).toHaveBeenCalledWith(
        "Schedules saved successfully",
      );
      expect(onClose).toHaveBeenCalled();
    });
  }, 30000);

  it("calls showError when save returns success false", async () => {
    const user = userEvent.setup();
    mockSelectedItems.push(oneInventoryItem as never);
    mockSaveOptimizedSchedule.mockReturnValue({
      unwrap: () => Promise.resolve({ success: false, data: "Server error" }),
    });

    render(<OptimizeManuallyDrawer {...defaultProps} />);

    await waitFor(
      () => {
        expect(
          screen.getByText("optimization.optimizeManuallyDrawer.title"),
        ).toBeInTheDocument();
      },
      { timeout: 15000 },
    );
    await waitFor(
      () =>
        expect(screen.getByTestId("schedule-grid-mock")).toBeInTheDocument(),
      { timeout: 5000 },
    );
    await new Promise((r) => setTimeout(r, 1100));

    const saveButton = await screen.findByRole("button", {
      name: /optimization\.optimizeManuallyDrawer\.saveChanges/i,
    });
    await user.click(saveButton);

    await waitFor(() => {
      expect(
        screen.getByText(
          "optimization.optimizeManuallyDrawer.saveChangesTitle",
        ),
      ).toBeInTheDocument();
    });

    const primaryButton = screen.getByRole("button", {
      name: /optimization\.optimizeManuallyDrawer\.saveChangesPrimaryButtonLabel/i,
    });
    await user.click(primaryButton);

    await waitFor(() => {
      expect(mockShowError).toHaveBeenCalledWith("Server error");
    });
  }, 30000);

  it("handles save API rejection without showing success", async () => {
    const user = userEvent.setup();
    mockSelectedItems.push(oneInventoryItem as never);
    mockSaveOptimizedSchedule.mockReturnValue({
      unwrap: () => Promise.reject(new Error("Network error")),
    });

    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    try {
      render(<OptimizeManuallyDrawer {...defaultProps} />);

      await waitFor(
        () => {
          expect(
            screen.getByText("optimization.optimizeManuallyDrawer.title"),
          ).toBeInTheDocument();
        },
        { timeout: 15000 },
      );
      await waitFor(
        () =>
          expect(screen.getByTestId("schedule-grid-mock")).toBeInTheDocument(),
        { timeout: 5000 },
      );
      await new Promise((r) => setTimeout(r, 1100));

      const saveButton = await screen.findByRole("button", {
        name: /optimization\.optimizeManuallyDrawer\.saveChanges/i,
      });
      await user.click(saveButton);

      await waitFor(() => {
        expect(
          screen.getByText(
            "optimization.optimizeManuallyDrawer.saveChangesTitle",
          ),
        ).toBeInTheDocument();
      });

      const primaryButton = screen.getByRole("button", {
        name: /optimization\.optimizeManuallyDrawer\.saveChangesPrimaryButtonLabel/i,
      });
      await user.click(primaryButton);

      await waitFor(() => {
        expect(mockShowSuccess).not.toHaveBeenCalled();
      });
    } finally {
      consoleSpy.mockRestore();
    }
  }, 30000);

  describe("Classic vs Digital field visibility", () => {
    it("hides day selection and schedule grid fields when all selected inventories are Classic", async () => {
      mockSelectedItems.push(oneClassicInventoryItem as never);

      render(<OptimizeManuallyDrawer {...defaultProps} />);

      await waitFor(
        () => {
          expect(
            screen.getByText("optimization.optimizeManuallyDrawer.title"),
          ).toBeInTheDocument();
        },
        { timeout: 15000 },
      );

      // Classic-applicable field still renders
      expect(
        screen.getByText("optimization.optimizeManuallyDrawer.scheduleDate"),
      ).toBeInTheDocument();

      // Digital-only fields must be hidden
      expect(
        screen.queryByText(
          "optimization.optimizeManuallyDrawer.dayScheduleTitle",
        ),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByText("optimization.optimizeManuallyDrawer.scheduleGrids"),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByTestId("schedule-grid-mock"),
      ).not.toBeInTheDocument();
    }, 25000);

    it("shows day selection and schedule grid fields when the selected inventory is Digital", async () => {
      mockSelectedItems.push(oneInventoryItem as never);

      render(<OptimizeManuallyDrawer {...defaultProps} />);

      await waitFor(
        () => {
          expect(
            screen.getByText("optimization.optimizeManuallyDrawer.title"),
          ).toBeInTheDocument();
        },
        { timeout: 15000 },
      );

      await waitFor(() => {
        expect(screen.getByTestId("schedule-grid-mock")).toBeInTheDocument();
      });
      expect(
        screen.getByText(
          "optimization.optimizeManuallyDrawer.dayScheduleTitle",
        ),
      ).toBeInTheDocument();
    }, 25000);

    it("shows schedule grid fields and the classic-not-applicable banner for a mixed Classic + Digital selection", async () => {
      mockSelectedItems.push(
        oneInventoryItem as never,
        oneClassicInventoryItem as never,
      );

      render(<OptimizeManuallyDrawer {...defaultProps} />);

      await waitFor(
        () => {
          expect(
            screen.getByText("optimization.optimizeManuallyDrawer.title"),
          ).toBeInTheDocument();
        },
        { timeout: 15000 },
      );

      await waitFor(() => {
        expect(screen.getByTestId("schedule-grid-mock")).toBeInTheDocument();
      });
      expect(
        screen.getByText(
          "optimization.optimizeManuallyDrawer.classicHourlyNotApplicable",
        ),
      ).toBeInTheDocument();
    }, 25000);

    it("does not flag Classic inventories with the hours-outside-operation-time error, only Digital", async () => {
      mockSelectedItems.push(
        oneInventoryItem as never,
        oneClassicInventoryItem as never,
      );

      render(<OptimizeManuallyDrawer {...defaultProps} />);

      await waitFor(
        () => {
          expect(
            screen.getByText("optimization.optimizeManuallyDrawer.title"),
          ).toBeInTheDocument();
        },
        { timeout: 15000 },
      );

      await waitFor(() => {
        expect(screen.getByTestId("schedule-grid-mock")).toBeInTheDocument();
      });

      // Both fixtures have no `operations.operatingTimes`, so once the
      // ScheduleGrid mock auto-selects hours (after its 1000ms timeout), the
      // Digital row must still flag as an error (it has no known operating
      // hours to check against) — proving the hour-selection actually ran —
      // while the Classic row must NOT, since hours don't apply to Classic.
      await waitFor(
        () => {
          const digitalHeader = screen
            .getByText("Test Inventory")
            .closest('[class*="rounded-sm"]') as HTMLElement;
          expect(digitalHeader.className).toContain("border-mw-error-500");
        },
        { timeout: 3000 },
      );

      const classicHeader = screen
        .getByText("Classic Inventory")
        .closest('[class*="rounded-sm"]') as HTMLElement;
      expect(classicHeader.className).not.toContain("error-500");
    }, 25000);
  });

  describe("Selling term minHours validation", () => {
    const inventoryWithMinHours = (minHours: number) => ({
      detail: {
        id: "inv-min-hours",
        name: "Min Hours Inventory",
        inventoryType: "Digital",
        sellingTerm: { minHours, minDays: 1 },
      },
      schedules: [],
    });

    it("disables Save and shows an error when a scheduled day is below the selling term's minHours", async () => {
      mockSelectedItems.push(inventoryWithMinHours(5) as never);

      render(<OptimizeManuallyDrawer {...defaultProps} />);

      await waitFor(
        () => {
          expect(
            screen.getByText("optimization.optimizeManuallyDrawer.title"),
          ).toBeInTheDocument();
        },
        { timeout: 15000 },
      );

      await waitFor(
        () => {
          expect(screen.getByTestId("schedule-grid-mock")).toBeInTheDocument();
        },
        { timeout: 5000 },
      );

      // Clear the initial select-all-grid fill before the mock's auto-select
      // fires, so the day ends up with only the 2 hours the mock adds
      // (below the minHours=5 requirement) instead of a full 24.
      const user = userEvent.setup();
      const deselectAllRadio = screen.getByRole("radio", {
        name: /optimization\.optimizeManuallyDrawer\.deselectAll/i,
      });
      await user.click(deselectAllRadio);

      await new Promise((r) => setTimeout(r, 1100));

      await waitFor(() => {
        expect(
          screen.getByText(
            "optimization.optimizeManuallyDrawer.minHoursViolation",
          ),
        ).toBeInTheDocument();
      });

      const saveButton = screen.getByRole("button", {
        name: /optimization\.optimizeManuallyDrawer\.saveChanges/i,
      });
      expect(saveButton).toBeDisabled();
    }, 30000);

    it("keeps Save enabled when the selected Digital inventory meets its selling term's minHours", async () => {
      mockSelectedItems.push(inventoryWithMinHours(1) as never);

      render(<OptimizeManuallyDrawer {...defaultProps} />);

      await waitFor(
        () => {
          expect(
            screen.getByText("optimization.optimizeManuallyDrawer.title"),
          ).toBeInTheDocument();
        },
        { timeout: 15000 },
      );

      await waitFor(
        () => {
          expect(screen.getByTestId("schedule-grid-mock")).toBeInTheDocument();
        },
        { timeout: 5000 },
      );
      await new Promise((r) => setTimeout(r, 1100));

      expect(
        screen.queryByText(
          "optimization.optimizeManuallyDrawer.minHoursViolation",
        ),
      ).not.toBeInTheDocument();
      const saveButton = screen.getByRole("button", {
        name: /optimization\.optimizeManuallyDrawer\.saveChanges/i,
      });
      expect(saveButton).not.toBeDisabled();
    }, 30000);
  });
});
