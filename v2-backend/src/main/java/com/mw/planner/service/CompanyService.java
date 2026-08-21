package com.mw.planner.service;

import com.mw.planner.dto.CompanyDto;
import com.mw.planner.dto.CompanyLookupResponseDTO;
import com.mw.planner.dto.IamCompanyResponse;
import com.mw.planner.exception.auth.AuthenticationException;
import com.mw.planner.exception.company.CompanyNotFoundException;
import com.mw.planner.exception.company.InvalidCompanyDataException;
import com.mw.planner.service.iam.IamCompanyApiClient;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyService {

  private final SecurityContextService securityContextService;
  private final IamCompanyApiClient iamCompanyApiClient;
  @Lazy @Autowired CompanyService companyService;

  /**
   * Get company by ID (cache-first).
   *
   * <p>Company data is stored in cache only. On cache miss, it is fetched from IAM API via {@link
   * IamCompanyApiClient}.
   *
   * @param id Company ID to fetch
   * @return CompanyDto containing company information
   * @throws InvalidCompanyDataException if company ID is invalid
   * @throws CompanyNotFoundException if company not found or fetch fails
   */
  public CompanyDto getCompanyById(String id) {
    if (id == null || id.trim().isEmpty()) {
      throw new InvalidCompanyDataException("companyId", id);
    }

    try {
      String token = securityContextService.getBearerToken();
      log.debug("Fetching company by ID (cache-first): {}", id);
      return companyService.getCompanyById(id, token);
    } catch (CompanyNotFoundException e) {
      // Re-throw CompanyNotFoundException as-is
      throw e;
    } catch (AuthenticationException e) {
      log.warn("Authentication failed while fetching company {}: {}", id, e.getMessage());
      throw new CompanyNotFoundException(id, e);
    } catch (Exception e) {
      log.warn("Failed to fetch company {} from IAM/cache: {}", id, e.getMessage(), e);
      throw new CompanyNotFoundException(id, e);
    }
  }

  /**
   * Fetches company lookup result by companyId where company_id is not belong to the logged-in user
   *
   * @param companyId Company ID to lookup
   * @return CompanyLookupResponseDTO containing company information with company_id, seat_id and
   *     external_id
   * @throws AuthenticationException if the API call fails or returns an error
   */
  @Cacheable(value = "company-lookup", key = "#companyId", unless = "#result == null")
  public CompanyLookupResponseDTO getCompanyLookupWithCompanyId(@NotNull String companyId) {
    return iamCompanyApiClient.getCompanyLookupWithCompanyId(companyId);
  }

  /**
   * Fetches company lookup result by companyId, optionally omitting the {@code X-Company-Id}
   * scoping header. Used for cross-company lookups (e.g. the ADS submission flow) where the target
   * company does not belong to the logged-in user's context.
   *
   * @param companyId Company ID to lookup
   * @param includeCompanyIdHeader when {@code false}, the {@code X-Company-Id} header is not sent
   * @return CompanyLookupResponseDTO containing company information
   * @throws AuthenticationException if the API call fails or returns an error
   */
  public CompanyLookupResponseDTO getCompanyLookupWithCompanyId(
      @NotNull String companyId, boolean includeCompanyIdHeader) {
    return iamCompanyApiClient.getCompanyLookupWithCompanyId(companyId, includeCompanyIdHeader);
  }

  /**
   * Get multiple companies by their IDs (cache-first, de-duplicated).
   *
   * @param companyIds List of company IDs to fetch
   * @return List of CompanyDto containing company information (only activated companies)
   */
  public List<CompanyDto> getCompaniesByIds(List<String> companyIds) {
    if (companyIds == null || companyIds.isEmpty()) {
      return List.of();
    }

    try {
      String token = securityContextService.getBearerToken();

      // De-dupe to avoid repeated cache lookups / API calls.
      return companyIds.stream()
          .filter(id -> id != null && !id.isBlank())
          .distinct()
          .map(
              id -> {
                try {
                  return companyService.getCompanyById(id, token);
                } catch (CompanyNotFoundException e) {
                  log.debug("Company {} not found: {}", id, e.getMessage());
                  return null;
                } catch (Exception e) {
                  log.debug("Skipping company {} due to fetch error: {}", id, e.getMessage(), e);
                  return null;
                }
              })
          .filter(Objects::nonNull)
          .filter(CompanyDto::isActivated)
          .collect(Collectors.toList());
    } catch (com.mw.planner.exception.auth.AuthenticationException e) {
      log.error("Authentication failed while fetching companies", e);
      return List.of();
    }
  }

  /**
   * Loads a company from cache, or on cache miss fetches from IAM API and caches the mapped result.
   *
   * <p>Cache key intentionally ignores the token because company data is stable per companyId.
   *
   * @param companyId Company ID to fetch
   * @param token Bearer token for IAM API authentication
   * @return CompanyDto containing company information
   * @throws CompanyNotFoundException if company not found or response is invalid
   */
  @Cacheable(value = "companies", key = "#companyId", unless = "#result == null")
  public CompanyDto getCompanyById(String companyId, String token) {
    log.debug("Cache miss: fetching company from IAM API by ID: {}", companyId);

    try {
      IamCompanyResponse response = iamCompanyApiClient.getCompanyById(companyId, token);

      if (response == null) {
        throw new CompanyNotFoundException(
            companyId, new IllegalStateException("IAM API returned null response"));
      }

      if (response.getData() == null) {
        throw new CompanyNotFoundException(
            companyId, new IllegalStateException("IAM API response data is null"));
      }

      IamCompanyResponse.Company iamCompany = response.getData().getCompany();
      if (iamCompany == null) {
        throw new CompanyNotFoundException(
            companyId, new IllegalStateException("Company data is null in IAM API response"));
      }

      return mapIamCompanyToCompany(iamCompany);
    } catch (CompanyNotFoundException e) {
      throw e;
    } catch (Exception e) {
      log.error("Error fetching company by ID: {}", companyId, e);
      throw new CompanyNotFoundException(companyId, e);
    }
  }

  private static CompanyDto mapIamCompanyToCompany(IamCompanyResponse.Company iamCompany) {
    CompanyDto companyDto = new CompanyDto();

    companyDto.setId(iamCompany.getId());
    companyDto.setName(iamCompany.getName());
    companyDto.setExternalId(iamCompany.getExternalId());
    companyDto.setSeatId(iamCompany.getSeatId());
    companyDto.setPhone(iamCompany.getPhone());
    companyDto.setEmail(iamCompany.getEmail());
    companyDto.setExternalUserId(iamCompany.getExternalUserId());
    companyDto.setStatus(iamCompany.getStatus());
    companyDto.setActivated(Boolean.TRUE.equals(iamCompany.getIsActive()));
    companyDto.setMaxUsers(iamCompany.getMaxUsers());
    companyDto.setCreatedAt(iamCompany.getCreatedAt());
    companyDto.setUpdatedAt(iamCompany.getUpdatedAt());

    if (iamCompany.getCompanyType() != null) {
      companyDto.setCompanyType(
          new CompanyDto.CompanyType(
              iamCompany.getCompanyType().getId(),
              iamCompany.getCompanyType().getName(),
              iamCompany.getCompanyType().getCode(),
              iamCompany.getCompanyType().getIsSupplierSide(),
              iamCompany.getCompanyType().getIsDemandSide(),
              iamCompany.getCompanyType().getIsParentCompanyType()));
    }

    if (iamCompany.getSubscriptions() != null) {
      companyDto.setSubscriptions(
          iamCompany.getSubscriptions().stream()
              .map(
                  s ->
                      new CompanyDto.Subscription(
                          s.getId(),
                          s.getCreatedAt(),
                          s.getUpdatedAt(),
                          s.getDeletedAt(),
                          s.getCompanyId(),
                          s.getProductId(),
                          s.getProduct() != null
                              ? new CompanyDto.Product(
                                  s.getProduct().getId(),
                                  s.getProduct().getCreatedAt(),
                                  s.getProduct().getUpdatedAt(),
                                  s.getProduct().getDeletedAt(),
                                  s.getProduct().getName(),
                                  s.getProduct().getDescription(),
                                  s.getProduct().getIsActive(),
                                  s.getProduct().getProductCode(),
                                  s.getProduct().getProductType())
                              : null,
                          s.getIsActive(),
                          s.getStartDate(),
                          s.getMaxLicenses()))
              .toList());
    }

    String companyTypeCode =
        iamCompany.getCompanyType() != null ? iamCompany.getCompanyType().getCode() : null;
    companyDto.setBusinessType(mapBusinessType(companyTypeCode));

    return companyDto;
  }

  private static CompanyDto.BusinessType mapBusinessType(String companyTypeCode) {
    if (companyTypeCode == null || companyTypeCode.isBlank()) {
      return null;
    }

    // Best-effort mapping. If IAM company type codes match existing enum names this will work.
    String normalized = companyTypeCode.trim().toUpperCase();
    normalized = normalized.replace('-', '_').replace(' ', '_');

    try {
      return CompanyDto.BusinessType.valueOf(normalized);
    } catch (IllegalArgumentException ignored) {
      // Fall back to heuristic matching.
    }

    if (normalized.contains("OWNER")) return CompanyDto.BusinessType.MEDIA_OWNER;
    if (normalized.contains("AGENCY")) return CompanyDto.BusinessType.MEDIA_AGENCY;
    if (normalized.contains("BUYER")) return CompanyDto.BusinessType.MEDIA_BUYER;
    if (normalized.contains("OPERATOR")) return CompanyDto.BusinessType.MEDIA_OPERATOR;

    return null;
  }
}
