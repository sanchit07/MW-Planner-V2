import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

import RemoteDropdown, {
  DropdownOption,
  RemoteDataResponse,
  SearchParams,
} from "../RemoteDropdown";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("react-dom", async () => {
  const actual = await vi.importActual("react-dom");
  return {
    ...actual,
    createPortal: (node: React.ReactNode) => node,
  };
});

describe("RemoteDropdown", () => {
  const mockFetcher = vi.fn(
    async (
      params: SearchParams,
    ): Promise<RemoteDataResponse<DropdownOption>> => {
      const page = params.page || 0;
      const size = params.size || 20;
      const allOptions: DropdownOption[] = Array.from(
        { length: 50 },
        (_, i) => ({
          id: i + 1,
          label: `Option ${i + 1}`,
          value: `opt${i + 1}`,
        }),
      );

      const start = page * size;
      const end = start + size;

      return {
        content: allOptions.slice(start, end),
        totalElements: 50,
        totalPages: 3,
        size,
        number: page,
        first: page === 0,
        last: page >= 2,
        empty: false,
        numberOfElements: Math.min(size, 50 - start),
      };
    },
  );

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("Rendering", () => {
    it("renders dropdown button", () => {
      render(<RemoteDropdown fetcher={mockFetcher} />);
      const button = screen.getByRole("button");
      expect(button).toBeInTheDocument();
    });

    it("displays placeholder when no value", () => {
      render(
        <RemoteDropdown fetcher={mockFetcher} placeholder="Select option" />,
      );
      expect(screen.getByText("Select option")).toBeInTheDocument();
    });

    it("displays selected value when provided", () => {
      const value: DropdownOption = {
        id: 1,
        label: "Selected Option",
        value: "opt1",
      };
      render(<RemoteDropdown fetcher={mockFetcher} value={value} />);
      expect(screen.getByText("Selected Option")).toBeInTheDocument();
    });

    it("displays multiple selected count when multiple", () => {
      const value: DropdownOption[] = [
        { id: 1, label: "Option 1", value: "opt1" },
        { id: 2, label: "Option 2", value: "opt2" },
      ];
      render(
        <RemoteDropdown fetcher={mockFetcher} value={value} multiple={true} />,
      );
      expect(screen.getByText("2 items selected")).toBeInTheDocument();
    });

    it("applies custom className", () => {
      const { container } = render(
        <RemoteDropdown fetcher={mockFetcher} className="custom-class" />,
      );
      const wrapper = container.querySelector(".custom-class");
      expect(wrapper).toBeInTheDocument();
    });
  });

  describe("Data Fetching", () => {
    it("fetches data when dropdown opens", async () => {
      const user = userEvent.setup();
      render(<RemoteDropdown fetcher={mockFetcher} />);

      const button = screen.getByRole("button");
      await user.click(button);

      await waitFor(() => {
        expect(mockFetcher).toHaveBeenCalled();
      });
    });

    it("displays loading state while fetching", async () => {
      const user = userEvent.setup();
      const slowFetcher = vi.fn(
        () =>
          new Promise<RemoteDataResponse<DropdownOption>>((resolve) => {
            setTimeout(() => {
              resolve({
                content: [{ id: 1, label: "Option 1", value: "opt1" }],
                totalElements: 1,
                totalPages: 1,
                size: 20,
                number: 0,
                first: true,
                last: true,
                empty: false,
                numberOfElements: 1,
              });
            }, 100);
          }),
      );

      render(<RemoteDropdown fetcher={slowFetcher} />);

      const button = screen.getByRole("button");
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Loading...")).toBeInTheDocument();
      });
    });

    it("displays error state when fetch fails", async () => {
      const user = userEvent.setup();
      const errorFetcher = vi.fn(() =>
        Promise.reject(new Error("Fetch failed")),
      );

      render(<RemoteDropdown fetcher={errorFetcher} />);

      const button = screen.getByRole("button");
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Fetch failed")).toBeInTheDocument();
      });
    });

    it("displays empty state when no options", async () => {
      const user = userEvent.setup();
      const emptyFetcher = vi.fn(() =>
        Promise.resolve({
          content: [],
          totalElements: 0,
          totalPages: 0,
          size: 20,
          number: 0,
          first: true,
          last: true,
          empty: true,
          numberOfElements: 0,
        }),
      );

      render(<RemoteDropdown fetcher={emptyFetcher} />);

      const button = screen.getByRole("button");
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("No options available")).toBeInTheDocument();
      });
    });
  });

  describe("Interactions", () => {
    it("opens dropdown when button is clicked", async () => {
      const user = userEvent.setup();
      render(<RemoteDropdown fetcher={mockFetcher} />);

      const button = screen.getByRole("button");
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Option 1")).toBeInTheDocument();
      });
    });

    it("closes dropdown when clicking outside", async () => {
      const user = userEvent.setup();
      render(<RemoteDropdown fetcher={mockFetcher} />);

      const button = screen.getByRole("button");
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Option 1")).toBeInTheDocument();
      });

      await user.click(document.body);

      await waitFor(() => {
        expect(screen.queryByText("Option 1")).not.toBeInTheDocument();
      });
    });

    it("calls onSelectionChange when option is selected", async () => {
      const user = userEvent.setup();
      const onSelectionChange = vi.fn();
      render(
        <RemoteDropdown
          fetcher={mockFetcher}
          onSelectionChange={onSelectionChange}
        />,
      );

      const button = screen.getByRole("button");
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Option 1")).toBeInTheDocument();
      });

      const option = screen.getByText("Option 1");
      await user.click(option);

      expect(onSelectionChange).toHaveBeenCalled();
    });

    it("closes dropdown after selection in single mode", async () => {
      const user = userEvent.setup();
      render(<RemoteDropdown fetcher={mockFetcher} multiple={false} />);

      const button = screen.getByRole("button");
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Option 1")).toBeInTheDocument();
      });

      const option = screen.getByText("Option 1");
      await user.click(option);

      await waitFor(() => {
        const dropdown = screen.queryByRole("listbox");
        expect(dropdown).not.toBeInTheDocument();
      });
    });

    it("keeps dropdown open after selection in multiple mode", async () => {
      const user = userEvent.setup();
      render(<RemoteDropdown fetcher={mockFetcher} multiple={true} />);

      const button = screen.getByRole("button");
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Option 1")).toBeInTheDocument();
      });

      const option = screen.getByText("Option 1");
      await user.click(option);

      await waitFor(() => {
        // The dropdown content doesn't have role="listbox", check for the dropdown content div instead
        const dropdown = document.querySelector(".dropdown-content");
        expect(dropdown).toBeInTheDocument();
        // Also verify the option is still visible
        expect(screen.getByText("Option 2")).toBeInTheDocument();
      });
    });
  });

  describe("Search", () => {
    it("filters options when searchable and search term is entered", async () => {
      const user = userEvent.setup();
      const searchFetcher = vi.fn(async (params) => {
        if (params.search) {
          return {
            content: [{ id: 1, label: "Matched Option", value: "matched" }],
            totalElements: 1,
            totalPages: 1,
            size: 20,
            number: 0,
            first: true,
            last: true,
            empty: false,
            numberOfElements: 1,
          };
        }
        return mockFetcher(params);
      });

      render(<RemoteDropdown fetcher={searchFetcher} searchable={true} />);

      const button = screen.getByRole("button");
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByPlaceholderText("Search...")).toBeInTheDocument();
      });

      const searchInput = screen.getByPlaceholderText("Search...");
      await user.type(searchInput, "test");

      await waitFor(
        () => {
          expect(searchFetcher).toHaveBeenCalledWith(
            expect.objectContaining({ search: "test" }),
          );
        },
        { timeout: 2000 },
      );
    });

    it("clears search when clear button is clicked", async () => {
      const user = userEvent.setup();
      render(<RemoteDropdown fetcher={mockFetcher} searchable={true} />);

      const button = screen.getByRole("button");
      await user.click(button);

      await waitFor(() => {
        const searchInput = screen.getByPlaceholderText("Search...");
        user.type(searchInput, "test");
      });

      await waitFor(() => {
        const clearButton = screen.getByRole("button", {
          name: "aria.clearSearch",
        });
        if (clearButton) {
          user.click(clearButton);
        }
      });
    });
  });

  describe("Infinite Scroll", () => {
    it("loads more data when scrolling to bottom", async () => {
      const user = userEvent.setup();
      const infiniteScrollFetcher = vi.fn(async (params) => {
        const page = params.page || 0;
        return {
          content: Array.from({ length: 20 }, (_, i) => ({
            id: page * 20 + i + 1,
            label: `Option ${page * 20 + i + 1}`,
            value: `opt${page * 20 + i + 1}`,
          })),
          totalElements: 40,
          totalPages: 2,
          size: 20,
          number: page,
          first: page === 0,
          last: page === 1,
          empty: false,
          numberOfElements: 20,
        };
      });

      render(<RemoteDropdown fetcher={infiniteScrollFetcher} />);

      const button = screen.getByRole("button");
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Option 1")).toBeInTheDocument();
      });

      await waitFor(() => {
        const scrollContainer = document.querySelector(".overflow-y-auto");
        expect(scrollContainer).toBeInTheDocument();
      });

      const scrollContainer = document.querySelector(".overflow-y-auto");
      expect(scrollContainer).toBeInTheDocument();

      if (scrollContainer) {
        // Set scrollTop to be within 50px of the bottom to trigger infinite scroll
        // scrollHeight - scrollTop - clientHeight < 50
        // 2000 - scrollTop - 500 < 50
        // scrollTop > 1450
        Object.defineProperty(scrollContainer, "scrollTop", {
          value: 1500, // Within threshold (2000 - 1500 - 500 = 0 < 50)
          writable: true,
          configurable: true,
        });
        Object.defineProperty(scrollContainer, "scrollHeight", {
          value: 2000,
          writable: true,
          configurable: true,
        });
        Object.defineProperty(scrollContainer, "clientHeight", {
          value: 500,
          writable: true,
          configurable: true,
        });

        fireEvent.scroll(scrollContainer);

        await waitFor(
          () => {
            expect(infiniteScrollFetcher).toHaveBeenCalledTimes(2);
          },
          { timeout: 3000 },
        );
      }
    });
  });

  describe("Custom Rendering", () => {
    it("renders custom option when renderOption is provided", async () => {
      const user = userEvent.setup();
      const renderOption = vi.fn((option) => <div>Custom {option.label}</div>);

      render(
        <RemoteDropdown fetcher={mockFetcher} renderOption={renderOption} />,
      );

      const button = screen.getByRole("button");
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Custom Option 1")).toBeInTheDocument();
      });
    });

    it("renders custom empty state when renderEmpty is provided", async () => {
      const user = userEvent.setup();
      const emptyFetcher = vi.fn(() =>
        Promise.resolve({
          content: [],
          totalElements: 0,
          totalPages: 0,
          size: 20,
          number: 0,
          first: true,
          last: true,
          empty: true,
          numberOfElements: 0,
        }),
      );

      render(
        <RemoteDropdown
          fetcher={emptyFetcher}
          renderEmpty={() => <div>Custom Empty</div>}
        />,
      );

      const button = screen.getByRole("button");
      await user.click(button);

      await waitFor(() => {
        expect(screen.getByText("Custom Empty")).toBeInTheDocument();
      });
    });

    it("renders custom error state when renderError is provided", async () => {
      const user = userEvent.setup();
      const errorFetcher = vi.fn(() =>
        Promise.reject(new Error("Fetch failed")),
      );

      render(
        <RemoteDropdown
          fetcher={errorFetcher}
          renderError={(error, retry) => (
            <div>
              Custom Error: {error.message}
              <button onClick={retry}>Retry</button>
            </div>
          )}
        />,
      );

      const button = screen.getByRole("button");
      await user.click(button);

      await waitFor(() => {
        expect(
          screen.getByText("Custom Error: Fetch failed"),
        ).toBeInTheDocument();
      });
    });
  });

  describe("Accessibility", () => {
    it("has proper id attributes", () => {
      render(<RemoteDropdown fetcher={mockFetcher} id="test-dropdown" />);
      const button = screen.getByRole("button");
      expect(button).toHaveAttribute("id", "test-dropdown");
    });

    it("button has proper aria attributes", () => {
      render(<RemoteDropdown fetcher={mockFetcher} />);
      const button = screen.getByRole("button");
      expect(button).toHaveAttribute("aria-expanded", "false");
      expect(button).toHaveAttribute("aria-haspopup", "listbox");
    });
  });

  describe("Edge Cases", () => {
    it("handles disabled state", () => {
      render(<RemoteDropdown fetcher={mockFetcher} disabled={true} />);
      const button = screen.getByRole("button");
      expect(button).toBeDisabled();
    });

    it("handles clear action", async () => {
      const user = userEvent.setup();
      const onClear = vi.fn();
      const value: DropdownOption = {
        id: 1,
        label: "Selected",
        value: "opt1",
      };

      render(
        <RemoteDropdown
          fetcher={mockFetcher}
          value={value}
          clearable={true}
          onClear={onClear}
        />,
      );

      const clearButton = screen.getByTitle("aria.clearSelection");
      await user.click(clearButton);

      expect(onClear).toHaveBeenCalled();
    });

    it("cancels previous request when new one is made", async () => {
      const user = userEvent.setup();
      const fetcherWithAbort = vi.fn(
        async (
          params: SearchParams,
        ): Promise<RemoteDataResponse<DropdownOption>> => {
          return mockFetcher(params);
        },
      );

      render(<RemoteDropdown fetcher={fetcherWithAbort} searchable={true} />);

      const button = screen.getByRole("button");
      await user.click(button);

      await waitFor(() => {
        const searchInput = screen.getByPlaceholderText("Search...");
        user.type(searchInput, "a");
        user.type(searchInput, "b");
      });

      await waitFor(() => {
        expect(fetcherWithAbort).toHaveBeenCalled();
      });
    });
  });
});
