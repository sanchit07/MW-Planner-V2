import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";

import AiSmartRecommendationPanel from "../AiSmartRecommendationPanel";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

// Lightweight Modal: render the body + action buttons when open.
vi.mock("@components/ui/Modal", () => ({
  Modal: ({
    isOpen,
    children,
    primaryButtonText,
    onPrimaryAction,
    secondaryButtonText,
    onSecondaryAction,
  }: {
    isOpen: boolean;
    children: React.ReactNode;
    primaryButtonText: string;
    onPrimaryAction: () => void;
    secondaryButtonText: string;
    onSecondaryAction: () => void;
  }) =>
    isOpen ? (
      <div role="dialog">
        {children}
        <button onClick={onPrimaryAction}>{primaryButtonText}</button>
        <button onClick={onSecondaryAction}>{secondaryButtonText}</button>
      </div>
    ) : null,
}));

function renderPanel(
  props: Partial<React.ComponentProps<typeof AiSmartRecommendationPanel>> = {},
) {
  return render(
    <AiSmartRecommendationPanel
      onEditManually={vi.fn()}
      onView={vi.fn()}
      {...props}
    />,
  );
}

describe("AiSmartRecommendationPanel", () => {
  it("renders title, description and Edit Manually button", () => {
    renderPanel();
    expect(
      screen.getByText("inventories.aiRecommendation.title"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("inventories.aiRecommendation.editManually"),
    ).toBeInTheDocument();
  });

  it("calls onEditManually when the button is clicked", async () => {
    const onEditManually = vi.fn();
    renderPanel({ onEditManually });
    await userEvent.click(
      screen.getByText("inventories.aiRecommendation.editManually"),
    );
    expect(onEditManually).toHaveBeenCalledTimes(1);
  });

  it("disables Edit Manually when editDisabled is true", () => {
    renderPanel({ editDisabled: true });
    expect(
      screen
        .getByText("inventories.aiRecommendation.editManually")
        .closest("button"),
    ).toBeDisabled();
  });

  it("calls onView when the View button is clicked", async () => {
    const onView = vi.fn();
    renderPanel({ onView });
    await userEvent.click(screen.getByText("inventories.planSummary.view"));
    expect(onView).toHaveBeenCalledTimes(1);
  });

  it("disables View when viewDisabled is true", () => {
    renderPanel({ viewDisabled: true });
    expect(
      screen.getByText("inventories.planSummary.view").closest("button"),
    ).toBeDisabled();
  });

  it("does not render Restore when no handler is provided", () => {
    renderPanel();
    expect(
      screen.queryByText("inventories.aiRecommendation.restoreAi"),
    ).not.toBeInTheDocument();
  });

  it("confirms then calls onRestoreRecommendation", async () => {
    const onRestoreRecommendation = vi.fn();
    renderPanel({ onRestoreRecommendation });

    await userEvent.click(
      screen.getByRole("button", {
        name: "inventories.aiRecommendation.restoreAi",
      }),
    );
    // Confirm modal primary action.
    await userEvent.click(
      screen.getByText("inventories.aiRecommendation.restoreConfirm"),
    );
    expect(onRestoreRecommendation).toHaveBeenCalledTimes(1);
  });

  it("disables Restore when restoreDisabled is true", () => {
    renderPanel({ onRestoreRecommendation: vi.fn(), restoreDisabled: true });
    expect(
      screen.getByRole("button", {
        name: "inventories.aiRecommendation.restoreAi",
      }),
    ).toBeDisabled();
  });

  it("spins the restore icon while restoring", () => {
    renderPanel({ onRestoreRecommendation: vi.fn(), isRestoring: true });
    const btn = screen.getByRole("button", {
      name: "inventories.aiRecommendation.restoreAi",
    });
    expect(btn.querySelector("svg")?.getAttribute("class")).toContain(
      "animate-spin",
    );
  });

  it("does not spin the restore icon when not restoring", () => {
    renderPanel({ onRestoreRecommendation: vi.fn(), isRestoring: false });
    const btn = screen.getByRole("button", {
      name: "inventories.aiRecommendation.restoreAi",
    });
    expect(btn.querySelector("svg")?.getAttribute("class") ?? "").not.toContain(
      "animate-spin",
    );
  });
});
