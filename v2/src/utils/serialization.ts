/**
 * Utility functions for serializing/deserializing data for Redux compatibility
 */

import { toAPIDateString, fromAPIDateString } from "./dateUtils";

export const serializeValue = (value: unknown): unknown => {
  if (value instanceof Date) {
    return toAPIDateString(value);
  }
  if (Array.isArray(value)) {
    return value.map(serializeValue);
  }
  if (value && typeof value === "object") {
    const serialized: Record<string, unknown> = {};
    for (const [key, val] of Object.entries(value)) {
      serialized[key] = serializeValue(val);
    }
    return serialized;
  }
  return value;
};

export const deserializeValue = (value: unknown): unknown => {
  if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    // Check if it's a valid date string (YYYY-MM-DD format)
    try {
      return fromAPIDateString(value);
    } catch {
      // If it's not a valid date string, return as-is
      return value;
    }
  }
  if (Array.isArray(value)) {
    return value.map(deserializeValue);
  }
  if (value && typeof value === "object") {
    const deserialized: Record<string, unknown> = {};
    for (const [key, val] of Object.entries(value)) {
      deserialized[key] = deserializeValue(val);
    }
    return deserialized;
  }
  return value;
};

export const serializeFormData = (
  formData: Record<string, unknown>,
): Record<string, unknown> => {
  const serialized: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(formData)) {
    serialized[key] = serializeValue(value);
  }
  return serialized;
};

export const deserializeFormData = (
  formData: Record<string, unknown>,
): Record<string, unknown> => {
  const deserialized: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(formData)) {
    deserialized[key] = deserializeValue(value);
  }
  return deserialized;
};
