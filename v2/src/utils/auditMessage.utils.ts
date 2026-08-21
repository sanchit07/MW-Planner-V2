/**
 * Formats raw campaign-audit message strings into human-readable text.
 *
 * The Campaign History audit endpoint emits messages that can contain
 * developer-oriented fragments:
 *   - raw map syntax:      `Budget Allocation:{digital=100.0}`
 *   - raw enum values:     `Client Type:DIRECT_ADVERTISER`
 *   - colons with no space: `Dates:08/07/2026`
 *
 * This helper rewrites those fragments into readable prose. It is intentionally
 * conservative: tokens it does not recognise are passed through unchanged.
 *
 * Note: ambiguous date formats (DD/MM vs MM/DD) cannot be disambiguated on the
 * client without knowing the backend's ordering, so dates are only spaced, not
 * reordered.
 */

/** `some_key` / `SOME-KEY` / `camelCase` → `Some Key` / `Camel Case`. */
const toTitleCase = (input: string): string =>
  input
    // split camelCase / PascalCase boundaries
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/[_-]+/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (char) => char.toUpperCase())
    .trim();

/**
 * Convert SCREAMING_SNAKE_CASE enum tokens to Title Case.
 * Requires at least one underscore so single all-caps tokens (CPM, BRL, N/A)
 * are left untouched.
 */
const formatEnums = (text: string): string =>
  text.replace(/\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\b/g, (match) =>
    toTitleCase(match),
  );

/**
 * Convert `{key=value, key2=value2}` map syntax to `Key: value, Key2: value2`
 * (braces removed).
 */
const formatMaps = (text: string): string =>
  text.replace(/\{([^{}]*)\}/g, (_full, inner: string) => {
    const pairs = inner
      .split(",")
      .map((part) => part.trim())
      .filter(Boolean)
      .map((part) => {
        const eq = part.indexOf("=");
        if (eq === -1) return part;
        const key = toTitleCase(part.slice(0, eq).trim());
        const value = part.slice(eq + 1).trim();
        return `${key}: ${value}`;
      });
    return pairs.join(", ");
  });

/** Ensure a space follows a colon that sits directly after a word char. */
const spaceAfterColons = (text: string): string =>
  text.replace(/([A-Za-z0-9]):(?=\S)/g, "$1: ");

export const formatAuditMessage = (message: string): string => {
  if (!message) return message;
  let output = formatMaps(message);
  output = formatEnums(output);
  output = spaceAfterColons(output);
  return output;
};
