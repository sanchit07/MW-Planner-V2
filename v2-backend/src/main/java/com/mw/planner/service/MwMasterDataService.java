package com.mw.planner.service;

import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.dto.MwCountryDTO;
import com.mw.planner.dto.MwDistrictDTO;
import com.mw.planner.dto.MwStateDTO;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.masterdata.MasterDataApiException;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class MwMasterDataService {

  private final MwPlannerProperties mwPlannerProperties;
  private final RestTemplate restTemplate;

  /**
   * Fetches countries from MW Master Data API
   *
   * @return List of countries from external API
   */
  public List<MwCountryDTO> fetchCountriesFromMasterDataApi() {
    log.info("Calling MW Master Data API to fetch countries");

    // Prepare headers
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    // Create HTTP entity with headers
    HttpEntity<String> entity = new HttpEntity<>(headers);

    try {
      // Make the API call
      String url = mwPlannerProperties.getMasterData().getFullCountryUrl();
      log.info("Making API call to: {} ", url);

      long startTime = System.currentTimeMillis();

      ResponseEntity<MwCountryDTO[]> response =
          restTemplate.exchange(url, HttpMethod.GET, entity, MwCountryDTO[].class);

      long endTime = System.currentTimeMillis();
      long duration = endTime - startTime;

      log.info(
          "Successfully received {} countries from MW Master Data API in {} ms",
          response.getBody() != null ? response.getBody().length : 0,
          duration);

      return response.getBody() != null ? Arrays.asList(response.getBody()) : List.of();

    } catch (HttpClientErrorException e) {
      log.error("Client error calling MW Master Data API: {}", e.getStatusCode(), e);
      ErrorCode errorCode = mapHttpStatusToErrorCode(e.getStatusCode());
      throw new MasterDataApiException(
          errorCode, "Failed to fetch countries from MW Master Data API: " + e.getMessage(), e);
    } catch (HttpServerErrorException e) {
      log.error("Server error calling MW Master Data API: {}", e.getStatusCode(), e);
      throw new MasterDataApiException(
          ErrorCode.MASTER_DATA_API_ERROR, "MW Master Data API server error: " + e.getMessage(), e);
    } catch (ResourceAccessException e) {
      log.error("Timeout or connection error calling MW Master Data API", e);
      throw new MasterDataApiException(
          ErrorCode.MASTER_DATA_API_TIMEOUT,
          "Timeout or connection error calling MW Master Data API: " + e.getMessage(),
          e);
    } catch (Exception e) {
      log.error("Unexpected error calling MW Master Data API", e);
      throw new MasterDataApiException(
          ErrorCode.MASTER_DATA_API_ERROR,
          "Unexpected error calling MW Master Data API: " + e.getMessage(),
          e);
    }
  }

  /**
   * Maps HTTP status codes to appropriate error codes
   *
   * @param statusCode The HTTP status code
   * @return The corresponding ErrorCode
   */
  private ErrorCode mapHttpStatusToErrorCode(org.springframework.http.HttpStatusCode statusCode) {
    if (statusCode.value() == 401) {
      return ErrorCode.MASTER_DATA_API_UNAUTHORIZED;
    } else if (statusCode.value() == 403) {
      return ErrorCode.MASTER_DATA_API_FORBIDDEN;
    } else if (statusCode.value() == 404) {
      return ErrorCode.MASTER_DATA_API_NOT_FOUND;
    } else if (statusCode.value() == 400) {
      return ErrorCode.MASTER_DATA_API_BAD_REQUEST;
    } else {
      return ErrorCode.MASTER_DATA_API_ERROR;
    }
  }

  /**
   * Fetches states from MW Master Data API
   *
   * @return List of states from external API
   */
  public List<MwStateDTO> fetchStatesFromMasterDataApi() {
    log.info("Calling MW Master Data API to fetch states");

    // Prepare headers
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    // Create HTTP entity with headers
    HttpEntity<String> entity = new HttpEntity<>(headers);

    try {
      String url = mwPlannerProperties.getMasterData().getFullStateUrl();
      log.info("Making API call to: {} ", url);

      long startTime = System.currentTimeMillis();

      // Make the API call
      ResponseEntity<MwStateDTO[]> response =
          restTemplate.exchange(url, HttpMethod.GET, entity, MwStateDTO[].class);

      long endTime = System.currentTimeMillis();
      long duration = endTime - startTime;

      log.info(
          "Successfully received {} states from MW Master Data API in {} ms",
          response.getBody() != null ? response.getBody().length : 0,
          duration);

      return response.getBody() != null ? Arrays.asList(response.getBody()) : List.of();

    } catch (HttpClientErrorException e) {
      log.error("Client error calling MW Master Data API: {}", e.getStatusCode(), e);
      log.error("Response body: {}", e.getResponseBodyAsString());
      ErrorCode errorCode = mapHttpStatusToErrorCode(e.getStatusCode());
      throw new MasterDataApiException(
          errorCode, "Failed to fetch states from MW Master Data API: " + e.getMessage(), e);
    } catch (HttpServerErrorException e) {
      log.error("Server error calling MW Master Data API: {}", e.getStatusCode(), e);
      log.error("Response body: {}", e.getResponseBodyAsString());
      throw new MasterDataApiException(
          ErrorCode.MASTER_DATA_API_ERROR, "MW Master Data API server error: " + e.getMessage(), e);
    } catch (ResourceAccessException e) {
      log.error(
          "Timeout or connection error calling MW Master Data API (this usually happens with large datasets)",
          e);
      throw new MasterDataApiException(
          ErrorCode.MASTER_DATA_API_TIMEOUT,
          "Timeout or connection error calling MW Master Data API. This may happen with large datasets. "
              + e.getMessage(),
          e);
    } catch (Exception e) {
      log.error("Unexpected error calling MW Master Data API", e);
      throw new MasterDataApiException(
          ErrorCode.MASTER_DATA_API_ERROR,
          "Unexpected error calling MW Master Data API: " + e.getMessage(),
          e);
    }
  }

  /**
   * Fetches districts from MW Master Data API for specific states
   *
   * @param stateId Comma-separated state IDs to fetch districts for
   * @return List of district DTOs from external API
   */
  public List<MwDistrictDTO> fetchDistrictsFromMasterDataApi(String stateId) {
    log.info("Calling MW Master Data API to fetch districts for states: {}", stateId);

    // Prepare headers
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    // Create HTTP entity with headers
    HttpEntity<String> entity = new HttpEntity<>(headers);

    try {
      String url = mwPlannerProperties.getMasterData().getFullDistrictUrl(stateId);
      log.info("Making API call to: {} ", url);

      long startTime = System.currentTimeMillis();

      // Make the API call - expecting array of district objects
      ResponseEntity<MwDistrictDTO[]> response =
          restTemplate.exchange(url, HttpMethod.GET, entity, MwDistrictDTO[].class);

      long endTime = System.currentTimeMillis();
      long duration = endTime - startTime;

      log.info(
          "Successfully received {} districts from MW Master Data API in {} ms for state: {}",
          response.getBody() != null ? response.getBody().length : 0,
          duration,
          stateId);

      return response.getBody() != null ? Arrays.asList(response.getBody()) : List.of();

    } catch (HttpClientErrorException e) {
      log.error("Client error calling MW Master Data API: {}", e.getStatusCode(), e);
      log.error("Response body: {}", e.getResponseBodyAsString());
      ErrorCode errorCode = mapHttpStatusToErrorCode(e.getStatusCode());
      throw new MasterDataApiException(
          errorCode, "Failed to fetch districts from MW Master Data API: " + e.getMessage(), e);
    } catch (HttpServerErrorException e) {
      log.error("Server error calling MW Master Data API: {}", e.getStatusCode(), e);
      log.error("Response body: {}", e.getResponseBodyAsString());
      throw new MasterDataApiException(
          ErrorCode.MASTER_DATA_API_ERROR, "MW Master Data API server error: " + e.getMessage(), e);
    } catch (ResourceAccessException e) {
      log.error(
          "Timeout or connection error calling MW Master Data API (this usually happens with large datasets)",
          e);
      throw new MasterDataApiException(
          ErrorCode.MASTER_DATA_API_TIMEOUT,
          "Timeout or connection error calling MW Master Data API. This may happen with large datasets. "
              + e.getMessage(),
          e);
    } catch (Exception e) {
      log.error("Unexpected error calling MW Master Data API", e);
      throw new MasterDataApiException(
          ErrorCode.MASTER_DATA_API_ERROR,
          "Unexpected error calling MW Master Data API: " + e.getMessage(),
          e);
    }
  }
}
