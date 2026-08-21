package com.mw.planner.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Per-user planner settings that are not stored in IAM (IAM user context is cache-only in this
 * service). Keyed by the IAM username/user id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_settings")
public class UserSettings {

  @Id private String userId;

  /**
   * When true the user works in Test Mode: new plans are stamped with the "demo" data mode and the
   * user only sees demo-partition plans. Mirrors the V1 header Test Mode switch.
   */
  @Builder.Default private Boolean testMode = false;
}
