package com.mw.recommendation.engine.v3.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AudienceFitCalculatorTest {

  private final AudienceFitCalculator calculator = new AudienceFitCalculator();

  @Test
  void givenPrdExampleSharesAndSiteSegments_whenCalculate_then80() {
    // PRD §5.7: campaign 60/40 vs site 70/20 → min(0.6,0.7)+min(0.4,0.2) = 0.8 → 80
    RecommendationV3RequestDTO.AudienceTargeting targeting =
        RecommendationV3RequestDTO.AudienceTargeting.builder()
            .audienceSegments(List.of("Travellers", "Adults25-44"))
            .audienceSegmentShares(Map.of("Travellers", 0.6, "Adults25-44", 0.4))
            .build();

    AudienceData audience = new AudienceData();
    audience.setMonthlySummary(AudienceData.MonthlySummary.builder().uniqueVisitors(1000L).build());
    audience.setAudienceSegments(
        List.of(
            AudienceData.AudienceSegment.builder()
                .segmentName("Travellers")
                .uniqueCount(700L)
                .build(),
            AudienceData.AudienceSegment.builder()
                .segmentName("Adults25-44")
                .uniqueCount(200L)
                .build()));

    Double score = calculator.calculate(new Inventory(), audience, targeting);

    assertThat(score).isCloseTo(80.0, offset(0.01));
  }

  @Test
  void givenNoTargeting_whenCalculate_thenNeutral50() {
    Double score = calculator.calculate(new Inventory(), new AudienceData(), null);

    assertThat(score).isEqualTo(50.0);
  }

  @Test
  void givenNoAudienceDataAndAirportVenueWithTravellerSegment_whenCalculate_thenHeuristic65() {
    RecommendationV3RequestDTO.AudienceTargeting targeting =
        RecommendationV3RequestDTO.AudienceTargeting.builder()
            .audienceSegments(List.of("Travellers"))
            .build();
    Inventory inventory = new Inventory();
    inventory.setVenueTypes(List.of("Airport"));

    Double score = calculator.calculate(inventory, null, targeting);

    assertThat(score).isEqualTo(65.0);
  }

  @Test
  void givenNoAudienceDataAndUnrelatedSegment_whenCalculate_thenWeak30() {
    RecommendationV3RequestDTO.AudienceTargeting targeting =
        RecommendationV3RequestDTO.AudienceTargeting.builder()
            .audienceSegments(List.of("Foodies"))
            .build();
    Inventory inventory = new Inventory();
    inventory.setVenueTypes(List.of("Airport"));

    Double score = calculator.calculate(inventory, null, targeting);

    assertThat(score).isEqualTo(30.0);
  }
}
