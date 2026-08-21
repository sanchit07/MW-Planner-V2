import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import ProfilePage from "../ProfilePage";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const mockUnwrap = vi.fn().mockResolvedValue({});
const mockSetPassword = vi.fn().mockReturnValue({ unwrap: mockUnwrap });
vi.mock("@services/account/accountApi", () => ({
  useSetPasswordMutation: () => [mockSetPassword],
}));

const mockUser = {
  id: "user-1",
  user_id: "user-1",
  sub: "sub-1",
  first_name: "Jane",
  last_name: "Smith",
  email: "jane@example.com",
  username: "janesmith",
  phone: "+1234567890",
  avatar: null as string | null,
  activated: true,
  locale: "en",
  email_verified: true,
  phone_verified: false,
  is_global_admin: false,
  has_system_role: false,
  system_permissions: null,
  permissions: null,
  memberships: [],
  current_company: {
    id: "co-1",
    name: "Acme Corp",
    role_id: "role-1",
    role_name: "Admin",
    seat_id: 1,
    company_type: {
      id: "ct-1",
      code: "AGENCY",
      name: "Agency",
      is_demand_side: true,
      is_supplier_side: false,
    },
  },
  updatedAt: "2024-03-15T10:00:00Z",
};

vi.mock("@store", () => ({
  useAppSelector: (selector: (state: unknown) => unknown) =>
    selector({
      profile: { profile: mockUser },
      auth: { token: "test-token-abc" },
    }),
}));

function renderPage() {
  return render(
    <MemoryRouter>
      <ProfilePage />
    </MemoryRouter>,
  );
}

async function switchToAccountTab() {
  const user = userEvent.setup();
  renderPage();
  await user.click(
    screen.getByRole("button", { name: "profile.tabs.account" }),
  );
  return user;
}

async function openPasswordDrawer() {
  const user = await switchToAccountTab();
  await user.click(
    screen.getByRole("button", {
      name: /profile\.tabs\.changePassword/i,
    }),
  );
  return user;
}

describe("ProfilePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUnwrap.mockResolvedValue({});
    vi.stubGlobal("requestAnimationFrame", (cb: FrameRequestCallback) => {
      cb(performance.now());
      return 0;
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.body.style.overflow = "";
  });

  describe("Page structure", () => {
    it("renders page heading", () => {
      renderPage();
      expect(screen.getByRole("heading", { level: 1 })).toBeInTheDocument();
    });

    it("renders Profile and Account tab buttons", () => {
      renderPage();
      expect(
        screen.getByRole("button", { name: "profile.tabs.profile" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: "profile.tabs.account" }),
      ).toBeInTheDocument();
    });

    it("shows profile content by default (Profile tab active)", () => {
      renderPage();
      // Personal info card only visible when Profile tab is active
      expect(
        screen.getByText("profile.personalInfo.title"),
      ).toBeInTheDocument();
    });
  });

  describe("Profile tab — user data", () => {
    it("displays full name from first_name + last_name", () => {
      renderPage();
      expect(screen.getByText("Jane Smith")).toBeInTheDocument();
    });

    it("displays email", () => {
      renderPage();
      expect(screen.getByText("jane@example.com")).toBeInTheDocument();
    });

    it("displays phone number", () => {
      renderPage();
      expect(screen.getByText("+1234567890")).toBeInTheDocument();
    });

    it("displays role from current_company.role_name", () => {
      renderPage();
      expect(screen.getByText("Admin")).toBeInTheDocument();
    });

    it("displays company from current_company.name", () => {
      renderPage();
      expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    });

    it("shows initials when avatar is null", () => {
      renderPage();
      expect(screen.getByText("JS")).toBeInTheDocument();
    });

    it("does not render img element when avatar is null", () => {
      renderPage();
      expect(screen.queryByRole("img")).not.toBeInTheDocument();
    });

    it("shows em-dash for missing phone number", () => {
      // Temporarily override mock for this test
      const original = mockUser.phone;
      // @ts-expect-error override for test
      mockUser.phone = undefined;

      renderPage();
      const dashes = screen.getAllByText("—");
      expect(dashes.length).toBeGreaterThan(0);

      mockUser.phone = original;
    });
  });

  describe("Account tab", () => {
    it("shows account security content after switching tabs", async () => {
      await switchToAccountTab();
      expect(
        screen.getByText("profile.tabs.accountSecurity"),
      ).toBeInTheDocument();
    });

    it("shows Active badge", async () => {
      await switchToAccountTab();
      expect(screen.getByText("profile.tabs.active")).toBeInTheDocument();
    });

    it("shows updatedAt date when available", async () => {
      await switchToAccountTab();
      const expectedDate = new Date(
        "2024-03-15T10:00:00Z",
      ).toLocaleDateString();
      expect(
        screen.getByText((content) => content.includes(expectedDate)),
      ).toBeInTheDocument();
    });

    it("shows Change Password button", async () => {
      await switchToAccountTab();
      expect(
        screen.getByRole("button", {
          name: /profile\.tabs\.changePassword/i,
        }),
      ).toBeInTheDocument();
    });

    it("hides profile content after switching to account tab", async () => {
      await switchToAccountTab();
      expect(
        screen.queryByText("profile.personalInfo.title"),
      ).not.toBeInTheDocument();
    });
  });

  describe("Change Password drawer", () => {
    it("opens drawer when Change Password button is clicked", async () => {
      await openPasswordDrawer();
      await waitFor(() => {
        expect(
          screen.getByText("profile.changePassword.requirements"),
        ).toBeInTheDocument();
      });
    });

    it("shows new password field", async () => {
      await openPasswordDrawer();
      await waitFor(() => {
        expect(
          screen.getByText("profile.tabs.newPassword"),
        ).toBeInTheDocument();
      });
    });

    it("shows confirm password field", async () => {
      await openPasswordDrawer();
      await waitFor(() => {
        expect(
          screen.getByText("profile.tabs.confirmPassword"),
        ).toBeInTheDocument();
      });
    });

    it("shows all 5 requirement items", async () => {
      await openPasswordDrawer();
      await waitFor(() => {
        expect(
          screen.getByText("profile.changePassword.req.length"),
        ).toBeInTheDocument();
        expect(
          screen.getByText("profile.changePassword.req.uppercase"),
        ).toBeInTheDocument();
        expect(
          screen.getByText("profile.changePassword.req.lowercase"),
        ).toBeInTheDocument();
        expect(
          screen.getByText("profile.changePassword.req.number"),
        ).toBeInTheDocument();
        expect(
          screen.getByText("profile.changePassword.req.match"),
        ).toBeInTheDocument();
      });
    });

    it("shows strength bar segments when new password is typed", async () => {
      const user = await openPasswordDrawer();

      await waitFor(() => {
        expect(
          document.querySelector('input[type="password"]'),
        ).toBeInTheDocument();
      });

      const inputs = document.querySelectorAll('input[type="password"]');
      await user.type(inputs[0] as HTMLElement, "Abc1");

      await waitFor(() => {
        const segments = document.querySelectorAll(".h-1.flex-1.rounded-full");
        expect(segments.length).toBe(4);
      });
    });

    it("shows two password input fields", async () => {
      await openPasswordDrawer();
      await waitFor(() => {
        const inputs = document.querySelectorAll('input[type="password"]');
        expect(inputs.length).toBe(2);
      });
    });

    it("closes drawer when X button is clicked", async () => {
      await openPasswordDrawer();

      await waitFor(() => {
        expect(
          screen.getByText("profile.changePassword.requirements"),
        ).toBeInTheDocument();
      });

      const closeBtn = document.querySelector(
        '[id="change-password-drawer-close"]',
      );
      expect(closeBtn).toBeInTheDocument();
      await userEvent.click(closeBtn!);

      await waitFor(() => {
        expect(
          screen.queryByText("profile.changePassword.requirements"),
        ).not.toBeInTheDocument();
      });
    });

    it("closes drawer when backdrop is clicked", async () => {
      await openPasswordDrawer();

      await waitFor(() => {
        expect(
          screen.getByText("profile.changePassword.requirements"),
        ).toBeInTheDocument();
      });

      const backdrop = document.querySelector(
        '[id="change-password-drawer-backdrop"]',
      );
      expect(backdrop).toBeInTheDocument();
      await userEvent.click(backdrop!);

      await waitFor(() => {
        expect(
          screen.queryByText("profile.changePassword.requirements"),
        ).not.toBeInTheDocument();
      });
    });
  });

  describe("Change Password form submission", () => {
    it("calls setPassword with correct payload on valid submit", async () => {
      const user = await openPasswordDrawer();

      await waitFor(() => {
        expect(
          document.querySelector('input[type="password"]'),
        ).toBeInTheDocument();
      });

      const inputs = document.querySelectorAll('input[type="password"]');
      await user.type(inputs[0] as HTMLElement, "NewSecure1Pass");
      await user.type(inputs[1] as HTMLElement, "NewSecure1Pass");

      await user.click(
        screen.getByRole("button", { name: "profile.tabs.updatePassword" }),
      );

      await waitFor(() => {
        expect(mockSetPassword).toHaveBeenCalledWith({
          id: "user-1",
          password: "NewSecure1Pass",
        });
      });
    });

    it("does not call setPassword when passwords do not match", async () => {
      const user = await openPasswordDrawer();

      await waitFor(() => {
        expect(
          document.querySelector('input[type="password"]'),
        ).toBeInTheDocument();
      });

      const inputs = document.querySelectorAll('input[type="password"]');
      await user.type(inputs[0] as HTMLElement, "NewSecure1Pass");
      await user.type(inputs[1] as HTMLElement, "DifferentPass1");

      await user.click(
        screen.getByRole("button", { name: "profile.tabs.updatePassword" }),
      );

      await waitFor(() => {
        expect(mockSetPassword).not.toHaveBeenCalled();
      });
    });

    it("closes drawer after successful password change", async () => {
      const user = await openPasswordDrawer();

      await waitFor(() => {
        expect(
          document.querySelector('input[type="password"]'),
        ).toBeInTheDocument();
      });

      const inputs = document.querySelectorAll('input[type="password"]');
      await user.type(inputs[0] as HTMLElement, "NewSecure1Pass");
      await user.type(inputs[1] as HTMLElement, "NewSecure1Pass");

      await user.click(
        screen.getByRole("button", { name: "profile.tabs.updatePassword" }),
      );

      await waitFor(() => {
        expect(
          screen.queryByText("profile.changePassword.requirements"),
        ).not.toBeInTheDocument();
      });
    });
  });
});
