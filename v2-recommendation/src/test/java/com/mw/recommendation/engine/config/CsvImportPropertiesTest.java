package com.mw.recommendation.engine.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link CsvImportProperties} from {@code recommendation.csv.*} in a lightweight
 * (Docker-free) context to prove the feature caps resolve at startup — defaults when unset,
 * overrides when set.
 */
class CsvImportPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

  @Test
  void bindsDefaults_whenNoPropertiesSet() {
    runner.run(
        ctx -> {
          CsvImportProperties props = ctx.getBean(CsvImportProperties.class);
          assertThat(props.maxRows()).isEqualTo(5000);
          assertThat(props.maxFileBytes()).isEqualTo(5_242_880L);
          assertThat(props.maxPageSize()).isEqualTo(100);
        });
  }

  @Test
  void bindsOverrides_fromProperties() {
    runner
        .withPropertyValues(
            "recommendation.csv.max-rows=1234",
            "recommendation.csv.max-file-bytes=999",
            "recommendation.csv.max-page-size=50")
        .run(
            ctx -> {
              CsvImportProperties props = ctx.getBean(CsvImportProperties.class);
              assertThat(props.maxRows()).isEqualTo(1234);
              assertThat(props.maxFileBytes()).isEqualTo(999L);
              assertThat(props.maxPageSize()).isEqualTo(50);
            });
  }

  @Configuration
  @EnableConfigurationProperties(CsvImportProperties.class)
  static class TestConfig {}
}
