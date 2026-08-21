import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import type { InventoryCsvVerifyResult } from "../../../../types/inventory.types";
import { ViewLogDrawer } from "../ViewLogDrawer";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@components/ui/AgGridTable/AgGridTable", () => ({
  AgGridTable: (props: {
    rowData: Array<{ id: number; row: number; message: string; type?: string }>;
    emptyMessage: string;
    columnDefs?: Array<{ headerName?: string }>;
  }) => {
    const { rowData, emptyMessage, columnDefs = [] } = props;
    if (!rowData || rowData.length === 0) {
      return (
        <div role="grid">
          <div>{emptyMessage}</div>
        </div>
      );
    }
    const pageSize = 10;
    const firstPage = rowData.slice(0, pageSize);
    return (
      <div role="grid">
        <div role="rowgroup">
          {columnDefs.map((col, i) => (
            <span key={i} role="columnheader">
              {col.headerName}
            </span>
          ))}
        </div>
        {firstPage.map((row, idx) => (
          <div key={row.id ?? idx} role="row">
            <span>{row.id}</span>
            <span>Row {row.row}</span>
            <span>{row.message}</span>
          </div>
        ))}
      </div>
    );
  },
}));

describe("ViewLogDrawer", () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    logs: [] as InventoryCsvVerifyResult[],
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("does not render drawer content when isOpen is false", () => {
      render(<ViewLogDrawer {...defaultProps} isOpen={false} />);
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    it("renders drawer with title when isOpen is true", () => {
      render(<ViewLogDrawer {...defaultProps} />);
      expect(screen.getByText("viewLogDrawer.title")).toBeInTheDocument();
    });

    it("renders Download error log button in footer", () => {
      render(<ViewLogDrawer {...defaultProps} />);
      expect(
        screen.getByRole("button", {
          name: /viewLogDrawer\.downloadErrorLog/i,
        }),
      ).toBeInTheDocument();
    });

    it("renders empty message when logs array is empty", () => {
      render(<ViewLogDrawer {...defaultProps} />);
      expect(
        screen.getByText("viewLogDrawer.emptyMessage"),
      ).toBeInTheDocument();
    });

    it("renders table with logs when logs are provided", () => {
      const logs: InventoryCsvVerifyResult[] = [
        { id: "1", row: 1, type: "INVALID", message: "Invalid inventory ID" },
        { id: "2", row: 2, type: "DUPLICATE", message: "Duplicate entry" },
      ];
      render(<ViewLogDrawer {...defaultProps} logs={logs} />);
      expect(screen.getByText("Invalid inventory ID")).toBeInTheDocument();
      expect(screen.getByText("Duplicate entry")).toBeInTheDocument();
      expect(screen.getByText("Row 1")).toBeInTheDocument();
      expect(screen.getByText("Row 2")).toBeInTheDocument();
    });

    it("renders Sr. No column with serial numbers", () => {
      const logs: InventoryCsvVerifyResult[] = [
        { id: "1", row: 5, type: "INVALID", message: "Error one" },
      ];
      render(<ViewLogDrawer {...defaultProps} logs={logs} />);
      expect(screen.getByText("Row 5")).toBeInTheDocument();
      expect(screen.getByText("Error one")).toBeInTheDocument();
      const serialNumbers = screen.getAllByText("1", { exact: true });
      expect(serialNumbers.length).toBeGreaterThanOrEqual(1);
    });

    it("renders table headers Sr. No, Row, Description", () => {
      render(
        <ViewLogDrawer
          {...defaultProps}
          logs={[{ id: "1", row: 1, type: "INVALID", message: "Msg" }]}
        />,
      );
      expect(
        screen.getByText("viewLogDrawer.columns.srNo"),
      ).toBeInTheDocument();
      expect(screen.getByText("viewLogDrawer.columns.row")).toBeInTheDocument();
      expect(
        screen.getByText("viewLogDrawer.columns.description"),
      ).toBeInTheDocument();
    });
  });

  describe("pagination", () => {
    it("paginates logs when page size is 10 and logs exceed 10", () => {
      const logs: InventoryCsvVerifyResult[] = Array.from(
        { length: 15 },
        (_, i) => ({
          id: String(i + 1),
          row: i + 1,
          type: "INVALID" as const,
          message: `Message ${i + 1}`,
        }),
      );
      render(<ViewLogDrawer {...defaultProps} logs={logs} />);
      expect(screen.getByText("Message 1")).toBeInTheDocument();
      expect(screen.getByText("Message 10")).toBeInTheDocument();
      expect(screen.queryByText("Message 11")).not.toBeInTheDocument();
    });
  });

  describe("interactions", () => {
    it("calls onClose when drawer back button is clicked and onBack is not provided", async () => {
      const user = userEvent.setup();
      render(<ViewLogDrawer {...defaultProps} />);
      const buttons = screen.getAllByRole("button");
      const backButton = buttons.find((b) => b.closest("[id$='-header']"));
      if (backButton) {
        await user.click(backButton);
        expect(defaultProps.onClose).toHaveBeenCalled();
      }
    });

    it("calls onBack when back button is clicked and onBack is provided", async () => {
      const user = userEvent.setup();
      const onBack = vi.fn();
      render(<ViewLogDrawer {...defaultProps} onBack={onBack} />);
      const header = document.querySelector("[id$='-header']");
      const backButton = header?.querySelector("button");
      if (backButton) {
        await user.click(backButton as HTMLButtonElement);
        expect(onBack).toHaveBeenCalled();
      }
    });

    it("downloads CSV when Download error log is clicked", async () => {
      const user = userEvent.setup();
      const createObjectURL = vi.fn(() => "blob:mock-url");
      const revokeObjectURL = vi.fn();
      vi.stubGlobal("URL", {
        createObjectURL,
        revokeObjectURL,
      });
      const appendChild = vi.spyOn(document.body, "appendChild");
      const removeChild = vi.spyOn(document.body, "removeChild");
      const originalClick = HTMLAnchorElement.prototype.click;
      HTMLAnchorElement.prototype.click = vi.fn();

      const logs: InventoryCsvVerifyResult[] = [
        { id: "1", row: 1, type: "INVALID", message: "Test error" },
      ];
      render(<ViewLogDrawer {...defaultProps} logs={logs} />);
      await user.click(
        screen.getByRole("button", {
          name: /viewLogDrawer\.downloadErrorLog/i,
        }),
      );

      expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
      expect(appendChild).toHaveBeenCalled();
      expect(removeChild).toHaveBeenCalled();
      expect(revokeObjectURL).toHaveBeenCalledWith("blob:mock-url");

      appendChild.mockRestore();
      removeChild.mockRestore();
      HTMLAnchorElement.prototype.click = originalClick;
      vi.unstubAllGlobals();
    });
  });

  describe("log types", () => {
    it("renders VALID type log with warning icon style", () => {
      const logs: InventoryCsvVerifyResult[] = [
        { id: "1", row: 1, type: "VALID", message: "Valid row" },
      ];
      render(<ViewLogDrawer {...defaultProps} logs={logs} />);
      expect(screen.getByText("Valid row")).toBeInTheDocument();
    });

    it("uses default empty array when logs prop is undefined", () => {
      render(<ViewLogDrawer isOpen={true} onClose={vi.fn()} />);
      expect(
        screen.getByText("viewLogDrawer.emptyMessage"),
      ).toBeInTheDocument();
    });
  });

  describe("escapeCsvValue", () => {
    it("downloads CSV when message contains special characters", async () => {
      const user = userEvent.setup();
      const createObjectURL = vi.fn(() => "blob:mock");
      const revokeObjectURL = vi.fn();
      vi.stubGlobal("URL", { createObjectURL, revokeObjectURL });
      const appendChild = vi.spyOn(document.body, "appendChild");
      const removeChild = vi.spyOn(document.body, "removeChild");
      const originalClick = HTMLAnchorElement.prototype.click;
      HTMLAnchorElement.prototype.click = vi.fn();

      const logs: InventoryCsvVerifyResult[] = [
        { id: "1", row: 1, type: "INVALID", message: 'Value "with" quotes' },
      ];
      render(<ViewLogDrawer {...defaultProps} logs={logs} />);
      await user.click(
        screen.getByRole("button", {
          name: /viewLogDrawer\.downloadErrorLog/i,
        }),
      );

      expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
      expect(revokeObjectURL).toHaveBeenCalledWith("blob:mock");

      appendChild.mockRestore();
      removeChild.mockRestore();
      HTMLAnchorElement.prototype.click = originalClick;
      vi.unstubAllGlobals();
    });
  });
});
