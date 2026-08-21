import html2canvas from "html2canvas";
import type { Map } from "mapbox-gl";

export interface ChartImageOptions {
  elementId: string;
  width?: string;
  height?: string;
  backgroundColor?: string;
  scale?: number;
  errorMessage?: string;
}

export interface MapImageOptions {
  mapInstance: Map;
  waitTime?: number;
  backgroundColor?: string | null;
  scale?: number;
  imageFormat?: "image/jpeg" | "image/png";
  imageQuality?: number;
  errorMessage?: string;
}

/**
 * Generic function to generate an image from an HTML element containing a Chart.js chart
 * @param options Configuration options for image generation
 * @returns Base64 encoded image string or undefined if generation fails
 */
export const generateChartImage = async (
  options: ChartImageOptions,
): Promise<string | undefined> => {
  const {
    elementId,
    width = "768px",
    height = "432px",
    backgroundColor = "#FFFFFF",
    scale = 2,
    errorMessage = `Failed to capture chart image for element: ${elementId}`,
  } = options;

  const chartElement = document.getElementById(elementId);
  if (!chartElement) {
    console.warn(`Element with id "${elementId}" not found`);
    return undefined;
  }

  try {
    // Store original style properties
    const originalStyles = {
      width: chartElement.style.width,
      height: chartElement.style.height,
      minWidth: chartElement.style.minWidth,
      minHeight: chartElement.style.minHeight,
      maxWidth: chartElement.style.maxWidth,
      maxHeight: chartElement.style.maxHeight,
    };

    // Set fixed dimensions for consistent image generation
    const fixedStyles = {
      width,
      height,
      minWidth: width,
      minHeight: height,
      maxWidth: width,
      maxHeight: height,
    };

    Object.assign(chartElement.style, fixedStyles);

    // Wait for chart to resize
    await new Promise((resolve) => setTimeout(resolve, 500));

    const chartCanvas = chartElement.querySelector("canvas");
    if (!chartCanvas) {
      console.warn(`No canvas element found in "${elementId}"`);
      // Restore original styles before returning
      Object.assign(chartElement.style, originalStyles);
      return undefined;
    }

    // Try to get Chart.js instance and resize it directly
    const chartInstance = (
      chartCanvas as HTMLCanvasElement & {
        chart?: { resize?: () => void };
      }
    ).chart;

    if (chartInstance && typeof chartInstance.resize === "function") {
      chartInstance.resize();
      await new Promise((resolve) => setTimeout(resolve, 300));
    } else {
      // Fallback to window resize event
      window.dispatchEvent(new Event("resize"));
      await new Promise((resolve) => setTimeout(resolve, 500));
    }

    // Force canvas to match container size
    const containerWidth = chartElement.offsetWidth;
    const containerHeight = chartElement.offsetHeight;

    // Capture the element as an image
    const canvas = await html2canvas(chartElement, {
      useCORS: true,
      allowTaint: false,
      backgroundColor,
      scale,
      logging: false,
      width: containerWidth,
      height: containerHeight,
    });

    const imageData = canvas.toDataURL("image/png", 1.0);

    // Restore original style properties
    Object.assign(chartElement.style, originalStyles);

    return imageData;
  } catch (error) {
    console.error(errorMessage, error);
    return undefined;
  }
};

/**
 * Generic function to generate an image from a Mapbox map instance
 * @param options Configuration options for map image generation
 * @returns Base64 encoded image string or undefined if generation fails
 */
export const generateMapImage = async (
  options: MapImageOptions,
): Promise<string | undefined> => {
  const {
    mapInstance,
    waitTime = 1000,
    backgroundColor = null,
    scale = 1,
    imageFormat = "image/jpeg",
    imageQuality = 0.8,
    errorMessage = "Failed to capture map image",
  } = options;

  try {
    // Wait for map to fully render
    await new Promise((resolve) => setTimeout(resolve, waitTime));

    const mapContainer = mapInstance.getContainer();
    if (!mapContainer) {
      console.warn("Map container not found");
      return undefined;
    }

    const canvas = await html2canvas(mapContainer, {
      useCORS: true,
      allowTaint: false,
      backgroundColor,
      scale,
      logging: false,
      width: mapContainer.offsetWidth,
      height: mapContainer.offsetHeight,
    });

    const imageData = canvas.toDataURL(imageFormat, imageQuality);

    // Validate the image data
    if (!imageData || imageData.length < 100) {
      console.warn("Map image capture resulted in invalid data");
      return undefined;
    }

    return imageData;
  } catch (error) {
    console.error(errorMessage, error);
    return undefined;
  }
};
