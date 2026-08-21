package com.mw.planner.domain;

import lombok.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "public_access_token")
public class PublicAccessToken extends BaseEntity<String> {

  @NonNull
  @Indexed(unique = true)
  private String campaignId;

  @NonNull private String domainName;
}
