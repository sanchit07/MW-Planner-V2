package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.Venues;
import com.mw.planner.dto.VenueItemDTO;
import com.mw.planner.repository.VenuesRepository;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VenuesServiceTest {

  @Mock private VenuesRepository venuesRepository;
  @Mock private MessageService messageService;

  @InjectMocks private VenuesService venuesService;

  private Venues parentVenue;
  private Venues childVenue1;
  private Venues childVenue2;
  private Venues grandChildVenue;

  @BeforeEach
  void setUp() {
    // Create parent venue
    parentVenue = new Venues();
    parentVenue.setId("parent1");
    parentVenue.setEnumerationId(1);
    parentVenue.setTier(1);
    parentVenue.setParentCategory("Parent Venue");
    parentVenue.setStringValue("parent-value");
    parentVenue.setParentEnumerationId(null);

    // Create child venue 1
    childVenue1 = new Venues();
    childVenue1.setId("child1");
    childVenue1.setEnumerationId(101);
    childVenue1.setTier(2);
    childVenue1.setChildCategory("Child Venue 1");
    childVenue1.setDefinition("Child 1 Description");
    childVenue1.setStringValue("child1-value");
    childVenue1.setParentEnumerationId(1);

    // Create child venue 2
    childVenue2 = new Venues();
    childVenue2.setId("child2");
    childVenue2.setEnumerationId(102);
    childVenue2.setTier(2);
    childVenue2.setChildCategory("Child Venue 2");
    childVenue2.setDefinition("Child 2 Description");
    childVenue2.setStringValue("child2-value");
    childVenue2.setParentEnumerationId(1);

    // Create grandchild venue
    grandChildVenue = new Venues();
    grandChildVenue.setId("grandchild1");
    grandChildVenue.setEnumerationId(10101);
    grandChildVenue.setTier(3);
    grandChildVenue.setGrandChildCategory("Grandchild Venue");
    grandChildVenue.setDefinition("Grandchild Description");
    grandChildVenue.setStringValue("grandchild-value");
    grandChildVenue.setParentEnumerationId(101);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(venuesRepository);
  }

  @Test
  void getHierarchicalVenues_WithEmptyDatabase_ShouldReturnEmptyList() {
    // Given
    when(venuesRepository.findAll()).thenReturn(Collections.emptyList());

    // When
    List<VenueItemDTO> result = venuesService.getHierarchicalVenues();

    // Then
    assertThat(result).isNotNull().isEmpty();
    verify(venuesRepository).findAll();
  }

  @Test
  void getHierarchicalVenues_WithSingleRootVenue_ShouldReturnSingleItem() {
    // Given
    when(venuesRepository.findAll()).thenReturn(Collections.singletonList(parentVenue));

    // When
    List<VenueItemDTO> result = venuesService.getHierarchicalVenues();

    // Then
    assertThat(result).hasSize(1);
    VenueItemDTO rootItem = result.get(0);
    assertThat(rootItem.getName()).isEqualTo("Parent Venue");
    assertThat(rootItem.getEnumerationId()).isEqualTo(1);
    assertThat(rootItem.getStringValue()).isEqualTo("parent-value");
    assertThat(rootItem.getChildren()).isEmpty();
    verify(venuesRepository).findAll();
  }

  @Test
  void getHierarchicalVenues_WithParentAndChildren_ShouldBuildHierarchy() {
    // Given
    List<Venues> allVenues = Arrays.asList(parentVenue, childVenue1, childVenue2);
    when(venuesRepository.findAll()).thenReturn(allVenues);

    // When
    List<VenueItemDTO> result = venuesService.getHierarchicalVenues();

    // Then
    assertThat(result).hasSize(1);
    VenueItemDTO rootItem = result.get(0);
    assertThat(rootItem.getName()).isEqualTo("Parent Venue");
    assertThat(rootItem.getChildren()).hasSize(2);

    VenueItemDTO child1 = rootItem.getChildren().get(0);
    assertThat(child1.getName()).isEqualTo("Child Venue 1");
    assertThat(child1.getEnumerationId()).isEqualTo(101);
    assertThat(child1.getChildren()).isEmpty();

    VenueItemDTO child2 = rootItem.getChildren().get(1);
    assertThat(child2.getName()).isEqualTo("Child Venue 2");
    assertThat(child2.getEnumerationId()).isEqualTo(102);
    assertThat(child2.getChildren()).isEmpty();

    verify(venuesRepository).findAll();
  }

  @Test
  void getHierarchicalVenues_WithMultiLevelHierarchy_ShouldBuildCompleteHierarchy() {
    // Given
    List<Venues> allVenues = Arrays.asList(parentVenue, childVenue1, childVenue2, grandChildVenue);
    when(venuesRepository.findAll()).thenReturn(allVenues);

    // When
    List<VenueItemDTO> result = venuesService.getHierarchicalVenues();

    // Then
    assertThat(result).hasSize(1);
    VenueItemDTO rootItem = result.get(0);
    assertThat(rootItem.getName()).isEqualTo("Parent Venue");
    assertThat(rootItem.getChildren()).hasSize(2);

    // Find child1 and verify it has grandchild
    VenueItemDTO child1 =
        rootItem.getChildren().stream()
            .filter(child -> "Child Venue 1".equals(child.getName()))
            .findFirst()
            .orElse(null);
    assertThat(child1).isNotNull();
    assertThat(child1.getChildren()).hasSize(1);
    assertThat(child1.getChildren().get(0).getName()).isEqualTo("Grandchild Venue");

    verify(venuesRepository).findAll();
  }

  @Test
  void getHierarchicalVenues_WithMultipleRootVenues_ShouldReturnMultipleRoots() {
    // Given
    Venues anotherParent = new Venues();
    anotherParent.setId("parent2");
    anotherParent.setEnumerationId(2);
    anotherParent.setTier(1);
    anotherParent.setParentCategory("Another Parent");
    anotherParent.setStringValue("parent2-value");
    anotherParent.setParentEnumerationId(null);

    List<Venues> allVenues = Arrays.asList(parentVenue, anotherParent, childVenue1);
    when(venuesRepository.findAll()).thenReturn(allVenues);

    // When
    List<VenueItemDTO> result = venuesService.getHierarchicalVenues();

    // Then
    assertThat(result).hasSize(2);

    // Verify first root
    VenueItemDTO firstRoot =
        result.stream()
            .filter(item -> "Parent Venue".equals(item.getName()))
            .findFirst()
            .orElse(null);
    assertThat(firstRoot).isNotNull();
    assertThat(firstRoot.getChildren()).hasSize(1);

    // Verify second root
    VenueItemDTO secondRoot =
        result.stream()
            .filter(item -> "Another Parent".equals(item.getName()))
            .findFirst()
            .orElse(null);
    assertThat(secondRoot).isNotNull();
    assertThat(secondRoot.getChildren()).isEmpty();

    verify(venuesRepository).findAll();
  }

  @Test
  void getHierarchicalVenues_WithOrphanedChildren_ShouldHandleCorrectly() {
    // Given
    // Create orphaned child (parent doesn't exist in the list)
    Venues orphanedChild = new Venues();
    orphanedChild.setId("orphan");
    orphanedChild.setEnumerationId(999);
    orphanedChild.setTier(2);
    orphanedChild.setChildCategory("Orphaned Child");
    orphanedChild.setDefinition("Orphaned Description");
    orphanedChild.setStringValue("orphan-value");
    orphanedChild.setParentEnumerationId(9999);

    List<Venues> allVenues = Arrays.asList(parentVenue, orphanedChild);
    when(venuesRepository.findAll()).thenReturn(allVenues);

    // When
    List<VenueItemDTO> result = venuesService.getHierarchicalVenues();

    // Then
    assertThat(result).hasSize(1);
    VenueItemDTO rootItem = result.get(0);
    assertThat(rootItem.getName()).isEqualTo("Parent Venue");
    assertThat(rootItem.getChildren()).isEmpty();
    verify(venuesRepository).findAll();
  }

  @Test
  void getHierarchicalVenues_WithComplexHierarchy_ShouldBuildCorrectStructure() {
    // Given
    Venues grandParent = new Venues();
    grandParent.setId("grandparent");
    grandParent.setEnumerationId(10);
    grandParent.setTier(1);
    grandParent.setParentCategory("Grand Parent");
    grandParent.setStringValue("grandparent-value");
    grandParent.setParentEnumerationId(null);

    Venues parent = new Venues();
    parent.setId("parent");
    parent.setEnumerationId(1001);
    parent.setTier(2);
    parent.setChildCategory("Parent");
    parent.setDefinition("Parent Description");
    parent.setStringValue("parent-value");
    parent.setParentEnumerationId(10);

    Venues child = new Venues();
    child.setId("child");
    child.setEnumerationId(100101);
    child.setTier(3);
    child.setGrandChildCategory("Child");
    child.setDefinition("Child Description");
    child.setStringValue("child-value");
    child.setParentEnumerationId(1001);

    List<Venues> allVenues = Arrays.asList(grandParent, parent, child);
    when(venuesRepository.findAll()).thenReturn(allVenues);

    // When
    List<VenueItemDTO> result = venuesService.getHierarchicalVenues();

    // Then
    assertThat(result).hasSize(1);
    VenueItemDTO grandParentItem = result.get(0);
    assertThat(grandParentItem.getName()).isEqualTo("Grand Parent");
    assertThat(grandParentItem.getChildren()).hasSize(1);

    VenueItemDTO parentItem = grandParentItem.getChildren().get(0);
    assertThat(parentItem.getName()).isEqualTo("Parent");
    assertThat(parentItem.getChildren()).hasSize(1);

    VenueItemDTO childItem = parentItem.getChildren().get(0);
    assertThat(childItem.getName()).isEqualTo("Child");
    assertThat(childItem.getChildren()).isEmpty();

    verify(venuesRepository).findAll();
  }

  @Test
  void getVenueSlugToIdMap_WithValidVenues_ReturnsSlugToIdMapping() {
    Venues v1 = new Venues();
    v1.setStringValue("health-beauty-gyms");
    v1.setEnumerationId(401);
    v1.setParentCategory("Gyms");

    Venues v2 = new Venues();
    v2.setStringValue("outdoor-billboards");
    v2.setEnumerationId(301);
    v2.setParentCategory("Billboards");

    when(venuesRepository.findAll()).thenReturn(Arrays.asList(v1, v2));

    Map<String, String> result = venuesService.getVenueSlugToIdMap();

    assertThat(result).hasSize(2);
    assertThat(result).containsEntry("health-beauty-gyms", "401");
    assertThat(result).containsEntry("outdoor-billboards", "301");
  }

  @Test
  void getVenueSlugToIdMap_WithNullStringValue_ExcludesEntry() {
    Venues v1 = new Venues();
    v1.setStringValue(null);
    v1.setEnumerationId(401);
    v1.setParentCategory("Gyms");

    Venues v2 = new Venues();
    v2.setStringValue("outdoor-billboards");
    v2.setEnumerationId(301);
    v2.setParentCategory("Billboards");

    when(venuesRepository.findAll()).thenReturn(Arrays.asList(v1, v2));

    Map<String, String> result = venuesService.getVenueSlugToIdMap();

    assertThat(result).hasSize(1);
    assertThat(result).containsEntry("outdoor-billboards", "301");
  }

  @Test
  void getVenueSlugToIdMap_WithNullEnumerationId_ExcludesEntry() {
    Venues v1 = new Venues();
    v1.setStringValue("health-beauty-gyms");
    v1.setEnumerationId(null);
    v1.setParentCategory("Gyms");

    Venues v2 = new Venues();
    v2.setStringValue("outdoor-billboards");
    v2.setEnumerationId(301);
    v2.setParentCategory("Billboards");

    when(venuesRepository.findAll()).thenReturn(Arrays.asList(v1, v2));

    Map<String, String> result = venuesService.getVenueSlugToIdMap();

    assertThat(result).hasSize(1);
    assertThat(result).containsEntry("outdoor-billboards", "301");
  }
}
