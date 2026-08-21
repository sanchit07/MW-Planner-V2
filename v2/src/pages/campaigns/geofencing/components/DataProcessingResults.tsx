import { Badge } from "@components/ui/Badge";
import { Button } from "@components/ui/Button";
import { Card } from "@components/ui/card";
import { useTranslate } from "@tolgee/react";
import { Copy, Info, TriangleAlert } from "lucide-react";
import React, { useMemo } from "react";

import { LocationCsvVerifyResult } from "../types/location-csv.types";

interface DataProcessingResultsProps {
  uploadResponse: {
    totalRow: number;
    validLocation: number;
    invalidLocation: number;
    duplicateLocation: number;
    logs: LocationCsvVerifyResult[];
  };
  onViewLog: () => void;
}

export const DataProcessingResults: React.FC<DataProcessingResultsProps> = ({
  uploadResponse,
  onViewLog,
}) => {
  const { t } = useTranslate(["campaigns"]);

  // Calculate success rate and badge variant
  const { successRate, variant } = useMemo(() => {
    if (uploadResponse.totalRow === 0) {
      return { successRate: 0, variant: "destructive" as const };
    }

    const rate = (uploadResponse.validLocation / uploadResponse.totalRow) * 100;

    if (rate === 100) {
      return { successRate: rate, variant: "success" as const };
    } else if (rate >= 70) {
      return { successRate: rate, variant: "warning" as const };
    } else {
      return { successRate: rate, variant: "destructive" as const };
    }
  }, [uploadResponse]);

  return (
    <>
      <div className="self-stretch flex justify-between items-center gap-4 mb-4">
        <div className="flex-1">
          <h3 className="text-lg font-semibold">
            {t("geofencingDrawer.dataProcessing.title")}
          </h3>
        </div>
        <Badge className="text-center" variant={variant} size="md">
          {t("geofencingDrawer.dataProcessing.successRate", {
            rate: successRate.toFixed(0),
          })}
        </Badge>
      </div>

      <div className="grid grid-cols-3 gap-4">
        <Card className="p-4 bg-mw-primary-50!">
          <div className="flex flex-col justify-start items-start gap-2">
            <p className="text-sm font-semibold leading-4">
              {t("geofencingDrawer.dataProcessing.totalRows")}
            </p>
            <p className="text-mw-primary-500 text-lg font-semibold leading-6">
              {uploadResponse.totalRow}
            </p>
          </div>
        </Card>
        <Card className="p-4 bg-mw-success-50!">
          <div className="flex flex-col justify-start items-start gap-2">
            <p className="text-sm font-semibold leading-4">
              {t("geofencingDrawer.dataProcessing.valid")}
            </p>
            <p className="text-mw-success-500 text-lg font-semibold leading-6">
              {uploadResponse.validLocation}
            </p>
          </div>
        </Card>
        <Card className="p-4 bg-mw-error-50!">
          <div className="flex flex-col justify-start items-start gap-2">
            <p className="text-sm font-semibold leading-4">
              {t("geofencingDrawer.dataProcessing.invalid")}
            </p>
            <p className="text-mw-error-500 text-lg font-semibold leading-6">
              {uploadResponse.invalidLocation}
            </p>
          </div>
        </Card>
      </div>

      <div className="pt-4 space-y-4">
        <div className="bg-mw-neutral-50 p-2 rounded gap-8">
          <div className="inline-flex justify-start items-center gap-1">
            <Info className="size-4 text-mw-neutral-500" />
            <div className="inline-flex flex-col gap-1">
              <p className="text-neutral-500 text-sm font-medium leading-4">
                {t("geofencingDrawer.dataProcessing.note")}
              </p>
              <p className="text-mw-neutral-500 text-sm font-normal leading-4">
                {t("geofencingDrawer.dataProcessing.noteText")}
              </p>
            </div>
          </div>
        </div>

        {uploadResponse.validLocation > 0 && (
          <div className="flex justify-start items-start gap-1">
            <div className="inline-flex justify-start items-center gap-1">
              <Info className="size-4 text-mw-success-500" />
              <div className="justify-start text-mw-success-500 text-sm font-medium leading-4">
                {t("geofencingDrawer.dataProcessing.locationsVerified", {
                  count: uploadResponse.validLocation,
                })}
              </div>
            </div>
          </div>
        )}

        {uploadResponse.invalidLocation > 0 && (
          <div className="flex justify-start items-start gap-1">
            <div className="inline-flex justify-start items-center gap-1">
              <TriangleAlert className="size-4 text-mw-error-500" />
              <div className="justify-start text-mw-error-500 text-sm font-medium leading-4">
                {t("geofencingDrawer.dataProcessing.invalidLocations", {
                  count: uploadResponse.invalidLocation,
                })}
              </div>
            </div>
          </div>
        )}

        {uploadResponse.duplicateLocation > 0 && (
          <div className="flex justify-start items-start gap-1">
            <div className="inline-flex justify-start items-center gap-1">
              <Copy className="size-4 text-mw-neutral-500" />
              <div className="justify-start text-mw-neutral-500 text-sm font-medium leading-4">
                {t("geofencingDrawer.dataProcessing.duplicateLocations", {
                  count: uploadResponse.duplicateLocation,
                })}
              </div>
            </div>
          </div>
        )}
      </div>

      <div className="flex justify-end items-end pt-4">
        <Button
          variant="primary"
          onClick={onViewLog}
          disabled={uploadResponse.validLocation === uploadResponse.totalRow}
        >
          {t("geofencingDrawer.dataProcessing.viewLogs")}
        </Button>
      </div>
    </>
  );
};
