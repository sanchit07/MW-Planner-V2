import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

import { Gallery } from "../Gallery";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

describe("Gallery", () => {
  const defaultItems = [
    {
      id: "1",
      src: "/image1.jpg",
      type: "image" as const,
      alt: "Image 1",
    },
    {
      id: "2",
      src: "/image2.jpg",
      type: "image" as const,
      alt: "Image 2",
      caption: "Caption 2",
    },
    {
      id: "3",
      src: "/video1.mp4",
      type: "video" as const,
      alt: "Video 1",
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    // Mock scrollTo for jsdom compatibility
    Element.prototype.scrollTo = vi.fn();
    HTMLElement.prototype.scrollTo = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("Rendering", () => {
    it("renders gallery with items", () => {
      const { container } = render(<Gallery items={defaultItems} />);
      const mainImage = container.querySelector(
        'img[src="/image1.jpg"][class*="object-contain"]',
      );
      expect(mainImage).toBeInTheDocument();
    });

    it("renders empty state when no items", () => {
      render(<Gallery items={[]} />);
      expect(screen.getByText("No items to display")).toBeInTheDocument();
    });

    it("renders thumbnails by default", () => {
      render(<Gallery items={defaultItems} />);
      const thumbnails = screen.getAllByRole("button");
      expect(thumbnails.length).toBeGreaterThan(0);
    });

    it("does not render thumbnails when showThumbnails is false", () => {
      render(<Gallery items={defaultItems} showThumbnails={false} />);
      const thumbnails = screen.queryAllByRole("button", {
        name: /View Image|View Video/i,
      });
      expect(thumbnails.length).toBe(0);
    });

    it("renders counter when showCounter is true and multiple items", () => {
      render(<Gallery items={defaultItems} showCounter={true} />);
      expect(screen.getByText(/1 \/ 3/)).toBeInTheDocument();
    });

    it("does not render counter when showCounter is false", () => {
      render(<Gallery items={defaultItems} showCounter={false} />);
      expect(screen.queryByText(/1 \/ 3/)).not.toBeInTheDocument();
    });

    it("renders caption when provided", async () => {
      const user = userEvent.setup();
      render(<Gallery items={defaultItems} />);
      const nextButton = screen.getByRole("button", { name: "aria.nextItem" });
      await user.click(nextButton);
      await waitFor(() => {
        expect(screen.getByText("Caption 2")).toBeInTheDocument();
      });
    });

    it("renders navigation buttons when showNavigation is true", () => {
      render(<Gallery items={defaultItems} showNavigation={true} />);
      const prevButton = screen.getByRole("button", {
        name: "aria.previousItem",
      });
      const nextButton = screen.getByRole("button", { name: "aria.nextItem" });
      expect(prevButton).toBeInTheDocument();
      expect(nextButton).toBeInTheDocument();
    });

    it("applies custom className", () => {
      const { container } = render(
        <Gallery items={defaultItems} className="custom-class" />,
      );
      const gallery = container.firstChild as HTMLElement;
      expect(gallery.className).toContain("custom-class");
    });
  });

  describe("Navigation", () => {
    it("navigates to next item when next button is clicked", async () => {
      const user = userEvent.setup();
      const { container } = render(<Gallery items={defaultItems} />);

      expect(
        container.querySelector(
          'img[src="/image1.jpg"][class*="object-contain"]',
        ),
      ).toBeInTheDocument();

      const nextButton = screen.getByRole("button", { name: "aria.nextItem" });
      await user.click(nextButton);

      await waitFor(() => {
        expect(
          container.querySelector(
            'img[src="/image2.jpg"][class*="object-contain"]',
          ),
        ).toBeInTheDocument();
      });
    });

    it("navigates to previous item when previous button is clicked", async () => {
      const user = userEvent.setup();
      const { container } = render(<Gallery items={defaultItems} />);

      const nextButton = screen.getByRole("button", { name: "aria.nextItem" });
      await user.click(nextButton);

      await waitFor(() => {
        expect(
          container.querySelector(
            'img[src="/image2.jpg"][class*="object-contain"]',
          ),
        ).toBeInTheDocument();
      });

      const prevButton = screen.getByRole("button", {
        name: "aria.previousItem",
      });
      await user.click(prevButton);

      await waitFor(() => {
        expect(
          container.querySelector(
            'img[src="/image1.jpg"][class*="object-contain"]',
          ),
        ).toBeInTheDocument();
      });
    });

    it("navigates to item when thumbnail is clicked", async () => {
      const user = userEvent.setup();
      const { container } = render(<Gallery items={defaultItems} />);

      const thumbnails = screen.getAllByRole("button", {
        name: /View Image|View Video/i,
      });
      if (thumbnails.length > 1) {
        await user.click(thumbnails[1]);

        await waitFor(() => {
          expect(
            container.querySelector(
              'img[src="/image2.jpg"][class*="object-contain"]',
            ),
          ).toBeInTheDocument();
        });
      }
    });

    it("wraps to last item when going previous from first in loop mode", async () => {
      const user = userEvent.setup();
      const { container } = render(
        <Gallery items={defaultItems} loop={true} />,
      );

      const prevButton = screen.getByRole("button", {
        name: "aria.previousItem",
      });
      await user.click(prevButton);

      await waitFor(() => {
        const video = container.querySelector('video[src="/video1.mp4"]');
        expect(video).toBeInTheDocument();
      });
    });

    it("wraps to first item when going next from last in loop mode", async () => {
      const user = userEvent.setup();
      const { container } = render(
        <Gallery items={defaultItems} loop={true} />,
      );

      const nextButton = screen.getByRole("button", { name: "aria.nextItem" });
      await user.click(nextButton);
      await user.click(nextButton);
      await user.click(nextButton);

      await waitFor(() => {
        expect(
          container.querySelector(
            'img[src="/image1.jpg"][class*="object-contain"]',
          ),
        ).toBeInTheDocument();
      });
    });

    it("does not wrap when loop is false", async () => {
      const user = userEvent.setup();
      render(<Gallery items={defaultItems} loop={false} />);

      const prevButton = screen.getByRole("button", {
        name: "aria.previousItem",
      });
      expect(prevButton).toBeDisabled();

      const nextButton = screen.getByRole("button", { name: "aria.nextItem" });
      await user.click(nextButton);
      await user.click(nextButton);

      expect(nextButton).toBeDisabled();
    });
  });

  describe("Keyboard Navigation", () => {
    it("navigates with ArrowLeft key", async () => {
      const user = userEvent.setup();
      const { container } = render(<Gallery items={defaultItems} />);

      await user.keyboard("{ArrowLeft}");

      await waitFor(() => {
        const video = container.querySelector('video[src="/video1.mp4"]');
        expect(video).toBeInTheDocument();
      });
    });

    it("navigates with ArrowRight key", async () => {
      const user = userEvent.setup();
      const { container } = render(<Gallery items={defaultItems} />);

      await user.keyboard("{ArrowRight}");

      await waitFor(() => {
        expect(
          container.querySelector(
            'img[src="/image2.jpg"][class*="object-contain"]',
          ),
        ).toBeInTheDocument();
      });
    });
  });

  describe("Callbacks", () => {
    it("calls onItemChange when item changes", async () => {
      const user = userEvent.setup();
      const onItemChange = vi.fn();
      render(<Gallery items={defaultItems} onItemChange={onItemChange} />);

      const nextButton = screen.getByRole("button", { name: "aria.nextItem" });
      await user.click(nextButton);

      await waitFor(() => {
        expect(onItemChange).toHaveBeenCalled();
      });
    });

    it("calls onThumbnailClick when thumbnail is clicked", async () => {
      const user = userEvent.setup();
      const onThumbnailClick = vi.fn();
      render(
        <Gallery items={defaultItems} onThumbnailClick={onThumbnailClick} />,
      );

      const thumbnails = screen.getAllByRole("button", {
        name: /View Image|View Video/i,
      });
      if (thumbnails.length > 0) {
        await user.click(thumbnails[0]);

        await waitFor(() => {
          expect(onThumbnailClick).toHaveBeenCalled();
        });
      }
    });
  });

  describe("Custom Rendering", () => {
    it("renders custom item when renderCustomItem is provided", () => {
      render(
        <Gallery
          items={defaultItems}
          renderCustomItem={(item) => <div>Custom {item.alt}</div>}
        />,
      );
      expect(screen.getByText("Custom Image 1")).toBeInTheDocument();
    });

    it("renders custom thumbnail when renderCustomThumbnail is provided", () => {
      render(
        <Gallery
          items={defaultItems}
          renderCustomThumbnail={(item) => <div>Thumb {item.alt}</div>}
        />,
      );
      expect(screen.getByText("Thumb Image 1")).toBeInTheDocument();
    });
  });

  describe("Video Items", () => {
    it("renders video element for video items", () => {
      const videoItems = [
        {
          id: "1",
          src: "/video1.mp4",
          type: "video" as const,
          alt: "Video 1",
        },
      ];
      const { container } = render(<Gallery items={videoItems} />);
      const video = container.querySelector('video[src="/video1.mp4"]');
      expect(video).toBeInTheDocument();
    });

    it("renders video poster when provided", () => {
      const videoItems = [
        {
          id: "1",
          src: "/video1.mp4",
          type: "video" as const,
          alt: "Video 1",
          poster: "/poster.jpg",
        },
      ];
      const { container } = render(<Gallery items={videoItems} />);
      const video = container.querySelector('video[src="/video1.mp4"]');
      expect(video).toHaveAttribute("poster", "/poster.jpg");
    });
  });

  describe("Edge Cases", () => {
    it("handles single item", () => {
      const { container } = render(<Gallery items={[defaultItems[0]]} />);
      expect(
        container.querySelector(
          'img[src="/image1.jpg"][class*="object-contain"]',
        ),
      ).toBeInTheDocument();
    });

    it("handles initialIndex out of bounds", () => {
      const { container } = render(
        <Gallery items={defaultItems} initialIndex={10} />,
      );
      // When initialIndex is out of bounds, it clamps to the last item (video)
      const video = container.querySelector('video[src="/video1.mp4"]');
      expect(video).toBeInTheDocument();
    });

    it("handles negative initialIndex", () => {
      const { container } = render(
        <Gallery items={defaultItems} initialIndex={-1} />,
      );
      expect(
        container.querySelector(
          'img[src="/image1.jpg"][class*="object-contain"]',
        ),
      ).toBeInTheDocument();
    });

    it("handles items with custom thumbnail", () => {
      const itemsWithThumbnail = [
        {
          ...defaultItems[0],
          thumbnail: "/thumb1.jpg",
        },
      ];
      const { container } = render(<Gallery items={itemsWithThumbnail} />);
      expect(
        container.querySelector(
          'img[src="/image1.jpg"][class*="object-contain"]',
        ),
      ).toBeInTheDocument();
    });
  });

  describe("Broken images", () => {
    it("shows the broken-image placeholder when the main image fails to load", () => {
      render(<Gallery items={[defaultItems[0]]} />);
      const img = screen.getByAltText("Image 1");
      fireEvent.error(img);
      expect(
        screen.getByTestId("inventory-thumbnail-fallback"),
      ).toBeInTheDocument();
    });
  });
});
