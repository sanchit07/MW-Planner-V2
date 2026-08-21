package com.mw.recommendation.engine.v3.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WarningCollectorTest {

  @Test
  void givenExactDuplicateWarnings_whenWarn_thenDeduplicated() {
    WarningCollector collector = new WarningCollector();

    collector.warn("availability unconfirmed");
    collector.warn("availability unconfirmed");
    collector.warn("budget shortfall");

    assertThat(collector.warnings())
        .containsExactly("availability unconfirmed", "budget shortfall");
  }

  @Test
  void givenRepeatedExclusions_whenExclude_thenCountsAccumulate() {
    WarningCollector collector = new WarningCollector();

    collector.exclude("no impressions data");
    collector.exclude("no impressions data");
    collector.exclude("low availability", 5);

    assertThat(collector.exclusionReasons())
        .containsEntry("no impressions data", 2)
        .containsEntry("low availability", 5);
  }

  @Test
  void givenMultipleExclusionReasons_whenTotalExcluded_thenSumsAllCounts() {
    WarningCollector collector = new WarningCollector();

    collector.exclude("no impressions data", 3);
    collector.exclude("low availability", 5);
    collector.exclude("over budget");

    assertThat(collector.totalExcluded()).isEqualTo(9);
  }
}
