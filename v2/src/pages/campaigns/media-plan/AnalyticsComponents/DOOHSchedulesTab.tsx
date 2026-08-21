import { Switch } from "@components/ui/Switch";
import { Tooltip } from "@components/ui/Tooltip";
import { useTranslate } from "@tolgee/react";
import React, { useState } from "react";

import type { AnalyticsExcelData, DOOHPanelRow } from "../analyticsTypes";
import { buildDOOHCalendarWeeks } from "../utils";

interface DOOHSchedulesTabProps {
  analyticsData?: AnalyticsExcelData;
  flightStartDate?: string;
  flightEndDate?: string;
}

const PATTERN_LABEL_KEYS: Record<string, string> = {
  default: "default",
  commuter: "commuter",
  business: "business",
  nightlife: "nightlife",
  weekend: "weekend",
  "24/7": "twentyFourSeven",
  custom: "custom",
};

const BORDER_COLOR = "rgb(226, 232, 240)";

const DOOHSchedulesTab: React.FC<DOOHSchedulesTabProps> = ({
  analyticsData,
  flightStartDate,
  flightEndDate,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const panels: DOOHPanelRow[] = analyticsData?.doohPanels || [];
  const weeks = buildDOOHCalendarWeeks(flightStartDate, flightEndDate);
  const dayColumnCount = weeks.length * 7;
  const rollup = analyticsData?.doohRollupHeatmap;
  const [showRollupHeatmap, setShowRollupHeatmap] = useState(true);

  const col = (key: string) =>
    tCampaigns(`mediaPlanAnalytics.doohSchedules.calendar.columns.${key}`);
  const cadenceCol = (key: string) =>
    tCampaigns(`mediaPlanAnalytics.doohSchedules.cadence.columns.${key}`);
  const cadenceTooltip = (key: string) =>
    tCampaigns(`mediaPlanAnalytics.doohSchedules.cadence.tooltips.${key}`);
  const patternLabel = (pattern: string) =>
    tCampaigns(
      `mediaPlanAnalytics.doohSchedules.pattern.${PATTERN_LABEL_KEYS[pattern] || "custom"}`,
    );

  const customDayPartingCount = panels.filter(
    (p) => p.pattern !== "24/7",
  ).length;
  const defaultCount = panels.filter((p) => p.pattern === "24/7").length;

  const renderCalendar = () => (
    <section
      id="media-plan-analytics-dooh-calendar-card"
      className="overflow-hidden rounded-md border bg-white"
      style={{ borderColor: BORDER_COLOR }}
    >
      <div className="border-b p-4" style={{ borderColor: BORDER_COLOR }}>
        <h3
          className="text-sm font-semibold uppercase tracking-wider"
          style={{ color: "rgb(100, 116, 139)" }}
        >
          {tCampaigns("mediaPlanAnalytics.doohSchedules.calendar.title")}
        </h3>
        <p className="mt-1 text-xs" style={{ color: "rgb(100, 116, 139)" }}>
          {tCampaigns("mediaPlanAnalytics.doohSchedules.calendar.subtitle", {
            count: panels.length,
          })}
        </p>
      </div>
      <div className="overflow-x-auto">
        <table
          className="border-collapse text-xs"
          style={{ color: "rgb(15, 23, 42)" }}
        >
          <thead>
            <tr style={{ background: "rgba(37, 99, 235, 0.063)" }}>
              <th className="px-3 py-2 text-left font-medium" rowSpan={2}>
                {col("schedule")}
              </th>
              <th className="px-3 py-2 text-left font-medium" rowSpan={2}>
                {col("billboardSegment")}
              </th>
              <th className="px-3 py-2 text-left font-medium" rowSpan={2}>
                {col("start")}
              </th>
              <th className="px-3 py-2 text-left font-medium" rowSpan={2}>
                {col("end")}
              </th>
              <th className="px-3 py-2 text-right font-medium" rowSpan={2}>
                {col("days")}
              </th>
              <th className="px-3 py-2 text-right font-medium" rowSpan={2}>
                {col("opHours")}
              </th>
              {weeks.map((week) => (
                <th
                  key={week.label}
                  colSpan={7}
                  className="border-l px-1 py-1 text-center font-medium"
                  style={{ borderColor: BORDER_COLOR }}
                >
                  {week.label}
                </th>
              ))}
            </tr>
            <tr style={{ background: "rgba(37, 99, 235, 0.063)" }}>
              {weeks.flatMap((week) =>
                week.days.map((day) => (
                  <th
                    key={day.date}
                    className="border-l px-0 py-1 text-center font-normal"
                    style={{
                      width: 18,
                      minWidth: 18,
                      borderColor: BORDER_COLOR,
                      color: "rgb(100, 116, 139)",
                    }}
                  >
                    {day.dayLetter}
                  </th>
                )),
              )}
            </tr>
          </thead>
          <tbody>
            {panels.length === 0 ? (
              <tr>
                <td
                  colSpan={6 + dayColumnCount}
                  className="px-4 py-6 text-center text-mw-neutral-500"
                >
                  {tCampaigns("mediaPlanAnalytics.doohSchedules.noData")}
                </td>
              </tr>
            ) : (
              panels.map((panel, panelIndex) => (
                <React.Fragment key={panel.id}>
                  <tr
                    id={`media-plan-analytics-dooh-panel-${panel.id}`}
                    className="border-t"
                    style={{
                      borderColor: BORDER_COLOR,
                      background: "rgb(250, 251, 252)",
                    }}
                  >
                    <td className="px-3 py-2 font-semibold">
                      {panelIndex + 1}
                    </td>
                    <td className="px-3 py-2">
                      <span
                        className="font-semibold"
                        style={{ color: "rgb(15, 23, 42)" }}
                      >
                        {panel.inventoryName}
                      </span>
                      <span
                        className="ml-1 text-[10px]"
                        style={{ color: "rgb(100, 116, 139)" }}
                      >
                        {panel.referenceId}
                      </span>
                    </td>
                    <td className="px-3 py-2">{panel.startDate}</td>
                    <td className="px-3 py-2">{panel.endDate}</td>
                    <td className="px-3 py-2 text-right tabular-nums">
                      {panel.days}
                    </td>
                    <td className="px-3 py-2 text-right tabular-nums">
                      {panel.opHoursLabel === "mixed"
                        ? tCampaigns(
                            "mediaPlanAnalytics.doohSchedules.calendar.mixed",
                          )
                        : panel.opHoursLabel}
                    </td>
                    <td colSpan={dayColumnCount} />
                  </tr>
                  {panel.segments.map((segment, segIndex) => (
                    <tr
                      key={segment.id}
                      id={`media-plan-analytics-dooh-segment-${segment.id}`}
                      className="border-t"
                      style={{ borderColor: BORDER_COLOR }}
                    >
                      <td
                        className="px-3 py-1 text-right"
                        style={{ color: "rgb(100, 116, 139)" }}
                      >
                        {panelIndex + 1}.{segIndex + 1}
                      </td>
                      <td
                        className="px-3 py-1"
                        style={{ color: "rgb(100, 116, 139)" }}
                      >
                        {segment.segmentName}
                      </td>
                      <td className="px-3 py-1">{segment.startDate}</td>
                      <td className="px-3 py-1">{segment.endDate}</td>
                      <td className="px-3 py-1 text-right tabular-nums">
                        {segment.days}
                      </td>
                      <td className="px-3 py-1 text-right tabular-nums">
                        {segment.opHoursLabel}
                      </td>
                      {weeks.flatMap((week) =>
                        week.days.map((day) => (
                          <td
                            key={day.date}
                            className="border-l"
                            style={{
                              width: 18,
                              minWidth: 18,
                              height: 16,
                              borderColor: BORDER_COLOR,
                              background: segment.activeDates.includes(day.date)
                                ? "rgb(37, 99, 235)"
                                : "transparent",
                            }}
                          />
                        )),
                      )}
                    </tr>
                  ))}
                </React.Fragment>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );

  const renderCadenceHeader = (key: string) => (
    <th className="px-4 py-2 text-right">
      <Tooltip content={cadenceTooltip(key)}>
        <span className="cursor-help underline decoration-dotted underline-offset-2">
          {cadenceCol(key)}
        </span>
      </Tooltip>
    </th>
  );

  const renderCadenceTable = () => (
    <section
      id="media-plan-analytics-dooh-cadence-card"
      className="rounded-md border bg-white"
      style={{ borderColor: BORDER_COLOR }}
    >
      <div className="border-b p-4" style={{ borderColor: BORDER_COLOR }}>
        <h3
          className="text-sm font-semibold uppercase tracking-wider"
          style={{ color: "rgb(100, 116, 139)" }}
        >
          {tCampaigns("mediaPlanAnalytics.doohSchedules.cadence.title")}
        </h3>
        <p className="mt-1 text-xs" style={{ color: "rgb(100, 116, 139)" }}>
          {tCampaigns("mediaPlanAnalytics.doohSchedules.cadence.subtitle", {
            total: panels.length,
            custom: customDayPartingCount,
            default: defaultCount,
          })}
        </p>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm" style={{ color: "rgb(15, 23, 42)" }}>
          <thead>
            <tr
              className="border-b text-left text-xs uppercase tracking-wider"
              style={{ borderColor: BORDER_COLOR, color: "rgb(100, 116, 139)" }}
            >
              <th className="px-4 py-2">{cadenceCol("inventory")}</th>
              <th className="px-4 py-2">{cadenceCol("channel")}</th>
              <th className="px-4 py-2">{cadenceCol("pattern")}</th>
              {renderCadenceHeader("spotsPerLoop")}
              {renderCadenceHeader("spotsPerHour")}
              {renderCadenceHeader("activeHrsPerDay")}
              {renderCadenceHeader("daysPerWeek")}
              {renderCadenceHeader("sov")}
            </tr>
          </thead>
          <tbody>
            {panels.length === 0 ? (
              <tr>
                <td
                  colSpan={8}
                  className="px-4 py-6 text-center text-mw-neutral-500"
                >
                  {tCampaigns("mediaPlanAnalytics.doohSchedules.noData")}
                </td>
              </tr>
            ) : (
              panels.map((panel, index) => (
                <tr
                  key={panel.id}
                  id={`media-plan-analytics-dooh-cadence-row-${panel.id}`}
                  className="border-b last:border-0"
                  style={{
                    borderColor: BORDER_COLOR,
                    background:
                      index % 2 === 1
                        ? "rgba(37, 99, 235, 0.02)"
                        : "transparent",
                  }}
                >
                  <td className="px-4 py-2">
                    <div
                      className="font-medium"
                      style={{ color: "rgb(15, 23, 42)" }}
                    >
                      {panel.inventoryName}
                    </div>
                    <div
                      className="text-xs"
                      style={{ color: "rgb(100, 116, 139)" }}
                    >
                      {[panel.format, panel.city].filter(Boolean).join(" · ")}
                    </div>
                  </td>
                  <td
                    className="px-4 py-2 text-xs"
                    style={{ color: "rgb(100, 116, 139)" }}
                  >
                    {panel.channel}
                  </td>
                  <td className="px-4 py-2">
                    <span
                      className="rounded-full px-2 py-0.5 text-xs"
                      style={{
                        background: "rgba(37, 99, 235, 0.082)",
                        color: "rgb(37, 99, 235)",
                      }}
                    >
                      {patternLabel(panel.pattern)}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {panel.spotsPerLoop.toFixed(
                      Number.isInteger(panel.spotsPerLoop) ? 0 : 1,
                    )}
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {Math.round(panel.spotsPerHour)}
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {panel.activeHoursPerDay.toFixed(1)}
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {panel.daysPerWeek}
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {panel.sov.toFixed(1)}%
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );

  const cellOpacity = (count: number) => {
    if (count === 0 || !rollup) return null;
    if (rollup.maxCount <= 1) return 0.85;
    return 0.28 + (0.85 - 0.28) * ((count - 1) / (rollup.maxCount - 1));
  };

  const renderRollupToggle = () => (
    <div
      className="flex items-center justify-between rounded-md border p-3"
      style={{ borderColor: BORDER_COLOR, background: "rgb(255, 255, 255)" }}
    >
      <div className="flex items-center gap-3">
        <Switch
          id="dooh-rollup-toggle"
          checked={showRollupHeatmap}
          onChange={setShowRollupHeatmap}
        />
        <label
          htmlFor="dooh-rollup-toggle"
          className="text-sm"
          style={{ color: "rgb(15, 23, 42)" }}
        >
          {tCampaigns("mediaPlanAnalytics.doohSchedules.rollup.toggleLabel")}
        </label>
      </div>
      <div className="text-xs" style={{ color: "rgb(100, 116, 139)" }}>
        {tCampaigns("mediaPlanAnalytics.doohSchedules.rollup.summary", {
          schedules: rollup?.totalSchedules || 0,
          patterns: rollup?.totalPatterns || 0,
        })}
      </div>
    </div>
  );

  const renderRollupHeatmap = () => (
    <section
      id="media-plan-analytics-dooh-rollup-card"
      className="rounded-md border bg-white p-4"
      style={{ borderColor: BORDER_COLOR }}
    >
      <h3
        className="mb-3 text-sm font-semibold uppercase tracking-wider"
        style={{ color: "rgb(100, 116, 139)" }}
      >
        {tCampaigns("mediaPlanAnalytics.doohSchedules.rollup.title")}
      </h3>
      <div className="space-y-1">
        <div className="flex items-center gap-1">
          <div className="w-10 text-right text-[10px]" />
          {Array.from({ length: 24 }, (_, hour) => (
            <div
              key={hour}
              className="h-5 w-7 text-center text-[10px]"
              style={{ color: "rgb(100, 116, 139)" }}
            >
              {hour % 6 === 0 ? hour : ""}
            </div>
          ))}
        </div>
        {(rollup?.rows || []).map((row, rowIndex) => (
          <div
            key={row.day}
            className="flex items-center gap-1 transition-all duration-300 ease-out"
            style={{
              opacity: showRollupHeatmap ? 1 : 0,
              transform: showRollupHeatmap
                ? "translateY(0)"
                : "translateY(-4px)",
              transitionDelay: showRollupHeatmap ? `${rowIndex * 40}ms` : "0ms",
            }}
          >
            <div
              className="w-10 text-right text-[10px]"
              style={{ color: "rgb(100, 116, 139)" }}
            >
              {row.day}
            </div>
            {row.cells.map((cell) => {
              const opacity = cellOpacity(cell.count);
              return (
                <div
                  key={cell.hour}
                  className="h-5 w-7 rounded-sm transition-all duration-300 ease-out hover:scale-110"
                  title={`${row.day} ${cell.hour}:00 — ${cell.count}`}
                  style={{
                    background:
                      opacity === null
                        ? "rgb(226, 232, 240)"
                        : `rgba(37, 99, 235, ${opacity})`,
                  }}
                />
              );
            })}
          </div>
        ))}
      </div>
    </section>
  );

  return (
    <div
      id="media-plan-analytics-dooh-schedules-container"
      className="space-y-6"
    >
      {renderCalendar()}
      {renderCadenceTable()}
      {renderRollupToggle()}
      <div
        className={`grid transition-[grid-template-rows] duration-300 ease-in-out ${
          showRollupHeatmap ? "grid-rows-[1fr]" : "grid-rows-[0fr]"
        }`}
      >
        <div className="overflow-hidden">{renderRollupHeatmap()}</div>
      </div>
    </div>
  );
};

export default DOOHSchedulesTab;
