import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

import MultiSelect, { TreeNode } from "../MultiSelect";

vi.mock("react-dom", async () => {
  const actual = await vi.importActual("react-dom");
  return {
    ...actual,
    createPortal: (node: React.ReactNode) => node,
  };
});

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string) => key,
  }),
}));

describe("MultiSelect", () => {
  const defaultOptions: TreeNode[] = [
    { id: "1", label: "Option 1", value: "opt1" },
    { id: "2", label: "Option 2", value: "opt2" },
    {
      id: "3",
      label: "Option 3",
      value: "opt3",
      children: [
        { id: "3-1", label: "Option 3.1", value: "opt3-1" },
        { id: "3-2", label: "Option 3.2", value: "opt3-2" },
      ],
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("Rendering", () => {
    it("renders multiselect input", async () => {
      const user = userEvent.setup();
      const { container } = render(
        <MultiSelect options={defaultOptions} value={[]} onChange={vi.fn()} />,
      );
      // Click the trigger to open dropdown
      const trigger = container.querySelector(
        'div[class*="border"][class*="rounded-md"]',
      ) as HTMLElement;
      await user.click(trigger);

      await waitFor(() => {
        const input = screen.getByRole("textbox");
        expect(input).toBeInTheDocument();
      });
    });

    it("displays placeholder when no value", () => {
      render(
        <MultiSelect
          options={defaultOptions}
          value={[]}
          onChange={vi.fn()}
          placeholder="Select options"
        />,
      );
      expect(screen.getByText("Select options")).toBeInTheDocument();
    });

    it("displays selected chips", () => {
      render(
        <MultiSelect
          options={defaultOptions}
          value={["opt1", "opt2"]}
          onChange={vi.fn()}
        />,
      );
      expect(screen.getByText("Option 1")).toBeInTheDocument();
      expect(screen.getByText("Option 2")).toBeInTheDocument();
    });

    it("displays remaining count when maxVisibleChips is exceeded", () => {
      const manyOptions: TreeNode[] = Array.from({ length: 10 }, (_, i) => ({
        id: String(i),
        label: `Option ${i}`,
        value: `opt${i}`,
      }));
      render(
        <MultiSelect
          options={manyOptions}
          value={manyOptions.map((opt) => opt.value)}
          onChange={vi.fn()}
          maxVisibleChips={3}
        />,
      );
      expect(screen.getByText(/\+7/)).toBeInTheDocument();
    });

    it("renders clear button when clearable and value exists", () => {
      render(
        <MultiSelect
          options={defaultOptions}
          value={["opt1"]}
          onChange={vi.fn()}
          clearable={true}
        />,
      );
      const buttons = screen.getAllByRole("button");
      const clearButton = buttons.find((btn) => {
        const svg = btn.querySelector("svg");
        return svg;
      });
      expect(clearButton).toBeInTheDocument();
    });

    it("applies custom className", () => {
      const { container } = render(
        <MultiSelect
          options={defaultOptions}
          value={[]}
          onChange={vi.fn()}
          className="custom-class"
        />,
      );
      const wrapper = container.querySelector(".custom-class");
      expect(wrapper).toBeInTheDocument();
    });
  });

  describe("Interactions", () => {
    it("opens dropdown when input is clicked", async () => {
      const user = userEvent.setup();
      const { container } = render(
        <MultiSelect options={defaultOptions} value={[]} onChange={vi.fn()} />,
      );

      // Click the trigger to open dropdown
      const trigger = container.querySelector(
        'div[class*="border"][class*="rounded-md"]',
      ) as HTMLElement;
      await user.click(trigger);

      await waitFor(() => {
        expect(screen.getByText("Option 1")).toBeInTheDocument();
      });
    });

    it("closes dropdown when clicking outside", async () => {
      const user = userEvent.setup();
      const { container } = render(
        <MultiSelect options={defaultOptions} value={[]} onChange={vi.fn()} />,
      );

      // Click the trigger to open dropdown
      const trigger = container.querySelector(
        'div[class*="border"][class*="rounded-md"]',
      ) as HTMLElement;
      await user.click(trigger);

      await waitFor(() => {
        expect(screen.getByText("Option 1")).toBeInTheDocument();
      });

      // Click on the overlay to close dropdown (the overlay is rendered via portal)
      const overlay = document.querySelector(
        ".dropdown-overlay",
      ) as HTMLElement;
      expect(overlay).toBeInTheDocument();
      if (overlay) {
        await user.click(overlay);
      }

      await waitFor(() => {
        // Check that the dropdown content is no longer in the document
        const dropdownContent = document.querySelector(".dropdown-content");
        expect(dropdownContent).not.toBeInTheDocument();
      });
    });

    it("calls onChange when option is selected", async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const { container } = render(
        <MultiSelect options={defaultOptions} value={[]} onChange={onChange} />,
      );

      // Click the trigger to open dropdown
      const trigger = container.querySelector(
        'div[class*="border"][class*="rounded-md"]',
      ) as HTMLElement;
      await user.click(trigger);

      await waitFor(() => {
        expect(screen.getByText("Option 1")).toBeInTheDocument();
      });

      // Click on the option div (which has the onClick handler), not just the text
      const optionText = screen.getByText("Option 1");
      const optionDiv = optionText.closest('div[class*="flex items-start"]');
      if (optionDiv) {
        await user.click(optionDiv);
      } else {
        await user.click(optionText);
      }

      await waitFor(() => {
        expect(onChange).toHaveBeenCalledWith(["opt1"]);
      });
    });

    it("calls onChange when option is deselected", async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const { container } = render(
        <MultiSelect
          options={defaultOptions}
          value={["opt1"]}
          onChange={onChange}
        />,
      );

      // Click the trigger to open dropdown
      const trigger = container.querySelector(
        'div[class*="border"][class*="rounded-md"]',
      ) as HTMLElement;
      await user.click(trigger);

      await waitFor(() => {
        // Find the label in the dropdown (not the chip), using the checkbox ID
        const checkbox = container.querySelector('[id="1"]');
        expect(checkbox).toBeInTheDocument();
      });

      // Click on the option div (which has the onClick handler), not the checkbox
      // Find the option div by finding the checkbox first, then its parent div
      const checkbox = container.querySelector('[id="1"]');
      const optionDiv = checkbox?.closest(
        'div[class*="flex items-start"]',
      ) as HTMLElement;
      expect(optionDiv).toBeInTheDocument();
      if (optionDiv) {
        await user.click(optionDiv);
      }

      await waitFor(() => {
        expect(onChange).toHaveBeenCalled();
      });
    });

    it("removes chip when remove button is clicked", async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      render(
        <MultiSelect
          options={defaultOptions}
          value={["opt1"]}
          onChange={onChange}
        />,
      );

      const chip = screen.getByText("Option 1");
      // The button is inside the chip's parent span (the outer span with the border)
      const chipContainer = chip.closest('span[class*="inline-flex"]');
      const removeButton = chipContainer?.querySelector("button");
      expect(removeButton).toBeInTheDocument();
      if (removeButton) {
        await user.click(removeButton);
        await waitFor(() => {
          expect(onChange).toHaveBeenCalledWith([]);
        });
      }
    });

    it("calls handleClearAll when clear button is clicked", async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const { container } = render(
        <MultiSelect
          options={defaultOptions}
          value={["opt1", "opt2"]}
          onChange={onChange}
          clearable={true}
        />,
      );

      // Find the clear button by looking for XCircle icon (clear button) vs ChevronDown (dropdown arrow)
      const buttons = container.querySelectorAll("button");
      const clearButton = Array.from(buttons).find((btn) => {
        const svg = btn.querySelector("svg");
        if (svg) {
          // XCircle has a circle element, ChevronDown doesn't
          const circle = svg.querySelector("circle");
          return circle !== null;
        }
        return false;
      });
      expect(clearButton).toBeInTheDocument();
      if (clearButton) {
        await user.click(clearButton);
        await waitFor(() => {
          expect(onChange).toHaveBeenCalledWith([]);
        });
      }
    });
  });

  describe("Search", () => {
    it("filters options when search term is entered", async () => {
      const user = userEvent.setup();
      const { container } = render(
        <MultiSelect
          options={defaultOptions}
          value={[]}
          onChange={vi.fn()}
          searchable={true}
        />,
      );

      // Click the trigger to open dropdown
      const trigger = container.querySelector(
        'div[class*="border"][class*="rounded-md"]',
      ) as HTMLElement;
      await user.click(trigger);

      await waitFor(() => {
        expect(screen.getByText("Option 1")).toBeInTheDocument();
      });

      const searchInput = screen.getByPlaceholderText(/search/i);
      await user.type(searchInput, "Option 1");

      await waitFor(() => {
        expect(screen.getByText("Option 1")).toBeInTheDocument();
        expect(screen.queryByText("Option 2")).not.toBeInTheDocument();
      });
    });

    it("calls onSearchChange when search term changes", async () => {
      const user = userEvent.setup();
      const onSearchChange = vi.fn();
      const { container } = render(
        <MultiSelect
          options={defaultOptions}
          value={[]}
          onChange={vi.fn()}
          searchable={true}
          onSearchChange={onSearchChange}
        />,
      );

      // Click the trigger to open dropdown
      const trigger = container.querySelector(
        'div[class*="border"][class*="rounded-md"]',
      ) as HTMLElement;
      await user.click(trigger);

      await waitFor(async () => {
        const searchInput = screen.getByPlaceholderText(/search/i);
        await user.type(searchInput, "test");
      });

      await waitFor(() => {
        expect(onSearchChange).toHaveBeenCalled();
      });
    });
  });

  describe("Status Text (i18n)", () => {
    it("shows noneSelected key for parent node with no children selected", async () => {
      const user = userEvent.setup();
      const { container } = render(
        <MultiSelect options={defaultOptions} value={[]} onChange={vi.fn()} />,
      );

      const trigger = container.querySelector(
        'div[class*="border"][class*="rounded-md"]',
      ) as HTMLElement;
      await user.click(trigger);

      await waitFor(() => {
        expect(screen.getByText("Option 3")).toBeInTheDocument();
        expect(screen.getByText("noneSelected")).toBeInTheDocument();
      });
    });

    it("shows nSelected key for parent node when children are selected", async () => {
      const user = userEvent.setup();
      const { container } = render(
        <MultiSelect
          options={defaultOptions}
          value={["opt3-1"]}
          onChange={vi.fn()}
        />,
      );

      const trigger = container.querySelector(
        'div[class*="border"][class*="rounded-md"]',
      ) as HTMLElement;
      await user.click(trigger);

      await waitFor(() => {
        expect(screen.getByText("Option 3")).toBeInTheDocument();
        expect(screen.getByText("nSelected")).toBeInTheDocument();
      });
    });
  });

  describe("Checkbox IDs", () => {
    it("assigns unique ids to checkboxes based on node id", async () => {
      const user = userEvent.setup();
      const { container } = render(
        <MultiSelect options={defaultOptions} value={[]} onChange={vi.fn()} />,
      );

      const trigger = container.querySelector(
        'div[class*="border"][class*="rounded-md"]',
      ) as HTMLElement;
      await user.click(trigger);

      await waitFor(() => {
        const cb1 = container.querySelector('[id="1"]');
        const cb2 = container.querySelector('[id="2"]');
        const cb3 = container.querySelector('[id="3"]');
        expect(cb1).toBeInTheDocument();
        expect(cb2).toBeInTheDocument();
        expect(cb3).toBeInTheDocument();
        // All three must be distinct elements
        expect(cb1).not.toBe(cb2);
        expect(cb2).not.toBe(cb3);
      });
    });
  });

  describe("Tree Structure", () => {
    it("renders nested children when expandable", async () => {
      const user = userEvent.setup();
      const { container } = render(
        <MultiSelect
          options={defaultOptions}
          value={[]}
          onChange={vi.fn()}
          expandable={true}
        />,
      );

      // Click the trigger to open dropdown
      const trigger = container.querySelector(
        'div[class*="border"][class*="rounded-md"]',
      ) as HTMLElement;
      await user.click(trigger);

      await waitFor(() => {
        expect(screen.getByText("Option 3")).toBeInTheDocument();
      });

      const expandButton = screen
        .getByText("Option 3")
        .closest("div")
        ?.querySelector("button");
      if (expandButton) {
        await user.click(expandButton);

        await waitFor(() => {
          expect(screen.getByText("Option 3.1")).toBeInTheDocument();
        });
      }
    });

    it("selects all children when parent is selected", async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const { container } = render(
        <MultiSelect
          options={defaultOptions}
          value={[]}
          onChange={onChange}
          expandable={false}
        />,
      );

      // Click the trigger to open dropdown
      const trigger = container.querySelector(
        'div[class*="border"][class*="rounded-md"]',
      ) as HTMLElement;
      await user.click(trigger);

      await waitFor(() => {
        expect(screen.getByText("Option 3")).toBeInTheDocument();
      });

      // Click on the option div (which has the onClick handler), not the checkbox
      const optionText = screen.getByText("Option 3");
      const optionDiv = optionText.closest('div[class*="flex items-start"]');
      if (optionDiv) {
        await user.click(optionDiv);
      } else {
        await user.click(optionText);
      }

      await waitFor(() => {
        expect(onChange).toHaveBeenCalled();
        const call = onChange.mock.calls[0][0];
        expect(call).toContain("opt3");
        expect(call).toContain("opt3-1");
        expect(call).toContain("opt3-2");
      });
    });
  });

  describe("Select All", () => {
    it("renders select all option when showSelectAll is true", async () => {
      const user = userEvent.setup();
      const { container } = render(
        <MultiSelect
          options={defaultOptions}
          value={[]}
          onChange={vi.fn()}
          showSelectAll={true}
        />,
      );

      // Click the trigger to open dropdown
      const trigger = container.querySelector(
        'div[class*="border"][class*="rounded-md"]',
      ) as HTMLElement;
      await user.click(trigger);

      await waitFor(() => {
        expect(screen.getByText("selectAll")).toBeInTheDocument();
      });
    });

    it("selects all options when select all is clicked", async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const { container } = render(
        <MultiSelect
          options={defaultOptions}
          value={[]}
          onChange={onChange}
          showSelectAll={true}
        />,
      );

      // Click the trigger to open dropdown
      const trigger = container.querySelector(
        'div[class*="border"][class*="rounded-md"]',
      ) as HTMLElement;
      await user.click(trigger);

      await waitFor(() => {
        expect(screen.getByText("selectAll")).toBeInTheDocument();
      });

      // Click on the Select All div (which has the onClick handler), not the checkbox
      const selectAllText = screen.getByText("selectAll");
      const selectAllDiv = selectAllText.closest(
        'div[class*="flex items-start"]',
      );
      if (selectAllDiv) {
        await user.click(selectAllDiv);
      } else {
        await user.click(selectAllText);
      }

      await waitFor(() => {
        expect(onChange).toHaveBeenCalled();
        const call = onChange.mock.calls[0][0];
        expect(call.length).toBeGreaterThan(0);
      });
    });
  });

  describe("Max Selections", () => {
    it("disables options when maxSelections is reached", async () => {
      const user = userEvent.setup();
      const { container } = render(
        <MultiSelect
          options={defaultOptions}
          value={["opt1", "opt2"]}
          onChange={vi.fn()}
          maxSelections={2}
        />,
      );

      // Click the trigger to open dropdown
      const trigger = container.querySelector(
        'div[class*="border"][class*="rounded-md"]',
      ) as HTMLElement;
      await user.click(trigger);

      await waitFor(() => {
        // Find the checkbox for Option 3 (which should be disabled since maxSelections is 2)
        const option3Checkbox = container.querySelector('[id="3"]');
        expect(option3Checkbox).toBeInTheDocument();
        // The parent div should have opacity-50 class when disabled
        const parentDiv = option3Checkbox?.closest(
          'div[class*="flex items-start"]',
        );
        expect(parentDiv?.className).toContain("opacity-50");
      });
    });
  });

  describe("Accessibility", () => {
    it("has proper id attributes", () => {
      const { container } = render(
        <MultiSelect
          options={defaultOptions}
          value={[]}
          onChange={vi.fn()}
          id="test-multiselect"
        />,
      );
      expect(container.querySelector("#test-multiselect")).toBeInTheDocument();
    });

    it("generates default id when not provided", () => {
      const { container } = render(
        <MultiSelect options={defaultOptions} value={[]} onChange={vi.fn()} />,
      );
      expect(container.querySelector("#multiselect")).toBeInTheDocument();
    });
  });

  describe("Edge Cases", () => {
    it("handles empty options", () => {
      render(<MultiSelect options={[]} value={[]} onChange={vi.fn()} />);
      expect(screen.getByText("multiSelect.placeholder")).toBeInTheDocument();
    });

    it("handles disabled options", async () => {
      const user = userEvent.setup();
      const disabledOptions: TreeNode[] = [
        { id: "1", label: "Option 1", value: "opt1", disabled: true },
      ];
      const { container } = render(
        <MultiSelect options={disabledOptions} value={[]} onChange={vi.fn()} />,
      );

      // Click the trigger to open dropdown
      const trigger = container.querySelector(
        'div[class*="border"][class*="rounded-md"]',
      ) as HTMLElement;
      await user.click(trigger);

      await waitFor(() => {
        // Find the specific checkbox for the disabled option by ID
        const checkbox = container.querySelector(
          '[id="1"]',
        ) as HTMLInputElement;
        expect(checkbox).toBeInTheDocument();
        // The component doesn't set disabled on the checkbox, but disables the parent div
        // Check that the parent div has the disabled styling (opacity-50)
        const parentDiv = checkbox.closest('div[class*="flex items-start"]');
        expect(parentDiv?.className).toContain("opacity-50");
        expect(parentDiv?.className).toContain("cursor-not-allowed");
      });
    });

    it("calls onBlur when dropdown closes", async () => {
      const user = userEvent.setup();
      const onBlur = vi.fn();
      const onChange = vi.fn();
      const { container } = render(
        <MultiSelect
          options={defaultOptions}
          value={[]}
          onChange={onChange}
          onBlur={onBlur}
        />,
      );

      // Click the trigger to open dropdown
      const trigger = container.querySelector(
        'div[class*="border"][class*="rounded-md"]',
      ) as HTMLElement;
      await user.click(trigger);

      await waitFor(() => {
        expect(screen.getByText("Option 1")).toBeInTheDocument();
      });

      // Select an option first (onBlur is only called when values have changed)
      const optionText = screen.getByText("Option 1");
      const optionDiv = optionText.closest('div[class*="flex items-start"]');
      if (optionDiv) {
        await user.click(optionDiv);
      }

      await waitFor(() => {
        expect(onChange).toHaveBeenCalled();
      });

      // Now close the dropdown by clicking outside (overlay is rendered via portal)
      const overlay = document.querySelector(
        ".dropdown-overlay",
      ) as HTMLElement;
      expect(overlay).toBeInTheDocument();
      if (overlay) {
        await user.click(overlay);
      }

      await waitFor(() => {
        expect(onBlur).toHaveBeenCalled();
      });
    });
  });
});
