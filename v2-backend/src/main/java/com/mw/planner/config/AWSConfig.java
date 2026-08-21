package com.mw.planner.config;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(value = {PlannerS3Properties.class})
public class AWSConfig {

  @Autowired private PlannerS3Properties plannerS3Properties;

  @Bean
  @Profile({"default", "local"})
  public S3Client localS3Client() {
    log.info(
        "Creating local S3Client bean for profiles 'default' or 'local' using endpoint: {}",
        plannerS3Properties.getEndpoint());
    return S3Client.builder()
        .endpointOverride(URI.create(plannerS3Properties.getEndpoint()))
        .region(Region.of(plannerS3Properties.getRegion()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    plannerS3Properties.getAccessKey(), plannerS3Properties.getSecretKey())))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build();
  }

  @Bean
  @Profile("!default & !local")
  public S3Client awsS3Client(AwsCredentialsProvider awsCredentialsProvider) {
    log.info(
        "Creating AWS S3Client bean for non-local profiles using region: {}",
        plannerS3Properties.getRegion());

    return S3Client.builder()
        .region(Region.of(plannerS3Properties.getRegion()))
        .credentialsProvider(awsCredentialsProvider)
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(false).build())
        .build();
  }

  @Bean
  @Profile("!default & !local")
  public AwsCredentialsProvider awsCredentialsProvider() {
    log.info("Creating default AwsCredentialsProvider bean for non-local profiles");
    return DefaultCredentialsProvider.builder().build(); // Fetches credentials from the environment
  }
}
