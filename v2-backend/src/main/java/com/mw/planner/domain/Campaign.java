package com.mw.planner.domain;

import com.mw.planner.dto.CampaignForecastDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "campaigns")
public class Campaign extends BaseEntity<String> {

  @NonNull
  @Indexed(unique = true)
  private String name;

  // Human-readable 12-digit plan ID (yyyyMMdd + 4-digit daily sequence), e.g. "202607210001".
  // Sparse: existing campaigns are null until the one-off backfill runs; new campaigns always get
  // one assigned before their first save.
  @Indexed(unique = true, sparse = true)
  private String planNumber;

  private String description;
  @Builder.Default private Status status = Status.DRAFT;
  private Double budget;
  @Builder.Default private String currency = "USD";
  @NonNull private LocalDate startDate;
  @NonNull private LocalDate endDate;
  @NonNull @Indexed private String userId;
  private CampaignBrand brand;
  @NonNull private ClientType clientType;
  private CampaignAgency agency;
  @NonNull private String companyId;
  private String countryId;
  private String currentCompanyId;
  private String currentCompanyName;
  private Goals goals;
  private CampaignForecastDTO performance;
  private Targeting targeting;
  private Map<String, Double> budgetAllocation; // Key: "DIGITAL", Value: 25.00 for 25
  private List<MediaChannel> mediaChannels;

  private Optimization optimization; // Out of scope for now
  private List<String> companyAccess;
  private String runId;
  @Builder.Default private Boolean skipRecommendation = false;
  @Builder.Default private Boolean isNegotiated = false;
  private CompanyDetails companyDetails;
  private String userEmail;
  private String dsp;

  /**
   * Data partition ("live" or "demo"), stamped server-side at creation from the creator's Test
   * Mode. Missing/null means live (legacy records). Never trust a client-supplied value.
   */
  private String dataMode;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CompanyDetails {
    private String id;
    private String name;
    private Integer seatId;
  }

  public enum MediaChannel {
    DIGITAL_OOH,
    CLASSIC_OOH,
    CINEMA
  }

  // Nested classes for complex JSON fields
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CampaignBrand {
    private String id;
    private String name;
    private List<IabCategory> categories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IabCategory {
      private String id;
      private String name;
      private String fullPath;
      private Integer tier;
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CampaignAgency {
    private String id;
    private String name;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Goals {
    private GoalType goalType;
    private String targetName;
    private Double targetValue;

    @Getter
    public enum GoalType {
      IMPRESSIONS("Impressions"),
      REACH("Reach"),
      SOV("Share of Voice"),
      ATTRIBUTION("Attribution"),
      OTHER("Other"),
      ADPLAYS("Ad Plays");

      private final String name;

      GoalType(String name) {
        this.name = name;
      }
    }

    public String getTypeName() {
      return Optional.ofNullable(goalType)
          .map(type -> type == GoalType.OTHER ? targetName : type.getName())
          .orElse(null);
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Targeting {
    private Map<String, List<String>> demographics; // Key: "age", Value: ["18-24", "25-34"]
    private VenueTypes venueTypes;
    private Geofencing geofencing;
    private List<String> signals;
    private Boolean programmaticOnly;
    private List<String> inventoryCluster;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VenueTypes {
      private List<String> digitalOoh;
      private List<String> classicOoh;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Geofencing {
      private List<Geometry> geometries;
      private List<Location> locations;

      @Data
      @Builder
      @NoArgsConstructor
      @AllArgsConstructor
      public static class Geometry {
        private String name;
        @NonNull private String type; // e.g., "Polygon"
        @NonNull private List<List<Double>> coordinates; // GeoJSON coordinates
        private boolean isIncluded; // true for inclusion, false for exclusion
        private List<String> poi;
        private Map<String, String> metadata;
      }

      @Data
      @Builder
      @NoArgsConstructor
      @AllArgsConstructor
      public static class Location {
        private String name;
        @NonNull private Double lat;
        @NonNull private Double lng;
        // Radius required for circle - If null then consider application level radius config
        private Double radius;
        private String address;
        private boolean isIncluded; // true for inclusion, false for exclusion
        private List<String> poi;
        private Map<String, String> metadata;
      }
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Optimization {
    private Map<String, Object> budgetAllocation;
    private Map<String, Object> schedule;
    private Boolean autoOptimize;
  }

  public enum Status {
    DRAFT,
    REVIEWING,
    PLANNED,
    NEGOTIATING,
    PENDING,
    APPROVED,
    DEAL_REQUESTED,
    ACTIVE,
    PAUSE,
    COMPLETED,
    REJECTED,
    ARCHIVED
  }

  public enum ClientType {
    AGENCY,
    DIRECT_ADVERTISER
  }
}
