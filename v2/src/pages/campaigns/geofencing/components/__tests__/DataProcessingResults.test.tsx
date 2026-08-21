import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { DataProcessingResults } from "../DataProcessingResults";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

const defaultUploadResponse = {
  totalRow: 100,
  validLocation: 80,
  invalidLocation: 15,
  duplicateLocation: 5,
  logs: [],
};

function renderResults(
  props: Partial<React.ComponentProps<typeof DataProcessingResults>> = {},
) {
  const onViewLog = vi.fn();
  render(
    <DataProcessingResults
      uploadResponse={defaultUploadResponse}
      onViewLog={onViewLog}
      {...props}
    />,
  );
  return { onViewLog };
}

describe("DataProcessingResults", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("renders title and success rate badge", () => {
      renderResults();
      expect(
        screen.getByText("geofencingDrawer.dataProcessing.title"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("geofencingDrawer.dataProcessing.successRate"),
      ).toBeInTheDocument();
    });

    it("renders Total Rows, Valid, and Invalid cards with correct values", () => {
      renderResults();
      expect(
        screen.getByText("geofencingDrawer.dataProcessing.totalRows"),
      ).toBeInTheDocument();
      expect(screen.getByText("100")).toBeInTheDocument();
      expect(
        screen.getByText("geofencingDrawer.dataProcessing.valid"),
      ).toBeInTheDocument();
      expect(screen.getAllByText("80")[0]).toBeInTheDocument();
      expect(
        screen.getByText("geofencingDrawer.dataProcessing.invalid"),
      ).toBeInTheDocument();
      expect(screen.getByText("15")).toBeInTheDocument();
    });

    it("renders note about valid data", () => {
      renderResults();
      expect(
        screen.getByText("geofencingDrawer.dataProcessing.note"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("geofencingDrawer.dataProcessing.noteText"),
      ).toBeInTheDocument();
    });

    it("renders View Logs button", () => {
      renderResults();
      expect(
        screen.getByRole("button", {
          name: "geofencingDrawer.dataProcessing.viewLogs",
        }),
      ).toBeInTheDocument();
    });
  });

  describe("success rate and badge variant", () => {
    it("shows 0% and destructive variant when totalRow is 0", () => {
      renderResults({
        uploadResponse: {
          ...defaultUploadResponse,
          totalRow: 0,
          validLocation: 0,
          invalidLocation: 0,
          duplicateLocation: 0,
          logs: [],
        },
      });
      expect(
        screen.getByText("geofencingDrawer.dataProcessing.successRate"),
      ).toBeInTheDocument();
    });

    it("shows 100% success rate when all rows are valid", () => {
      renderResults({
        uploadResponse: {
          ...defaultUploadResponse,
          totalRow: 50,
          validLocation: 50,
          invalidLocation: 0,
          duplicateLocation: 0,
          logs: [],
        },
      });
      expect(
        screen.getByText("geofencingDrawer.dataProcessing.successRate"),
      ).toBeInTheDocument();
    });

    it("shows success rate between 70 and 100 as percentage", () => {
      renderResults({
        uploadResponse: {
          ...defaultUploadResponse,
          totalRow: 100,
          validLocation: 75,
          invalidLocation: 25,
          duplicateLocation: 0,
          logs: [],
        },
      });
      expect(
        screen.getByText("geofencingDrawer.dataProcessing.successRate"),
      ).toBeInTheDocument();
    });

    it("shows success rate below 70 as percentage", () => {
      renderResults({
        uploadResponse: {
          ...defaultUploadResponse,
          totalRow: 100,
          validLocation: 50,
          invalidLocation: 50,
          duplicateLocation: 0,
          logs: [],
        },
      });
      expect(
        screen.getByText("geofencingDrawer.dataProcessing.successRate"),
      ).toBeInTheDocument();
    });
  });

  describe("conditional messages", () => {
    it("shows valid locations message when validLocation > 0", () => {
      renderResults();
      expect(
        screen.getByText("geofencingDrawer.dataProcessing.locationsVerified"),
      ).toBeInTheDocument();
    });

    it("does not show valid locations message when validLocation is 0", () => {
      renderResults({
        uploadResponse: {
          ...defaultUploadResponse,
          validLocation: 0,
          invalidLocation: 100,
          duplicateLocation: 0,
          logs: [],
        },
      });
      expect(
        screen.queryByText("geofencingDrawer.dataProcessing.locationsVerified"),
      ).not.toBeInTheDocument();
    });

    it("shows invalid locations message when invalidLocation > 0", () => {
      renderResults();
      expect(
        screen.getByText("geofencingDrawer.dataProcessing.invalidLocations"),
      ).toBeInTheDocument();
    });

    it("does not show invalid message when invalidLocation is 0", () => {
      renderResults({
        uploadResponse: {
          ...defaultUploadResponse,
          validLocation: 100,
          invalidLocation: 0,
          duplicateLocation: 0,
          logs: [],
        },
      });
      expect(
        screen.queryByText("geofencingDrawer.dataProcessing.invalidLocations"),
      ).not.toBeInTheDocument();
    });

    it("shows duplicate locations message when duplicateLocation > 0", () => {
      renderResults();
      expect(
        screen.getByText("geofencingDrawer.dataProcessing.duplicateLocations"),
      ).toBeInTheDocument();
    });

    it("does not show duplicate message when duplicateLocation is 0", () => {
      renderResults({
        uploadResponse: {
          ...defaultUploadResponse,
          duplicateLocation: 0,
          logs: [],
        },
      });
      expect(
        screen.queryByText(
          "geofencingDrawer.dataProcessing.duplicateLocations",
        ),
      ).not.toBeInTheDocument();
    });
  });

  describe("View Logs button", () => {
    it("calls onViewLog when View Logs is clicked", async () => {
      const { onViewLog } = renderResults({
        uploadResponse: {
          ...defaultUploadResponse,
          validLocation: 80,
          totalRow: 100,
        },
      });
      const user = userEvent.setup();
      await user.click(
        screen.getByRole("button", {
          name: "geofencingDrawer.dataProcessing.viewLogs",
        }),
      );
      expect(onViewLog).toHaveBeenCalledTimes(1);
    });

    it("disables View Logs when all rows are valid", () => {
      renderResults({
        uploadResponse: {
          ...defaultUploadResponse,
          totalRow: 50,
          validLocation: 50,
          invalidLocation: 0,
          duplicateLocation: 0,
          logs: [],
        },
      });
      const button = screen.getByRole("button", {
        name: "geofencingDrawer.dataProcessing.viewLogs",
      });
      expect(button).toBeDisabled();
    });

    it("enables View Logs when there are invalid or duplicate rows", () => {
      renderResults();
      const button = screen.getByRole("button", {
        name: "geofencingDrawer.dataProcessing.viewLogs",
      });
      expect(button).not.toBeDisabled();
    });
  });
});
