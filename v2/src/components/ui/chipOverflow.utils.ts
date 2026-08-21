/**
 * How many selection chips fit in `containerWidth`, keeping room for a
 * "+N" overflow badge when not all of them fit (SI 47 — responsive chips).
 *
 * All widths in px; `gap` is the flex gap between adjacent items.
 */
export const computeVisibleChipCount = (
  containerWidth: number,
  chipWidths: number[],
  overflowBadgeWidth: number,
  gap: number,
): number => {
  const total = chipWidths.length;
  if (total === 0 || containerWidth <= 0) return 0;

  const widthOfAll = chipWidths.reduce(
    (sum, width, index) => sum + width + (index > 0 ? gap : 0),
    0,
  );
  if (widthOfAll <= containerWidth) return total;

  let used = overflowBadgeWidth;
  let count = 0;
  for (const width of chipWidths) {
    const next = used + gap + width;
    if (next > containerWidth) break;
    used = next;
    count++;
  }
  return count;
};
