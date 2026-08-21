package com.mw.planner.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "districts")
public class District extends BaseEntity<String> {

  @NotBlank private String name;

  @NotBlank private String stateId;

  private String type;

  @NotNull private Double latitude;

  @NotNull private Double longitude;

  @Positive private Integer zoom;

  @PositiveOrZero private Long population;

  @NotBlank private String iso;

  @NotBlank private String locale;
}
