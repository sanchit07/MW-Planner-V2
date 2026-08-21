import { configureStore } from "@reduxjs/toolkit";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import CampaignFilterModal, { FilterValues } from "../CampaignFilterModal";

// ─── Hoisted mocks (available before vi.mock hoisting) ────────────────────────

const { mockGetUsers } = vi.hoisted(() => ({
  mockGetUsers: vi.fn(),
}));

// ─── Module mocks ─────────────────────────────────────────────────────────────

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@constants/campaign.constants", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@constants/campaign.constants")>();
  return {
    ...actual,
    CAMPAIGN_STATUS_OPTIONS: [
      { label: "Active", value: "active" },
      { label: "Draft", value: "draft" },
    ],
  };
});

vi.mock("@utils/budget.utils", () => ({
  createGoalTypes: () => [
    { label: "Awareness", value: "awareness" },
    { label: "Reach", value: "reach" },
  ],
}));

vi.mock("@services/account/accountApi", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/account/accountApi")>();
  return {
    ...actual,
    useGetUsersQuery: (
      args: { company_id: string },
      opts: { skip: boolean },
    ) => {
      if (opts.skip) {
        return {
          data: undefined,
          isLoading: false,
          isSuccess: false,
          isError: false,
        };
      }
      return {
        data: mockGetUsers(args),
        isLoading: false,
        isSuccess: true,
        isError: false,
      };
    },
  };
});

vi.mock("@components/ui/DateRangePicker", () => ({
  DateRangePicker: ({
    id,
    error,
    value,
  }: {
    id: string;
    error?: string;
    value?: { from?: Date | null; to?: Date | null };
  }) => (
    <div data-testid={id} data-has-value={Boolean(value?.from || value?.to)}>
      {error && <span data-testid="date-range-error">{error}</span>}
    </div>
  ),
}));

vi.mock("@components/ui/ModalDrawer", () => ({
  ModalDrawer: ({
    isOpen,
    children,
    title,
    footer,
    id,
  }: {
    isOpen: boolean;
    children: React.ReactNode;
    title: string;
    footer: React.ReactNode;
    id?: string;
    onClose?: () => void;
    size?: string;
  }) =>
    isOpen ? (
      <div data-testid={id || "modal-drawer"}>
        <h1>{title}</h1>
        {children}
        {footer}
      </div>
    ) : null,
}));

vi.mock("@components/ui/MultiSelect", () => ({
  __esModule: true,
  default: ({
    id,
    options,
    value,
    onChange,
    placeholder,
  }: {
    id: string;
    options: Array<{ label: string; value: string }>;
    value: string[];
    onChange: (values: string[]) => void;
    placeholder?: string;
  }) => (
    <div id={id} data-testid={id}>
      {placeholder && !value.length && <span>{placeholder}</span>}
      {value.map((v) => {
        const opt = options.find((o) => o.value === v);
        return opt ? (
          <span key={v} data-testid={`chip-${v}`}>
            {opt.label}
          </span>
        ) : null;
      })}
      {options.map((opt) => (
        <button
          key={opt.value}
          type="button"
          data-testid={`option-${opt.value}`}
          onClick={() =>
            value.includes(opt.value)
              ? onChange(value.filter((v) => v !== opt.value))
              : onChange([...value, opt.value])
          }
        >
          {opt.label}
        </button>
      ))}
    </div>
  ),
}));

vi.mock("@components/ui/Label", () => ({
  Label: ({ children, info }: { children: React.ReactNode; info?: string }) => (
    <label>
      {children}
      {info && <span data-testid="label-tooltip">{info}</span>}
    </label>
  ),
}));

vi.mock("@components/ui/Button", () => ({
  Button: ({
    id,
    children,
    onClick,
  }: {
    id?: string;
    children: React.ReactNode;
    onClick?: () => void;
    variant?: string;
    size?: string;
    className?: string;
  }) => (
    <button id={id} onClick={onClick} type="button">
      {children}
    </button>
  ),
}));

// ─── Store helpers ────────────────────────────────────────────────────────────

const createStore = (activeCompanyId: string | null = "co-1") =>
  configureStore({
    reducer: {
      profile: () => ({
        profile: activeCompanyId ? { activeCompanyId } : null,
      }),
    },
  });

const TestWrapper = ({
  children,
  store,
}: {
  children: React.ReactNode;
  store: ReturnType<typeof createStore>;
}) => <Provider store={store}>{children}</Provider>;

// ─── Stable test data ─────────────────────────────────────────────────────────

const USERS = [
  {
    id: "u1",
    first_name: "Alice",
    last_name: "Smith",
    username: "alice",
    email: "alice@test.com",
    is_active: true,
  },
  {
    id: "u2",
    first_name: "Bob",
    last_name: "Jones",
    username: "bob",
    email: "bob@test.com",
    is_active: true,
  },
];

const EMPTY_VALUES: FilterValues = {
  status: [],
  userName: [],
  period: null,
  campaignGoal: [],
};

// ─── Render helper ────────────────────────────────────────────────────────────

const renderModal = (
  props: Partial<{
    isOpen: boolean;
    onClose: ReturnType<typeof vi.fn>;
    onApply: ReturnType<typeof vi.fn>;
    initialValues: Partial<FilterValues>;
  }> = {},
  activeCompanyId: string | null = "co-1",
) => {
  const store = createStore(activeCompanyId);
  const merged = {
    isOpen: true,
    onClose: vi.fn(),
    onApply: vi.fn(),
    initialValues: EMPTY_VALUES,
    ...props,
  };
  return {
    store,
    onApply: merged.onApply,
    onClose: merged.onClose,
    ...render(
      <TestWrapper store={store}>
        <CampaignFilterModal {...merged} />
      </TestWrapper>,
    ),
  };
};

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("CampaignFilterModal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetUsers.mockReturnValue({ data: USERS });
  });

  // ── Rendering ──────────────────────────────────────────────────────────────

  describe("rendering", () => {
    it("renders the modal when isOpen is true", () => {
      renderModal();
      expect(screen.getByText("filter.title")).toBeInTheDocument();
    });

    it("does not render when isOpen is false", () => {
      renderModal({ isOpen: false });
      expect(screen.queryByText("filter.title")).not.toBeInTheDocument();
    });

    it("renders the Planned By filter section", () => {
      renderModal();
      expect(screen.getByText("filter.planned_by")).toBeInTheDocument();
      expect(
        document.getElementById("campaign-filter-modal-planned-by-filter"),
      ).toBeInTheDocument();
    });

    it("renders the Planned By multiselect with correct id", () => {
      renderModal();
      expect(
        document.getElementById("campaign-filter-modal-planned-by-multiselect"),
      ).toBeInTheDocument();
    });

    it("renders Reset and Apply buttons", () => {
      renderModal();
      expect(
        document.getElementById("campaign-filter-modal-reset-btn"),
      ).toBeInTheDocument();
      expect(
        document.getElementById("campaign-filter-modal-apply-btn"),
      ).toBeInTheDocument();
    });
  });

  // ── API integration ────────────────────────────────────────────────────────

  describe("planned by — API integration", () => {
    it("calls useGetUsersQuery with the active company id", () => {
      renderModal({}, "co-42");
      expect(mockGetUsers).toHaveBeenCalledWith({ company_id: "co-42" });
    });

    it("skips API call when modal is closed", () => {
      renderModal({ isOpen: false });
      expect(mockGetUsers).not.toHaveBeenCalled();
    });

    it("skips API call when there is no active company", () => {
      renderModal({}, null);
      expect(mockGetUsers).not.toHaveBeenCalled();
    });

    it("populates Planned By options from API response", () => {
      renderModal();
      expect(screen.getByText("Alice Smith")).toBeInTheDocument();
      expect(screen.getByText("Bob Jones")).toBeInTheDocument();
    });

    it("shows no Planned By options when API returns empty list", () => {
      mockGetUsers.mockReturnValue({ data: [] });
      renderModal();
      expect(screen.queryByText("Alice Smith")).not.toBeInTheDocument();
    });

    it("shows no Planned By options when API returns undefined", () => {
      mockGetUsers.mockReturnValue(undefined);
      renderModal();
      expect(screen.queryByText("Alice Smith")).not.toBeInTheDocument();
    });
  });

  // ── Selection ──────────────────────────────────────────────────────────────

  describe("planned by — selection", () => {
    it("selects a user and shows their chip", async () => {
      const user = userEvent.setup();
      renderModal();
      await user.click(screen.getByTestId("option-u1"));
      expect(screen.getByTestId("chip-u1")).toBeInTheDocument();
      expect(
        within(screen.getByTestId("chip-u1")).getByText("Alice Smith"),
      ).toBeInTheDocument();
    });

    it("passes selected userName ids to onApply when Apply is clicked", async () => {
      const user = userEvent.setup();
      const onApply = vi.fn();
      renderModal({ onApply });
      await user.click(screen.getByTestId("option-u1"));
      await user.click(
        document.getElementById("campaign-filter-modal-apply-btn")!,
      );
      expect(onApply).toHaveBeenCalledWith(
        expect.objectContaining({ userName: ["u1"] }),
      );
    });

    it("clears selected users when Reset is clicked", async () => {
      const user = userEvent.setup();
      const onApply = vi.fn();
      renderModal({
        onApply,
        initialValues: { ...EMPTY_VALUES, userName: ["u1"] },
      });
      await user.click(
        document.getElementById("campaign-filter-modal-reset-btn")!,
      );
      await user.click(
        document.getElementById("campaign-filter-modal-apply-btn")!,
      );
      expect(onApply).toHaveBeenCalledWith(
        expect.objectContaining({ userName: [] }),
      );
    });

    it("deselects a user when clicked again", async () => {
      const user = userEvent.setup();
      renderModal();
      await user.click(screen.getByTestId("option-u1"));
      expect(screen.getByTestId("chip-u1")).toBeInTheDocument();
      await user.click(screen.getByTestId("option-u1"));
      expect(screen.queryByTestId("chip-u1")).not.toBeInTheDocument();
    });
  });

  // ── Apply / Close behaviour ────────────────────────────────────────────────

  describe("apply and close behaviour", () => {
    it("calls onApply and onClose when Apply is clicked", async () => {
      const user = userEvent.setup();
      const onApply = vi.fn();
      const onClose = vi.fn();
      renderModal({ onApply, onClose });
      await user.click(
        document.getElementById("campaign-filter-modal-apply-btn")!,
      );
      expect(onApply).toHaveBeenCalledTimes(1);
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it("includes all filter fields in the onApply payload", async () => {
      const user = userEvent.setup();
      const onApply = vi.fn();
      renderModal({ onApply });
      await user.click(
        document.getElementById("campaign-filter-modal-apply-btn")!,
      );
      expect(onApply).toHaveBeenCalledWith(
        expect.objectContaining({
          status: expect.any(Array),
          userName: expect.any(Array),
          campaignGoal: expect.any(Array),
        }),
      );
    });

    it("does not call onApply when Reset is clicked", async () => {
      const user = userEvent.setup();
      const onApply = vi.fn();
      renderModal({ onApply });
      await user.click(
        document.getElementById("campaign-filter-modal-reset-btn")!,
      );
      expect(onApply).not.toHaveBeenCalled();
    });
  });

  // ── Period mirrors the slice (initialValues) ─────────────────────────────────

  describe("period — mirrors initialValues", () => {
    const pickerId = "campaign-filter-modal-period-picker";

    it("shows the period from initialValues", () => {
      renderModal({
        initialValues: {
          ...EMPTY_VALUES,
          period: { from: new Date("2026-01-01"), to: new Date("2026-01-31") },
        },
      });
      expect(screen.getByTestId(pickerId)).toHaveAttribute(
        "data-has-value",
        "true",
      );
    });

    it("shows no period when initialValues has none", () => {
      renderModal({ initialValues: EMPTY_VALUES });
      expect(screen.getByTestId(pickerId)).toHaveAttribute(
        "data-has-value",
        "false",
      );
    });

    it("clears the period when filters are cleared externally (page Clear all)", () => {
      const store = createStore("co-1");
      const withPeriod: Partial<FilterValues> = {
        ...EMPTY_VALUES,
        period: { from: new Date("2026-01-01"), to: new Date("2026-01-31") },
      };
      const { rerender } = render(
        <TestWrapper store={store}>
          <CampaignFilterModal
            isOpen
            onClose={vi.fn()}
            onApply={vi.fn()}
            initialValues={withPeriod}
          />
        </TestWrapper>,
      );
      expect(screen.getByTestId(pickerId)).toHaveAttribute(
        "data-has-value",
        "true",
      );

      // Page-level "Clear all" resets the slice → period becomes null
      rerender(
        <TestWrapper store={store}>
          <CampaignFilterModal
            isOpen
            onClose={vi.fn()}
            onApply={vi.fn()}
            initialValues={{ ...EMPTY_VALUES, period: null }}
          />
        </TestWrapper>,
      );
      expect(screen.getByTestId(pickerId)).toHaveAttribute(
        "data-has-value",
        "false",
      );
    });
  });
});
