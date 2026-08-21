export const toKebabKey = (input: string): string =>
  input
    .toLowerCase()
    .replace(/[\s,]+/g, "-")
    .replace(/\.+/g, "");

/** Uppercase the first character, leaving the rest untouched. */
export const capitalizeFirst = (input?: string | null): string => {
  if (!input) return "";
  return input.charAt(0).toUpperCase() + input.slice(1);
};

export const toPascalCase = (input: string): string => {
  return input
    .trim()
    .replace(/[^a-zA-Z0-9]+/g, " ")
    .split(" ")
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join("");
};
