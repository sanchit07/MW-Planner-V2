import { renderHook, act } from "@testing-library/react";
import { useRef } from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { useClickOutside } from "../useClickOutside";

describe("useClickOutside", () => {
  let containerDiv: HTMLDivElement;

  beforeEach(() => {
    containerDiv = document.createElement("div");
    document.body.appendChild(containerDiv);
  });

  it("calls onClose when clicking outside the container", () => {
    const onClose = vi.fn();
    const { result } = renderHook(() => {
      const ref = useRef<HTMLDivElement>(containerDiv);
      useClickOutside(ref, true, onClose);
      return ref;
    });

    expect(result.current.current).toBe(containerDiv);

    act(() => {
      const outsideEl = document.createElement("div");
      document.body.appendChild(outsideEl);
      document.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
    });

    expect(onClose).toHaveBeenCalled();
  });

  it("does not call onClose when clicking inside the container", () => {
    const onClose = vi.fn();
    renderHook(() => {
      const ref = useRef<HTMLDivElement>(containerDiv);
      useClickOutside(ref, true, onClose);
      return ref;
    });

    act(() => {
      containerDiv.dispatchEvent(
        new MouseEvent("mousedown", { bubbles: true }),
      );
    });

    expect(onClose).not.toHaveBeenCalled();
  });

  it("calls onClose when Escape key is pressed", () => {
    const onClose = vi.fn();
    renderHook(() => {
      const ref = useRef<HTMLDivElement>(containerDiv);
      useClickOutside(ref, true, onClose);
      return ref;
    });

    act(() => {
      document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    });

    expect(onClose).toHaveBeenCalled();
  });

  it("does not listen for Escape when listenEscape=false", () => {
    const onClose = vi.fn();
    renderHook(() => {
      const ref = useRef<HTMLDivElement>(containerDiv);
      useClickOutside(ref, true, onClose, { listenEscape: false });
      return ref;
    });

    act(() => {
      document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    });

    expect(onClose).not.toHaveBeenCalled();
  });

  it("does nothing when isActive is false", () => {
    const onClose = vi.fn();
    renderHook(() => {
      const ref = useRef<HTMLDivElement>(containerDiv);
      useClickOutside(ref, false, onClose);
      return ref;
    });

    act(() => {
      const outside = document.createElement("button");
      document.body.appendChild(outside);
      document.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
      document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    });

    expect(onClose).not.toHaveBeenCalled();
  });

  it("does not call onClose for non-Escape keys", () => {
    const onClose = vi.fn();
    renderHook(() => {
      const ref = useRef<HTMLDivElement>(containerDiv);
      useClickOutside(ref, true, onClose);
      return ref;
    });

    act(() => {
      document.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter" }));
    });

    expect(onClose).not.toHaveBeenCalled();
  });
});
