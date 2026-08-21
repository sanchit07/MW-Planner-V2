package com.mw.recommendation.engine.v3.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.recommendation.engine.v3.dto.RecommendationV3RequestDTO;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestHashV3UtilsTest {

  private static RecommendationV3RequestDTO.RecommendationV3RequestDTOBuilder baseRequest() {
    return RecommendationV3RequestDTO.builder()
        .country("Malaysia")
        .startDate(LocalDate.of(2026, 8, 1))
        .endDate(LocalDate.of(2026, 8, 31));
  }

  @Test
  void givenIdenticalRequests_whenHash_thenSameHash() {
    String hashA = RequestHashV3Utils.hashRequest(baseRequest().build());
    String hashB = RequestHashV3Utils.hashRequest(baseRequest().build());

    assertThat(hashA).isEqualTo(hashB);
  }

  @Test
  void givenOnlyTopNDiffers_whenHash_thenSameHash() {
    // topN only limits how many results come back — it must not break dedup
    String hashA = RequestHashV3Utils.hashRequest(baseRequest().topN(10).build());
    String hashB = RequestHashV3Utils.hashRequest(baseRequest().topN(500).build());

    assertThat(hashA).isEqualTo(hashB);
  }

  @Test
  void givenSeedDiffers_whenHash_thenDifferentHash() {
    // seed changes the produced ranking, so it must be part of the dedup identity
    String hashA = RequestHashV3Utils.hashRequest(baseRequest().seed("alpha").build());
    String hashB = RequestHashV3Utils.hashRequest(baseRequest().seed("beta").build());

    assertThat(hashA).isNotEqualTo(hashB);
  }

  @Test
  void givenSearchKeywordCaseWhitespaceAndOrderVariants_whenHash_thenSameHash() {
    String hashA =
        RequestHashV3Utils.hashRequest(
            baseRequest().searchKeywords(List.of("Mall", " mall ", "AIRPORT")).build());
    String hashB =
        RequestHashV3Utils.hashRequest(
            baseRequest().searchKeywords(List.of("airport", "mall")).build());

    assertThat(hashA).isEqualTo(hashB);
  }

  @Test
  void givenDifferentCountry_whenHash_thenDifferentHash() {
    String hashA = RequestHashV3Utils.hashRequest(baseRequest().build());
    String hashB = RequestHashV3Utils.hashRequest(baseRequest().country("Singapore").build());

    assertThat(hashA).isNotEqualTo(hashB);
  }
}
