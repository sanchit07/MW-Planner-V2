package com.mw.planner.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.planner.TestcontainersConfiguration;
import com.mw.planner.domain.InventoryCountrySummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Integration test for {@link InventoryCountrySummaryRepository} against a real MongoDB
 * (Testcontainers). Verifies that the country name works as the document id (save is an upsert) and
 * that {@code findByCountryIn} returns only the requested countries.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InventoryCountrySummaryRepositoryIntegrationTest {

  @Autowired private InventoryCountrySummaryRepository repository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  private InventoryCountrySummary summary(String country, Map<String, Long> counts) {
    long total = counts.values().stream().mapToLong(Long::longValue).sum();
    return InventoryCountrySummary.builder()
        .country(country)
        .classificationCounts(counts)
        .totalCount(total)
        .updatedAt(Instant.now())
        .build();
  }

  @Test
  @DisplayName("findByCountryIn returns only the requested countries")
  void findByCountryIn_ReturnsRequestedCountries() {
    repository.save(summary("India", Map.of("Classic", 125050L, "Digital", 38532L)));
    repository.save(summary("Singapore", Map.of("Digital", 4467L, "Classic", 575L)));
    repository.save(summary("Malaysia", Map.of("Classic", 10L)));

    List<InventoryCountrySummary> result =
        repository.findByCountryIn(List.of("India", "Singapore"));

    assertThat(result)
        .extracting(InventoryCountrySummary::getCountry)
        .containsExactlyInAnyOrder("India", "Singapore");
    InventoryCountrySummary india =
        result.stream().filter(s -> s.getCountry().equals("India")).findFirst().orElseThrow();
    assertThat(india.getTotalCount()).isEqualTo(163582L);
    assertThat(india.getClassificationCounts())
        .containsEntry("Classic", 125050L)
        .containsEntry("Digital", 38532L);
  }

  @Test
  @DisplayName("saving the same country replaces the existing document (upsert by id)")
  void save_SameCountry_ReplacesDocument() {
    repository.save(summary("India", Map.of("Classic", 100L)));
    repository.save(summary("India", Map.of("Classic", 200L, "Digital", 50L)));

    assertThat(repository.count()).isEqualTo(1);
    InventoryCountrySummary reloaded = repository.findById("India").orElseThrow();
    assertThat(reloaded.getTotalCount()).isEqualTo(250L);
    assertThat(reloaded.getClassificationCounts())
        .containsEntry("Classic", 200L)
        .containsEntry("Digital", 50L);
  }
}
