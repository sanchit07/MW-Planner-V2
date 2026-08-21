package com.mw.planner.service;

import static com.mw.planner.constants.CampaignActivityKey.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.CampaignGeoImportFile;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.Inventory;
import com.mw.planner.domain.SelectInventoryImports;
import com.mw.planner.dto.GeoCoordinatesImportRequestDTO;
import com.mw.planner.dto.GeoImportFileResponseDTO;
import com.mw.planner.dto.ImportInventoryDetailResponseDTO;
import com.mw.planner.dto.InventoryImportStatusResponseDTO;
import com.mw.planner.dto.InventoryImportStatusResponseDTO.ValidationType;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.csv.CsvUploadException;
import com.mw.planner.exception.inventory.InventoryImportException;
import com.mw.planner.repository.CampaignGeoImportFileRepository;
import com.mw.planner.repository.InventoryRepository;
import com.mw.planner.repository.SelectInventoryImportsRepository;
import com.mw.planner.service.CsvParsingService.InventoryIdRecord;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/** Comprehensive test class for InventoryImportService covering CSV verification functionality. */
@ExtendWith(MockitoExtension.class)
class InventoryImportServiceTest {

  @Mock private CsvParsingService csvParsingService;
  @Mock private InventoryService inventoryService;
  @Mock private SelectInventoryImportsRepository selectInventoryImportsRepository;
  @Mock private CampaignInventorySchedulesService campaignInventorySchedulesService;
  @Mock private InventoryRepository inventoryRepository;
  @Mock private CampaignGeoImportFileRepository campaignGeoImportFileRepository;
  @Mock private CampaignActivityService campaignActivityService;

  @InjectMocks private InventoryImportService inventoryImportService;

  private MultipartFile mockCsvFile;
  private List<InventoryIdRecord> mockRecords;
  private List<Inventory> mockInventories;
  private List<CampaignInventorySchedules> mockConfigs;

  @BeforeEach
  void setUp() {
    mockCsvFile = new MockMultipartFile("test.csv", "test.csv", "text/csv", "content".getBytes());
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(
        csvParsingService,
        inventoryService,
        selectInventoryImportsRepository,
        campaignInventorySchedulesService,
        inventoryRepository,
        campaignGeoImportFileRepository,
        campaignActivityService);
  }

  /**
   * Helper method to build SelectInventoryImports for tests.
   *
   * @param campaignId Campaign ID
   * @param companyId Company ID
   * @param fileName File name
   * @param referenceIds List of reference IDs
   * @param countryName Country name
   * @return SelectInventoryImports instance
   */
  private SelectInventoryImports buildSelectInventoryImports(
      String campaignId,
      String companyId,
      String fileName,
      List<String> referenceIds,
      String countryName) {
    return SelectInventoryImports.builder()
        .campaignId(campaignId)
        .companyId(companyId)
        .fileName(fileName)
        .inventoryRefIds(referenceIds)
        .countryName(countryName)
        .build();
  }

  // ========== verifyCsvFile Tests ==========

  @Test
  @DisplayName("verifyCsvFile - Should return valid results for valid inventories")
  void verifyCsvFile_WithValidInventories_ShouldReturnValid() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";

    mockRecords =
        List.of(
            InventoryIdRecord.builder().id("ref123").row(2).build(),
            InventoryIdRecord.builder().id("ref456").row(3).build());

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");

    Inventory inv1 = new Inventory();
    inv1.setId("inv123-mongodb-id");
    inv1.setReferenceId("ref123");
    inv1.setLocation(location);

    Inventory inv2 = new Inventory();
    inv2.setId("inv456-mongodb-id");
    inv2.setReferenceId("ref456");
    inv2.setLocation(location);

    mockInventories = List.of(inv1, inv2);
    mockConfigs = new ArrayList<>();

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);

    // When
    InventoryImportStatusResponseDTO result =
        inventoryImportService.verifyCsvFile(mockCsvFile, campaignId, countryName);

    // Then
    assertThat(result.getResults()).hasSize(2);
    assertThat(result.getResults().get(0).getType()).isEqualTo(ValidationType.VALID);
    assertThat(result.getResults().get(0).getId()).isEqualTo("ref123");
    assertThat(result.getResults().get(1).getType()).isEqualTo(ValidationType.VALID);
    assertThat(result.getResults().get(1).getId()).isEqualTo("ref456");
  }

  @Test
  @DisplayName("verifyCsvFile - Should return invalid for non-existent inventory IDs")
  void verifyCsvFile_WithNonExistentInventories_ShouldReturnInvalid() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";

    mockRecords = List.of(InventoryIdRecord.builder().id("invalid123").row(2).build());
    mockInventories = new ArrayList<>(); // No inventories found
    mockConfigs = new ArrayList<>();

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);

    // When
    InventoryImportStatusResponseDTO result =
        inventoryImportService.verifyCsvFile(mockCsvFile, campaignId, countryName);

    // Then
    assertThat(result.getResults()).hasSize(1);
    assertThat(result.getResults().get(0).getType()).isEqualTo(ValidationType.INVALID);
    assertThat(result.getResults().get(0).getId()).isEqualTo("invalid123");
    assertThat(result.getResults().get(0).getMessage())
        .isEqualTo("Inventory reference ID does not exist");
  }

  @Test
  @DisplayName("verifyCsvFile - Should return invalid for country mismatch")
  void verifyCsvFile_WithCountryMismatch_ShouldReturnInvalid() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";

    mockRecords = List.of(InventoryIdRecord.builder().id("ref123").row(2).build());

    Inventory.Location location = new Inventory.Location();
    location.setCountry("Canada"); // Different country

    Inventory inv = new Inventory();
    inv.setId("inv123-mongodb-id");
    inv.setReferenceId("ref123");
    inv.setLocation(location);

    mockInventories = List.of(inv);
    mockConfigs = new ArrayList<>();

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);

    // When
    InventoryImportStatusResponseDTO result =
        inventoryImportService.verifyCsvFile(mockCsvFile, campaignId, countryName);

    // Then
    assertThat(result.getResults()).hasSize(1);
    assertThat(result.getResults().get(0).getType()).isEqualTo(ValidationType.INVALID);
    assertThat(result.getResults().get(0).getId()).isEqualTo("ref123");
    assertThat(result.getResults().get(0).getMessage()).contains("does not match required country");
  }

  @Test
  @DisplayName("verifyCsvFile - Should return invalid for inventory with null location")
  void verifyCsvFile_WithNullLocation_ShouldReturnInvalid() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";

    mockRecords = List.of(InventoryIdRecord.builder().id("ref123").row(2).build());

    Inventory inv = new Inventory();
    inv.setId("inv123-mongodb-id");
    inv.setReferenceId("ref123");
    inv.setLocation(null); // Null location

    mockInventories = List.of(inv);
    mockConfigs = new ArrayList<>();

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);

    // When
    InventoryImportStatusResponseDTO result =
        inventoryImportService.verifyCsvFile(mockCsvFile, campaignId, countryName);

    // Then
    assertThat(result.getResults()).hasSize(1);
    assertThat(result.getResults().get(0).getType()).isEqualTo(ValidationType.INVALID);
    assertThat(result.getResults().get(0).getMessage())
        .contains("Inventory country 'unknown' does not match");
  }

  @Test
  @DisplayName("verifyCsvFile - Should return duplicate for IDs appearing multiple times in CSV")
  void verifyCsvFile_WithDuplicatesInCsv_ShouldReturnDuplicate() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";

    mockRecords =
        List.of(
            InventoryIdRecord.builder().id("ref123").row(2).build(),
            InventoryIdRecord.builder().id("ref123").row(3).build());

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");

    Inventory inv = new Inventory();
    inv.setId("inv123-mongodb-id");
    inv.setReferenceId("ref123");
    inv.setLocation(location);

    mockInventories = List.of(inv);
    mockConfigs = new ArrayList<>();

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);

    // When
    InventoryImportStatusResponseDTO result =
        inventoryImportService.verifyCsvFile(mockCsvFile, campaignId, countryName);

    // Then
    assertThat(result.getResults()).hasSize(2);
    assertThat(result.getResults().get(0).getType()).isEqualTo(ValidationType.DUPLICATE);
    assertThat(result.getResults().get(0).getMessage())
        .isEqualTo("Inventory reference ID appears multiple times in CSV file");
    assertThat(result.getResults().get(1).getType()).isEqualTo(ValidationType.DUPLICATE);
  }

  @Test
  @DisplayName("verifyCsvFile - Should return duplicate for already selected inventory")
  void verifyCsvFile_WithAlreadySelectedInventory_ShouldReturnDuplicate() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";

    mockRecords = List.of(InventoryIdRecord.builder().id("ref123").row(2).build());

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");

    Inventory inv = new Inventory();
    inv.setId("inv123-mongodb-id");
    inv.setReferenceId("ref123");
    inv.setLocation(location);

    // Config stores MongoDB inventory ID, not referenceId
    CampaignInventorySchedules config = new CampaignInventorySchedules();
    config.setInventoryId("inv123-mongodb-id");

    mockInventories = List.of(inv);
    mockConfigs = List.of(config); // Already selected

    // Mock the selected inventory lookup
    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);
    when(inventoryService.findAllByIds(anyList())).thenReturn(mockInventories);

    // When
    InventoryImportStatusResponseDTO result =
        inventoryImportService.verifyCsvFile(mockCsvFile, campaignId, countryName);

    // Then
    assertThat(result.getResults()).hasSize(1);
    assertThat(result.getResults().get(0).getType()).isEqualTo(ValidationType.DUPLICATE);
    assertThat(result.getResults().get(0).getMessage())
        .isEqualTo("Inventory reference ID is already selected in the campaign");
  }

  @Test
  @DisplayName(
      "verifyCsvFile - Should return invalid for duplicate invalid IDs (invalid takes precedence)")
  void verifyCsvFile_WithDuplicateInvalidIds_ShouldReturnInvalid() throws IOException {
    // Given - Invalid inventory that appears multiple times
    String campaignId = "campaign123";
    String countryName = "United States";

    mockRecords =
        List.of(
            InventoryIdRecord.builder().id("invalid123").row(2).build(),
            InventoryIdRecord.builder().id("invalid123").row(3).build());

    mockInventories = new ArrayList<>(); // No inventories found
    mockConfigs = new ArrayList<>();

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);

    // When
    InventoryImportStatusResponseDTO result =
        inventoryImportService.verifyCsvFile(mockCsvFile, campaignId, countryName);

    // Then - Should be INVALID, not DUPLICATE
    assertThat(result.getResults()).hasSize(2);
    assertThat(result.getResults().get(0).getType()).isEqualTo(ValidationType.INVALID);
    assertThat(result.getResults().get(1).getType()).isEqualTo(ValidationType.INVALID);
  }

  @Test
  @DisplayName("verifyCsvFile - Should return empty results for empty CSV")
  void verifyCsvFile_WithEmptyCsv_ShouldReturnEmptyResults() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class)))
        .thenReturn(new ArrayList<>());

    // When
    InventoryImportStatusResponseDTO result =
        inventoryImportService.verifyCsvFile(mockCsvFile, campaignId, countryName);

    // Then
    assertThat(result.getResults()).isEmpty();
    verify(inventoryService, never()).findAllByReferenceIds(anyList());
    verify(campaignInventorySchedulesService, never()).findByCampaignId(anyString());
  }

  @Test
  @DisplayName("verifyCsvFile - Should handle mixed validation results correctly")
  void verifyCsvFile_WithMixedResults_ShouldReturnCorrectTypes() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";

    mockRecords =
        List.of(
            InventoryIdRecord.builder().id("ref123").row(2).build(), // Valid
            InventoryIdRecord.builder().id("invalid123").row(3).build(), // Invalid
            InventoryIdRecord.builder().id("ref456").row(4).build(), // Valid
            InventoryIdRecord.builder().id("ref123").row(5).build()); // Duplicate

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");

    Inventory inv1 = new Inventory();
    inv1.setId("inv123-mongodb-id");
    inv1.setReferenceId("ref123");
    inv1.setLocation(location);

    Inventory inv2 = new Inventory();
    inv2.setId("inv456-mongodb-id");
    inv2.setReferenceId("ref456");
    inv2.setLocation(location);

    mockInventories = List.of(inv1, inv2); // invalid123 not found
    mockConfigs = new ArrayList<>();

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);

    // When
    InventoryImportStatusResponseDTO result =
        inventoryImportService.verifyCsvFile(mockCsvFile, campaignId, countryName);

    // Then
    assertThat(result.getResults()).hasSize(4);
    assertThat(result.getResults().get(0).getType()).isEqualTo(ValidationType.DUPLICATE);
    assertThat(result.getResults().get(1).getType()).isEqualTo(ValidationType.INVALID);
    assertThat(result.getResults().get(2).getType()).isEqualTo(ValidationType.VALID);
    assertThat(result.getResults().get(3).getType()).isEqualTo(ValidationType.DUPLICATE);
  }

  @Test
  @DisplayName("verifyCsvFile - Should handle case-insensitive country matching")
  void verifyCsvFile_WithCaseInsensitiveCountry_ShouldMatch() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "united states"; // lowercase

    mockRecords = List.of(InventoryIdRecord.builder().id("ref123").row(2).build());

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States"); // Mixed case

    Inventory inv = new Inventory();
    inv.setId("inv123-mongodb-id");
    inv.setReferenceId("ref123");
    inv.setLocation(location);

    mockInventories = List.of(inv);
    mockConfigs = new ArrayList<>();

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);

    // When
    InventoryImportStatusResponseDTO result =
        inventoryImportService.verifyCsvFile(mockCsvFile, campaignId, countryName);

    // Then
    assertThat(result.getResults()).hasSize(1);
    assertThat(result.getResults().get(0).getType()).isEqualTo(ValidationType.VALID);
  }

  @Test
  @DisplayName("verifyCsvFile - Should extract unique IDs for bulk query")
  void verifyCsvFile_WithDuplicateIds_ShouldQueryUniqueIds() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";

    mockRecords =
        List.of(
            InventoryIdRecord.builder().id("ref123").row(2).build(),
            InventoryIdRecord.builder().id("ref123").row(3).build(),
            InventoryIdRecord.builder().id("ref456").row(4).build());

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");

    Inventory inv1 = new Inventory();
    inv1.setId("inv123-mongodb-id");
    inv1.setReferenceId("ref123");
    inv1.setLocation(location);

    Inventory inv2 = new Inventory();
    inv2.setId("inv456-mongodb-id");
    inv2.setReferenceId("ref456");
    inv2.setLocation(location);

    mockInventories = List.of(inv1, inv2);
    mockConfigs = new ArrayList<>();

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);

    // When
    inventoryImportService.verifyCsvFile(mockCsvFile, campaignId, countryName);

    // Then - Should call findAllByReferenceIds with unique reference IDs only
    verify(inventoryService, times(1))
        .findAllByReferenceIds(
            argThat(
                list -> list.size() == 2 && list.contains("ref123") && list.contains("ref456")));
  }

  @Test
  @DisplayName("verifyCsvFile - Should throw IOException when CSV parsing fails")
  void verifyCsvFile_WithParsingException_ShouldThrowIOException() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class)))
        .thenThrow(new IOException("Failed to parse CSV"));

    // When & Then
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> inventoryImportService.verifyCsvFile(mockCsvFile, campaignId, countryName))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Failed to parse CSV");
  }

  @Test
  @DisplayName("verifyCsvFile - Should handle multiple already selected inventories")
  void verifyCsvFile_WithMultipleSelectedInventories_ShouldReturnAllDuplicates()
      throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";

    mockRecords =
        List.of(
            InventoryIdRecord.builder().id("ref123").row(2).build(),
            InventoryIdRecord.builder().id("ref456").row(3).build());

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");

    Inventory inv1 = new Inventory();
    inv1.setId("inv123-mongodb-id");
    inv1.setReferenceId("ref123");
    inv1.setLocation(location);

    Inventory inv2 = new Inventory();
    inv2.setId("inv456-mongodb-id");
    inv2.setReferenceId("ref456");
    inv2.setLocation(location);

    // Config stores MongoDB inventory IDs, not referenceIds
    CampaignInventorySchedules config1 = new CampaignInventorySchedules();
    config1.setInventoryId("inv123-mongodb-id");

    CampaignInventorySchedules config2 = new CampaignInventorySchedules();
    config2.setInventoryId("inv456-mongodb-id");

    mockInventories = List.of(inv1, inv2);
    mockConfigs = List.of(config1, config2);

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);
    when(inventoryService.findAllByIds(anyList())).thenReturn(mockInventories);

    // When
    InventoryImportStatusResponseDTO result =
        inventoryImportService.verifyCsvFile(mockCsvFile, campaignId, countryName);

    // Then
    assertThat(result.getResults()).hasSize(2);
    assertThat(result.getResults().get(0).getType()).isEqualTo(ValidationType.DUPLICATE);
    assertThat(result.getResults().get(1).getType()).isEqualTo(ValidationType.DUPLICATE);
  }

  @Test
  @DisplayName("verifyCsvFile - Should preserve row numbers in results")
  void verifyCsvFile_ShouldPreserveRowNumbers() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";

    mockRecords =
        List.of(
            InventoryIdRecord.builder().id("ref123").row(2).build(),
            InventoryIdRecord.builder().id("ref456").row(5).build(),
            InventoryIdRecord.builder().id("invalid123").row(8).build());

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");

    Inventory inv1 = new Inventory();
    inv1.setId("inv123-mongodb-id");
    inv1.setReferenceId("ref123");
    inv1.setLocation(location);

    Inventory inv2 = new Inventory();
    inv2.setId("inv456-mongodb-id");
    inv2.setReferenceId("ref456");
    inv2.setLocation(location);

    mockInventories = List.of(inv1, inv2);
    mockConfigs = new ArrayList<>();

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);

    // When
    InventoryImportStatusResponseDTO result =
        inventoryImportService.verifyCsvFile(mockCsvFile, campaignId, countryName);

    // Then
    assertThat(result.getResults()).hasSize(3);
    assertThat(result.getResults().get(0).getRow()).isEqualTo(2);
    assertThat(result.getResults().get(1).getRow()).isEqualTo(5);
    assertThat(result.getResults().get(2).getRow()).isEqualTo(8);
  }

  // ========== uploadCsvFile Tests ==========

  @Test
  @DisplayName("uploadCsvFile - Should successfully upload and save valid CSV file")
  void uploadCsvFile_WithValidData_ShouldSaveSuccessfully() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";
    String companyId = "company123";

    mockRecords =
        List.of(
            InventoryIdRecord.builder().id("ref123").row(2).build(),
            InventoryIdRecord.builder().id("ref456").row(3).build());

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");

    Inventory inv1 = new Inventory();
    inv1.setId("inv123-mongodb-id");
    inv1.setReferenceId("ref123");
    inv1.setLocation(location);

    Inventory inv2 = new Inventory();
    inv2.setId("inv456-mongodb-id");
    inv2.setReferenceId("ref456");
    inv2.setLocation(location);

    mockInventories = List.of(inv1, inv2);
    mockConfigs = new ArrayList<>();

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);
    when(selectInventoryImportsRepository.save(any(SelectInventoryImports.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    inventoryImportService.uploadCsvFile(mockCsvFile, campaignId, countryName, companyId);

    // Then
    verify(campaignInventorySchedulesService, times(1))
        .bulkSelectInventoriesByIds(
            eq(campaignId),
            argThat(
                list ->
                    list.size() == 2
                        && list.contains("inv123-mongodb-id")
                        && list.contains("inv456-mongodb-id")));
    verify(selectInventoryImportsRepository, times(1))
        .save(
            argThat(
                importRecord ->
                    importRecord.getCampaignId().equals(campaignId)
                        && importRecord.getCompanyId().equals(companyId)
                        && importRecord.getCountryName().equals(countryName)
                        && importRecord.getFileName().equals("test.csv")
                        && importRecord.getInventoryRefIds().size() == 2));
    verify(campaignActivityService, times(1))
        .logActivity(
            eq(campaignId),
            eq(CampaignActivityService.OperationType.ADDED),
            eq("csv_upload_selected_count"),
            eq(0),
            eq("csv_upload_filename"),
            eq("test.csv"));
  }

  @Test
  @DisplayName("uploadCsvFile - Should throw exception for empty CSV file")
  void uploadCsvFile_WithEmptyCsv_ShouldThrowException() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";
    String companyId = "company123";

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class)))
        .thenReturn(new ArrayList<>());

    // When & Then
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                inventoryImportService.uploadCsvFile(
                    mockCsvFile, campaignId, countryName, companyId))
        .isInstanceOf(CsvUploadException.class)
        .hasMessageContaining("Invalid file data please verify");
    verify(campaignInventorySchedulesService, never()).bulkSelectInventoriesByIds(any(), anyList());
    verify(selectInventoryImportsRepository, never()).save(any());
  }

  @Test
  @DisplayName("uploadCsvFile - Should throw exception for invalid inventory")
  void uploadCsvFile_WithInvalidInventory_ShouldThrowException() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";
    String companyId = "company123";

    mockRecords = List.of(InventoryIdRecord.builder().id("invalid123").row(2).build());
    mockInventories = new ArrayList<>();
    mockConfigs = new ArrayList<>();

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);

    // When & Then
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                inventoryImportService.uploadCsvFile(
                    mockCsvFile, campaignId, countryName, companyId))
        .isInstanceOf(CsvUploadException.class)
        .hasMessageContaining("Invalid file data please verify");
    verify(campaignInventorySchedulesService, never()).bulkSelectInventoriesByIds(any(), anyList());
    verify(selectInventoryImportsRepository, never()).save(any());
  }

  @Test
  @DisplayName("uploadCsvFile - Should throw exception for country mismatch")
  void uploadCsvFile_WithCountryMismatch_ShouldThrowException() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";
    String companyId = "company123";

    mockRecords = List.of(InventoryIdRecord.builder().id("ref123").row(2).build());

    Inventory.Location location = new Inventory.Location();
    location.setCountry("Canada");

    Inventory inv = new Inventory();
    inv.setId("inv123-mongodb-id");
    inv.setReferenceId("ref123");
    inv.setLocation(location);

    mockInventories = List.of(inv);
    mockConfigs = new ArrayList<>();

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);

    // When & Then
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                inventoryImportService.uploadCsvFile(
                    mockCsvFile, campaignId, countryName, companyId))
        .isInstanceOf(CsvUploadException.class)
        .hasMessageContaining("Invalid file data please verify");
    verify(campaignInventorySchedulesService, never()).bulkSelectInventoriesByIds(any(), anyList());
    verify(selectInventoryImportsRepository, never()).save(any());
  }

  @Test
  @DisplayName("uploadCsvFile - Should process duplicate in CSV without throwing exception")
  void uploadCsvFile_WithDuplicatesInCsv_ShouldProcessSuccessfully() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";
    String companyId = "company123";

    mockRecords =
        List.of(
            InventoryIdRecord.builder().id("ref123").row(2).build(),
            InventoryIdRecord.builder().id("ref123").row(3).build());

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");

    Inventory inv = new Inventory();
    inv.setId("inv123-mongodb-id");
    inv.setReferenceId("ref123");
    inv.setLocation(location);

    mockInventories = List.of(inv);
    mockConfigs = new ArrayList<>();

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);
    when(campaignInventorySchedulesService.bulkSelectInventoriesByIds(eq(campaignId), anyList()))
        .thenReturn(1);
    when(selectInventoryImportsRepository.save(any(SelectInventoryImports.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    inventoryImportService.uploadCsvFile(mockCsvFile, campaignId, countryName, companyId);

    // Then - duplicates are skipped during validation but unique IDs are still processed
    verify(campaignInventorySchedulesService, times(1))
        .bulkSelectInventoriesByIds(
            eq(campaignId),
            argThat(list -> list.size() == 1 && list.contains("inv123-mongodb-id")));
    verify(selectInventoryImportsRepository, times(1))
        .save(
            argThat(
                importData ->
                    importData.getInventoryRefIds().size() == 1
                        && importData.getInventoryRefIds().contains("ref123")));
    verify(campaignActivityService, times(1))
        .logActivity(
            eq(campaignId),
            eq(CampaignActivityService.OperationType.ADDED),
            eq("csv_upload_selected_count"),
            eq(1),
            eq("csv_upload_filename"),
            eq("test.csv"));
  }

  @Test
  @DisplayName(
      "uploadCsvFile - Should process already selected inventory without throwing exception")
  void uploadCsvFile_WithAlreadySelectedInventory_ShouldProcessSuccessfully() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";
    String companyId = "company123";

    mockRecords = List.of(InventoryIdRecord.builder().id("ref123").row(2).build());

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");

    Inventory inv = new Inventory();
    inv.setId("inv123-mongodb-id");
    inv.setReferenceId("ref123");
    inv.setLocation(location);

    CampaignInventorySchedules config = new CampaignInventorySchedules();
    config.setInventoryId("inv123-mongodb-id");

    mockInventories = List.of(inv);
    mockConfigs = List.of(config);

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);
    when(inventoryService.findAllByIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.bulkSelectInventoriesByIds(eq(campaignId), anyList()))
        .thenReturn(1);
    when(selectInventoryImportsRepository.save(any(SelectInventoryImports.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    inventoryImportService.uploadCsvFile(mockCsvFile, campaignId, countryName, companyId);

    // Then - already selected inventory is skipped during validation but still processed
    verify(campaignInventorySchedulesService, times(1))
        .bulkSelectInventoriesByIds(
            eq(campaignId),
            argThat(list -> list.size() == 1 && list.contains("inv123-mongodb-id")));
    verify(selectInventoryImportsRepository, times(1))
        .save(
            argThat(
                importData ->
                    importData.getInventoryRefIds().size() == 1
                        && importData.getInventoryRefIds().contains("ref123")));
    verify(campaignActivityService, times(1))
        .logActivity(
            eq(campaignId),
            eq(CampaignActivityService.OperationType.ADDED),
            eq("csv_upload_selected_count"),
            eq(1),
            eq("csv_upload_filename"),
            eq("test.csv"));
  }

  @Test
  @DisplayName("uploadCsvFile - Should skip duplicates in CSV and continue with other valid items")
  void uploadCsvFile_WithDuplicatesInCsv_ShouldSkipAndContinue() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";
    String companyId = "company123";

    // ref123 appears twice (duplicate), ref456 is unique and valid
    mockRecords =
        List.of(
            InventoryIdRecord.builder().id("ref123").row(2).build(),
            InventoryIdRecord.builder().id("ref123").row(3).build(),
            InventoryIdRecord.builder().id("ref456").row(4).build());

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");

    Inventory inv1 = new Inventory();
    inv1.setId("inv123-mongodb-id");
    inv1.setReferenceId("ref123");
    inv1.setLocation(location);

    Inventory inv2 = new Inventory();
    inv2.setId("inv456-mongodb-id");
    inv2.setReferenceId("ref456");
    inv2.setLocation(location);

    mockInventories = List.of(inv1, inv2);
    mockConfigs = new ArrayList<>();

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);
    when(campaignInventorySchedulesService.bulkSelectInventoriesByIds(eq(campaignId), anyList()))
        .thenReturn(2);
    when(selectInventoryImportsRepository.save(any(SelectInventoryImports.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    inventoryImportService.uploadCsvFile(mockCsvFile, campaignId, countryName, companyId);

    // Then - duplicates are skipped during validation but unique IDs are still processed
    verify(campaignInventorySchedulesService, times(1))
        .bulkSelectInventoriesByIds(
            eq(campaignId),
            argThat(
                list ->
                    list.size() == 2
                        && list.contains("inv123-mongodb-id")
                        && list.contains("inv456-mongodb-id")));
    verify(selectInventoryImportsRepository, times(1))
        .save(
            argThat(
                importData ->
                    importData.getInventoryRefIds().size() == 2
                        && importData.getInventoryRefIds().contains("ref123")
                        && importData.getInventoryRefIds().contains("ref456")));
    verify(campaignActivityService, times(1))
        .logActivity(
            eq(campaignId),
            eq(CampaignActivityService.OperationType.ADDED),
            eq("csv_upload_selected_count"),
            eq(2),
            eq("csv_upload_filename"),
            eq("test.csv"));
  }

  @Test
  @DisplayName(
      "uploadCsvFile - Should skip already selected inventory and continue with other valid items")
  void uploadCsvFile_WithAlreadySelectedInventory_ShouldSkipAndContinue() throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";
    String companyId = "company123";

    // ref123 is already selected, ref456 is new and valid
    mockRecords =
        List.of(
            InventoryIdRecord.builder().id("ref123").row(2).build(),
            InventoryIdRecord.builder().id("ref456").row(3).build());

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");

    Inventory inv1 = new Inventory();
    inv1.setId("inv123-mongodb-id");
    inv1.setReferenceId("ref123");
    inv1.setLocation(location);

    Inventory inv2 = new Inventory();
    inv2.setId("inv456-mongodb-id");
    inv2.setReferenceId("ref456");
    inv2.setLocation(location);

    CampaignInventorySchedules config = new CampaignInventorySchedules();
    config.setInventoryId("inv123-mongodb-id");

    mockInventories = List.of(inv1, inv2);
    mockConfigs = List.of(config);

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inv1));
    when(campaignInventorySchedulesService.bulkSelectInventoriesByIds(eq(campaignId), anyList()))
        .thenReturn(2);
    when(selectInventoryImportsRepository.save(any(SelectInventoryImports.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    inventoryImportService.uploadCsvFile(mockCsvFile, campaignId, countryName, companyId);

    // Then - already selected items are skipped during validation but unique IDs are still
    // processed
    verify(campaignInventorySchedulesService, times(1))
        .bulkSelectInventoriesByIds(
            eq(campaignId),
            argThat(
                list ->
                    list.size() == 2
                        && list.contains("inv123-mongodb-id")
                        && list.contains("inv456-mongodb-id")));
    verify(selectInventoryImportsRepository, times(1))
        .save(
            argThat(
                importData ->
                    importData.getInventoryRefIds().size() == 2
                        && importData.getInventoryRefIds().contains("ref123")
                        && importData.getInventoryRefIds().contains("ref456")));
    verify(campaignActivityService, times(1))
        .logActivity(
            eq(campaignId),
            eq(CampaignActivityService.OperationType.ADDED),
            eq("csv_upload_selected_count"),
            eq(2),
            eq("csv_upload_filename"),
            eq("test.csv"));
  }

  @Test
  @DisplayName(
      "uploadCsvFile - Should skip both duplicates and already selected, process only valid new items")
  void uploadCsvFile_WithDuplicatesAndAlreadySelected_ShouldSkipBothAndContinue()
      throws IOException {
    // Given
    String campaignId = "campaign123";
    String countryName = "United States";
    String companyId = "company123";

    // ref123 is duplicate in CSV, ref456 is already selected, ref789 is new and valid
    mockRecords =
        List.of(
            InventoryIdRecord.builder().id("ref123").row(2).build(),
            InventoryIdRecord.builder().id("ref123").row(3).build(),
            InventoryIdRecord.builder().id("ref456").row(4).build(),
            InventoryIdRecord.builder().id("ref789").row(5).build());

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");

    Inventory inv1 = new Inventory();
    inv1.setId("inv123-mongodb-id");
    inv1.setReferenceId("ref123");
    inv1.setLocation(location);

    Inventory inv2 = new Inventory();
    inv2.setId("inv456-mongodb-id");
    inv2.setReferenceId("ref456");
    inv2.setLocation(location);

    Inventory inv3 = new Inventory();
    inv3.setId("inv789-mongodb-id");
    inv3.setReferenceId("ref789");
    inv3.setLocation(location);

    CampaignInventorySchedules config = new CampaignInventorySchedules();
    config.setInventoryId("inv456-mongodb-id");

    mockInventories = List.of(inv1, inv2, inv3);
    mockConfigs = List.of(config);

    when(csvParsingService.parseInventoryIdCsvFile(any(InputStream.class))).thenReturn(mockRecords);
    when(inventoryService.findAllByReferenceIds(anyList())).thenReturn(mockInventories);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(mockConfigs);
    when(inventoryService.findAllByIds(anyList())).thenReturn(List.of(inv2));
    when(campaignInventorySchedulesService.bulkSelectInventoriesByIds(eq(campaignId), anyList()))
        .thenReturn(3);
    when(selectInventoryImportsRepository.save(any(SelectInventoryImports.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    inventoryImportService.uploadCsvFile(mockCsvFile, campaignId, countryName, companyId);

    // Then - duplicates and already selected are skipped during validation but unique IDs are still
    // processed
    verify(campaignInventorySchedulesService, times(1))
        .bulkSelectInventoriesByIds(
            eq(campaignId),
            argThat(
                list ->
                    list.size() == 3
                        && list.contains("inv123-mongodb-id")
                        && list.contains("inv456-mongodb-id")
                        && list.contains("inv789-mongodb-id")));
    verify(selectInventoryImportsRepository, times(1))
        .save(
            argThat(
                importData ->
                    importData.getInventoryRefIds().size() == 3
                        && importData.getInventoryRefIds().contains("ref123")
                        && importData.getInventoryRefIds().contains("ref456")
                        && importData.getInventoryRefIds().contains("ref789")));
    verify(campaignActivityService, times(1))
        .logActivity(
            eq(campaignId),
            eq(CampaignActivityService.OperationType.ADDED),
            eq("csv_upload_selected_count"),
            eq(3),
            eq("csv_upload_filename"),
            eq("test.csv"));
  }

  // ========== useInventoryImport Tests ==========

  @Test
  @DisplayName("useInventoryImport - Should successfully use inventory import")
  void useInventoryImport_WithValidImport_ShouldSelectInventories() {
    // Given
    String importId = "import123";
    String campaignId = "campaign123";
    String countryName = "United States";
    List<String> referenceIds = List.of("ref123", "ref456");

    SelectInventoryImports importData = new SelectInventoryImports();
    importData.setId(importId);
    importData.setCampaignId(campaignId);
    importData.setCountryName(countryName);
    importData.setInventoryRefIds(referenceIds);

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");

    Inventory inv1 = new Inventory();
    inv1.setId("inv123");
    inv1.setReferenceId("ref123");
    inv1.setLocation(location);

    Inventory inv2 = new Inventory();
    inv2.setId("inv456");
    inv2.setReferenceId("ref456");
    inv2.setLocation(location);

    when(selectInventoryImportsRepository.findById(importId))
        .thenReturn(java.util.Optional.of(importData));
    when(inventoryService.findAllByReferenceIds(referenceIds)).thenReturn(List.of(inv1, inv2));
    when(campaignInventorySchedulesService.bulkSelectInventoriesByIds(
            campaignId, List.of("inv123", "inv456")))
        .thenReturn(2);

    // When
    inventoryImportService.useInventoryImport(campaignId, importId);

    // Then
    verify(selectInventoryImportsRepository).findById(importId);
    verify(inventoryService).findAllByReferenceIds(referenceIds);
    verify(campaignInventorySchedulesService)
        .bulkSelectInventoriesByIds(campaignId, List.of("inv123", "inv456"));
    verify(campaignActivityService, times(1))
        .logActivity(
            eq(campaignId),
            eq(CampaignActivityService.OperationType.ADDED),
            eq("inventory_import_selected_count"),
            eq(2),
            eq("inventory_import_filename"),
            eq(null));
  }

  @Test
  @DisplayName("useInventoryImport - Should throw exception when import not found")
  void useInventoryImport_WithNonExistentImport_ShouldThrowException() {
    // Given
    String importId = "nonExistent";

    when(selectInventoryImportsRepository.findById(importId))
        .thenReturn(java.util.Optional.empty());

    // When & Then
    assertThatThrownBy(() -> inventoryImportService.useInventoryImport("campaign123", importId))
        .isInstanceOf(InventoryImportException.class)
        .hasMessageContaining("Inventory import not found with ID: " + importId);
    verify(selectInventoryImportsRepository).findById(importId);
    verify(campaignInventorySchedulesService, never()).bulkSelectInventoriesByIds(any(), anyList());
  }

  @Test
  @DisplayName("useInventoryImport - Should throw exception when import has no reference IDs")
  void useInventoryImport_WithEmptyReferenceIds_ShouldThrowException() {
    // Given
    String importId = "import123";
    SelectInventoryImports importData = new SelectInventoryImports();
    importData.setId(importId);
    importData.setInventoryRefIds(new ArrayList<>());

    when(selectInventoryImportsRepository.findById(importId))
        .thenReturn(java.util.Optional.of(importData));

    // When & Then
    assertThatThrownBy(() -> inventoryImportService.useInventoryImport("campaign123", importId))
        .isInstanceOf(InventoryImportException.class)
        .hasMessageContaining("No inventory reference IDs found in import");
    verify(selectInventoryImportsRepository).findById(importId);
    verify(campaignInventorySchedulesService, never()).bulkSelectInventoriesByIds(any(), anyList());
  }

  @Test
  @DisplayName("useInventoryImport - Should throw exception when inventory not found")
  void useInventoryImport_WithNonExistentInventory_ShouldThrowException() {
    // Given
    String importId = "import123";
    String campaignId = "campaign123";
    String countryName = "United States";
    List<String> referenceIds = List.of("ref123");

    SelectInventoryImports importData = new SelectInventoryImports();
    importData.setId(importId);
    importData.setCampaignId(campaignId);
    importData.setCountryName(countryName);
    importData.setInventoryRefIds(referenceIds);

    when(selectInventoryImportsRepository.findById(importId))
        .thenReturn(java.util.Optional.of(importData));
    when(inventoryService.findAllByReferenceIds(referenceIds)).thenReturn(new ArrayList<>());

    // When & Then
    assertThatThrownBy(() -> inventoryImportService.useInventoryImport(campaignId, importId))
        .isInstanceOf(InventoryImportException.class)
        .hasMessageContaining("Inventory reference ID does not exist");
    verify(selectInventoryImportsRepository).findById(importId);
    verify(inventoryService).findAllByReferenceIds(referenceIds);
    verify(campaignInventorySchedulesService, never()).bulkSelectInventoriesByIds(any(), anyList());
  }

  @Test
  @DisplayName(
      "useInventoryImport - Should process already selected inventory without throwing exception")
  void useInventoryImport_WithAlreadySelectedInventory_ShouldProcessSuccessfully() {
    // Given
    String importId = "import123";
    String campaignId = "campaign123";
    String countryName = "United States";
    List<String> referenceIds = List.of("ref123");

    SelectInventoryImports importData = new SelectInventoryImports();
    importData.setId(importId);
    importData.setCampaignId(campaignId);
    importData.setCountryName(countryName);
    importData.setInventoryRefIds(referenceIds);
    importData.setFileName("test_import.csv");

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");

    Inventory inv = new Inventory();
    inv.setId("inv123");
    inv.setReferenceId("ref123");
    inv.setLocation(location);

    CampaignInventorySchedules schedule = new CampaignInventorySchedules();
    schedule.setInventoryId("inv123");
    List<CampaignInventorySchedules> schedules = new ArrayList<>();
    schedules.add(schedule);

    when(selectInventoryImportsRepository.findById(importId))
        .thenReturn(java.util.Optional.of(importData));
    when(inventoryService.findAllByReferenceIds(referenceIds)).thenReturn(List.of(inv));
    when(campaignInventorySchedulesService.bulkSelectInventoriesByIds(
            campaignId, List.of("inv123")))
        .thenReturn(1);

    // When
    inventoryImportService.useInventoryImport(campaignId, importId);

    // Then - already selected inventory is logged but still processed
    verify(selectInventoryImportsRepository).findById(importId);
    verify(inventoryService).findAllByReferenceIds(referenceIds);
    verify(campaignInventorySchedulesService)
        .bulkSelectInventoriesByIds(
            eq(campaignId), argThat(list -> list.size() == 1 && list.contains("inv123")));
    verify(campaignActivityService, times(1))
        .logActivity(
            eq(campaignId),
            eq(CampaignActivityService.OperationType.ADDED),
            eq("inventory_import_selected_count"),
            eq(1),
            eq("inventory_import_filename"),
            eq("test_import.csv"));
  }

  @Test
  @DisplayName("useInventoryImport - Should throw exception when country mismatch")
  void useInventoryImport_WithCountryMismatch_ShouldThrowException() {
    // Given
    String importId = "import123";
    String campaignId = "campaign123";
    String countryName = "United States";
    List<String> referenceIds = List.of("ref123");

    SelectInventoryImports importData = new SelectInventoryImports();
    importData.setId(importId);
    importData.setCampaignId(campaignId);
    importData.setCountryName(countryName);
    importData.setInventoryRefIds(referenceIds);

    Inventory.Location location = new Inventory.Location();
    location.setCountry("Canada"); // Different country

    Inventory inv = new Inventory();
    inv.setId("inv123");
    inv.setReferenceId("ref123");
    inv.setLocation(location);

    when(selectInventoryImportsRepository.findById(importId))
        .thenReturn(java.util.Optional.of(importData));
    when(inventoryService.findAllByReferenceIds(referenceIds)).thenReturn(List.of(inv));

    // When & Then
    assertThatThrownBy(() -> inventoryImportService.useInventoryImport(campaignId, importId))
        .isInstanceOf(InventoryImportException.class)
        .hasMessageContaining("Inventory country");
    verify(selectInventoryImportsRepository).findById(importId);
    verify(inventoryService).findAllByReferenceIds(referenceIds);
    verify(campaignInventorySchedulesService, never()).bulkSelectInventoriesByIds(any(), anyList());
  }

  // ========== generateInventoryImportCsv Tests ==========

  @Test
  @DisplayName("generateInventoryImportCsv - Should generate CSV with correct filename and content")
  void generateInventoryImportCsv_WithValidImport_ShouldReturnCsvFile() {
    // Given
    String importId = "import123";
    String fileName = "test_inventory.csv";
    List<String> referenceIds = List.of("ref123", "ref456");

    SelectInventoryImports importData = new SelectInventoryImports();
    importData.setId(importId);
    importData.setFileName(fileName);
    importData.setInventoryRefIds(referenceIds);

    when(selectInventoryImportsRepository.findById(importId))
        .thenReturn(java.util.Optional.of(importData));

    // When
    InventoryImportService.CsvFileResult result =
        inventoryImportService.generateInventoryImportCsv(importId);

    // Then
    assertThat(result.fileName()).isEqualTo(fileName);
    assertThat(result.content()).isNotNull();
    String csvContent = new String(result.content());
    assertThat(csvContent).contains("inventory_id");
    assertThat(csvContent).contains("ref123");
    assertThat(csvContent).contains("ref456");
    verify(selectInventoryImportsRepository).findById(importId);
  }

  @Test
  @DisplayName("generateInventoryImportCsv - Should throw exception when import not found")
  void generateInventoryImportCsv_WithNonExistentImport_ShouldThrowException() {
    // Given
    String importId = "nonExistent";

    when(selectInventoryImportsRepository.findById(importId))
        .thenReturn(java.util.Optional.empty());

    // When & Then
    assertThatThrownBy(() -> inventoryImportService.generateInventoryImportCsv(importId))
        .isInstanceOf(InventoryImportException.class)
        .hasMessageContaining("Inventory import not found with ID: " + importId);
    verify(selectInventoryImportsRepository).findById(importId);
  }

  @Test
  @DisplayName("generateInventoryImportCsv - Should return empty CSV when no reference IDs")
  void generateInventoryImportCsv_WithEmptyReferenceIds_ShouldReturnEmptyCsv() {
    // Given
    String importId = "import123";
    String fileName = "test_inventory.csv";

    SelectInventoryImports importData = new SelectInventoryImports();
    importData.setId(importId);
    importData.setFileName(fileName);
    importData.setInventoryRefIds(new ArrayList<>());

    when(selectInventoryImportsRepository.findById(importId))
        .thenReturn(java.util.Optional.of(importData));

    // When
    InventoryImportService.CsvFileResult result =
        inventoryImportService.generateInventoryImportCsv(importId);

    // Then
    assertThat(result.fileName()).isEqualTo(fileName);
    assertThat(result.content()).isNotNull();
    String csvContent = new String(result.content());
    assertThat(csvContent).contains("inventory_id");
    assertThat(csvContent.split("\n").length).isEqualTo(1); // Header + empty line
    verify(selectInventoryImportsRepository).findById(importId);
  }

  @Test
  @DisplayName("generateInventoryImportCsv - Should use default filename when fileName is null")
  void generateInventoryImportCsv_WithNullFileName_ShouldUseDefault() {
    // Given
    String importId = "import123";
    List<String> referenceIds = List.of("ref123");

    SelectInventoryImports importData = new SelectInventoryImports();
    importData.setId(importId);
    importData.setFileName(null);
    importData.setInventoryRefIds(referenceIds);

    when(selectInventoryImportsRepository.findById(importId))
        .thenReturn(java.util.Optional.of(importData));

    // When
    InventoryImportService.CsvFileResult result =
        inventoryImportService.generateInventoryImportCsv(importId);

    // Then
    assertThat(result.fileName()).isEqualTo("inventory_import_" + importId + ".csv");
    verify(selectInventoryImportsRepository).findById(importId);
  }

  // ========== deleteInventoryImport Tests ==========

  @Test
  @DisplayName("deleteInventoryImport - Should successfully delete inventory import")
  void deleteInventoryImport_WithValidImport_ShouldDelete() {
    // Given
    String importId = "import123";
    String campaignId = "campaign123";
    String fileName = "test.csv";
    String countryName = "United States";
    List<String> referenceIds = List.of("ref123");

    SelectInventoryImports importData = new SelectInventoryImports();
    importData.setId(importId);
    importData.setCampaignId(campaignId);
    importData.setFileName(fileName);
    importData.setCountryName(countryName);
    importData.setInventoryRefIds(referenceIds);

    when(selectInventoryImportsRepository.findById(importId)).thenReturn(Optional.of(importData));
    doNothing().when(selectInventoryImportsRepository).deleteById(importId);

    // When
    inventoryImportService.deleteInventoryImport(importId);

    // Then
    verify(selectInventoryImportsRepository).findById(importId);
    verify(selectInventoryImportsRepository).deleteById(importId);
    verify(campaignActivityService, times(1))
        .logActivity(
            eq(campaignId),
            eq(CampaignActivityService.OperationType.REMOVED),
            eq(INVENTORY_IMPORT_DELETED_FILENAME.key()),
            eq(fileName));
  }

  @Test
  @DisplayName("deleteInventoryImport - Should throw exception when import not found")
  void deleteInventoryImport_WithNonExistentImport_ShouldThrowException() {
    // Given
    String importId = "nonExistent";

    when(selectInventoryImportsRepository.findById(importId)).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> inventoryImportService.deleteInventoryImport(importId))
        .isInstanceOf(InventoryImportException.class)
        .hasMessageContaining("Inventory import not found with ID: " + importId);
    verify(selectInventoryImportsRepository).findById(importId);
    verify(selectInventoryImportsRepository, never()).deleteById(anyString());
  }

  // ========== getInventoriesByImportId Tests ==========

  @Test
  @DisplayName("getInventoriesByImportId - Should return paginated inventories successfully")
  void getInventoriesByImportId_WithValidImportId_ShouldReturnPaginatedInventories() {
    // Given
    String importId = "import123";
    Pageable pageable = PageRequest.of(0, 10, Sort.by("name").ascending());

    List<String> referenceIds = List.of("ref123", "ref456", "ref789");

    SelectInventoryImports importRecord =
        buildSelectInventoryImports(
            "campaign123", "company123", "test.csv", referenceIds, "United States");
    importRecord.setId(importId);

    Inventory.Location location1 = new Inventory.Location();
    location1.setCountry("United States");
    Inventory inv1 = new Inventory();
    inv1.setId("inv123");
    inv1.setReferenceId("ref123");
    inv1.setName("Inventory 1");
    inv1.setLocation(location1);

    Inventory.Location location2 = new Inventory.Location();
    location2.setCountry("United States");
    Inventory inv2 = new Inventory();
    inv2.setId("inv456");
    inv2.setReferenceId("ref456");
    inv2.setName("Inventory 2");
    inv2.setLocation(location2);

    List<Inventory> inventories = List.of(inv1, inv2);
    Page<Inventory> inventoryPage = new PageImpl<>(inventories, pageable, 2);

    when(selectInventoryImportsRepository.findById(importId)).thenReturn(Optional.of(importRecord));
    when(inventoryRepository.findByReferenceIdIn(referenceIds, pageable)).thenReturn(inventoryPage);

    // When
    Page<ImportInventoryDetailResponseDTO> result =
        inventoryImportService.getInventoriesByImportId(importId, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getTotalPages()).isEqualTo(1);
    assertThat(result.getNumber()).isEqualTo(0);
    assertThat(result.getSize()).isEqualTo(10);

    assertThat(result.getContent().get(0).getReferenceId()).isEqualTo("ref123");
    assertThat(result.getContent().get(0).getId()).isEqualTo("inv123");
    assertThat(result.getContent().get(1).getReferenceId()).isEqualTo("ref456");
    assertThat(result.getContent().get(1).getId()).isEqualTo("inv456");

    verify(selectInventoryImportsRepository, times(1)).findById(importId);
    verify(inventoryRepository, times(1)).findByReferenceIdIn(referenceIds, pageable);
  }

  @Test
  @DisplayName("getInventoriesByImportId - Should throw exception when import record not found")
  void getInventoriesByImportId_WithNonExistentImportId_ShouldThrowException() {
    // Given
    String importId = "nonExistentImport";
    Pageable pageable = PageRequest.of(0, 10);

    when(selectInventoryImportsRepository.findById(importId)).thenReturn(Optional.empty());

    // When & Then
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> inventoryImportService.getInventoriesByImportId(importId, pageable))
        .isInstanceOf(CsvUploadException.class)
        .satisfies(
            exception -> {
              CsvUploadException csvException = (CsvUploadException) exception;
              assertThat(csvException.getErrorCode())
                  .isEqualTo(ErrorCode.INVENTORY_IMPORTS_NOT_FOUND);
              assertThat(csvException.getMessage()).contains("Import record not found");
            });

    verify(selectInventoryImportsRepository, times(1)).findById(importId);
    verify(inventoryRepository, never()).findByReferenceIdIn(anyList(), any(Pageable.class));
  }

  @Test
  @DisplayName("getInventoriesByImportId - Should return empty page when reference IDs are null")
  void getInventoriesByImportId_WithNullReferenceIds_ShouldReturnEmptyPage() {
    // Given
    String importId = "import123";
    Pageable pageable = PageRequest.of(0, 10);

    SelectInventoryImports importRecord =
        buildSelectInventoryImports("campaign123", "company123", "test.csv", null, "United States");
    importRecord.setId(importId);

    when(selectInventoryImportsRepository.findById(importId)).thenReturn(Optional.of(importRecord));

    // When
    Page<ImportInventoryDetailResponseDTO> result =
        inventoryImportService.getInventoriesByImportId(importId, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0);
    assertThat(result.getTotalPages()).isEqualTo(0);

    verify(selectInventoryImportsRepository, times(1)).findById(importId);
    verify(inventoryRepository, never()).findByReferenceIdIn(anyList(), any(Pageable.class));
  }

  @Test
  @DisplayName("getInventoriesByImportId - Should return empty page when reference IDs are empty")
  void getInventoriesByImportId_WithEmptyReferenceIds_ShouldReturnEmptyPage() {
    // Given
    String importId = "import123";
    Pageable pageable = PageRequest.of(0, 10);

    SelectInventoryImports importRecord =
        buildSelectInventoryImports(
            "campaign123", "company123", "test.csv", new ArrayList<>(), "United States");
    importRecord.setId(importId);

    when(selectInventoryImportsRepository.findById(importId)).thenReturn(Optional.of(importRecord));

    // When
    Page<ImportInventoryDetailResponseDTO> result =
        inventoryImportService.getInventoriesByImportId(importId, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0);
    assertThat(result.getTotalPages()).isEqualTo(0);

    verify(selectInventoryImportsRepository, times(1)).findById(importId);
    verify(inventoryRepository, never()).findByReferenceIdIn(anyList(), any(Pageable.class));
  }

  @Test
  @DisplayName("getInventoriesByImportId - Should handle pagination correctly")
  void getInventoriesByImportId_WithPagination_ShouldReturnCorrectPage() {
    // Given
    String importId = "import123";
    Pageable pageable = PageRequest.of(1, 2, Sort.by("name").descending());

    List<String> referenceIds = List.of("ref123", "ref456", "ref789", "ref101", "ref112");

    SelectInventoryImports importRecord =
        buildSelectInventoryImports(
            "campaign123", "company123", "test.csv", referenceIds, "United States");
    importRecord.setId(importId);

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");
    Inventory inv1 = new Inventory();
    inv1.setId("inv789");
    inv1.setReferenceId("ref789");
    inv1.setName("Inventory 3");
    inv1.setLocation(location);

    Inventory inv2 = new Inventory();
    inv2.setId("inv101");
    inv2.setReferenceId("ref101");
    inv2.setName("Inventory 4");
    inv2.setLocation(location);

    List<Inventory> inventories = List.of(inv1, inv2);
    Page<Inventory> inventoryPage = new PageImpl<>(inventories, pageable, 5);

    when(selectInventoryImportsRepository.findById(importId)).thenReturn(Optional.of(importRecord));
    when(inventoryRepository.findByReferenceIdIn(referenceIds, pageable)).thenReturn(inventoryPage);

    // When
    Page<ImportInventoryDetailResponseDTO> result =
        inventoryImportService.getInventoriesByImportId(importId, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getTotalElements()).isEqualTo(5);
    assertThat(result.getTotalPages()).isEqualTo(3);
    assertThat(result.getNumber()).isEqualTo(1);
    assertThat(result.getSize()).isEqualTo(2);

    verify(selectInventoryImportsRepository, times(1)).findById(importId);
    verify(inventoryRepository, times(1)).findByReferenceIdIn(referenceIds, pageable);
  }

  @Test
  @DisplayName("getInventoriesByImportId - Should return empty page when no inventories found")
  void getInventoriesByImportId_WithNoMatchingInventories_ShouldReturnEmptyPage() {
    // Given
    String importId = "import123";
    Pageable pageable = PageRequest.of(0, 10);

    List<String> referenceIds = List.of("ref999", "ref888");

    SelectInventoryImports importRecord =
        buildSelectInventoryImports(
            "campaign123", "company123", "test.csv", referenceIds, "United States");
    importRecord.setId(importId);

    Page<Inventory> emptyInventoryPage = new PageImpl<>(new ArrayList<>(), pageable, 0);

    when(selectInventoryImportsRepository.findById(importId)).thenReturn(Optional.of(importRecord));
    when(inventoryRepository.findByReferenceIdIn(referenceIds, pageable))
        .thenReturn(emptyInventoryPage);

    // When
    Page<ImportInventoryDetailResponseDTO> result =
        inventoryImportService.getInventoriesByImportId(importId, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0);
    assertThat(result.getTotalPages()).isEqualTo(0);

    verify(selectInventoryImportsRepository, times(1)).findById(importId);
    verify(inventoryRepository, times(1)).findByReferenceIdIn(referenceIds, pageable);
  }

  @Test
  @DisplayName("getInventoriesByImportId - Should correctly convert Inventory to DTO")
  void getInventoriesByImportId_ShouldConvertInventoryToDTO() {
    // Given
    String importId = "import123";
    Pageable pageable = PageRequest.of(0, 10);

    List<String> referenceIds = List.of("ref123");

    SelectInventoryImports importRecord =
        buildSelectInventoryImports(
            "campaign123", "company123", "test.csv", referenceIds, "United States");
    importRecord.setId(importId);

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");
    location.setState("California");
    location.setAddress("123 Main St");
    location.setZipCode("90210");

    Inventory inv = new Inventory();
    inv.setId("inv123");
    inv.setReferenceId("ref123");
    inv.setName("Test Inventory");
    inv.setType("CLASSIC");
    inv.setEnvironment("Outdoor");
    inv.setThumbnailUrl("https://example.com/thumb.jpg");
    inv.setSize("48x14");
    inv.setInventoryCluster(List.of("cluster-A"));
    inv.setLocation(location);

    List<Inventory> inventories = List.of(inv);
    Page<Inventory> inventoryPage = new PageImpl<>(inventories, pageable, 1);

    when(selectInventoryImportsRepository.findById(importId)).thenReturn(Optional.of(importRecord));
    when(inventoryRepository.findByReferenceIdIn(referenceIds, pageable)).thenReturn(inventoryPage);

    // When
    Page<ImportInventoryDetailResponseDTO> result =
        inventoryImportService.getInventoriesByImportId(importId, pageable);

    // Then
    assertThat(result.getContent()).hasSize(1);
    ImportInventoryDetailResponseDTO dto = result.getContent().get(0);
    assertThat(dto.getId()).isEqualTo("inv123");
    assertThat(dto.getReferenceId()).isEqualTo("ref123");
    assertThat(dto.getInventoryName()).isEqualTo("Test Inventory");
    assertThat(dto.getType()).isEqualTo("CLASSIC");
    assertThat(dto.getEnvironment()).isEqualTo("Outdoor");
    assertThat(dto.getThumbnail()).isEqualTo("https://example.com/thumb.jpg");
    assertThat(dto.getSize()).isEqualTo("48x14");
    assertThat(dto.getInventoryCluster()).containsExactly("cluster-A");
    assertThat(dto.getLocation()).isNotNull();
    assertThat(dto.getLocation().getCountry()).isEqualTo("United States");
    assertThat(dto.getLocation().getState()).isEqualTo("California");
    assertThat(dto.getLocation().getAddress()).isEqualTo("123 Main St");
    assertThat(dto.getLocation().getZipCode()).isEqualTo("90210");

    verify(selectInventoryImportsRepository, times(1)).findById(importId);
    verify(inventoryRepository, times(1)).findByReferenceIdIn(referenceIds, pageable);
  }

  @Test
  @DisplayName(
      "getInventoriesByImportId - Should correctly convert Inventory with GeoJsonPoint to DTO")
  void getInventoriesByImportId_WithGeoJsonPoint_ShouldConvertToDTO() {
    // Given
    String importId = "import123";
    Pageable pageable = PageRequest.of(0, 10);

    List<String> referenceIds = List.of("ref123");

    SelectInventoryImports importRecord =
        buildSelectInventoryImports(
            "campaign123", "company123", "test.csv", referenceIds, "United States");
    importRecord.setId(importId);

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");
    location.setState("California");
    location.setCity("Los Angeles");
    location.setAddress("123 Main St");
    location.setZipCode("90210");
    location.setLocationCoordinates(
        new org.springframework.data.mongodb.core.geo.GeoJsonPoint(34.0522, -118.2437));

    Inventory inv = new Inventory();
    inv.setId("inv123");
    inv.setReferenceId("ref123");
    inv.setName("Test Inventory");
    inv.setType("CLASSIC");
    inv.setEnvironment("Outdoor");
    inv.setThumbnailUrl("https://example.com/thumb.jpg");
    inv.setLocation(location);

    List<Inventory> inventories = List.of(inv);
    Page<Inventory> inventoryPage = new PageImpl<>(inventories, pageable, 1);

    when(selectInventoryImportsRepository.findById(importId)).thenReturn(Optional.of(importRecord));
    when(inventoryRepository.findByReferenceIdIn(referenceIds, pageable)).thenReturn(inventoryPage);

    // When
    Page<ImportInventoryDetailResponseDTO> result =
        inventoryImportService.getInventoriesByImportId(importId, pageable);

    // Then
    assertThat(result.getContent()).hasSize(1);
    ImportInventoryDetailResponseDTO dto = result.getContent().get(0);
    assertThat(dto.getLocation()).isNotNull();
    assertThat(dto.getLocation().getCity()).isEqualTo("Los Angeles");
    assertThat(dto.getLocation().getLocationCoordinates()).isNotNull();
    assertThat(dto.getLocation().getLocationCoordinates().getType()).isEqualTo("Point");
    assertThat(dto.getLocation().getLocationCoordinates().getCoordinates()).hasSize(1);
    assertThat(dto.getLocation().getLocationCoordinates().getCoordinates().get(0).getLatitude())
        .isEqualTo(-118.2437);
    assertThat(dto.getLocation().getLocationCoordinates().getCoordinates().get(0).getLongitude())
        .isEqualTo(34.0522);

    verify(selectInventoryImportsRepository, times(1)).findById(importId);
    verify(inventoryRepository, times(1)).findByReferenceIdIn(referenceIds, pageable);
  }

  @Test
  @DisplayName(
      "getInventoriesByImportId - Should correctly convert Inventory with GeoJsonLineString to DTO")
  void getInventoriesByImportId_WithGeoJsonLineString_ShouldConvertToDTO() {
    // Given
    String importId = "import123";
    Pageable pageable = PageRequest.of(0, 10);

    List<String> referenceIds = List.of("ref123");

    SelectInventoryImports importRecord =
        buildSelectInventoryImports(
            "campaign123", "company123", "test.csv", referenceIds, "United States");
    importRecord.setId(importId);

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");
    location.setState("California");
    location.setCity("San Francisco");
    location.setAddress("456 Market St");
    location.setZipCode("94102");
    location.setLocationCoordinates(
        new org.springframework.data.mongodb.core.geo.GeoJsonLineString(
            new org.springframework.data.geo.Point(-122.4194, 37.7749),
            new org.springframework.data.geo.Point(-122.4094, 37.7849)));

    Inventory inv = new Inventory();
    inv.setId("inv123");
    inv.setReferenceId("ref123");
    inv.setName("Test Inventory");
    inv.setType("CLASSIC");
    inv.setEnvironment("Outdoor");
    inv.setThumbnailUrl("https://example.com/thumb.jpg");
    inv.setLocation(location);

    List<Inventory> inventories = List.of(inv);
    Page<Inventory> inventoryPage = new PageImpl<>(inventories, pageable, 1);

    when(selectInventoryImportsRepository.findById(importId)).thenReturn(Optional.of(importRecord));
    when(inventoryRepository.findByReferenceIdIn(referenceIds, pageable)).thenReturn(inventoryPage);

    // When
    Page<ImportInventoryDetailResponseDTO> result =
        inventoryImportService.getInventoriesByImportId(importId, pageable);

    // Then
    assertThat(result.getContent()).hasSize(1);
    ImportInventoryDetailResponseDTO dto = result.getContent().get(0);
    assertThat(dto.getLocation()).isNotNull();
    assertThat(dto.getLocation().getCity()).isEqualTo("San Francisco");
    assertThat(dto.getLocation().getLocationCoordinates()).isNotNull();
    assertThat(dto.getLocation().getLocationCoordinates().getType()).isEqualTo("LineString");
    assertThat(dto.getLocation().getLocationCoordinates().getCoordinates()).hasSize(2);
    assertThat(dto.getLocation().getLocationCoordinates().getCoordinates().get(0).getLatitude())
        .isEqualTo(37.7749);
    assertThat(dto.getLocation().getLocationCoordinates().getCoordinates().get(0).getLongitude())
        .isEqualTo(-122.4194);
    assertThat(dto.getLocation().getLocationCoordinates().getCoordinates().get(1).getLatitude())
        .isEqualTo(37.7849);
    assertThat(dto.getLocation().getLocationCoordinates().getCoordinates().get(1).getLongitude())
        .isEqualTo(-122.4094);

    verify(selectInventoryImportsRepository, times(1)).findById(importId);
    verify(inventoryRepository, times(1)).findByReferenceIdIn(referenceIds, pageable);
  }

  @Test
  @DisplayName(
      "getInventoriesByImportId - Should correctly convert Inventory with Map-based Point coordinates to DTO")
  void getInventoriesByImportId_WithMapBasedPoint_ShouldConvertToDTO() {
    // Given
    String importId = "import123";
    Pageable pageable = PageRequest.of(0, 10);

    List<String> referenceIds = List.of("ref123");

    SelectInventoryImports importRecord =
        buildSelectInventoryImports(
            "campaign123", "company123", "test.csv", referenceIds, "United States");
    importRecord.setId(importId);

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");
    location.setState("New York");
    location.setCity("New York");
    location.setAddress("789 Broadway");
    location.setZipCode("10003");
    // Create Map-based GeoJSON (as it might be deserialized from MongoDB)
    java.util.Map<String, Object> coordMap = new java.util.HashMap<>();
    coordMap.put("type", "Point");
    coordMap.put("coordinates", java.util.List.of(-74.0060, 40.7128));
    location.setLocationCoordinates(coordMap);

    Inventory inv = new Inventory();
    inv.setId("inv123");
    inv.setReferenceId("ref123");
    inv.setName("Test Inventory");
    inv.setType("CLASSIC");
    inv.setEnvironment("Outdoor");
    inv.setThumbnailUrl("https://example.com/thumb.jpg");
    inv.setLocation(location);

    List<Inventory> inventories = List.of(inv);
    Page<Inventory> inventoryPage = new PageImpl<>(inventories, pageable, 1);

    when(selectInventoryImportsRepository.findById(importId)).thenReturn(Optional.of(importRecord));
    when(inventoryRepository.findByReferenceIdIn(referenceIds, pageable)).thenReturn(inventoryPage);

    // When
    Page<ImportInventoryDetailResponseDTO> result =
        inventoryImportService.getInventoriesByImportId(importId, pageable);

    // Then
    assertThat(result.getContent()).hasSize(1);
    ImportInventoryDetailResponseDTO dto = result.getContent().get(0);
    assertThat(dto.getLocation()).isNotNull();
    assertThat(dto.getLocation().getCity()).isEqualTo("New York");
    assertThat(dto.getLocation().getLocationCoordinates()).isNotNull();
    assertThat(dto.getLocation().getLocationCoordinates().getType()).isEqualTo("Point");
    assertThat(dto.getLocation().getLocationCoordinates().getCoordinates()).hasSize(1);
    assertThat(dto.getLocation().getLocationCoordinates().getCoordinates().get(0).getLatitude())
        .isEqualTo(40.7128);
    assertThat(dto.getLocation().getLocationCoordinates().getCoordinates().get(0).getLongitude())
        .isEqualTo(-74.0060);

    verify(selectInventoryImportsRepository, times(1)).findById(importId);
    verify(inventoryRepository, times(1)).findByReferenceIdIn(referenceIds, pageable);
  }

  @Test
  @DisplayName(
      "getInventoriesByImportId - Should correctly convert Inventory with Map-based LineString coordinates to DTO")
  void getInventoriesByImportId_WithMapBasedLineString_ShouldConvertToDTO() {
    // Given
    String importId = "import123";
    Pageable pageable = PageRequest.of(0, 10);

    List<String> referenceIds = List.of("ref123");

    SelectInventoryImports importRecord =
        buildSelectInventoryImports(
            "campaign123", "company123", "test.csv", referenceIds, "United States");
    importRecord.setId(importId);

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");
    location.setState("Texas");
    location.setCity("Houston");
    location.setAddress("321 Main St");
    location.setZipCode("77002");
    // Create Map-based GeoJSON LineString (as it might be deserialized from MongoDB)
    java.util.Map<String, Object> coordMap = new java.util.HashMap<>();
    coordMap.put("type", "LineString");
    coordMap.put(
        "coordinates",
        java.util.List.of(
            java.util.List.of(-95.3698, 29.7604), java.util.List.of(-95.3598, 29.7704)));
    location.setLocationCoordinates(coordMap);

    Inventory inv = new Inventory();
    inv.setId("inv123");
    inv.setReferenceId("ref123");
    inv.setName("Test Inventory");
    inv.setType("CLASSIC");
    inv.setEnvironment("Outdoor");
    inv.setThumbnailUrl("https://example.com/thumb.jpg");
    inv.setLocation(location);

    List<Inventory> inventories = List.of(inv);
    Page<Inventory> inventoryPage = new PageImpl<>(inventories, pageable, 1);

    when(selectInventoryImportsRepository.findById(importId)).thenReturn(Optional.of(importRecord));
    when(inventoryRepository.findByReferenceIdIn(referenceIds, pageable)).thenReturn(inventoryPage);

    // When
    Page<ImportInventoryDetailResponseDTO> result =
        inventoryImportService.getInventoriesByImportId(importId, pageable);

    // Then
    assertThat(result.getContent()).hasSize(1);
    ImportInventoryDetailResponseDTO dto = result.getContent().get(0);
    assertThat(dto.getLocation()).isNotNull();
    assertThat(dto.getLocation().getCity()).isEqualTo("Houston");
    assertThat(dto.getLocation().getLocationCoordinates()).isNotNull();
    assertThat(dto.getLocation().getLocationCoordinates().getType()).isEqualTo("LineString");
    assertThat(dto.getLocation().getLocationCoordinates().getCoordinates()).hasSize(2);
    assertThat(dto.getLocation().getLocationCoordinates().getCoordinates().get(0).getLatitude())
        .isEqualTo(29.7604);
    assertThat(dto.getLocation().getLocationCoordinates().getCoordinates().get(0).getLongitude())
        .isEqualTo(-95.3698);
    assertThat(dto.getLocation().getLocationCoordinates().getCoordinates().get(1).getLatitude())
        .isEqualTo(29.7704);
    assertThat(dto.getLocation().getLocationCoordinates().getCoordinates().get(1).getLongitude())
        .isEqualTo(-95.3598);

    verify(selectInventoryImportsRepository, times(1)).findById(importId);
    verify(inventoryRepository, times(1)).findByReferenceIdIn(referenceIds, pageable);
  }

  @Test
  @DisplayName(
      "getInventoriesByImportId - Should correctly handle Inventory with null location coordinates")
  void getInventoriesByImportId_WithNullLocationCoordinates_ShouldConvertToDTO() {
    // Given
    String importId = "import123";
    Pageable pageable = PageRequest.of(0, 10);

    List<String> referenceIds = List.of("ref123");

    SelectInventoryImports importRecord =
        buildSelectInventoryImports(
            "campaign123", "company123", "test.csv", referenceIds, "United States");
    importRecord.setId(importId);

    Inventory.Location location = new Inventory.Location();
    location.setCountry("United States");
    location.setState("Florida");
    location.setCity("Miami");
    location.setAddress("555 Ocean Dr");
    location.setZipCode("33139");
    location.setLocationCoordinates(null);

    Inventory inv = new Inventory();
    inv.setId("inv123");
    inv.setReferenceId("ref123");
    inv.setName("Test Inventory");
    inv.setType("CLASSIC");
    inv.setEnvironment("Outdoor");
    inv.setThumbnailUrl("https://example.com/thumb.jpg");
    inv.setLocation(location);

    List<Inventory> inventories = List.of(inv);
    Page<Inventory> inventoryPage = new PageImpl<>(inventories, pageable, 1);

    when(selectInventoryImportsRepository.findById(importId)).thenReturn(Optional.of(importRecord));
    when(inventoryRepository.findByReferenceIdIn(referenceIds, pageable)).thenReturn(inventoryPage);

    // When
    Page<ImportInventoryDetailResponseDTO> result =
        inventoryImportService.getInventoriesByImportId(importId, pageable);

    // Then
    assertThat(result.getContent()).hasSize(1);
    ImportInventoryDetailResponseDTO dto = result.getContent().get(0);
    assertThat(dto.getLocation()).isNotNull();
    assertThat(dto.getLocation().getCity()).isEqualTo("Miami");
    assertThat(dto.getLocation().getLocationCoordinates()).isNull();

    verify(selectInventoryImportsRepository, times(1)).findById(importId);
    verify(inventoryRepository, times(1)).findByReferenceIdIn(referenceIds, pageable);
  }

  // ========== importGeoCoordinates Tests ==========

  @Test
  @DisplayName("importGeoCoordinates - Should successfully import geo coordinates")
  void importGeoCoordinates_WithValidData_ShouldSaveSuccessfully() {
    // Given
    String companyId = "company123";
    String fileName = "geo_import.csv";
    String countryName = "Singapore";

    GeoCoordinatesImportRequestDTO.GeoDetailDTO geoDetail1 =
        GeoCoordinatesImportRequestDTO.GeoDetailDTO.builder()
            .locationName("Location 1")
            .latitude("1.3352566")
            .longitude("103.963586")
            .siteType("OUTDOOR")
            .radius("100")
            .build();

    GeoCoordinatesImportRequestDTO.GeoDetailDTO geoDetail2 =
        GeoCoordinatesImportRequestDTO.GeoDetailDTO.builder()
            .locationName("Location 2")
            .latitude("1.3452566")
            .longitude("103.973586")
            .siteType("INDOOR")
            .radius("200")
            .build();

    GeoCoordinatesImportRequestDTO request =
        GeoCoordinatesImportRequestDTO.builder()
            .fileName(fileName)
            .countryName(countryName)
            .geoDetails(List.of(geoDetail1, geoDetail2))
            .build();

    when(campaignGeoImportFileRepository.save(any(CampaignGeoImportFile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    inventoryImportService.importGeoCoordinates(request, companyId);

    // Then
    verify(campaignGeoImportFileRepository, times(1))
        .save(
            argThat(
                geoImportFile ->
                    geoImportFile.getFileName().equals(fileName)
                        && geoImportFile.getCountryName().equals(countryName)
                        && geoImportFile.getCompanyId().equals(companyId)
                        && geoImportFile.getGeoDetails().size() == 2
                        && geoImportFile
                            .getGeoDetails()
                            .get(0)
                            .getLocationName()
                            .equals("Location 1")
                        && geoImportFile.getGeoDetails().get(0).getLatitude().equals("1.3352566")
                        && geoImportFile.getGeoDetails().get(0).getLongitude().equals("103.963586")
                        && geoImportFile.getGeoDetails().get(0).getSiteType().equals("OUTDOOR")
                        && geoImportFile.getGeoDetails().get(0).getRadius().equals("100")));
  }

  @Test
  @DisplayName("importGeoCoordinates - Should throw exception when geo details list is empty")
  void importGeoCoordinates_WithEmptyGeoDetails_ShouldThrowException() {
    // Given
    String companyId = "company123";

    GeoCoordinatesImportRequestDTO request =
        GeoCoordinatesImportRequestDTO.builder()
            .fileName("geo_import.csv")
            .countryName("Singapore")
            .geoDetails(new ArrayList<>())
            .build();

    // When & Then
    assertThatThrownBy(() -> inventoryImportService.importGeoCoordinates(request, companyId))
        .isInstanceOf(CsvUploadException.class)
        .satisfies(
            exception -> {
              CsvUploadException csvException = (CsvUploadException) exception;
              assertThat(csvException.getErrorCode()).isEqualTo(ErrorCode.CSV_UPLOAD_INVALID_FILE);
              assertThat(csvException.getMessage()).isEqualTo("Geo details list cannot be empty");
            });
    verify(campaignGeoImportFileRepository, never()).save(any());
  }

  @Test
  @DisplayName("importGeoCoordinates - Should trim whitespace from geo detail fields")
  void importGeoCoordinates_WithWhitespaceInFields_ShouldTrimValues() {
    // Given
    String companyId = "company123";

    GeoCoordinatesImportRequestDTO.GeoDetailDTO geoDetail =
        GeoCoordinatesImportRequestDTO.GeoDetailDTO.builder()
            .locationName("  Location 1  ")
            .latitude("  1.3352566  ")
            .longitude("  103.963586  ")
            .siteType("  OUTDOOR  ")
            .radius("  100  ")
            .build();

    GeoCoordinatesImportRequestDTO request =
        GeoCoordinatesImportRequestDTO.builder()
            .fileName("geo_import.csv")
            .countryName("Singapore")
            .geoDetails(List.of(geoDetail))
            .build();

    when(campaignGeoImportFileRepository.save(any(CampaignGeoImportFile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    inventoryImportService.importGeoCoordinates(request, companyId);

    // Then
    verify(campaignGeoImportFileRepository, times(1))
        .save(
            argThat(
                geoImportFile ->
                    geoImportFile.getGeoDetails().get(0).getLocationName().equals("Location 1")
                        && geoImportFile.getGeoDetails().get(0).getLatitude().equals("1.3352566")
                        && geoImportFile.getGeoDetails().get(0).getLongitude().equals("103.963586")
                        && geoImportFile.getGeoDetails().get(0).getSiteType().equals("OUTDOOR")
                        && geoImportFile.getGeoDetails().get(0).getRadius().equals("100")));
  }

  @Test
  @DisplayName("importGeoCoordinates - Should handle null siteType and radius")
  void importGeoCoordinates_WithNullOptionalFields_ShouldHandleGracefully() {
    // Given
    String companyId = "company123";

    GeoCoordinatesImportRequestDTO.GeoDetailDTO geoDetail =
        GeoCoordinatesImportRequestDTO.GeoDetailDTO.builder()
            .locationName("Location 1")
            .latitude("1.3352566")
            .longitude("103.963586")
            .siteType(null)
            .radius(null)
            .build();

    GeoCoordinatesImportRequestDTO request =
        GeoCoordinatesImportRequestDTO.builder()
            .fileName("geo_import.csv")
            .countryName("Singapore")
            .geoDetails(List.of(geoDetail))
            .build();

    when(campaignGeoImportFileRepository.save(any(CampaignGeoImportFile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    inventoryImportService.importGeoCoordinates(request, companyId);

    // Then
    verify(campaignGeoImportFileRepository, times(1))
        .save(
            argThat(
                geoImportFile ->
                    geoImportFile.getGeoDetails().get(0).getLocationName().equals("Location 1")
                        && geoImportFile.getGeoDetails().get(0).getSiteType() == null
                        && geoImportFile.getGeoDetails().get(0).getRadius() == null));
  }

  // ========== getGeoImportFileById Tests ==========

  @Test
  @DisplayName("getGeoImportFileById - Should return geo details list successfully")
  void getGeoImportFileById_WithValidId_ShouldReturnGeoDetails() {
    // Given
    String geoImportId = "geoImport123";

    CampaignGeoImportFile.GeoDetails geoDetail1 =
        CampaignGeoImportFile.GeoDetails.builder()
            .locationName("Location 1")
            .latitude("1.3352566")
            .longitude("103.963586")
            .siteType("OUTDOOR")
            .radius("100")
            .build();

    CampaignGeoImportFile.GeoDetails geoDetail2 =
        CampaignGeoImportFile.GeoDetails.builder()
            .locationName("Location 2")
            .latitude("1.3452566")
            .longitude("103.973586")
            .siteType("INDOOR")
            .radius("200")
            .build();

    CampaignGeoImportFile geoImportFile =
        CampaignGeoImportFile.builder()
            .fileName("geo_import.csv")
            .countryName("Singapore")
            .companyId("company123")
            .geoDetails(List.of(geoDetail1, geoDetail2))
            .build();
    geoImportFile.setId(geoImportId);

    when(campaignGeoImportFileRepository.findById(geoImportId))
        .thenReturn(Optional.of(geoImportFile));

    // When
    List<GeoImportFileResponseDTO.GeoDetailsDTO> result =
        inventoryImportService.getGeoImportFileById(geoImportId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getLocationName()).isEqualTo("Location 1");
    assertThat(result.get(0).getLatitude()).isEqualTo("1.3352566");
    assertThat(result.get(0).getLongitude()).isEqualTo("103.963586");
    assertThat(result.get(0).getSiteType()).isEqualTo("OUTDOOR");
    assertThat(result.get(0).getRadius()).isEqualTo("100");
    assertThat(result.get(1).getLocationName()).isEqualTo("Location 2");
    assertThat(result.get(1).getLatitude()).isEqualTo("1.3452566");
    assertThat(result.get(1).getLongitude()).isEqualTo("103.973586");
    assertThat(result.get(1).getSiteType()).isEqualTo("INDOOR");
    assertThat(result.get(1).getRadius()).isEqualTo("200");

    verify(campaignGeoImportFileRepository, times(1)).findById(geoImportId);
  }

  @Test
  @DisplayName("getGeoImportFileById - Should throw exception when geo import file not found")
  void getGeoImportFileById_WithNonExistentId_ShouldThrowException() {
    // Given
    String geoImportId = "nonExistent";

    when(campaignGeoImportFileRepository.findById(geoImportId)).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> inventoryImportService.getGeoImportFileById(geoImportId))
        .isInstanceOf(CsvUploadException.class)
        .satisfies(
            exception -> {
              CsvUploadException csvException = (CsvUploadException) exception;
              assertThat(csvException.getErrorCode())
                  .isEqualTo(ErrorCode.INVENTORY_IMPORTS_NOT_FOUND);
              assertThat(csvException.getMessage()).isEqualTo("Geo import file not found");
            });

    verify(campaignGeoImportFileRepository, times(1)).findById(geoImportId);
  }

  @Test
  @DisplayName("getGeoImportFileById - Should return empty list when geo details is null")
  void getGeoImportFileById_WithNullGeoDetails_ShouldReturnEmptyList() {
    // Given
    String geoImportId = "geoImport123";

    CampaignGeoImportFile geoImportFile =
        CampaignGeoImportFile.builder()
            .fileName("geo_import.csv")
            .countryName("Singapore")
            .companyId("company123")
            .geoDetails(null)
            .build();
    geoImportFile.setId(geoImportId);

    when(campaignGeoImportFileRepository.findById(geoImportId))
        .thenReturn(Optional.of(geoImportFile));

    // When
    List<GeoImportFileResponseDTO.GeoDetailsDTO> result =
        inventoryImportService.getGeoImportFileById(geoImportId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
    verify(campaignGeoImportFileRepository, times(1)).findById(geoImportId);
  }

  @Test
  @DisplayName("getGeoImportFileById - Should return empty list when geo details is empty")
  void getGeoImportFileById_WithEmptyGeoDetails_ShouldReturnEmptyList() {
    // Given
    String geoImportId = "geoImport123";

    CampaignGeoImportFile geoImportFile =
        CampaignGeoImportFile.builder()
            .fileName("geo_import.csv")
            .countryName("Singapore")
            .companyId("company123")
            .geoDetails(new ArrayList<>())
            .build();
    geoImportFile.setId(geoImportId);

    when(campaignGeoImportFileRepository.findById(geoImportId))
        .thenReturn(Optional.of(geoImportFile));

    // When
    List<GeoImportFileResponseDTO.GeoDetailsDTO> result =
        inventoryImportService.getGeoImportFileById(geoImportId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
    verify(campaignGeoImportFileRepository, times(1)).findById(geoImportId);
  }

  // ========== generateGeoImportCsv Tests ==========

  @Test
  @DisplayName("generateGeoImportCsv - Should generate CSV with correct filename and content")
  void generateGeoImportCsv_WithValidId_ShouldReturnCsvFile() {
    // Given
    String geoImportId = "geoImport123";
    String fileName = "geo_import.csv";

    CampaignGeoImportFile.GeoDetails geoDetail1 =
        CampaignGeoImportFile.GeoDetails.builder()
            .locationName("Location 1")
            .latitude("1.3352566")
            .longitude("103.963586")
            .siteType("OUTDOOR")
            .radius("100")
            .build();

    CampaignGeoImportFile.GeoDetails geoDetail2 =
        CampaignGeoImportFile.GeoDetails.builder()
            .locationName("Location 2")
            .latitude("1.3452566")
            .longitude("103.973586")
            .siteType("INDOOR")
            .radius("200")
            .build();

    CampaignGeoImportFile geoImportFile =
        CampaignGeoImportFile.builder()
            .fileName(fileName)
            .countryName("Singapore")
            .companyId("company123")
            .geoDetails(List.of(geoDetail1, geoDetail2))
            .build();
    geoImportFile.setId(geoImportId);

    when(campaignGeoImportFileRepository.findById(geoImportId))
        .thenReturn(Optional.of(geoImportFile));

    // When
    InventoryImportService.CsvFileResult result =
        inventoryImportService.generateGeoImportCsv(geoImportId);

    // Then
    assertThat(result.fileName()).isEqualTo(fileName);
    assertThat(result.content()).isNotNull();
    String csvContent = new String(result.content());
    assertThat(csvContent).contains("Location,Latitude,Longitude,Type,Radius");
    assertThat(csvContent).contains("Location 1");
    assertThat(csvContent).contains("1.3352566");
    assertThat(csvContent).contains("103.963586");
    assertThat(csvContent).contains("OUTDOOR");
    assertThat(csvContent).contains("100");
    assertThat(csvContent).contains("Location 2");
    assertThat(csvContent).contains("1.3452566");
    assertThat(csvContent).contains("103.973586");
    assertThat(csvContent).contains("INDOOR");
    assertThat(csvContent).contains("200");

    verify(campaignGeoImportFileRepository, times(1)).findById(geoImportId);
  }

  @Test
  @DisplayName("generateGeoImportCsv - Should throw exception when geo import file not found")
  void generateGeoImportCsv_WithNonExistentId_ShouldThrowException() {
    // Given
    String geoImportId = "nonExistent";

    when(campaignGeoImportFileRepository.findById(geoImportId)).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> inventoryImportService.generateGeoImportCsv(geoImportId))
        .isInstanceOf(CsvUploadException.class)
        .satisfies(
            exception -> {
              CsvUploadException csvException = (CsvUploadException) exception;
              assertThat(csvException.getErrorCode())
                  .isEqualTo(ErrorCode.INVENTORY_IMPORTS_NOT_FOUND);
              assertThat(csvException.getMessage()).isEqualTo("Geo import file not found");
            });

    verify(campaignGeoImportFileRepository, times(1)).findById(geoImportId);
  }

  @Test
  @DisplayName("generateGeoImportCsv - Should use default filename when fileName is null")
  void generateGeoImportCsv_WithNullFileName_ShouldUseDefault() {
    // Given
    String geoImportId = "geoImport123";

    CampaignGeoImportFile.GeoDetails geoDetail =
        CampaignGeoImportFile.GeoDetails.builder()
            .locationName("Location 1")
            .latitude("1.3352566")
            .longitude("103.963586")
            .siteType("OUTDOOR")
            .radius("100")
            .build();

    CampaignGeoImportFile geoImportFile =
        CampaignGeoImportFile.builder()
            .fileName(null)
            .countryName("Singapore")
            .companyId("company123")
            .geoDetails(List.of(geoDetail))
            .build();
    geoImportFile.setId(geoImportId);

    when(campaignGeoImportFileRepository.findById(geoImportId))
        .thenReturn(Optional.of(geoImportFile));

    // When
    InventoryImportService.CsvFileResult result =
        inventoryImportService.generateGeoImportCsv(geoImportId);

    // Then
    assertThat(result.fileName()).isEqualTo("geo_import_" + geoImportId + ".csv");
    assertThat(result.content()).isNotNull();
    verify(campaignGeoImportFileRepository, times(1)).findById(geoImportId);
  }

  @Test
  @DisplayName("generateGeoImportCsv - Should use default filename when fileName is empty")
  void generateGeoImportCsv_WithEmptyFileName_ShouldUseDefault() {
    // Given
    String geoImportId = "geoImport123";

    CampaignGeoImportFile.GeoDetails geoDetail =
        CampaignGeoImportFile.GeoDetails.builder()
            .locationName("Location 1")
            .latitude("1.3352566")
            .longitude("103.963586")
            .siteType("OUTDOOR")
            .radius("100")
            .build();

    CampaignGeoImportFile geoImportFile =
        CampaignGeoImportFile.builder()
            .fileName("   ")
            .countryName("Singapore")
            .companyId("company123")
            .geoDetails(List.of(geoDetail))
            .build();
    geoImportFile.setId(geoImportId);

    when(campaignGeoImportFileRepository.findById(geoImportId))
        .thenReturn(Optional.of(geoImportFile));

    // When
    InventoryImportService.CsvFileResult result =
        inventoryImportService.generateGeoImportCsv(geoImportId);

    // Then
    assertThat(result.fileName()).isEqualTo("geo_import_" + geoImportId + ".csv");
    verify(campaignGeoImportFileRepository, times(1)).findById(geoImportId);
  }

  @Test
  @DisplayName(
      "generateGeoImportCsv - Should return CSV with header only when geo details is empty")
  void generateGeoImportCsv_WithEmptyGeoDetails_ShouldReturnHeaderOnly() {
    // Given
    String geoImportId = "geoImport123";
    String fileName = "geo_import.csv";

    CampaignGeoImportFile geoImportFile =
        CampaignGeoImportFile.builder()
            .fileName(fileName)
            .countryName("Singapore")
            .companyId("company123")
            .geoDetails(new ArrayList<>())
            .build();
    geoImportFile.setId(geoImportId);

    when(campaignGeoImportFileRepository.findById(geoImportId))
        .thenReturn(Optional.of(geoImportFile));

    // When
    InventoryImportService.CsvFileResult result =
        inventoryImportService.generateGeoImportCsv(geoImportId);

    // Then
    assertThat(result.fileName()).isEqualTo(fileName);
    assertThat(result.content()).isNotNull();
    String csvContent = new String(result.content());
    assertThat(csvContent).contains("Location,Latitude,Longitude,Type,Radius");
    assertThat(csvContent.split("\n").length).isEqualTo(1); // Only header
    verify(campaignGeoImportFileRepository, times(1)).findById(geoImportId);
  }

  @Test
  @DisplayName("generateGeoImportCsv - Should handle null siteType and radius in CSV")
  void generateGeoImportCsv_WithNullOptionalFields_ShouldHandleGracefully() {
    // Given
    String geoImportId = "geoImport123";

    CampaignGeoImportFile.GeoDetails geoDetail =
        CampaignGeoImportFile.GeoDetails.builder()
            .locationName("Location 1")
            .latitude("1.3352566")
            .longitude("103.963586")
            .siteType(null)
            .radius(null)
            .build();

    CampaignGeoImportFile geoImportFile =
        CampaignGeoImportFile.builder()
            .fileName("geo_import.csv")
            .countryName("Singapore")
            .companyId("company123")
            .geoDetails(List.of(geoDetail))
            .build();
    geoImportFile.setId(geoImportId);

    when(campaignGeoImportFileRepository.findById(geoImportId))
        .thenReturn(Optional.of(geoImportFile));

    // When
    InventoryImportService.CsvFileResult result =
        inventoryImportService.generateGeoImportCsv(geoImportId);

    // Then
    assertThat(result.content()).isNotNull();
    String csvContent = new String(result.content());
    assertThat(csvContent).contains("Location 1");
    assertThat(csvContent).contains("1.3352566");
    assertThat(csvContent).contains("103.963586");
    // Null values should be empty strings in CSV
    String[] lines = csvContent.split("\n");
    assertThat(lines[1]).contains("Location 1,1.3352566,103.963586,,");
    verify(campaignGeoImportFileRepository, times(1)).findById(geoImportId);
  }

  @Test
  @DisplayName("generateGeoImportCsv - Should escape CSV values with commas")
  void generateGeoImportCsv_WithCommasInValues_ShouldEscapeCorrectly() {
    // Given
    String geoImportId = "geoImport123";

    CampaignGeoImportFile.GeoDetails geoDetail =
        CampaignGeoImportFile.GeoDetails.builder()
            .locationName("Location, with comma")
            .latitude("1.3352566")
            .longitude("103.963586")
            .siteType("OUTDOOR")
            .radius("100")
            .build();

    CampaignGeoImportFile geoImportFile =
        CampaignGeoImportFile.builder()
            .fileName("geo_import.csv")
            .countryName("Singapore")
            .companyId("company123")
            .geoDetails(List.of(geoDetail))
            .build();
    geoImportFile.setId(geoImportId);

    when(campaignGeoImportFileRepository.findById(geoImportId))
        .thenReturn(Optional.of(geoImportFile));

    // When
    InventoryImportService.CsvFileResult result =
        inventoryImportService.generateGeoImportCsv(geoImportId);

    // Then
    assertThat(result.content()).isNotNull();
    String csvContent = new String(result.content());
    // Values with commas should be wrapped in quotes
    assertThat(csvContent).contains("\"Location, with comma\"");
    verify(campaignGeoImportFileRepository, times(1)).findById(geoImportId);
  }

  // ========== deleteGeoImportFileById Tests ==========

  @Test
  @DisplayName("deleteGeoImportFileById - Should successfully delete geo import file")
  void deleteGeoImportFileById_WithValidId_ShouldDelete() {
    // Given
    String geoImportId = "geoImport123";

    when(campaignGeoImportFileRepository.existsById(geoImportId)).thenReturn(true);
    doNothing().when(campaignGeoImportFileRepository).deleteById(geoImportId);

    // When
    inventoryImportService.deleteGeoImportFileById(geoImportId);

    // Then
    verify(campaignGeoImportFileRepository, times(1)).existsById(geoImportId);
    verify(campaignGeoImportFileRepository, times(1)).deleteById(geoImportId);
  }

  @Test
  @DisplayName("deleteGeoImportFileById - Should throw exception when geo import file not found")
  void deleteGeoImportFileById_WithNonExistentId_ShouldThrowException() {
    // Given
    String geoImportId = "nonExistent";

    when(campaignGeoImportFileRepository.existsById(geoImportId)).thenReturn(false);

    // When & Then
    assertThatThrownBy(() -> inventoryImportService.deleteGeoImportFileById(geoImportId))
        .isInstanceOf(CsvUploadException.class)
        .satisfies(
            exception -> {
              CsvUploadException csvException = (CsvUploadException) exception;
              assertThat(csvException.getErrorCode())
                  .isEqualTo(ErrorCode.INVENTORY_IMPORTS_NOT_FOUND);
              assertThat(csvException.getMessage()).isEqualTo("Geo import file not found");
            });

    verify(campaignGeoImportFileRepository, times(1)).existsById(geoImportId);
    verify(campaignGeoImportFileRepository, never()).deleteById(anyString());
  }

  // ========== getGeoImportFilesByCountry Tests ==========

  @Test
  @DisplayName("getGeoImportFilesByCountry - Should return paginated geo import files successfully")
  void getGeoImportFilesByCountry_WithValidParams_ShouldReturnPaginatedFiles() {
    // Given
    String companyId = "company123";
    String countryName = "Singapore";
    Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());

    CampaignGeoImportFile.GeoDetails geoDetail1 =
        CampaignGeoImportFile.GeoDetails.builder()
            .locationName("Location 1")
            .latitude("1.3352566")
            .longitude("103.963586")
            .siteType("OUTDOOR")
            .radius("100")
            .build();

    CampaignGeoImportFile.GeoDetails geoDetail2 =
        CampaignGeoImportFile.GeoDetails.builder()
            .locationName("Location 2")
            .latitude("1.3452566")
            .longitude("103.973586")
            .siteType("INDOOR")
            .radius("200")
            .build();

    CampaignGeoImportFile geoImportFile1 =
        CampaignGeoImportFile.builder()
            .fileName("geo_import_1.csv")
            .countryName(countryName)
            .companyId(companyId)
            .geoDetails(List.of(geoDetail1, geoDetail2))
            .build();
    geoImportFile1.setId("geoImport1");

    CampaignGeoImportFile geoImportFile2 =
        CampaignGeoImportFile.builder()
            .fileName("geo_import_2.csv")
            .countryName(countryName)
            .companyId(companyId)
            .geoDetails(List.of(geoDetail1))
            .build();
    geoImportFile2.setId("geoImport2");

    List<CampaignGeoImportFile> geoImportFiles = List.of(geoImportFile1, geoImportFile2);
    Page<CampaignGeoImportFile> geoImportsPage = new PageImpl<>(geoImportFiles, pageable, 2);

    when(campaignGeoImportFileRepository.findByCompanyIdAndCountryName(
            companyId, countryName, pageable))
        .thenReturn(geoImportsPage);

    // When
    Page<GeoImportFileResponseDTO> result =
        inventoryImportService.getGeoImportFilesByCountry(companyId, countryName, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getTotalPages()).isEqualTo(1);
    assertThat(result.getNumber()).isEqualTo(0);
    assertThat(result.getSize()).isEqualTo(10);

    GeoImportFileResponseDTO dto1 = result.getContent().get(0);
    assertThat(dto1.getId()).isEqualTo("geoImport1");
    assertThat(dto1.getFileName()).isEqualTo("geo_import_1.csv");
    assertThat(dto1.getCountryName()).isEqualTo(countryName);
    assertThat(dto1.getCompanyId()).isEqualTo(companyId);
    assertThat(dto1.getCountOfCoordinates()).isEqualTo(2);
    assertThat(dto1.getGeoDetails()).isNull(); // Should be null in list view

    GeoImportFileResponseDTO dto2 = result.getContent().get(1);
    assertThat(dto2.getId()).isEqualTo("geoImport2");
    assertThat(dto2.getFileName()).isEqualTo("geo_import_2.csv");
    assertThat(dto2.getCountOfCoordinates()).isEqualTo(1);

    verify(campaignGeoImportFileRepository, times(1))
        .findByCompanyIdAndCountryName(companyId, countryName, pageable);
  }

  @Test
  @DisplayName("getGeoImportFilesByCountry - Should return empty page when no files found")
  void getGeoImportFilesByCountry_WithNoMatchingFiles_ShouldReturnEmptyPage() {
    // Given
    String companyId = "company123";
    String countryName = "Singapore";
    Pageable pageable = PageRequest.of(0, 10);

    Page<CampaignGeoImportFile> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);

    when(campaignGeoImportFileRepository.findByCompanyIdAndCountryName(
            companyId, countryName, pageable))
        .thenReturn(emptyPage);

    // When
    Page<GeoImportFileResponseDTO> result =
        inventoryImportService.getGeoImportFilesByCountry(companyId, countryName, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0);
    assertThat(result.getTotalPages()).isEqualTo(0);

    verify(campaignGeoImportFileRepository, times(1))
        .findByCompanyIdAndCountryName(companyId, countryName, pageable);
  }

  @Test
  @DisplayName("getGeoImportFilesByCountry - Should handle pagination correctly")
  void getGeoImportFilesByCountry_WithPagination_ShouldReturnCorrectPage() {
    // Given
    String companyId = "company123";
    String countryName = "Singapore";
    Pageable pageable = PageRequest.of(1, 2, Sort.by("fileName").ascending());

    CampaignGeoImportFile geoImportFile1 =
        CampaignGeoImportFile.builder()
            .fileName("geo_import_1.csv")
            .countryName(countryName)
            .companyId(companyId)
            .geoDetails(new ArrayList<>())
            .build();
    geoImportFile1.setId("geoImport1");

    CampaignGeoImportFile geoImportFile2 =
        CampaignGeoImportFile.builder()
            .fileName("geo_import_2.csv")
            .countryName(countryName)
            .companyId(companyId)
            .geoDetails(new ArrayList<>())
            .build();
    geoImportFile2.setId("geoImport2");

    List<CampaignGeoImportFile> geoImportFiles = List.of(geoImportFile1, geoImportFile2);
    Page<CampaignGeoImportFile> geoImportsPage = new PageImpl<>(geoImportFiles, pageable, 5);

    when(campaignGeoImportFileRepository.findByCompanyIdAndCountryName(
            companyId, countryName, pageable))
        .thenReturn(geoImportsPage);

    // When
    Page<GeoImportFileResponseDTO> result =
        inventoryImportService.getGeoImportFilesByCountry(companyId, countryName, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getTotalElements()).isEqualTo(5);
    assertThat(result.getTotalPages()).isEqualTo(3);
    assertThat(result.getNumber()).isEqualTo(1);
    assertThat(result.getSize()).isEqualTo(2);

    verify(campaignGeoImportFileRepository, times(1))
        .findByCompanyIdAndCountryName(companyId, countryName, pageable);
  }

  @Test
  @DisplayName("getGeoImportFilesByCountry - Should handle null geo details gracefully")
  void getGeoImportFilesByCountry_WithNullGeoDetails_ShouldHandleGracefully() {
    // Given
    String companyId = "company123";
    String countryName = "Singapore";
    Pageable pageable = PageRequest.of(0, 10);

    CampaignGeoImportFile geoImportFile =
        CampaignGeoImportFile.builder()
            .fileName("geo_import_1.csv")
            .countryName(countryName)
            .companyId(companyId)
            .geoDetails(null)
            .build();
    geoImportFile.setId("geoImport1");

    Page<CampaignGeoImportFile> geoImportsPage =
        new PageImpl<>(List.of(geoImportFile), pageable, 1);

    when(campaignGeoImportFileRepository.findByCompanyIdAndCountryName(
            companyId, countryName, pageable))
        .thenReturn(geoImportsPage);

    // When
    Page<GeoImportFileResponseDTO> result =
        inventoryImportService.getGeoImportFilesByCountry(companyId, countryName, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getCountOfCoordinates()).isEqualTo(0);
    assertThat(result.getContent().get(0).getGeoDetails()).isNull();

    verify(campaignGeoImportFileRepository, times(1))
        .findByCompanyIdAndCountryName(companyId, countryName, pageable);
  }

  @Test
  @DisplayName("getGeoImportFilesByCountry - Should preserve metadata fields correctly")
  void getGeoImportFilesByCountry_ShouldPreserveMetadataFields() {
    // Given
    String companyId = "company123";
    String countryName = "Singapore";
    Pageable pageable = PageRequest.of(0, 10);

    CampaignGeoImportFile geoImportFile =
        CampaignGeoImportFile.builder()
            .fileName("geo_import_1.csv")
            .countryName(countryName)
            .companyId(companyId)
            .geoDetails(new ArrayList<>())
            .build();
    geoImportFile.setId("geoImport1");
    geoImportFile.setCreatedBy("user@example.com");
    geoImportFile.setLastModifiedBy("admin@example.com");
    geoImportFile.setCreatedAt(java.time.LocalDateTime.now());
    geoImportFile.setUpdatedAt(java.time.LocalDateTime.now());

    Page<CampaignGeoImportFile> geoImportsPage =
        new PageImpl<>(List.of(geoImportFile), pageable, 1);

    when(campaignGeoImportFileRepository.findByCompanyIdAndCountryName(
            companyId, countryName, pageable))
        .thenReturn(geoImportsPage);

    // When
    Page<GeoImportFileResponseDTO> result =
        inventoryImportService.getGeoImportFilesByCountry(companyId, countryName, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    GeoImportFileResponseDTO dto = result.getContent().get(0);
    assertThat(dto.getCreatedBy()).isEqualTo("user@example.com");
    assertThat(dto.getLastModifiedBy()).isEqualTo("admin@example.com");
    assertThat(dto.getCreatedAt()).isNotNull();
    assertThat(dto.getUpdatedAt()).isNotNull();

    verify(campaignGeoImportFileRepository, times(1))
        .findByCompanyIdAndCountryName(companyId, countryName, pageable);
  }
}
