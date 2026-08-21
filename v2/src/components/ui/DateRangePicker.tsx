import { useClickOutside } from "@hooks/useClickOutside";
import { useTranslate } from "@tolgee/react";
import { createNDaysPresets, type DateRange } from "@utils/dashboard.utils";
import { isSameDay, isDateBetween, formatDisplayDate } from "@utils/dateUtils";
import { cn } from "@utils/tailwindMerge";
import {
  Calendar,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  XCircle,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { Input } from "./Input";

function normalizeToFirstOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

interface DateRangePickerProps {
  value?: DateRange;
  onChange?: (range: DateRange) => void;
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
  clearable?: boolean;
  minDate?: Date;
  maxDate?: Date;
  selectsRange?: boolean;
  numberOfMonths?: 1 | 2;
  className?: string;
  format?: string;
  label?: string;
  error?: string;
  helpText?: string;
  presets?: Array<{
    label: string;
    range: DateRange;
  }>;
  isActionsVisible?: boolean;
  onBlur?: () => void;
  id?: string;
}

// Utility functions are now imported from dateUtils

interface CalendarProps {
  currentMonth: Date;
  selectedRange: DateRange;
  onDateSelect: (date: Date) => void;
  onMonthChange: (month: Date) => void;
  minDate?: Date;
  maxDate?: Date;
  selectingStart: boolean;
  numberOfMonths: 1 | 2;
}

function CalendarMonth({
  currentMonth,
  selectedRange,
  onDateSelect,
  onMonthChange,
  minDate,
  maxDate,
  numberOfMonths,
}: CalendarProps) {
  const { t: tCommon } = useTranslate(["common"]);
  const monthNames = Array.from(
    { length: 12 },
    (_, i) =>
      tCommon(`calendar.monthNames.${i}`) ||
      [
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December",
      ][i],
  );

  const weekDays = Array.from(
    { length: 7 },
    (_, i) =>
      tCommon(`calendar.weekDaysShort.${i}`) ||
      ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"][i],
  );

  const firstDayOfMonth = new Date(
    currentMonth.getFullYear(),
    currentMonth.getMonth(),
    1,
  );
  const lastDayOfMonth = new Date(
    currentMonth.getFullYear(),
    currentMonth.getMonth() + 1,
    0,
  );
  // Get day of week (0 = Sunday, 1 = Monday, etc.)
  // Convert to Monday-based (0 = Monday, 6 = Sunday)
  let firstDayOfWeek = firstDayOfMonth.getDay();
  firstDayOfWeek = firstDayOfWeek === 0 ? 6 : firstDayOfWeek - 1;
  const daysInMonth = lastDayOfMonth.getDate();

  const days: Array<{ date: Date; isCurrentMonth: boolean }> = [];

  // Previous month's trailing days (Monday-based)
  for (let i = firstDayOfWeek - 1; i >= 0; i--) {
    const date = new Date(firstDayOfMonth);
    date.setDate(date.getDate() - (i + 1));
    days.push({ date, isCurrentMonth: false });
  }

  // Current month's days
  for (let day = 1; day <= daysInMonth; day++) {
    const date = new Date(
      currentMonth.getFullYear(),
      currentMonth.getMonth(),
      day,
    );
    days.push({ date, isCurrentMonth: true });
  }

  // Next month's leading days
  const totalCells = Math.ceil(days.length / 7) * 7;
  const dateLength = totalCells - days.length;
  for (let i = 0; i < dateLength; i++) {
    const dayOfNextMonth: number = i + 1;
    const date: Date = new Date(
      currentMonth.getFullYear(),
      currentMonth.getMonth() + 1,
      dayOfNextMonth,
    );
    days.push({ date, isCurrentMonth: false });
  }

  return (
    <div className="w-80 flex flex-col justify-start items-start overflow-hidden">
      {/* Header */}
      <div className="self-stretch  h-10 min-h-10 relative bg-white border-b border-t border-mw-neutral-100">
        <button
          onClick={() => {
            const newMonth = new Date(
              currentMonth.getFullYear() - 1,
              currentMonth.getMonth(),
              1,
            );
            onMonthChange(newMonth);
          }}
          className="w-5 h-10 min-h-10 left-[8px] top-0 absolute flex items-center justify-center hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700 cursor-pointer"
        >
          <ChevronsLeft className="size-4 text-mw-neutral-400" />
        </button>

        <button
          onClick={() => {
            const newMonth = new Date(
              currentMonth.getFullYear(),
              currentMonth.getMonth() - 1,
              1,
            );
            onMonthChange(newMonth);
          }}
          className="w-5 h-10 min-h-10 left-[33px] top-0 absolute flex items-center justify-center hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700 cursor-pointer"
        >
          <ChevronLeft className="size-4 text-mw-neutral-400" />
        </button>

        <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 text-center">
          <div className="text-xs font-medium leading-4 text-black">
            {monthNames[currentMonth.getMonth()].slice(0, 3)}{" "}
            {currentMonth.getFullYear()}
          </div>
        </div>

        <button
          onClick={() => {
            const newMonth = new Date(
              currentMonth.getFullYear(),
              currentMonth.getMonth() + 1,
              1,
            );
            onMonthChange(newMonth);
          }}
          className="w-5 h-10 min-h-10 right-[33px] top-0 absolute flex items-center justify-center hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700 cursor-pointer"
        >
          <ChevronRight className="size-4 text-mw-neutral-400" />
        </button>

        <button
          onClick={() => {
            const newMonth = new Date(
              currentMonth.getFullYear() + 1,
              currentMonth.getMonth(),
              1,
            );
            onMonthChange(newMonth);
          }}
          className="w-5 h-10 min-h-10 right-[8px] top-0 absolute flex items-center justify-center hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700 cursor-pointer"
        >
          <ChevronsRight className="size-4 text-mw-neutral-400" />
        </button>
      </div>

      {/* Calendar Content */}
      <div className="self-stretch px-3 py-2 bg-white flex flex-col justify-start items-start gap-2.5 overflow-hidden">
        {/* Week days */}
        <div className="self-stretch inline-flex justify-between items-center overflow-hidden">
          {weekDays.map((day) => (
            <div
              key={day}
              className="w-9 h-7 text-center justify-center text-black text-xs font-normal leading-4"
            >
              {day}
            </div>
          ))}
        </div>

        {/* Days Grid */}
        <div className="self-stretch flex flex-col justify-start items-center overflow-hidden">
          {Array.from({ length: Math.ceil(days.length / 7) }).map(
            (_, weekIndex) => (
              <div
                key={weekIndex}
                className="self-stretch px-1.5 py-[3px] inline-flex justify-between items-start overflow-hidden"
              >
                {days
                  .slice(weekIndex * 7, weekIndex * 7 + 7)
                  .map(({ date, isCurrentMonth }, dayIndex) => {
                    const isSelected =
                      isSameDay(date, selectedRange.from) ||
                      isSameDay(date, selectedRange.to);
                    const isRangeStart =
                      (isCurrentMonth || numberOfMonths === 1) &&
                      isSameDay(date, selectedRange.from);
                    const isRangeEnd =
                      (isCurrentMonth || numberOfMonths === 1) &&
                      isSameDay(date, selectedRange.to);
                    const isBetween =
                      selectedRange.from &&
                      selectedRange.to &&
                      !isSelected &&
                      (isCurrentMonth || numberOfMonths === 1) &&
                      isDateBetween(date, selectedRange.from, selectedRange.to);
                    const isDisabled =
                      (minDate && date < minDate) ||
                      (maxDate && date > maxDate);
                    const isToday = isSameDay(date, new Date());

                    return (
                      <div
                        key={dayIndex}
                        className={cn(
                          "relative",
                          isRangeStart && "overflow-hidden",
                          isRangeEnd && "overflow-hidden",
                        )}
                      >
                        {isBetween && (
                          <div className="w-9 h-6 left-[-7px] top-0 absolute bg-mw-primary-50" />
                        )}
                        {isRangeStart && (
                          <div className="w-8 h-6 left-0 top-0 absolute bg-mw-primary-50 rounded-tl-sm rounded-bl-sm" />
                        )}
                        {isRangeEnd && (
                          <div className="w-8 h-6 left-[-8px] top-0 absolute bg-mw-primary-50 rounded-tr-sm rounded-br-sm" />
                        )}
                        <button
                          onClick={() => !isDisabled && onDateSelect(date)}
                          disabled={isDisabled}
                          className={cn(
                            "w-6 h-6 p-px rounded-sm flex justify-center items-center overflow-hidden relative text-center text-xs font-normal leading-4 cursor-pointer",
                            isRangeStart &&
                              "bg-mw-primary-500 rounded-tl-sm rounded-bl-sm text-white",
                            isRangeEnd &&
                              "bg-mw-primary-500 rounded-tr-sm rounded-br-sm text-white",
                            !isSelected &&
                              !isCurrentMonth &&
                              !isBetween &&
                              "text-mw-neutral-500 bg-white",
                            !isSelected && isCurrentMonth && "text-black",
                            isToday &&
                              !isSelected &&
                              "outline -outline-offset-1 outline-mw-primary-500",
                            isDisabled && "opacity-50 cursor-not-allowed",
                            !isDisabled &&
                              !isSelected &&
                              "hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700",
                          )}
                        >
                          {date.getDate()}
                        </button>
                      </div>
                    );
                  })}
              </div>
            ),
          )}
        </div>
      </div>
    </div>
  );
}

export function DateRangePicker({
  value,
  onChange,
  placeholder = "Select Date range",
  disabled = false,
  clearable = true,
  minDate,
  maxDate,
  numberOfMonths = 1,
  className,
  label,
  error,
  helpText,
  presets,
  isActionsVisible = false,
  onBlur,
  id,
}: DateRangePickerProps) {
  const { t: tCommon } = useTranslate(["common"]);
  // The "Next N days" preset list is shared across the whole platform —
  // callers only need to pass `presets` when they want something different.
  const defaultPresets = useMemo(() => createNDaysPresets(tCommon), [tCommon]);
  const resolvedPresets = presets ?? defaultPresets;
  const [isOpen, setIsOpen] = useState(false);
  const [selectedRange, setSelectedRange] = useState<DateRange>(
    value ?? { from: null, to: null },
  );
  const [currentMonth, setCurrentMonthState] = useState(() =>
    normalizeToFirstOfMonth(new Date()),
  );
  const [selectingStart, setSelectingStart] = useState(true);
  const containerRef = useRef<HTMLDivElement>(null);
  const onBlurRef = useRef(onBlur);
  onBlurRef.current = onBlur;

  const setCurrentMonth = useCallback((date: Date) => {
    setCurrentMonthState(normalizeToFirstOfMonth(date));
  }, []);

  const handleClose = useCallback(() => {
    setIsOpen(false);
    onBlurRef.current?.();
  }, []);

  useClickOutside(containerRef, isOpen, handleClose);

  useEffect(() => {
    setSelectedRange(value ?? { from: null, to: null });
  }, [value]);

  const handleToggleOpen = useCallback(() => {
    if (disabled) return;
    if (!isOpen) {
      const from = selectedRange.from ?? selectedRange.to ?? new Date();
      setCurrentMonthState(normalizeToFirstOfMonth(from));
    } else {
      // Closing via the trigger button should notify the same way closing
      // via an outside click does (handleClose) — consumers rely on onBlur
      // firing whenever the picker closes, regardless of how.
      onBlurRef.current?.();
    }
    setIsOpen((prev) => !prev);
  }, [disabled, isOpen, selectedRange.from, selectedRange.to]);

  const handleDateSelect = useCallback(
    (date: Date) => {
      let newRange: DateRange;
      if (selectingStart || !selectedRange.from) {
        newRange = { from: date, to: null };
        setSelectingStart(false);
      } else {
        newRange =
          date < selectedRange.from
            ? { from: date, to: selectedRange.from }
            : { from: selectedRange.from, to: date };
        setSelectingStart(true);
      }
      setSelectedRange(newRange);
      onChange?.(newRange);
      if (numberOfMonths === 1) {
        setCurrentMonthState(normalizeToFirstOfMonth(date));
      }
      if (newRange.from && newRange.to) {
        setIsOpen(false);
      }
    },
    [selectingStart, selectedRange.from, numberOfMonths, onChange],
  );

  const handleClear = useCallback(
    (e?: React.MouseEvent) => {
      e?.stopPropagation();
      const cleared = { from: null, to: null };
      setSelectedRange(cleared);
      onChange?.(cleared);
    },
    [onChange],
  );

  const displayValue = useMemo(() => {
    if (!selectedRange.from && !selectedRange.to) return placeholder;
    if (selectedRange.from && !selectedRange.to) {
      return formatDisplayDate(selectedRange.from.toISOString(), tCommon);
    }
    if (selectedRange.from && selectedRange.to) {
      return `${formatDisplayDate(selectedRange.from.toISOString(), tCommon)} - ${formatDisplayDate(selectedRange.to.toISOString(), tCommon)}`;
    }
    return placeholder;
  }, [selectedRange.from, selectedRange.to, placeholder, tCommon]);

  const handlePresetSelect = useCallback(
    (preset: { label: string; range: DateRange }) => {
      setSelectedRange(preset.range);
      onChange?.(preset.range);
      setIsOpen(false);
    },
    [onChange],
  );

  const setSecondCalendarMonthToPrev = useCallback((month: Date) => {
    setCurrentMonthState(
      normalizeToFirstOfMonth(
        new Date(month.getFullYear(), month.getMonth() - 1, 1),
      ),
    );
  }, []);

  const pickerId = id ?? "date-range-picker";
  return (
    <div id={pickerId}>
      <div ref={containerRef} className={cn("relative", className)}>
        <div className={cn(isOpen && "z-99999")}>
          <div className="relative">
            <div className="">
              <Input
                id={`${pickerId}-trigger`}
                label={label}
                type="text"
                value={displayValue}
                readOnly
                placeholder={placeholder}
                disabled={disabled}
                onClick={handleToggleOpen}
                aria-label={label ?? "Date range"}
                aria-expanded={isOpen}
                aria-haspopup="dialog"
                aria-controls={isOpen ? `${pickerId}-calendar` : undefined}
                error={error}
                helpText={helpText}
                className={cn(
                  "cursor-pointer",
                  disabled && "cursor-not-allowed",
                )}
              />
            </div>
            <div
              id={`${pickerId}-display`}
              className={cn(
                "absolute inset-y-0 right-0 flex items-center pr-3",
                "cursor-pointer",
                label && "pt-6",
                disabled && "cursor-not-allowed",
              )}
              aria-hidden
            >
              {clearable && (selectedRange.from || selectedRange.to) && (
                <button
                  id={`${pickerId}-clear`}
                  type="button"
                  onClick={handleClear}
                  className={cn(
                    "p-1 hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700 rounded shrink-0 cursor-pointer",
                    disabled && "cursor-not-allowed",
                  )}
                >
                  <XCircle className="w-4 h-4 text-mw-neutral-400" />
                </button>
              )}
              <button
                id={`${pickerId}-toggle`}
                type="button"
                onClick={handleToggleOpen}
                className={cn(
                  "p-1 hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700 rounded shrink-0 cursor-pointer",
                  disabled && "cursor-not-allowed",
                )}
              >
                <Calendar className="w-4 h-4 text-mw-neutral-400" />
              </button>
            </div>
          </div>
        </div>

        {isOpen && (
          <div
            id={`${pickerId}-calendar`}
            className="absolute z-50 mt-1 rounded-lg outline -outline-offset-1 outline-mw-neutral-100 inline-flex justify-start items-start overflow-hidden bg-white dark:bg-mw-neutral-800 shadow-lg"
          >
            {resolvedPresets.length > 0 && (
              <div
                id={`${pickerId}-presets`}
                className="self-stretch rounded-tl-lg rounded-bl-lg outline -outline-offset-1 outline-mw-neutral-100 inline-flex flex-col justify-start items-start overflow-hidden"
              >
                <div className="self-stretch px-5 py-3 bg-white rounded-tl-lg border-b border-mw-neutral-100 flex flex-col justify-start items-start gap-2.5 overflow-hidden">
                  <div className="inline-flex justify-start items-center gap-2 overflow-hidden">
                    <div className="flex justify-start items-center gap-2.5">
                      <div className="justify-center text-black text-xs font-medium leading-4">
                        Quick Select
                      </div>
                    </div>
                  </div>
                </div>
                <div className="self-stretch flex-1 px-3.5 py-2 bg-white flex flex-col justify-start items-center gap-2.5 overflow-hidden">
                  <div className="w-24 rounded flex flex-col justify-start items-start">
                    {resolvedPresets.map((preset, index) => (
                      <div
                        key={`preset-${index}`}
                        className="self-stretch flex flex-col justify-center items-start gap-1"
                      >
                        <button
                          id={`${pickerId}-preset-${index}`}
                          onClick={() => handlePresetSelect(preset)}
                          className="self-stretch p-2 inline-flex justify-start items-center gap-2 hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700 rounded cursor-pointer"
                        >
                          <div className="justify-center text-black text-xs font-normal leading-4 line-clamp-1">
                            {preset.label}
                          </div>
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}
            {/* Calendar */}
            <div className="flex">
              <div className="border-r border-mw-neutral-100">
                <CalendarMonth
                  currentMonth={currentMonth}
                  selectedRange={selectedRange}
                  onDateSelect={handleDateSelect}
                  onMonthChange={setCurrentMonth}
                  minDate={minDate}
                  maxDate={maxDate}
                  selectingStart={selectingStart}
                  numberOfMonths={numberOfMonths}
                />
              </div>

              {numberOfMonths === 2 && (
                <div className="rounded-tr-lg rounded-br-lg">
                  <CalendarMonth
                    currentMonth={
                      new Date(
                        currentMonth.getFullYear(),
                        currentMonth.getMonth() + 1,
                        1,
                      )
                    }
                    selectedRange={selectedRange}
                    onDateSelect={handleDateSelect}
                    onMonthChange={setSecondCalendarMonthToPrev}
                    minDate={minDate}
                    maxDate={maxDate}
                    selectingStart={selectingStart}
                    numberOfMonths={numberOfMonths}
                  />
                </div>
              )}
            </div>

            {/* Actions */}
            {isActionsVisible && (
              <div className="flex items-center justify-between p-3 border-t border-mw-neutral-100 dark:border-mw-neutral-700">
                <div className="text-xs text-mw-neutral-500 dark:text-mw-neutral-400">
                  {selectingStart
                    ? tCommon("calendar.selectStartDate")
                    : tCommon("calendar.selectEndDate")}
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => setIsOpen(false)}
                    className="px-3 py-1 text-sm text-mw-neutral-600 dark:text-mw-neutral-400 hover:text-mw-neutral-900 dark:hover:text-white"
                  >
                    {tCommon("buttons.cancel")}
                  </button>
                  <button
                    onClick={() => setIsOpen(false)}
                    disabled={!selectedRange.from}
                    className="px-3 py-1 text-sm bg-mw-primary-500 text-white rounded hover:bg-mw-primary-600 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {tCommon("buttons.apply")}
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export default DateRangePicker;
