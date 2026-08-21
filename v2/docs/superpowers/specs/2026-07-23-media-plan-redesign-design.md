# Media Plan View — Redesign Spec

Redesign the **presentation** view of `ViewMediaPlanPage` to match the provided design
(`new-design-media-plan.png` / `new design.pdf`). Sidebar, header, and route are untouched.
Content is centered in the page like the design. Work proceeds **phase by phase**: implement
→ user manually tests & confirms → next phase.

## Global rules

- Do not break existing functionality (analytics view, share, download, map).
- Follow modular structure — each section is its own component in `src/pages/campaigns/media-plan/`.
- Update/add tests as needed per phase.
- Maintain `bug_tracker.md` **only when a bug is fixed**.
- No git commits (per user).
- All user-visible strings via Tolgee (`campaigns` namespace), keys in both `en.json` + `ja.json`.

## Phases

| #   | Section                            | Component                                                |
| --- | ---------------------------------- | -------------------------------------------------------- |
| 1   | Image section (hero)               | `MediaPlanTitleSlide.tsx`                                |
| 2   | Estimated Performance Metrics      | `MediaPlanPerformanceMetrics.tsx`                        |
| 3   | Inventory Mix                      | (TBD phase 3)                                            |
| 4   | Audience Trends                    | (TBD)                                                    |
| 5   | Geographic Plan                    | (TBD)                                                    |
| 6   | Audience Map — Heatmap & inventory | (TBD)                                                    |
| 7   | Goals & KPIs                       | (TBD)                                                    |
| 8   | Inventory Snapshots                | (TBD)                                                    |
| 9   | Why This Plan Works                | (TBD)                                                    |
| 10  | Export alignment (PPT + Excel)     | `mediaPlanPPTGenerator.ts`, `mediaPlanExcelGenerator.ts` |

**PPT/Excel note:** both generators are frontend, data-driven (`PptxGenJS` / xlsx), decoupled
from the on-screen DOM (except charts/map which `html2canvas` capture DOM by element `id`).
Redesigning screen sections does NOT break export. Screen and export intentionally diverge
during Phases 1–9; export is re-skinned once in Phase 10. Preserve chart/map element `id`s.

---

## Phase 1 — Image section (hero)

**Scope:** rewrite the hero in `MediaPlanTitleSlide.tsx`. No data/API changes; uses existing
`headerInfo` + `brandDetails` props already passed from `ViewMediaPlanPage`.

**Layout (per design):**

- Full-bleed background image `assets/images/media-plan-bg.jpg`, `object-cover`,
  fixed height (~380px), rounded corners. Image at full opacity (not faded).
- Fixed dark gradient overlay for text legibility (dark at bottom → lighter at top).
  **Theme no longer tints the hero** — overlay is fixed dark regardless of selected theme.
- **Top-left:** existing logo treatment — "MW" white box + "Moving Walls" label, smaller.
- **Top-right:** brand chip (first-letter box + brand name) — only when `brandDetails.name` exists.
- **Bottom-left:** large, **left-aligned** campaign title (`headerInfo.name`), wraps to 2 lines.
- **Bottom info row (overlaid on image):**
  - Left: `PLAN DATES` label → date range
    `formatDisplayDate(startDate) – formatDisplayDate(endDate)` → computed duration `"{n} days"`.
    Duration = `endDate − startDate + 1` (whole days), computed in-component; only shown when
    both dates present.
  - Right: `preparedBy` value + "MW Planner Internal" sub-line, and a **status badge**
    (`headerInfo.status`, e.g. "Planned") using existing `StatusBadge`.

**Removed from hero (move to Phase 2 / dropped):**

- All 5 metric cards (Total Budget, Total Cost, Impressions, Campaign Period, Prepared-by).
- Centered layout and the campaign-ID line.

**Non-goals:** no change to `useThemeClasses` for other sections; PPT title slide untouched
(Phase 10); metrics section untouched (Phase 2).

**i18n keys (new/reused, `campaigns` namespace):**

- `media_plan.title_slide.plan_dates` → "Plan Dates"
- `media_plan.title_slide.days` → "{count} days" (interpolated)
- reuse `media_plan.title_slide.prepared_by`, status labels `campaignsList.status.*`.

**Tests:** update `MediaPlanTitleSlide` test (if present) / add one — renders title, date range,
duration, status badge, brand chip conditional, and that metric cards are gone.
