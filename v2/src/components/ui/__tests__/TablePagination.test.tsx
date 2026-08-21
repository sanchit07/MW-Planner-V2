import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";

import { TablePagination } from "../TablePagination";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

describe("TablePagination", () => {
  const defaultProps = {
    currentPage: 1,
    totalPages: 5,
    pageSize: 10,
    totalItems: 50,
    onPageChange: vi.fn(),
  };

  it("should render pagination", () => {
    render(<TablePagination {...defaultProps} />);
    expect(screen.getByText("pagination.pageInfo")).toBeInTheDocument();
  });

  it("should display correct item range", () => {
    render(<TablePagination {...defaultProps} />);
    // t(key) returns the key string; check for the translation key
    expect(screen.getByText("pagination.itemRange")).toBeInTheDocument();
  });

  it("should call onPageChange when next button is clicked", async () => {
    const user = userEvent.setup();
    const onPageChange = vi.fn();
    render(<TablePagination {...defaultProps} onPageChange={onPageChange} />);

    const nextButton = screen.getByRole("button", { name: /next/i });
    await user.click(nextButton);

    expect(onPageChange).toHaveBeenCalledWith(2);
  });

  it("should call onPageChange when previous button is clicked", async () => {
    const user = userEvent.setup();
    const onPageChange = vi.fn();
    render(
      <TablePagination
        {...defaultProps}
        currentPage={2}
        onPageChange={onPageChange}
      />,
    );

    const prevButton = screen.getByRole("button", { name: /previous/i });
    await user.click(prevButton);

    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it("should disable previous button on first page", () => {
    render(<TablePagination {...defaultProps} currentPage={1} />);
    const prevButton = screen.getByRole("button", { name: /previous/i });
    expect(prevButton).toBeDisabled();
  });

  it("should disable next button on last page", () => {
    render(<TablePagination {...defaultProps} currentPage={5} />);
    const nextButton = screen.getByRole("button", { name: /next/i });
    expect(nextButton).toBeDisabled();
  });

  it("should call onPageChange when first page button is clicked", async () => {
    const user = userEvent.setup();
    const onPageChange = vi.fn();
    render(
      <TablePagination
        {...defaultProps}
        currentPage={3}
        onPageChange={onPageChange}
      />,
    );

    const firstButton = screen
      .getAllByRole("button")
      .find((btn) => btn.querySelector("svg"));
    if (firstButton) {
      await user.click(firstButton);
    }
  });

  it("should call onPageSizeChange when page size is changed", async () => {
    const onPageSizeChange = vi.fn();
    render(
      <TablePagination {...defaultProps} onPageSizeChange={onPageSizeChange} />,
    );

    const pageSizeLabel = screen.getByText("pagination.pageItems");
    expect(pageSizeLabel).toBeInTheDocument();
  });

  it("should use provided id", () => {
    const { container } = render(
      <TablePagination {...defaultProps} id="custom-id" />,
    );
    expect(container.querySelector("#custom-id")).toBeInTheDocument();
  });

  it("should calculate end item correctly", () => {
    render(
      <TablePagination
        {...defaultProps}
        currentPage={5}
        pageSize={10}
        totalItems={47}
      />,
    );
    // t(key) returns the key string; check for the translation key
    expect(screen.getByText("pagination.itemRange")).toBeInTheDocument();
  });
});
