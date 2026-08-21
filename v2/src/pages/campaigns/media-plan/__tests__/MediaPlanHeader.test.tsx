import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";

import MediaPlanHeader from "../MediaPlanHeader";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

describe("MediaPlanHeader", () => {
  const defaultProps = {
    viewType: "presentation" as const,
    selectedTheme: "default",
    onViewTypeChange: vi.fn(),
    onThemeChange: vi.fn(),
    onDownload: vi.fn(),
    onShare: vi.fn(),
    isDownloading: false,
  };

  it("renders view type dropdown with current view", () => {
    render(<MediaPlanHeader {...defaultProps} />);
    expect(
      screen.getByRole("button", {
        name: /media_plan\.view_type\.presentation/i,
      }),
    ).toBeInTheDocument();
  });

  it("renders analytics view type when viewType is analytics", () => {
    render(<MediaPlanHeader {...defaultProps} viewType="analytics" />);
    expect(
      screen.getByRole("button", { name: /media_plan\.view_type\.analytics/i }),
    ).toBeInTheDocument();
  });

  it("calls onViewTypeChange when presentation option is selected", async () => {
    const onViewTypeChange = vi.fn();
    const user = userEvent.setup();
    render(
      <MediaPlanHeader
        {...defaultProps}
        viewType="analytics"
        onViewTypeChange={onViewTypeChange}
      />,
    );
    await user.click(
      screen.getByRole("button", { name: /media_plan\.view_type\.analytics/i }),
    );
    await user.click(screen.getByText("media_plan.view_type.presentation"));
    expect(onViewTypeChange).toHaveBeenCalledWith("presentation");
  });

  it("calls onDownload when download button is clicked", async () => {
    const onDownload = vi.fn();
    const user = userEvent.setup();
    render(<MediaPlanHeader {...defaultProps} onDownload={onDownload} />);
    const downloadBtn = screen.getByRole("button", {
      name: /media_plan\.actions\.download_ppt/i,
    });
    await user.click(downloadBtn);
    expect(onDownload).toHaveBeenCalled();
  });

  it("shows processing and disables download when isDownloading is true", () => {
    render(<MediaPlanHeader {...defaultProps} isDownloading />);
    const downloadBtn = screen.getByRole("button", {
      name: /media_plan\.actions\.processing/i,
    });
    expect(downloadBtn).toBeDisabled();
  });

  it("shows preparing and disables download when isDataLoading is true", () => {
    render(<MediaPlanHeader {...defaultProps} isDataLoading />);
    const downloadBtn = screen.getByRole("button", {
      name: /media_plan\.actions\.preparing/i,
    });
    expect(downloadBtn).toBeDisabled();
  });

  it("prefers the processing label over preparing when both are true", () => {
    render(<MediaPlanHeader {...defaultProps} isDownloading isDataLoading />);
    expect(
      screen.getByRole("button", { name: /media_plan\.actions\.processing/i }),
    ).toBeDisabled();
  });

  it("shows download Excel label when viewType is analytics", () => {
    render(<MediaPlanHeader {...defaultProps} viewType="analytics" />);
    expect(
      screen.getByRole("button", {
        name: /media_plan\.actions\.download_excel/i,
      }),
    ).toBeInTheDocument();
  });

  it("shows the theme selector when viewType is presentation", () => {
    render(<MediaPlanHeader {...defaultProps} viewType="presentation" />);
    expect(
      screen.getByRole("button", { name: /mediaPlan\.theme\.primary/i }),
    ).toBeInTheDocument();
  });

  it("hides the theme selector when viewType is analytics", () => {
    render(<MediaPlanHeader {...defaultProps} viewType="analytics" />);
    expect(
      screen.queryByRole("button", { name: /mediaPlan\.theme\.primary/i }),
    ).not.toBeInTheDocument();
  });
});
