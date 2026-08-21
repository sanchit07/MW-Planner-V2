package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mw.planner.domain.InventoryCountrySummary;
import com.mw.planner.repository.InventoryCountrySummaryRepository;
import com.mw.planner.repository.InventoryRepositoryCustom;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryCountrySummaryServiceTest {

  @Mock private InventoryRepositoryCustom inventoryRepositoryCustom;
  @Mock private InventoryCountrySummaryRepository summaryRepository;

  @InjectMocks private InventoryCountrySummaryService service;

  @Test
  void refreshSummaryByCountry_recomputesAndUpsertsTheCountry() {
    // Given
    when(inventoryRepositoryCustom.getInventoryCountsByCountryAndClassification(anyCollection()))
        .thenReturn(Map.of("India", Map.of("Classic", 125050L, "Digital", 38532L)));

    // When
    service.refreshSummaryByCountry("India");

    // Then
    ArgumentCaptor<InventoryCountrySummary> captor =
        ArgumentCaptor.forClass(InventoryCountrySummary.class);
    verify(summaryRepository).save(captor.capture());
    InventoryCountrySummary saved = captor.getValue();
    assertThat(saved.getCountry()).isEqualTo("India");
    assertThat(saved.getClassificationCounts())
        .containsEntry("Classic", 125050L)
        .containsEntry("Digital", 38532L);
    assertThat(saved.getTotalCount()).isEqualTo(163582L);
    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  @Test
  void refreshSummaryByCountry_whenNoInventories_storesZero() {
    // Given — the aggregation returns nothing for the country
    when(inventoryRepositoryCustom.getInventoryCountsByCountryAndClassification(anyCollection()))
        .thenReturn(Map.of());

    // When
    service.refreshSummaryByCountry("Atlantis");

    // Then — the summary is still written with an empty map and a zero total
    ArgumentCaptor<InventoryCountrySummary> captor =
        ArgumentCaptor.forClass(InventoryCountrySummary.class);
    verify(summaryRepository).save(captor.capture());
    InventoryCountrySummary saved = captor.getValue();
    assertThat(saved.getCountry()).isEqualTo("Atlantis");
    assertThat(saved.getClassificationCounts()).isEmpty();
    assertThat(saved.getTotalCount()).isZero();
  }

  @Test
  void refreshSummaryByCountry_whenCountryBlank_isNoOp() {
    // When
    service.refreshSummaryByCountry("   ");
    service.refreshSummaryByCountry(null);

    // Then
    verifyNoInteractions(inventoryRepositoryCustom, summaryRepository);
  }

  @Test
  void rebuildAll_writesEveryCountryAndReturnsCount() {
    // Given
    when(inventoryRepositoryCustom.getInventoryCountsByCountryAndClassification())
        .thenReturn(
            Map.of(
                "India", Map.of("Classic", 10L, "Digital", 5L),
                "Singapore", Map.of("Digital", 3L)));

    // When
    int written = service.rebuildAll();

    // Then
    assertThat(written).isEqualTo(2);
    verify(summaryRepository, times(2)).save(any(InventoryCountrySummary.class));
    verify(inventoryRepositoryCustom, never())
        .getInventoryCountsByCountryAndClassification(anyCollection());
  }
}
