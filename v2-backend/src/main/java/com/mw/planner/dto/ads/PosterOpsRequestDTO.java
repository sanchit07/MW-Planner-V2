package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosterOpsRequestDTO {

  @JsonProperty("planner_order_id")
  private String plannerOrderId;

  @JsonProperty("company_id")
  private String companyId;

  @JsonProperty("campaign_name")
  private String campaignName;

  @JsonProperty("brand_name")
  private String brandName;

  @JsonProperty("client_name")
  private String clientName;

  @JsonProperty("client_type")
  private String clientType;

  @JsonProperty("campaign_note")
  private String campaignNote;

  @JsonProperty("start_date")
  private String startDate;

  @JsonProperty("end_date")
  private String endDate;

  @JsonProperty("total_value")
  private Double totalValue;

  @JsonProperty("currency")
  private String currency;

  @JsonProperty("action")
  private String action;

  @JsonProperty("callback_url")
  private String callbackUrl;

  @JsonProperty("billboards")
  private List<PosterOpsBillboardDTO> billboards;

  @JsonProperty("created_by")
  private String createdBy;
}
