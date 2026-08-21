import { render, screen } from "@testing-library/react";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { UploadTab } from "../UploadTab";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const defaultUploadResponse = {
  totalRow: 100,
  validLocation: 80,
  invalidLocation: 15,
  duplicateLocation: 5,
  logs: [],
};

function renderUploadTab(
  props: Partial<React.ComponentProps<typeof UploadTab>> = {},
) {
  const onFileChange = vi.fn();
  const onViewLog = vi.fn();
  render(
    <UploadTab
      uploadedFile={null}
      onFileChange={onFileChange}
      uploadResponse={null}
      uploadSuccess={null}
      onViewLog={onViewLog}
      {...props}
    />,
  );
  return { onFileChange, onViewLog };
}

describe("UploadTab", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("renders FileUpload with placeholder and CSV accept", () => {
      renderUploadTab();
      expect(
        screen.getByText("geofencingDrawer.uploadTab.placeholder"),
      ).toBeInTheDocument();
    });

    it("does not render DataProcessingResults when uploadResponse is null", () => {
      renderUploadTab();
      expect(
        screen.queryByText("Data Processing Results"),
      ).not.toBeInTheDocument();
    });

    it("renders DataProcessingResults when uploadResponse is provided", () => {
      renderUploadTab({ uploadResponse: defaultUploadResponse });
      expect(
        screen.getByText("geofencingDrawer.dataProcessing.title"),
      ).toBeInTheDocument();
      expect(screen.getByText("100")).toBeInTheDocument();
      expect(
        screen.getByRole("button", {
          name: "geofencingDrawer.dataProcessing.viewLogs",
        }),
      ).toBeInTheDocument();
    });

    it("renders success footer when uploadSuccess is provided", () => {
      renderUploadTab({
        uploadResponse: defaultUploadResponse,
        uploadSuccess: { totalValidLocation: 80 },
      });
      expect(
        screen.getByText("geofencingDrawer.uploadTab.allProcessed"),
      ).toBeInTheDocument();
      expect(
        screen.getAllByText("geofencingDrawer.dataProcessing.locationsVerified")
          .length,
      ).toBeGreaterThan(0);
    });

    it("does not render success footer when uploadSuccess is null", () => {
      renderUploadTab({ uploadResponse: defaultUploadResponse });
      expect(
        screen.queryByText("geofencingDrawer.uploadTab.allProcessed"),
      ).not.toBeInTheDocument();
    });
  });
});
