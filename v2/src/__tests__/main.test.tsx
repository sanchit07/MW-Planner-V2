import React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { bootstrap } from "../main";

const mockRender = vi.fn();
const mockCreateRoot = vi.fn((_container?: unknown) => ({
  render: mockRender,
}));

vi.mock("react-dom/client", () => ({
  createRoot: (container: unknown) => mockCreateRoot(container),
}));

describe("main bootstrap", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("throws when root element is not found", () => {
    vi.spyOn(document, "getElementById").mockReturnValue(null);

    expect(() => bootstrap()).toThrow('Root element with id "root" not found');
    expect(mockCreateRoot).not.toHaveBeenCalled();
  });

  it("calls createRoot with root element when root exists", () => {
    const rootEl = document.createElement("div");
    rootEl.id = "root";
    vi.spyOn(document, "getElementById").mockReturnValue(rootEl);

    bootstrap();

    expect(mockCreateRoot).toHaveBeenCalledWith(rootEl);
  });

  it("calls render with StrictMode and wrapper when root exists", () => {
    const rootEl = document.createElement("div");
    rootEl.id = "root";
    vi.spyOn(document, "getElementById").mockReturnValue(rootEl);

    bootstrap();

    expect(mockRender).toHaveBeenCalledTimes(1);
    const rendered = mockRender.mock.calls[0][0];
    expect(rendered.type).toBe(React.StrictMode);
    expect(rendered.props.children).toBeDefined();
  });
});
