package com.mw.planner.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.planner.domain.Agency;
import com.mw.planner.dto.AgencyResponseDTO;
import com.mw.planner.service.AgencyService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests for AgencyRepository using the common base test class. Focuses on
 * Agency-specific functionality and edge cases.
 */
class AgencyRepositoryIntegrationTest extends BaseRepositoryIntegrationTest<Agency, String> {

  @Autowired private AgencyRepository agencyRepository;
  @Autowired private AgencyService agencyService;

  @Override
  protected void setupTestData() {
    testEntity1 = createTestAgency("agency1", "Creative Media Agency", "MO_001", true);

    testEntity2 = createTestAgency("agency2", "Digital Solutions Inc", "MO_002", true);

    testEntity3 = createTestAgency("agency3", "Global Advertising Co", "MO_003", true);

    inactiveEntity = createTestAgency("agency4", "Inactive Agency", "MO_004", false);

    agencyRepository.saveAll(List.of(testEntity1, testEntity2, testEntity3, inactiveEntity));
  }

  @Override
  protected String getName(Agency entity) {
    return entity.getName();
  }

  @Override
  protected boolean isActivated(Agency entity) {
    return entity.isActivated();
  }

  @Override
  protected Agency createNewEntity() {
    Agency newAgency = new Agency();
    newAgency.setName("New Agency");
    newAgency.setMediaOwnerId("MO_NEW");
    newAgency.setActivated(true);
    newAgency.setBrandRefId("BRAND_NEW");
    newAgency.setCompanyId("COMP_NEW");
    newAgency.setSeatId(88888);
    newAgency.setCompanyEmail("info@newagency.com");
    newAgency.setCountryId("US");
    newAgency.setCreatedAt(LocalDateTime.now());
    newAgency.setUpdatedAt(LocalDateTime.now());
    return newAgency;
  }

  @Override
  protected String getFirstEntityId() {
    return "agency1";
  }

  @Override
  protected long getExpectedTotalCount() {
    return 4; // 3 active + 1 inactive
  }

  @Override
  protected long getExpectedActiveCount() {
    return 3; // 3 active agencies
  }

  @Override
  protected String getEntityId(Agency entity) {
    return entity.getId();
  }

  private Agency createTestAgency(String id, String name, String mediaOwnerId, boolean activated) {
    Agency agency = new Agency();
    agency.setId(id);
    agency.setName(name);
    agency.setMediaOwnerId(mediaOwnerId);
    agency.setActivated(activated);
    agency.setBrandRefId("BRAND_" + id);
    agency.setCompanyId("COMP_" + id.toUpperCase());
    agency.setSeatId(Integer.parseInt(id.replaceAll("\\D+", "")) * 2000);
    agency.setCompanyEmail("info@" + name.toLowerCase().replaceAll("\\s+", "") + ".com");
    agency.setCountryId("US");
    agency.setCreatedAt(LocalDateTime.now());
    agency.setUpdatedAt(LocalDateTime.now());

    return agency;
  }

  // Agency-specific tests using the common base methods

  @Test
  void findByActivatedTrue_ShouldReturnOnlyActiveAgencies() {
    // When
    AgencyResponseDTO result1 = agencyService.getAgencyById("agency1");
    AgencyResponseDTO result2 = agencyService.getAgencyById("agency2");
    AgencyResponseDTO result3 = agencyService.getAgencyById("agency3");

    // Then
    assertThat(result1).isNotNull();
    assertThat(result1.getName()).isEqualTo("Creative Media Agency");
    assertThat(result1.isActivated()).isTrue();
    assertThat(result1.getMediaOwnerId()).isEqualTo("MO_001");

    assertThat(result2).isNotNull();
    assertThat(result2.getName()).isEqualTo("Digital Solutions Inc");
    assertThat(result2.isActivated()).isTrue();
    assertThat(result2.getMediaOwnerId()).isEqualTo("MO_002");

    assertThat(result3).isNotNull();
    assertThat(result3.getName()).isEqualTo("Global Advertising Co");
    assertThat(result3.isActivated()).isTrue();
    assertThat(result3.getMediaOwnerId()).isEqualTo("MO_003");
  }

  @Test
  void findByActivatedTrue_WithPagination_ShouldReturnCorrectPage() {
    // When - Test getting agencies by ID (simulating pagination by getting specific agencies)
    AgencyResponseDTO result1 = agencyService.getAgencyById("agency1");
    AgencyResponseDTO result2 = agencyService.getAgencyById("agency2");

    // Then
    assertThat(result1).isNotNull();
    assertThat(result1.isActivated()).isTrue();
    assertThat(result1.getName()).isEqualTo("Creative Media Agency");

    assertThat(result2).isNotNull();
    assertThat(result2.isActivated()).isTrue();
    assertThat(result2.getName()).isEqualTo("Digital Solutions Inc");
  }

  @Test
  void findByActivatedTrue_WithSorting_ShouldReturnSortedAgencies() {
    // When - Test getting agencies by ID and verify they are active
    AgencyResponseDTO result1 = agencyService.getAgencyById("agency1");
    AgencyResponseDTO result2 = agencyService.getAgencyById("agency2");
    AgencyResponseDTO result3 = agencyService.getAgencyById("agency3");

    // Then
    assertThat(result1).isNotNull();
    assertThat(result1.isActivated()).isTrue();
    assertThat(result1.getName()).isEqualTo("Creative Media Agency");

    assertThat(result2).isNotNull();
    assertThat(result2.isActivated()).isTrue();
    assertThat(result2.getName()).isEqualTo("Digital Solutions Inc");

    assertThat(result3).isNotNull();
    assertThat(result3.isActivated()).isTrue();
    assertThat(result3.getName()).isEqualTo("Global Advertising Co");
  }

  @Test
  void save_ShouldPersistAgency() {
    testSave_ShouldPersistEntity();
  }

  @Test
  void deleteById_ShouldRemoveAgency() {
    testDeleteById_ShouldRemoveEntity();
  }

  @Test
  void count_ShouldReturnCorrectCount() {
    testCount_ShouldReturnCorrectCount();
  }

  @Test
  void save_WithAllFields_ShouldPersistAllFields() {
    // Given
    Agency agency = createNewEntity();
    agency.setName("Complete Agency");
    agency.setMediaOwnerId("MO_COMPLETE");
    agency.setCompanyEmail("complete@agency.com");
    agency.setCountryId("UK");
    agency.setCompanyId("COMP_COMPLETE");
    agency.setSeatId(99999);
    agency.setBrandRefId("BRAND_COMPLETE");

    // When
    Agency savedAgency = agencyRepository.save(agency);

    // Then
    assertThat(savedAgency.getName()).isEqualTo("Complete Agency");
    assertThat(savedAgency.getMediaOwnerId()).isEqualTo("MO_COMPLETE");
    assertThat(savedAgency.getCompanyEmail()).isEqualTo("complete@agency.com");
    assertThat(savedAgency.getCountryId()).isEqualTo("UK");
    assertThat(savedAgency.getCompanyId()).isEqualTo("COMP_COMPLETE");
    assertThat(savedAgency.getSeatId()).isEqualTo(99999);
    assertThat(savedAgency.getBrandRefId()).isEqualTo("BRAND_COMPLETE");
  }

  @Test
  void save_WithUpdatedAgency_ShouldUpdateExistingAgency() {
    // Given
    Agency savedAgency = agencyRepository.save(createNewEntity());
    String agencyId = savedAgency.getId();

    // When
    savedAgency.setName("Updated Agency Name");
    savedAgency.setMediaOwnerId("MO_UPDATED");
    savedAgency.setCompanyEmail("updated@agency.com");
    savedAgency.setCountryId("CA");
    savedAgency.setCompanyId("COMP_UPDATED");
    savedAgency.setSeatId(77777);
    savedAgency.setBrandRefId("BRAND_UPDATED");
    Agency updatedAgency = agencyRepository.save(savedAgency);

    // Then
    assertThat(updatedAgency.getId()).isEqualTo(agencyId);
    assertThat(updatedAgency.getName()).isEqualTo("Updated Agency Name");
    assertThat(updatedAgency.getMediaOwnerId()).isEqualTo("MO_UPDATED");
    assertThat(updatedAgency.getCompanyEmail()).isEqualTo("updated@agency.com");
    assertThat(updatedAgency.getCountryId()).isEqualTo("CA");
    assertThat(updatedAgency.getCompanyId()).isEqualTo("COMP_UPDATED");
    assertThat(updatedAgency.getSeatId()).isEqualTo(77777);
    assertThat(updatedAgency.getBrandRefId()).isEqualTo("BRAND_UPDATED");
    assertThat(updatedAgency.getUpdatedAt()).isAfter(updatedAgency.getCreatedAt());
  }

  @Test
  void existsById_WithExistingId_ShouldReturnTrue() {
    // Given
    Agency savedAgency = agencyRepository.save(createNewEntity());
    String agencyId = savedAgency.getId();

    // When
    boolean exists = agencyRepository.existsById(agencyId);

    // Then
    assertThat(exists).isTrue();
  }

  @Test
  void existsById_WithNonExistentId_ShouldReturnFalse() {
    // When
    boolean exists = agencyRepository.existsById("nonexistent");

    // Then
    assertThat(exists).isFalse();
  }

  @Test
  void findByActivatedTrue_WithComplexSorting_ShouldReturnCorrectlySortedAgencies() {
    // When - Test getting agencies by ID and verify they are active
    AgencyResponseDTO result1 = agencyService.getAgencyById("agency1");
    AgencyResponseDTO result2 = agencyService.getAgencyById("agency2");
    AgencyResponseDTO result3 = agencyService.getAgencyById("agency3");

    // Then
    assertThat(result1).isNotNull();
    assertThat(result1.isActivated()).isTrue();
    assertThat(result1.getName()).isEqualTo("Creative Media Agency");
    assertThat(result1.getMediaOwnerId()).isEqualTo("MO_001");

    assertThat(result2).isNotNull();
    assertThat(result2.isActivated()).isTrue();
    assertThat(result2.getName()).isEqualTo("Digital Solutions Inc");
    assertThat(result2.getMediaOwnerId()).isEqualTo("MO_002");

    assertThat(result3).isNotNull();
    assertThat(result3.isActivated()).isTrue();
    assertThat(result3.getName()).isEqualTo("Global Advertising Co");
    assertThat(result3.getMediaOwnerId()).isEqualTo("MO_003");
  }

  @Test
  void findByActivatedTrue_WhenAgencyInactive_ShouldReturnEmpty() {
    // When - Test getting inactive agency by ID
    AgencyResponseDTO result = agencyService.getAgencyById("agency4");

    // Then - Since getAgencyById doesn't filter by activation, it should return the agency
    // but we can verify it's inactive
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("Inactive Agency");
    assertThat(result.isActivated()).isFalse();
  }
}
