package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for ADS submission response */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "ADS campaign submission response")
public class AdsSubmissionResponseDTO {

  @JsonProperty("requestId")
  @Schema(description = "Unique request identifier")
  private String requestId;

  @JsonProperty("status")
  @Schema(description = "HTTP status code", example = "201")
  private Integer status;

  @JsonProperty("message")
  @Schema(description = "Response message")
  private String message;

  @JsonProperty("errors")
  @Schema(description = "Validation errors (present only on failure)")
  private List<ValidationError> errors;

  // Fields from the actual API response (at root level, not nested)
  @JsonProperty("total")
  @Schema(description = "Total campaigns processed")
  private Integer total;

  @JsonProperty("successful")
  @Schema(description = "Number of successful campaigns")
  private Integer successful;

  @JsonProperty("failed")
  @Schema(description = "Number of failed campaigns")
  private Integer failed;

  @JsonProperty("details")
  @Schema(description = "Detailed results")
  private Details details;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "Detailed results")
  public static class Details {
    @JsonProperty("successful")
    @Schema(description = "List of successful campaign submissions")
    private List<SuccessfulCampaign> successful;

    @JsonProperty("failed")
    @Schema(description = "List of failed campaign submissions")
    private List<FailedCampaign> failed;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "Successful campaign submission")
  public static class SuccessfulCampaign {
    @JsonProperty("external_id")
    @Schema(description = "External campaign ID from source system")
    private String externalId;

    @JsonProperty("campaign_id")
    @Schema(description = "Campaign ID in ADS system")
    private String campaignId;

    @JsonProperty("action")
    @Schema(description = "Action performed", example = "updated")
    private String action;

    @JsonProperty("message")
    @Schema(description = "Success message")
    private String message;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "Failed campaign submission")
  public static class FailedCampaign {
    @JsonProperty("external_id")
    @Schema(description = "External campaign ID from source system")
    private String externalId;

    @JsonProperty("error")
    @Schema(description = "Error message")
    private String error;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "Validation error")
  public static class ValidationError {
    @JsonProperty("field")
    @Schema(description = "Field with validation error")
    private String field;

    @JsonProperty("message")
    @Schema(description = "Validation error message")
    private String message;
  }
}
