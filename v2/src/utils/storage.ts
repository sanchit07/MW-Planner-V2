export interface StorageAdapter {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
  clear(): void;
}

const resolveLocalStorage = (): StorageAdapter | undefined => {
  try {
    if (
      typeof window !== "undefined" &&
      "localStorage" in window &&
      window.localStorage
    ) {
      return window.localStorage;
    }
  } catch {
    // Accessing localStorage can throw in some environments (e.g., privacy mode)
  }
  return undefined;
};

export const getItem = (key: string): string | null => {
  const storage = resolveLocalStorage();
  return storage ? storage.getItem(key) : null;
};

export const setItem = (key: string, value: string): void => {
  const storage = resolveLocalStorage();
  if (storage) {
    storage.setItem(key, value);
  }
};

export const removeItem = (key: string): void => {
  const storage = resolveLocalStorage();
  if (storage) {
    storage.removeItem(key);
  }
};

export const removeAll = (): void => {
  const storage = resolveLocalStorage();
  if (storage) {
    storage.clear();
  }
};

export default { getItem, setItem, removeItem, removeAll };
