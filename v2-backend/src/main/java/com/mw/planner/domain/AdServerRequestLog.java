package com.mw.planner.domain;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document for tracking outbound requests/responses to the ADS (Advertising Data System).
 * Used for debugging, troubleshooting, and audit purposes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "adserver_request_logs")
public class AdServerRequestLog extends BaseEntity<String> {

  /** Full URL of the third-party ADS API endpoint called */
  private String endpoint;

  /** HTTP request headers sent to ADS API (sensitive headers masked) */
  private Map<String, String> requestHeaders;

  /** Request payload sent to ADS API (stored as nested document for queryability) */
  private Object requestBody;

  /** HTTP response status code received from ADS API */
  private Integer responseCode;

  /** Response payload received from ADS API (or error details) */
  private Object responseBody;

  /**
   * Campaign ID extracted from request for easy filtering and correlation. Allows querying logs by
   * campaign without parsing requestBody.
   */
  private String campaignId;

  // Note: createdAt, updatedAt, createdBy, lastModifiedBy are inherited from BaseEntity
}
