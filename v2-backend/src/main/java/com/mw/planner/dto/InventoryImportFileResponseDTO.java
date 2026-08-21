package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for inventory import file information")
public class InventoryImportFileResponseDTO {

  @Schema(description = "Unique import file identifier", example = "import_123456")
  private String id;

  @Schema(description = "Name of the uploaded CSV file", example = "inventory_import.csv")
  private String fileName;

  @JsonProperty("inventoryCount")
  @Schema(description = "Count of inventory reference IDs in the import", example = "150")
  private Integer inventoryRefIdCount;

  @Schema(description = "User who created the import", example = "user@example.com")
  private String createdBy;

  @JsonProperty("createdAt")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Schema(description = "Import file creation timestamp", example = "2024-01-15 10:30:00")
  private LocalDateTime createdAt;
}
