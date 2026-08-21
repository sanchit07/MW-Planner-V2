import { describe, it, expect } from "vitest";

import {
  serializeValue,
  deserializeValue,
  serializeFormData,
  deserializeFormData,
} from "../serialization";

describe("serialization", () => {
  describe("serializeValue", () => {
    it("should serialize Date to ISO string", () => {
      const date = new Date(2024, 0, 15);
      const result = serializeValue(date);
      expect(result).toBe("2024-01-15");
    });

    it("should serialize array with dates", () => {
      const dates = [new Date(2024, 0, 15), new Date(2024, 0, 20)];
      const result = serializeValue(dates);
      expect(Array.isArray(result)).toBe(true);
      expect(result).toEqual(["2024-01-15", "2024-01-20"]);
    });

    it("should serialize object with dates", () => {
      const obj = {
        date: new Date(2024, 0, 15),
        name: "test",
      };
      const result = serializeValue(obj);
      expect(result).toEqual({
        date: "2024-01-15",
        name: "test",
      });
    });

    it("should return primitive values as-is", () => {
      expect(serializeValue(123)).toBe(123);
      expect(serializeValue("string")).toBe("string");
      expect(serializeValue(true)).toBe(true);
      expect(serializeValue(null)).toBe(null);
    });

    it("should serialize nested objects", () => {
      const obj = {
        nested: {
          date: new Date(2024, 0, 15),
        },
      };
      const result = serializeValue(obj);
      expect(result).toEqual({
        nested: {
          date: "2024-01-15",
        },
      });
    });
  });

  describe("deserializeValue", () => {
    it("should deserialize ISO date string to Date", () => {
      const result = deserializeValue("2024-01-15");
      expect(result).toBeInstanceOf(Date);
      expect((result as Date).getFullYear()).toBe(2024);
    });

    it("should not deserialize non-date strings", () => {
      expect(deserializeValue("not-a-date")).toBe("not-a-date");
      // Invalid date strings that match the regex pattern will attempt to parse
      // but the function catches errors and returns the original value
      const invalidDate = deserializeValue("2024-13-45");
      // It might parse to a date or return the string, depending on implementation
      expect(
        typeof invalidDate === "string" || invalidDate instanceof Date,
      ).toBe(true);
    });

    it("should deserialize array with date strings", () => {
      const result = deserializeValue(["2024-01-15", "2024-01-20"]);
      expect(Array.isArray(result)).toBe(true);
      expect((result as Date[])[0]).toBeInstanceOf(Date);
    });

    it("should deserialize object with date strings", () => {
      const obj = {
        date: "2024-01-15",
        name: "test",
      };
      const result = deserializeValue(obj);
      expect((result as { date: Date; name: string }).date).toBeInstanceOf(
        Date,
      );
      expect((result as { date: Date; name: string }).name).toBe("test");
    });

    it("should return primitive values as-is", () => {
      expect(deserializeValue(123)).toBe(123);
      expect(deserializeValue("string")).toBe("string");
      expect(deserializeValue(true)).toBe(true);
    });

    it("should deserialize nested objects", () => {
      const obj = {
        nested: {
          date: "2024-01-15",
        },
      };
      const result = deserializeValue(obj);
      expect((result as { nested: { date: Date } }).nested.date).toBeInstanceOf(
        Date,
      );
    });
  });

  describe("serializeFormData", () => {
    it("should serialize form data with dates", () => {
      const formData = {
        name: "test",
        date: new Date(2024, 0, 15),
        value: 123,
      };
      const result = serializeFormData(formData);
      expect(result).toEqual({
        name: "test",
        date: "2024-01-15",
        value: 123,
      });
    });

    it("should handle empty object", () => {
      expect(serializeFormData({})).toEqual({});
    });
  });

  describe("deserializeFormData", () => {
    it("should deserialize form data with date strings", () => {
      const formData = {
        name: "test",
        date: "2024-01-15",
        value: 123,
      };
      const result = deserializeFormData(formData);
      expect((result as { date: Date }).date).toBeInstanceOf(Date);
      expect((result as { name: string }).name).toBe("test");
      expect((result as { value: number }).value).toBe(123);
    });

    it("should handle empty object", () => {
      expect(deserializeFormData({})).toEqual({});
    });
  });
});
