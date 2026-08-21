package com.mw.planner.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

// FIX ME: Use External brand object from Measure
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "brands")
public class Brand extends BaseEntity<String> {

  private String name;
  private String logoUrl;
  private String industry;

  private String category; // IAB taxonomy
  private String companyId;
}
