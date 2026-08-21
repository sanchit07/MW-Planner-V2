package com.mw.recommendation.engine.controller;

import com.mw.recommendation.engine.config.CsvImportProperties;
import com.mw.recommendation.engine.dto.ApiResponse;
import com.mw.recommendation.engine.dto.PaginatedRecommendationResponseDTO;
import com.mw.recommendation.engine.dto.csv.CsvImportResponse;
import com.mw.recommendation.engine.dto.csv.CsvMatchCriteria;
import com.mw.recommendation.engine.dto.csv.CsvVerifyResponse;
import com.mw.recommendation.engine.dto.csv.InventoryImportSummary;
import com.mw.recommendation.engine.service.InventoryCsvImportService;
import com.mw.recommendation.engine.service.InventoryCsvImportService.CsvDownload;
import com.mw.recommendation.engine.service.SecurityContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * CSV inventory import endpoints. A CSV with a single {@code inventory_id} column (holding
 * inventory <em>reference ids</em>) is resolved to full inventory objects and optionally retained
 * per campaign. {@code campaignId} path segments are the consumer's line-item id.
 *
 * <p>The engine only resolves + retains; the caller owns selection (persists to deals-api). All
 * retained-import operations are scoped to the caller's primary company (from the JWT).
 */
@RestController
@RequestMapping("/api/v1/recommendation")
@RequiredArgsConstructor
@EnableConfigurationProperties(CsvImportProperties.class)
@Slf4j
@Tag(name = "Inventory CSV Import", description = "Resolve and retain CSV inventory imports")
@SecurityRequirement(name = "bearerAuth")
public class InventoryCsvImportController {

  private final InventoryCsvImportService importService;
  private final SecurityContextService securityContextService;
  private final CsvImportProperties props;

  // ---- E1: verify (read-only) ----------------------------------------------

  @PostMapping(
      path = "/campaigns/{campaignId}/inventory-imports/verify",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Verify a CSV of inventory reference ids",
      description =
          "Parse + categorize (VALID/INVALID/DUPLICATE) and return matched inventory objects. "
              + "Read-only — nothing is persisted. "
              + "Optional resolution: single value (1920x1080) or comma-separated list "
              + "(1920x1080,720x1280) for OR match — inventory is VALID if it supports any.")
  public ResponseEntity<ApiResponse<CsvVerifyResponse>> verify(
      @PathVariable String campaignId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "country", required = false) String country,
      @RequestParam(value = "classification", required = false) String classification,
      @RequestParam(value = "mediaOwnerId", required = false) String mediaOwnerId,
      @RequestParam(value = "programmaticSupport", required = false) String programmaticSupport,
      @RequestParam(value = "dealType", required = false) String dealType,
      @RequestParam(value = "creativeType", required = false) String creativeType,
      @RequestParam(value = "adDuration", required = false) String adDuration,
      @RequestParam(value = "resolution", required = false) String resolution) {
    log.info("Verify inventory CSV for campaign {} ({} bytes)", campaignId, file.getSize());
    CsvVerifyResponse response =
        importService.verify(
            file,
            new CsvMatchCriteria(
                country,
                classification,
                mediaOwnerId,
                programmaticSupport,
                dealType,
                creativeType,
                adDuration,
                CsvMatchCriteria.parseResolutions(resolution)));
    return ResponseEntity.ok(
        ApiResponse.<CsvVerifyResponse>builder().success(true).data(response).build());
  }

  // ---- E2: import (verify + persist) ---------------------------------------

  @PostMapping(
      path = "/campaigns/{campaignId}/inventory-imports",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Import (verify + retain) a CSV of inventory reference ids",
      description = "Verifies then persists the matched ref ids as a reusable import.")
  public ResponseEntity<ApiResponse<CsvImportResponse>> importCsv(
      @PathVariable String campaignId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "country", required = false) String country,
      @RequestParam(value = "classification", required = false) String classification,
      @RequestParam(value = "mediaOwnerId", required = false) String mediaOwnerId,
      @RequestParam(value = "programmaticSupport", required = false) String programmaticSupport,
      @RequestParam(value = "dealType", required = false) String dealType,
      @RequestParam(value = "creativeType", required = false) String creativeType,
      @RequestParam(value = "adDuration", required = false) String adDuration,
      @RequestParam(value = "resolution", required = false) String resolution) {
    String companyId = securityContextService.getPrimaryCompanyId();
    CsvImportResponse response =
        importService.importCsv(
            companyId,
            campaignId,
            file,
            new CsvMatchCriteria(
                country,
                classification,
                mediaOwnerId,
                programmaticSupport,
                dealType,
                creativeType,
                adDuration,
                CsvMatchCriteria.parseResolutions(resolution)));
    URI location = URI.create("/api/v1/recommendation/inventory-imports/" + response.importId());
    return ResponseEntity.created(location)
        .body(ApiResponse.<CsvImportResponse>builder().success(true).data(response).build());
  }

  // ---- E3: list retained imports -------------------------------------------

  @GetMapping("/campaigns/{campaignId}/inventory-imports")
  @Operation(summary = "List retained CSV imports for a campaign (newest first)")
  public ResponseEntity<ApiResponse<Page<InventoryImportSummary>>> list(
      @PathVariable String campaignId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    String companyId = securityContextService.getPrimaryCompanyId();
    int cappedSize = Math.max(1, Math.min(size, props.maxPageSize()));
    Pageable pageable = PageRequest.of(page, cappedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<InventoryImportSummary> response = importService.list(companyId, campaignId, pageable);
    return ResponseEntity.ok(
        ApiResponse.<Page<InventoryImportSummary>>builder().success(true).data(response).build());
  }

  // ---- E4: paged inventories for an import ----------------------------------

  @GetMapping("/inventory-imports/{importId}/inventories")
  @Operation(summary = "Re-resolve a page of a retained import's inventories (live)")
  public ResponseEntity<ApiResponse<PaginatedRecommendationResponseDTO>> getImportInventories(
      @PathVariable String importId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    String companyId = securityContextService.getPrimaryCompanyId();
    PaginatedRecommendationResponseDTO response =
        importService.getImportInventories(companyId, importId, page, size);
    return ResponseEntity.ok(
        ApiResponse.<PaginatedRecommendationResponseDTO>builder()
            .success(true)
            .data(response)
            .build());
  }

  // ---- E5: use (re-resolve all stored ref ids) ------------------------------

  @PostMapping("/inventory-imports/{importId}/use")
  @Operation(summary = "Re-resolve a retained import's stored ref ids live")
  public ResponseEntity<ApiResponse<CsvVerifyResponse>> useImport(@PathVariable String importId) {
    String companyId = securityContextService.getPrimaryCompanyId();
    CsvVerifyResponse response = importService.useImport(companyId, importId);
    return ResponseEntity.ok(
        ApiResponse.<CsvVerifyResponse>builder().success(true).data(response).build());
  }

  // ---- E6: download (regenerated CSV) ---------------------------------------

  @GetMapping("/inventory-imports/{importId}/download")
  @Operation(summary = "Download a CSV regenerated from the retained ref ids")
  public ResponseEntity<byte[]> download(@PathVariable String importId) {
    String companyId = securityContextService.getPrimaryCompanyId();
    CsvDownload download = importService.download(companyId, importId);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("text/csv"));
    headers.setContentDisposition(
        ContentDisposition.attachment().filename(download.fileName()).build());
    return new ResponseEntity<>(download.content(), headers, HttpStatus.OK);
  }

  // ---- E7: delete -----------------------------------------------------------

  @DeleteMapping("/inventory-imports/{importId}")
  @Operation(summary = "Delete a retained CSV import")
  public ResponseEntity<Void> delete(@PathVariable String importId) {
    String companyId = securityContextService.getPrimaryCompanyId();
    importService.delete(companyId, importId);
    return ResponseEntity.noContent().build();
  }
}
