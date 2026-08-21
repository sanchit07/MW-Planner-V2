package com.mw.planner.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "venues")
public class Venues extends BaseEntity<String> {

  private Integer enumerationId;

  private Integer parentEnumerationId;

  private Integer tier;

  // Tier-specific category fields matching actual MongoDB document structure
  private String parentCategory; // tier 1
  private String childCategory; // tier 2
  private String grandChildCategory; // tier 3

  private String definition;

  private String stringValue;

  /** Resolves the display name from whichever tier-specific category field is populated. */
  public String getName() {
    if (parentCategory != null) return parentCategory;
    if (childCategory != null) return childCategory;
    if (grandChildCategory != null) return grandChildCategory;
    return null;
  }
}
