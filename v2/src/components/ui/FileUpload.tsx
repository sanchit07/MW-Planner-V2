import { useAnnounce } from "@hooks/useAnnounce";
import { useTranslate } from "@tolgee/react";
import { Image, FileText, FileSpreadsheet, File, Trash2 } from "lucide-react";
import React, { useState, useRef, useEffect } from "react";

import { Label } from "./Label";
import { Progress } from "./Progressbar";

export interface FileWithUploadProgress extends File {
  isUploading?: boolean;
  uploadProgress?: number; // 0 to 100
}

export interface FileUploadProps {
  label?: string;
  placeholder?: string;
  accept?: string;
  acceptedFormats?: string; // Display text for accepted formats
  maxSize?: number; // Max size in bytes
  maxSizeLabel?: string; // Display label for max size (e.g., "5MB")
  file?: FileWithUploadProgress | null;
  files?: FileWithUploadProgress[]; // For multiple file uploads
  onChange?: (file: FileWithUploadProgress | null) => void; // For single file
  onFilesChange?: (files: FileWithUploadProgress[]) => void; // For multiple files
  error?: string;
  required?: boolean;
  icon?: React.ReactNode; // Custom icon for empty state
  showPreview?: boolean; // Whether to show image preview (default true for images)
  showProgress?: boolean; // Whether to show upload progress (default false)
  multiple?: boolean; // Allow multiple file uploads
  showRemoveIcon?: boolean;
}

export const FileUpload: React.FC<FileUploadProps> = ({
  label = "Upload File",
  placeholder = "Tap here or Drag & Drop your file here",
  accept = "image/*",
  acceptedFormats,
  maxSize,
  maxSizeLabel = "5MB",
  showProgress = false,
  file,
  files,
  onChange,
  onFilesChange,
  error,
  required = false,
  icon,
  showPreview = true,
  multiple = false,
  showRemoveIcon = true,
}) => {
  const [isDragging, setIsDragging] = useState(false);
  const [preview, setPreview] = useState<string>("");
  const [internalError, setInternalError] = useState<string>("");
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Determine which mode we're in
  const isMultipleMode = multiple && onFilesChange;
  const [currentFiles, setCurrentFiles] = useState<FileWithUploadProgress[]>(
    [],
  );
  const { t } = useTranslate("common");
  const { showError } = useAnnounce();

  // Sync file prop with currentFiles state
  useEffect(() => {
    if (isMultipleMode) {
      // For multiple files mode, sync with files prop
      if (files !== undefined) {
        setCurrentFiles(files);
      }
    } else {
      // For single file mode, sync with file prop
      if (file) {
        setCurrentFiles([file]);
        // Create preview for images
        if (showPreview && isImage(file)) {
          const reader = new FileReader();
          reader.onload = (e) => {
            setPreview(e.target?.result as string);
          };
          reader.readAsDataURL(file);
        } else {
          setPreview("");
        }
      } else if (file === null) {
        // Clear if file prop is explicitly null
        setCurrentFiles([]);
        setPreview("");
      }
    }
  }, [file, files, isMultipleMode, showPreview]);

  // Determine if file is an image
  const isImage = (file: FileWithUploadProgress) =>
    file.type.startsWith("image/");

  // Auto-detect accepted formats from accept prop if not provided
  const getFormatsLabel = () => {
    if (acceptedFormats) return acceptedFormats;
    if (accept.includes("image")) return "PNG, JPG or JPEG";
    if (accept.includes("csv") || accept.includes("text/csv")) return "CSV";
    if (accept.includes("pdf")) return "PDF";
    return accept
      .split(",")
      .map((ext) => ext.replace(".", "").toUpperCase())
      .join(", ");
  };

  // Format file size for display
  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(2)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  };

  // Validate file type against accept prop
  const isFileTypeValid = (file: FileWithUploadProgress): boolean => {
    if (!accept) return true;

    const acceptedTypes = accept.split(",").map((type) => type.trim());

    for (const type of acceptedTypes) {
      // Handle wildcard patterns (e.g., image/*, video/*)
      if (type.includes("/*")) {
        const baseType = type.split("/")[0];
        if (file.type.startsWith(baseType + "/")) {
          return true;
        }
      }
      // Handle specific MIME types (e.g., image/png, application/pdf)
      else if (type.startsWith(".")) {
        // Handle file extensions (e.g., .csv, .pdf)
        if (file.name.toLowerCase().endsWith(type.toLowerCase())) {
          return true;
        }
      } else if (file.type === type) {
        // Exact MIME type match
        return true;
      }
      // Handle cases where accept contains MIME type and file extension matches
      else if (
        type === "text/csv" &&
        file.name.toLowerCase().endsWith(".csv")
      ) {
        return true;
      }
    }

    return false;
  };

  const checkForFileSelectChange = (
    fileIndex: number,
    totalFiles: number,
    validFiles: FileWithUploadProgress[],
    withProgress = true,
  ) => {
    if (fileIndex === totalFiles) {
      if (!validFiles.length) {
        const message = t("messages.title");
        setInternalError(message);
        if (withProgress) setTimeout(() => setCurrentFiles([]), 0);
      } else {
        if (withProgress) setTimeout(() => setCurrentFiles([...validFiles]), 0);
        // Set files
        if (isMultipleMode) {
          onFilesChange?.([...validFiles]);
        } else {
          const selectedFile = validFiles[0];
          onChange?.(selectedFile);

          // Create preview for images in single mode
          if (showPreview && isImage(selectedFile)) {
            const reader = new FileReader();
            reader.onload = (e) => {
              setPreview(e.target?.result as string);
            };
            reader.readAsDataURL(selectedFile);
          } else {
            setPreview("");
          }
        }
      }
    }
  };

  const checkFileSize = (file: FileWithUploadProgress) => {
    let underMaxSize = true;
    const filename = file.name;
    if (maxSize) {
      if (file.size > maxSize) {
        showError(`${filename} ${t("messages.file_too_large")}`);
        underMaxSize = false;
      }
    }
    return underMaxSize;
  };

  const handleProgressUploads = (filesArray: FileWithUploadProgress[]) => {
    const validFiles: FileWithUploadProgress[] = [];
    for (let fileIndex = 0; fileIndex < filesArray.length; fileIndex++) {
      if (filesArray[fileIndex]) {
        const reader = new FileReader();
        reader.onload = async (e) => {
          const uploadingFile = filesArray[fileIndex];
          uploadingFile.uploadProgress = 10;
          setTimeout(() => setCurrentFiles([...filesArray]), 0);

          // The FileUpload component gives us a base64 data URL
          // We need to convert it back to parse the file
          const response = await fetch(e.target?.result as string);

          // Check if fetch was successful
          if (!response.ok) {
            showError(t("messages.connection_lost"));
            filesArray.splice(fileIndex, 1);
            setTimeout(() => setCurrentFiles([...filesArray]), 0);
            return;
          }

          // Check File size
          if (!checkFileSize(uploadingFile)) {
            uploadingFile.isUploading = false;
            uploadingFile.uploadProgress = 0;
            setTimeout(() => setCurrentFiles([...filesArray]), 0);
            checkForFileSelectChange(
              fileIndex + 1,
              filesArray.length,
              validFiles,
            );
            return;
          }

          uploadingFile.uploadProgress = 30;
          setTimeout(() => setCurrentFiles([...filesArray]), 0);

          // Validate file type
          if (!isFileTypeValid(uploadingFile)) {
            const message = t("messages.invalid_image_type");
            showError(message);
            uploadingFile.isUploading = false;
            uploadingFile.uploadProgress = 0;
            setTimeout(() => setCurrentFiles([...filesArray]), 0);
            checkForFileSelectChange(
              fileIndex + 1,
              filesArray.length,
              validFiles,
            );
            return;
          }

          uploadingFile.uploadProgress = 50;
          setTimeout(() => setCurrentFiles([...filesArray]), 0);

          // Small delay to show 100% before completing
          await new Promise((resolve) => setTimeout(resolve, 200));
          uploadingFile.isUploading = false;
          uploadingFile.uploadProgress = 100;
          setTimeout(() => setCurrentFiles([...filesArray]), 0);

          validFiles.push(uploadingFile);
          checkForFileSelectChange(
            fileIndex + 1,
            filesArray.length,
            validFiles,
          );
        };
        reader.readAsDataURL(filesArray[fileIndex]);
      }
    }
  };

  const handleUploadWithoutProgress = (
    filesArray: FileWithUploadProgress[],
  ) => {
    const validFiles: FileWithUploadProgress[] = [];
    for (let fileIndex = 0; fileIndex < filesArray.length; fileIndex++) {
      const uploadingFile = filesArray[fileIndex];
      if (!checkFileSize(uploadingFile)) {
        const validatedFiles = [...filesArray];
        validatedFiles.splice(fileIndex, 1);
        setCurrentFiles(validatedFiles);
      } else if (!isFileTypeValid(uploadingFile)) {
        const message = t("messages.invalid_image_type");
        showError(message);
        const validatedFiles = [...filesArray];
        validatedFiles.splice(fileIndex, 1);
        setCurrentFiles(validatedFiles);
      } else {
        validFiles.push(uploadingFile);
      }
    }
    checkForFileSelectChange(
      filesArray.length,
      filesArray.length,
      validFiles,
      false,
    );
  };

  // Handle file selection for single or multiple files
  const handleFileSelect = (selectedFiles: FileWithUploadProgress[] | null) => {
    // Clear previous errors
    setInternalError("");

    if (!selectedFiles || selectedFiles.length === 0) {
      if (isMultipleMode) {
        onFilesChange?.([]);
      } else {
        onChange?.(null);
      }
      setPreview("");
      return;
    }

    const filesArray = Array.from(selectedFiles);
    if (showProgress) {
      handleProgressUploads(filesArray);
    } else {
      handleUploadWithoutProgress(filesArray);
    }
  };

  // Handle removing a file
  const handleRemoveFile = (index: number) => {
    currentFiles.splice(index, 1);
    setCurrentFiles([...currentFiles]);
    if (isMultipleMode) {
      onFilesChange?.(currentFiles);
    } else {
      onChange?.(null);
      setPreview("");
    }
    setInternalError("");
  };

  // Handle file input change
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFiles = e.target.files || [];
    const uploadedFiles: FileWithUploadProgress[] = [];
    for (const uploadFile of selectedFiles) {
      const uploadedFile = uploadFile as FileWithUploadProgress;
      uploadedFile.isUploading = true;
      uploadedFile.uploadProgress = 0;
      uploadedFiles.push(uploadedFile);
    }
    setCurrentFiles(uploadedFiles);
    handleFileSelect(uploadedFiles);
    // Reset input value to allow selecting the same file again
    e.target.value = "";
  };

  // Handle drag and drop
  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
    const droppedFiles = isMultipleMode
      ? e.dataTransfer.files
      : [e.dataTransfer.files[0]];
    const uploadedFiles: FileWithUploadProgress[] = [];
    for (const uploadFile of droppedFiles) {
      const uploadedFile = uploadFile as FileWithUploadProgress;
      uploadedFile.isUploading = true;
      uploadedFile.uploadProgress = 0;
      uploadedFiles.push(uploadedFile);
    }
    if (droppedFiles && droppedFiles.length > 0) {
      setCurrentFiles(uploadedFiles);
      handleFileSelect(uploadedFiles);
    }
  };

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
  };

  // Determine border color based on state
  const getBorderColor = () => {
    const hasError = error || internalError;
    if (isDragging)
      return "border-mw-primary-500 bg-mw-primary-50 dark:bg-mw-primary-900/20";
    if (currentFiles.length && !hasError)
      return "border-mw-success-500 bg-mw-success-50 dark:bg-mw-success-900/20";
    if (hasError)
      return "border-mw-error-500 bg-mw-error-50 dark:bg-mw-error-900/20";
    return "border-mw-neutral-500 dark:border-mw-neutral-600 hover:border-mw-neutral-400 dark:hover:border-mw-neutral-500";
  };

  // Determine icon to display in empty state
  const getIcon = () => {
    if (icon) return icon;
    if (accept.includes("csv") || accept.includes("text")) {
      return <FileText className="w-8 h-8  text-mw-neutral-300" />;
    }
    return <Image className="w-8 h-8 text-mw-neutral-300" />;
  };

  // Get icon based on file type
  const getFileIcon = (file: FileWithUploadProgress) => {
    const fileName = file.name.toLowerCase();
    const fileType = file.type.toLowerCase();

    // Image files
    if (fileType.startsWith("image/")) {
      return preview ? (
        <img src={preview} alt="File preview" />
      ) : (
        <Image className="w-4 h-4 text-mw-neutral-300" />
      );
    }

    // PDF files
    if (fileType === "application/pdf" || fileName.endsWith(".pdf")) {
      return <File className="w-4 h-4 text-mw-neutral-300 " />;
    }

    // Excel/CSV files
    if (
      fileType.includes("spreadsheet") ||
      fileType.includes("excel") ||
      fileType === "text/csv" ||
      fileName.endsWith(".csv") ||
      fileName.endsWith(".xlsx") ||
      fileName.endsWith(".xls")
    ) {
      return <FileSpreadsheet className="w-4 h-4 text-mw-neutral-300" />;
    }

    // Default file icon
    return <FileText className="w-4 h-4 text-mw-neutral-300 " />;
  };

  // Generate unique ID for the file input
  const fileInputId = `file-upload-${Math.random().toString(36).substring(2, 9)}`;

  // Display error - prioritize external error over internal error
  const displayError = error || internalError;

  // Determine if we should show the drag-drop area
  const showDragDropArea = isMultipleMode || currentFiles.length === 0;

  return (
    <div className="space-y-2">
      {label && (
        <Label>
          {label}
          {!required && (
            <span className="text-mw-neutral-500 ml-1">(Optional)</span>
          )}
        </Label>
      )}

      {/* Drag and Drop Area - Show for multiple mode OR when no files in single mode */}
      {showDragDropArea && (
        <div
          className={`border-2 border-dashed rounded-lg p-8 text-center transition-colors cursor-pointer ${getBorderColor()}`}
          onDrop={handleDrop}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onClick={() => fileInputRef.current?.click()}
        >
          {!isMultipleMode && currentFiles.length && preview ? (
            // Single file with image preview
            <div className="space-y-2">
              <img
                src={preview}
                alt="File preview"
                className="mx-auto max-h-32 max-w-32 object-contain rounded-lg"
              />
              <div className="space-y-1">
                <div className="text-sm font-medium text-primary dark:text-white">
                  {currentFiles[0].name}
                </div>
                <div className="text-xs font-normal text-secondary dark:text-mw-neutral-400">
                  {formatFileSize(currentFiles[0].size)}
                </div>
              </div>
              <div className="text-sm text-mw-neutral-600 dark:text-mw-neutral-400">
                Click to change file
              </div>
            </div>
          ) : (
            // Empty state
            <div className="space-y-2">
              <div className="flex justify-center">
                <div className="w-16 h-16 bg-mw-neutral-50 rounded-full dark:bg-mw-neutral-700 flex items-center justify-center">
                  {getIcon()}
                </div>
              </div>
              <div className="space-y-1">
                <div className="text-sm font-medium text-primary dark:text-mw-neutral-300 leading-tight">
                  {placeholder}
                </div>
                <div className="text-xs font-normal text-secondary dark:text-mw-neutral-400">
                  {t("fileUpload.supported_format", {
                    formats: getFormatsLabel(),
                    maxSize: maxSizeLabel,
                  })}
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* File List - Show when files are selected */}
      {currentFiles.length > 0 && (
        <div className="space-y-2">
          {currentFiles.map((fileItem, index) => (
            <div
              key={`${fileItem.name}-${index}`}
              className="h-14 px-2 py-3 rounded-sm outline outline-mw-neutral-100 flex justify-between items-center"
            >
              <div className="flex items-center gap-2 flex-1 min-w-0">
                {/* File Icon */}
                <div className="shrink-0 w-8 h-8 bg-mw-neutral-50 rounded-full flex items-center justify-center">
                  {getFileIcon(fileItem)}
                </div>

                {/* File Info */}
                <div className="flex-1 min-w-0 flex flex-col gap-1">
                  <div className="text-xs font-normal text-primary truncate">
                    {fileItem.name}
                  </div>
                  <div className="text-[10px] font-normal text-secondary">
                    {formatFileSize(fileItem.size)}
                    {showProgress && fileItem.isUploading && (
                      <Progress
                        className="pt-1"
                        value={fileItem.uploadProgress}
                        showPercentage={false}
                      />
                    )}
                  </div>
                </div>
              </div>

              {/* Remove Button */}
              {showRemoveIcon && (
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleRemoveFile(index);
                  }}
                  className="shrink-0 p-2 rounded-sm hover:bg-mw-error-50 dark:hover:bg-mw-error-900/20 transition-colors"
                  aria-label={t("aria.removeFile")}
                >
                  <Trash2 className="w-4 h-4 text-mw-error-500" />
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      <input
        ref={fileInputRef}
        id={fileInputId}
        type="file"
        accept={accept}
        multiple={multiple}
        onChange={handleFileChange}
        className="hidden"
      />

      {displayError && (
        <div className="text-sm text-mw-error-500 mt-1">{displayError}</div>
      )}
    </div>
  );
};

export default FileUpload;
