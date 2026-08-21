package com.mw.recommendation.engine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Add integrations configs */
@Data
@Configuration
@ConfigurationProperties(prefix = "mw-recommendation-engine")
public class MwRecommendationEngineProperties {

  private Measure measure = new Measure();
  private Management management = new Management();
  private IAM iam = new IAM();

  @Data
  public static class Measure {
    /**
     * Measure API base URL for reach/frequency (e.g. <a
     * href="https://measure-api.example.com">measure-api</a>). If blank, schedule enrichment is
     * skipped.
     */
    private String apiUrl = "";

    private Debug debug = new Debug();

    @Data
    public static class Debug {
      /** Enable debug logging of Measure API requests to file */
      private boolean enabled = false;

      /** Directory where debug logs will be written */
      private String logDirectory = "./logs/measure-api";

      /** Include response data in debug logs */
      private boolean includeResponse = true;

      /** Maximum log file size in MB before rotation */
      private int maxFileSizeMb = 10;

      /** Number of days to retain debug log files */
      private int retentionDays = 7;
    }
  }

  @Data
  public static class Management {
    private Credentials credentials = new Credentials();

    @Data
    public static class Credentials {
      private String username;
      private String password;
    }
  }

  @Data
  public static class IAM {
    private String serviceUrl;
    private String productId;
  }
}
