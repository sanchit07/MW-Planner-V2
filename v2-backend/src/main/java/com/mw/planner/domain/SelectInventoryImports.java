package com.mw.planner.domain;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "select_inventory_imports")
public class SelectInventoryImports extends BaseEntity<String> {

  @Indexed private String companyId;

  @Indexed private String campaignId;

  private String fileName;

  private List<String> inventoryRefIds;

  private String countryName;
}
