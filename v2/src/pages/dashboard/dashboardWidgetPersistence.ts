import storage from "@utils/storage";

export type WidgetVisibility = Record<string, boolean>;

const DASHBOARD_WIDGET_VISIBILITY_KEY = "dashboard_widget_visibility";

const MAX_STORED_KEYS = 100;

function isPlainObjectWithBooleans(
  value: unknown,
): value is Record<string, boolean> {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    return false;
  }
  for (const v of Object.values(value)) {
    if (typeof v !== "boolean") return false;
  }
  return true;
}

export function loadWidgetVisibilityFromStorage(): WidgetVisibility | null {
  try {
    const stored = storage.getItem(DASHBOARD_WIDGET_VISIBILITY_KEY);
    if (!stored) return null;
    const parsed = JSON.parse(stored) as unknown;
    if (!isPlainObjectWithBooleans(parsed)) return null;
    const keys = Object.keys(parsed);
    if (keys.length > MAX_STORED_KEYS) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function saveWidgetVisibilityToStorage(
  visibility: WidgetVisibility,
): void {
  try {
    const sanitized: WidgetVisibility = {};
    let count = 0;
    for (const [key, value] of Object.entries(visibility)) {
      if (
        typeof key === "string" &&
        typeof value === "boolean" &&
        count < MAX_STORED_KEYS
      ) {
        sanitized[key] = value;
        count++;
      }
    }
    storage.setItem(DASHBOARD_WIDGET_VISIBILITY_KEY, JSON.stringify(sanitized));
  } catch {
    // Ignore storage errors (e.g. quota, private mode)
  }
}
