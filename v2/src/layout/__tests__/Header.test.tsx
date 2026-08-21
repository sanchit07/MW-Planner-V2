import { configureStore } from "@reduxjs/toolkit";
import authSlice from "@services/auth/authSlice";
import campaignsUISlice, {
  setSearchQuery,
  setSelectedItems,
} from "@services/campaign/campaignsUISlice";
import sidebarSlice from "@services/sidebar-toggle/sidebar-toggle.slice";
import stepperSlice, {
  setInventoryFilters,
} from "@services/stepper/stepperSlice";
import userSlice, { UserProfile } from "@services/user/userSlice";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { Header } from "../Header";

const mockShowSuccess = vi.fn();
vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({ showSuccess: mockShowSuccess }),
}));

const mockUserLogout = vi.fn();
vi.mock("@services/auth/authSlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/auth/authSlice")>();
  return {
    ...actual,
    useLazyLogoutQuery: () => [mockUserLogout, {}],
  };
});

vi.mock("../LanguageSwitcher", () => ({
  __esModule: true,
  default: () => <div data-testid="language-switcher">LanguageSwitcher</div>,
}));

vi.mock("../ProductSwitcher", () => ({
  __esModule: true,
  default: () => <div data-testid="product-switcher">ProductSwitcher</div>,
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const mockUseGetChildCompaniesQuery = vi.hoisted(() =>
  vi.fn().mockReturnValue({ data: undefined, isFetching: false }),
);
vi.mock("@services/agency/agencySlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/agency/agencySlice")>();
  return {
    ...actual,
    useGetChildCompaniesQuery: mockUseGetChildCompaniesQuery,
  };
});

const createMockProfile = (overrides: Partial<UserProfile> = {}): UserProfile =>
  ({
    id: "user-1",
    user_id: "user-1",
    sub: "sub-1",
    email: "user@test.com",
    username: "user",
    first_name: "Jane",
    last_name: "Doe",
    email_verified: true,
    phone_verified: false,
    is_global_admin: false,
    has_system_role: false,
    system_permissions: null,
    permissions: [],
    memberships: [
      {
        company_id: "co-1",
        company_name: "Company One",
        role_id: "r1",
        role_name: "Admin",
        is_active: true,
        subscriptions: [],
      },
    ],
    current_company: {
      id: "co-1",
      name: "Company One",
      role_name: "Admin",
      seat_id: 1,
      role_id: "r1",
      company_type: {
        id: "ct-1",
        name: "Agency",
        code: "AG",
        is_supplier_side: false,
        is_demand_side: true,
      },
    },
    activeCompanyId: "co-1",
    avatar: null,
    locale: "en",
    activated: true,
    ...overrides,
  }) as UserProfile;

const createStore = (options: {
  isSidebarCollapsed?: boolean;
  profile?: UserProfile | null;
  refreshToken?: string | null;
}) => {
  const {
    isSidebarCollapsed = false,
    profile = null,
    refreshToken = null,
  } = options;
  return configureStore({
    reducer: {
      sidebar: sidebarSlice,
      profile: userSlice,
      auth: authSlice,
      campaignsUI: campaignsUISlice,
      stepper: stepperSlice,
    },
    preloadedState: {
      sidebar: { isSidebarCollapsed },
      profile: { profile },
      auth: {
        isAuthenticated: !!profile,
        token: profile ? "token" : null,
        refreshToken,
      },
    },
  });
};

const TestWrapper = ({
  children,
  store,
}: {
  children: React.ReactNode;
  store: ReturnType<typeof createStore>;
}) => <Provider store={store}>{children}</Provider>;

describe("Header", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUserLogout.mockResolvedValue({
      data: { success: true, data: "Logged out successfully" },
    });
    mockUseGetChildCompaniesQuery.mockReturnValue({
      data: undefined,
      isFetching: false,
    });
  });

  describe("rendering", () => {
    it("renders header with id app-header", () => {
      const store = createStore({});
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      expect(document.getElementById("app-header")).toBeInTheDocument();
    });

    it("renders logo and Planner text", () => {
      const store = createStore({});
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      expect(document.getElementById("header-logo")).toBeInTheDocument();
      expect(screen.getByText("header.planner")).toBeInTheDocument();
    });

    it("renders ProductSwitcher and LanguageSwitcher", () => {
      const store = createStore({});
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      expect(screen.getByTestId("product-switcher")).toBeInTheDocument();
      expect(screen.getByTestId("language-switcher")).toBeInTheDocument();
    });

    it("shows Guest when no profile", () => {
      const store = createStore({ profile: null });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      expect(screen.getByText("header.guest")).toBeInTheDocument();
    });

    it("shows user full name when profile exists", () => {
      const store = createStore({
        profile: createMockProfile({ first_name: "Jane", last_name: "Doe" }),
      });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      expect(screen.getByText("Jane Doe")).toBeInTheDocument();
    });

    it("shows initials in avatar when user has first and last name", () => {
      const store = createStore({
        profile: createMockProfile({ first_name: "Jane", last_name: "Doe" }),
      });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      const avatar = document.getElementById("user-avatar");
      expect(avatar).toBeInTheDocument();
      expect(within(avatar!).getByText("JD")).toBeInTheDocument();
    });

    it("shows G when user has no first or last name", () => {
      const store = createStore({
        profile: createMockProfile({
          first_name: "",
          last_name: "",
        }),
      });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      const avatar = document.getElementById("user-avatar");
      expect(within(avatar!).getByText("G")).toBeInTheDocument();
    });
  });

  describe("sidebar toggle", () => {
    it("renders PanelLeftOpen when sidebar is collapsed", () => {
      const store = createStore({ isSidebarCollapsed: true });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      expect(
        document.getElementById("sidebar-toggle-open"),
      ).toBeInTheDocument();
      expect(
        document.getElementById("sidebar-toggle-close"),
      ).not.toBeInTheDocument();
    });

    it("renders PanelLeftClose when sidebar is expanded", () => {
      const store = createStore({ isSidebarCollapsed: false });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      expect(
        document.getElementById("sidebar-toggle-close"),
      ).toBeInTheDocument();
      expect(
        document.getElementById("sidebar-toggle-open"),
      ).not.toBeInTheDocument();
    });

    it("dispatches toggle when toggle button is clicked", async () => {
      const user = userEvent.setup();
      const store = createStore({ isSidebarCollapsed: false });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      const toggleBtn = document.getElementById("sidebar-toggle-close");
      await user.click(toggleBtn!);
      expect(store.getState().sidebar.isSidebarCollapsed).toBe(true);
    });
  });

  describe("logout", () => {
    it("calls logout API and showSuccess when logout succeeds", async () => {
      const user = userEvent.setup();
      const store = createStore({
        profile: createMockProfile(),
        refreshToken: "refresh-token",
      });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      const userAvatar = document.getElementById("user-avatar");
      await user.click(userAvatar!);
      const logoutBtn = await screen.findByRole("button", {
        name: /header\.logout/i,
      });
      await user.click(logoutBtn);
      await expect(mockUserLogout).toHaveBeenCalledWith({
        refresh_token: "refresh-token",
      });
      expect(mockShowSuccess).toHaveBeenCalledWith("Logged out successfully");
    });

    it("does not call showSuccess when logout response has no success", async () => {
      const user = userEvent.setup();
      mockUserLogout.mockResolvedValueOnce({ data: { success: false } });
      const store = createStore({
        profile: createMockProfile(),
        refreshToken: "refresh-token",
      });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      const userAvatar = document.getElementById("user-avatar");
      await user.click(userAvatar!);
      const logoutBtn = await screen.findByRole("button", {
        name: /header\.logout/i,
      });
      await user.click(logoutBtn);
      expect(mockShowSuccess).not.toHaveBeenCalled();
    });
  });

  describe("company selector", () => {
    it("shows selected company name when user menu is open", async () => {
      const user = userEvent.setup();
      const store = createStore({ profile: createMockProfile() });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      const userAvatar = document.getElementById("user-avatar");
      await user.click(userAvatar!);
      expect(
        await screen.findByText("Company One", {}, { timeout: 2000 }),
      ).toBeInTheDocument();
    });

    it("opens company list when company selector trigger is clicked", async () => {
      const user = userEvent.setup();
      const store = createStore({
        profile: createMockProfile({
          memberships: [
            {
              company_id: "co-2",
              company_name: "Company Two",
              role_id: "r2",
              role_name: "Viewer",
              is_active: true,
              subscriptions: [],
            },
          ],
        }),
      });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      await user.click(document.getElementById("user-avatar")!);
      const trigger = await screen.findByRole("button", {
        name: /company one/i,
      });
      await user.click(trigger);
      expect(await screen.findByText("Company Two")).toBeInTheDocument();
    });

    it("switches selected company and updates activeCompanyId in store", async () => {
      const user = userEvent.setup();
      const store = createStore({
        profile: createMockProfile({
          memberships: [
            {
              company_id: "co-2",
              company_name: "Company Two",
              role_id: "r2",
              role_name: "Viewer",
              is_active: true,
              subscriptions: [],
            },
          ],
        }),
      });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      await user.click(document.getElementById("user-avatar")!);
      const trigger = await screen.findByRole("button", {
        name: /company one/i,
      });
      await user.click(trigger);
      const companyTwoBtn = await screen.findByRole("button", {
        name: "Company Two",
      });
      await user.click(companyTwoBtn);
      expect(store.getState().profile.profile?.activeCompanyId).toBe("co-2");
    });

    it("resets campaign list filters, selection, and inventory filters when switching companies", async () => {
      const user = userEvent.setup();
      const store = createStore({
        profile: createMockProfile({
          memberships: [
            {
              company_id: "co-2",
              company_name: "Company Two",
              role_id: "r2",
              role_name: "Viewer",
              is_active: true,
              subscriptions: [],
            },
          ],
        }),
      });
      store.dispatch(setSearchQuery("some search"));
      store.dispatch(setSelectedItems(["campaign-1"]));
      store.dispatch(
        setInventoryFilters({
          mediaOwners: ["owner-1"],
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
        }),
      );

      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      await user.click(document.getElementById("user-avatar")!);
      const trigger = await screen.findByRole("button", {
        name: /company one/i,
      });
      await user.click(trigger);
      const companyTwoBtn = await screen.findByRole("button", {
        name: "Company Two",
      });
      await user.click(companyTwoBtn);

      expect(store.getState().campaignsUI.searchQuery).toBe("");
      expect(store.getState().campaignsUI.selectedItems).toEqual([]);
      expect(store.getState().stepper.inventoryFilters.mediaOwners).toEqual([]);
    });

    it("closes company list after selecting a company", async () => {
      const user = userEvent.setup();
      const store = createStore({
        profile: createMockProfile({
          memberships: [
            {
              company_id: "co-2",
              company_name: "Company Two",
              role_id: "r2",
              role_name: "Viewer",
              is_active: true,
              subscriptions: [],
            },
          ],
        }),
      });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      await user.click(document.getElementById("user-avatar")!);
      const trigger = await screen.findByRole("button", {
        name: /company one/i,
      });
      await user.click(trigger);
      const companyTwoBtn = await screen.findByRole("button", {
        name: "Company Two",
      });
      await user.click(companyTwoBtn);
      // After selection, the list closes. The trigger now shows "Company Two" but the list item should be gone.
      // Only 1 button named "Company Two" should exist (the trigger), not 2 (trigger + list item).
      expect(
        screen.getAllByRole("button", { name: "Company Two" }),
      ).toHaveLength(1);
    });

    it("does not duplicate current_company if it exists in memberships", async () => {
      const user = userEvent.setup();
      const store = createStore({ profile: createMockProfile() });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      await user.click(document.getElementById("user-avatar")!);
      const trigger = await screen.findByRole("button", {
        name: /company one/i,
      });
      await user.click(trigger);
      const items = screen.getAllByText("Company One");
      // One in the trigger + one in the list = 2. Dedup means it should NOT appear twice in the list (which would be 3).
      expect(items.length).toBe(2);
    });

    it("fetches child companies from the /children API using the login company id", () => {
      const store = createStore({ profile: createMockProfile() });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      expect(mockUseGetChildCompaniesQuery).toHaveBeenCalledWith(
        { id: "co-1" },
        { skip: false },
      );
    });

    it("skips the /children fetch when there is no logged-in company", () => {
      const store = createStore({ profile: null });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      expect(mockUseGetChildCompaniesQuery).toHaveBeenCalledWith(
        { id: undefined },
        { skip: true },
      );
    });

    it("shows child companies in dropdown", async () => {
      const user = userEvent.setup();
      const store = createStore({
        profile: createMockProfile({
          memberships: [],
          current_company: {
            id: "co-1",
            name: "Company One",
            role_name: "Admin",
            seat_id: 1,
            role_id: "r1",
            company_type: {
              id: "ct-1",
              name: "Media Owner",
              code: "MEDIA_OWNER",
              is_supplier_side: true,
              is_demand_side: false,
            },
          },
        }),
      });
      mockUseGetChildCompaniesQuery.mockReturnValue({
        data: {
          children: [
            {
              company: {
                id: "child-1",
                name: "Child Company A",
                company_type: {
                  id: "ct-1",
                  name: "Media Owner",
                  code: "MEDIA_OWNER",
                  is_supplier_side: true,
                  is_demand_side: false,
                },
              },
              access_level: "none",
              linked_at: "",
              allow_reporting_access: false,
              allow_billing_view: false,
              allow_inventory_management: false,
            },
          ],
          count: 1,
        },
        isFetching: false,
      });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      await user.click(document.getElementById("user-avatar")!);
      const trigger = await screen.findByRole("button", {
        name: /company one/i,
      });
      await user.click(trigger);
      expect(await screen.findByText("Child Company A")).toBeInTheDocument();
    });

    it("ignores child company entries where company is null instead of throwing", async () => {
      const user = userEvent.setup();
      const store = createStore({
        profile: createMockProfile({
          memberships: [],
          current_company: {
            id: "co-1",
            name: "Company One",
            role_name: "Admin",
            seat_id: 1,
            role_id: "r1",
            company_type: {
              id: "ct-1",
              name: "Media Owner",
              code: "MEDIA_OWNER",
              is_supplier_side: true,
              is_demand_side: false,
            },
          },
        }),
      });
      mockUseGetChildCompaniesQuery.mockReturnValue({
        data: {
          children: [
            {
              company: null,
              access_level: "none",
              linked_at: "",
              allow_reporting_access: false,
              allow_billing_view: false,
              allow_inventory_management: false,
            },
            {
              company: {
                id: "child-1",
                name: "Child Company A",
                company_type: {
                  id: "ct-1",
                  name: "Media Owner",
                  code: "MEDIA_OWNER",
                  is_supplier_side: true,
                  is_demand_side: false,
                },
              },
              access_level: "none",
              linked_at: "",
              allow_reporting_access: false,
              allow_billing_view: false,
              allow_inventory_management: false,
            },
          ],
          count: 2,
        },
        isFetching: false,
      });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      await user.click(document.getElementById("user-avatar")!);
      const trigger = await screen.findByRole("button", {
        name: /company one/i,
      });
      await user.click(trigger);
      expect(await screen.findByText("Child Company A")).toBeInTheDocument();
    });

    it("selects a child company and updates activeCompanyId in store", async () => {
      const user = userEvent.setup();
      const store = createStore({
        profile: createMockProfile({
          memberships: [],
          current_company: {
            id: "co-1",
            name: "Company One",
            role_name: "Admin",
            seat_id: 1,
            role_id: "r1",
            company_type: {
              id: "ct-1",
              name: "Media Owner",
              code: "MEDIA_OWNER",
              is_supplier_side: true,
              is_demand_side: false,
            },
          },
        }),
      });
      mockUseGetChildCompaniesQuery.mockReturnValue({
        data: {
          children: [
            {
              company: {
                id: "child-1",
                name: "Child Company A",
                company_type: {
                  id: "ct-1",
                  name: "Media Owner",
                  code: "MEDIA_OWNER",
                  is_supplier_side: true,
                  is_demand_side: false,
                },
              },
              access_level: "none",
              linked_at: "",
              allow_reporting_access: false,
              allow_billing_view: false,
              allow_inventory_management: false,
            },
          ],
          count: 1,
        },
        isFetching: false,
      });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      await user.click(document.getElementById("user-avatar")!);
      const trigger = await screen.findByRole("button", {
        name: /company one/i,
      });
      await user.click(trigger);
      const childBtn = await screen.findByRole("button", {
        name: /Child Company A/,
      });
      await user.click(childBtn);
      expect(store.getState().profile.profile?.activeCompanyId).toBe("child-1");
    });

    it("does not duplicate child company if it also appears in memberships", async () => {
      const user = userEvent.setup();
      const store = createStore({
        profile: createMockProfile({
          memberships: [
            {
              company_id: "child-1",
              company_name: "Child Company A",
              role_id: "r2",
              role_name: "Viewer",
              is_active: true,
              subscriptions: [],
            },
          ],
          current_company: {
            id: "co-1",
            name: "Company One",
            role_name: "Admin",
            seat_id: 1,
            role_id: "r1",
            company_type: {
              id: "ct-1",
              name: "Media Owner",
              code: "MEDIA_OWNER",
              is_supplier_side: true,
              is_demand_side: false,
            },
          },
        }),
      });
      mockUseGetChildCompaniesQuery.mockReturnValue({
        data: {
          children: [
            {
              company: {
                id: "child-1",
                name: "Child Company A",
                company_type: {
                  id: "ct-1",
                  name: "Media Owner",
                  code: "MEDIA_OWNER",
                  is_supplier_side: true,
                  is_demand_side: false,
                },
              },
              access_level: "none",
              linked_at: "",
              allow_reporting_access: false,
              allow_billing_view: false,
              allow_inventory_management: false,
            },
          ],
          count: 1,
        },
        isFetching: false,
      });
      render(
        <TestWrapper store={store}>
          <Header />
        </TestWrapper>,
      );
      await user.click(document.getElementById("user-avatar")!);
      const trigger = await screen.findByRole("button", {
        name: /company one/i,
      });
      await user.click(trigger);
      // "Child Company A" should appear only once in the list, not twice (from both membership and childCompanies)
      expect(screen.getAllByText("Child Company A")).toHaveLength(1);
    });
  });
});
