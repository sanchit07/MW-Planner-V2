package com.mw.planner.domain;

import java.util.List;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "campaign_comments")
public class CampaignComments extends BaseEntity<String> {
  private String comment;
  private List<String> fileUrls;
  private List<String> taggedCompanyIds;
  private String campaignId;
  private String companyId;
}
