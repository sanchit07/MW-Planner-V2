import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

import { Carousel, CarouselSlide, ImageCarousel } from "../Carousel";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

describe("Carousel", () => {
  const defaultSlides = [
    <CarouselSlide key="1">
      <div>Slide 1</div>
    </CarouselSlide>,
    <CarouselSlide key="2">
      <div>Slide 2</div>
    </CarouselSlide>,
    <CarouselSlide key="3">
      <div>Slide 3</div>
    </CarouselSlide>,
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
    vi.clearAllTimers();
  });

  describe("Rendering", () => {
    it("renders carousel with slides", () => {
      render(<Carousel>{defaultSlides}</Carousel>);
      expect(screen.getByText("Slide 1")).toBeInTheDocument();
    });

    it("renders navigation arrows by default", () => {
      render(<Carousel>{defaultSlides}</Carousel>);
      const prevButton = screen.getByRole("button", {
        name: "aria.previousSlide",
      });
      const nextButton = screen.getByRole("button", { name: "aria.nextSlide" });
      expect(prevButton).toBeInTheDocument();
      expect(nextButton).toBeInTheDocument();
    });

    it("renders dots indicator by default", () => {
      render(<Carousel>{defaultSlides}</Carousel>);
      const dots = screen.getAllByRole("button", { name: /aria.goToSlide/i });
      expect(dots.length).toBeGreaterThan(0);
    });

    it("renders slide counter by default", () => {
      render(<Carousel>{defaultSlides}</Carousel>);
      expect(screen.getByText(/1 \/ 3/)).toBeInTheDocument();
    });

    it("does not render arrows when showArrows is false", () => {
      render(<Carousel showArrows={false}>{defaultSlides}</Carousel>);
      expect(
        screen.queryByRole("button", { name: "aria.previousSlide" }),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: "aria.nextSlide" }),
      ).not.toBeInTheDocument();
    });

    it("does not render dots when showDots is false", () => {
      render(<Carousel showDots={false}>{defaultSlides}</Carousel>);
      const dots = screen.queryAllByRole("button", { name: /aria.goToSlide/i });
      expect(dots.length).toBe(0);
    });

    it("does not render counter when showTotalCount is false", () => {
      render(<Carousel showTotalCount={false}>{defaultSlides}</Carousel>);
      expect(screen.queryByText(/1 \/ 3/)).not.toBeInTheDocument();
    });

    it("renders play button when showPlayButton is true", () => {
      render(<Carousel showPlayButton={true}>{defaultSlides}</Carousel>);
      const playButton = screen.getByRole("button", {
        name: /aria.startAutoplay|aria.pauseAutoplay/i,
      });
      expect(playButton).toBeInTheDocument();
    });

    it("applies custom className", () => {
      const { container } = render(
        <Carousel className="custom-class">{defaultSlides}</Carousel>,
      );
      const carousel = container.firstChild as HTMLElement;
      expect(carousel.className).toContain("custom-class");
    });
  });

  describe("Navigation", () => {
    it("navigates to next slide when next button is clicked", async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      render(<Carousel>{defaultSlides}</Carousel>);

      expect(screen.getByText("Slide 1")).toBeInTheDocument();
      expect(screen.getByText(/1 \/ 3/)).toBeInTheDocument();

      const nextButton = screen.getByRole("button", { name: "aria.nextSlide" });
      await user.click(nextButton);

      await waitFor(
        () => {
          expect(screen.getByText(/2 \/ 3/)).toBeInTheDocument();
        },
        { timeout: 3000 },
      );
    });

    it("navigates to previous slide when previous button is clicked", async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      render(<Carousel>{defaultSlides}</Carousel>);

      const nextButton = screen.getByRole("button", { name: "aria.nextSlide" });
      await user.click(nextButton);

      await waitFor(
        () => {
          expect(screen.getByText(/2 \/ 3/)).toBeInTheDocument();
        },
        { timeout: 3000 },
      );

      const prevButton = screen.getByRole("button", {
        name: "aria.previousSlide",
      });
      await user.click(prevButton);

      await waitFor(
        () => {
          expect(screen.getByText(/1 \/ 3/)).toBeInTheDocument();
        },
        { timeout: 3000 },
      );
    });

    it("navigates to specific slide when dot is clicked", async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      render(<Carousel>{defaultSlides}</Carousel>);

      const dot3 = screen.getAllByRole("button", {
        name: /aria\.goToSlide/i,
      })[2];
      await user.click(dot3);

      await waitFor(
        () => {
          expect(screen.getByText(/3 \/ 3/)).toBeInTheDocument();
        },
        { timeout: 3000 },
      );
    });

    it("wraps to last slide when going previous from first slide in infinite mode", async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      render(<Carousel infinite={true}>{defaultSlides}</Carousel>);

      const prevButton = screen.getByRole("button", {
        name: "aria.previousSlide",
      });
      await user.click(prevButton);

      await waitFor(
        () => {
          expect(screen.getByText(/3 \/ 3/)).toBeInTheDocument();
        },
        { timeout: 3000 },
      );
    });

    it("wraps to first slide when going next from last slide in infinite mode", async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      render(<Carousel infinite={true}>{defaultSlides}</Carousel>);

      const nextButton = screen.getByRole("button", { name: "aria.nextSlide" });
      await user.click(nextButton);
      await waitFor(
        () => expect(screen.getByText(/2 \/ 3/)).toBeInTheDocument(),
        { timeout: 3000 },
      );
      await user.click(nextButton);
      await waitFor(
        () => expect(screen.getByText(/3 \/ 3/)).toBeInTheDocument(),
        { timeout: 3000 },
      );
      await user.click(nextButton);

      await waitFor(
        () => {
          expect(screen.getByText(/1 \/ 3/)).toBeInTheDocument();
        },
        { timeout: 3000 },
      );
    });

    it("does not wrap when infinite is false", async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      render(<Carousel infinite={false}>{defaultSlides}</Carousel>);

      const prevButton = screen.getByRole("button", {
        name: "aria.previousSlide",
      });
      await user.click(prevButton);

      await waitFor(
        () => {
          expect(screen.getByText(/1 \/ 3/)).toBeInTheDocument();
        },
        { timeout: 3000 },
      );
    });
  });

  describe("Keyboard Navigation", () => {
    it("navigates with ArrowLeft key", async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      const { container } = render(<Carousel>{defaultSlides}</Carousel>);

      const carousel = container.querySelector('[tabindex="0"]') as HTMLElement;
      expect(carousel).toBeInTheDocument();
      carousel.focus();
      await user.keyboard("{ArrowLeft}");

      await waitFor(
        () => {
          expect(screen.getByText(/3 \/ 3/)).toBeInTheDocument();
        },
        { timeout: 3000 },
      );
    });

    it("navigates with ArrowRight key", async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      const { container } = render(<Carousel>{defaultSlides}</Carousel>);

      const carousel = container.querySelector('[tabindex="0"]') as HTMLElement;
      expect(carousel).toBeInTheDocument();
      carousel.focus();
      await user.keyboard("{ArrowRight}");

      await waitFor(
        () => {
          expect(screen.getByText(/2 \/ 3/)).toBeInTheDocument();
        },
        { timeout: 3000 },
      );
    });

    it("toggles autoplay with Space key", async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      const { container } = render(
        <Carousel showPlayButton={true}>{defaultSlides}</Carousel>,
      );

      const carousel = container.querySelector('[tabindex="0"]') as HTMLElement;
      expect(carousel).toBeInTheDocument();
      carousel.focus();
      await user.keyboard(" ");

      await waitFor(
        () => {
          const playButton = screen.getByRole("button", {
            name: /aria.pauseAutoplay/i,
          });
          expect(playButton).toBeInTheDocument();
        },
        { timeout: 3000 },
      );
    });
  });

  describe("Autoplay", () => {
    it("starts autoplay when autoplay prop is true", async () => {
      vi.useRealTimers();
      render(
        <Carousel autoplay={true} autoplayInterval={500}>
          {defaultSlides}
        </Carousel>,
      );

      expect(screen.getByText("Slide 1")).toBeInTheDocument();
      expect(screen.getByText(/1 \/ 3/)).toBeInTheDocument();

      await waitFor(
        () => {
          expect(screen.getByText(/2 \/ 3/)).toBeInTheDocument();
        },
        { timeout: 2000 },
      );
    });

    it("pauses autoplay when mouse enters", async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      const { container } = render(
        <Carousel autoplay={true} autoplayInterval={500}>
          {defaultSlides}
        </Carousel>,
      );

      const carousel = container.querySelector('[tabindex="0"]') as HTMLElement;
      expect(carousel).toBeInTheDocument();
      await user.hover(carousel);

      await new Promise((resolve) => setTimeout(resolve, 1000));

      await waitFor(
        () => {
          expect(screen.getByText(/1 \/ 3/)).toBeInTheDocument();
        },
        { timeout: 2000 },
      );
    });

    it("resumes autoplay when mouse leaves", async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      const { container } = render(
        <Carousel autoplay={true} autoplayInterval={500}>
          {defaultSlides}
        </Carousel>,
      );

      const carousel = container.querySelector('[tabindex="0"]') as HTMLElement;
      expect(carousel).toBeInTheDocument();
      await user.hover(carousel);
      await user.unhover(carousel);

      await new Promise((resolve) => setTimeout(resolve, 600));

      await waitFor(
        () => {
          expect(screen.getByText(/2 \/ 3/)).toBeInTheDocument();
        },
        { timeout: 2000 },
      );
    });

    it("toggles autoplay when play button is clicked", async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      render(<Carousel showPlayButton={true}>{defaultSlides}</Carousel>);

      const playButton = screen.getByRole("button", {
        name: /aria.startAutoplay/i,
      });
      await user.click(playButton);

      await waitFor(
        () => {
          const pauseButton = screen.getByRole("button", {
            name: /aria.pauseAutoplay/i,
          });
          expect(pauseButton).toBeInTheDocument();
        },
        { timeout: 3000 },
      );
    });
  });

  describe("Touch/Swipe Support", () => {
    it("handles touch start", () => {
      render(<Carousel>{defaultSlides}</Carousel>);
      const carousel = screen.getByText("Slide 1").closest("div");
      if (carousel) {
        const touchStartEvent = new TouchEvent("touchstart", {
          bubbles: true,
          cancelable: true,
        });
        Object.defineProperty(touchStartEvent, "targetTouches", {
          value: [{ clientX: 100 }],
        });
        carousel.dispatchEvent(touchStartEvent);
        expect(carousel).toBeInTheDocument();
      }
    });
  });

  describe("Callbacks", () => {
    it("calls onSlideChange when slide changes", async () => {
      vi.useRealTimers();
      const user = userEvent.setup();
      const onSlideChange = vi.fn();
      render(
        <Carousel onSlideChange={onSlideChange}>{defaultSlides}</Carousel>,
      );

      const nextButton = screen.getByRole("button", { name: "aria.nextSlide" });
      await user.click(nextButton);

      await waitFor(
        () => {
          expect(onSlideChange).toHaveBeenCalledWith(1);
        },
        { timeout: 3000 },
      );
    });
  });

  describe("ImageCarousel", () => {
    const images = [
      { src: "/image1.jpg", alt: "Image 1" },
      { src: "/image2.jpg", alt: "Image 2", caption: "Caption 2" },
    ];

    it("renders ImageCarousel with images", () => {
      render(<ImageCarousel images={images} />);
      expect(screen.getByAltText("Image 1")).toBeInTheDocument();
    });

    it("renders image captions when provided", () => {
      render(<ImageCarousel images={images} />);
      expect(screen.getByText("Caption 2")).toBeInTheDocument();
    });

    it("applies object-cover when objectFit is cover", () => {
      const { container } = render(
        <ImageCarousel images={images} objectFit="cover" />,
      );
      const img = container.querySelector("img");
      expect(img?.className).toContain("object-cover");
    });

    it("applies object-contain when objectFit is contain", () => {
      const { container } = render(
        <ImageCarousel images={images} objectFit="contain" />,
      );
      const img = container.querySelector("img");
      expect(img?.className).toContain("object-contain");
    });
  });

  describe("Edge Cases", () => {
    it("handles single slide", () => {
      render(
        <Carousel>
          {[
            <CarouselSlide key="single">
              <div>Single Slide</div>
            </CarouselSlide>,
          ]}
        </Carousel>,
      );
      expect(screen.getByText("Single Slide")).toBeInTheDocument();
    });

    it("handles empty children", () => {
      const { container } = render(<Carousel>{[]}</Carousel>);
      const carousel = container.querySelector('[tabindex="0"]');
      expect(carousel).toBeInTheDocument();
    });

    it("handles slidesToShow greater than 1", () => {
      render(<Carousel slidesToShow={2}>{defaultSlides}</Carousel>);
      expect(screen.getByText("Slide 1")).toBeInTheDocument();
    });
  });
});
