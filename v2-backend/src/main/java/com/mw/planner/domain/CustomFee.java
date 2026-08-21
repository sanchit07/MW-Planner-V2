package com.mw.planner.domain;

import com.mw.planner.enums.CustomFeeBasedOn;
import com.mw.planner.enums.CustomFeeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "custom_fees")
public class CustomFee extends BaseEntity<String> {

  @NotBlank private String name;

  private String description;

  @NotNull private CustomFeeType type;

  @NotNull private Double value;

  @NotNull private CustomFeeBasedOn basedOn;

  @NotNull private Boolean isIncludeInMediaPlan = true;

  @NotNull private Boolean isActive = true;

  /** Company ID. Always set; custom fee is scoped to this company. */
  @NotBlank private String companyId;

  /**
   * Campaign ID. If null, custom fee is for the company; if non-null, custom fee is for this
   * campaign.
   */
  private String campaignId;
}
