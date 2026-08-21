import { AgGridTable } from "@components/ui/AgGridTable/AgGridTable";
import { Button } from "@components/ui/Button";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownTrigger,
} from "@components/ui/Dropdown";
import { useTranslate } from "@tolgee/react";
import { formatDisplayDate } from "@utils/dateUtils";
import type { ColDef } from "ag-grid-community";
import {
  Download,
  Eye,
  MoreHorizontal,
  PackageOpen,
  Trash2,
  UploadIcon,
} from "lucide-react";
import React, { useMemo } from "react";

import { LocationCsvFile } from "../types/location-csv.types";

interface ExistingFilesTabProps {
  files: LocationCsvFile[];
  selectedFileId: string;
  onSelectFile: (fileId: string) => void;
  onDeleteFile: (fileId: string) => void;
  onDownloadFile: (fileId: string) => void;
  onViewFile: (fileId: string) => void;
  currentPage: number;
  pageSize: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  isLoading?: boolean;
  setActiveTab?: (tab: string) => void;
  totalItems?: number;
}

export const ExistingFilesTab: React.FC<ExistingFilesTabProps> = ({
  files,
  selectedFileId,
  onSelectFile,
  onDeleteFile,
  onDownloadFile,
  onViewFile,
  currentPage,
  pageSize,
  onPageChange,

  onPageSizeChange,
  isLoading = false,
  setActiveTab,
  totalItems = 0,
}) => {
  const { t } = useTranslate(["campaigns"]);
  const columnDefs = useMemo<ColDef<LocationCsvFile>[]>(
    () => [
      {
        headerName: t("geofencingExistingFiles.columns.srNo"),
        width: 80,
        valueGetter: (params) => (params.node?.rowIndex ?? 0) + 1,
      },
      {
        colId: "fileName",
        headerName: t("geofencingExistingFiles.columns.fileName"),
        field: "fileName",
        flex: 1,
        minWidth: 120,
      },
      {
        colId: "locationCount",
        headerName: t("geofencingExistingFiles.columns.noOfLocations"),
        width: 140,
        sortable: false,
        field: "locationCount",
      },
      {
        colId: "createdBy",
        headerName: t("geofencingExistingFiles.columns.uploadedBy"),
        field: "createdBy",
        minWidth: 120,
      },
      {
        colId: "createdAt",
        headerName: t("geofencingExistingFiles.columns.uploadedOn"),
        field: "createdAt",
        minWidth: 120,
        valueFormatter: (params) =>
          params.value ? formatDisplayDate(params.value as string) : "",
      },
      {
        colId: "actions",
        headerName: t("geofencingExistingFiles.columns.action"),
        width: 100,
        sortable: false,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        cellRenderer: (params: any) => {
          const fileRow = params.data;
          if (!fileRow) return null;
          return (
            <div className="flex justify-center">
              <Dropdown>
                <DropdownTrigger asChild>
                  <Button variant="ghost" size="iconMd">
                    <MoreHorizontal className="size-5 text-mw-neutral-700 dark:text-mw-neutral-300" />
                  </Button>
                </DropdownTrigger>
                <DropdownContent className="min-w-[150px]" align="right">
                  <DropdownItem onClick={() => onViewFile(fileRow.id)}>
                    <div className="flex items-center gap-2">
                      <Eye className="size-4 text-mw-neutral-700 dark:text-mw-neutral-300" />
                      <span>{t("geofencingExistingFiles.actions.view")}</span>
                    </div>
                  </DropdownItem>
                  <DropdownItem onClick={() => onDownloadFile(fileRow.id)}>
                    <div className="flex items-center gap-2">
                      <Download className="size-4 text-mw-neutral-700 dark:text-mw-neutral-300" />
                      <span>
                        {t("geofencingExistingFiles.actions.download")}
                      </span>
                    </div>
                  </DropdownItem>
                  <DropdownItem onClick={() => onDeleteFile(fileRow.id)}>
                    <div className="flex items-center gap-2 text-mw-error-500">
                      <Trash2 className="size-4" />
                      <span>{t("geofencingExistingFiles.actions.delete")}</span>
                    </div>
                  </DropdownItem>
                </DropdownContent>
              </Dropdown>
            </div>
          );
        },
      },
    ],
    [onDeleteFile, onDownloadFile, onViewFile, t],
  );

  if (isLoading) {
    return (
      <div className="flex flex-col h-[calc(100vh-320px)] py-6">
        <div className="flex-1 min-h-0">
          <AgGridTable<LocationCsvFile>
            rowData={[]}
            columnDefs={columnDefs}
            getRowId={(row) => row.id}
            loading={true}
            emptyMessage={t("geofencingExistingFiles.loadingFiles")}
            height="100%"
            className="h-full"
          />
        </div>
      </div>
    );
  }

  if (files.length === 0) {
    return (
      <div className="py-6 text-center text-mw-neutral-500">
        <div className="flex flex-1 justify-center items-center min-h-[calc(100vh-350px)]">
          <div className="text-center">
            <div className="flex justify-center">
              <PackageOpen className="w-24 h-24 sm:w-18 sm:h-18 text-mw-neutral-400 dark:text-mw-neutral-500" />
            </div>
            <h2 className="text-base font-semibold text-mw-neutral-900 dark:text-white leading-right">
              {t("geofencingExistingFiles.noFilesYet")}
            </h2>
            <p className="text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-400 leading-right">
              {t("geofencingExistingFiles.startUploadingBulk")}
            </p>
            <Button
              variant="outline"
              size="md"
              className="gap-1 mt-4 outline-mw-primary-500 text-mw-primary-500"
              onClick={() => {
                if (setActiveTab) {
                  setActiveTab("upload");
                }
              }}
            >
              <UploadIcon className="size-4" />
              {t("geofencingExistingFiles.uploadCsvXlsx")}
            </Button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-[calc(100vh-280px)]">
      <p className="text-black text-xs mb-2 shrink-0">
        {t("geofencingExistingFiles.autoSelectGps")}
      </p>
      <div className="flex-1 min-h-0 rounded-lg overflow-hidden">
        <AgGridTable<LocationCsvFile>
          rowData={files}
          columnDefs={columnDefs}
          getRowId={(row) => row.id}
          emptyMessage={t("geofencingExistingFiles.noFilesAvailable")}
          height="100%"
          className="h-full"
          rowSelection="single"
          selectedRowIds={selectedFileId ? [selectedFileId] : []}
          onSelectionChange={(ids) => onSelectFile(ids[0] ?? "")}
          serverSidePagination
          serverSideConfig={{
            totalCount: totalItems,
            currentPage,
            pageSize,
            onPageChange: onPageChange,
            onPageSizeChange: onPageSizeChange,
            pageSizeSelector: [10, 25, 50, 100],
          }}
        />
      </div>
    </div>
  );
};
