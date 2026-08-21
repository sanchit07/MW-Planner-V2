import { Button } from "@components/ui/Button";
import { CardHeader } from "@components/ui/card";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownTrigger,
} from "@components/ui/Dropdown";
import { useTranslate } from "@tolgee/react";
import {
  ChevronDown,
  Download,
  Loader2,
  Presentation,
  Share2,
} from "lucide-react";
import React from "react";

import PresentationThemeSelector from "./PresentationThemeSelector";
import { ViewType } from "./types";

interface MediaPlanHeaderProps {
  viewType: ViewType;
  selectedTheme: string;
  onViewTypeChange: (viewType: ViewType) => void;
  onThemeChange: (themeId: string) => void;
  onDownload: () => void;
  onShare: () => void;
  isDownloading?: boolean;
  /** True while the underlying media plan data is still loading — kept
   * disabled so downloads can't start against a plan that isn't fully
   * loaded yet. */
  isDataLoading?: boolean;
}

const MediaPlanHeader: React.FC<MediaPlanHeaderProps> = ({
  viewType,
  selectedTheme,
  onViewTypeChange,
  onThemeChange,
  onDownload,
  onShare,
  isDownloading = false,
  isDataLoading = false,
}) => {
  const { t } = useTranslate(["campaigns"]);

  return (
    <CardHeader className="mb-4 p-4">
      <div className="inline-flex">
        <div className="flex items-center justify-start gap-4">
          <Dropdown>
            <DropdownTrigger asChild>
              <Button variant="outline" size="sm" className="gap-2">
                <p className="flex items-center gap-2">
                  <Presentation className="size-4" />{" "}
                  {t(`media_plan.view_type.${viewType}`)}
                </p>
                <ChevronDown className="size-4" />
              </Button>
            </DropdownTrigger>
            <DropdownContent align="right">
              <DropdownItem onClick={() => onViewTypeChange("presentation")}>
                {t("media_plan.view_type.presentation")}
              </DropdownItem>
              <DropdownItem onClick={() => onViewTypeChange("analytics")}>
                {t("media_plan.view_type.analytics")}
              </DropdownItem>
            </DropdownContent>
          </Dropdown>

          {viewType === "presentation" && (
            <PresentationThemeSelector
              selectedTheme={selectedTheme}
              onThemeChange={onThemeChange}
            />
          )}
        </div>
        <div className="flex-1 flex items-end justify-end gap-4">
          <div className="flex gap-4">
            <Button
              variant="outline"
              onClick={onDownload}
              disabled={isDownloading || isDataLoading}
            >
              {isDownloading || isDataLoading ? (
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
              ) : (
                <Download className="h-4 w-4 mr-2" />
              )}
              {isDownloading
                ? t("media_plan.actions.processing")
                : isDataLoading
                  ? t("media_plan.actions.preparing")
                  : viewType === "presentation"
                    ? t("media_plan.actions.download_ppt")
                    : t("media_plan.actions.download_excel")}
            </Button>
            <Button variant="outline" size="sm" onClick={onShare}>
              <Share2 className="size-4" />
            </Button>
          </div>
        </div>
      </div>
    </CardHeader>
  );
};

export default MediaPlanHeader;
