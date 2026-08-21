import { renderHook } from "@testing-library/react";
import { toast } from "react-toastify";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { useAnnounce } from "../useAnnounce";

vi.mock("react-toastify", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    warn: vi.fn(),
    info: vi.fn(),
  },
}));

describe("useAnnounce", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("should return all announce functions", () => {
    const { result } = renderHook(() => useAnnounce());
    expect(result.current.showSuccess).toBeDefined();
    expect(result.current.showError).toBeDefined();
    expect(result.current.showWarning).toBeDefined();
    expect(result.current.showInfo).toBeDefined();
  });

  it("should call toast.success when showSuccess is called", () => {
    const { result } = renderHook(() => useAnnounce());
    result.current.showSuccess("Success message");
    expect(toast.success).toHaveBeenCalledWith("Success message");
  });

  it("should call toast.error when showError is called", () => {
    const { result } = renderHook(() => useAnnounce());
    result.current.showError("Error message");
    expect(toast.error).toHaveBeenCalledWith("Error message");
  });

  it("should call toast.warn when showWarning is called", () => {
    const { result } = renderHook(() => useAnnounce());
    result.current.showWarning("Warning message");
    expect(toast.warn).toHaveBeenCalled();
  });

  it("should call toast.info when showInfo is called", () => {
    const { result } = renderHook(() => useAnnounce());
    result.current.showInfo("Info message");
    expect(toast.info).toHaveBeenCalledWith("Info message");
  });
});
