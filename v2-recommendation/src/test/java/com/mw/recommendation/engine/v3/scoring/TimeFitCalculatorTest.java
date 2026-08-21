package com.mw.recommendation.engine.v3.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TimeFitCalculatorTest {

  private final TimeFitCalculator calculator = new TimeFitCalculator();

  private static AudienceData.HourlySummary hour(long visitors) {
    return AudienceData.HourlySummary.builder().totalVisitors(visitors).build();
  }

  @Test
  void givenNoDayparts_whenCalculate_thenNeutral50() {
    assertThat(calculator.calculate(new AudienceData(), null)).isEqualTo(50.0);
    assertThat(calculator.calculate(new AudienceData(), List.of())).isEqualTo(50.0);
  }

  @Test
  void givenMorningDaypartCarryingHalfDailyVisitors_whenCalculate_then50() {
    // Hours 8 and 9 carry 50% of the daily total → weighted share 0.5 → 50
    AudienceData audience = new AudienceData();
    Map<Integer, AudienceData.HourlySummary> hourly = new HashMap<>();
    hourly.put(8, hour(250L));
    hourly.put(9, hour(250L));
    hourly.put(12, hour(300L));
    hourly.put(18, hour(200L));
    audience.setHourlySummary(hourly);

    List<RecommendationV3RequestDTO.Daypart> dayparts =
        List.of(
            RecommendationV3RequestDTO.Daypart.builder()
                .startHour(8)
                .endHour(10)
                .weight(1.0)
                .build());

    Double score = calculator.calculate(audience, dayparts);

    assertThat(score).isCloseTo(50.0, offset(0.5));
  }

  @Test
  void givenNoHourlyData_whenCalculate_thenNeutral50() {
    List<RecommendationV3RequestDTO.Daypart> dayparts =
        List.of(
            RecommendationV3RequestDTO.Daypart.builder()
                .startHour(8)
                .endHour(10)
                .weight(1.0)
                .build());

    assertThat(calculator.calculate(null, dayparts)).isEqualTo(50.0);
    assertThat(calculator.calculate(new AudienceData(), dayparts)).isEqualTo(50.0);
  }
}
