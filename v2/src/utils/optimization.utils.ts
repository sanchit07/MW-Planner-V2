import { OptimizationFormData } from "@schemas/campaigns/optimzation.schema";

/**
 * Transforms campaign data to budget form format
 */
export const transformOptimizationAdjustHundredPercentShare = (
  newValue: number,
  oldValue: number,
  watchedValues: Record<string, number>,
  currentKey: string,
): Partial<OptimizationFormData> | number | undefined => {
  const difference = newValue - oldValue;

  if (difference === 0) return;

  const otherTypes = Object.keys(watchedValues).filter((t) => t !== currentKey);

  if (otherTypes.length === 0) {
    return 100;
  }

  // Total allocation for other types
  const otherTotal = otherTypes.reduce((sum, t) => sum + watchedValues[t], 0);

  if (otherTotal === 0 && difference < 0) {
    // Can't reduce if others are already at 0
    return;
  }
  // Adjust other types proportionally
  const adjustedValues: Record<string, number> = {};
  otherTypes.forEach((otherType) => {
    const currentShare = watchedValues[otherType];
    const proportion =
      otherTotal === 0 ? 1 / otherTypes.length : currentShare / otherTotal;

    const adjustment =
      difference < 0 ? -difference * proportion : -difference * proportion;

    const newShare = Math.max(0, currentShare + adjustment);
    adjustedValues[otherType] = newShare;
  });
  const allTypes = Object.keys(watchedValues);
  const sum = allTypes.reduce(
    (sum, t) => sum + (t === currentKey ? newValue : adjustedValues[t]),
    0,
  );
  if (Math.abs(sum - 100) > 0.0) {
    // Adjust the last type to make sum exactly 100
    let lastType = otherTypes[otherTypes.length - 1];
    let currentVal = adjustedValues[lastType];
    if (!currentVal) {
      for (let i = otherTypes.length - 1; i >= 0; i--) {
        if (adjustedValues[otherTypes[i]]) {
          lastType = otherTypes[i];
          currentVal = adjustedValues[lastType];
          break;
        }
      }
    }
    const newVal = currentVal + (100 - sum);
    adjustedValues[lastType] = newVal;
  }

  return adjustedValues;
};

export function formatTime(time: string) {
  const [hours, minutes] = time.split(":");
  let period = "AM";

  let formattedHours = parseInt(hours, 10);

  if (formattedHours >= 12) {
    period = "PM";
    if (formattedHours > 12) {
      formattedHours -= 12;
    }
  } else if (formattedHours === 0) {
    formattedHours = 12;
  }

  return `${formattedHours}:${minutes}${period}`;
}
