import { FlaskConical } from "lucide-react";
import React from "react";

import { Switch } from "./ui/Switch";
import {
  useGetTestModeQuery,
  useUpdateTestModeMutation,
} from "../services/testMode/testModeSlice";
import { clsx } from "clsx";

/**
 * Header Test Mode toggle (ported from V1). Switching flips the user between the
 * live and demo data partitions; every plan list/detail read depends on it, so we
 * do a full reload after a successful update to drop all cached query data.
 */
export const TestModeSwitch: React.FC = () => {
  const { data, isLoading } = useGetTestModeQuery();
  const [updateTestMode, { isLoading: isUpdating }] =
    useUpdateTestModeMutation();

  const testMode = Boolean(data?.data?.testMode);

  const handleChange = async (checked: boolean) => {
    const result = await updateTestMode(checked);
    if (!("error" in result)) {
      // Switching partitions invalidates effectively every cached read.
      window.location.reload();
    }
  };

  return (
    <div
      id="test-mode-switch"
      className={clsx(
        "flex items-center gap-2 px-3 py-1.5 rounded-full border transition-colors",
        testMode
          ? "bg-amber-100 border-amber-300 text-amber-800"
          : "bg-transparent border-container-border text-mw-neutral-400 dark:text-mw-neutral-300",
      )}
      title={
        testMode
          ? "Test Mode is ON — new plans are demo data, hidden from live users"
          : "Turn on Test Mode to work with demo data"
      }
    >
      <FlaskConical size={14} />
      <span className="text-xs font-medium whitespace-nowrap">Test Mode</span>
      <Switch
        size="sm"
        checked={testMode}
        disabled={isLoading || isUpdating}
        onChange={handleChange}
        id="test-mode-toggle"
      />
    </div>
  );
};

/** Amber banner shown under the header while the user works in the demo partition. */
export const DemoModeBanner: React.FC = () => {
  const { data } = useGetTestModeQuery();
  if (!data?.data?.testMode) return null;
  return (
    <div
      id="demo-mode-banner"
      className="bg-amber-100 border-b border-amber-300 text-amber-800 text-xs px-6 py-1.5 text-center"
    >
      Test Mode is on — everything you create is demo data and is kept separate
      from live plans and analytics.
    </div>
  );
};
