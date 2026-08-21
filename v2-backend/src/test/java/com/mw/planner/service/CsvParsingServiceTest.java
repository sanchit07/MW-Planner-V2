package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.csv.CsvUploadException;
import com.mw.planner.service.CsvParsingService.InventoryIdRecord;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for CsvParsingService. Tests CSV parsing functionality for inventory ID extraction.
 */
@ExtendWith(MockitoExtension.class)
class CsvParsingServiceTest {

  private CsvParsingService csvParsingService;

  @BeforeEach
  void setUp() {
    csvParsingService = new CsvParsingService();
  }

  // ========== parseInventoryIdCsvFile Tests ==========

  @Test
  @DisplayName("parseInventoryIdCsvFile - Should parse CSV with inventory_id header")
  void parseInventoryIdCsvFile_WithInventoryIdHeader_ShouldParseSuccessfully() throws IOException {
    // Given
    String csvContent = "inventory_id\ninv123\ninv456\ninv789";
    InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

    // When
    List<InventoryIdRecord> records = csvParsingService.parseInventoryIdCsvFile(inputStream);

    // Then
    assertThat(records).hasSize(3);
    assertThat(records.get(0).getId()).isEqualTo("inv123");
    assertThat(records.get(0).getRow()).isEqualTo(2);
    assertThat(records.get(1).getId()).isEqualTo("inv456");
    assertThat(records.get(1).getRow()).isEqualTo(3);
    assertThat(records.get(2).getId()).isEqualTo("inv789");
    assertThat(records.get(2).getRow()).isEqualTo(4);
  }

  @Test
  @DisplayName(
      "parseInventoryIdCsvFile - Should throw exception when inventory_id header is missing")
  void parseInventoryIdCsvFile_WithInventoryIdNoUnderscore_ShouldThrowException() {
    // Given
    String csvContent = "inventoryid\ninv123\ninv456";
    InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

    // When & Then
    assertThatThrownBy(() -> csvParsingService.parseInventoryIdCsvFile(inputStream))
        .isInstanceOf(CsvUploadException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CSV_UPLOAD_INVALID_FILE)
        .hasMessageContaining("Required column header 'inventory_id' is missing");
  }

  @Test
  @DisplayName("parseInventoryIdCsvFile - Should throw exception when header not found")
  void parseInventoryIdCsvFile_WithNoHeader_ShouldThrowException() {
    // Given
    String csvContent = "inv123\ninv456\ninv789";
    InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

    // When & Then
    assertThatThrownBy(() -> csvParsingService.parseInventoryIdCsvFile(inputStream))
        .isInstanceOf(CsvUploadException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CSV_UPLOAD_INVALID_FILE)
        .hasMessageContaining("Required column header 'inventory_id' is missing");
  }

  @Test
  @DisplayName("parseInventoryIdCsvFile - Should skip empty lines")
  void parseInventoryIdCsvFile_WithEmptyLines_ShouldSkipThem() throws IOException {
    // Given
    String csvContent = "inventory_id\n\ninv123\n\ninv456\n";
    InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

    // When
    List<InventoryIdRecord> records = csvParsingService.parseInventoryIdCsvFile(inputStream);

    // Then
    assertThat(records).hasSize(2);
    assertThat(records.get(0).getId()).isEqualTo("inv123");
    assertThat(records.get(1).getId()).isEqualTo("inv456");
  }

  @Test
  @DisplayName("parseInventoryIdCsvFile - Should skip empty inventory IDs")
  void parseInventoryIdCsvFile_WithEmptyInventoryIds_ShouldSkipThem() throws IOException {
    // Given
    String csvContent = "inventory_id\ninv123\n\ninv456";
    InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

    // When
    List<InventoryIdRecord> records = csvParsingService.parseInventoryIdCsvFile(inputStream);

    // Then
    assertThat(records).hasSize(2);
    assertThat(records.get(0).getId()).isEqualTo("inv123");
    assertThat(records.get(1).getId()).isEqualTo("inv456");
  }

  @Test
  @DisplayName("parseInventoryIdCsvFile - Should handle CSV with multiple columns")
  void parseInventoryIdCsvFile_WithMultipleColumns_ShouldExtractCorrectColumn() throws IOException {
    // Given
    String csvContent =
        "inventory_id,name,country\ninv123,Inventory 1,USA\ninv456,Inventory 2,Canada";
    InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

    // When
    List<InventoryIdRecord> records = csvParsingService.parseInventoryIdCsvFile(inputStream);

    // Then
    assertThat(records).hasSize(2);
    assertThat(records.get(0).getId()).isEqualTo("inv123");
    assertThat(records.get(1).getId()).isEqualTo("inv456");
  }

  @Test
  @DisplayName("parseInventoryIdCsvFile - Should handle inventory_id in different column position")
  void parseInventoryIdCsvFile_WithInventoryIdInSecondColumn_ShouldExtractCorrectly()
      throws IOException {
    // Given
    String csvContent =
        "name,inventory_id,country\nInventory 1,inv123,USA\nInventory 2,inv456,Canada";
    InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

    // When
    List<InventoryIdRecord> records = csvParsingService.parseInventoryIdCsvFile(inputStream);

    // Then
    assertThat(records).hasSize(2);
    assertThat(records.get(0).getId()).isEqualTo("inv123");
    assertThat(records.get(1).getId()).isEqualTo("inv456");
  }

  @Test
  @DisplayName("parseInventoryIdCsvFile - Should trim whitespace from inventory IDs")
  void parseInventoryIdCsvFile_WithWhitespace_ShouldTrim() throws IOException {
    // Given
    String csvContent = "inventory_id\n  inv123  \n  inv456  ";
    InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

    // When
    List<InventoryIdRecord> records = csvParsingService.parseInventoryIdCsvFile(inputStream);

    // Then
    assertThat(records).hasSize(2);
    assertThat(records.get(0).getId()).isEqualTo("inv123");
    assertThat(records.get(1).getId()).isEqualTo("inv456");
  }

  @Test
  @DisplayName("parseInventoryIdCsvFile - Should handle case-insensitive header")
  void parseInventoryIdCsvFile_WithCaseInsensitiveHeader_ShouldParse() throws IOException {
    // Given
    String csvContent = "INVENTORY_ID\ninv123\ninv456";
    InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

    // When
    List<InventoryIdRecord> records = csvParsingService.parseInventoryIdCsvFile(inputStream);

    // Then
    assertThat(records).hasSize(2);
    assertThat(records.get(0).getId()).isEqualTo("inv123");
    assertThat(records.get(1).getId()).isEqualTo("inv456");
  }

  @Test
  @DisplayName("parseInventoryIdCsvFile - Should return empty list for empty CSV")
  void parseInventoryIdCsvFile_WithEmptyCsv_ShouldReturnEmptyList() throws IOException {
    // Given
    String csvContent = "";
    InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

    // When
    List<InventoryIdRecord> records = csvParsingService.parseInventoryIdCsvFile(inputStream);

    // Then
    assertThat(records).isEmpty();
  }

  @Test
  @DisplayName("parseInventoryIdCsvFile - Should return empty list for header only")
  void parseInventoryIdCsvFile_WithHeaderOnly_ShouldReturnEmptyList() throws IOException {
    // Given
    String csvContent = "inventory_id";
    InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

    // When
    List<InventoryIdRecord> records = csvParsingService.parseInventoryIdCsvFile(inputStream);

    // Then
    assertThat(records).isEmpty();
  }

  @Test
  @DisplayName("parseInventoryIdCsvFile - Should handle lines with insufficient columns")
  void parseInventoryIdCsvFile_WithInsufficientColumns_ShouldSkipLine() throws IOException {
    // Given
    String csvContent = "inventory_id\ninv123\n\ninv456";
    InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

    // When
    List<InventoryIdRecord> records = csvParsingService.parseInventoryIdCsvFile(inputStream);

    // Then
    assertThat(records).hasSize(2);
    assertThat(records.get(0).getId()).isEqualTo("inv123");
    assertThat(records.get(1).getId()).isEqualTo("inv456");
  }

  @Test
  @DisplayName("parseInventoryIdCsvFile - Should throw IOException for invalid stream")
  void parseInventoryIdCsvFile_WithInvalidStream_ShouldThrowIOException() {
    // Given
    InputStream invalidStream =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("Stream error");
          }
        };

    // When & Then
    assertThatThrownBy(() -> csvParsingService.parseInventoryIdCsvFile(invalidStream))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Stream error");
  }

  @Test
  @DisplayName("parseInventoryIdCsvFile - Should handle large CSV files")
  void parseInventoryIdCsvFile_WithLargeFile_ShouldParseAllRecords() throws IOException {
    // Given
    StringBuilder csvContent = new StringBuilder("inventory_id\n");
    for (int i = 1; i <= 1000; i++) {
      csvContent.append("inv").append(i).append("\n");
    }
    InputStream inputStream =
        new ByteArrayInputStream(csvContent.toString().getBytes(StandardCharsets.UTF_8));

    // When
    List<InventoryIdRecord> records = csvParsingService.parseInventoryIdCsvFile(inputStream);

    // Then
    assertThat(records).hasSize(1000);
    assertThat(records.get(0).getId()).isEqualTo("inv1");
    assertThat(records.get(999).getId()).isEqualTo("inv1000");
  }

  @Test
  @DisplayName("parseInventoryIdCsvFile - Should handle CSV with special characters in IDs")
  void parseInventoryIdCsvFile_WithSpecialCharacters_ShouldParse() throws IOException {
    // Given
    String csvContent = "inventory_id\ninv-123\ninv_456\ninv.789";
    InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

    // When
    List<InventoryIdRecord> records = csvParsingService.parseInventoryIdCsvFile(inputStream);

    // Then
    assertThat(records).hasSize(3);
    assertThat(records.get(0).getId()).isEqualTo("inv-123");
    assertThat(records.get(1).getId()).isEqualTo("inv_456");
    assertThat(records.get(2).getId()).isEqualTo("inv.789");
  }
}
