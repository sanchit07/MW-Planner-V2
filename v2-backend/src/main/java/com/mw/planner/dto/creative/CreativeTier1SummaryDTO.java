package com.mw.planner.dto.creative;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard "Creative Status Tracker" widget data — Tier 1 (internal) approval counts for the
 * acting company's creative library (PRD §11 / creative-management spec: every upload starts
 * Processing and a manager transitions it to Accepted or Inadequate).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreativeTier1SummaryDTO {

  private long processing;
  private long accepted;
  private long inadequate;

  private long totalCreatives;
  private long images;
  private long videos;

  /** % of each format's library already Accepted — how ready that format is for assignment. */
  private int imagesAcceptedPercent;

  private int videosAcceptedPercent;
}
