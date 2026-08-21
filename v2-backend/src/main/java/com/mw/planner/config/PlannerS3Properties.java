package com.mw.planner.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "aws.s3.planner")
public class PlannerS3Properties {
  private String accessKey;
  private String secretKey;
  private String region;
  private String bucketName;
  private String parentDirectory;
  private String endpoint;
}
