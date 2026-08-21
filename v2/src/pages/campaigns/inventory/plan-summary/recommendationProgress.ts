/** i18n keys for the recommendation generation progress messages. */
const PROGRESS_KEYS: { maxProgress: number; key: string }[] = [
  { maxProgress: 12, key: "inventories.smartSuggestion.progress.budgetFit" },
  { maxProgress: 24, key: "inventories.smartSuggestion.progress.goalFit" },
  { maxProgress: 36, key: "inventories.smartSuggestion.progress.geoFitness" },
  { maxProgress: 48, key: "inventories.smartSuggestion.progress.audienceFit" },
  {
    maxProgress: 60,
    key: "inventories.smartSuggestion.progress.brandCategoryFit",
  },
  {
    maxProgress: 72,
    key: "inventories.smartSuggestion.progress.inventoryAvailability",
  },
  { maxProgress: 84, key: "inventories.smartSuggestion.progress.timeFit" },
  {
    maxProgress: 100,
    key: "inventories.smartSuggestion.progress.selectingInventory",
  },
];

/** Maps a completion percentage (0–100) to the matching progress message key. */
export function getProgressMessageKey(progress: number): string {
  for (const { maxProgress, key } of PROGRESS_KEYS) {
    if (progress <= maxProgress) return key;
  }
  return PROGRESS_KEYS[PROGRESS_KEYS.length - 1].key;
}

/** Poll backoff used while the recommendation run is IN_PROGRESS. */
export function getNextPollDelayMs(attempt: number): number {
  if (attempt === 0) return 5000;
  if (attempt === 1) return 4000;
  return 3000;
}
