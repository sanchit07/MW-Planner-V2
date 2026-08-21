package com.mw.planner.dto.creative;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Dashboard's Creative Status Tracker — replaces V1's hardcoded mock endpoint. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreativeStatusTrackerDTO {

  /** One row per line item on an Approved/Active campaign for the acting company. */
  private long totalLineItems;

  private List<FormatRow> byFormat;
  private List<MissingLineItem> missing;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FormatRow {
    private String format; // VIDEO | STATIC | AUDIO | HTML5
    private long totalBound;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class MissingLineItem {
    private String lineItemId;
    private String campaignId;
    private String inventoryId;
  }
}
