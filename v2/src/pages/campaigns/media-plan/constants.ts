import {
  getThemePrimaryColorVar,
  getThemeSecondaryColorVar,
  getThemeAccentColorVar,
} from "@utils/themeColors";

import { PresentationTheme } from "./types";

type ThemeKey = "primary" | "warning" | "success" | "purple-warning";

function createPresentationTheme(
  id: ThemeKey,
  name: string,
): PresentationTheme {
  return {
    id,
    name,
    colors: {
      primary: getThemePrimaryColorVar(id),
      secondary: getThemeSecondaryColorVar(id),
      accent: getThemeAccentColorVar(id),
    },
  };
}

const THEME_DEFINITIONS: { id: ThemeKey; name: string }[] = [
  { id: "primary", name: "Modern Executive" },
  { id: "warning", name: "Vibrant Orange" },
  { id: "success", name: "Professional Green" },
  { id: "purple-warning", name: "Elegant Purple" },
];

export const THEMES_COLORS: PresentationTheme[] = THEME_DEFINITIONS.map(
  ({ id, name }) => createPresentationTheme(id, name),
);
