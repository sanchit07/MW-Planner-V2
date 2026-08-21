/* eslint-disable @typescript-eslint/no-explicit-any */
/**
 * Theme color utilities using CSS variables from tailwind.css
 * Maps theme IDs to CSS variable names and Tailwind classes
 */

/**
 * Maps theme ID to mw-color family name (for CSS variable construction)
 */
export const getThemeColorFamily = (themeId: string): string => {
  const themeColorMap: Record<string, string> = {
    primary: "primary",
    warning: "warning",
    success: "success",
    "purple-warning": "purple-warning",
    "data-blue": "primary",
    "dashboard-green": "success",
    "metrics-gray": "primary",
    "analytics-navy": "primary",
    "report-teal": "info",
  };

  return themeColorMap[themeId] || "primary";
};

/**
 * Gets the CSS variable name for a theme's primary color (500 shade)
 * Returns format: --color-mw-{family}-500
 */
export const getThemePrimaryColorVar = (themeId: string): string => {
  const colorFamily = getThemeColorFamily(themeId);
  return `--color-mw-${colorFamily}-500`;
};

/**
 * Gets the CSS variable name for a theme's secondary color (400 shade)
 */
export const getThemeSecondaryColorVar = (themeId: string): string => {
  const colorFamily = getThemeColorFamily(themeId);
  return `--color-mw-${colorFamily}-400`;
};

/**
 * Gets the CSS variable name for a theme's accent color (300 shade)
 */
export const getThemeAccentColorVar = (themeId: string): string => {
  const colorFamily = getThemeColorFamily(themeId);
  return `--color-mw-${colorFamily}-300`;
};

/**
 * Static color map extracted from tailwind.css
 * Maps CSS variable names to hex values for PPT generation
 */
const CSS_VARIABLE_COLORS: Record<string, string> = {
  // Primary
  "--color-mw-primary-300": "#6898c9",
  "--color-mw-primary-400": "#4a84bf",
  "--color-mw-primary-500": "#2176cc",
  // Secondary
  "--color-mw-secondary-300": "#94cfef",
  "--color-mw-secondary-400": "#80c6ec",
  "--color-mw-secondary-500": "#60b8e7",
  // Success
  "--color-mw-success-300": "#72a876",
  "--color-mw-success-400": "#57975b",
  "--color-mw-success-500": "#2d7d32",
  // Warning
  "--color-mw-warning-300": "#fbc56d",
  "--color-mw-warning-400": "#fab951",
  "--color-mw-warning-500": "#f9a825",
  // Info
  "--color-mw-info-300": "#55afe0",
  "--color-mw-info-400": "#34a0da",
  "--color-mw-info-500": "#0188d1",
  // Purple Warning
  "--color-mw-purple-warning-300": "#b97af8",
  "--color-mw-purple-warning-400": "#9e54f6",
  "--color-mw-purple-warning-500": "#8a38f5",
  // Orange Warning
  "--color-mw-orange-warning-300": "#f9a387",
  "--color-mw-orange-warning-400": "#f7845f",
  "--color-mw-orange-warning-500": "#f57738",
};

/**
 * Gets the hex color value from CSS variable name (for PPT generation)
 * Uses static mapping instead of reading from DOM
 */
export const getCssVariableValue = (varName: string): string => {
  return CSS_VARIABLE_COLORS[varName] || "#2176cc";
};

/**
 * Gets the primary color hex value for a theme (for PPT generation)
 */
export const getThemePrimaryColor = (themeId: string): string => {
  const varName = getThemePrimaryColorVar(themeId);
  return getCssVariableValue(varName);
};

/**
 * Gets the secondary color hex value for a theme (for PPT generation)
 */
export const getThemeSecondaryColor = (themeId: string): string => {
  const varName = getThemeSecondaryColorVar(themeId);
  return getCssVariableValue(varName);
};

/**
 * Gets the accent color hex value for a theme (for PPT generation)
 */
export const getThemeAccentColor = (themeId: string): string => {
  const varName = getThemeAccentColorVar(themeId);
  return getCssVariableValue(varName);
};

/**
 * Gets the Tailwind class name for text color based on theme
 * Returns format: text-mw-{family}-500
 */
export const getThemeTextColorClass = (themeId: string): string => {
  const colorFamily = getThemeColorFamily(themeId);
  return `text-mw-${colorFamily}-500`;
};

/**
 * Gets the Tailwind class name for background color based on theme (50 shade)
 * Returns format: bg-mw-{family}-50
 */
export const getThemeBgColorClass = (themeId: string): string => {
  const colorFamily = getThemeColorFamily(themeId);
  return `bg-mw-${colorFamily}-50`;
};

/**
 * Converts hex color to RGB format (without #)
 * Used for PowerPoint color format
 */
export const hexToRgbString = (hex: string): string => {
  // Remove # if present
  const cleanHex = hex.replace("#", "");

  // If already in correct format (6 chars), return uppercase
  if (cleanHex.length === 6) {
    return cleanHex.toUpperCase();
  }

  // Handle 3-char hex
  if (cleanHex.length === 3) {
    return cleanHex
      .split("")
      .map((char) => char + char)
      .join("")
      .toUpperCase();
  }

  return cleanHex.toUpperCase();
};

export const initializeThemeColors = (
  defaultColors?: any,
  theme?: any,
): {
  primary: string;
  secondary: string;
  lightGray: string;
} => {
  if (defaultColors) {
    return {
      primary: defaultColors.primary || "FF4472C4",
      secondary: defaultColors.secondary || "FF2E75B6",
      lightGray: defaultColors.lightGray || "FFF2F2F2",
    };
  }

  if (theme) {
    const primaryHex = getCssVariableValue(theme.colors.primary);
    const secondaryHex = getCssVariableValue(theme.colors.secondary);
    return {
      primary: hexToArgb(primaryHex),
      secondary: hexToArgb(secondaryHex),
      lightGray: "FFF2F2F2",
    };
  }

  return {
    primary: "FF4472C4",
    secondary: "FF2E75B6",
    lightGray: "FFF2F2F2",
  };
};

/**
 * Convert hex color to ARGB format
 */
const hexToArgb = (hex: string): string => {
  const cleanHex = hex.replace("#", "");
  if (cleanHex.length === 3) {
    const expanded = cleanHex
      .split("")
      .map((char) => char + char)
      .join("");
    return `FF${expanded.toUpperCase()}`;
  }
  return `FF${cleanHex.toUpperCase()}`;
};
