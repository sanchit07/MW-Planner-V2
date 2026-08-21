import { Tooltip } from "@components/ui/Tooltip";
import { useTranslate } from "@tolgee/react";
import { getAvailabilitySyncWarning } from "@utils/inventoryAvailabilityUI.utils";
import { TriangleAlert } from "lucide-react";
import type { AvailabilitySyncInfo } from "src/types/price-management.types";

interface AvailabilitySyncWarningProps {
  syncInfo: AvailabilitySyncInfo | null | undefined;
}

/**
 * Subtle staleness/failure indicator for IMS availability data.
 *
 * Renders nothing when the data is fresh and the last sync succeeded.
 * Shows a small warning badge when the last sync FAILED or the data is
 * older than the staleness threshold; the tooltip carries the last-synced
 * timestamp and (for failures) the sync error.
 */
export function AvailabilitySyncWarning({
  syncInfo,
}: AvailabilitySyncWarningProps) {
  const { t: tPrice } = useTranslate(["price"]);
  const warning = getAvailabilitySyncWarning(syncInfo);
  if (!warning) return null;

  const lastSyncedLine = syncInfo?.lastSyncedAt
    ? tPrice("availability.lastSynced", {
        time: new Date(syncInfo.lastSyncedAt).toLocaleString(),
      })
    : tPrice("availability.notSyncedYet");

  return (
    <Tooltip
      position="bottom"
      content={
        <div className="max-w-xs space-y-1 text-xs">
          <div>{lastSyncedLine}</div>
          {warning === "failed" && (
            <div>
              {syncInfo?.error
                ? tPrice("availability.syncFailed", { error: syncInfo.error })
                : tPrice("availability.syncFailedGeneric")}
            </div>
          )}
        </div>
      }
    >
      <span
        className="inline-flex items-center gap-1 text-xs text-mw-orange-warning-600 cursor-default"
        data-testid="badge-availability-sync-warning"
        role="status"
      >
        <TriangleAlert className="w-3.5 h-3.5" aria-hidden="true" />
        {warning === "failed"
          ? tPrice("availability.syncWarningFailed")
          : tPrice("availability.syncWarningStale")}
      </span>
    </Tooltip>
  );
}

export default AvailabilitySyncWarning;
