import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { MediaOwnerDropdown } from "../MediaOwnerDropdown";

const mockFilterCompanies = vi.fn();

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("../../../services/campaign/campaignSlice", () => ({
  useLazyFilterCompaniesQuery: () => [mockFilterCompanies, {}],
}));

vi.mock("@components/ui/MultiSelect", () => ({
  default: function MultiSelectMock({
    value,
    onChange,
    placeholder,
    disabled,
    onRemoteLoad,
  }: {
    value: string[];
    onChange: (v: string[]) => void;
    placeholder?: string;
    disabled?: boolean;
    onRemoteLoad?: () => void;
  }) {
    return (
      <div data-testid="media-owner-multiselect">
        <span>{placeholder}</span>
        {disabled && <span data-testid="disabled">disabled</span>}
        <button
          type="button"
          onClick={() => onRemoteLoad?.()}
          aria-label="open"
        >
          Open
        </button>
        <button
          type="button"
          onClick={() => onChange([...value, "mo-1"])}
          aria-label="add"
        >
          Add
        </button>
        <span data-value={value.join(",")}>{value.length} selected</span>
      </div>
    );
  },
}));

describe("MediaOwnerDropdown", () => {
  const defaultProps = {
    value: [],
    onChange: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockFilterCompanies.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: [
            { id: "mo-1", name: "Owner 1" },
            { id: "mo-2", name: "Owner 2" },
          ],
          meta: { total: 2, offset: 0, limit: 50 },
        }),
    });
  });

  describe("rendering", () => {
    it("renders with default placeholder", () => {
      render(<MediaOwnerDropdown {...defaultProps} />);
      expect(
        screen.getByText("mediaOwnerDropdown.placeholder"),
      ).toBeInTheDocument();
    });

    it("renders with custom placeholder", () => {
      render(
        <MediaOwnerDropdown
          {...defaultProps}
          placeholder="Choose media owners"
        />,
      );
      expect(screen.getByText("Choose media owners")).toBeInTheDocument();
    });

    it("renders selected count", () => {
      render(<MediaOwnerDropdown {...defaultProps} value={["mo-1"]} />);
      expect(screen.getByText("1 selected")).toBeInTheDocument();
    });

    it("passes id to multiselect", () => {
      render(
        <MediaOwnerDropdown {...defaultProps} id="custom-media-owner-id" />,
      );
      expect(screen.getByTestId("media-owner-multiselect")).toBeInTheDocument();
    });
  });

  describe("interactions", () => {
    it("calls onChange when selection changes", async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      render(<MediaOwnerDropdown {...defaultProps} onChange={onChange} />);
      await user.click(screen.getByRole("button", { name: /add/i }));
      expect(onChange).toHaveBeenCalledWith(["mo-1"]);
    });

    it("calls filterCompanies when dropdown is opened via onRemoteLoad", async () => {
      const user = userEvent.setup();
      render(<MediaOwnerDropdown {...defaultProps} />);
      await user.click(screen.getByRole("button", { name: /open/i }));
      await waitFor(() => {
        expect(mockFilterCompanies).toHaveBeenCalledWith(
          expect.objectContaining({
            offset: 0,
            limit: 50,
            company_type: "MEDIA_OWNER",
          }),
        );
      });
    });

    it("uses custom companyType when provided", async () => {
      const user = userEvent.setup();
      render(
        <MediaOwnerDropdown {...defaultProps} companyType="MEDIA_OPERATOR" />,
      );
      await user.click(screen.getByRole("button", { name: /open/i }));
      await waitFor(() => {
        expect(mockFilterCompanies).toHaveBeenCalledWith(
          expect.objectContaining({
            company_type: "MEDIA_OPERATOR",
          }),
        );
      });
    });
  });

  describe("disabled state", () => {
    it("renders disabled when disabled prop is true", () => {
      render(<MediaOwnerDropdown {...defaultProps} disabled />);
      expect(screen.getByTestId("disabled")).toBeInTheDocument();
    });
  });
});
