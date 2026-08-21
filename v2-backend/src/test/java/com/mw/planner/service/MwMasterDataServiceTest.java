package com.mw.planner.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.dto.MwCountryDTO;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.masterdata.MasterDataApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MwMasterDataServiceTest {

  @Mock private RestTemplate restTemplate;

  @Mock private MwPlannerProperties mwPlannerProperties;

  @Mock private SecurityContext securityContext;

  @Mock private Authentication authentication;

  @InjectMocks private MwMasterDataService mwMasterDataService;

  private MwCountryDTO sampleCountry;
  private String testToken;

  @BeforeEach
  void setUp() {
    testToken = "test-bearer-token";

    // Setup sample country data
    sampleCountry = new MwCountryDTO();
    sampleCountry.setCountryId("france");
    sampleCountry.setName("France");
    sampleCountry.setNameJa("フランス");
    sampleCountry.setLatitude(51.0344);
    sampleCountry.setLongitude(2.618787);
    sampleCountry.setZoom(5);
    sampleCountry.setPopulation(67150000L);
    sampleCountry.setIso("FR");
    sampleCountry.setPostalformat("99999");
    sampleCountry.setPostalname("Code postal");
    sampleCountry.setActive(true);
    sampleCountry.setDialingCode("+33");

    // Setup security context
    when(securityContext.getAuthentication()).thenReturn(authentication);
    when(authentication.getCredentials()).thenReturn(testToken);
    SecurityContextHolder.setContext(securityContext);

    // Setup mock for MwPlannerProperties
    MwPlannerProperties.MasterData mockMasterData = mock(MwPlannerProperties.MasterData.class);
    when(mwPlannerProperties.getMasterData()).thenReturn(mockMasterData);
    lenient().when(mockMasterData.getFullCountryUrl()).thenReturn("https://test-api.com/countries");
  }

  @Test
  void testFetchCountriesFromMasterDataApi_Success() {
    // Arrange
    MwCountryDTO[] countries = {sampleCountry};
    ResponseEntity<MwCountryDTO[]> response = new ResponseEntity<>(countries, HttpStatus.OK);
    when(restTemplate.exchange(
            eq("https://test-api.com/countries"), any(), any(), eq(MwCountryDTO[].class)))
        .thenReturn(response);

    // Act
    List<MwCountryDTO> result = mwMasterDataService.fetchCountriesFromMasterDataApi();

    // Assert
    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("france", result.get(0).getCountryId());
    assertEquals("France", result.get(0).getName());

    verify(restTemplate)
        .exchange(eq("https://test-api.com/countries"), any(), any(), eq(MwCountryDTO[].class));
  }

  @Test
  void testFetchCountriesFromMasterDataApi_EmptyResponse() {
    // Arrange
    ResponseEntity<MwCountryDTO[]> response = ResponseEntity.ok().body(null);
    when(restTemplate.exchange(
            eq("https://test-api.com/countries"), any(), any(), eq(MwCountryDTO[].class)))
        .thenReturn(response);

    // Act
    List<MwCountryDTO> result = mwMasterDataService.fetchCountriesFromMasterDataApi();

    // Assert
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void testFetchCountriesFromMasterDataApi_NoToken() {
    // Arrange - Reset mocks to avoid unnecessary stubbing
    reset(mwPlannerProperties);
    when(authentication.getCredentials()).thenReturn(null);

    // Setup mock for MwPlannerProperties after reset
    MwPlannerProperties.MasterData mockMasterData = mock(MwPlannerProperties.MasterData.class);
    when(mwPlannerProperties.getMasterData()).thenReturn(mockMasterData);
    when(mockMasterData.getFullCountryUrl()).thenReturn("https://test-api.com/countries");

    // Act & Assert
    MasterDataApiException exception =
        assertThrows(
            MasterDataApiException.class,
            () -> mwMasterDataService.fetchCountriesFromMasterDataApi());

    assertEquals(ErrorCode.MASTER_DATA_API_ERROR, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("Unexpected error calling MW Master Data API"));
  }

  @Test
  void testFetchCountriesFromMasterDataApi_Unauthorized() {
    // Arrange
    when(restTemplate.exchange(
            eq("https://test-api.com/countries"), any(), any(), eq(MwCountryDTO[].class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

    // Act & Assert
    MasterDataApiException exception =
        assertThrows(
            MasterDataApiException.class,
            () -> mwMasterDataService.fetchCountriesFromMasterDataApi());

    assertEquals(ErrorCode.MASTER_DATA_API_UNAUTHORIZED, exception.getErrorCode());
    assertTrue(
        exception.getMessage().contains("Failed to fetch countries from MW Master Data API"));
  }

  @Test
  void testFetchCountriesFromMasterDataApi_Forbidden() {
    // Arrange
    when(restTemplate.exchange(
            eq("https://test-api.com/countries"), any(), any(), eq(MwCountryDTO[].class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));

    // Act & Assert
    MasterDataApiException exception =
        assertThrows(
            MasterDataApiException.class,
            () -> mwMasterDataService.fetchCountriesFromMasterDataApi());

    assertEquals(ErrorCode.MASTER_DATA_API_FORBIDDEN, exception.getErrorCode());
  }

  @Test
  void testFetchCountriesFromMasterDataApi_NotFound() {
    // Arrange
    when(restTemplate.exchange(
            eq("https://test-api.com/countries"), any(), any(), eq(MwCountryDTO[].class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

    // Act & Assert
    MasterDataApiException exception =
        assertThrows(
            MasterDataApiException.class,
            () -> mwMasterDataService.fetchCountriesFromMasterDataApi());

    assertEquals(ErrorCode.MASTER_DATA_API_NOT_FOUND, exception.getErrorCode());
  }

  @Test
  void testFetchCountriesFromMasterDataApi_BadRequest() {
    // Arrange
    when(restTemplate.exchange(
            eq("https://test-api.com/countries"), any(), any(), eq(MwCountryDTO[].class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

    // Act & Assert
    MasterDataApiException exception =
        assertThrows(
            MasterDataApiException.class,
            () -> mwMasterDataService.fetchCountriesFromMasterDataApi());

    assertEquals(ErrorCode.MASTER_DATA_API_BAD_REQUEST, exception.getErrorCode());
  }

  @Test
  void testFetchCountriesFromMasterDataApi_ServerError() {
    // Arrange
    when(restTemplate.exchange(
            eq("https://test-api.com/countries"), any(), any(), eq(MwCountryDTO[].class)))
        .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

    // Act & Assert
    MasterDataApiException exception =
        assertThrows(
            MasterDataApiException.class,
            () -> mwMasterDataService.fetchCountriesFromMasterDataApi());

    assertEquals(ErrorCode.MASTER_DATA_API_ERROR, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("MW Master Data API server error"));
  }

  @Test
  void testFetchCountriesFromMasterDataApi_Timeout() {
    // Arrange
    when(restTemplate.exchange(
            eq("https://test-api.com/countries"), any(), any(), eq(MwCountryDTO[].class)))
        .thenThrow(new ResourceAccessException("Connection timeout"));

    // Act & Assert
    MasterDataApiException exception =
        assertThrows(
            MasterDataApiException.class,
            () -> mwMasterDataService.fetchCountriesFromMasterDataApi());

    assertEquals(ErrorCode.MASTER_DATA_API_TIMEOUT, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("Timeout or connection error"));
  }

  @Test
  void testFetchCountriesFromMasterDataApi_UnexpectedError() {
    // Arrange
    when(restTemplate.exchange(
            eq("https://test-api.com/countries"), any(), any(), eq(MwCountryDTO[].class)))
        .thenThrow(new RuntimeException("Unexpected error"));

    // Act & Assert
    MasterDataApiException exception =
        assertThrows(
            MasterDataApiException.class,
            () -> mwMasterDataService.fetchCountriesFromMasterDataApi());

    assertEquals(ErrorCode.MASTER_DATA_API_ERROR, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("Unexpected error calling MW Master Data API"));
  }
}
