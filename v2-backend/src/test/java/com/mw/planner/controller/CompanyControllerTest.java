package com.mw.planner.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mw.planner.dto.CompanyLookupResponseDTO;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.GlobalExceptionHandler;
import com.mw.planner.exception.auth.AuthenticationException;
import com.mw.planner.service.MessageService;
import com.mw.planner.service.MetricsService;
import com.mw.planner.service.SecurityContextService;
import com.mw.planner.service.UserService;
import com.mw.planner.service.iam.IamCompanyApiClient;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CompanyControllerTest {

  @Mock private IamCompanyApiClient iamCompanyApiClient;
  @Mock private SecurityContextService securityContextService;
  @Mock private UserService userService;
  @Mock private MessageService messageService;
  @Mock private MetricsService metricsService;

  @InjectMocks private CompanyController companyController;

  private MockMvc mockMvc;
  private static final String TEST_TOKEN = "test-bearer-token";

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(companyController)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageService, metricsService, userService))
            .build();

    IamUserContext testIamUserContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .locale(Locale.ENGLISH)
            .build();

    // Mock the iamUserContextService.getIamUserContext() call that GlobalExceptionHandler makes
    // Using lenient() because GlobalExceptionHandler may not always be triggered in all tests
    lenient().when(userService.getIamUserContext()).thenReturn(testIamUserContext);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(iamCompanyApiClient, securityContextService);
  }

  // ========== Company Lookup Tests ==========

  @Test
  @DisplayName("companyLookup - Should return companies successfully with no filters")
  void companyLookup_WithNoFilters_ShouldReturnSuccess() throws Exception {
    // Given
    CompanyLookupResponseDTO.CompanyLookupListResponse mockResponse =
        createMockCompanyLookupListResponse();
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()))
        .thenReturn(mockResponse);

    // When & Then
    mockMvc
        .perform(get("/api/v1/companies/lookup").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id").value("company1"))
        .andExpect(jsonPath("$.data[0].name").value("Test Company 1"))
        .andExpect(jsonPath("$.data[0].company_type").value("AGENCY"))
        .andExpect(jsonPath("$.data[0].country_code").value("US"))
        .andExpect(jsonPath("$.data[1].id").value("company2"))
        .andExpect(jsonPath("$.data[1].name").value("Test Company 2"));

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull());
  }

  @Test
  @DisplayName("companyLookup - Should return companies successfully with all filters")
  void companyLookup_WithAllFilters_ShouldReturnSuccess() throws Exception {
    // Given
    CompanyLookupResponseDTO.CompanyLookupListResponse mockResponse =
        createMockCompanyLookupListResponse();
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            eq(50),
            eq(0),
            eq("MW India"),
            eq("AGENCY"),
            eq("US"),
            eq("test.com"),
            eq("test@example.com"),
            eq("USD"),
            eq("America/New_York"),
            eq("parent123"),
            eq("company123"),
            eq("domain,seat_id")))
        .thenReturn(mockResponse);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/companies/lookup")
                .param("limit", "50")
                .param("offset", "0")
                .param("search", "MW India")
                .param("company_type", "AGENCY")
                .param("country", "US")
                .param("domain", "test.com")
                .param("notification_email", "test@example.com")
                .param("currencyCode", "USD")
                .param("timezone", "America/New_York")
                .param("parentCompanyId", "parent123")
                .param("companyId", "company123")
                .param("fields", "domain,seat_id")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(2));

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            eq(50),
            eq(0),
            eq("MW India"),
            eq("AGENCY"),
            eq("US"),
            eq("test.com"),
            eq("test@example.com"),
            eq("USD"),
            eq("America/New_York"),
            eq("parent123"),
            eq("company123"),
            eq("domain,seat_id"));
  }

  @Test
  @DisplayName("companyLookup - Should return companies successfully with partial filters")
  void companyLookup_WithPartialFilters_ShouldReturnSuccess() throws Exception {
    // Given
    CompanyLookupResponseDTO.CompanyLookupListResponse mockResponse =
        createMockCompanyLookupListResponse();
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            eq(25),
            eq(10),
            eq("Test"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()))
        .thenReturn(mockResponse);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/companies/lookup")
                .param("limit", "25")
                .param("offset", "10")
                .param("search", "Test")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray());

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            eq(25),
            eq(10),
            eq("Test"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull());
  }

  @Test
  @DisplayName("companyLookup - Should cap limit at 100 when limit exceeds maximum")
  void companyLookup_WithLimitExceedingMax_ShouldCapAt100() throws Exception {
    // Given
    CompanyLookupResponseDTO.CompanyLookupListResponse mockResponse =
        createMockCompanyLookupListResponse();
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            eq(100),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()))
        .thenReturn(mockResponse);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/companies/lookup")
                .param("limit", "150")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            eq(100),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull());
  }

  @Test
  @DisplayName("companyLookup - Should return empty list when no companies found")
  void companyLookup_WithNoResults_ShouldReturnEmptyList() throws Exception {
    // Given
    CompanyLookupResponseDTO.CompanyLookupListResponse emptyResponse =
        CompanyLookupResponseDTO.CompanyLookupListResponse.builder()
            .success(true)
            .data(Collections.emptyList())
            .build();
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()))
        .thenReturn(emptyResponse);

    // When & Then
    mockMvc
        .perform(get("/api/v1/companies/lookup").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(0));

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull());
  }

  @Test
  @DisplayName("companyLookup - Should handle authentication exception")
  void companyLookup_WithAuthenticationException_ShouldReturnUnauthorized() throws Exception {
    // Given
    when(securityContextService.getBearerToken())
        .thenThrow(new AuthenticationException(ErrorCode.UNAUTHORIZED, "Token not available"));

    // When & Then
    mockMvc
        .perform(get("/api/v1/companies/lookup").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient, never())
        .companyLookup(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any());
  }

  @Test
  @DisplayName("companyLookup - Should handle general exception")
  void companyLookup_WithGeneralException_ShouldReturnInternalServerError() throws Exception {
    // Given
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()))
        .thenThrow(new RuntimeException("External API error"));

    // When & Then
    mockMvc
        .perform(get("/api/v1/companies/lookup").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError());

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull());
  }

  @Test
  @DisplayName("companyLookup - Should handle IAM API client exception")
  void companyLookup_WithIamApiClientException_ShouldReturnInternalServerError() throws Exception {
    // Given
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()))
        .thenThrow(
            new AuthenticationException(
                ErrorCode.INTERNAL_SERVER_ERROR, "Failed to fetch company lookup from IAM API"));

    // When & Then
    mockMvc
        .perform(get("/api/v1/companies/lookup").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError());

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull());
  }

  @Test
  @DisplayName("companyLookup - Should handle search filter with special characters")
  void companyLookup_WithSearchFilterSpecialCharacters_ShouldReturnSuccess() throws Exception {
    // Given
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            eq("Test & Company"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()))
        .thenReturn(createMockCompanyLookupListResponse());

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/companies/lookup")
                .param("search", "Test & Company")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            eq("Test & Company"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull());
  }

  @Test
  @DisplayName("companyLookup - Should handle company_type filter")
  void companyLookup_WithCompanyTypeFilter_ShouldReturnSuccess() throws Exception {
    // Given
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            eq("MEDIA_OWNER"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()))
        .thenReturn(createMockCompanyLookupListResponse());

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/companies/lookup")
                .param("company_type", "MEDIA_OWNER")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            eq("MEDIA_OWNER"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull());
  }

  @Test
  @DisplayName("companyLookup - Should handle country filter")
  void companyLookup_WithCountryFilter_ShouldReturnSuccess() throws Exception {
    // Given
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq("MY"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()))
        .thenReturn(createMockCompanyLookupListResponse());

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/companies/lookup")
                .param("country", "MY")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq("MY"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull());
  }

  @Test
  @DisplayName("companyLookup - Should handle fields parameter")
  void companyLookup_WithFieldsParameter_ShouldReturnSuccess() throws Exception {
    // Given
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq("domain,seat_id,external_id")))
        .thenReturn(createMockCompanyLookupListResponse());

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/companies/lookup")
                .param("fields", "domain,seat_id,external_id")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq("domain,seat_id,external_id"));
  }

  @Test
  @DisplayName("companyLookup - Should handle limit at boundary value 100")
  void companyLookup_WithLimitAt100_ShouldReturnSuccess() throws Exception {
    // Given
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            eq(100),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()))
        .thenReturn(createMockCompanyLookupListResponse());

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/companies/lookup")
                .param("limit", "100")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            eq(100),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull());
  }

  @Test
  @DisplayName("companyLookup - Should handle offset parameter")
  void companyLookup_WithOffsetParameter_ShouldReturnSuccess() throws Exception {
    // Given
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            eq(50),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()))
        .thenReturn(createMockCompanyLookupListResponse());

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/companies/lookup")
                .param("offset", "50")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            eq(50),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull());
  }

  @Test
  @DisplayName("companyLookup - Should handle domain filter")
  void companyLookup_WithDomainFilter_ShouldReturnSuccess() throws Exception {
    // Given
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq("example.com"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()))
        .thenReturn(createMockCompanyLookupListResponse());

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/companies/lookup")
                .param("domain", "example.com")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq("example.com"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull());
  }

  @Test
  @DisplayName("companyLookup - Should handle notification_email filter")
  void companyLookup_WithNotificationEmailFilter_ShouldReturnSuccess() throws Exception {
    // Given
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq("notify@example.com"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull()))
        .thenReturn(createMockCompanyLookupListResponse());

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/companies/lookup")
                .param("notification_email", "notify@example.com")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq("notify@example.com"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull());
  }

  @Test
  @DisplayName("companyLookup - Should handle currencyCode filter")
  void companyLookup_WithCurrencyCodeFilter_ShouldReturnSuccess() throws Exception {
    // Given
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq("MYR"),
            isNull(),
            isNull(),
            isNull(),
            isNull()))
        .thenReturn(createMockCompanyLookupListResponse());

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/companies/lookup")
                .param("currencyCode", "MYR")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq("MYR"),
            isNull(),
            isNull(),
            isNull(),
            isNull());
  }

  @Test
  @DisplayName("companyLookup - Should handle timezone filter")
  void companyLookup_WithTimezoneFilter_ShouldReturnSuccess() throws Exception {
    // Given
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq("Asia/Kuala_Lumpur"),
            isNull(),
            isNull(),
            isNull()))
        .thenReturn(createMockCompanyLookupListResponse());

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/companies/lookup")
                .param("timezone", "Asia/Kuala_Lumpur")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq("Asia/Kuala_Lumpur"),
            isNull(),
            isNull(),
            isNull());
  }

  @Test
  @DisplayName("companyLookup - Should handle parentCompanyId filter")
  void companyLookup_WithParentCompanyIdFilter_ShouldReturnSuccess() throws Exception {
    // Given
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq("parent-company-123"),
            isNull(),
            isNull()))
        .thenReturn(createMockCompanyLookupListResponse());

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/companies/lookup")
                .param("parentCompanyId", "parent-company-123")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq("parent-company-123"),
            isNull(),
            isNull());
  }

  @Test
  @DisplayName("companyLookup - Should handle companyId filter")
  void companyLookup_WithCompanyIdFilter_ShouldReturnSuccess() throws Exception {
    // Given
    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(iamCompanyApiClient.companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq("company-uuid-123"),
            isNull()))
        .thenReturn(createMockCompanyLookupListResponse());

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/companies/lookup")
                .param("companyId", "company-uuid-123")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(securityContextService).getBearerToken();
    verify(iamCompanyApiClient)
        .companyLookup(
            eq(TEST_TOKEN),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq("company-uuid-123"),
            isNull());
  }

  // ========== Helper Methods ==========

  private List<CompanyLookupResponseDTO> createMockCompanyList() {
    CompanyLookupResponseDTO company1 =
        CompanyLookupResponseDTO.builder()
            .id("company1")
            .name("Test Company 1")
            .companyType("AGENCY")
            .countryCode("US")
            .currencyCode("USD")
            .domain("test1.com")
            .externalId("ext1")
            .seatId(1)
            .logoUrl("https://example.com/logo1.png")
            .timezone("America/New_York")
            .build();

    CompanyLookupResponseDTO company2 =
        CompanyLookupResponseDTO.builder()
            .id("company2")
            .name("Test Company 2")
            .companyType("MEDIA_OWNER")
            .countryCode("MY")
            .currencyCode("MYR")
            .domain("test2.com")
            .externalId("ext2")
            .seatId(2)
            .logoUrl("https://example.com/logo2.png")
            .timezone("Asia/Kuala_Lumpur")
            .build();

    return Arrays.asList(company1, company2);
  }

  private CompanyLookupResponseDTO.CompanyLookupListResponse createMockCompanyLookupListResponse() {
    List<CompanyLookupResponseDTO> companies = createMockCompanyList();
    CompanyLookupResponseDTO.Meta meta =
        CompanyLookupResponseDTO.Meta.builder().total(2).limit(50).offset(0).build();

    return CompanyLookupResponseDTO.CompanyLookupListResponse.builder()
        .success(true)
        .data(companies)
        .meta(meta)
        .build();
  }
}
