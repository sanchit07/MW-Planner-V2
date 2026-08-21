import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";

import { Tabs, TabsList, TabsTrigger, TabsContent } from "../Tabs";

describe("Tabs", () => {
  it("should render tabs with default value", () => {
    render(
      <Tabs defaultValue="tab1">
        <TabsList>
          <TabsTrigger value="tab1">Tab 1</TabsTrigger>
        </TabsList>
        <TabsContent value="tab1">Content 1</TabsContent>
      </Tabs>,
    );
    expect(screen.getByText("Tab 1")).toBeInTheDocument();
  });

  it("should show content for active tab", () => {
    render(
      <Tabs defaultValue="tab1">
        <TabsList>
          <TabsTrigger value="tab1">Tab 1</TabsTrigger>
          <TabsTrigger value="tab2">Tab 2</TabsTrigger>
        </TabsList>
        <TabsContent value="tab1">Content 1</TabsContent>
        <TabsContent value="tab2">Content 2</TabsContent>
      </Tabs>,
    );
    expect(screen.getByText("Content 1")).toBeInTheDocument();
    expect(screen.queryByText("Content 2")).not.toBeInTheDocument();
  });

  it("should switch tabs when clicked", async () => {
    const user = userEvent.setup();
    render(
      <Tabs defaultValue="tab1">
        <TabsList>
          <TabsTrigger value="tab1">Tab 1</TabsTrigger>
          <TabsTrigger value="tab2">Tab 2</TabsTrigger>
        </TabsList>
        <TabsContent value="tab1">Content 1</TabsContent>
        <TabsContent value="tab2">Content 2</TabsContent>
      </Tabs>,
    );

    const tab2 = screen.getByText("Tab 2");
    await user.click(tab2);

    expect(screen.getByText("Content 2")).toBeInTheDocument();
    expect(screen.queryByText("Content 1")).not.toBeInTheDocument();
  });

  it("should call onValueChange when provided", async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    render(
      <Tabs value="tab1" onValueChange={onValueChange}>
        <TabsList>
          <TabsTrigger value="tab1">Tab 1</TabsTrigger>
          <TabsTrigger value="tab2">Tab 2</TabsTrigger>
        </TabsList>
        <TabsContent value="tab1">Content 1</TabsContent>
        <TabsContent value="tab2">Content 2</TabsContent>
      </Tabs>,
    );

    const tab2 = screen.getByText("Tab 2");
    await user.click(tab2);

    expect(onValueChange).toHaveBeenCalledWith("tab2");
  });

  it("should disable tab when disabled prop is set", () => {
    render(
      <Tabs defaultValue="tab1">
        <TabsList>
          <TabsTrigger value="tab1" disabled>
            Disabled Tab
          </TabsTrigger>
        </TabsList>
      </Tabs>,
    );

    const button = screen.getByText("Disabled Tab");
    expect(button).toBeDisabled();
  });

  it("should apply custom className", () => {
    const { container } = render(
      <Tabs className="custom-class" defaultValue="tab1">
        <TabsList>
          <TabsTrigger value="tab1">Tab 1</TabsTrigger>
        </TabsList>
      </Tabs>,
    );
    expect(container.querySelector(".custom-class")).toBeInTheDocument();
  });

  it("should throw error if TabsTrigger used outside Tabs", () => {
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    expect(() => {
      render(<TabsTrigger value="tab1">Tab</TabsTrigger>);
    }).toThrow("TabsTrigger must be used within Tabs");
    consoleError.mockRestore();
  });

  it("should throw error if TabsContent used outside Tabs", () => {
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    expect(() => {
      render(<TabsContent value="tab1">Content</TabsContent>);
    }).toThrow("TabsContent must be used within Tabs");
    consoleError.mockRestore();
  });
});
