package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for creating or updating an agency")
public class AgencyRequestDTO {

  @NotBlank(message = "validation.agency_name_required")
  @Size(max = 100, message = "validation.agency_name_size")
  @Schema(description = "Agency name", example = "Creative Media Agency")
  private String name;

  @JsonIgnore private String mediaOwnerId;

  @JsonIgnore private String companyId; // company_id that created in account portal

  @JsonProperty("companyEmail")
  @Email(message = "validation.company_email_format")
  @Size(max = 100, message = "validation.company_email_size")
  @Schema(description = "Company email address", example = "coke@gmail.com")
  private String companyEmail;

  @JsonProperty("countryId")
  @NotBlank(message = "validation.country_id_required")
  @Size(max = 50, message = "validation.country_id_size")
  @Schema(description = "Country ID", example = "23fe23")
  private String countryId;

  @JsonProperty("seatId")
  @Schema(description = "Seat ID", example = "32131")
  private Integer seatId;

  @JsonProperty("brandRefId")
  @Size(max = 50, message = "validation.brand_ref_id_size")
  @Schema(description = "Brand reference ID", example = "BR_001")
  private String brandRefId;
}
