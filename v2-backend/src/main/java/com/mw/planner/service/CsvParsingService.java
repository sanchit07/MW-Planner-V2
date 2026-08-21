package com.mw.planner.service;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.csv.CsvUploadException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Service for parsing CSV files containing inventory reference IDs and SOV values. */
@Service
@Slf4j
public class CsvParsingService {

  /**
   * Parse CSV file and extract inventory reference IDs.
   *
   * @param inputStream CSV file input stream
   * @return List of InventoryIdRecord objects with id (referenceId) and row number
   * @throws IOException if file cannot be read
   */
  public List<InventoryIdRecord> parseInventoryIdCsvFile(InputStream inputStream)
      throws IOException {
    List<InventoryIdRecord> records = new ArrayList<>();

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      int lineNumber = 0;
      int inventoryIdColumnIndex = -1;

      while ((line = reader.readLine()) != null) {
        lineNumber++;

        // Skip empty lines
        if (line.trim().isEmpty()) {
          continue;
        }

        String[] parts = line.split(",");

        // Detect header line
        if (lineNumber == 1) {
          for (int i = 0; i < parts.length; i++) {
            String header = parts[i].trim().toLowerCase();
            if (header.equals("inventory_id")) {
              inventoryIdColumnIndex = i;
              log.debug("Found inventory_id header at column index: {}", i);
              break;
            }
          }
          if (inventoryIdColumnIndex == -1) {
            log.warn("Header 'inventory_id' not found in CSV file");
            throw new CsvUploadException(
                ErrorCode.CSV_UPLOAD_INVALID_FILE,
                "CSV file is not valid. Required column header 'inventory_id' is missing.");
          }
          continue;
        }

        // Parse data rows
        if (parts.length > inventoryIdColumnIndex) {
          String referenceId = parts[inventoryIdColumnIndex].trim();
          if (!referenceId.isEmpty()) {
            records.add(InventoryIdRecord.builder().id(referenceId).row(lineNumber).build());
          } else {
            log.warn("Empty reference ID at line {}", lineNumber);
          }
        } else {
          log.warn("Line {} does not have enough columns", lineNumber);
        }
      }
    }

    log.info("Parsed {} inventory reference ID records from CSV file", records.size());
    return records;
  }

  /** Record for inventory ID with row number. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InventoryIdRecord {
    private String id;
    private Integer row;
  }
}
