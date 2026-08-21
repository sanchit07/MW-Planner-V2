package com.mw.planner.repository.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.mw.planner.domain.Agency;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@ExtendWith(MockitoExtension.class)
class AgencyRepositoryImplTest {

  @Mock private MongoTemplate mongoTemplate;

  @Mock private AggregationResults<Agency> aggregationResults;

  @Mock private AggregationResults<AgencyRepositoryImpl.CountResult> countResults;

  private AgencyRepositoryImpl agencyRepositoryImpl;

  @BeforeEach
  void setUp() {
    agencyRepositoryImpl = new AgencyRepositoryImpl(mongoTemplate);
  }

  @Test
  void findByNameOrCountryNameContainingIgnoreCase_WithValidSearch_ShouldReturnResults() {
    // Given
    String searchTerm = "test";
    Pageable pageable = PageRequest.of(0, 10);
    Agency testAgency = createTestAgency();
    List<Agency> agencies = List.of(testAgency);

    // Mock count result
    AgencyRepositoryImpl.CountResult countResult = new AgencyRepositoryImpl.CountResult();
    countResult.setTotal(1L);

    when(mongoTemplate.aggregate(any(Aggregation.class), eq("agencies"), eq(Agency.class)))
        .thenReturn(aggregationResults);
    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq("agencies"), eq(AgencyRepositoryImpl.CountResult.class)))
        .thenReturn(countResults);
    when(aggregationResults.getMappedResults()).thenReturn(agencies);
    when(countResults.getMappedResults()).thenReturn(List.of(countResult));

    // When
    Page<Agency> result =
        agencyRepositoryImpl.findByNameOrCountryNameContainingIgnoreCase(searchTerm, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getName()).isEqualTo("Test Agency");
    assertThat(result.getTotalElements()).isEqualTo(1);
  }

  @Test
  void findByNameOrCountryNameContainingIgnoreCase_WithEmptyResults_ShouldReturnEmptyPage() {
    // Given
    String searchTerm = "nonexistent";
    Pageable pageable = PageRequest.of(0, 10);

    when(mongoTemplate.aggregate(any(Aggregation.class), eq("agencies"), eq(Agency.class)))
        .thenReturn(aggregationResults);
    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq("agencies"), eq(AgencyRepositoryImpl.CountResult.class)))
        .thenReturn(countResults);
    when(aggregationResults.getMappedResults()).thenReturn(Collections.emptyList());
    when(countResults.getMappedResults()).thenReturn(Collections.emptyList());

    // When
    Page<Agency> result =
        agencyRepositoryImpl.findByNameOrCountryNameContainingIgnoreCase(searchTerm, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0);
  }

  private Agency createTestAgency() {
    Agency agency = new Agency();
    agency.setId("agency123");
    agency.setName("Test Agency");
    agency.setCountryId("US");
    return agency;
  }
}
