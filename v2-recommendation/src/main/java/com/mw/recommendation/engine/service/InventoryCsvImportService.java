package com.mw.recommendation.engine.service;

import com.mw.recommendation.engine.config.CsvImportProperties;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.SelectInventoryImports;
import com.mw.recommendation.engine.dto.PaginatedRecommendationResponseDTO;
import com.mw.recommendation.engine.dto.PaginatedRecommendationResponseDTO.RecommendedInventory;
import com.mw.recommendation.engine.dto.csv.CsvImportResponse;
import com.mw.recommendation.engine.dto.csv.CsvMatchCriteria;
import com.mw.recommendation.engine.dto.csv.CsvRowResult;
import com.mw.recommendation.engine.dto.csv.CsvRowType;
import com.mw.recommendation.engine.dto.csv.CsvVerifyResponse;
import com.mw.recommendation.engine.dto.csv.InventoryImportSummary;
import com.mw.recommendation.engine.enums.ErrorCode;
import com.mw.recommendation.engine.exception.BaseException;
import com.mw.recommendation.engine.mapper.InventoryItemMapper;
import com.mw.recommendation.engine.repository.InventoryRepository;
import com.mw.recommendation.engine.repository.SelectInventoryImportsRepository;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Resolves and retains CSV inventory imports for a campaign (line item).
 *
 * <p>The uploaded CSV has a single {@code inventory_id} column that actually holds inventory
 * <em>reference ids</em>. Reference ids are resolved against {@link InventoryRepository} and mapped
 * to the same {@code RecommendedInventory} shape as browse/results (via {@link
 * InventoryItemMapper}) so the frontend can feed matched objects straight into its selection.
 * Selection itself is NOT owned here — the engine only resolves and retains; the caller persists
 * selection to deals-api.
 *
 * <p>All persisted-import operations are tenant-scoped by {@code companyId} (from the caller's
 * JWT); a mismatch surfaces as {@link ErrorCode#IMPORT_NOT_FOUND} rather than leaking existence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryCsvImportService {

  private static final String HEADER = "inventory_id";

  private final InventoryRepository inventoryRepository;
  private final SelectInventoryImportsRepository importRepository;
  private final InventoryItemMapper inventoryItemMapper;
  private final CsvImportProperties props;

  /** Regenerated CSV download payload (we do not retain the original bytes). */
  public record CsvDownload(String fileName, byte[] content) {}

  // ---- E1: verify (read-only, no persistence) -------------------------------

  /**
   * Parse and categorize a CSV of inventory reference ids without persisting anything. Optional
   * {@code country} filters out inventories that are not in that country.
   */
  public CsvVerifyResponse verify(MultipartFile file, CsvMatchCriteria criteria) {
    List<String> referenceIds = parse(file);
    return categorize(referenceIds, criteria);
  }

  // ---- E2: import (verify + persist) ----------------------------------------

  /** Verify then retain the import (VALID matched ref ids only) for the given tenant + campaign. */
  public CsvImportResponse importCsv(
      String companyId, String campaignId, MultipartFile file, CsvMatchCriteria criteria) {
    CsvVerifyResponse result = verify(file, criteria);

    List<String> matchedRefIds =
        result.rows().stream()
            .filter(r -> r.type() == CsvRowType.VALID)
            .map(CsvRowResult::referenceId)
            .toList();

    SelectInventoryImports saved =
        importRepository.save(
            SelectInventoryImports.builder()
                .companyId(companyId)
                .campaignId(campaignId)
                .fileName(file.getOriginalFilename())
                .inventoryRefIds(matchedRefIds)
                .countryName(criteria.country())
                .classification(criteria.classification())
                .mediaOwnerId(criteria.mediaOwnerId())
                .programmaticSupport(criteria.programmaticSupport())
                .build());

    log.info(
        "Persisted inventory import {} (company={}, campaign={}, matched={})",
        saved.getId(),
        companyId,
        campaignId,
        matchedRefIds.size());

    return new CsvImportResponse(saved.getId(), result);
  }

  // ---- E3: list -------------------------------------------------------------

  /** List retained imports for a campaign (tenant-scoped), newest first per the compound index. */
  public Page<InventoryImportSummary> list(String companyId, String campaignId, Pageable pageable) {
    return importRepository
        .findByCompanyIdAndCampaignId(companyId, campaignId, pageable)
        .map(
            d ->
                new InventoryImportSummary(
                    d.getId(),
                    d.getCampaignId(),
                    d.getFileName(),
                    d.getCountryName(),
                    d.getInventoryRefIds() == null ? 0 : d.getInventoryRefIds().size(),
                    d.getCreatedBy(),
                    d.getCreatedAt()));
  }

  // ---- E4: paged inventories for an import ----------------------------------

  /** Re-resolve a single page of a retained import's ref ids into full inventory objects. */
  public PaginatedRecommendationResponseDTO getImportInventories(
      String companyId, String importId, int page, int size) {
    SelectInventoryImports doc = requireImport(companyId, importId);
    List<String> refIds = doc.getInventoryRefIds() == null ? List.of() : doc.getInventoryRefIds();

    int pageSize = Math.max(1, Math.min(size, props.maxPageSize()));
    int from = Math.min(page * pageSize, refIds.size());
    int to = Math.min(from + pageSize, refIds.size());
    List<String> pageRefs = from < to ? refIds.subList(from, to) : List.of();

    Map<String, Inventory> byRef = resolve(pageRefs);
    List<RecommendedInventory> items = new ArrayList<>();
    for (String ref : pageRefs) {
      Inventory inv = byRef.get(ref);
      if (inv != null) {
        items.add(
            inventoryItemMapper.toRecommendedInventory(
                inv, InventoryItemMapper.DEFAULT_TOTAL_DAYS, null, null));
      }
    }

    int totalElements = refIds.size();
    int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
    PaginatedRecommendationResponseDTO.PaginationInfo pagination =
        PaginatedRecommendationResponseDTO.PaginationInfo.builder()
            .page(page)
            .size(pageSize)
            .totalElements((long) totalElements)
            .totalPages(totalPages)
            .hasNext(page + 1 < totalPages)
            .hasPrevious(page > 0)
            .build();

    return PaginatedRecommendationResponseDTO.builder()
        .campaignId(doc.getCampaignId())
        .companyId(companyId)
        .recommendations(items)
        .pagination(pagination)
        .build();
  }

  // ---- E5: use (re-resolve all stored ref ids) ------------------------------

  /** Re-resolve a retained import's stored ref ids live (archived inventories → INVALID). */
  public CsvVerifyResponse useImport(String companyId, String importId) {
    SelectInventoryImports doc = requireImport(companyId, importId);
    List<String> refIds = doc.getInventoryRefIds() == null ? List.of() : doc.getInventoryRefIds();
    return categorize(
        refIds,
        new CsvMatchCriteria(
            doc.getCountryName(),
            doc.getClassification(),
            doc.getMediaOwnerId(),
            doc.getProgrammaticSupport()));
  }

  // ---- E6: download (regenerated from ref ids) ------------------------------

  /** Regenerate a canonical single-column CSV from the retained ref ids (original not stored). */
  public CsvDownload download(String companyId, String importId) {
    SelectInventoryImports doc = requireImport(companyId, importId);
    StringBuilder sb = new StringBuilder(HEADER).append("\n");
    if (doc.getInventoryRefIds() != null) {
      for (String ref : doc.getInventoryRefIds()) {
        sb.append(ref).append("\n");
      }
    }
    String fileName = doc.getFileName() != null ? doc.getFileName() : "inventory-import.csv";
    return new CsvDownload(fileName, sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  // ---- E7: delete -----------------------------------------------------------

  /** Delete a retained import (tenant-scoped). */
  public void delete(String companyId, String importId) {
    requireImport(companyId, importId);
    importRepository.deleteByIdAndCompanyId(importId, companyId);
  }

  // ---- internals ------------------------------------------------------------

  private SelectInventoryImports requireImport(String companyId, String importId) {
    return importRepository
        .findByIdAndCompanyId(importId, companyId)
        .orElseThrow(
            () ->
                new BaseException(
                    ErrorCode.IMPORT_NOT_FOUND, "Inventory import not found: " + importId));
  }

  /**
   * Parse the CSV bytes into an ordered list of trimmed {@code inventory_id} cell values (one per
   * data row). Enforces file-size and row-count caps, header presence, and non-emptiness.
   */
  private List<String> parse(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BaseException(ErrorCode.EMPTY_FILE, "The uploaded CSV is empty");
    }
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new BaseException(ErrorCode.INVALID_FILE, "Unable to read the uploaded file", e);
    }
    if (bytes.length > props.maxFileBytes()) {
      throw new BaseException(
          ErrorCode.FILE_TOO_LARGE,
          "The uploaded file is "
              + bytes.length
              + " bytes, which exceeds the maximum allowed size of "
              + props.maxFileBytes()
              + " bytes.");
    }

    String content = new String(bytes, StandardCharsets.UTF_8);
    if (content.startsWith("﻿")) {
      content = content.substring(1); // strip UTF-8 BOM
    }

    try (CSVReader reader = new CSVReader(new StringReader(content))) {
      String[] header = reader.readNext();
      if (header == null) {
        throw new BaseException(ErrorCode.EMPTY_FILE, "The uploaded CSV is empty");
      }
      int idx = headerIndex(header);
      if (idx < 0) {
        throw new BaseException(
            ErrorCode.MISSING_HEADER, "CSV must contain an '" + HEADER + "' column");
      }

      // Read every data row first so the row-count error can report the ACTUAL number found
      // (not just the limit). The byte-size cap above bounds how much we read here.
      List<String> refs = new ArrayList<>();
      String[] row;
      while ((row = reader.readNext()) != null) {
        String value = idx < row.length && row[idx] != null ? row[idx].trim() : "";
        // Trim out empty rows (blank lines / trailing newlines) — they are not real
        // reference ids, so they must not count toward the cap nor become "missing" errors.
        if (value.isEmpty()) {
          continue;
        }
        refs.add(value);
      }
      if (refs.isEmpty()) {
        throw new BaseException(ErrorCode.EMPTY_FILE, "The uploaded CSV has no data rows");
      }
      if (refs.size() > props.maxRows()) {
        // Distinct code from the byte-size cap so a small-but-too-many-rows file is not
        // mislabelled as "file size exceeded". Full sentence with the actual count in details.
        throw new BaseException(
            ErrorCode.TOO_MANY_ROWS,
            "The uploaded CSV contains "
                + refs.size()
                + " reference ids, which exceeds the maximum allowed of "
                + props.maxRows()
                + ". Please reduce the number of reference ids and try again.");
      }
      return refs;
    } catch (IOException | CsvValidationException e) {
      throw new BaseException(ErrorCode.INVALID_FILE, "The uploaded file is not a valid CSV", e);
    }
  }

  private int headerIndex(String[] header) {
    for (int i = 0; i < header.length; i++) {
      String h = header[i] == null ? "" : header[i].replace("﻿", "").trim();
      if (h.equalsIgnoreCase(HEADER)) {
        return i;
      }
    }
    return -1;
  }

  private Map<String, Inventory> resolve(List<String> referenceIds) {
    List<String> distinct =
        referenceIds.stream().filter(v -> v != null && !v.isBlank()).distinct().toList();
    Map<String, Inventory> byRef = new HashMap<>();
    if (!distinct.isEmpty()) {
      for (Inventory inv : inventoryRepository.findByReferenceIdIn(distinct)) {
        byRef.putIfAbsent(inv.getReferenceId(), inv);
      }
    }
    return byRef;
  }

  /**
   * Categorize each reference id (file order, 1-based) as VALID / INVALID / DUPLICATE and collect
   * the deduped VALID inventory objects. Optional line-item context in {@link CsvMatchCriteria} —
   * country ({@code locationHierarchy.countryName}), classification (Classic/Digital), mediaOwnerId
   * and programmaticSupport — is matched when given; a mismatch marks the row INVALID. All criteria
   * are passed by the caller (the engine has no line-item context).
   */
  /**
   * Parse a caller-supplied numeric string (e.g. ad duration) to an Integer, or null if blank/bad.
   */
  private static Integer parseIntOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Integer.valueOf(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private CsvVerifyResponse categorize(List<String> referenceIds, CsvMatchCriteria criteria) {
    Map<String, Inventory> byRef = resolve(referenceIds);

    List<CsvRowResult> rows = new ArrayList<>();
    List<RecommendedInventory> matched = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    int valid = 0;
    int invalid = 0;
    int duplicate = 0;
    String country = criteria.country();
    String classification = criteria.classification();
    String mediaOwnerId = criteria.mediaOwnerId();
    boolean hasCountry = country != null && !country.isBlank();
    boolean hasClassification = classification != null && !classification.isBlank();
    boolean hasMediaOwner = mediaOwnerId != null && !mediaOwnerId.isBlank();
    boolean requireProgrammatic = "YES".equalsIgnoreCase(criteria.programmaticSupport());
    String dealType = criteria.dealType();
    boolean hasDealType = dealType != null && !dealType.isBlank();
    String creativeType = criteria.creativeType();
    boolean hasCreativeType = creativeType != null && !creativeType.isBlank();
    Integer adDurationSec = parseIntOrNull(criteria.adDuration());
    // Normalize resolutions: drop null/blank; empty after normalize → no resolution check.
    List<String> resolutions =
        criteria.resolutions() == null
            ? List.of()
            : criteria.resolutions().stream()
                .filter(r -> r != null && !r.isBlank())
                .map(String::trim)
                .toList();
    boolean hasResolution = !resolutions.isEmpty();

    for (int i = 0; i < referenceIds.size(); i++) {
      String ref = referenceIds.get(i);
      int rowNum = i + 1;

      if (ref == null || ref.isBlank()) {
        rows.add(
            new CsvRowResult(rowNum, "", CsvRowType.INVALID, List.of("Reference id is missing")));
        invalid++;
        continue;
      }
      if (!seen.add(ref)) {
        rows.add(
            new CsvRowResult(
                rowNum, ref, CsvRowType.DUPLICATE, List.of("Duplicate reference id in file")));
        duplicate++;
        continue;
      }

      Inventory inv = byRef.get(ref);
      if (inv == null) {
        rows.add(
            new CsvRowResult(rowNum, ref, CsvRowType.INVALID, List.of("Reference id not found")));
        invalid++;
        continue;
      }

      // Collect EVERY reason the resolved inventory doesn't fit the line item — the caller shows
      // all of them under the ref id, not just the first failing check.
      List<String> reasons = new ArrayList<>();
      if (hasCountry) {
        String invCountry =
            inv.getLocationHierarchy() != null ? inv.getLocationHierarchy().getCountryName() : null;
        if (invCountry == null || !country.equalsIgnoreCase(invCountry)) {
          reasons.add("Inventory is not in " + country);
        }
      }
      if (hasClassification
          && (inv.getClassification() == null
              || !classification.equalsIgnoreCase(inv.getClassification()))) {
        reasons.add("Inventory is not " + classification);
      }
      if (hasMediaOwner && !mediaOwnerId.equals(inv.getMediaOwnerId())) {
        reasons.add("Inventory does not belong to the selected media owner");
      }
      // Programmatic line items require the inventory to offer a programmatic deal type. When the
      // line item's specific deal type is known, the inventory must offer THAT type
      // (case-insensitive)
      // — not merely any programmatic type (browse's programmaticSupport=YES rule is the broad
      // one).
      if (requireProgrammatic) {
        List<String> invDealTypes = inv.getProgrammaticDealTypes();
        if (invDealTypes == null || invDealTypes.isEmpty()) {
          reasons.add("Inventory does not support programmatic");
        } else if (hasDealType
            && invDealTypes.stream().noneMatch(t -> t != null && t.equalsIgnoreCase(dealType))) {
          reasons.add("Inventory does not support the " + dealType + " deal type");
        }
      }

      // Creative type — the inventory must offer a matching creativeFormats.creativeType
      // ("video"/"image"/"audio"), matched case-insensitively.
      if (hasCreativeType
          && (inv.getCreativeFormats() == null
              || inv.getCreativeFormats().stream()
                  .noneMatch(
                      cf ->
                          cf.getCreativeType() != null
                              && cf.getCreativeType().equalsIgnoreCase(creativeType)))) {
        reasons.add("Inventory does not support " + creativeType + " creatives");
      }

      // Ad duration — the inventory must sell a spot at this duration. Primary source is
      // prices.durationSeconds (the selling-terms field browse filters on); when NO price row
      // matches, fall back to digitalFields.spotDuration. Rejected only when NEITHER matches.
      if (adDurationSec != null) {
        boolean matchesPriceDuration =
            inv.getPrices() != null
                && inv.getPrices().stream()
                    .anyMatch(p -> adDurationSec.equals(p.getDurationSeconds()));
        boolean matchesSpotDuration =
            inv.getDigitalFields() != null
                && adDurationSec.equals(inv.getDigitalFields().getSpotDuration());
        if (!matchesPriceDuration && !matchesSpotDuration) {
          reasons.add("Inventory does not support a " + adDurationSec + "s ad duration");
        }
      }

      // Resolution — inventory must have a panel matching ANY requested "WxH" (OR, same as scored
      // recommendation fetch). Rejected only when none of the requested resolutions match.
      if (hasResolution) {
        boolean anyResolutionMatches =
            inv.getPanels() != null
                && resolutions.stream()
                    .anyMatch(
                        res ->
                            inv.getPanels().stream()
                                .anyMatch(
                                    pn ->
                                        pn.getPixelWidth() != null
                                            && pn.getPixelHeight() != null
                                            && res.equalsIgnoreCase(
                                                pn.getPixelWidth() + "x" + pn.getPixelHeight())));
        if (!anyResolutionMatches) {
          if (resolutions.size() == 1) {
            reasons.add("Inventory does not support the " + resolutions.get(0) + " resolution");
          } else {
            reasons.add(
                "Inventory does not support any of the requested resolutions: "
                    + String.join(", ", resolutions));
          }
        }
      }

      if (!reasons.isEmpty()) {
        rows.add(new CsvRowResult(rowNum, ref, CsvRowType.INVALID, reasons));
        invalid++;
        continue;
      }

      rows.add(new CsvRowResult(rowNum, ref, CsvRowType.VALID, List.of("Matched")));
      valid++;
      matched.add(
          inventoryItemMapper.toRecommendedInventory(
              inv, InventoryItemMapper.DEFAULT_TOTAL_DAYS, null, null));
    }

    return new CsvVerifyResponse(
        rows, matched, country, referenceIds.size(), valid, invalid, duplicate);
  }
}
