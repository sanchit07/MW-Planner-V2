package com.mw.planner.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.District;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DistrictRepositoryTest {

  @Mock private DistrictRepository repository;

  private District testDistrict1;
  private District testDistrict2;

  @BeforeEach
  void setUp() {
    testDistrict1 = new District();
    testDistrict1.setId("district123");
    testDistrict1.setName("Los Angeles");
    testDistrict1.setStateId("CA");
    testDistrict1.setType("DISTRICT");
    testDistrict1.setLatitude(34.0522);
    testDistrict1.setLongitude(-118.2437);

    testDistrict2 = new District();
    testDistrict2.setId("district456");
    testDistrict2.setName("San Francisco");
    testDistrict2.setStateId("CA");
    testDistrict2.setType("DISTRICT");
    testDistrict2.setLatitude(37.7749);
    testDistrict2.setLongitude(-122.4194);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(repository);
  }

  // ========== findByName Tests ==========

  @Test
  @DisplayName("findByName - Should return district when found")
  void findByName_WithExistingDistrict_ShouldReturnDistrict() {
    // Given
    when(repository.findByName("Los Angeles")).thenReturn(Optional.of(testDistrict1));

    // When
    Optional<District> result = repository.findByName("Los Angeles");

    // Then
    assertThat(result).isPresent();
    assertThat(result.get().getName()).isEqualTo("Los Angeles");
    assertThat(result.get().getStateId()).isEqualTo("CA");
    verify(repository).findByName("Los Angeles");
  }

  @Test
  @DisplayName("findByName - Should return empty when district not found")
  void findByName_WithNonExistentDistrict_ShouldReturnEmpty() {
    // Given
    when(repository.findByName("NonExistent")).thenReturn(Optional.empty());

    // When
    Optional<District> result = repository.findByName("NonExistent");

    // Then
    assertThat(result).isEmpty();
    verify(repository).findByName("NonExistent");
  }

  // ========== findByStateId Tests ==========

  @Test
  @DisplayName("findByStateId - Should return all districts for state")
  void findByStateId_WithValidState_ShouldReturnDistricts() {
    // Given
    when(repository.findByStateId("CA")).thenReturn(List.of(testDistrict1, testDistrict2));

    // When
    List<District> result = repository.findByStateId("CA");

    // Then
    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(District::getName)
        .containsExactlyInAnyOrder("Los Angeles", "San Francisco");
    verify(repository).findByStateId("CA");
  }

  @Test
  @DisplayName("findByStateId - Should return empty list when no districts found")
  void findByStateId_WithNoDistricts_ShouldReturnEmptyList() {
    // Given
    when(repository.findByStateId("NY")).thenReturn(List.of());

    // When
    List<District> result = repository.findByStateId("NY");

    // Then
    assertThat(result).isEmpty();
    verify(repository).findByStateId("NY");
  }
}
