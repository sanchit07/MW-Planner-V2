import { Button } from "@components/ui/Button";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownTrigger,
} from "@components/ui/Dropdown";
import { useTranslate } from "@tolgee/react";
import { ChevronDown, Palette } from "lucide-react";
import React from "react";

import { THEMES_COLORS } from "./constants";

interface PresentationThemeSelectorProps {
  selectedTheme: string;
  onThemeChange: (themeId: string) => void;
}

const PresentationThemeSelector: React.FC<PresentationThemeSelectorProps> = ({
  selectedTheme,
  onThemeChange,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);

  const availableThemes = THEMES_COLORS;
  const theme =
    availableThemes.find((t) => t.id === selectedTheme) || availableThemes[0];

  return (
    <Dropdown>
      <DropdownTrigger asChild>
        <Button variant="outline" size="sm" className="gap-2">
          <p className="flex items-center gap-2">
            <Palette className="size-4" />{" "}
            {tCampaigns(`mediaPlan.theme.${theme.id}`)}
          </p>
          <ChevronDown className="size-4" />
        </Button>
      </DropdownTrigger>
      <DropdownContent align="right">
        {availableThemes.map((theme) => (
          <DropdownItem
            key={theme.id}
            value={theme.id}
            onClick={() => onThemeChange(theme.id)}
          >
            {tCampaigns(`mediaPlan.theme.${theme.id}`)}
          </DropdownItem>
        ))}
      </DropdownContent>
    </Dropdown>
  );
};

export default PresentationThemeSelector;
