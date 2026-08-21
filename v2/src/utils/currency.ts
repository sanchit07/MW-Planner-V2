/**
 * Currency formatting utilities
 */

/**
 * Format a number as currency with locale formatting and decimal places
 * @param value - The numeric value to format
 * @param currency - The currency code (e.g., 'USD', 'EUR', 'GBP')
 * @param decimals - Number of decimal places (default: 2)
 * @returns Formatted currency string with locale formatting and decimals or "-" if value is undefined/null
 */
export const formatCurrencyWithLocale = (
  value: number | undefined | null,
  currency: string = "",
  decimals: number = 2,
): string => {
  if (value === undefined || value === null) return "-";
  return `${currency} ${value.toLocaleString(undefined, {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })}`;
};
