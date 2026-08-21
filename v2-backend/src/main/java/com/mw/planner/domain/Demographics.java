package com.mw.planner.domain;

import com.mw.planner.enums.DemographicsType;
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
@Document(collection = "demographics")
public class Demographics extends BaseEntity<String> {

  @NotNull private DemographicsType demoType;

  @NotBlank private String demoKey;

  @NotBlank private String name;

  private String description;

  private String countryId;
}
