package com.mw.planner.domain;

import java.util.Map;
import lombok.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "campaign_activity")
public class CampaignActivity extends BaseEntity<String> {

  @NonNull @Indexed private String campaignId;

  @NonNull @Indexed private String userId;

  @NonNull @Indexed private String companyId;

  @NonNull private String updatedBy; // User name or "System"

  @NonNull private String operationType; // Operation type: CREATED, UPDATED, ADDED, REMOVED

  @NonNull
  private Map<String, Object> values; // Map of field names and values for message generation
}
