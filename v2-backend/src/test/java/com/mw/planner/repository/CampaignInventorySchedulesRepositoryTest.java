package com.mw.planner.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.dto.CampaignSchedulePriceFilterDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CampaignInventorySchedulesRepositoryTest {

  @Mock private CampaignInventorySchedulesRepository repository;

  private CampaignInventorySchedules testSchedule1;
  private CampaignInventorySchedules testSchedule2;

  @BeforeEach
  void setUp() {
    testSchedule1 = new CampaignInventorySchedules();
    testSchedule1.setId("schedule1");
    testSchedule1.setCampaignId("campaign123");
    testSchedule1.setInventoryId("inventory123");
    testSchedule1.setMediaOwnerId("mediaOwner123");

    testSchedule2 = new CampaignInventorySchedules();
    testSchedule2.setId("schedule2");
    testSchedule2.setCampaignId("campaign123");
    testSchedule2.setInventoryId("inventory456");
    testSchedule2.setMediaOwnerId("mediaOwner123");
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(repository);
  }

  // ========== findByCampaignId Tests ==========

  @Test
  @DisplayName("findByCampaignId - Should return all schedules for campaign")
  void findByCampaignId_WithValidCampaign_ShouldReturnSchedules() {
    // Given
    when(repository.findByCampaignId("campaign123"))
        .thenReturn(List.of(testSchedule1, testSchedule2));

    // When
    List<CampaignInventorySchedules> result = repository.findByCampaignId("campaign123");

    // Then
    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(CampaignInventorySchedules::getId)
        .containsExactlyInAnyOrder("schedule1", "schedule2");
    verify(repository).findByCampaignId("campaign123");
  }

  @Test
  @DisplayName("findByCampaignId - Should return empty list when no schedules found")
  void findByCampaignId_WithNoSchedules_ShouldReturnEmptyList() {
    // Given
    when(repository.findByCampaignId("nonexistent")).thenReturn(List.of());

    // When
    List<CampaignInventorySchedules> result = repository.findByCampaignId("nonexistent");

    // Then
    assertThat(result).isEmpty();
    verify(repository).findByCampaignId("nonexistent");
  }

  // ========== findByCampaignIdAndInventoryId Tests ==========

  @Test
  @DisplayName("findByCampaignIdAndInventoryId - Should return schedule when found")
  void findByCampaignIdAndInventoryId_WithValidIds_ShouldReturnSchedule() {
    // Given
    when(repository.findByCampaignIdAndInventoryId("campaign123", "inventory123"))
        .thenReturn(Optional.of(testSchedule1));

    // When
    Optional<CampaignInventorySchedules> result =
        repository.findByCampaignIdAndInventoryId("campaign123", "inventory123");

    // Then
    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo("schedule1");
    assertThat(result.get().getInventoryId()).isEqualTo("inventory123");
    verify(repository).findByCampaignIdAndInventoryId("campaign123", "inventory123");
  }

  @Test
  @DisplayName("findByCampaignIdAndInventoryId - Should return empty when not found")
  void findByCampaignIdAndInventoryId_WithInvalidIds_ShouldReturnEmpty() {
    // Given
    when(repository.findByCampaignIdAndInventoryId("campaign123", "nonexistent"))
        .thenReturn(Optional.empty());

    // When
    Optional<CampaignInventorySchedules> result =
        repository.findByCampaignIdAndInventoryId("campaign123", "nonexistent");

    // Then
    assertThat(result).isEmpty();
    verify(repository).findByCampaignIdAndInventoryId("campaign123", "nonexistent");
  }

  // ========== findByCampaignIdAndInventoryIdIn Tests ==========

  @Test
  @DisplayName("findByCampaignIdAndInventoryIdIn - Should return matching schedules")
  void findByCampaignIdAndInventoryIdIn_WithValidIds_ShouldReturnSchedules() {
    // Given
    when(repository.findByCampaignIdAndInventoryIdIn(
            "campaign123", List.of("inventory123", "inventory456")))
        .thenReturn(List.of(testSchedule1, testSchedule2));

    // When
    List<CampaignInventorySchedules> result =
        repository.findByCampaignIdAndInventoryIdIn(
            "campaign123", List.of("inventory123", "inventory456"));

    // Then
    assertThat(result).hasSize(2);
    verify(repository)
        .findByCampaignIdAndInventoryIdIn("campaign123", List.of("inventory123", "inventory456"));
  }

  @Test
  @DisplayName("findByCampaignIdAndInventoryIdIn - Should return empty when no matches")
  void findByCampaignIdAndInventoryIdIn_WithNoMatches_ShouldReturnEmpty() {
    // Given
    when(repository.findByCampaignIdAndInventoryIdIn("campaign123", List.of("nonexistent")))
        .thenReturn(List.of());

    // When
    List<CampaignInventorySchedules> result =
        repository.findByCampaignIdAndInventoryIdIn("campaign123", List.of("nonexistent"));

    // Then
    assertThat(result).isEmpty();
    verify(repository).findByCampaignIdAndInventoryIdIn("campaign123", List.of("nonexistent"));
  }

  // ========== findByCampaignIdAndMediaOwnerId Tests ==========

  @Test
  @DisplayName("findByCampaignIdAndMediaOwnerId - Should return schedules for media owner")
  void findByCampaignIdAndMediaOwnerId_WithValidIds_ShouldReturnSchedules() {
    // Given
    when(repository.findByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner123"))
        .thenReturn(List.of(testSchedule1, testSchedule2));

    // When
    List<CampaignInventorySchedules> result =
        repository.findByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner123");

    // Then
    assertThat(result).hasSize(2);
    verify(repository).findByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner123");
  }

  // ========== findByCampaignIdAndMediaOwnerIdIn Tests ==========

  @Test
  @DisplayName("findByCampaignIdAndMediaOwnerIdIn - Should return schedules for media owners")
  void findByCampaignIdAndMediaOwnerIdIn_WithValidIds_ShouldReturnSchedules() {
    // Given
    List<String> mediaOwnerIds = List.of("mediaOwner123", "mediaOwner456");
    when(repository.findByCampaignIdAndMediaOwnerIdIn("campaign123", mediaOwnerIds))
        .thenReturn(List.of(testSchedule1, testSchedule2));

    // When
    List<CampaignInventorySchedules> result =
        repository.findByCampaignIdAndMediaOwnerIdIn("campaign123", mediaOwnerIds);

    // Then
    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(CampaignInventorySchedules::getId)
        .containsExactlyInAnyOrder("schedule1", "schedule2");
    verify(repository).findByCampaignIdAndMediaOwnerIdIn("campaign123", mediaOwnerIds);
  }

  @Test
  @DisplayName("findByCampaignIdAndMediaOwnerIdIn - Should return empty list when no matches")
  void findByCampaignIdAndMediaOwnerIdIn_WithNoMatches_ShouldReturnEmptyList() {
    // Given
    List<String> mediaOwnerIds = List.of("nonexistent");
    when(repository.findByCampaignIdAndMediaOwnerIdIn("campaign123", mediaOwnerIds))
        .thenReturn(List.of());

    // When
    List<CampaignInventorySchedules> result =
        repository.findByCampaignIdAndMediaOwnerIdIn("campaign123", mediaOwnerIds);

    // Then
    assertThat(result).isEmpty();
    verify(repository).findByCampaignIdAndMediaOwnerIdIn("campaign123", mediaOwnerIds);
  }

  // ========== countByCampaignId Tests ==========

  @Test
  @DisplayName("countByCampaignId - Should return correct count")
  void countByCampaignId_WithValidCampaign_ShouldReturnCount() {
    // Given
    when(repository.countByCampaignId("campaign123")).thenReturn(2L);

    // When
    long count = repository.countByCampaignId("campaign123");

    // Then
    assertThat(count).isEqualTo(2);
    verify(repository).countByCampaignId("campaign123");
  }

  @Test
  @DisplayName("countByCampaignId - Should return zero when no schedules")
  void countByCampaignId_WithNoSchedules_ShouldReturnZero() {
    // Given
    when(repository.countByCampaignId("nonexistent")).thenReturn(0L);

    // When
    long count = repository.countByCampaignId("nonexistent");

    // Then
    assertThat(count).isZero();
    verify(repository).countByCampaignId("nonexistent");
  }

  // ========== countByCampaignIdAndMediaOwnerId Tests ==========

  @Test
  @DisplayName("countByCampaignIdAndMediaOwnerId - Should return correct count")
  void countByCampaignIdAndMediaOwnerId_WithValidIds_ShouldReturnCount() {
    // Given
    when(repository.countByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner123"))
        .thenReturn(2L);

    // When
    long count = repository.countByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner123");

    // Then
    assertThat(count).isEqualTo(2);
    verify(repository).countByCampaignIdAndMediaOwnerId("campaign123", "mediaOwner123");
  }

  // ========== deleteByCampaignIdAndInventoryId Tests ==========

  @Test
  @DisplayName("deleteByCampaignIdAndInventoryId - Should delete schedule")
  void deleteByCampaignIdAndInventoryId_WithValidIds_ShouldDeleteSchedule() {
    // Given
    when(repository.deleteByCampaignIdAndInventoryId("campaign123", "inventory123")).thenReturn(1L);

    // When
    long deleted = repository.deleteByCampaignIdAndInventoryId("campaign123", "inventory123");

    // Then
    assertThat(deleted).isEqualTo(1);
    verify(repository).deleteByCampaignIdAndInventoryId("campaign123", "inventory123");
  }

  // ========== deleteByCampaignId Tests ==========

  @Test
  @DisplayName("deleteByCampaignId - Should delete all schedules for campaign")
  void deleteByCampaignId_WithValidCampaign_ShouldDeleteAllSchedules() {
    // Given
    doNothing().when(repository).deleteByCampaignId("campaign123");

    // When
    repository.deleteByCampaignId("campaign123");

    // Then
    verify(repository).deleteByCampaignId("campaign123");
  }

  // ========== deleteByCampaignIdAndInventoryIdIn Tests ==========

  @Test
  @DisplayName("deleteByCampaignIdAndInventoryIdIn - Should delete matching schedules")
  void deleteByCampaignIdAndInventoryIdIn_WithValidIds_ShouldDeleteSchedules() {
    // Given
    when(repository.deleteByCampaignIdAndInventoryIdIn(
            "campaign123", List.of("inventory123", "inventory456")))
        .thenReturn(2L);

    // When
    long deleted =
        repository.deleteByCampaignIdAndInventoryIdIn(
            "campaign123", List.of("inventory123", "inventory456"));

    // Then
    assertThat(deleted).isEqualTo(2);
    verify(repository)
        .deleteByCampaignIdAndInventoryIdIn("campaign123", List.of("inventory123", "inventory456"));
  }

  // ========== findWithPriceFilters Tests ==========

  @Test
  @DisplayName("findWithPriceFilters - Should return filtered schedules with pagination")
  void findWithPriceFilters_WithValidFilters_ShouldReturnFilteredResults() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter =
        CampaignSchedulePriceFilterDTO.builder()
            .cities(List.of("Bunkyo"))
            .inventoryTypes(List.of("Digital"))
            .mediaOwnerIds(List.of("mediaOwner123"))
            .minPricing(100.0)
            .maxPricing(1000.0)
            .build();

    Pageable pageable = PageRequest.of(0, 10);

    CampaignInventorySchedules filteredSchedule = new CampaignInventorySchedules();
    filteredSchedule.setId("schedule1");
    filteredSchedule.setCampaignId(campaignId);
    filteredSchedule.setInventoryId("inventory123");
    filteredSchedule.setMediaOwnerId("mediaOwner123");
    filteredSchedule.setScheduleIds(List.of("scheduleId1"));

    Page<CampaignInventorySchedules> expectedPage =
        new PageImpl<>(List.of(filteredSchedule), pageable, 1);

    when(repository.findWithPriceFilters(
            eq(campaignId), any(CampaignSchedulePriceFilterDTO.class), eq(pageable), isNull()))
        .thenReturn(expectedPage);

    // When
    Page<CampaignInventorySchedules> result =
        repository.findWithPriceFilters(campaignId, filter, pageable, null);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().getFirst().getInventoryId()).isEqualTo("inventory123");

    verify(repository)
        .findWithPriceFilters(
            eq(campaignId), any(CampaignSchedulePriceFilterDTO.class), eq(pageable), isNull());
  }

  @Test
  @DisplayName("findWithPriceFilters - Should return empty page when no matches")
  void findWithPriceFilters_WithNoMatches_ShouldReturnEmptyPage() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter =
        CampaignSchedulePriceFilterDTO.builder().cities(List.of("NonexistentCity")).build();

    Pageable pageable = PageRequest.of(0, 10);
    Page<CampaignInventorySchedules> emptyPage = new PageImpl<>(List.of(), pageable, 0);

    when(repository.findWithPriceFilters(
            eq(campaignId), any(CampaignSchedulePriceFilterDTO.class), eq(pageable), isNull()))
        .thenReturn(emptyPage);

    // When
    Page<CampaignInventorySchedules> result =
        repository.findWithPriceFilters(campaignId, filter, pageable, null);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();

    verify(repository)
        .findWithPriceFilters(
            eq(campaignId), any(CampaignSchedulePriceFilterDTO.class), eq(pageable), isNull());
  }

  @Test
  @DisplayName("findWithPriceFilters - Should handle null filter gracefully")
  void findWithPriceFilters_WithNullFilter_ShouldReturnResults() {
    // Given
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10);

    CampaignInventorySchedules schedule = new CampaignInventorySchedules();
    schedule.setId("schedule1");
    schedule.setCampaignId(campaignId);
    schedule.setInventoryId("inventory123");

    Page<CampaignInventorySchedules> expectedPage = new PageImpl<>(List.of(schedule), pageable, 1);

    when(repository.findWithPriceFilters(eq(campaignId), isNull(), eq(pageable), isNull()))
        .thenReturn(expectedPage);

    // When
    Page<CampaignInventorySchedules> result =
        repository.findWithPriceFilters(campaignId, null, pageable, null);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);

    verify(repository).findWithPriceFilters(eq(campaignId), isNull(), eq(pageable), isNull());
  }

  @Test
  @DisplayName("findWithPriceFilters - Should respect pagination parameters")
  void findWithPriceFilters_WithPagination_ShouldReturnCorrectPage() {
    // Given
    String campaignId = "campaign123";
    CampaignSchedulePriceFilterDTO filter = CampaignSchedulePriceFilterDTO.builder().build();
    Pageable pageable = PageRequest.of(1, 5); // Second page, 5 items per page

    List<CampaignInventorySchedules> schedules = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      CampaignInventorySchedules s = new CampaignInventorySchedules();
      s.setId("schedule" + i);
      s.setCampaignId(campaignId);
      s.setInventoryId("inventory" + i);
      schedules.add(s);
    }

    Page<CampaignInventorySchedules> expectedPage =
        new PageImpl<>(schedules, pageable, 15); // Total 15 items, page 1 (0-indexed)

    when(repository.findWithPriceFilters(
            eq(campaignId), any(CampaignSchedulePriceFilterDTO.class), eq(pageable), isNull()))
        .thenReturn(expectedPage);

    // When
    Page<CampaignInventorySchedules> result =
        repository.findWithPriceFilters(campaignId, filter, pageable, null);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(5);
    assertThat(result.getNumber()).isEqualTo(1); // Second page (0-indexed)
    assertThat(result.getSize()).isEqualTo(5);
    assertThat(result.getTotalElements()).isEqualTo(15);
    assertThat(result.getTotalPages()).isEqualTo(3);

    verify(repository)
        .findWithPriceFilters(
            eq(campaignId), any(CampaignSchedulePriceFilterDTO.class), eq(pageable), isNull());
  }
}
