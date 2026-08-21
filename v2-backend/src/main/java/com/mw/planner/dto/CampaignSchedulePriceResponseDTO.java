package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for campaign schedule price management response. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Campaign schedule price management response")
public class CampaignSchedulePriceResponseDTO {

  @Schema(description = "Campaign Inventory Schedule ID")
  private String id;

  @Schema(description = "Inventory ID")
  private String inventoryId;

  @Schema(description = "Inventory name")
  private String inventoryName;

  @Schema(description = "Campaign start date")
  private LocalDate startDate;

  @Schema(description = "Campaign end date")
  private LocalDate endDate;

  @Schema(description = "Timeslot information")
  private Timeslot timeslot;

  @Schema(description = "Share of Voice (SOV)")
  private Double sov;

  @Schema(description = "Ad plays")
  private Long adPlays;

  @Schema(description = "Current rate")
  private Double currentRate;

  @Schema(description = "Proposed rate")
  private Double proposedRate;

  @Schema(description = "Media Owner ID")
  private String mediaOwnerId;

  @Schema(description = "Media Owner name")
  private String mediaOwnerName;

  @Schema(description = "Impressions")
  private Long impressions;

  @Schema(description = "Discount percentage")
  private Double discountPercent;

  @Schema(description = "Monthly rate card")
  private Double monthlyRateCard;

  @Schema(description = "Weekly rate card")
  private Double weeklyRateCard;

  @Schema(description = "Daily rate")
  private Double dailyRate;

  @Schema(description = "CPM rate")
  private Double cpmRate;

  @Schema(description = "Reach")
  private Long reach;

  @Schema(description = "List of schedules")
  private List<SchedulePriceDTO> schedules;

  @Schema(
      description = "Cinema-specific buy attributes (operator, hall, showtimes, genres, ratings)")
  private com.mw.planner.domain.Inventory.CinemaFields cinemaFields;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Schedule price information")
  public static class SchedulePriceDTO {
    @Schema(description = "Schedule ID")
    private String id;

    @Schema(description = "Schedule name")
    private String name;

    @Schema(description = "Schedule start date")
    private LocalDate startDate;

    @Schema(description = "Schedule end date")
    private LocalDate endDate;

    @Schema(description = "Schedule days")
    private List<String> scheduleDays;

    @Schema(description = "Schedule type")
    private String type;

    @Schema(description = "Bonus type")
    private String bonusType;

    @Schema(description = "Booking matrix")
    private Map<String, List<Integer>> bookingMatrix;

    @Schema(description = "Duration (seconds)")
    private Long duration;

    @Schema(description = "Spots per loop")
    private Long spotsPerLoop;

    @Schema(description = "Spots per hour")
    private Long spotsPerHour;

    @Schema(description = "Share of Voice (SOV)")
    private Double sov;

    @Schema(description = "Ad plays")
    private Long adPlays;

    @Schema(description = "Planned SOT")
    private Double plannedSot;

    @Schema(description = "Total SOT")
    private Double totalSot;

    @Schema(description = "Schedule order")
    private Integer order;

    @Schema(description = "Impressions")
    private Long impressions;

    @Schema(description = "Reach")
    private Long reach;

    @Schema(description = "Discount")
    private DiscountDTO discount;

    @Schema(description = "Current rate")
    private Double currentRate;

    @Schema(description = "Proposed rate")
    private Double proposedRate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Discount information")
    public static class DiscountDTO {
      @Schema(description = "Discount value type")
      private String valueType;

      @Schema(description = "Discount value")
      private String value;
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Timeslot information")
  public static class Timeslot {
    @Schema(description = "Start time")
    private String startTime;

    @Schema(description = "End time")
    private String endTime;
  }
}
