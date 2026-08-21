package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for agency information")
public class AgencyResponseDTO {

  @Schema(description = "Unique agency identifier", example = "agency_123456")
  private String id;

  @Schema(description = "Agency name", example = "Creative Media Agency")
  private String name;

  @JsonProperty("mediaOwnerId")
  @Schema(description = "Media owner ID", example = "MO_001")
  private String mediaOwnerId;

  @JsonProperty("companyEmail")
  @Schema(description = "Company email address", example = "coke@gmail.com")
  private String companyEmail;

  @JsonProperty("countryId")
  @Schema(description = "Country ID", example = "23fe23")
  private String countryId;

  @JsonProperty("countryName")
  @Schema(description = "Country name", example = "United States")
  private String countryName;

  @JsonProperty("companyId")
  @Schema(description = "Company ID", example = "sds")
  private String companyId;

  @JsonProperty("seatId")
  @Schema(description = "Seat ID (optional)", example = "dff34")
  private Integer seatId;

  @JsonProperty("brandRefId")
  @Schema(description = "Brand reference ID (optional)", example = "")
  private String brandRefId;

  @Schema(description = "Whether the agency is activated", example = "true")
  private boolean activated;

  @JsonProperty("createdAt")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Schema(description = "Agency creation timestamp", example = "2025-01-15 10:30:00")
  private LocalDateTime createdAt;

  @JsonProperty("updatedAt")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Schema(description = "Agency last update timestamp", example = "2025-01-15 14:45:00")
  private LocalDateTime updatedAt;
}
