package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for external inventory messages from external systems Represents the structure of inventory
 * data received via RabbitMQ
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalInventoryMessageDTO {

  @JsonProperty("id")
  private String id;

  @JsonProperty("typePath")
  private String typePath;

  @JsonProperty("createdAt")
  private String createdAt;

  @JsonProperty("createdBy")
  private String createdBy;

  @JsonProperty("updatedAt")
  private String updatedAt;

  @JsonProperty("updatedBy")
  private String updatedBy;

  @NotBlank(message = "Name is required")
  @JsonProperty("name")
  private String name;

  @JsonProperty("mediaOwnerId")
  private String mediaOwnerId;

  @JsonProperty("mediaOwnerName")
  private String mediaOwnerName;

  @JsonProperty("geoms")
  private List<String> geoms;

  @JsonProperty("address")
  private String address;

  @JsonProperty("timeZone")
  private String timeZone;

  @JsonProperty("environment")
  private String environment;

  @JsonProperty("orientation")
  private String orientation;

  @JsonProperty("archived")
  private Boolean archived;

  @JsonProperty("requiresContentApproval")
  private Boolean requiresContentApproval;

  @JsonProperty("adminLevel0Id")
  private String adminLevel0Id;

  @JsonProperty("adminLevel1Id")
  private String adminLevel1Id;

  @JsonProperty("adminLevel2Id")
  private String adminLevel2Id;

  @JsonProperty("typeName")
  private String typeName;

  @JsonProperty("displayFormatName")
  private String displayFormatName;

  @JsonProperty("displayFormatId")
  private Integer displayFormatId;

  @JsonProperty("adminLevel0Name")
  private String adminLevel0Name;

  @JsonProperty("adminLevel1Name")
  private String adminLevel1Name;

  @JsonProperty("adminLevel2Name")
  private String adminLevel2Name;

  @JsonProperty("thumbnailUrl")
  private String thumbnailUrl;

  @JsonProperty("thumbnailUrlOriginal")
  private String thumbnailUrlOriginal;

  @JsonProperty("content-exclusions")
  private List<ContentExclusion> contentExclusions;

  @JsonProperty("medias")
  private List<Media> medias;

  @JsonProperty("tags")
  private List<Tag> tags;

  @JsonProperty("size")
  private String size;

  @JsonProperty("viewingDistance")
  private Double viewingDistance;

  @JsonProperty("panels")
  private List<Panel> panels;

  @JsonProperty("dsps")
  private List<Dsp> dsps;

  @JsonProperty("venues")
  private List<Venue> venues;

  @JsonProperty("externalIds")
  private List<ExternalId> externalIds;

  @JsonProperty("referenceId")
  private String referenceId;

  @JsonProperty("prices")
  private List<Price> prices;

  @JsonProperty("creativeFormats")
  private List<CreativeFormat> creativeFormats;

  @JsonProperty("programmaticDealTypes")
  private List<String> programmaticDealTypes;

  @JsonProperty("sellingTerm")
  private SellingTerm sellingTerm;

  @JsonProperty("schedule")
  private Schedule schedule;

  @JsonProperty("digitalFields")
  private DigitalFields digitalFields;

  @JsonProperty("classicFields")
  private ClassicFields classicFields;

  @JsonProperty("transitFields")
  private TransitFields transitFields;

  // Nested classes for complex objects
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Panel {
    @JsonProperty("pixelWidth")
    private Integer pixelWidth;

    @JsonProperty("pixelHeight")
    private Integer pixelHeight;

    @JsonProperty("physicalWidth")
    private Double physicalWidth;

    @JsonProperty("physicalHeight")
    private Double physicalHeight;

    @JsonProperty("panelCount")
    private Integer panelCount;

    @JsonProperty("size")
    private String size;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ContentExclusion {
    @JsonProperty("name")
    private String name;

    @JsonProperty("taxonomyId")
    private String taxonomyId;

    @JsonProperty("version")
    private String version;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Media {
    @JsonProperty("url")
    private String url;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Tag {
    @JsonProperty("id")
    private String id;

    @JsonProperty("mediaOwnerId")
    private String mediaOwnerId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("hexColor")
    private String hexColor;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Dsp {
    @JsonProperty("dspId")
    private Integer dspId;

    @JsonProperty("dspName")
    private String dspName;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Venue {
    @JsonProperty("taxonomyId")
    private String taxonomyId;

    @JsonProperty("version")
    private String version;

    @JsonProperty("name")
    private String name;

    @JsonProperty("path")
    private String path;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ExternalId {
    @JsonProperty("platform")
    private String platform;

    @JsonProperty("externalId")
    private String externalId;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Price {
    @JsonProperty("cpm")
    private Double cpm;

    @JsonProperty("cps")
    private Double cps;

    @JsonProperty("monthly")
    private Double monthly;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("durationSeconds")
    private Integer durationSeconds;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CreativeFormat {
    @JsonProperty("format")
    private String format;

    @JsonProperty("creativeType")
    private String creativeType;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SellingTerm {
    @JsonProperty("leadDays")
    private Integer leadDays;

    @JsonProperty("minHours")
    private Integer minHours;

    @JsonProperty("minDays")
    private Integer minDays;

    @JsonProperty("dayPartGroups")
    private Map<String, DayPartGroup> dayPartGroups;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DayPartGroup {
    @JsonProperty("start")
    private String start;

    @JsonProperty("end")
    private String end;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Schedule {
    @JsonProperty("operatingTimes")
    private Map<String, List<OperatingTime>> operatingTimes;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class OperatingTime {
    @JsonProperty("start")
    private String start;

    @JsonProperty("end")
    private String end;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DigitalFields {
    @JsonProperty("playerSoftwareId")
    private Integer playerSoftwareId;

    @JsonProperty("playerSoftwareName")
    private String playerSoftwareName;

    @JsonProperty("playerCount")
    private Integer playerCount;

    @JsonProperty("spotDuration")
    private Integer spotDuration;

    @JsonProperty("spotsPerLoop")
    private Integer spotsPerLoop;

    @JsonProperty("bookingMode")
    private String bookingMode;

    @JsonProperty("loopDuration")
    private Integer loopDuration;

    @JsonProperty("loopsPerHour")
    private Integer loopsPerHour;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ClassicFields {
    @JsonProperty("illuminated")
    private Boolean illuminated;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TransitFields {
    @JsonProperty("routeId")
    private String routeId;

    @JsonProperty("routeName")
    private String routeName;
  }
}
