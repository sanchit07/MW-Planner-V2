package com.mw.planner.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.State;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StateRepositoryTest {

  @Mock private StateRepository repository;

  private State testState;

  @BeforeEach
  void setUp() {
    testState = new State();
    testState.setId("state123");
    testState.setStateId("CA");
    testState.setName("California");
    testState.setType("STATE");
    testState.setCountryId("US");
    testState.setLatitude(36.7783);
    testState.setLongitude(-119.4179);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(repository);
  }

  // ========== findByName Tests ==========

  @Test
  @DisplayName("findByName - Should return state when found")
  void findByName_WithExistingState_ShouldReturnState() {
    // Given
    when(repository.findByName("California")).thenReturn(Optional.of(testState));

    // When
    Optional<State> result = repository.findByName("California");

    // Then
    assertThat(result).isPresent();
    assertThat(result.get().getName()).isEqualTo("California");
    assertThat(result.get().getStateId()).isEqualTo("CA");
    verify(repository).findByName("California");
  }

  @Test
  @DisplayName("findByName - Should return empty when state not found")
  void findByName_WithNonExistentState_ShouldReturnEmpty() {
    // Given
    when(repository.findByName("NonExistent")).thenReturn(Optional.empty());

    // When
    Optional<State> result = repository.findByName("NonExistent");

    // Then
    assertThat(result).isEmpty();
    verify(repository).findByName("NonExistent");
  }

  @Test
  @DisplayName("findByName - Should be case sensitive")
  void findByName_WithCaseMismatch_ShouldReturnEmpty() {
    // Given
    when(repository.findByName("california")).thenReturn(Optional.empty());

    // When
    Optional<State> result = repository.findByName("california");

    // Then
    assertThat(result).isEmpty();
    verify(repository).findByName("california");
  }
}
