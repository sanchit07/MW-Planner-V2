package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for campaign in external payload */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Campaign details in external payload")
public class ExternalCampaignDTO {

  @JsonProperty("externalId")
  @Schema(description = "External campaign ID", example = "ext-split-001")
  private String externalId;

  @JsonProperty("name")
  @Schema(description = "Campaign name", example = "National Retail Campaign - Multi-Publisher")
  private String name;

  @JsonProperty("source")
  @Schema(description = "Source system", example = "external-planning-tool")
  private String source;

  @JsonProperty("status")
  @Schema(description = "Campaign status", example = "DRAFT")
  private String status;

  @JsonProperty("currency")
  @Schema(description = "Currency code", example = "USD")
  private String currency;

  @JsonProperty("brand")
  @Schema(description = "Brand name", example = "Adidas")
  private String brand;

  @JsonProperty("clientType")
  @Schema(description = "Client type", example = "AGENCY")
  private String clientType;

  @JsonProperty("approvalEmails")
  @Schema(description = "List of approval email addresses")
  private List<String> approvalEmails;

  @JsonProperty("advertiser")
  @Schema(description = "Advertiser information")
  private AdvertiserDTO advertiser;

  @JsonProperty("seller")
  @Schema(description = "Seller information (media owner)")
  private SellerDTO seller;

  @JsonProperty("account")
  @Schema(description = "Account information (media owner)")
  private AccountDTO account;

  @JsonProperty("marketSelection")
  @Schema(description = "Market selection details")
  private MarketSelectionDTO marketSelection;

  @JsonProperty("budgetSetup")
  @Schema(description = "Budget setup information")
  private BudgetSetupDTO budgetSetup;

  @JsonProperty("campaignGoal")
  @Schema(description = "Campaign goal information")
  private CampaignGoalDTO campaignGoal;

  @JsonProperty("startDate")
  @Schema(description = "Campaign start date in ISO format", example = "2025-02-01T00:00:00Z")
  private String startDate;

  @JsonProperty("endDate")
  @Schema(description = "Campaign end date in ISO format", example = "2025-04-30T23:59:59Z")
  private String endDate;

  @JsonProperty("country")
  @Schema(description = "Country code", example = "US")
  private String country;

  @JsonProperty("timezoneId")
  @Schema(description = "Timezone ID", example = "America/Chicago")
  private String timezoneId;

  @JsonProperty("creativeType")
  @Schema(description = "Creative type", example = "DISPLAY")
  private String creativeType;

  @JsonProperty("creativeSource")
  @Schema(description = "Creative source", example = "ADVERTISER")
  private String creativeSource;

  @JsonProperty("pacing")
  @Schema(description = "Pacing configuration")
  private PacingDTO pacing;

  @JsonProperty("targeting")
  @Schema(description = "Targeting configuration")
  private ExternalTargetingDTO targeting;

  @JsonProperty("deliveryTargeting")
  @Schema(description = "Delivery targeting configuration")
  private DeliveryTargetingDTO deliveryTargeting;

  /** DTO for account information (media owner) */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Account details (media owner information)")
  public static class AccountDTO {

    @JsonProperty("userId")
    @Schema(description = "User ID", example = "USR-001")
    private String userId;

    @JsonProperty("companyName")
    @Schema(description = "Company name", example = "Clear Channel")
    private String companyName;

    @JsonProperty("companyId")
    @Schema(description = "Company ID", example = "COMP-001")
    private String companyId;

    @JsonProperty("email")
    @Schema(description = "Email address", example = "account@clearchannel.com")
    private String email;

    @JsonProperty("externalId")
    @Schema(description = "External ID", example = "ACC-EXT-001")
    private String externalId;

    @JsonProperty("externalUserId")
    @Schema(description = "External user ID", example = "USR-EXT-001")
    private String externalUserId;
  }

  /** DTO for seller information (media owner) */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Seller details (media owner information)")
  public static class SellerDTO {

    @JsonProperty("id")
    @Schema(description = "Seller ID", example = "SEL-001")
    private String id;

    @JsonProperty("name")
    @Schema(description = "Seller name", example = "John Smith")
    private String name;

    @JsonProperty("publisherId")
    @Schema(description = "Publisher ID", example = "PUB-001")
    private String publisherId;

    @JsonProperty("publisherName")
    @Schema(description = "Publisher name", example = "Clear Channel")
    private String publisherName;

    @JsonProperty("phone")
    @Schema(description = "Phone number", example = "+1234567890")
    private String phone;

    @JsonProperty("email")
    @Schema(description = "Email address", example = "seller@clearchannel.com")
    private String email;

    @JsonProperty("externalId")
    @Schema(description = "External ID", example = "SEL-EXT-001")
    private String externalId;

    @JsonProperty("externalUserId")
    @Schema(description = "External user ID", example = "USR-EXT-001")
    private String externalUserId;
  }
}
