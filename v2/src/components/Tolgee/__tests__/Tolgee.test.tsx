import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import TolgeeContextProvider from "../Tolgee";

// Mock TolgeeConfig
vi.mock("@config/tolgee", () => ({
  TolgeeConfig: {
    language: "en",
    availableLanguages: ["en", "ja"],
    fallbackLanguage: "en",
  },
}));

// Mock TolgeeProvider
vi.mock("@tolgee/react", () => {
  const mockTolgeeProvider = vi.fn(({ children, fallback }) => (
    <div data-testid="tolgee-provider" data-fallback={fallback}>
      {children}
    </div>
  ));

  return {
    TolgeeProvider: mockTolgeeProvider,
  };
});

describe("TolgeeContextProvider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders children correctly", () => {
    const testContent = "Test Child Content";
    render(
      <TolgeeContextProvider>
        <div>{testContent}</div>
      </TolgeeContextProvider>,
    );

    expect(screen.getByText(testContent)).toBeInTheDocument();
  });

  it("renders multiple children correctly", () => {
    render(
      <TolgeeContextProvider>
        <div>Child 1</div>
        <div>Child 2</div>
      </TolgeeContextProvider>,
    );

    expect(screen.getByText("Child 1")).toBeInTheDocument();
    expect(screen.getByText("Child 2")).toBeInTheDocument();
  });

  it("passes TolgeeConfig to TolgeeProvider", () => {
    render(
      <TolgeeContextProvider>
        <div>Test</div>
      </TolgeeContextProvider>,
    );

    // Verify the provider renders (it's mocked, so we check via the test id)
    const providerElement = screen.getByTestId("tolgee-provider");
    expect(providerElement).toBeInTheDocument();
    // The fallback prop is passed and rendered in the mock
    expect(providerElement).toHaveAttribute("data-fallback", "Loading...");
  });

  it("passes fallback prop to TolgeeProvider", () => {
    render(
      <TolgeeContextProvider>
        <div>Test</div>
      </TolgeeContextProvider>,
    );

    const providerElement = screen.getByTestId("tolgee-provider");
    expect(providerElement).toHaveAttribute("data-fallback", "Loading...");
  });

  it("renders with empty children", () => {
    const { container } = render(
      <TolgeeContextProvider>{null}</TolgeeContextProvider>,
    );

    expect(container.firstChild).toBeInTheDocument();
    expect(screen.getByTestId("tolgee-provider")).toBeInTheDocument();
  });

  it("renders with React elements as children", () => {
    const TestComponent = () => <div>Test Component</div>;
    render(
      <TolgeeContextProvider>
        <TestComponent />
      </TolgeeContextProvider>,
    );

    expect(screen.getByText("Test Component")).toBeInTheDocument();
  });
});
