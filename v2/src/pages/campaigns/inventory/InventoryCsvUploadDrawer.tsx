import { AgGridTable } from "@components/ui/AgGridTable/AgGridTable";
import { Badge } from "@components/ui/Badge";
import { Button } from "@components/ui/Button";
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@components/ui/card";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownTrigger,
} from "@components/ui/Dropdown";
import FileUpload from "@components/ui/FileUpload";
import Modal from "@components/ui/Modal";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@components/ui/Tabs";
import { useAnnounce } from "@hooks/useAnnounce";
import {
  useVerifyInventoryCsvMutation,
  useUploadInventoryCsvMutation,
  useGetInventoryCsvFilesQuery,
  useDeleteInventoryCsvFileMutation,
  useDownloadInventoryCsvFileMutation,
  useUseInventoryCsvFileMutation,
} from "@services/inventory/inventorySlice";
import { useTranslate } from "@tolgee/react";
import { formatDisplayDate } from "@utils/dateUtils";
import type { ColDef } from "ag-grid-community";
import {
  AlertCircle,
  CheckCircle,
  Download,
  Eye,
  Info,
  MoreHorizontal,
  PackageOpen,
  TriangleAlert,
  Trash2,
  UploadIcon,
  Copy,
} from "lucide-react";
import React, { useState, useMemo, useCallback } from "react";

import { ViewFileInventoryDrawer } from "./ViewFileInventoryDrawer";
import { ViewLogDrawer } from "./ViewLogDrawer";
import { CampaignCreateResponse } from "../../../types/campaign.types";
import {
  InventoryCsvVerifyResult,
  InventoryCsvFile,
} from "../../../types/inventory.types";

export interface InventoryData extends Record<string, unknown> {
  id?: string | number; // Add id for Table component
  inventoryId: string;
  inventoryName: string;
  format: string;
  location: string;
  mediaOwner: string;
  cpm: number;
  isValid: boolean;
  errors: string[];
  included: boolean;
}

// Grouped location data for display
export interface LocationGroupData extends Record<string, unknown> {
  id: string;
  location: string;
  inventories: number; // Count of inventories for this location
  inventoryItems: InventoryData[]; // Original inventory items
  hasErrors: boolean;
}

interface InventoryCsvUploadDrawerProps {
  isOpen: boolean;
  onClose: (isFromCloseButton?: boolean) => void;
  campaignId?: string;
  campaignData: CampaignCreateResponse | null;
  onImport?: (
    inventories: InventoryData[],
  ) => Promise<{ success: boolean; errors?: string[] }>;
}

export const InventoryCsvUploadDrawer: React.FC<
  InventoryCsvUploadDrawerProps
> = ({ isOpen, onClose, campaignId, onImport: _onImport, campaignData }) => {
  const { t: tCommon } = useTranslate(["common"]);
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { showError, showSuccess } = useAnnounce();
  const [verifyCsv] = useVerifyInventoryCsvMutation();
  const [uploadCsv] = useUploadInventoryCsvMutation();
  const [deleteCsvFile] = useDeleteInventoryCsvFileMutation();
  const [downloadCsvFile] = useDownloadInventoryCsvFileMutation();
  const [inventoryCsvFile] = useUseInventoryCsvFileMutation();

  const [activeTab, setActiveTab] = useState("upload");
  const [isViewLogDrawerOpen, setIsViewLogDrawerOpen] = useState(false);
  const [isViewFileInventoryDrawerOpen, setIsViewFileInventoryDrawerOpen] =
    useState(false);
  const [selectedFileId, setSelectedFileId] = useState<string>("");
  const [selectedFileName, setSelectedFileName] = useState<string>("");
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [fileToDelete, setFileToDelete] = useState<{
    id: string;
    fileName: string;
  } | null>(null);

  // Pagination state
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Selected file ID for radio button
  const [selectedFileIdForUse, setSelectedFileIdForUse] = useState<string>("");

  // Fetch CSV files
  const {
    data: csvFilesData,
    isLoading: isLoadingCsvFiles,
    refetch: refetchCsvFiles,
  } = useGetInventoryCsvFilesQuery(
    {
      params: {
        countryName: campaignData?.countryId || "",
        page: currentPage - 1,
        size: pageSize,
        sortBy: "uploadedOn",
        sortDir: "desc",
      },
    },
    {
      skip: !campaignId || !isOpen || activeTab !== "existing",
    },
  );

  // Store uploaded file reference
  const [uploadedFile, setUploadedFile] = useState<File | null>(null);

  // Success message state after upload
  const [uploadSuccess, setUploadSuccess] = useState<{
    totalValidInventory: number;
  } | null>(null);

  // CSV upload response state
  const [uploadResponse, setUploadResponse] = useState<{
    totalRow: number;
    validInventory: number;
    invalidInventory: number;
    duplicateInventory: number;
    logs: InventoryCsvVerifyResult[];
  } | null>(null);

  const handleDownloadTemplate = () => {
    const csvContent = `inventory_id`;

    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const link = document.createElement("a");
    const url = URL.createObjectURL(blob);
    link.setAttribute("href", url);
    link.setAttribute("download", "inventory_template.csv");
    link.style.visibility = "hidden";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  // Calculate success rate and badge variant
  const getSuccessRateAndVariant = (
    totalRow: number,
    validInventory: number,
    _invalidInventory: number,
  ): {
    successRate: number;
    variant: "success" | "warning" | "destructive";
  } => {
    if (totalRow === 0) {
      return { successRate: 0, variant: "destructive" };
    }

    const successRate = (validInventory / totalRow) * 100;

    if (successRate === 100) {
      return { successRate, variant: "success" };
    } else if (successRate >= 70) {
      return { successRate, variant: "warning" };
    } else {
      return { successRate, variant: "destructive" };
    }
  };

  // Handle file upload (verification)
  const handleFileChange = async (file: File | null) => {
    try {
      if (file && campaignId && campaignData) {
        // Store the file for later upload
        setUploadedFile(file);
        setUploadSuccess(null); // Clear previous success message

        const country = campaignData.countryId;
        const result = await verifyCsv({ campaignId, file, country }).unwrap();

        if (result.data) {
          const results = result.data.results || [];
          const totalRow = results.length;

          // Filter results by type
          const validInventory = results.filter(
            (item) => item.type === "VALID",
          ).length;
          const invalidInventory = results.filter(
            (item) => item.type === "INVALID",
          ).length;
          const duplicateInventory = results.filter(
            (item) => item.type === "DUPLICATE",
          ).length;

          // Filter logs to show only invalid and duplicate entries
          const allLogs: InventoryCsvVerifyResult[] = result.data.results || [];
          const filteredLogs = allLogs.filter(
            (log) => log.type === "INVALID" || log.type === "DUPLICATE",
          );

          setUploadResponse({
            totalRow,
            validInventory,
            invalidInventory,
            duplicateInventory,
            logs: filteredLogs,
          });
        }
      } else if (file && !campaignId) {
        showError(tCampaigns("inventoryCsvUpload.errors.campaignIdRequired"));
      } else if (!file) {
        // Clear file and response when file is removed
        setUploadedFile(null);
        setUploadResponse(null);
        setUploadSuccess(null);
      }
    } catch (error) {
      console.error("Error uploading file:", error);
      showError(tCampaigns("inventoryCsvUpload.errors.failedToUpload"));
      setUploadedFile(null);
    }
  };

  // Handle import button click (for upload tab)
  const handleImport = async () => {
    if (!uploadedFile || !campaignId || !campaignData) {
      showError(tCampaigns("inventoryCsvUpload.errors.uploadAndVerifyFirst"));
      return;
    }

    try {
      const country = campaignData.countryId;
      const result = await uploadCsv({
        campaignId,
        file: uploadedFile,
        country,
      }).unwrap();

      if (result.success) {
        const totalValidInventory = uploadResponse?.validInventory ?? 0;

        setUploadSuccess({ totalValidInventory });

        showSuccess(tCampaigns("inventoryCsvUpload.success.uploaded"));
        handleClose(true);
      }
    } catch (error) {
      console.error("Error uploading CSV:", error);
    }
  };

  // Handle use file button click (for existing files tab)
  const handleUseFile = async () => {
    if (!selectedFileIdForUse) {
      showError(tCampaigns("inventoryCsvUpload.errors.selectFileToUse"));
      return;
    }

    if (!campaignId) {
      showError(
        tCampaigns("inventoryCsvUpload.errors.campaignIdRequiredForUse"),
      );
      return;
    }

    try {
      const result = await inventoryCsvFile({
        fileId: selectedFileIdForUse,
        campaignId,
      }).unwrap();

      if (result.success) {
        showSuccess(tCampaigns("inventoryCsvUpload.success.fileUsed"));
        setSelectedFileIdForUse("");
        refetchCsvFiles();
        onClose();
      }
    } catch (error) {
      console.error("Error using CSV file:", error);
    }
  };

  const handleClose = (isFromCloseButton?: boolean) => {
    setActiveTab("upload");
    setUploadResponse(null);
    setUploadedFile(null);
    setUploadSuccess(null);
    setCurrentPage(1);
    setPageSize(10);
    setIsViewFileInventoryDrawerOpen(false);
    setSelectedFileId("");
    setSelectedFileName("");
    setIsDeleteModalOpen(false);
    setFileToDelete(null);
    setSelectedFileIdForUse("");
    onClose(isFromCloseButton);
  };

  const handleOpenViewLog = () => {
    setIsViewLogDrawerOpen(true);
  };

  const handleCloseViewLog = () => {
    setIsViewLogDrawerOpen(false);
  };

  const handleBackFromViewLog = () => {
    setIsViewLogDrawerOpen(false);
  };

  const handleCloseViewFileInventory = () => {
    setIsViewFileInventoryDrawerOpen(false);
    setSelectedFileId("");
    setSelectedFileName("");
  };

  const handleBackFromViewFileInventory = () => {
    setIsViewFileInventoryDrawerOpen(false);
    setSelectedFileId("");
    setSelectedFileName("");
  };

  // Handle delete CSV file - show confirmation modal
  const handleDeleteFile = useCallback(
    (fileId: string) => {
      // Find the file to get its name
      const file = csvFilesData?.data?.content?.find((f) => f.id === fileId);
      if (file) {
        setFileToDelete({ id: fileId, fileName: file.fileName });
        setIsDeleteModalOpen(true);
      }
    },
    [csvFilesData?.data?.content],
  );

  // Handle confirm delete - actually delete the file
  const handleConfirmDelete = useCallback(async () => {
    if (!fileToDelete) return;

    try {
      await deleteCsvFile({ fileId: fileToDelete.id }).unwrap();
      showSuccess(tCampaigns("inventoryCsvUpload.success.deleted"));
      refetchCsvFiles();
      setIsDeleteModalOpen(false);
      setFileToDelete(null);
    } catch (error) {
      console.error("Error deleting file:", error);
      showError(tCampaigns("inventoryCsvUpload.errors.failedToDelete"));
    }
  }, [
    fileToDelete,
    deleteCsvFile,
    showSuccess,
    showError,
    refetchCsvFiles,
    tCampaigns,
  ]);

  // Handle cancel delete - close modal
  const handleCancelDelete = useCallback(() => {
    setIsDeleteModalOpen(false);
    setFileToDelete(null);
  }, []);

  // Handle view file
  const handleViewFile = useCallback(
    (fileId: string) => {
      // Find the file to get its name
      const file = csvFilesData?.data?.content?.find((f) => f.id === fileId);
      if (file) {
        setSelectedFileId(fileId);
        setSelectedFileName(file.fileName);
        setIsViewFileInventoryDrawerOpen(true);
      }
    },
    [csvFilesData?.data?.content],
  );

  // Handle download file
  const handleDownloadFile = useCallback(
    async (fileId: string) => {
      try {
        // Find the file to get its name
        const file = csvFilesData?.data?.content?.find((f) => f.id === fileId);
        const fileName = file?.fileName || `inventory_file_${fileId}.csv`;

        // Download the file
        const blob = await downloadCsvFile({ fileId }).unwrap();

        // Create download link
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = fileName;
        link.style.visibility = "hidden";
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);

        showSuccess(tCampaigns("inventoryCsvUpload.success.downloaded"));
      } catch (error) {
        console.error("Error downloading file:", error);
        showError(tCampaigns("inventoryCsvUpload.errors.failedToDownload"));
      }
    },
    [
      downloadCsvFile,
      csvFilesData?.data?.content,
      showSuccess,
      showError,
      tCampaigns,
    ],
  );

  const csvFileColumnDefs = useMemo<ColDef<InventoryCsvFile>[]>(
    () => [
      {
        headerName: tCampaigns("inventoryCsvUpload.columns.srNo"),
        width: 80,
        valueGetter: (params) => (params.node?.rowIndex ?? 0) + 1,
      },
      {
        colId: "fileName",
        headerName: tCampaigns("inventoryCsvUpload.columns.fileName"),
        field: "fileName",
        flex: 1,
        minWidth: 120,
      },
      {
        colId: "inventoryCount",
        headerName: tCampaigns("inventoryCsvUpload.columns.noOfInventories"),
        width: 160,
        sortable: false,
        field: "inventoryCount",
      },
      {
        colId: "createdBy",
        headerName: tCampaigns("inventoryCsvUpload.columns.uploadedBy"),
        field: "createdBy",
        minWidth: 120,
      },
      {
        colId: "createdAt",
        headerName: tCampaigns("inventoryCsvUpload.columns.uploadedOn"),
        field: "createdAt",
        minWidth: 120,
        valueFormatter: (params) =>
          params.value ? formatDisplayDate(params.value as string) : "",
      },
      {
        colId: "actions",
        headerName: tCampaigns("inventoryCsvUpload.columns.action"),
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
                  <DropdownItem onClick={() => handleViewFile(fileRow.id)}>
                    <div className="flex items-center gap-2">
                      <Eye className="size-4 text-mw-neutral-700 dark:text-mw-neutral-300" />
                      <span>
                        {tCampaigns("inventoryCsvUpload.actions.view")}
                      </span>
                    </div>
                  </DropdownItem>
                  <DropdownItem onClick={() => handleDownloadFile(fileRow.id)}>
                    <div className="flex items-center gap-2">
                      <Download className="size-4 text-mw-neutral-700 dark:text-mw-neutral-300" />
                      <span>
                        {tCampaigns("inventoryCsvUpload.actions.download")}
                      </span>
                    </div>
                  </DropdownItem>
                  <DropdownItem onClick={() => handleDeleteFile(fileRow.id)}>
                    <div className="flex items-center gap-2 text-mw-error-500">
                      <Trash2 className="size-4" />
                      <span>
                        {tCampaigns("inventoryCsvUpload.actions.delete")}
                      </span>
                    </div>
                  </DropdownItem>
                </DropdownContent>
              </Dropdown>
            </div>
          );
        },
      },
    ],
    [handleDeleteFile, handleDownloadFile, handleViewFile, tCampaigns],
  );

  const tableData = useMemo(() => {
    if (!csvFilesData?.data?.content) return [];
    return csvFilesData.data.content;
  }, [csvFilesData]);

  // Handle page change
  const handlePageChange = (page: number) => {
    setCurrentPage(page);
  };

  // Handle page size change
  const handlePageSizeChange = (size: number) => {
    setPageSize(size);
    setCurrentPage(1);
  };

  // Calculate pagination values
  const totalItems = csvFilesData?.data?.totalElements || 0;

  return (
    <>
      <ModalDrawer
        isOpen={
          isOpen && !isViewLogDrawerOpen && !isViewFileInventoryDrawerOpen
        }
        onClose={() => handleClose(true)}
        title={tCampaigns("inventoryCsvUpload.title")}
        size="custom"
        customWidth="60vw"
        footer={
          <div className="flex gap-3 justify-end">
            <Button variant="outline" onClick={() => handleClose(true)}>
              {tCommon("buttons.cancel")}
            </Button>
            {activeTab === "upload" && (
              <Button
                variant="primary"
                disabled={
                  !uploadedFile ||
                  !uploadResponse ||
                  uploadResponse.validInventory === 0
                }
                onClick={handleImport}
              >
                Import
              </Button>
            )}
            {activeTab === "existing" && (
              <Button
                variant="primary"
                onClick={handleUseFile}
                disabled={!selectedFileIdForUse}
              >
                Use File
              </Button>
            )}
          </div>
        }
      >
        {/* Template Download Info - Above Tabs */}
        <div className="bg-mw-info-50 dark:bg-mw-primary-900/20 rounded-lg p-3 flex items-center mb-4">
          <div className="flex items-start gap-3 flex-1">
            <AlertCircle className="text-mw-info-500 dark:text-mw-info-400 shrink-0 size-5" />
            <div className="flex-1">
              <div className="text-sm font-medium text-mw-info-700 dark:text-mw-info-100 mb-1">
                Need a template?
              </div>
              <div className="text-sm text-mw-info-600 dark:text-mw-info-300">
                Download our sample CSV template to get started
              </div>
            </div>
          </div>
          <Button variant="primary" size="sm" onClick={handleDownloadTemplate}>
            <Download className="w-4 h-4 mr-2" />
            Download Template
          </Button>
        </div>

        <Tabs value={activeTab} onValueChange={setActiveTab}>
          <TabsList>
            <TabsTrigger value="existing">
              {tCampaigns("inventoryCsvUpload.tabs.existingFiles")}
            </TabsTrigger>
            <TabsTrigger value="upload">
              {tCampaigns("inventoryCsvUpload.tabs.uploadNew")}
            </TabsTrigger>
          </TabsList>

          <TabsContent value="existing">
            {isLoadingCsvFiles ? (
              <div className="py-6">
                <AgGridTable<InventoryCsvFile>
                  rowData={[]}
                  columnDefs={csvFileColumnDefs}
                  getRowId={(row) => row.id}
                  loading={true}
                  emptyMessage={tCampaigns("inventoryCsvUpload.loadingFiles")}
                />
              </div>
            ) : tableData.length === 0 ? (
              <div className="py-6 text-center text-mw-neutral-500">
                <div className="flex flex-1 justify-center items-center min-h-[calc(100vh-350px)]">
                  <div className="text-center">
                    <div className="flex justify-center">
                      <PackageOpen className="w-24 h-24 sm:w-18 sm:h-18 text-mw-neutral-400 dark:text-mw-neutral-500" />
                    </div>
                    <h2 className="text-base font-semibold text-mw-neutral-900 dark:text-white leading-right">
                      No files yet
                    </h2>
                    <p className="text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-400 leading-right">
                      Start uploading inventories in bulk
                    </p>
                    <Button
                      variant="primary"
                      size="md"
                      className="gap-1 mt-4"
                      onClick={() => setActiveTab("upload")}
                    >
                      <UploadIcon className="size-4" />
                      Upload CSV
                    </Button>
                  </div>
                </div>
              </div>
            ) : (
              <div className="flex flex-col h-[calc(100vh-280px)]">
                <p className="text-black text-xs mb-2 shrink-0">
                  System will auto select available inventories based on
                  Reference ID's and inventories which are found nearby your GPS
                  Coordinate.
                </p>
                <div className="flex-1 min-h-0 rounded-lg overflow-hidden">
                  <AgGridTable<InventoryCsvFile>
                    rowData={tableData}
                    columnDefs={csvFileColumnDefs}
                    getRowId={(row) => row.id}
                    emptyMessage={tCampaigns(
                      "inventoryCsvUpload.noFilesAvailable",
                    )}
                    height="100%"
                    className="h-full"
                    rowSelection="single"
                    selectedRowIds={
                      selectedFileIdForUse ? [selectedFileIdForUse] : []
                    }
                    onSelectionChange={(ids) =>
                      setSelectedFileIdForUse(ids[0] ?? "")
                    }
                    serverSidePagination
                    serverSideConfig={{
                      totalCount: totalItems,
                      currentPage,
                      pageSize,
                      onPageChange: handlePageChange,
                      onPageSizeChange: handlePageSizeChange,
                      pageSizeSelector: [10, 25, 50, 100],
                    }}
                  />
                </div>
              </div>
            )}
          </TabsContent>

          <TabsContent value="upload">
            <div className="space-y-6 py-3">
              {/* File Upload Area */}
              <FileUpload
                label=""
                placeholder={tCampaigns("inventoryCsvUpload.uploadPlaceholder")}
                acceptedFormats="CSV"
                accept="text/csv"
                maxSize={2 * 1024 * 1024}
                maxSizeLabel="2MB"
                showProgress={true}
                file={uploadedFile}
                onChange={handleFileChange}
              />

              {uploadResponse && (
                <Card>
                  <CardHeader className="p-4">
                    <div className="self-stretch inline-flex justify-start items-center gap-4">
                      <div className="flex-1 justify-start">
                        <CardTitle>
                          {tCampaigns(
                            "inventoryCsvUpload.dataProcessingResults",
                          )}
                        </CardTitle>
                      </div>
                      {(() => {
                        const { successRate, variant } =
                          getSuccessRateAndVariant(
                            uploadResponse.totalRow,
                            uploadResponse.validInventory,
                            uploadResponse.invalidInventory,
                          );
                        return (
                          <Badge
                            className="text-center justify-start"
                            variant={variant}
                            size="md"
                          >
                            {successRate.toFixed(0)}% Success Rate
                          </Badge>
                        );
                      })()}
                    </div>
                  </CardHeader>
                  <CardContent>
                    <div className="grid grid-cols-4 gap-4">
                      <Card className="p-4 bg-mw-primary-50!">
                        <div className="flex flex-col justify-start items-start gap-2">
                          <p className="text-sm font-semibold leading-4">
                            Total Rows
                          </p>
                          <p className="text-mw-primary-500 text-lg font-semibold leading-6">
                            {uploadResponse.totalRow}
                          </p>
                        </div>
                      </Card>
                      <Card className="p-4 bg-mw-success-50!">
                        <div className="flex flex-col justify-start items-start gap-2">
                          <p className="text-sm font-semibold leading-4">
                            Valid
                          </p>
                          <p className="text-mw-success-500 text-lg font-semibold leading-6">
                            {uploadResponse.validInventory}
                          </p>
                        </div>
                      </Card>
                      <Card className="p-4 bg-mw-error-50!">
                        <div className="flex flex-col justify-start items-start gap-2">
                          <p className="text-sm font-semibold leading-4">
                            Invalid
                          </p>
                          <p className="text-mw-error-500 text-lg font-semibold leading-6">
                            {uploadResponse.invalidInventory}
                          </p>
                        </div>
                      </Card>
                      <Card className="p-4 bg-mw-neutral-50!">
                        <div className="flex flex-col justify-start items-start gap-2">
                          <p className="text-sm font-semibold leading-4">
                            Duplicate
                          </p>
                          <p className="text-mw-neutral-500 text-lg font-semibold leading-6">
                            {uploadResponse.duplicateInventory}
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
                              Note :
                            </p>
                            <p className="text-mw-neutral-500 text-sm font-normal leading-4">
                              Only the valid data will be saved and used for
                              inventories selection
                            </p>
                          </div>
                        </div>
                      </div>

                      {uploadResponse.validInventory > 0 && (
                        <div className="flex justify-start items-start gap-1">
                          <div className="inline-flex justify-start items-center gap-1">
                            <Info className="size-4 text-mw-success-500" />
                            <div className="justify-start text-mw-success-500 text-sm font-medium leading-4">
                              {uploadResponse.validInventory} Inventories
                              Verified
                            </div>
                          </div>
                        </div>
                      )}

                      {uploadResponse.invalidInventory > 0 && (
                        <div className="flex justify-start items-start gap-1">
                          <div className="inline-flex justify-start items-center gap-1">
                            <TriangleAlert className="size-4 text-mw-error-500" />
                            <div className="justify-start text-mw-error-500 text-sm font-medium leading-4">
                              {uploadResponse.invalidInventory} Invalid
                              Inventories
                            </div>
                          </div>
                        </div>
                      )}

                      {uploadResponse.duplicateInventory > 0 && (
                        <div className="flex justify-start items-start gap-1">
                          <div className="inline-flex justify-start items-center gap-1">
                            <Copy className="size-4 text-mw-neutral-500" />
                            <div className="justify-start text-mw-neutral-500 text-sm font-medium leading-4">
                              {uploadResponse.duplicateInventory} Duplicate
                              Inventories
                            </div>
                          </div>
                        </div>
                      )}
                    </div>
                    <div className="flex justify-end items-end">
                      <Button
                        variant="primary"
                        onClick={handleOpenViewLog}
                        disabled={
                          uploadResponse.validInventory ===
                          uploadResponse.totalRow
                        }
                      >
                        View Logs
                      </Button>
                    </div>
                  </CardContent>
                  <CardFooter className="pt-0">
                    {uploadSuccess && (
                      <div className="inline-flex justify-start items-center gap-1">
                        <CheckCircle className="size-5 relative overflow-hidden text-mw-success-500" />
                        <p className="text-mw-success-500 text-sm font-medium leading-4">
                          All data processed successfully! -{" "}
                          {uploadSuccess.totalValidInventory} Inventories
                          Verified
                        </p>
                      </div>
                    )}
                  </CardFooter>
                </Card>
              )}
            </div>
          </TabsContent>
        </Tabs>
      </ModalDrawer>

      {/* View Log Drawer */}
      <ViewLogDrawer
        isOpen={isViewLogDrawerOpen}
        onClose={handleCloseViewLog}
        onBack={handleBackFromViewLog}
        logs={uploadResponse?.logs || []}
      />

      {/* View File Inventory Drawer */}
      {campaignId && (
        <ViewFileInventoryDrawer
          isOpen={isViewFileInventoryDrawerOpen}
          onClose={handleCloseViewFileInventory}
          onBack={handleBackFromViewFileInventory}
          fileId={selectedFileId}
          fileName={selectedFileName}
          campaignId={campaignId}
        />
      )}

      <Modal
        isOpen={isDeleteModalOpen}
        onClose={handleCancelDelete}
        title={tCampaigns("inventoryCsvUpload.deleteFileTitle")}
        primaryButtonText={tCampaigns("inventoryCsvUpload.deleteFileConfirm")}
        secondaryButtonText={tCampaigns("inventoryCsvUpload.deleteFileCancel")}
        onPrimaryAction={handleConfirmDelete}
        onSecondaryAction={handleCancelDelete}
        primaryButtonVariant="danger"
        size="sm"
      >
        <p>
          {tCampaigns("inventoryCsvUpload.deleteFileMessage")} -{" "}
          <strong>
            "
            {fileToDelete?.fileName ||
              tCampaigns("inventoryCsvUpload.deleteFileThisFile")}
            "
          </strong>
          ?
        </p>
      </Modal>
    </>
  );
};

export default InventoryCsvUploadDrawer;
