package com.mw.planner.dto;

import com.mw.planner.domain.Inventory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for campaign inventory filter response with comprehensive inventory details and performance
 * metrics. Contains grouped information for detail, location, performance, and operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
    description =
        "Campaign inventory filter response with comprehensive details and performance metrics")
public class CampaignInventoryFilterResponseDTO {

  @Schema(description = "Inventory detail information")
  private Detail detail;

  @Schema(description = "Location information with POI and demographics")
  private LocationInfo location;

  @Schema(description = "Performance metrics and calculations")
  private Performance performance;

  @Schema(description = "Operations information")
  private Operations operations;

  @Schema(description = "List of schedules for this inventory")
  private List<InventorySchedulesResponseDTO.ScheduleDTO> schedules;

  /** Inventory detail information */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Inventory detail information")
  public static class Detail {
    @Schema(description = "Inventory ID")
    private String id;

    @Schema(description = "Inventory name")
    private String name;

    @Schema(description = "External ID from external system")
    private String externalId;

    @Schema(description = "Reference ID for user readability")
    private String referenceId;

    @Schema(description = "Media owner ID")
    private String mediaOwnerId;

    @Schema(description = "Media owner name")
    private String mediaOwnerName;

    @Schema(description = "Inventory type")
    private String inventoryType;

    @Schema(description = "Inventory environment")
    private String environment;

    @Schema(description = "Venue types")
    private List<String> venueType;

    @Schema(description = "Thumbnail URL")
    private String thumbnail;

    @Schema(description = "Format")
    private String format;

    @Schema(description = "Panels")
    private List<InventoryResponseDTO.PanelDTO> panels;

    @Schema(description = "Mode of booking")
    private String bookingMode;

    @Schema(description = "Execution details (placeholder)")
    private String execution;

    @Schema(description = "Number of screens")
    private Integer screens;

    @Schema(description = "SOV value if inventory is selected")
    private Double sov;

    @Schema(description = "Whether inventory is selected for campaign")
    private Boolean isSelected;

    @Schema(description = "Whether inventory is accepted")
    private Boolean isAccepted;

    @Schema(description = "Whether inventory is compliant with the applied filters")
    private Boolean isCompliant;

    @Schema(description = "Content exclusions (IAB taxonomy)")
    private List<Inventory.ContentExclusion> contentExclusions;

    @Schema(description = "Supported creative formats")
    private List<Inventory.CreativeFormat> creativeFormats;

    @Schema(description = "Programmatic deal types supported by the inventory")
    private List<String> programmaticDealTypes;

    @Schema(description = "Inventory size")
    private String size;

    @Schema(description = "Inventory cluster")
    private List<String> inventoryCluster;

    @Schema(description = "Selling terms (lead time and day-part booking rules)")
    private Inventory.SellingTerm sellingTerm;

    @Schema(description = "Cinema buy attributes (present only for Cinema classification)")
    private Inventory.CinemaFields cinemaFields;
  }

  /** Location information with demographics */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Location information with demographics")
  public static class LocationInfo {
    @Schema(description = "Location details")
    private InventoryResponseDTO.LocationDTO location;

    @Schema(description = "Demographics information")
    private DemographicsDTO demographics;
  }

  /** Performance metrics and calculations */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Performance metrics and calculations")
  public static class Performance {
    @Schema(description = "Currency for all monetary values", example = "USD")
    private String currency;

    @Schema(description = "CPM rate (placeholder)")
    private Double cpmRate;

    @Schema(description = "Spot rate")
    private Double spotRate;

    @Schema(description = "Estimated cost for campaign")
    private Double estimatedCost;

    @Schema(description = "Cost per day")
    private Double perDayCost;

    @Schema(description = "Ad plays per day")
    private Long perDayAdPlays;

    @Schema(description = "Total ad plays for campaign duration")
    private Long totalAdPlays;

    @Schema(description = "Total share of time", example = "82.00")
    private Double totalSot;

    @Schema(description = "Planned share of time", example = "6hrs")
    private Double plannedSot;

    @Schema(description = "Share of voice", example = "10.25%")
    private Double sov;

    @Schema(description = "Estimated total impressions", example = "117800")
    private Long estimatedImpression;

    @Schema(description = "Estimated total reach", example = "13497")
    private Long estimatedReach;

    @Schema(description = "Estimated average frequency", example = "8.72")
    private Double estimatedFrequency;
  }

  /** Operations information */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Operations information")
  public static class Operations {
    @Schema(description = "Operating times")
    private Map<Inventory.Weekday, List<Inventory.OperatingTime>> operatingTimes;

    @Schema(description = "Maintenance window (placeholder)")
    private String maintenanceWindow;

    @Schema(description = "Loop size")
    private Integer loopSize;

    @Schema(description = "Slot duration")
    private Integer slotDuration;

    @Schema(description = "Client per loop")
    private Integer clientPerLoop;

    @Schema(description = "Cycle time")
    private Integer cycleTime;
  }

  /** Demographics information */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Demographics information")
  public static class DemographicsDTO {
    @Schema(description = "Age demographic with highest index score")
    private String age;

    @Schema(description = "Gender demographic with highest index score")
    private String gender;

    @Schema(description = "Overall demographic with highest index score")
    private String overall;

    @Schema(description = "Age-Gender combination demographic with highest index score")
    private String ageGender;

    @Schema(description = "Income demographic with highest index score (future)")
    private String income;

    @Schema(description = "Behaviour demographic with highest index score (future)")
    private String behaviour;

    @Schema(description = "Interest demographic with highest index score (future)")
    private String interest;

    @Schema(description = "Highest index score across all demography")
    private String highestIndexScore;
  }
}
