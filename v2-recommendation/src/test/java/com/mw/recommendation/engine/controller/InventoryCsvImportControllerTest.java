package com.mw.recommendation.engine.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mw.recommendation.engine.config.CsvImportProperties;
import com.mw.recommendation.engine.dto.csv.CsvImportResponse;
import com.mw.recommendation.engine.dto.csv.CsvMatchCriteria;
import com.mw.recommendation.engine.dto.csv.CsvVerifyResponse;
import com.mw.recommendation.engine.enums.ErrorCode;
import com.mw.recommendation.engine.exception.BaseException;
import com.mw.recommendation.engine.exception.GlobalExceptionHandler;
import com.mw.recommendation.engine.service.InventoryCsvImportService;
import com.mw.recommendation.engine.service.InventoryCsvImportService.CsvDownload;
import com.mw.recommendation.engine.service.SecurityContextService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Web-layer spec for {@link InventoryCsvImportController}: multipart wiring, status codes (200/201
 * + Location/204), download headers, and error → HTTP status mapping via the real {@link
 * GlobalExceptionHandler}. Service is mocked (standaloneSetup — no Spring context).
 */
@ExtendWith(MockitoExtension.class)
class InventoryCsvImportControllerTest {

  private static final String BASE = "/api/v1/recommendation";
  private static final String COMPANY = "company-1";
  private static final String CAMPAIGN = "line-item-1";

  @Mock private InventoryCsvImportService importService;
  @Mock private SecurityContextService securityContextService;
  @Mock private MessageSource messageSource;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    CsvImportProperties props = new CsvImportProperties(5000, 5_242_880L, 100);
    InventoryCsvImportController controller =
        new InventoryCsvImportController(importService, securityContextService, props);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler(messageSource))
            .build();
    lenient().when(securityContextService.getPrimaryCompanyId()).thenReturn(COMPANY);
    lenient()
        .when(messageSource.getMessage(any(String.class), any(), any()))
        .thenReturn("localized-message");
  }

  private static MockMultipartFile csvFile() {
    return new MockMultipartFile(
        "file",
        "inventories.csv",
        "text/csv",
        "inventory_id\nR1\nR2".getBytes(StandardCharsets.UTF_8));
  }

  private static CsvVerifyResponse verifyResult() {
    return new CsvVerifyResponse(List.of(), List.of(), "Japan", 2, 2, 0, 0);
  }

  @Test
  void verify_returns200WithData() throws Exception {
    when(importService.verify(any(), any())).thenReturn(verifyResult());

    mockMvc
        .perform(
            multipart(BASE + "/campaigns/{c}/inventory-imports/verify", CAMPAIGN).file(csvFile()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.validCount").value(2));
  }

  @Test
  void verify_forwardsCommaSeparatedResolutionAsList() throws Exception {
    when(importService.verify(any(), any())).thenReturn(verifyResult());
    ArgumentCaptor<CsvMatchCriteria> criteriaCaptor =
        ArgumentCaptor.forClass(CsvMatchCriteria.class);

    mockMvc
        .perform(
            multipart(BASE + "/campaigns/{c}/inventory-imports/verify", CAMPAIGN)
                .file(csvFile())
                .param("resolution", "1920x1080,720x1280"))
        .andExpect(status().isOk());

    verify(importService).verify(any(), criteriaCaptor.capture());
    assertEquals(List.of("1920x1080", "720x1280"), criteriaCaptor.getValue().resolutions());
  }

  @Test
  void import_returns201WithLocationHeader() throws Exception {
    when(importService.importCsv(eq(COMPANY), eq(CAMPAIGN), any(), any()))
        .thenReturn(new CsvImportResponse("import-1", verifyResult()));

    mockMvc
        .perform(multipart(BASE + "/campaigns/{c}/inventory-imports", CAMPAIGN).file(csvFile()))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", BASE + "/inventory-imports/import-1"))
        .andExpect(jsonPath("$.data.importId").value("import-1"));
  }

  @Test
  void verify_serviceThrowsMissingHeader_maps400WithErrorCode() throws Exception {
    when(importService.verify(any(), any()))
        .thenThrow(new BaseException(ErrorCode.MISSING_HEADER, "no header"));

    mockMvc
        .perform(
            multipart(BASE + "/campaigns/{c}/inventory-imports/verify", CAMPAIGN).file(csvFile()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("ERR_3001"));
  }

  @Test
  void download_returnsCsvWithAttachmentHeader() throws Exception {
    when(importService.download(COMPANY, "import-1"))
        .thenReturn(
            new CsvDownload(
                "my-upload.csv", "inventory_id\nR1\nR2\n".getBytes(StandardCharsets.UTF_8)));

    mockMvc
        .perform(get(BASE + "/inventory-imports/{id}/download", "import-1"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
        .andExpect(
            header()
                .string(
                    "Content-Disposition", org.hamcrest.Matchers.containsString("my-upload.csv")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("R1")));
  }

  @Test
  void delete_returns204AndScopesToCompany() throws Exception {
    mockMvc
        .perform(delete(BASE + "/inventory-imports/{id}", "import-1"))
        .andExpect(status().isNoContent());
    verify(importService).delete(COMPANY, "import-1");
  }
}
