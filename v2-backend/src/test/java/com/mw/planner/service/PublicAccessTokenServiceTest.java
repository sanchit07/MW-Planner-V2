package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.PublicAccessToken;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.exception.campaign.CampaignNotFoundException;
import com.mw.planner.exception.publicaccess.PublicAccessTokenInvalidException;
import com.mw.planner.exception.publicaccess.PublicAccessTokenNotFoundException;
import com.mw.planner.repository.PublicAccessTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicAccessTokenServiceTest {

  @Mock private PublicAccessTokenRepository publicAccessTokenRepository;

  @Mock private CampaignService campaignService;

  @Mock private UserService userService;

  @Mock private HttpServletRequest httpServletRequest;

  @InjectMocks private PublicAccessTokenService publicAccessTokenService;

  private static final String CAMPAIGN_ID = "campaign123";
  private static final String TOKEN_ID = "token123";
  private static final String USERNAME = "testuser";
  private static final String HOST_NAME = "example.com";

  private PublicAccessToken testToken;
  private IamUserContext userContext;

  @BeforeEach
  void setUp() {
    testToken = PublicAccessToken.builder().campaignId(CAMPAIGN_ID).domainName(HOST_NAME).build();
    testToken.setId(TOKEN_ID);

    userContext = new IamUserContext();
    userContext.setUsername(USERNAME);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(publicAccessTokenRepository, campaignService, userService);
  }

  // ========== createPublicAccessToken Tests ==========

  @Test
  @DisplayName(
      "createPublicAccessToken - Should create new token when campaign exists and no token exists")
  void createPublicAccessToken_WithValidCampaignAndNoExistingToken_ShouldCreateNewToken() {
    // Given
    when(httpServletRequest.getServerName()).thenReturn(HOST_NAME);
    when(publicAccessTokenRepository.findByCampaignId(CAMPAIGN_ID)).thenReturn(Optional.empty());
    when(userService.getIamUserContext()).thenReturn(userContext);
    when(publicAccessTokenRepository.save(any(PublicAccessToken.class))).thenReturn(testToken);

    // When
    String result =
        publicAccessTokenService.getOrCreatePublicAccessToken(CAMPAIGN_ID, httpServletRequest);

    // Then
    assertThat(result).isEqualTo(TOKEN_ID);
    verify(campaignService).findByIdForCurrentMode(CAMPAIGN_ID);
    verify(publicAccessTokenRepository).findByCampaignId(CAMPAIGN_ID);
    verify(userService).getIamUserContext();
    verify(publicAccessTokenRepository).save(any(PublicAccessToken.class));
  }

  @Test
  @DisplayName(
      "createPublicAccessToken - Should return existing token when token already exists for campaign")
  void createPublicAccessToken_WithExistingToken_ShouldReturnExistingTokenId() {
    // Given
    when(httpServletRequest.getServerName()).thenReturn(HOST_NAME);
    when(publicAccessTokenRepository.findByCampaignId(CAMPAIGN_ID))
        .thenReturn(Optional.of(testToken));

    // When
    String result =
        publicAccessTokenService.getOrCreatePublicAccessToken(CAMPAIGN_ID, httpServletRequest);

    // Then
    assertThat(result).isEqualTo(TOKEN_ID);
    verify(campaignService).findByIdForCurrentMode(CAMPAIGN_ID);
    verify(publicAccessTokenRepository).findByCampaignId(CAMPAIGN_ID);
    verify(userService, never()).getIamUserContext();
    verify(publicAccessTokenRepository, never()).save(any(PublicAccessToken.class));
  }

  @Test
  @DisplayName("createPublicAccessToken - Should throw exception when campaign does not exist")
  void createPublicAccessToken_WithNonExistentCampaign_ShouldThrowCampaignNotFoundException() {
    // Given
    when(campaignService.findByIdForCurrentMode(CAMPAIGN_ID))
        .thenThrow(new CampaignNotFoundException(CAMPAIGN_ID));

    // When & Then
    assertThatThrownBy(
            () ->
                publicAccessTokenService.getOrCreatePublicAccessToken(
                    CAMPAIGN_ID, httpServletRequest))
        .isInstanceOf(CampaignNotFoundException.class)
        .hasMessageContaining(CAMPAIGN_ID);

    verify(campaignService).findByIdForCurrentMode(CAMPAIGN_ID);
    verify(publicAccessTokenRepository, never()).findByCampaignId(any());
    verify(userService, never()).getIamUserContext();
    verify(publicAccessTokenRepository, never()).save(any(PublicAccessToken.class));
  }

  @Test
  @DisplayName("createPublicAccessToken - Should extract host name correctly")
  void createPublicAccessToken_ShouldExtractHostName() {
    // Given
    when(httpServletRequest.getServerName()).thenReturn(HOST_NAME);
    when(publicAccessTokenRepository.findByCampaignId(CAMPAIGN_ID)).thenReturn(Optional.empty());
    when(userService.getIamUserContext()).thenReturn(userContext);
    when(publicAccessTokenRepository.save(any(PublicAccessToken.class))).thenReturn(testToken);

    // When
    publicAccessTokenService.getOrCreatePublicAccessToken(CAMPAIGN_ID, httpServletRequest);

    // Then
    verify(publicAccessTokenRepository)
        .save(
            argThat(
                token ->
                    token.getDomainName().equals(HOST_NAME)
                        && token.getCampaignId().equals(CAMPAIGN_ID)
                        && token.getCreatedBy().equals(USERNAME)));
    verify(httpServletRequest).getServerName();
    verify(campaignService).findByIdForCurrentMode(CAMPAIGN_ID);
  }

  @Test
  @DisplayName("createPublicAccessToken - Should set createdBy from user context")
  void createPublicAccessToken_ShouldSetCreatedByFromUserContext() {
    // Given
    when(httpServletRequest.getServerName()).thenReturn(HOST_NAME);
    when(publicAccessTokenRepository.findByCampaignId(CAMPAIGN_ID)).thenReturn(Optional.empty());
    when(userService.getIamUserContext()).thenReturn(userContext);
    when(publicAccessTokenRepository.save(any(PublicAccessToken.class))).thenReturn(testToken);

    // When
    publicAccessTokenService.getOrCreatePublicAccessToken(CAMPAIGN_ID, httpServletRequest);

    // Then
    verify(publicAccessTokenRepository)
        .save(argThat(token -> USERNAME.equals(token.getCreatedBy())));
    verify(campaignService).findByIdForCurrentMode(CAMPAIGN_ID);
  }

  // ========== validateAndGetToken Tests ==========

  @Test
  @DisplayName("validateAndGetToken - Should return token when token exists")
  void validateAndGetToken_WithValidToken_ShouldReturnToken() {
    // Given
    when(publicAccessTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(testToken));

    // When
    PublicAccessToken result = publicAccessTokenService.validateAndGetToken(TOKEN_ID);

    // Then
    assertThat(result.getCampaignId()).isEqualTo(CAMPAIGN_ID);
    assertThat(result.getDomainName()).isEqualTo(HOST_NAME);
    verify(publicAccessTokenRepository).findById(TOKEN_ID);
  }

  @Test
  @DisplayName("validateAndGetToken - Should throw exception when token does not exist")
  void validateAndGetToken_WithNonExistentToken_ShouldThrowPublicAccessTokenNotFoundException() {
    // Given
    when(publicAccessTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> publicAccessTokenService.validateAndGetToken(TOKEN_ID))
        .isInstanceOf(PublicAccessTokenNotFoundException.class)
        .hasMessageContaining(TOKEN_ID);

    verify(publicAccessTokenRepository).findById(TOKEN_ID);
  }

  @Test
  @DisplayName("validateAndGetToken - Should handle null token ID")
  void validateAndGetToken_WithNullTokenId_ShouldThrowException() {
    // Given
    when(publicAccessTokenRepository.findById(null)).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> publicAccessTokenService.validateAndGetToken(null))
        .isInstanceOf(PublicAccessTokenNotFoundException.class);

    verify(publicAccessTokenRepository).findById(null);
  }

  // ========== validateDomainName Tests ==========

  @Test
  @DisplayName("validateDomainName - Should pass when host names match")
  void validateDomainName_WithMatchingHostName_ShouldNotThrowException() {
    // Given
    when(httpServletRequest.getServerName()).thenReturn(HOST_NAME);

    // When & Then
    publicAccessTokenService.validateDomainName(testToken, httpServletRequest);

    // No exception should be thrown
    verify(httpServletRequest).getServerName();
  }

  @Test
  @DisplayName("validateDomainName - Should throw exception when host names don't match")
  void validateDomainName_WithMismatchedHostName_ShouldThrowPublicAccessTokenInvalidException() {
    // Given
    when(httpServletRequest.getServerName()).thenReturn("different.com");

    // When & Then
    assertThatThrownBy(
            () -> publicAccessTokenService.validateDomainName(testToken, httpServletRequest))
        .isInstanceOf(PublicAccessTokenInvalidException.class)
        .hasMessageContaining("Invalid domain name");

    verify(httpServletRequest).getServerName();
  }
}
