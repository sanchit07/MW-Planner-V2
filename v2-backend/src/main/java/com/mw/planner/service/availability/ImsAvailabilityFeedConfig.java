package com.mw.planner.service.availability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Selects the IMS availability feed implementation.
 *
 * <p>When {@code mw-planner.ims.availability-feed-url} is set, the real HTTP client is used.
 * Otherwise (local/dev environments without IMS access) the deterministic simulated feed is wired
 * in, keeping the ingestion path production-shaped either way.
 */
@Slf4j
@Configuration
public class ImsAvailabilityFeedConfig {

  @Bean
  @ConditionalOnProperty("mw-planner.ims.availability-feed-url")
  public ImsAvailabilityFeed httpImsAvailabilityFeed(
      RestTemplate restTemplate,
      @Value("${mw-planner.ims.availability-feed-url}") String baseUrl,
      @Value("${mw-planner.ims.availability-feed-api-key:}") String apiKey) {
    log.info("IMS availability feed: HTTP client against {}", baseUrl);
    return new HttpImsAvailabilityFeed(restTemplate, baseUrl, apiKey);
  }

  @Bean
  @ConditionalOnMissingBean(ImsAvailabilityFeed.class)
  public ImsAvailabilityFeed simulatedImsAvailabilityFeed() {
    log.info(
        "IMS availability feed: no mw-planner.ims.availability-feed-url configured — using the"
            + " deterministic simulated feed");
    return new SimulatedImsAvailabilityFeed();
  }
}
