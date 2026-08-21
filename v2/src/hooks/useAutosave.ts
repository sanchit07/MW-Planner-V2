import { useCallback, useEffect, useRef, useState } from "react";
import { useSelector } from "react-redux";

import { useAutosaveCampaignMutation } from "../services/campaign/campaignSlice";
import { RootState } from "../store";

interface AutosaveOptions {
  debounceMs?: number;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  onSuccess?: (response: any) => void;
  onError?: (error: unknown) => void;
}

interface AutosaveHookReturn {
  autosave: (
    fieldName: string,
    value: unknown,
    canSkipValueNullValidation?: boolean,
  ) => Promise<void>;
  autosaveBatch: (data: Record<string, unknown>) => Promise<void>;
  isAutosaving: boolean;
}

// Shared state manager for autosave loading state across all hook instances
type StateChangeListener = (isAutosaving: boolean) => void;

class AutosaveStateManager {
  private isAutosaving = false;
  private listeners = new Set<StateChangeListener>();
  private pendingRequests = new Set<string>();

  subscribe(listener: StateChangeListener): () => void {
    this.listeners.add(listener);
    // Immediately notify the new subscriber of current state
    listener(this.isAutosaving);
    // Return unsubscribe function
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notifyListeners() {
    this.listeners.forEach((listener) => {
      listener(this.isAutosaving);
    });
  }

  addRequest(requestKey: string) {
    this.pendingRequests.add(requestKey);
    if (!this.isAutosaving) {
      this.isAutosaving = true;
      this.notifyListeners();
    }
  }

  removeRequest(requestKey: string) {
    this.pendingRequests.delete(requestKey);
    if (this.pendingRequests.size === 0 && this.isAutosaving) {
      this.isAutosaving = false;
      this.notifyListeners();
    }
  }

  clear() {
    this.pendingRequests.clear();
    if (this.isAutosaving) {
      this.isAutosaving = false;
      this.notifyListeners();
    }
  }

  getState(): boolean {
    return this.isAutosaving;
  }
}

// Create a singleton instance
const autosaveStateManager = new AutosaveStateManager();

/**
 * Custom hook for handling autosave functionality with performance optimization
 * Features:
 * - Per-field debounce timers so blurring multiple fields quickly doesn't cancel earlier saves
 * - Pending-value queue: if a field's request is in-flight, the latest value is stored and
 *   sent as a follow-up once the current request completes (no data is dropped)
 * - Memory cleanup for background operations
 * - Error handling with retry mechanism
 * - Support for both single field and batch autosave operations
 *
 * @example
 * // Single field autosave (existing functionality)
 * const { autosave } = useAutosave();
 * await autosave('fieldName', value);
 *
 * @example
 * // Batch autosave (new functionality)
 * const { autosaveBatch } = useAutosave();
 * await autosaveBatch({ field1: value1, field2: value2, field3: value3 });
 */
export const useAutosave = (
  options: AutosaveOptions = {},
): AutosaveHookReturn => {
  const { debounceMs = 0, onSuccess, onError } = options;

  // Get campaign ID from Redux store
  const campaignId = useSelector((state: RootState) => {
    const id = state.campaign.campaignId;
    if (!id && state.campaign.campaignData) {
      return state.campaign.campaignData.id;
    }
    return id;
  });

  // RTK Query mutation hook
  const [autosaveCampaignMutation] = useAutosaveCampaignMutation();

  // Shared loading state from state manager
  const [isAutosaving, setIsAutosaving] = useState(() =>
    autosaveStateManager.getState(),
  );

  // Per-field debounce timers — blurring field B won't cancel field A's pending save
  const debounceTimersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(
    new Map(),
  );
  const pendingRequestsRef = useRef<Set<string>>(new Set());
  // Latest unconfirmed value per field — sent as one follow-up after in-flight completes
  const pendingNextValueRef = useRef<Map<string, unknown>>(new Map());

  // Subscribe to shared state changes
  useEffect(() => {
    const unsubscribe = autosaveStateManager.subscribe((isAutosavingState) => {
      setIsAutosaving(isAutosavingState);
    });
    return unsubscribe;
  }, []);

  // Cleanup function to prevent memory leaks
  const cleanup = useCallback(() => {
    // Clear all per-field debounce timers
    debounceTimersRef.current.forEach((timer) => clearTimeout(timer));
    debounceTimersRef.current.clear();

    // Remove all pending requests from shared state manager
    pendingRequestsRef.current.forEach((requestKey) => {
      autosaveStateManager.removeRequest(requestKey);
    });

    // Clear pending requests tracking
    pendingRequestsRef.current.clear();
    pendingNextValueRef.current.clear();
  }, []);

  // Helper function to validate and filter data
  const validateAndFilterData = useCallback(
    (data: Record<string, unknown>): Record<string, unknown> => {
      const filteredData: Record<string, unknown> = {};

      Object.entries(data).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== "") {
          filteredData[key] = value;
        }
      });

      return filteredData;
    },
    [],
  );

  // Main autosave function with debouncing and error handling
  const autosave = useCallback(
    async (
      fieldName: string,
      value: unknown,
      canSkipValueNullValidation = false,
    ): Promise<void> => {
      // Validate required data
      if (!campaignId) {
        console.warn("Autosave: Campaign ID not available");
        return;
      }

      if (!canSkipValueNullValidation) {
        if (value === undefined || value === null || value === "") {
          console.log(`Autosave: Skipping empty value for field ${fieldName}`);
          return;
        }
      }

      // Clear any existing debounce timer for this specific field only
      const existingTimer = debounceTimersRef.current.get(fieldName);
      if (existingTimer) {
        clearTimeout(existingTimer);
      }

      // Set up per-field debounced autosave
      const timer = setTimeout(async () => {
        debounceTimersRef.current.delete(fieldName);

        const requestKey = `${fieldName}_${Date.now()}`;

        // If a request for this field is already in-flight, queue the latest value
        // and let the in-flight request's finally block pick it up
        const existingRequest = Array.from(pendingRequestsRef.current).find(
          (key) => key.startsWith(fieldName),
        );

        if (existingRequest) {
          console.log(
            `Autosave: Request in-flight for ${fieldName}, queuing latest value`,
          );
          pendingNextValueRef.current.set(fieldName, value);
          return;
        }

        // Add to pending requests
        pendingRequestsRef.current.add(requestKey);
        autosaveStateManager.addRequest(requestKey);

        console.log(
          `Autosave: Saving ${fieldName} = ${value} for campaign ${campaignId}`,
        );

        try {
          const response = await autosaveCampaignMutation({
            id: campaignId,
            data: { [fieldName]: value },
          }).unwrap();

          console.log(`Autosave: Successfully saved ${fieldName}`, response);

          if (onSuccess) {
            onSuccess(response);
          }
        } catch (error: unknown) {
          if (error && typeof error === "object" && "name" in error) {
            if (error.name === "AbortError") {
              console.log(`Autosave: Request aborted for ${fieldName}`);
              return;
            }
          }

          console.error(`Autosave: Failed to save ${fieldName}`, error);

          if (onError) {
            onError(error);
          }
        } finally {
          pendingRequestsRef.current.delete(requestKey);
          autosaveStateManager.removeRequest(requestKey);

          // If a newer value was queued while this request was in-flight, send it now
          const pendingValue = pendingNextValueRef.current.get(fieldName);
          if (pendingValue !== undefined) {
            pendingNextValueRef.current.delete(fieldName);
            console.log(
              `Autosave: Sending queued value for ${fieldName} after in-flight completed`,
            );
            void autosave(fieldName, pendingValue, canSkipValueNullValidation);
          }
        }
      }, debounceMs);

      debounceTimersRef.current.set(fieldName, timer);
    },
    [campaignId, autosaveCampaignMutation, debounceMs, onSuccess, onError],
  );

  // Batch autosave function for multiple attributes
  const autosaveBatch = useCallback(
    async (data: Record<string, unknown>): Promise<void> => {
      // Validate required data
      if (!campaignId) {
        console.warn("Autosave: Campaign ID not available");
        return;
      }

      // Filter out empty values
      const filteredData = validateAndFilterData(data);

      if (Object.keys(filteredData).length === 0) {
        console.log("Autosave: No valid data to save");
        return;
      }

      const batchKey = "batch";

      // Clear any existing debounce timer for batch
      const existingTimer = debounceTimersRef.current.get(batchKey);
      if (existingTimer) {
        clearTimeout(existingTimer);
      }

      const timer = setTimeout(async () => {
        debounceTimersRef.current.delete(batchKey);

        const requestKey = `batch_${Date.now()}`;

        // If a batch request is already in-flight, queue the latest data
        const existingRequest = Array.from(pendingRequestsRef.current).find(
          (key) => key.startsWith(batchKey),
        );

        if (existingRequest) {
          console.log("Autosave: Batch request in-flight, queuing latest data");
          pendingNextValueRef.current.set(batchKey, filteredData);
          return;
        }

        pendingRequestsRef.current.add(requestKey);
        autosaveStateManager.addRequest(requestKey);

        try {
          const response = await autosaveCampaignMutation({
            id: campaignId,
            data: filteredData,
          }).unwrap();

          if (onSuccess) {
            onSuccess(response);
          }
        } catch (error: unknown) {
          if (error && typeof error === "object" && "name" in error) {
            if (error.name === "AbortError") {
              console.log("Autosave: Batch request aborted");
              return;
            }
          }

          if (onError) {
            onError(error);
          }
        } finally {
          pendingRequestsRef.current.delete(requestKey);
          autosaveStateManager.removeRequest(requestKey);

          // If newer batch data was queued while this request was in-flight, send it now
          const pendingData = pendingNextValueRef.current.get(batchKey);
          if (pendingData !== undefined) {
            pendingNextValueRef.current.delete(batchKey);
            void autosaveBatch(pendingData as Record<string, unknown>);
          }
        }
      }, debounceMs);

      debounceTimersRef.current.set(batchKey, timer);
    },
    [
      campaignId,
      autosaveCampaignMutation,
      debounceMs,
      onSuccess,
      onError,
      validateAndFilterData,
    ],
  );

  // Cleanup on unmount
  useEffect(() => {
    return cleanup;
  }, [cleanup]);

  return {
    autosave,
    autosaveBatch,
    isAutosaving,
  };
};

export default useAutosave;
