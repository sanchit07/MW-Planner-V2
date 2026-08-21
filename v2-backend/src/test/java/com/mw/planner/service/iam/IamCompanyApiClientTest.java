package com.mw.planner.service.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.dto.CompanyLookupResponseDTO;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.auth.AuthenticationException;
import com.mw.planner.service.SecurityContextService;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class IamCompanyApiClientTest {

  @Mock private MwPlannerProperties mwPlannerProperties;
  @Mock private RestTemplate restTemplate;
  @Mock private SecurityContextService securityContextService;

  @InjectMocks private IamCompanyApiClient iamCompanyApiClient;

  private static final String TEST_TOKEN = "test-bearer-token";
  private static final String COMPANY_ID = "company-123";

  private MwPlannerProperties.IAM iamConfig;

  @BeforeEach
  void setUp() {
    iamConfig = new MwPlannerProperties.IAM();
    MwPlannerProperties.IAM.Endpoints endpoints = new MwPlannerProperties.IAM.Endpoints();
    endpoints.setCompanyLookup("/api/company/lookup");
    iamConfig.setServiceUrl("https://iam.example.com");
    iamConfig.setEndpoints(endpoints);

    when(mwPlannerProperties.getIam()).thenReturn(iamConfig);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(restTemplate, securityContextService);
  }

  // ========== companyLookup Tests ==========

  @Test
  @DisplayName("companyLookup - Should return companies successfully with all parameters")
  void companyLookup_WithAllParameters_ShouldReturnSuccess() {
    // Given
    CompanyLookupResponseDTO.CompanyLookupListResponse mockResponse =
        createMockCompanyLookupListResponse();

    ResponseEntity<CompanyLookupResponseDTO.CompanyLookupListResponse> responseEntity =
        new ResponseEntity<>(mockResponse, HttpStatus.OK);

    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class)))
        .thenReturn(responseEntity);

    // When
    CompanyLookupResponseDTO.CompanyLookupListResponse result =
        iamCompanyApiClient.companyLookup(
            TEST_TOKEN,
            50,
            10,
            "Test Company",
            "AGENCY",
            "US",
            "test.com",
            "test@example.com",
            "USD",
            "America/New_York",
            "parent-123",
            "company-123",
            "domain,seat_id,external_id");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getSuccess()).isTrue();
    assertThat(result.getData()).hasSize(2);
    assertThat(result.getData().get(0).getId()).isEqualTo("company1");
    assertThat(result.getData().get(0).getName()).isEqualTo("Test Company 1");

    verify(restTemplate)
        .exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class));
  }

  @Test
  @DisplayName("companyLookup - Should return companies successfully with minimal parameters")
  void companyLookup_WithMinimalParameters_ShouldReturnSuccess() {
    // Given
    CompanyLookupResponseDTO.CompanyLookupListResponse mockResponse =
        createMockCompanyLookupListResponse();

    ResponseEntity<CompanyLookupResponseDTO.CompanyLookupListResponse> responseEntity =
        new ResponseEntity<>(mockResponse, HttpStatus.OK);

    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class)))
        .thenReturn(responseEntity);

    // When
    CompanyLookupResponseDTO.CompanyLookupListResponse result =
        iamCompanyApiClient.companyLookup(
            TEST_TOKEN, null, null, null, null, null, null, null, null, null, null, null, null);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getSuccess()).isTrue();
    assertThat(result.getData()).hasSize(2);

    verify(restTemplate)
        .exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class));
  }

  @Test
  @DisplayName("companyLookup - Should handle HttpClientErrorException.NotFound")
  void companyLookup_WithNotFoundError_ShouldThrowAuthenticationException() {
    // Given
    HttpClientErrorException.NotFound notFoundException =
        mock(HttpClientErrorException.NotFound.class);

    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class)))
        .thenThrow(notFoundException);

    // When & Then
    assertThatThrownBy(
            () ->
                iamCompanyApiClient.companyLookup(
                    TEST_TOKEN,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(AuthenticationException.class)
        .satisfies(
            ex -> {
              AuthenticationException authEx = (AuthenticationException) ex;
              assertThat(authEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
              assertThat(authEx.getMessage()).contains("Company not found");
            });

    verify(restTemplate)
        .exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class));
  }

  @Test
  @DisplayName("companyLookup - Should handle HttpClientErrorException")
  void companyLookup_WithClientError_ShouldThrowAuthenticationException() {
    // Given
    HttpClientErrorException clientException =
        new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request");

    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class)))
        .thenThrow(clientException);

    // When & Then
    assertThatThrownBy(
            () ->
                iamCompanyApiClient.companyLookup(
                    TEST_TOKEN,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(AuthenticationException.class)
        .satisfies(
            ex -> {
              AuthenticationException authEx = (AuthenticationException) ex;
              assertThat(authEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
            });

    verify(restTemplate)
        .exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class));
  }

  @Test
  @DisplayName("companyLookup - Should handle HttpServerErrorException")
  void companyLookup_WithServerError_ShouldThrowAuthenticationException() {
    // Given
    HttpServerErrorException serverException =
        new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");

    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class)))
        .thenThrow(serverException);

    // When & Then
    assertThatThrownBy(
            () ->
                iamCompanyApiClient.companyLookup(
                    TEST_TOKEN,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(AuthenticationException.class)
        .satisfies(
            ex -> {
              AuthenticationException authEx = (AuthenticationException) ex;
              assertThat(authEx.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
              assertThat(authEx.getMessage()).contains("IAM Company Lookup API server error");
            });

    verify(restTemplate)
        .exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class));
  }

  @Test
  @DisplayName("companyLookup - Should handle RestClientException")
  void companyLookup_WithRestClientException_ShouldThrowAuthenticationException() {
    // Given
    RestClientException restException = new RestClientException("Connection failed");

    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class)))
        .thenThrow(restException);

    // When & Then
    assertThatThrownBy(
            () ->
                iamCompanyApiClient.companyLookup(
                    TEST_TOKEN,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(AuthenticationException.class)
        .satisfies(
            ex -> {
              AuthenticationException authEx = (AuthenticationException) ex;
              assertThat(authEx.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
              assertThat(authEx.getMessage())
                  .contains("Failed to connect to IAM Company Lookup API");
            });

    verify(restTemplate)
        .exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class));
  }

  @Test
  @DisplayName("getCompanyLookupWithCompanyId - Should send X-Company-Id header by default")
  void getCompanyLookupWithCompanyId_Default_ShouldSendCompanyIdHeader() {
    // Given
    CompanyLookupResponseDTO company =
        CompanyLookupResponseDTO.builder().id(COMPANY_ID).name("Test Company").build();
    CompanyLookupResponseDTO.CompanyLookupListResponse mockResponse =
        CompanyLookupResponseDTO.CompanyLookupListResponse.builder()
            .success(true)
            .data(Arrays.asList(company))
            .build();
    ResponseEntity<CompanyLookupResponseDTO.CompanyLookupListResponse> responseEntity =
        new ResponseEntity<>(mockResponse, HttpStatus.OK);

    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class)))
        .thenReturn(responseEntity);

    // When
    iamCompanyApiClient.getCompanyLookupWithCompanyId(COMPANY_ID);

    // Then
    @SuppressWarnings("unchecked")
    ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            entityCaptor.capture(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class));
    assertThat(entityCaptor.getValue().getHeaders().getFirst("X-Company-Id")).isEqualTo(COMPANY_ID);
  }

  @Test
  @DisplayName(
      "getCompanyLookupWithCompanyId - Should omit X-Company-Id header when includeCompanyIdHeader is false")
  void getCompanyLookupWithCompanyId_WithoutHeaderFlag_ShouldNotSendCompanyIdHeader() {
    // Given
    CompanyLookupResponseDTO company =
        CompanyLookupResponseDTO.builder().id(COMPANY_ID).name("Test Company").build();
    CompanyLookupResponseDTO.CompanyLookupListResponse mockResponse =
        CompanyLookupResponseDTO.CompanyLookupListResponse.builder()
            .success(true)
            .data(Arrays.asList(company))
            .build();
    ResponseEntity<CompanyLookupResponseDTO.CompanyLookupListResponse> responseEntity =
        new ResponseEntity<>(mockResponse, HttpStatus.OK);

    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class)))
        .thenReturn(responseEntity);

    // When
    iamCompanyApiClient.getCompanyLookupWithCompanyId(COMPANY_ID, false);

    // Then
    @SuppressWarnings("unchecked")
    ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            entityCaptor.capture(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class));
    assertThat(entityCaptor.getValue().getHeaders().containsKey("X-Company-Id")).isFalse();
  }

  // ========== getCompanyLookupWithSeatId Tests ==========

  @Test
  @DisplayName("getCompanyLookupWithSeatId - Should return company successfully")
  void getCompanyLookupWithSeatId_WithValidCompanyId_ShouldReturnCompany() {
    // Given
    CompanyLookupResponseDTO company =
        CompanyLookupResponseDTO.builder()
            .id(COMPANY_ID)
            .name("Test Company")
            .seatId(123)
            .externalId("ext-123")
            .domain("test.com")
            .companyType("AGENCY")
            .isActive("true")
            .notificationEmail("test@example.com")
            .companyCountry("US")
            .currencyCode("USD")
            .timezone("America/New_York")
            .build();

    CompanyLookupResponseDTO.CompanyLookupListResponse mockResponse =
        CompanyLookupResponseDTO.CompanyLookupListResponse.builder()
            .success(true)
            .data(Arrays.asList(company))
            .build();

    ResponseEntity<CompanyLookupResponseDTO.CompanyLookupListResponse> responseEntity =
        new ResponseEntity<>(mockResponse, HttpStatus.OK);

    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class)))
        .thenReturn(responseEntity);

    // When
    CompanyLookupResponseDTO result = iamCompanyApiClient.getCompanyLookupWithCompanyId(COMPANY_ID);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(COMPANY_ID);
    assertThat(result.getName()).isEqualTo("Test Company");
    assertThat(result.getSeatId()).isEqualTo(123);
    assertThat(result.getExternalId()).isEqualTo("ext-123");

    verify(securityContextService).getBearerToken();
    verify(restTemplate)
        .exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class));
  }

  @Test
  @DisplayName("getCompanyLookupWithSeatId - Should throw exception when company not found")
  void getCompanyLookupWithSeatId_WithEmptyResponse_ShouldThrowAuthenticationException() {
    // Given
    CompanyLookupResponseDTO.CompanyLookupListResponse mockResponse =
        CompanyLookupResponseDTO.CompanyLookupListResponse.builder()
            .success(true)
            .data(Collections.emptyList())
            .build();

    ResponseEntity<CompanyLookupResponseDTO.CompanyLookupListResponse> responseEntity =
        new ResponseEntity<>(mockResponse, HttpStatus.OK);

    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class)))
        .thenReturn(responseEntity);

    // When & Then
    assertThatThrownBy(() -> iamCompanyApiClient.getCompanyLookupWithCompanyId(COMPANY_ID))
        .isInstanceOf(AuthenticationException.class)
        .satisfies(
            ex -> {
              AuthenticationException authEx = (AuthenticationException) ex;
              assertThat(authEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
              assertThat(authEx.getMessage()).contains("Company not found: " + COMPANY_ID);
            });

    verify(securityContextService).getBearerToken();
    verify(restTemplate)
        .exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class));
  }

  @Test
  @DisplayName("getCompanyLookupWithSeatId - Should throw exception when data is null")
  void getCompanyLookupWithSeatId_WithNullData_ShouldThrowAuthenticationException() {
    // Given
    CompanyLookupResponseDTO.CompanyLookupListResponse mockResponse =
        CompanyLookupResponseDTO.CompanyLookupListResponse.builder()
            .success(true)
            .data(null)
            .build();

    ResponseEntity<CompanyLookupResponseDTO.CompanyLookupListResponse> responseEntity =
        new ResponseEntity<>(mockResponse, HttpStatus.OK);

    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class)))
        .thenReturn(responseEntity);

    // When & Then
    assertThatThrownBy(() -> iamCompanyApiClient.getCompanyLookupWithCompanyId(COMPANY_ID))
        .isInstanceOf(AuthenticationException.class)
        .satisfies(
            ex -> {
              AuthenticationException authEx = (AuthenticationException) ex;
              assertThat(authEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
              assertThat(authEx.getMessage()).contains("Company not found: " + COMPANY_ID);
            });

    verify(securityContextService).getBearerToken();
    verify(restTemplate)
        .exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class));
  }

  @Test
  @DisplayName("getCompanyLookupWithSeatId - Should handle HttpClientErrorException")
  void getCompanyLookupWithSeatId_WithClientError_ShouldThrowAuthenticationException() {
    // Given
    HttpClientErrorException clientException =
        new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found");

    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class)))
        .thenThrow(clientException);

    // When & Then
    assertThatThrownBy(() -> iamCompanyApiClient.getCompanyLookupWithCompanyId(COMPANY_ID))
        .isInstanceOf(AuthenticationException.class)
        .satisfies(
            ex -> {
              AuthenticationException authEx = (AuthenticationException) ex;
              assertThat(authEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
            });

    verify(securityContextService).getBearerToken();
    verify(restTemplate)
        .exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class));
  }

  @Test
  @DisplayName("getCompanyLookupWithSeatId - Should handle HttpServerErrorException")
  void getCompanyLookupWithSeatId_WithServerError_ShouldThrowAuthenticationException() {
    // Given
    HttpServerErrorException serverException =
        new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");

    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class)))
        .thenThrow(serverException);

    // When & Then
    assertThatThrownBy(() -> iamCompanyApiClient.getCompanyLookupWithCompanyId(COMPANY_ID))
        .isInstanceOf(AuthenticationException.class)
        .satisfies(
            ex -> {
              AuthenticationException authEx = (AuthenticationException) ex;
              assertThat(authEx.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
              assertThat(authEx.getMessage()).contains("IAM Company Lookup API server error");
            });

    verify(securityContextService).getBearerToken();
    verify(restTemplate)
        .exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class));
  }

  @Test
  @DisplayName("getCompanyLookupWithSeatId - Should handle RestClientException")
  void getCompanyLookupWithSeatId_WithRestClientException_ShouldThrowAuthenticationException() {
    // Given
    RestClientException restException = new RestClientException("Connection failed");

    when(securityContextService.getBearerToken()).thenReturn(TEST_TOKEN);
    when(restTemplate.exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class)))
        .thenThrow(restException);

    // When & Then
    assertThatThrownBy(() -> iamCompanyApiClient.getCompanyLookupWithCompanyId(COMPANY_ID))
        .isInstanceOf(AuthenticationException.class)
        .satisfies(
            ex -> {
              AuthenticationException authEx = (AuthenticationException) ex;
              assertThat(authEx.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
              assertThat(authEx.getMessage())
                  .contains("Failed to connect to IAM Company Lookup API");
            });

    verify(securityContextService).getBearerToken();
    verify(restTemplate)
        .exchange(
            anyString(),
            eq(org.springframework.http.HttpMethod.GET),
            any(),
            eq(CompanyLookupResponseDTO.CompanyLookupListResponse.class));
  }

  // ========== Helper Methods ==========

  private CompanyLookupResponseDTO.CompanyLookupListResponse createMockCompanyLookupListResponse() {
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

    CompanyLookupResponseDTO.Meta meta =
        CompanyLookupResponseDTO.Meta.builder().total(2).limit(50).offset(0).build();

    return CompanyLookupResponseDTO.CompanyLookupListResponse.builder()
        .success(true)
        .data(Arrays.asList(company1, company2))
        .meta(meta)
        .build();
  }
}
