package com.mw.planner.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "countries")
public class Country extends BaseEntity<String> {

  @Indexed(unique = true)
  @NotBlank
  private String countryId;

  @NotBlank private String name;

  @NotNull private Double latitude;

  @NotNull private Double longitude;

  @Positive private Integer zoom;

  private String mediaOwnerTermsAndConditions;

  private String buyerTermsAndConditions;

  @PositiveOrZero private Long population;

  @Indexed(unique = true)
  @NotBlank
  private String iso;

  private String postalformat;

  private String postalname;

  @NotNull private Boolean active;

  private String dialingCode;

  private String timezone;

  private Tax tax;

  private long impressions;

  /** Tax information for the country */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Tax {
    private String label;
    @PositiveOrZero private Double percent;
  }
}
