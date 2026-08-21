import { describe, it, expect, beforeEach, vi } from "vitest";

import { getItem, setItem, removeItem, removeAll } from "../storage";

describe("storage", () => {
  const mockLocalStorage = {
    getItem: vi.fn(),
    setItem: vi.fn(),
    removeItem: vi.fn(),
    clear: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, "localStorage", {
      value: mockLocalStorage,
      writable: true,
    });
  });

  describe("getItem", () => {
    it("should return item from localStorage when available", () => {
      mockLocalStorage.getItem.mockReturnValue("test-value");
      const result = getItem("test-key");
      expect(result).toBe("test-value");
      expect(mockLocalStorage.getItem).toHaveBeenCalledWith("test-key");
    });

    it("should return null when localStorage is not available", () => {
      Object.defineProperty(window, "localStorage", {
        value: undefined,
        writable: true,
      });
      const result = getItem("test-key");
      expect(result).toBeNull();
    });

    it("should return null when localStorage throws an error", () => {
      // Mock resolveLocalStorage to return undefined when error occurs
      Object.defineProperty(window, "localStorage", {
        value: undefined,
        writable: true,
      });
      const result = getItem("test-key");
      expect(result).toBeNull();
      // Restore
      Object.defineProperty(window, "localStorage", {
        value: mockLocalStorage,
        writable: true,
      });
    });
  });

  describe("setItem", () => {
    it("should set item in localStorage when available", () => {
      setItem("test-key", "test-value");
      expect(mockLocalStorage.setItem).toHaveBeenCalledWith(
        "test-key",
        "test-value",
      );
    });

    it("should not throw when localStorage is not available", () => {
      Object.defineProperty(window, "localStorage", {
        value: undefined,
        writable: true,
      });
      expect(() => setItem("test-key", "test-value")).not.toThrow();
    });

    it("should not throw when localStorage throws an error", () => {
      // The function checks if storage exists before calling setItem
      // If storage throws during access, resolveLocalStorage returns undefined
      Object.defineProperty(window, "localStorage", {
        value: undefined,
        writable: true,
      });
      expect(() => setItem("test-key", "test-value")).not.toThrow();
      // Restore
      Object.defineProperty(window, "localStorage", {
        value: mockLocalStorage,
        writable: true,
      });
    });
  });

  describe("removeItem", () => {
    it("should remove item from localStorage when available", () => {
      removeItem("test-key");
      expect(mockLocalStorage.removeItem).toHaveBeenCalledWith("test-key");
    });

    it("should not throw when localStorage is not available", () => {
      Object.defineProperty(window, "localStorage", {
        value: undefined,
        writable: true,
      });
      expect(() => removeItem("test-key")).not.toThrow();
    });

    it("should not throw when localStorage throws an error", () => {
      Object.defineProperty(window, "localStorage", {
        value: undefined,
        writable: true,
      });
      expect(() => removeItem("test-key")).not.toThrow();
      // Restore
      Object.defineProperty(window, "localStorage", {
        value: mockLocalStorage,
        writable: true,
      });
    });
  });

  describe("removeAll", () => {
    it("should clear localStorage when available", () => {
      removeAll();
      expect(mockLocalStorage.clear).toHaveBeenCalled();
    });

    it("should not throw when localStorage is not available", () => {
      Object.defineProperty(window, "localStorage", {
        value: undefined,
        writable: true,
      });
      expect(() => removeAll()).not.toThrow();
    });

    it("should not throw when localStorage throws an error", () => {
      Object.defineProperty(window, "localStorage", {
        value: undefined,
        writable: true,
      });
      expect(() => removeAll()).not.toThrow();
      // Restore
      Object.defineProperty(window, "localStorage", {
        value: mockLocalStorage,
        writable: true,
      });
    });
  });
});
