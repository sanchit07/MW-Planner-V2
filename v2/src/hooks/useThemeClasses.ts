import { PresentationTheme } from "@pages/campaigns/media-plan/types";
import {
  getThemeTextColorClass,
  getThemeBgColorClass,
} from "@utils/themeColors";
import { useMemo } from "react";

/**
 * Hook to get theme-based Tailwind classes
 */
export const useThemeClasses = (theme: PresentationTheme) => {
  return useMemo(
    () => ({
      textColor: getThemeTextColorClass(theme.id),
      bgColor: getThemeBgColorClass(theme.id),
    }),
    [theme.id],
  );
};
