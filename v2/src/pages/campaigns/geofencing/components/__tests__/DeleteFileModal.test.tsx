import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { DeleteFileModal } from "../DeleteFileModal";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

function renderModal(
  props: Partial<React.ComponentProps<typeof DeleteFileModal>> = {},
) {
  const onConfirm = vi.fn();
  const onCancel = vi.fn();
  render(
    <DeleteFileModal
      isOpen={true}
      fileName="locations.csv"
      onConfirm={onConfirm}
      onCancel={onCancel}
      {...props}
    />,
  );
  return { onConfirm, onCancel };
}

describe("DeleteFileModal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("visibility", () => {
    it("renders nothing when isOpen is false", () => {
      renderModal({ isOpen: false });
      expect(
        screen.queryByText("geofencingDrawer.deleteFile.title"),
      ).not.toBeInTheDocument();
    });

    it("renders modal when isOpen is true", () => {
      renderModal({ isOpen: true });
      expect(
        screen.getByText("geofencingDrawer.deleteFile.title"),
      ).toBeInTheDocument();
    });
  });

  describe("content", () => {
    it("renders message with file name when fileName is provided", () => {
      renderModal({ fileName: "my-file.csv" });
      expect(
        screen.getByText(/geofencingDrawer.deleteFile.message/),
      ).toBeInTheDocument();
      expect(screen.getByText(/"my-file.csv"/)).toBeInTheDocument();
    });

    it("renders fallback 'this file' when fileName is empty", () => {
      renderModal({ fileName: "" });
      expect(
        screen.getByText(/"geofencingDrawer.deleteFile.thisFile"/),
      ).toBeInTheDocument();
    });

    it("renders primary and secondary action buttons", () => {
      renderModal();
      expect(
        screen.getByRole("button", {
          name: /geofencingDrawer.deleteFile.confirm/i,
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", {
          name: /geofencingDrawer.deleteFile.cancel/i,
        }),
      ).toBeInTheDocument();
    });
  });

  describe("interactions", () => {
    it("calls onConfirm when Yes, Delete is clicked", async () => {
      const { onConfirm } = renderModal();
      const user = userEvent.setup();
      await user.click(
        screen.getByRole("button", {
          name: /geofencingDrawer.deleteFile.confirm/i,
        }),
      );
      expect(onConfirm).toHaveBeenCalledTimes(1);
    });

    it("calls onCancel when Don't Delete is clicked", async () => {
      const { onCancel } = renderModal();
      const user = userEvent.setup();
      await user.click(
        screen.getByRole("button", {
          name: /geofencingDrawer.deleteFile.cancel/i,
        }),
      );
      expect(onCancel).toHaveBeenCalledTimes(1);
    });
  });
});
