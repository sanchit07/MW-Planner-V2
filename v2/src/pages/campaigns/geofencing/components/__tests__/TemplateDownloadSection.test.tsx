import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { TemplateDownloadSection } from "../TemplateDownloadSection";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

function renderSection(
  props: Partial<React.ComponentProps<typeof TemplateDownloadSection>> = {},
) {
  const onDownloadTemplate = vi.fn();
  render(
    <TemplateDownloadSection
      onDownloadTemplate={onDownloadTemplate}
      {...props}
    />,
  );
  return { onDownloadTemplate };
}

describe("TemplateDownloadSection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("renders need template heading and description", () => {
      renderSection();
      expect(
        screen.getByText("targeting.geofencing.need_template"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("targeting.geofencing.download_template_desc"),
      ).toBeInTheDocument();
    });

    it("renders download template button", () => {
      renderSection();
      expect(
        screen.getByRole("button", {
          name: /targeting\.geofencing\.download_template/i,
        }),
      ).toBeInTheDocument();
    });
  });

  describe("interactions", () => {
    it("calls onDownloadTemplate when button is clicked", async () => {
      const { onDownloadTemplate } = renderSection();
      const user = userEvent.setup();
      await user.click(
        screen.getByRole("button", {
          name: /targeting\.geofencing\.download_template/i,
        }),
      );
      expect(onDownloadTemplate).toHaveBeenCalledTimes(1);
    });
  });
});
