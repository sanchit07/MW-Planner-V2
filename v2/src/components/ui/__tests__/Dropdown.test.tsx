import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import {
  Dropdown,
  DropdownTrigger,
  DropdownContent,
  DropdownItem,
} from "../Dropdown";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

// Mock createPortal
vi.mock("react-dom", async () => {
  const actual = await vi.importActual("react-dom");
  return {
    ...actual,
    createPortal: (node: React.ReactNode) => node,
  };
});

describe("Dropdown", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("should render dropdown trigger", () => {
    render(
      <Dropdown>
        <DropdownTrigger>
          <button>Open</button>
        </DropdownTrigger>
        <DropdownContent>
          <DropdownItem value="1">Item 1</DropdownItem>
        </DropdownContent>
      </Dropdown>,
    );
    expect(screen.getByText("Open")).toBeInTheDocument();
  });

  it("should open dropdown when trigger is clicked", async () => {
    const user = userEvent.setup();
    render(
      <Dropdown>
        <DropdownTrigger>
          <button>Open</button>
        </DropdownTrigger>
        <DropdownContent>
          <DropdownItem value="1">Item 1</DropdownItem>
        </DropdownContent>
      </Dropdown>,
    );

    const trigger = screen.getByText("Open");
    await user.click(trigger);

    await waitFor(() => {
      expect(screen.getByText("Item 1")).toBeInTheDocument();
    });
  });

  it("should call onChange when item is selected", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <Dropdown onChange={onChange}>
        <DropdownTrigger>
          <button>Open</button>
        </DropdownTrigger>
        <DropdownContent>
          <DropdownItem value="1">Item 1</DropdownItem>
        </DropdownContent>
      </Dropdown>,
    );

    const trigger = screen.getByText("Open");
    await user.click(trigger);

    await waitFor(() => {
      const item = screen.getByText("Item 1");
      expect(item).toBeInTheDocument();
    });

    const item = screen.getByText("Item 1");
    await user.click(item);

    expect(onChange).toHaveBeenCalledWith("1");
  });

  it("should close dropdown on escape key", async () => {
    const user = userEvent.setup();
    render(
      <Dropdown>
        <DropdownTrigger>
          <button>Open</button>
        </DropdownTrigger>
        <DropdownContent>
          <DropdownItem value="1">Item 1</DropdownItem>
        </DropdownContent>
      </Dropdown>,
    );

    const trigger = screen.getByText("Open");
    await user.click(trigger);

    await waitFor(() => {
      expect(screen.getByText("Item 1")).toBeInTheDocument();
    });

    await user.keyboard("{Escape}");

    await waitFor(() => {
      expect(screen.queryByText("Item 1")).not.toBeInTheDocument();
    });
  });

  it("should support searchable dropdown", async () => {
    const user = userEvent.setup();
    render(
      <Dropdown searchable>
        <DropdownTrigger>
          <button>Open</button>
        </DropdownTrigger>
        <DropdownContent>
          <DropdownItem value="1">Item 1</DropdownItem>
          <DropdownItem value="2">Item 2</DropdownItem>
        </DropdownContent>
      </Dropdown>,
    );

    const trigger = screen.getByText("Open");
    await user.click(trigger);

    await waitFor(() => {
      expect(screen.getByText("Item 1")).toBeInTheDocument();
    });
  });

  it("should apply custom className", () => {
    const { container } = render(
      <Dropdown className="custom-class">
        <DropdownTrigger>
          <button>Open</button>
        </DropdownTrigger>
        <DropdownContent>
          <DropdownItem value="1">Item 1</DropdownItem>
        </DropdownContent>
      </Dropdown>,
    );
    expect(container.querySelector(".custom-class")).toBeInTheDocument();
  });
});
