import { useTranslate } from "@tolgee/react";
import { formatDateToYYYYMMDD } from "@utils/schedule.utils";
import { clsx } from "clsx";
import { useMemo, useState } from "react";

export interface ScheduleGridProps {
  startDate: Date;
  endDate: Date;
  selectedDays: string[]; // Array of day names like ["Mon", "Tue", ...]
  selectedHours: Set<string>; // Set of hour keys like "2025-11-12-0", "2025-11-12-1", etc.
  onCellClick: (date: Date, hour: number) => void;
  onRowClick?: (date: Date) => void;
  onColumnClick?: (hour: number) => void;
  className?: string;
  cellClassName?: Record<string, string>;
}

const DAYS_OF_WEEK = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const HOURS = Array.from({ length: 24 }, (_, i) => i);

export const ScheduleGrid: React.FC<ScheduleGridProps> = ({
  startDate,
  endDate,
  selectedDays,
  selectedHours,
  onCellClick,
  onRowClick,
  onColumnClick,
  className,
  cellClassName,
}) => {
  const { t: tCommon } = useTranslate(["common"]);
  const [hoveredColumn, setHoveredColumn] = useState<number | null>(null);
  const [hoveredRow, setHoveredRow] = useState<number | null>(null);
  const [hoveredCell, setHoveredCell] = useState<{
    dateIndex: number;
    hour: number;
  } | null>(null);

  // Generate all dates in the range (show all days, not filtered)
  const dates = useMemo(() => {
    const dateArray: Date[] = [];
    const current = new Date(startDate);
    current.setHours(0, 0, 0, 0);
    const end = new Date(endDate);
    end.setHours(23, 59, 59, 999);

    while (current <= end) {
      dateArray.push(new Date(current));
      current.setDate(current.getDate() + 1);
    }
    return dateArray;
  }, [startDate, endDate]);

  // Check if a date's day is selected
  const isDaySelected = (date: Date) => {
    const dayName = DAYS_OF_WEEK[date.getDay() === 0 ? 6 : date.getDay() - 1];
    return selectedDays.includes(dayName);
  };

  const formatDateLabel = (date: Date) => {
    const day = date.getDate();
    const month = tCommon(`calendar.monthNamesShort.${date.getMonth()}`);
    const dayIdx = date.getDay() === 0 ? 6 : date.getDay() - 1;
    const dayName = tCommon(`calendar.weekDaysShort.${dayIdx}`);
    return `${day} ${month} (${dayName})`;
  };

  const getHourKey = (date: Date, hour: number) => {
    const dateStr = formatDateToYYYYMMDD(date);
    return `${dateStr}-${hour}`;
  };

  const isCellSelected = (date: Date, hour: number) => {
    // Only show as selected if the day is selected AND the hour is in selectedHours
    if (!isDaySelected(date)) {
      return false;
    }
    return selectedHours.has(getHourKey(date, hour));
  };

  const isRowSelected = (date: Date) => {
    // Only show row as selected if the day is selected AND all hours are selected
    if (!isDaySelected(date)) {
      return false;
    }
    return HOURS.every((hour) => isCellSelected(date, hour));
  };

  const isColumnSelected = (hour: number) => {
    if (selectedDays.length === 0) {
      return false;
    }
    // Check if all selected days have this hour selected
    return dates.every((date) => {
      if (!isDaySelected(date)) {
        return true; // Skip unselected days
      }
      return isCellSelected(date, hour);
    });
  };

  const handleColumnClick = (hour: number) => {
    if (onColumnClick) {
      onColumnClick(hour);
    }
  };

  return (
    <div
      className={clsx(
        "w-full overflow-x-auto border border-white rounded-md",
        className,
      )}
    >
      <div className="inline-block min-w-full">
        <table className="w-full border border-white border-separate border-spacing-x-0.5 border-spacing-y-0.5">
          <thead>
            <tr>
              <th className="sticky left-0 z-20 bg-white border border-white px-3 py-2 text-left text-sm font-medium text-black min-w-[150px] leading-4">
                {tCommon("calendar.dateHours")}
              </th>
              {HOURS.map((hour) => {
                const columnSelected = isColumnSelected(hour);
                return (
                  <th
                    key={hour}
                    id={`schedule-grid-column-${hour}`}
                    className={clsx(
                      "border border-white px-2 py-2 text-center text-sm font-medium min-w-[40px] leading-4 cursor-pointer transition-colors",
                      columnSelected
                        ? "bg-mw-primary-100 text-black font-medium hover:bg-mw-primary-200 rounded-sm"
                        : "text-black hover:bg-mw-primary-100",
                    )}
                    onMouseEnter={() => setHoveredColumn(hour)}
                    onMouseLeave={() => setHoveredColumn(null)}
                    onClick={() => handleColumnClick(hour)}
                  >
                    {hour}
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {dates.map((date, dateIndex) => {
              const dayIsSelected = isDaySelected(date);
              const rowSelected = isRowSelected(date);
              const isRowHovered = hoveredRow === dateIndex && dayIsSelected;
              return (
                <tr key={dateIndex} className="border border-white">
                  <td
                    id={`schedule-grid-row-${dateIndex}`}
                    className={clsx(
                      "sticky left-0 z-20 border border-white px-3 py-2 text-sm font-medium transition-colors cursor-pointer",
                      rowSelected
                        ? "bg-mw-primary-100 text-black font-medium rounded-sm "
                        : isRowHovered
                          ? "bg-mw-primary-50 text-black"
                          : dayIsSelected
                            ? "bg-white text-black hover:bg-mw-primary-50"
                            : "bg-white text-black",
                    )}
                    onMouseEnter={() => {
                      if (dayIsSelected) {
                        setHoveredRow(dateIndex);
                        setHoveredCell(null);
                      }
                    }}
                    onMouseLeave={() => setHoveredRow(null)}
                    onClick={() => onRowClick?.(date)}
                  >
                    {formatDateLabel(date)}
                  </td>
                  {HOURS.map((hour) => {
                    const cellSelected = isCellSelected(date, hour);
                    const isThisCellHovered =
                      hoveredCell?.dateIndex === dateIndex &&
                      hoveredCell?.hour === hour;
                    const isColumnHovered =
                      hoveredColumn === hour && dayIsSelected;
                    const shouldHighlightCell =
                      isThisCellHovered ||
                      (!isThisCellHovered && isRowHovered && dayIsSelected) ||
                      (!isThisCellHovered &&
                        !isRowHovered &&
                        isColumnHovered) ||
                      (!isThisCellHovered && isRowHovered && isColumnHovered);
                    const isDisabled = !dayIsSelected;
                    const dateStr = formatDateToYYYYMMDD(date);
                    return (
                      <td
                        key={hour}
                        id={`schedule-grid-cell-${dateStr}-${hour}`}
                        className={clsx(
                          "border border-mw-neutral-200 rounded-sm px-1 py-1 transition-colors",
                          isDisabled
                            ? "bg-white cursor-not-allowed opacity-50"
                            : cellSelected
                              ? "bg-mw-primary-100 hover:bg-mw-primary-200 border-none cursor-pointer"
                              : shouldHighlightCell
                                ? "bg-mw-primary-50 hover:bg-mw-primary-100 cursor-pointer"
                                : "bg-white hover:bg-mw-neutral-50 cursor-pointer",
                          cellClassName?.[getHourKey(date, hour)] || "",
                        )}
                        onMouseEnter={() => {
                          if (!isDisabled) {
                            setHoveredCell({ dateIndex, hour });
                            setHoveredRow(null);
                          }
                        }}
                        onMouseLeave={() => {
                          setHoveredCell(null);
                        }}
                        onClick={() => {
                          if (!isDisabled) {
                            onCellClick(date, hour);
                          }
                        }}
                      >
                        <div className="w-full h-full"></div>
                      </td>
                    );
                  })}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};
