package com.mw.recommendation.engine.domain;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A retained CSV inventory import for a campaign (line item). One document per uploaded file.
 *
 * <p>Field shape intentionally mirrors the mw-planner-backend {@code SelectInventoryImports} entity
 * for cross-service consistency. The raw upload is NOT stored — {@code inventoryRefIds} holds the
 * VALID matched inventory <em>reference ids</em> and the download endpoint regenerates a canonical
 * single-column CSV from them.
 */
@Document(collection = "select_inventory_imports")
@CompoundIndex(
    name = "company_campaign_idx",
    def = "{'companyId': 1, 'campaignId': 1, 'createdAt': -1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class SelectInventoryImports extends BaseEntity<String> {

  @Indexed private String companyId;

  @Indexed private String campaignId;

  private String fileName;

  private List<String> inventoryRefIds;

  private String countryName;

  /** Line-item inventory classification (e.g. "Digital"/"Classic") — re-applied on `use`. */
  private String classification;

  /** Line-item media owner id (matched against {@code Inventory.mediaOwnerId}). */
  private String mediaOwnerId;

  /** "YES" when the line item is programmatic (inventory must offer a programmatic deal type). */
  private String programmaticSupport;
}
