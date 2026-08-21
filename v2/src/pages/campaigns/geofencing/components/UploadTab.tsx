import { Card, CardContent, CardFooter } from "@components/ui/card";
import FileUpload from "@components/ui/FileUpload";
import { useTranslate } from "@tolgee/react";
import { CheckCircle } from "lucide-react";
import React from "react";

import { DataProcessingResults } from "./DataProcessingResults";
import { LocationCsvVerifyResult } from "../types/location-csv.types";

interface UploadTabProps {
  uploadedFile: File | null;
  onFileChange: (file: File | null) => void;
  uploadResponse: {
    totalRow: number;
    validLocation: number;
    invalidLocation: number;
    duplicateLocation: number;
    logs: LocationCsvVerifyResult[];
  } | null;
  uploadSuccess: {
    totalValidLocation: number;
  } | null;
  onViewLog: () => void;
}

export const UploadTab: React.FC<UploadTabProps> = ({
  uploadedFile,
  onFileChange,
  uploadResponse,
  uploadSuccess,
  onViewLog,
}) => {
  const { t } = useTranslate(["campaigns"]);

  return (
    <div className="space-y-6 py-3">
      {/* File Upload Area */}
      <FileUpload
        label=""
        placeholder={t("geofencingDrawer.uploadTab.placeholder")}
        acceptedFormats="CSV"
        accept="text/csv"
        maxSize={3 * 1024 * 1024}
        maxSizeLabel="3MB"
        showProgress={true}
        file={uploadedFile}
        onChange={onFileChange}
      />

      {/* Data Processing Results */}
      {uploadResponse && (
        <Card>
          <CardContent className="pt-4">
            <DataProcessingResults
              uploadResponse={uploadResponse}
              onViewLog={onViewLog}
            />
          </CardContent>
          <CardFooter className="pt-0">
            {uploadSuccess && (
              <div className="inline-flex justify-start items-center gap-1">
                <CheckCircle className="size-5 relative overflow-hidden text-mw-success-500" />
                <p className="text-mw-success-500 text-sm font-medium leading-4">
                  {t("geofencingDrawer.uploadTab.allProcessed", {
                    count: uploadSuccess.totalValidLocation,
                  })}
                </p>
              </div>
            )}
          </CardFooter>
        </Card>
      )}
    </div>
  );
};
