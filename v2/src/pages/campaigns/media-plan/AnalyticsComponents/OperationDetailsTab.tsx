import { useTranslate } from "@tolgee/react";
import { formatCompactNumber } from "@utils/dashboard.utils";
import React from "react";

import type {
  AnalyticsExcelData,
  ClassicOperationScheduleRow,
  DigitalOperationScheduleRow,
  MobileOperationScheduleRow,
} from "../analyticsTypes";

interface OperationDetailsTabProps {
  analyticsData: AnalyticsExcelData;
}

interface InventoryGroupInfo {
  key: string;
  inventoryName: string;
  referenceId: string;
  format: string;
  city: string;
}

/** Groups schedule rows by inventory (referenceId, falling back to name),
 * preserving first-seen order — each group renders as one inventory section
 * (header row + segment rows + a Total row) inside the section's table. */
function groupByInventory<T extends Omit<InventoryGroupInfo, "key">>(
  rows: T[],
): Array<{ group: InventoryGroupInfo; rows: T[] }> {
  const order: string[] = [];
  const groups = new Map<string, { group: InventoryGroupInfo; rows: T[] }>();
  rows.forEach((row) => {
    const key = row.referenceId || row.inventoryName;
    if (!groups.has(key)) {
      order.push(key);
      groups.set(key, {
        group: {
          key,
          inventoryName: row.inventoryName,
          referenceId: row.referenceId,
          format: row.format,
          city: row.city,
        },
        rows: [],
      });
    }
    groups.get(key)!.rows.push(row);
  });
  return order.map((key) => groups.get(key)!);
}

const OperationDetailsTab: React.FC<OperationDetailsTabProps> = ({
  analyticsData,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const operationData = analyticsData.operationDetails;

  const col = (key: string) =>
    tCampaigns(`mediaPlanAnalytics.operationDetails.columns.${key}`);
  const totalLabel = tCampaigns(
    "mediaPlanAnalytics.operationDetails.totalLabel",
  );
  const noData = tCampaigns("mediaPlanAnalytics.operationDetails.noData");

  const renderGroupHeaderRow = (group: InventoryGroupInfo, colSpan: number) => (
    <tr
      key={`${group.key}-header`}
      style={{ background: "rgb(250, 251, 252)" }}
    >
      <td
        colSpan={colSpan}
        className="border-t px-4 py-2"
        style={{ borderColor: "rgb(226, 232, 240)" }}
      >
        <span className="font-semibold" style={{ color: "rgb(15, 23, 42)" }}>
          {group.inventoryName}
        </span>
        <span className="ml-2 text-xs" style={{ color: "rgb(100, 116, 139)" }}>
          {[group.referenceId, group.format, group.city]
            .filter(Boolean)
            .join(" · ")}
        </span>
      </td>
    </tr>
  );

  const renderClassicSection = (rows: ClassicOperationScheduleRow[]) => {
    const groups = groupByInventory(rows);
    return (
      <section
        id="media-plan-analytics-operation-details-classic-card"
        className="overflow-hidden rounded-md border bg-white"
        style={{ borderColor: "rgb(226, 232, 240)" }}
      >
        <div
          className="px-4 py-2 text-sm font-semibold text-white"
          style={{ background: "rgb(37, 99, 235)" }}
        >
          {tCampaigns(
            "mediaPlanAnalytics.operationDetails.sectionTitle.classic",
          )}
        </div>
        <div className="overflow-x-auto">
          <table
            className="w-full text-sm"
            style={{ color: "rgb(15, 23, 42)" }}
          >
            <thead style={{ background: "rgba(37, 99, 235, 0.063)" }}>
              <tr>
                <th className="px-4 py-2 text-left font-medium">
                  {col("segment")}
                </th>
                <th className="px-4 py-2 text-left font-medium">
                  {col("startDate")}
                </th>
                <th className="px-4 py-2 text-left font-medium">
                  {col("endDate")}
                </th>
                <th className="px-4 py-2 text-right font-medium">
                  {col("operationDays")}
                </th>
              </tr>
            </thead>
            <tbody>
              {groups.map(({ group, rows: groupRows }) => {
                const totalOperationDays = groupRows.reduce(
                  (sum, row) => sum + row.operationDays,
                  0,
                );
                return (
                  <React.Fragment key={group.key}>
                    {renderGroupHeaderRow(group, 4)}
                    {groupRows.map((row) => (
                      <tr
                        key={row.id}
                        id={`media-plan-analytics-operation-details-row-${row.id}`}
                        className="border-t"
                        style={{ borderColor: "rgb(226, 232, 240)" }}
                      >
                        <td
                          className="px-4 py-2"
                          style={{ color: "rgb(100, 116, 139)" }}
                        >
                          {row.segment}
                        </td>
                        <td className="px-4 py-2">{row.startDate}</td>
                        <td className="px-4 py-2">{row.endDate}</td>
                        <td className="px-4 py-2 text-right tabular-nums">
                          {row.operationDays}
                        </td>
                      </tr>
                    ))}
                    <tr
                      className="border-t"
                      style={{
                        borderColor: "rgb(226, 232, 240)",
                        background: "rgba(37, 99, 235, 0.024)",
                      }}
                    >
                      <td
                        colSpan={3}
                        className="px-4 py-2 text-right text-xs font-semibold"
                        style={{ color: "rgb(100, 116, 139)" }}
                      >
                        {totalLabel}
                      </td>
                      <td className="px-4 py-2 text-right text-xs font-semibold tabular-nums">
                        {totalOperationDays}
                      </td>
                    </tr>
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>
    );
  };

  const renderDigitalOrMobileSection = (
    sectionKey: "digital" | "mobile",
    rows: DigitalOperationScheduleRow[] | MobileOperationScheduleRow[],
  ) => {
    const groups = groupByInventory(rows);
    const hasTotalSpots = sectionKey === "digital";
    const columnCount = hasTotalSpots ? 8 : 7;
    return (
      <section
        id={`media-plan-analytics-operation-details-${sectionKey}-card`}
        className="overflow-hidden rounded-md border bg-white"
        style={{ borderColor: "rgb(226, 232, 240)" }}
      >
        <div
          className="px-4 py-2 text-sm font-semibold text-white"
          style={{ background: "rgb(37, 99, 235)" }}
        >
          {tCampaigns(
            `mediaPlanAnalytics.operationDetails.sectionTitle.${sectionKey}`,
          )}
        </div>
        <div className="overflow-x-auto">
          <table
            className="w-full text-sm"
            style={{ color: "rgb(15, 23, 42)" }}
          >
            <thead style={{ background: "rgba(37, 99, 235, 0.063)" }}>
              <tr>
                <th className="px-4 py-2 text-left font-medium">
                  {col("segment")}
                </th>
                <th className="px-4 py-2 text-left font-medium">
                  {col("start")}
                </th>
                <th className="px-4 py-2 text-left font-medium">
                  {col("end")}
                </th>
                <th className="px-4 py-2 text-right font-medium">
                  {col("opDays")}
                </th>
                <th className="px-4 py-2 text-right font-medium">
                  {col("opHours")}
                </th>
                <th className="px-4 py-2 text-right font-medium">
                  {col("startTime")}
                </th>
                <th className="px-4 py-2 text-right font-medium">
                  {col("endTime")}
                </th>
                {hasTotalSpots && (
                  <th className="px-4 py-2 text-right font-medium">
                    {col("totalSpots")}
                  </th>
                )}
              </tr>
            </thead>
            <tbody>
              {groups.map(({ group, rows: groupRows }) => {
                const totalOpDays = groupRows.reduce(
                  (sum, row) => sum + row.operationDays,
                  0,
                );
                const totalSpots = hasTotalSpots
                  ? (groupRows as DigitalOperationScheduleRow[]).reduce(
                      (sum, row) => sum + row.totalSpots,
                      0,
                    )
                  : 0;
                return (
                  <React.Fragment key={group.key}>
                    {renderGroupHeaderRow(group, columnCount)}
                    {groupRows.map((row) => (
                      <tr
                        key={row.id}
                        id={`media-plan-analytics-operation-details-row-${row.id}`}
                        className="border-t"
                        style={{ borderColor: "rgb(226, 232, 240)" }}
                      >
                        <td
                          className="px-4 py-2"
                          style={{ color: "rgb(100, 116, 139)" }}
                        >
                          {row.segment}
                        </td>
                        <td className="px-4 py-2">{row.startDate}</td>
                        <td className="px-4 py-2">{row.endDate}</td>
                        <td className="px-4 py-2 text-right tabular-nums">
                          {row.operationDays}
                        </td>
                        <td className="px-4 py-2 text-right tabular-nums">
                          {row.operationHours}
                        </td>
                        <td className="px-4 py-2 text-right tabular-nums">
                          {row.startTime}
                        </td>
                        <td className="px-4 py-2 text-right tabular-nums">
                          {row.endTime}
                        </td>
                        {hasTotalSpots && (
                          <td className="px-4 py-2 text-right tabular-nums">
                            {formatCompactNumber(
                              (row as DigitalOperationScheduleRow).totalSpots,
                              1,
                            )}
                          </td>
                        )}
                      </tr>
                    ))}
                    <tr
                      className="border-t"
                      style={{
                        borderColor: "rgb(226, 232, 240)",
                        background: "rgba(37, 99, 235, 0.024)",
                      }}
                    >
                      <td
                        colSpan={3}
                        className="px-4 py-2 text-right text-xs font-semibold"
                        style={{ color: "rgb(100, 116, 139)" }}
                      >
                        {totalLabel}
                      </td>
                      <td className="px-4 py-2 text-right text-xs font-semibold tabular-nums">
                        {totalOpDays}
                      </td>
                      <td colSpan={3} />
                      {hasTotalSpots && (
                        <td className="px-4 py-2 text-right text-xs font-semibold tabular-nums">
                          {formatCompactNumber(totalSpots, 1)}
                        </td>
                      )}
                    </tr>
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>
    );
  };

  const hasAnyData =
    (operationData?.classic?.length || 0) > 0 ||
    (operationData?.digital?.length || 0) > 0 ||
    (operationData?.mobile?.length || 0) > 0;

  return (
    <div
      id="media-plan-analytics-operation-details-container"
      className="space-y-6"
    >
      {!hasAnyData && (
        <div className="px-4 py-6 text-center text-mw-neutral-500">
          {noData}
        </div>
      )}
      {operationData?.classic &&
        operationData.classic.length > 0 &&
        renderClassicSection(operationData.classic)}
      {operationData?.digital &&
        operationData.digital.length > 0 &&
        renderDigitalOrMobileSection("digital", operationData.digital)}
      {operationData?.mobile &&
        operationData.mobile.length > 0 &&
        renderDigitalOrMobileSection("mobile", operationData.mobile)}
    </div>
  );
};

export default OperationDetailsTab;
