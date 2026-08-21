import { useGetCreativeTier1SummaryQuery } from "@services/creative/creativeSlice";
import React from "react";

import CreativeStatusTracker, {
  type CreativeStatusTrackerData,
} from "./CreativeStatusTracker";

/**
 * Fetches the real Tier 1 creative-status summary and feeds it to the presentational
 * CreativeStatusTracker. Kept as a thin container so the presentational component (and its
 * existing test suite, which renders it with an explicit `data` prop) stays untouched.
 */
const CreativeStatusTrackerWidget: React.FC = () => {
  const { data: response } = useGetCreativeTier1SummaryQuery();
  const summary =
    response && "success" in response && response.success ? response.data : undefined;

  // Zeroed rather than omitted while loading/unavailable — an empty state, not the
  // presentational component's fabricated sample numbers (28/48/22, etc.).
  const data: CreativeStatusTrackerData = summary
    ? {
        status: {
          processing: summary.processing,
          accepted: summary.accepted,
          inadequate: summary.inadequate,
        },
        breakdown: {
          totalCreatives: summary.totalCreatives,
          images: summary.images,
          videos: summary.videos,
        },
        displayFormats: {
          images: summary.imagesAcceptedPercent,
          videos: summary.videosAcceptedPercent,
        },
      }
    : {
        status: { processing: 0, accepted: 0, inadequate: 0 },
        breakdown: { totalCreatives: 0, images: 0, videos: 0 },
        displayFormats: { images: 0, videos: 0 },
      };

  return <CreativeStatusTracker data={data} />;
};

export default CreativeStatusTrackerWidget;
