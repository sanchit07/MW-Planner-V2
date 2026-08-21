package com.mw.planner.controller;

import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.LogoutRequest;
import com.mw.planner.dto.OAuthTokenResponse;
import com.mw.planner.dto.RefreshTokenRequest;
import com.mw.planner.dto.RefreshTokenResponse;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.auth.AuthenticationException;
import com.mw.planner.security.IamAuthorizeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication related operations")
@RequiredArgsConstructor
public class AuthController {

  private final IamAuthorizeService iamAuthorizeService;

  @PostMapping("/refresh")
  @Operation(
      summary = "Refresh access token",
      description =
          "Uses refresh token to obtain a new access token from IAM service. Returns new access_token and refresh_token.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Token refreshed successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"access_token\": \"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\", \"expires_in\": 3600}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid refresh token or validation error",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": false, \"message\": \"Refresh token is required\", \"timestamp\": \"2024-01-15T10:30:00Z\"}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Invalid or expired refresh token - re-login required",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": false, \"message\": \"Invalid or expired refresh token. Please login again.\", \"timestamp\": \"2024-01-15T10:30:00Z\"}")))
      })
  public ApiResponse<RefreshTokenResponse> refresh(
      @Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {

    // Validate refresh token
    if (!StringUtils.hasText(refreshTokenRequest.getRefreshToken())) {
      throw new AuthenticationException(ErrorCode.VALIDATION_ERROR, "Refresh token is required");
    }

    // Internally call the OAuth token endpoint
    OAuthTokenResponse oauthResponse =
        iamAuthorizeService.refreshToken(refreshTokenRequest.getRefreshToken());

    // Map OAuth response to public API response format
    RefreshTokenResponse response =
        RefreshTokenResponse.builder()
            .accessToken(oauthResponse.getAccessToken())
            .expiresIn(oauthResponse.getExpiresIn())
            .build();

    return ApiResponse.success(response);
  }

  @GetMapping("/oauth/authorize")
  @Operation(
      summary = "Initiate OAuth 2.0 authorization flow",
      description =
          "Redirects to IAM service authorization endpoint to begin OAuth 2.0 flow with PKCE (S256). Generates unique state and code_challenge parameters for security. User will be redirected to IAM login page.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "302",
            description = "Redirect to external authorization endpoint"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid configuration",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public RedirectView authorize(
      @RequestParam(value = "redirect_uri", required = false) String redirectUri) {

    IamAuthorizeService.AuthorizationUrlResult result =
        iamAuthorizeService.buildAuthorizationUrlWithPkce(redirectUri);

    String authorizationUrl = result.authorizationUrl();
    validateAuthorizationUrl(authorizationUrl);

    RedirectView redirectView = new RedirectView(authorizationUrl, false);
    redirectView.setHttp10Compatible(false);
    return redirectView;
  }

  private void validateAuthorizationUrl(String authorizationUrl) {
    try {
      URI uri = UriComponentsBuilder.fromUriString(authorizationUrl).build().toUri();

      if (uri.getScheme() == null
          || (!"http".equalsIgnoreCase(uri.getScheme())
              && !"https".equalsIgnoreCase(uri.getScheme()))) {
        throw invalidAuthUrlException(authorizationUrl);
      }

    } catch (IllegalArgumentException ex) {
      throw invalidAuthUrlException(authorizationUrl);
    }
  }

  private AuthenticationException invalidAuthUrlException(String authorizationUrl) {
    return new AuthenticationException(
        ErrorCode.INTERNAL_SERVER_ERROR,
        "Generated authorization URL is invalid: " + authorizationUrl);
  }

  @GetMapping("/oauth/callback")
  @Operation(
      summary = "OAuth Callback",
      description =
          "Handles OAuth2 authorization code callback with PKCE validation and token exchange. Returns token response with access_token, expires_in, refresh_token, scope, state, and token_type.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Token exchange successful",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid state, missing code, or missing code_verifier",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Token exchange failed",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<OAuthTokenResponse> callback(
      @RequestParam(value = "code", required = false) String code,
      @RequestParam(value = "state", required = false) String state,
      @RequestParam(value = "redirect_uri", required = false) String redirectUri) {

    // Validate query parameters
    validateCallbackParameters(code, state);

    // Exchange authorization code for tokens
    OAuthTokenResponse tokenResponse =
        iamAuthorizeService.exchangeCodeForToken(code, redirectUri, state);

    // Return token response
    return ApiResponse.success(tokenResponse);
  }

  private void validateCallbackParameters(String code, String state) {
    if (!StringUtils.hasText(code)) {
      throw new AuthenticationException(
          ErrorCode.VALIDATION_ERROR, "Authorization code is required");
    }
    if (!StringUtils.hasText(state)) {
      throw new AuthenticationException(ErrorCode.VALIDATION_ERROR, "State parameter is required");
    }
  }

  @PostMapping("/logout")
  @Operation(
      summary = "User logout",
      description = "Logs out the user by revoking the refresh token")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Logout successful",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"message\": \"Logout successful\", \"timestamp\": \"2024-01-15T10:30:00Z\"}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "User not authenticated",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<String> logout(@RequestBody(required = false) LogoutRequest logoutRequest) {

    if (StringUtils.hasText(logoutRequest.getRefresh_token())) {
      iamAuthorizeService.logout(logoutRequest);
      log.debug("Refresh token revoked successfully");
    } else {
      log.debug("No refresh token to revoke. Access token will expire naturally.");
    }
    return ApiResponse.success("Logged out successfully");
  }
}
