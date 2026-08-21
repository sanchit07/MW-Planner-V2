package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.config.CsvImportProperties;
import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.SelectInventoryImports;
import com.mw.recommendation.engine.dto.PaginatedRecommendationResponseDTO;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unit spec for {@link InventoryCsvImportService}. Drives parsing, VALID/INVALID/DUPLICATE
 * categorization, matched-object shape, persistence, re-resolution, download regeneration, and
 * tenant scoping. No Spring context / no DB — repository + mapper are mocked.
 */
@ExtendWith(MockitoExtension.class)
class InventoryCsvImportServiceTest {

  private static final String COMPANY = "company-1";
  private static final String CAMPAIGN = "line-item-1";

  @Mock private InventoryRepository inventoryRepository;
  @Mock private SelectInventoryImportsRepository importRepository;
  @Mock private InventoryItemMapper inventoryItemMapper;

  private InventoryCsvImportService service;

  @BeforeEach
  void setUp() {
    CsvImportProperties props = new CsvImportProperties(5000, 5_242_880L, 100);
    service =
        new InventoryCsvImportService(
            inventoryRepository, importRepository, inventoryItemMapper, props);
  }

  // ---- helpers ---------------------------------------------------------------

  private static MultipartFile csv(String content) {
    return new MockMultipartFile(
        "file", "inventories.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
  }

  private static Inventory inventory(String inventoryId, String referenceId, String country) {
    return inventory(inventoryId, referenceId, country, null, null);
  }

  private static Inventory inventory(
      String inventoryId,
      String referenceId,
      String country,
      String classification,
      String mediaOwnerId) {
    Inventory inv = new Inventory();
    inv.setInventoryId(inventoryId);
    inv.setReferenceId(referenceId);
    inv.setLocationHierarchy(Inventory.LocationHierarchy.builder().countryName(country).build());
    inv.setClassification(classification);
    inv.setMediaOwnerId(mediaOwnerId);
    return inv;
  }

  /** Mapper stub: echo inventoryId/referenceId so matched-object assertions are meaningful. */
  private void stubMapperEcho() {
    when(inventoryItemMapper.toRecommendedInventory(any(Inventory.class), anyLong(), any(), any()))
        .thenAnswer(
            i -> {
              Inventory inv = i.getArgument(0);
              return PaginatedRecommendationResponseDTO.RecommendedInventory.builder()
                  .inventoryId(inv.getInventoryId())
                  .referenceId(inv.getReferenceId())
                  .build();
            });
  }

  private static void assertErrorCode(ErrorCode expected, Executable action) {
    BaseException ex = assertThrows(BaseException.class, action);
    assertEquals(expected, ex.getErrorCode());
  }

  // ---- verify: parsing + header ---------------------------------------------

  @Test
  void verify_missingInventoryIdHeader_throwsMissingHeader() {
    assertErrorCode(
        ErrorCode.MISSING_HEADER,
        () -> service.verify(csv("wrong_col\nR1\nR2"), CsvMatchCriteria.none()));
    verifyNoInteractions(inventoryRepository);
  }

  @Test
  void verify_headerOnlyNoDataRows_throwsEmptyFile() {
    assertErrorCode(
        ErrorCode.EMPTY_FILE, () -> service.verify(csv("inventory_id\n"), CsvMatchCriteria.none()));
  }

  @Test
  void verify_completelyEmptyFile_throwsEmptyFile() {
    assertErrorCode(ErrorCode.EMPTY_FILE, () -> service.verify(csv(""), CsvMatchCriteria.none()));
  }

  @Test
  void verify_bomAndUppercaseHeader_isAccepted() {
    when(inventoryRepository.findByReferenceIdIn(anyList()))
        .thenReturn(List.of(inventory("i1", "R1", "Japan")));
    stubMapperEcho();

    // UTF-8 BOM + uppercase + surrounding whitespace on the header cell
    CsvVerifyResponse res = service.verify(csv("﻿ INVENTORY_ID \nR1"), CsvMatchCriteria.none());

    assertEquals(1, res.totalRows());
    assertEquals(1, res.validCount());
    assertEquals(CsvRowType.VALID, res.rows().get(0).type());
  }

  // ---- verify: categorization ------------------------------------------------

  @Test
  void verify_allValid_returnsMatchedInventoryObjects() {
    when(inventoryRepository.findByReferenceIdIn(anyList()))
        .thenReturn(List.of(inventory("i1", "R1", "Japan"), inventory("i2", "R2", "Japan")));
    stubMapperEcho();

    CsvVerifyResponse res = service.verify(csv("inventory_id\nR1\nR2"), CsvMatchCriteria.none());

    assertEquals(2, res.totalRows());
    assertEquals(2, res.validCount());
    assertEquals(0, res.invalidCount());
    assertEquals(0, res.duplicateCount());
    assertEquals(2, res.matchedInventories().size());
    assertEquals(
        List.of("i1", "i2"),
        res.matchedInventories().stream().map(m -> m.getInventoryId()).toList());
    assertEquals(1, res.rows().get(0).row());
    assertEquals("R1", res.rows().get(0).referenceId());
  }

  @Test
  void verify_unknownReferenceId_isInvalid() {
    when(inventoryRepository.findByReferenceIdIn(anyList()))
        .thenReturn(List.of(inventory("i1", "R1", "Japan")));
    stubMapperEcho();

    CsvVerifyResponse res =
        service.verify(csv("inventory_id\nR1\nR_MISSING"), CsvMatchCriteria.none());

    assertEquals(1, res.validCount());
    assertEquals(1, res.invalidCount());
    assertEquals(CsvRowType.INVALID, res.rows().get(1).type());
    assertEquals(1, res.matchedInventories().size());
  }

  @Test
  void verify_countryMismatch_isInvalid() {
    when(inventoryRepository.findByReferenceIdIn(anyList()))
        .thenReturn(List.of(inventory("i1", "R1", "Singapore")));

    CsvVerifyResponse res =
        service.verify(csv("inventory_id\nR1"), new CsvMatchCriteria("Japan", null, null, null));

    assertEquals(0, res.validCount());
    assertEquals(1, res.invalidCount());
    assertEquals(CsvRowType.INVALID, res.rows().get(0).type());
    assertTrue(res.matchedInventories().isEmpty());
  }

  @Test
  void verify_countryMatchesCaseInsensitive_isValid() {
    when(inventoryRepository.findByReferenceIdIn(anyList()))
        .thenReturn(List.of(inventory("i1", "R1", "Japan")));
    stubMapperEcho();

    CsvVerifyResponse res =
        service.verify(csv("inventory_id\nR1"), new CsvMatchCriteria("japan", null, null, null));

    assertEquals(1, res.validCount());
  }

  // ---- verify: classification (Classic/Digital) ------------------------------

  @Test
  void verify_classificationMismatch_isInvalid() {
    when(inventoryRepository.findByReferenceIdIn(anyList()))
        .thenReturn(List.of(inventory("i1", "R1", "Japan", "Classic", "owner-1")));

    CsvVerifyResponse res =
        service.verify(csv("inventory_id\nR1"), new CsvMatchCriteria(null, "Digital", null, null));

    assertEquals(0, res.validCount());
    assertEquals(1, res.invalidCount());
    assertEquals(CsvRowType.INVALID, res.rows().get(0).type());
    assertTrue(res.matchedInventories().isEmpty());
  }

  @Test
  void verify_classificationMatchesCaseInsensitive_isValid() {
    when(inventoryRepository.findByReferenceIdIn(anyList()))
        .thenReturn(List.of(inventory("i1", "R1", "Japan", "Digital", "owner-1")));
    stubMapperEcho();

    CsvVerifyResponse res =
        service.verify(csv("inventory_id\nR1"), new CsvMatchCriteria(null, "digital", null, null));

    assertEquals(1, res.validCount());
  }

  // ---- verify: media owner ---------------------------------------------------

  @Test
  void verify_mediaOwnerMismatch_isInvalid() {
    when(inventoryRepository.findByReferenceIdIn(anyList()))
        .thenReturn(List.of(inventory("i1", "R1", "Japan", "Digital", "owner-1")));

    CsvVerifyResponse res =
        service.verify(csv("inventory_id\nR1"), new CsvMatchCriteria(null, null, "owner-2", null));

    assertEquals(0, res.validCount());
    assertEquals(1, res.invalidCount());
    assertEquals(CsvRowType.INVALID, res.rows().get(0).type());
    assertTrue(res.matchedInventories().isEmpty());
  }

  @Test
  void verify_mediaOwnerMatches_isValid() {
    when(inventoryRepository.findByReferenceIdIn(anyList()))
        .thenReturn(List.of(inventory("i1", "R1", "Japan", "Digital", "owner-1")));
    stubMapperEcho();

    CsvVerifyResponse res =
        service.verify(csv("inventory_id\nR1"), new CsvMatchCriteria(null, null, "owner-1", null));

    assertEquals(1, res.validCount());
  }

  @Test
  void verify_country_classification_and_mediaOwner_enforcedTogether() {
    when(inventoryRepository.findByReferenceIdIn(anyList()))
        .thenReturn(List.of(inventory("i1", "R1", "Japan", "Digital", "owner-1")));
    stubMapperEcho();

    CsvVerifyResponse res =
        service.verify(
            csv("inventory_id\nR1"), new CsvMatchCriteria("Japan", "Digital", "owner-1", null));

    assertEquals(1, res.validCount());
    assertEquals(0, res.invalidCount());
  }

  @Test
  void verify_multipleCriteriaFail_reportsEveryReason() {
    // Inventory is Japan/Digital/owner-1 & non-programmatic; the line item demands
    // Malaysia/Classic/owner-2/programmatic → the row must carry ALL four reasons.
    when(inventoryRepository.findByReferenceIdIn(anyList()))
        .thenReturn(List.of(inventory("i1", "R1", "Japan", "Digital", "owner-1")));

    CsvVerifyResponse res =
        service.verify(
            csv("inventory_id\nR1"), new CsvMatchCriteria("Malaysia", "Classic", "owner-2", "YES"));

    assertEquals(0, res.validCount());
    assertEquals(1, res.invalidCount());
    CsvRowResult row = res.rows().get(0);
    assertEquals(CsvRowType.INVALID, row.type());
    assertEquals(4, row.messages().size(), "one message per failed check");
    assertTrue(row.messages().contains("Inventory is not in Malaysia"));
    assertTrue(row.messages().contains("Inventory is not Classic"));
    assertTrue(row.messages().contains("Inventory does not belong to the selected media owner"));
    assertTrue(row.messages().contains("Inventory does not support programmatic"));
  }

  @Test
  void verify_notFound_singleReasonIsNotFound() {
    // A missing inventory reports ONLY "not found" — never the criteria reasons.
    when(inventoryRepository.findByReferenceIdIn(anyList())).thenReturn(List.of());

    CsvVerifyResponse res =
        service.verify(
            csv("inventory_id\nR1"), new CsvMatchCriteria("Malaysia", "Classic", "owner-2", "YES"));

    CsvRowResult row = res.rows().get(0);
    assertEquals(CsvRowType.INVALID, row.type());
    assertEquals(List.of("Reference id not found"), row.messages());
  }

  // ---- verify: programmatic support ------------------------------------------

  @Test
  void verify_programmaticRequired_inventoryNotProgrammatic_isInvalid() {
    // programmaticDealTypes null/empty → not programmatic
    when(inventoryRepository.findByReferenceIdIn(anyList()))
        .thenReturn(List.of(inventory("i1", "R1", "Japan", "Digital", "owner-1")));

    CsvVerifyResponse res =
        service.verify(csv("inventory_id\nR1"), new CsvMatchCriteria(null, null, null, "YES"));

    assertEquals(0, res.validCount());
    assertEquals(1, res.invalidCount());
    assertEquals(CsvRowType.INVALID, res.rows().get(0).type());
    assertTrue(res.matchedInventories().isEmpty());
  }

  @Test
  void verify_programmaticRequired_inventoryIsProgrammatic_isValid() {
    Inventory inv = inventory("i1", "R1", "Japan", "Digital", "owner-1");
    inv.setProgrammaticDealTypes(List.of("open_auction"));
    when(inventoryRepository.findByReferenceIdIn(anyList())).thenReturn(List.of(inv));
    stubMapperEcho();

    CsvVerifyResponse res =
        service.verify(csv("inventory_id\nR1"), new CsvMatchCriteria(null, null, null, "YES"));

    assertEquals(1, res.validCount());
  }

  @Test
  void verify_programmaticDealTypeMismatch_isInvalid() {
    // Inventory is programmatic but only offers PREFERRED_DEAL — the GUARANTEED line item must fail
    // (previously it passed on the broad "any programmatic" check).
    Inventory inv = inventory("i1", "R1", "Japan", "Digital", "owner-1");
    inv.setProgrammaticDealTypes(List.of("preferred_deal"));
    when(inventoryRepository.findByReferenceIdIn(anyList())).thenReturn(List.of(inv));

    CsvVerifyResponse res =
        service.verify(
            csv("inventory_id\nR1"), new CsvMatchCriteria(null, null, null, "YES", "GUARANTEED"));

    assertEquals(0, res.validCount());
    assertEquals(1, res.invalidCount());
    CsvRowResult row = res.rows().get(0);
    assertEquals(CsvRowType.INVALID, row.type());
    assertTrue(row.messages().stream().anyMatch(m -> m.contains("GUARANTEED")));
  }

  @Test
  void verify_programmaticDealTypeMatch_isValid_caseInsensitive() {
    // Inventory offers guaranteed (stored lowercase) — the GUARANTEED line item matches.
    Inventory inv = inventory("i1", "R1", "Japan", "Digital", "owner-1");
    inv.setProgrammaticDealTypes(List.of("guaranteed", "preferred_deal"));
    when(inventoryRepository.findByReferenceIdIn(anyList())).thenReturn(List.of(inv));
    stubMapperEcho();

    CsvVerifyResponse res =
        service.verify(
            csv("inventory_id\nR1"), new CsvMatchCriteria(null, null, null, "YES", "GUARANTEED"));

    assertEquals(1, res.validCount());
    assertEquals(0, res.invalidCount());
  }

  // ---- verify: creative type / ad duration / resolution ----------------------

  @Test
  void verify_creativeTypeMismatch_isInvalid() {
    // Inventory only offers image creatives — a VIDEO line item must fail.
    Inventory inv = inventory("i1", "R1", "Japan", "Digital", "owner-1");
    inv.setCreativeFormats(
        List.of(Inventory.CreativeFormat.builder().creativeType("image").build()));
    when(inventoryRepository.findByReferenceIdIn(anyList())).thenReturn(List.of(inv));

    CsvVerifyResponse res =
        service.verify(
            csv("inventory_id\nR1"),
            new CsvMatchCriteria(null, null, null, null, null, "video", null, null));

    assertEquals(1, res.invalidCount());
    assertTrue(res.rows().get(0).messages().stream().anyMatch(m -> m.contains("video")));
  }

  @Test
  void verify_adDurationMismatch_isInvalid() {
    // Inventory sells only a 30s spot — a 15s line item must fail.
    Inventory inv = inventory("i1", "R1", "Japan", "Digital", "owner-1");
    inv.setPrices(List.of(Inventory.PriceModel.builder().durationSeconds(30).build()));
    when(inventoryRepository.findByReferenceIdIn(anyList())).thenReturn(List.of(inv));

    CsvVerifyResponse res =
        service.verify(
            csv("inventory_id\nR1"),
            new CsvMatchCriteria(null, null, null, null, null, null, "15", null));

    assertEquals(1, res.invalidCount());
    assertTrue(res.rows().get(0).messages().stream().anyMatch(m -> m.contains("15s")));
  }

  @Test
  void verify_adDuration_fallsBackToSpotDuration_whenNoPriceMatches_isValid() {
    // Prices offer only a 30s spot, but digitalFields.spotDuration is 15 — the fallback
    // must make a 15s line item valid (matches when EITHER source has the duration).
    Inventory inv = inventory("i1", "R1", "Japan", "Digital", "owner-1");
    inv.setPrices(List.of(Inventory.PriceModel.builder().durationSeconds(30).build()));
    inv.setDigitalFields(Inventory.DigitalFields.builder().spotDuration(15).build());
    when(inventoryRepository.findByReferenceIdIn(anyList())).thenReturn(List.of(inv));

    CsvVerifyResponse res =
        service.verify(
            csv("inventory_id\nR1"),
            new CsvMatchCriteria(null, null, null, null, null, null, "15", null));

    assertEquals(1, res.validCount());
    assertEquals(0, res.invalidCount());
  }

  @Test
  void verify_resolutionMismatch_isInvalid() {
    // Inventory panel is 1920x1080 — a 720x1280 line item must fail.
    Inventory inv = inventory("i1", "R1", "Japan", "Digital", "owner-1");
    inv.setPanels(List.of(Inventory.Panel.builder().pixelWidth(1920).pixelHeight(1080).build()));
    when(inventoryRepository.findByReferenceIdIn(anyList())).thenReturn(List.of(inv));

    CsvVerifyResponse res =
        service.verify(
            csv("inventory_id\nR1"),
            new CsvMatchCriteria(null, null, null, null, null, null, null, List.of("720x1280")));

    assertEquals(1, res.invalidCount());
    assertTrue(res.rows().get(0).messages().stream().anyMatch(m -> m.contains("720x1280")));
  }

  @Test
  void verify_multipleResolutions_passesWhenInventoryMatchesAny() {
    // Inventory supports 720x1280 only; request asks for 1920x1080 OR 720x1280 → VALID (OR match).
    Inventory inv = inventory("i1", "R1", "Japan", "Digital", "owner-1");
    inv.setPanels(List.of(Inventory.Panel.builder().pixelWidth(720).pixelHeight(1280).build()));
    when(inventoryRepository.findByReferenceIdIn(anyList())).thenReturn(List.of(inv));
    stubMapperEcho();

    CsvVerifyResponse res =
        service.verify(
            csv("inventory_id\nR1"),
            new CsvMatchCriteria(
                null, null, null, null, null, null, null, List.of("1920x1080", "720x1280")));

    assertEquals(1, res.validCount());
    assertEquals(0, res.invalidCount());
  }

  @Test
  void verify_multipleResolutions_failsOnlyWhenNoneMatch() {
    Inventory inv = inventory("i1", "R1", "Japan", "Digital", "owner-1");
    inv.setPanels(List.of(Inventory.Panel.builder().pixelWidth(1080).pixelHeight(1920).build()));
    when(inventoryRepository.findByReferenceIdIn(anyList())).thenReturn(List.of(inv));

    CsvVerifyResponse res =
        service.verify(
            csv("inventory_id\nR1"),
            new CsvMatchCriteria(
                null, null, null, null, null, null, null, List.of("1920x1080", "720x1280")));

    assertEquals(1, res.invalidCount());
    assertTrue(
        res.rows().get(0).messages().stream()
            .anyMatch(
                m ->
                    m.contains("any of the requested resolutions")
                        && m.contains("1920x1080")
                        && m.contains("720x1280")));
  }

  @Test
  void verify_everyNewCriterionFails_reportsAllMismatchesInOneArray() {
    // A GUARANTEED / video / 15s / 720x1280 line item vs an inventory that matches NONE of them —
    // the row must carry all four reasons together (not just the first).
    Inventory inv = inventory("i1", "R1", "Japan", "Digital", "owner-1");
    inv.setProgrammaticDealTypes(List.of("preferred_deal"));
    inv.setCreativeFormats(
        List.of(Inventory.CreativeFormat.builder().creativeType("image").build()));
    inv.setPrices(List.of(Inventory.PriceModel.builder().durationSeconds(30).build()));
    inv.setPanels(List.of(Inventory.Panel.builder().pixelWidth(1920).pixelHeight(1080).build()));
    when(inventoryRepository.findByReferenceIdIn(anyList())).thenReturn(List.of(inv));

    CsvVerifyResponse res =
        service.verify(
            csv("inventory_id\nR1"),
            new CsvMatchCriteria(
                null, null, null, "YES", "GUARANTEED", "video", "15", List.of("720x1280")));

    CsvRowResult row = res.rows().get(0);
    assertEquals(CsvRowType.INVALID, row.type());
    assertEquals(4, row.messages().size(), "one message per failed check");
    assertTrue(row.messages().stream().anyMatch(m -> m.contains("GUARANTEED")));
    assertTrue(row.messages().stream().anyMatch(m -> m.contains("video")));
    assertTrue(row.messages().stream().anyMatch(m -> m.contains("15s")));
    assertTrue(row.messages().stream().anyMatch(m -> m.contains("720x1280")));
  }

  @Test
  void verify_creativeType_adDuration_resolution_allMatch_isValid() {
    Inventory inv = inventory("i1", "R1", "Japan", "Digital", "owner-1");
    inv.setCreativeFormats(
        List.of(Inventory.CreativeFormat.builder().creativeType("video").build()));
    inv.setPrices(List.of(Inventory.PriceModel.builder().durationSeconds(15).build()));
    inv.setPanels(List.of(Inventory.Panel.builder().pixelWidth(720).pixelHeight(1280).build()));
    when(inventoryRepository.findByReferenceIdIn(anyList())).thenReturn(List.of(inv));
    stubMapperEcho();

    CsvVerifyResponse res =
        service.verify(
            csv("inventory_id\nR1"),
            new CsvMatchCriteria(null, null, null, null, null, "VIDEO", "15", List.of("720x1280")));

    assertEquals(1, res.validCount());
    assertEquals(0, res.invalidCount());
  }

  @Test
  void verify_directLineItem_appliesNoProgrammaticCheck() {
    // Not programmatic, but programmaticSupport is null (direct) → the check is skipped.
    when(inventoryRepository.findByReferenceIdIn(anyList()))
        .thenReturn(List.of(inventory("i1", "R1", "Japan", "Classic", "owner-1")));
    stubMapperEcho();

    CsvVerifyResponse res = service.verify(csv("inventory_id\nR1"), CsvMatchCriteria.none());

    assertEquals(1, res.validCount());
  }

  @Test
  void verify_duplicateReferenceInFile_firstValidLaterDuplicate_matchedDeduped() {
    when(inventoryRepository.findByReferenceIdIn(anyList()))
        .thenReturn(List.of(inventory("i1", "R1", "Japan")));
    stubMapperEcho();

    CsvVerifyResponse res = service.verify(csv("inventory_id\nR1\nR1"), CsvMatchCriteria.none());

    assertEquals(2, res.totalRows());
    assertEquals(1, res.validCount());
    assertEquals(1, res.duplicateCount());
    assertEquals(CsvRowType.VALID, res.rows().get(0).type());
    assertEquals(CsvRowType.DUPLICATE, res.rows().get(1).type());
    assertEquals(
        1, res.matchedInventories().size(), "duplicate must not double the matched objects");
  }

  @Test
  void verify_blankRows_areTrimmed_notInvalidOrDuplicate() {
    when(inventoryRepository.findByReferenceIdIn(anyList()))
        .thenReturn(List.of(inventory("i1", "R1", "Japan")));
    stubMapperEcho();

    // Trailing/blank rows are trimmed during parse — only R1 counts; they neither become
    // "missing reference id" errors nor eat into the row cap.
    CsvVerifyResponse res =
        service.verify(csv("inventory_id\nR1\n\"\"\n\"\""), CsvMatchCriteria.none());

    assertEquals(1, res.validCount());
    assertEquals(0, res.invalidCount(), "blank rows are trimmed, not invalid");
    assertEquals(0, res.duplicateCount());
  }

  // ---- verify: caps ----------------------------------------------------------

  @Test
  void verify_rowCountExceedsCap_throwsTooManyRows_withActualCountInMessage() {
    CsvImportProperties tiny = new CsvImportProperties(2, 5_242_880L, 100);
    service =
        new InventoryCsvImportService(
            inventoryRepository, importRepository, inventoryItemMapper, tiny);

    BaseException ex =
        assertThrows(
            BaseException.class,
            () -> service.verify(csv("inventory_id\nR1\nR2\nR3"), CsvMatchCriteria.none()));
    // Distinct code from the byte-size cap, and a full-sentence detail with the ACTUAL count.
    assertEquals(ErrorCode.TOO_MANY_ROWS, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("3"), "message should report the actual count (3)");
    assertTrue(ex.getMessage().contains("2"), "message should report the maximum (2)");
  }

  @Test
  void verify_fileSizeExceedsCap_throwsFileTooLarge_withActualSizeInMessage() {
    CsvImportProperties tiny = new CsvImportProperties(5000, 8L, 100);
    service =
        new InventoryCsvImportService(
            inventoryRepository, importRepository, inventoryItemMapper, tiny);

    BaseException ex =
        assertThrows(
            BaseException.class,
            () -> service.verify(csv("inventory_id\nR1\nR2\nR3"), CsvMatchCriteria.none()));
    assertEquals(ErrorCode.FILE_TOO_LARGE, ex.getErrorCode());
    // Full-sentence detail with the actual size (21 bytes) and the limit (8 bytes).
    assertTrue(ex.getMessage().contains("21"), "message should report the actual byte size");
    assertTrue(ex.getMessage().contains("8"), "message should report the maximum byte size");
  }

  // ---- import ----------------------------------------------------------------

  @Test
  void importCsv_persistsMatchedRefIds_andReturnsImportId() {
    when(inventoryRepository.findByReferenceIdIn(anyList()))
        .thenReturn(List.of(inventory("i1", "R1", "Japan"), inventory("i2", "R2", "Japan")));
    stubMapperEcho();
    when(importRepository.save(any(SelectInventoryImports.class)))
        .thenAnswer(
            i -> {
              SelectInventoryImports saved = i.getArgument(0);
              saved.setId("import-1");
              return saved;
            });

    CsvImportResponse res =
        service.importCsv(
            COMPANY,
            CAMPAIGN,
            csv("inventory_id\nR1\nR2\nR_MISSING"),
            new CsvMatchCriteria("Japan", null, null, null));

    assertEquals("import-1", res.importId());
    assertEquals(2, res.result().validCount());

    ArgumentCaptor<SelectInventoryImports> captor =
        ArgumentCaptor.forClass(SelectInventoryImports.class);
    verify(importRepository).save(captor.capture());
    SelectInventoryImports persisted = captor.getValue();
    assertEquals(COMPANY, persisted.getCompanyId());
    assertEquals(CAMPAIGN, persisted.getCampaignId());
    assertEquals("inventories.csv", persisted.getFileName());
    assertEquals("Japan", persisted.getCountryName());
    assertEquals(List.of("R1", "R2"), persisted.getInventoryRefIds(), "only VALID matched ref ids");
  }

  // ---- list ------------------------------------------------------------------

  @Test
  void list_derivesInventoryCountFromRefIds() {
    SelectInventoryImports doc = new SelectInventoryImports();
    doc.setId("import-1");
    doc.setCompanyId(COMPANY);
    doc.setCampaignId(CAMPAIGN);
    doc.setFileName("inventories.csv");
    doc.setCountryName("Japan");
    doc.setInventoryRefIds(List.of("R1", "R2", "R3"));
    doc.setCreatedAt(LocalDateTime.now());
    Pageable pageable = PageRequest.of(0, 20);
    when(importRepository.findByCompanyIdAndCampaignId(COMPANY, CAMPAIGN, pageable))
        .thenReturn(new PageImpl<>(List.of(doc), pageable, 1));

    Page<InventoryImportSummary> page = service.list(COMPANY, CAMPAIGN, pageable);

    assertEquals(1, page.getTotalElements());
    assertEquals(3, page.getContent().get(0).inventoryCount());
    assertEquals("import-1", page.getContent().get(0).importId());
  }

  // ---- use (re-resolve) ------------------------------------------------------

  @Test
  void useImport_reresolvesStoredRefIdsLive() {
    SelectInventoryImports doc = new SelectInventoryImports();
    doc.setId("import-1");
    doc.setCompanyId(COMPANY);
    doc.setInventoryRefIds(List.of("R1", "R2"));
    doc.setCountryName("Japan");
    when(importRepository.findByIdAndCompanyId("import-1", COMPANY)).thenReturn(Optional.of(doc));
    // R2 was archived/deleted since import → only R1 resolves now
    when(inventoryRepository.findByReferenceIdIn(anyList()))
        .thenReturn(List.of(inventory("i1", "R1", "Japan")));
    stubMapperEcho();

    CsvVerifyResponse res = service.useImport(COMPANY, "import-1");

    assertEquals(2, res.totalRows());
    assertEquals(1, res.validCount());
    assertEquals(1, res.invalidCount());
    assertEquals(1, res.matchedInventories().size());
  }

  @Test
  void useImport_unknownIdOrOtherTenant_throwsImportNotFound() {
    when(importRepository.findByIdAndCompanyId("nope", COMPANY)).thenReturn(Optional.empty());
    assertErrorCode(ErrorCode.IMPORT_NOT_FOUND, () -> service.useImport(COMPANY, "nope"));
  }

  // ---- download --------------------------------------------------------------

  @Test
  void download_regeneratesCanonicalCsvFromRefIds() {
    SelectInventoryImports doc = new SelectInventoryImports();
    doc.setId("import-1");
    doc.setCompanyId(COMPANY);
    doc.setFileName("my-upload.csv");
    doc.setInventoryRefIds(List.of("R1", "R2"));
    when(importRepository.findByIdAndCompanyId("import-1", COMPANY)).thenReturn(Optional.of(doc));

    InventoryCsvImportService.CsvDownload dl = service.download(COMPANY, "import-1");

    assertEquals("my-upload.csv", dl.fileName());
    String body = new String(dl.content(), StandardCharsets.UTF_8);
    assertTrue(body.startsWith("inventory_id"), "header regenerated");
    assertTrue(body.contains("R1"));
    assertTrue(body.contains("R2"));
  }

  // ---- delete ----------------------------------------------------------------

  @Test
  void delete_existing_deletesScopedToTenant() {
    SelectInventoryImports doc = new SelectInventoryImports();
    doc.setId("import-1");
    doc.setCompanyId(COMPANY);
    when(importRepository.findByIdAndCompanyId("import-1", COMPANY)).thenReturn(Optional.of(doc));

    service.delete(COMPANY, "import-1");

    verify(importRepository).deleteByIdAndCompanyId("import-1", COMPANY);
  }

  @Test
  void delete_otherTenant_throwsImportNotFound_andDoesNotDelete() {
    when(importRepository.findByIdAndCompanyId("import-1", "other")).thenReturn(Optional.empty());
    assertErrorCode(ErrorCode.IMPORT_NOT_FOUND, () -> service.delete("other", "import-1"));
    verify(importRepository, never()).deleteByIdAndCompanyId(anyString(), anyString());
  }
}
