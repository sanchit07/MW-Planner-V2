import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import ShareModalDrawer from "../ShareModalDrawer";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

const mockShowSuccess = vi.fn();
vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({ showSuccess: mockShowSuccess }),
}));

const writeTextMock = vi.fn().mockResolvedValue(undefined);

describe("ShareModalDrawer", () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    shareUrl: "https://example.com/share/123",
  };

  beforeEach(() => {
    vi.clearAllMocks();
    writeTextMock.mockResolvedValue(undefined);
    vi.stubGlobal("navigator", {
      ...navigator,
      clipboard: { writeText: writeTextMock },
    });
  });

  it("renders nothing when isOpen is false", () => {
    render(<ShareModalDrawer {...defaultProps} isOpen={false} />);
    expect(screen.queryByText("shareModal.title")).not.toBeInTheDocument();
  });

  it("renders title and share link section when open", () => {
    render(<ShareModalDrawer {...defaultProps} />);
    expect(screen.getByText("shareModal.title")).toBeInTheDocument();
    expect(screen.getByText("shareModal.shareLink")).toBeInTheDocument();
    const inputs = document.querySelectorAll("input[readonly]");
    expect(inputs.length).toBeGreaterThan(0);
    expect((inputs[0] as HTMLInputElement).value).toBe(
      "https://example.com/share/123",
    );
  });

  // it("shows success when copy button is clicked", async () => {
  //   const user = userEvent.setup();
  //   render(<ShareModalDrawer {...defaultProps} />);
  //   const copyBtn = document.querySelector(
  //     'button[class*="cursor-pointer"]',
  //   ) as HTMLElement;
  //   expect(copyBtn).toBeInTheDocument();
  //   await user.click(copyBtn);
  //   expect(mockShowSuccess).toHaveBeenCalledWith("Link copied to clipboard");
  // });

  it("renders Share via section with the WhatsApp button (no Email button)", () => {
    render(<ShareModalDrawer {...defaultProps} />);
    expect(screen.getByText("shareModal.shareVia")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /WhatsApp/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Email/i }),
    ).not.toBeInTheDocument();
  });

  it("opens WhatsApp with encoded text when WhatsApp button is clicked", async () => {
    const onWhatsAppShare = vi.fn();
    const user = userEvent.setup();
    const openSpy = vi.spyOn(window, "open").mockImplementation(() => null);

    render(
      <ShareModalDrawer {...defaultProps} onWhatsAppShare={onWhatsAppShare} />,
    );
    await user.click(screen.getByRole("button", { name: /WhatsApp/i }));
    expect(openSpy).toHaveBeenCalledWith(
      expect.stringContaining("wa.me"),
      "_blank",
    );
    expect(onWhatsAppShare).toHaveBeenCalled();
    openSpy.mockRestore();
  });
});
