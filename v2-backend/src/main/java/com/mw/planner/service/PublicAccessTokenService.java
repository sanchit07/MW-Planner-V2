package com.mw.planner.service;

import com.mw.planner.domain.PublicAccessToken;
import com.mw.planner.exception.campaign.CampaignNotFoundException;
import com.mw.planner.exception.publicaccess.PublicAccessTokenInvalidException;
import com.mw.planner.exception.publicaccess.PublicAccessTokenNotFoundException;
import com.mw.planner.repository.PublicAccessTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublicAccessTokenService {

  private final PublicAccessTokenRepository publicAccessTokenRepository;
  private final CampaignService campaignService;
  private final UserService userService;

  /**
   * Retrieves an existing public access token for the given campaign, or creates and persists a new
   * one if none exists.
   *
   * <p>The campaign must exist; otherwise a {@link CampaignNotFoundException} is thrown.
   *
   * @param campaignId Campaign ID for which public access is requested
   * @param request HTTP request used to extract the domain name
   * @return Public access token ID
   */
  public String getOrCreatePublicAccessToken(String campaignId, HttpServletRequest request) {
    log.info("Generating public access token for campaignId: {}", campaignId);

    // Token generation is an authenticated action: the caller may only share plans from their own
    // Test Mode partition (cross-mode behaves as not-found). Consumption of an issued token stays
    // mode-agnostic — the token itself is the access boundary for public viewers.
    campaignService.findByIdForCurrentMode(campaignId);

    // Extract domain name from request
    String domainName = extractHostName(request);

    return publicAccessTokenRepository
        .findByCampaignId(campaignId)
        .map(PublicAccessToken::getId)
        .orElseGet(() -> createAndSaveToken(campaignId, domainName));
  }

  private String createAndSaveToken(String campaignId, String domainName) {

    // Get user ID from authenticated user context
    String username = userService.getIamUserContext().getUsername();

    // Create and save token data
    PublicAccessToken tokenData =
        PublicAccessToken.builder().campaignId(campaignId).domainName(domainName).build();

    // Set createdBy explicitly from authenticated user
    tokenData.setCreatedBy(username);

    PublicAccessToken savedToken = publicAccessTokenRepository.save(tokenData);

    log.info(
        "Successfully generated public access token with ID: {} for campaignId: {} by user: {}",
        savedToken.getId(),
        campaignId,
        username);

    return savedToken.getId();
  }

  /**
   * Validate and retrieve a public access token.
   *
   * @param tokenId Token ID to validate
   * @return Valid PublicAccessToken
   * @throws PublicAccessTokenNotFoundException if token not found
   */
  public PublicAccessToken validateAndGetToken(String tokenId) {
    log.debug("Validating public access token: {}", tokenId);

    PublicAccessToken tokenData =
        publicAccessTokenRepository
            .findById(tokenId)
            .orElseThrow(
                () -> {
                  log.warn("Public access token {} not found", tokenId);
                  return new PublicAccessTokenNotFoundException(
                      "Public access token not found: " + tokenId);
                });

    log.debug(
        "Public access token {} is valid for campaignId: {}", tokenId, tokenData.getCampaignId());

    return tokenData;
  }

  /**
   * Validate domain name against the stored token domain.
   *
   * @param token Public access token
   * @param request HTTP request to extract domain name
   * @throws PublicAccessTokenInvalidException if domain name doesn't match
   */
  public void validateDomainName(PublicAccessToken token, HttpServletRequest request) {
    String requestDomainName = extractHostName(request);
    String storedDomainName = token.getDomainName();
    log.info("Domain Name: {}, Stored Domain Name: {}", requestDomainName, storedDomainName);

    if (!requestDomainName.equals(storedDomainName)) {
      log.warn(
          "Domain name mismatch for token {}. Expected: {}, Actual: {}",
          token.getId(),
          storedDomainName,
          requestDomainName);
      throw new PublicAccessTokenInvalidException(
          "Invalid domain name. Expected: " + storedDomainName + ", Actual: " + requestDomainName);
    }

    log.debug("Domain name validated successfully for token: {}", token.getId());
  }

  /**
   * Extract domain name from HTTP request.
   *
   * @param request HTTP servlet request
   * @return Host name
   */
  public String extractHostName(HttpServletRequest request) {
    return request.getServerName();
  }
}
