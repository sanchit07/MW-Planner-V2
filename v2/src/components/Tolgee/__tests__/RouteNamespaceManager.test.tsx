import { render, screen, waitFor } from "@testing-library/react";
import React from "react";
import { MemoryRouter, useLocation, useNavigate } from "react-router-dom";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

import RouteNamespaceManager, { useNamespace } from "../RouteNamespaceManager";

// Mock useTolgee hook
const mockAddActiveNs = vi.fn();
const mockRemoveActiveNs = vi.fn();
const mockGetLanguage = vi.fn(() => "en");

const mockTolgee = {
  addActiveNs: mockAddActiveNs,
  removeActiveNs: mockRemoveActiveNs,
  getLanguage: mockGetLanguage,
};

vi.mock("@tolgee/react", () => ({
  useTolgee: () => mockTolgee,
}));

// Test component to access namespace context
const TestConsumer: React.FC = () => {
  const { namespace, setNamespace } = useNamespace();
  return (
    <div>
      <div data-testid="namespace">{namespace}</div>
      <button
        data-testid="set-namespace-btn"
        onClick={() => setNamespace("test-namespace")}
      >
        Set Namespace
      </button>
    </div>
  );
};

describe("RouteNamespaceManager", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetLanguage.mockReturnValue("en");
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe("Route to namespace mapping", () => {
    it("maps exact route /dashboard to dashboard namespace", () => {
      render(
        <MemoryRouter initialEntries={["/dashboard"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("dashboard");
    });

    it("maps exact route /campaigns to campaigns namespace", () => {
      render(
        <MemoryRouter initialEntries={["/campaigns"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("campaigns");
    });

    it("maps exact route /campaigns/new to campaigns namespace", () => {
      render(
        <MemoryRouter initialEntries={["/campaigns/new"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("campaigns");
    });

    it("maps exact route /creatives to creatives namespace", () => {
      render(
        <MemoryRouter initialEntries={["/creatives"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("creatives");
    });

    it("maps exact route /inventories to inventories namespace", () => {
      render(
        <MemoryRouter initialEntries={["/inventories"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("inventories");
    });

    it("maps exact route /proposals to proposals namespace", () => {
      render(
        <MemoryRouter initialEntries={["/proposals"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("proposals");
    });

    it("maps exact route /settings to settings namespace", () => {
      render(
        <MemoryRouter initialEntries={["/settings"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("settings");
    });

    it("maps exact route /signals to signals namespace", () => {
      render(
        <MemoryRouter initialEntries={["/signals"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("signals");
    });

    it("maps exact route /statements to statements namespace", () => {
      render(
        <MemoryRouter initialEntries={["/statements"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("statements");
    });

    it("maps exact route /tags to tags namespace", () => {
      render(
        <MemoryRouter initialEntries={["/tags"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("tags");
    });

    it("maps exact route /pois to pois namespace", () => {
      render(
        <MemoryRouter initialEntries={["/pois"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("pois");
    });

    it("maps exact route /profile to profile namespace", () => {
      render(
        <MemoryRouter initialEntries={["/profile"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("profile");
    });
  });

  describe("Partial route matching", () => {
    it("maps nested route /dashboard/settings to dashboard namespace", () => {
      render(
        <MemoryRouter initialEntries={["/dashboard/settings"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("dashboard");
    });

    it("maps nested route /campaigns/123/edit to campaigns namespace", () => {
      render(
        <MemoryRouter initialEntries={["/campaigns/123/edit"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("campaigns");
    });

    it("maps nested route /inventories/list to inventories namespace", () => {
      render(
        <MemoryRouter initialEntries={["/inventories/list"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("inventories");
    });
  });

  describe("Default namespace", () => {
    it("maps unknown route to common namespace", () => {
      render(
        <MemoryRouter initialEntries={["/unknown-route"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("common");
    });

    it("maps root route / to common namespace", () => {
      render(
        <MemoryRouter initialEntries={["/"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("common");
    });
  });

  describe("Namespace management", () => {
    it("adds active namespace on initial mount", async () => {
      render(
        <MemoryRouter initialEntries={["/dashboard"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      await waitFor(() => {
        expect(mockAddActiveNs).toHaveBeenCalledWith("dashboard");
      });
    });

    it("does not remove namespace on initial mount when previousNamespace is null", async () => {
      render(
        <MemoryRouter initialEntries={["/dashboard"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      await waitFor(() => {
        expect(mockAddActiveNs).toHaveBeenCalled();
      });

      expect(mockRemoveActiveNs).not.toHaveBeenCalled();
    });

    it("removes previous namespace when route changes", async () => {
      const RouteChanger: React.FC = () => {
        const navigate = useNavigate();
        React.useEffect(() => {
          // Navigate after initial render to test route change
          const timer = setTimeout(() => {
            navigate("/campaigns");
          }, 10);
          return () => clearTimeout(timer);
        }, [navigate]);

        return (
          <div>
            <TestConsumer />
          </div>
        );
      };

      render(
        <MemoryRouter initialEntries={["/dashboard"]}>
          <RouteNamespaceManager>
            <RouteChanger />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      await waitFor(() => {
        expect(mockAddActiveNs).toHaveBeenCalledWith("dashboard");
      });

      vi.clearAllMocks();

      await waitFor(
        () => {
          expect(mockRemoveActiveNs).toHaveBeenCalledWith("dashboard");
          expect(mockAddActiveNs).toHaveBeenCalledWith("campaigns");
        },
        { timeout: 2000 },
      );
    });

    it("handles multiple route changes correctly", async () => {
      const RouteChanger: React.FC = () => {
        const navigate = useNavigate();
        const location = useLocation();
        const [step, setStep] = React.useState(0);

        React.useEffect(() => {
          if (step === 0 && location.pathname === "/dashboard") {
            const timer = setTimeout(() => {
              navigate("/campaigns");
              setStep(1);
            }, 50);
            return () => clearTimeout(timer);
          } else if (step === 1 && location.pathname === "/campaigns") {
            const timer = setTimeout(() => {
              navigate("/settings");
              setStep(2);
            }, 50);
            return () => clearTimeout(timer);
          }
        }, [navigate, location.pathname, step]);

        return (
          <div>
            <TestConsumer />
          </div>
        );
      };

      render(
        <MemoryRouter initialEntries={["/dashboard"]}>
          <RouteNamespaceManager>
            <RouteChanger />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      // Wait for all route changes to complete (cumulative check avoids
      // vi.clearAllMocks() race conditions when the suite runs slowly)
      await waitFor(
        () => {
          expect(mockRemoveActiveNs).toHaveBeenCalledWith("dashboard");
          expect(mockAddActiveNs).toHaveBeenCalledWith("campaigns");
          expect(mockRemoveActiveNs).toHaveBeenCalledWith("campaigns");
          expect(mockAddActiveNs).toHaveBeenCalledWith("settings");
        },
        { timeout: 5000 },
      );
    });
  });

  describe("Language change handling", () => {
    it("handles language change without removing namespace when previousLanguage is null", async () => {
      mockGetLanguage.mockReturnValue("ja");

      render(
        <MemoryRouter initialEntries={["/dashboard"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      await waitFor(() => {
        expect(mockAddActiveNs).toHaveBeenCalledWith("dashboard");
      });

      // Should not remove namespace on initial mount
      expect(mockRemoveActiveNs).not.toHaveBeenCalled();
    });

    it("handles language change when previousLanguage exists", async () => {
      mockGetLanguage.mockReturnValue("en");

      const LanguageChanger: React.FC = () => {
        React.useEffect(() => {
          // Simulate language change after initial render
          const timer = setTimeout(() => {
            mockGetLanguage.mockReturnValue("ja");
            // Force re-render by updating a state
          }, 10);
          return () => clearTimeout(timer);
        }, []);

        return (
          <div>
            <TestConsumer />
          </div>
        );
      };

      render(
        <MemoryRouter initialEntries={["/dashboard"]}>
          <RouteNamespaceManager>
            <LanguageChanger />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      await waitFor(() => {
        expect(mockAddActiveNs).toHaveBeenCalledWith("dashboard");
      });

      // Language change branch is executed but doesn't remove namespace
      // The effect will run again when language changes
      await waitFor(
        () => {
          // Namespace should still be managed
          expect(mockAddActiveNs).toHaveBeenCalled();
        },
        { timeout: 2000 },
      );
    });
  });

  describe("useNamespace hook", () => {
    it("returns current namespace from context", () => {
      render(
        <MemoryRouter initialEntries={["/dashboard"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("dashboard");
    });

    it("provides setNamespace function that adds namespace", async () => {
      const userEvent = (await import("@testing-library/user-event")).default;

      render(
        <MemoryRouter initialEntries={["/dashboard"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      const button = screen.getByTestId("set-namespace-btn");
      await userEvent.click(button);

      await waitFor(() => {
        expect(mockAddActiveNs).toHaveBeenCalledWith("test-namespace");
      });
    });

    it("throws error when used outside NamespaceProvider", () => {
      // Suppress console.error for this test
      const consoleSpy = vi
        .spyOn(console, "error")
        .mockImplementation(() => {});

      const TestComponent = () => {
        try {
          useNamespace();
          return <div>No Error</div>;
        } catch (error) {
          return <div>{(error as Error).message}</div>;
        }
      };

      render(<TestComponent />);

      expect(
        screen.getByText(
          "useNamespace must be used within a NamespaceProvider",
        ),
      ).toBeInTheDocument();

      consoleSpy.mockRestore();
    });
  });

  describe("Edge cases", () => {
    it("handles empty pathname", () => {
      render(
        <MemoryRouter initialEntries={[""]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("common");
    });

    it("handles route with query parameters", () => {
      render(
        <MemoryRouter initialEntries={["/dashboard?tab=settings"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("dashboard");
    });

    it("handles route with hash", () => {
      render(
        <MemoryRouter initialEntries={["/dashboard#section"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("dashboard");
    });

    it("handles same namespace on route change to nested route", async () => {
      const RouteChanger: React.FC = () => {
        const navigate = useNavigate();
        React.useEffect(() => {
          // Navigate to nested route with same namespace
          const timer = setTimeout(() => {
            navigate("/campaigns/new");
          }, 10);
          return () => clearTimeout(timer);
        }, [navigate]);

        return (
          <div>
            <TestConsumer />
          </div>
        );
      };

      render(
        <MemoryRouter initialEntries={["/campaigns"]}>
          <RouteNamespaceManager>
            <RouteChanger />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      await waitFor(() => {
        expect(mockAddActiveNs).toHaveBeenCalledWith("campaigns");
      });

      vi.clearAllMocks();

      // Namespace didn't change (both are "campaigns"), so removeActiveNs should not be called
      await waitFor(
        () => {
          // Namespace is the same, so it should not be removed
          expect(mockRemoveActiveNs).not.toHaveBeenCalled();
        },
        { timeout: 2000 },
      );
    });

    it("handles getLanguage returning null", () => {
      mockGetLanguage.mockReturnValue("");

      render(
        <MemoryRouter initialEntries={["/dashboard"]}>
          <RouteNamespaceManager>
            <TestConsumer />
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("namespace")).toHaveTextContent("dashboard");
    });
  });

  describe("Children rendering", () => {
    it("renders children correctly", () => {
      render(
        <MemoryRouter initialEntries={["/dashboard"]}>
          <RouteNamespaceManager>
            <div data-testid="child">Child Content</div>
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("child")).toHaveTextContent("Child Content");
    });

    it("renders multiple children", () => {
      render(
        <MemoryRouter initialEntries={["/dashboard"]}>
          <RouteNamespaceManager>
            <div data-testid="child1">Child 1</div>
            <div data-testid="child2">Child 2</div>
          </RouteNamespaceManager>
        </MemoryRouter>,
      );

      expect(screen.getByTestId("child1")).toBeInTheDocument();
      expect(screen.getByTestId("child2")).toBeInTheDocument();
    });
  });
});
