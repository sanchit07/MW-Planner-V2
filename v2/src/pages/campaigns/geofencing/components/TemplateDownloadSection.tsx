import { Button } from "@components/ui/Button";
import { useTranslate } from "@tolgee/react";
import { AlertCircle, Download } from "lucide-react";
import React from "react";

interface TemplateDownloadSectionProps {
  onDownloadTemplate: () => void;
}

export const TemplateDownloadSection: React.FC<
  TemplateDownloadSectionProps
> = ({ onDownloadTemplate }) => {
  const { t } = useTranslate("campaigns");

  return (
    <div className="bg-mw-info-50 dark:bg-mw-primary-900/20 rounded-lg p-3 flex items-center mb-4">
      <div className="flex items-start gap-3 flex-1">
        <AlertCircle className="text-mw-info-500 dark:text-mw-info-400 shrink-0 size-5" />
        <div className="flex-1">
          <div className="text-sm font-medium text-mw-info-700 dark:text-mw-info-100 mb-1">
            {t("targeting.geofencing.need_template")}
          </div>
          <div className="text-sm text-mw-info-600 dark:text-mw-info-300">
            {t("targeting.geofencing.download_template_desc")}
          </div>
        </div>
      </div>
      <Button variant="primary" size="sm" onClick={onDownloadTemplate}>
        <Download className="w-4 h-4 mr-2" />
        {t("targeting.geofencing.download_template")}
      </Button>
    </div>
  );
};
