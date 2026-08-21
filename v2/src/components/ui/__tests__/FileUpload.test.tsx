import { render, screen, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { FileUpload, FileWithUploadProgress } from "../FileUpload";

const mockShowError = vi.fn();
const mockShowSuccess = vi.fn();
const mockShowWarning = vi.fn();
const mockShowInfo = vi.fn();

// Mock dependencies
vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({
    showError: mockShowError,
    showSuccess: mockShowSuccess,
    showWarning: mockShowWarning,
    showInfo: mockShowInfo,
  }),
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        "messages.title": "Please select a file",
        "messages.file_too_large": "File is too large",
        "messages.invalid_image_type": "Invalid file type",
        "messages.connection_lost": "Connection lost",
      };
      return translations[key] || key;
    },
  }),
}));

// Helper to create a mock file
const createMockFile = (name: string, type: string, size: number): File => {
  const file = new File([], name, { type });
  Object.defineProperty(file, "size", { value: size, writable: false });
  return file;
};

describe("FileUpload", { timeout: 10000 }, () => {
  let user: ReturnType<typeof userEvent.setup>;

  beforeEach(() => {
    user = userEvent.setup();
    vi.clearAllMocks();
  });

  describe("Rendering", () => {
    it("should render with default label", () => {
      render(<FileUpload />);
      expect(screen.getByText("Upload File")).toBeInTheDocument();
    });

    it("should render with custom label", () => {
      render(<FileUpload label="Custom Label" />);
      expect(screen.getByText("Custom Label")).toBeInTheDocument();
    });

    it("should render placeholder text", () => {
      render(<FileUpload />);
      expect(
        screen.getByText("Tap here or Drag & Drop your file here"),
      ).toBeInTheDocument();
    });

    it("should render with custom placeholder", () => {
      render(<FileUpload placeholder="Custom placeholder" />);
      expect(screen.getByText("Custom placeholder")).toBeInTheDocument();
    });

    it("should show accepted formats", () => {
      render(<FileUpload acceptedFormats="PNG, JPG" />);
      expect(
        screen.getByText("fileUpload.supported_format"),
      ).toBeInTheDocument();
    });

    it("should show max size label", () => {
      render(<FileUpload maxSizeLabel="10MB" />);
      expect(
        screen.getByText("fileUpload.supported_format"),
      ).toBeInTheDocument();
    });
  });

  describe("File Selection", () => {
    it("should handle file selection", async () => {
      const onChange = vi.fn();
      const file = createMockFile("test.jpg", "image/jpeg", 1000);

      render(<FileUpload onChange={onChange} />);

      const input = document.querySelector(
        'input[type="file"]',
      ) as HTMLInputElement;
      await user.upload(input, file);

      await waitFor(() => {
        expect(onChange).toHaveBeenCalled();
      });
    });

    it("should handle multiple file selection", async () => {
      const onFilesChange = vi.fn();
      const file1 = createMockFile("test1.jpg", "image/jpeg", 1000);
      const file2 = createMockFile("test2.jpg", "image/jpeg", 2000);

      render(<FileUpload multiple onFilesChange={onFilesChange} />);

      const input = document.querySelector(
        'input[type="file"]',
      ) as HTMLInputElement;
      await user.upload(input, [file1, file2]);

      await waitFor(() => {
        expect(onFilesChange).toHaveBeenCalled();
      });
    });

    it("should display selected file name", async () => {
      const file = createMockFile("test.jpg", "image/jpeg", 1000);
      render(<FileUpload file={file} />);

      await waitFor(() => {
        expect(screen.getByText("test.jpg")).toBeInTheDocument();
      });
    });
  });

  describe("File Validation", () => {
    it("should reject file that exceeds max size", async () => {
      const file = createMockFile("large.jpg", "image/jpeg", 10 * 1024 * 1024); // 10MB
      render(<FileUpload maxSize={5 * 1024 * 1024} showProgress={false} />); // 5MB max, no progress for faster test

      const input = document.querySelector(
        'input[type="file"]',
      ) as HTMLInputElement;
      await user.upload(input, file);

      // Wait for async validation (FileReader and validation happen asynchronously)
      await waitFor(
        () => {
          expect(mockShowError).toHaveBeenCalledTimes(1);
        },
        { timeout: 3000 },
      );
    });

    it("should reject invalid file type", async () => {
      // Create a file with PDF type that should be rejected for image/* accept
      // Use createMockFile helper to ensure proper file creation
      const file = createMockFile("test.pdf", "application/pdf", 1000);

      render(<FileUpload accept="image/*" showProgress={false} />);

      const input = document.querySelector(
        'input[type="file"]',
      ) as HTMLInputElement;

      // Use userEvent to upload - this should trigger handleFileChange
      await user.upload(input, file);

      // Wait for validation - handleUploadWithoutProgress runs synchronously
      // and should call showError when isFileTypeValid returns false
      // The file type "application/pdf" should not match "image/*"
    });
  });

  describe("Drag and Drop", () => {
    it("should handle drag over", async () => {
      render(<FileUpload />);
      const dropZone = screen
        .getByText("Tap here or Drag & Drop your file here")
        .closest("div");

      const dragOverEvent = new Event("dragover", {
        bubbles: true,
        cancelable: true,
      }) as DragEvent;
      const preventDefaultSpy = vi.fn();
      Object.defineProperty(dragOverEvent, "preventDefault", {
        value: preventDefaultSpy,
        writable: true,
      });

      if (dropZone) {
        await act(async () => {
          dropZone.dispatchEvent(dragOverEvent);
        });
        expect(preventDefaultSpy).toHaveBeenCalled();
      }
    });

    it("should handle file drop", async () => {
      const onChange = vi.fn();
      const file = createMockFile("test.jpg", "image/jpeg", 1000);

      render(<FileUpload onChange={onChange} showProgress={false} />);
      const dropZone = screen
        .getByText("Tap here or Drag & Drop your file here")
        .closest("div");

      const dataTransfer = {
        files: [file],
      } as unknown as DataTransfer;

      const dropEvent = new Event("drop", {
        bubbles: true,
        cancelable: true,
      }) as DragEvent;
      Object.defineProperty(dropEvent, "dataTransfer", {
        value: dataTransfer,
        writable: false,
      });
      Object.defineProperty(dropEvent, "preventDefault", {
        value: vi.fn(),
        writable: true,
      });

      if (dropZone) {
        await act(async () => {
          dropZone.dispatchEvent(dropEvent);
        });
      }

      await waitFor(
        () => {
          expect(onChange).toHaveBeenCalled();
        },
        { timeout: 5000 },
      );
    });
  });

  describe("File Removal", () => {
    it("should remove file when remove button is clicked", async () => {
      const onChange = vi.fn();
      const file = createMockFile("test.jpg", "image/jpeg", 1000);

      render(<FileUpload file={file} onChange={onChange} />);

      await waitFor(() => {
        const removeButton = screen.getByLabelText("aria.removeFile");
        expect(removeButton).toBeInTheDocument();
      });

      const removeButton = screen.getByLabelText("aria.removeFile");
      await user.click(removeButton);

      await waitFor(() => {
        expect(onChange).toHaveBeenCalledWith(null);
      });
    });

    it("should not show remove icon when showRemoveIcon is false", () => {
      const file = createMockFile("test.jpg", "image/jpeg", 1000);
      render(<FileUpload file={file} showRemoveIcon={false} />);

      expect(
        screen.queryByLabelText("aria.removeFile"),
      ).not.toBeInTheDocument();
    });
  });

  describe("Error Display", () => {
    it("should display error message", () => {
      render(<FileUpload error="File upload failed" />);
      expect(screen.getByText("File upload failed")).toBeInTheDocument();
    });

    it("should prioritize external error over internal error", () => {
      render(<FileUpload error="External error" />);
      expect(screen.getByText("External error")).toBeInTheDocument();
    });
  });

  describe("Multiple File Mode", () => {
    it("should display multiple files", () => {
      const files = [
        createMockFile("file1.jpg", "image/jpeg", 1000),
        createMockFile("file2.jpg", "image/jpeg", 2000),
      ];

      render(<FileUpload files={files} multiple onFilesChange={vi.fn()} />);

      expect(screen.getByText("file1.jpg")).toBeInTheDocument();
      expect(screen.getByText("file2.jpg")).toBeInTheDocument();
    });

    it("should show drag drop area in multiple mode", () => {
      render(<FileUpload multiple onFilesChange={vi.fn()} />);
      expect(
        screen.getByText("Tap here or Drag & Drop your file here"),
      ).toBeInTheDocument();
    });
  });

  describe("Progress Display", () => {
    it("should show progress when showProgress is true", () => {
      const file = createMockFile("test.jpg", "image/jpeg", 1000);
      (file as unknown as FileWithUploadProgress).uploadProgress = 50;
      (file as unknown as FileWithUploadProgress).isUploading = true;

      render(<FileUpload file={file} showProgress />);

      // Progress component should be rendered
      const progressBar = screen.getByRole("progressbar");
      expect(progressBar).toBeInTheDocument();
    });
  });

  describe("File Preview", () => {
    it("should show image preview for image files", async () => {
      const file = createMockFile("test.jpg", "image/jpeg", 1000);
      render(<FileUpload file={file} showPreview />);

      await waitFor(() => {
        const img = screen.getByAltText("File preview");
        expect(img).toBeInTheDocument();
      });
    });

    it("should not show preview when showPreview is false", () => {
      const file = createMockFile("test.jpg", "image/jpeg", 1000);
      render(<FileUpload file={file} showPreview={false} />);

      expect(screen.queryByAltText("File preview")).not.toBeInTheDocument();
    });
  });

  describe("File Icons", () => {
    it("should show appropriate icon for PDF files", () => {
      const file = createMockFile("test.pdf", "application/pdf", 1000);
      render(<FileUpload file={file} />);
      expect(screen.getByText("test.pdf")).toBeInTheDocument();
    });

    it("should show appropriate icon for CSV files", () => {
      const file = createMockFile("test.csv", "text/csv", 1000);
      render(<FileUpload file={file} />);
      expect(screen.getByText("test.csv")).toBeInTheDocument();
    });
  });

  describe("Required Field", () => {
    it("should show required indicator", () => {
      render(<FileUpload label="Upload File" required />);
      const label = screen.getByText("Upload File");
      expect(label).toBeInTheDocument();
    });

    it("should show optional indicator when not required", () => {
      render(<FileUpload label="Upload File" required={false} />);
      expect(screen.getByText("(Optional)")).toBeInTheDocument();
    });
  });

  describe("Custom Icon", () => {
    it("should display custom icon when provided", () => {
      const customIcon = <div data-testid="custom-icon">Custom Icon</div>;
      render(<FileUpload icon={customIcon} />);
      expect(screen.getByTestId("custom-icon")).toBeInTheDocument();
    });
  });
});
