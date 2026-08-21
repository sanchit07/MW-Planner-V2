import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import ProfileLoadErrorPage from "../ProfileLoadErrorPage";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

const mockDispatch = vi.fn();
const mockLogout = vi.fn();
vi.mock("../../../store", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../../store")>();
  return {
    ...actual,
    useAppDispatch: () => mockDispatch,
  };
});

vi.mock("@services/auth/authSlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/auth/authSlice")>();
  return {
    ...actual,
    get logout() {
      return mockLogout;
    },
  };
});

function renderProfileLoadErrorPage() {
  return render(
    <MemoryRouter>
      <ProfileLoadErrorPage />
    </MemoryRouter>,
  );
}

describe("ProfileLoadErrorPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockLogout.mockReturnValue({ type: "auth/logout" });
    Object.defineProperty(document, "cookie", {
      writable: true,
      value: "",
      configurable: true,
    });
  });

  describe("rendering", () => {
    it("renders alert region with aria-live assertive", () => {
      renderProfileLoadErrorPage();
      const alert = screen.getByRole("alert");
      expect(alert).toBeInTheDocument();
      expect(alert).toHaveAttribute("aria-live", "assertive");
    });

    it("renders heading and description text", () => {
      renderProfileLoadErrorPage();
      expect(
        screen.getByText("Could not load your profile"),
      ).toBeInTheDocument();
      expect(
        screen.getByText(
          /We could not load your user profile. This may be due to a temporary issue or a session problem./i,
        ),
      ).toBeInTheDocument();
    });

    it("renders Back to login button", () => {
      renderProfileLoadErrorPage();
      expect(
        screen.getByRole("button", { name: /back to login/i }),
      ).toBeInTheDocument();
    });

    it("renders Close application button", () => {
      renderProfileLoadErrorPage();
      expect(
        screen.getByRole("button", { name: /close application/i }),
      ).toBeInTheDocument();
    });
  });

  describe("Back to login", () => {
    it("dispatches logout and navigates to /login when Back to login is clicked", async () => {
      const user = userEvent.setup();
      mockLogout.mockReturnValue({ type: "auth/logout" });
      renderProfileLoadErrorPage();
      await user.click(screen.getByRole("button", { name: /back to login/i }));
      expect(mockLogout).toHaveBeenCalled();
      expect(mockDispatch).toHaveBeenCalledWith({ type: "auth/logout" });
      expect(mockNavigate).toHaveBeenCalledWith("/login", { replace: true });
    });

    it("navigates to /login with replace when Back to login is clicked", async () => {
      const user = userEvent.setup();
      mockLogout.mockReturnValue({ type: "auth/logout" });
      renderProfileLoadErrorPage();
      await user.click(screen.getByRole("button", { name: /back to login/i }));
      expect(mockNavigate).toHaveBeenCalledWith("/login", { replace: true });
    });
  });

  describe("Close application", () => {
    it("dispatches logout and calls window.close when Close application is clicked", async () => {
      const user = userEvent.setup();
      mockLogout.mockReturnValue({ type: "auth/logout" });
      const closeSpy = vi.spyOn(window, "close").mockImplementation(() => {});
      renderProfileLoadErrorPage();
      await user.click(
        screen.getByRole("button", { name: /close application/i }),
      );
      expect(mockLogout).toHaveBeenCalled();
      expect(mockDispatch).toHaveBeenCalledWith({ type: "auth/logout" });
      expect(closeSpy).toHaveBeenCalled();
      closeSpy.mockRestore();
    });
  });

  describe("clearAuthAndCookies", () => {
    it("dispatches when Back to login is clicked", async () => {
      const user = userEvent.setup();
      mockLogout.mockReturnValue({ type: "auth/logout" });
      renderProfileLoadErrorPage();
      await user.click(screen.getByRole("button", { name: /back to login/i }));
      expect(mockDispatch).toHaveBeenCalled();
    });
  });
});
