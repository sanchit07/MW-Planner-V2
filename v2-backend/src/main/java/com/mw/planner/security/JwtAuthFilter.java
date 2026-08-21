package com.mw.planner.security;

import com.mw.planner.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT Authentication Filter that pre-initializes IAM user context for caching. This filter runs
 * before OAuth2 Resource Server and ensures IamUserContext is cached for faster access in
 * subsequent requests. OAuth2 Resource Server handles actual JWT validation and authentication.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;
  private final UserService userService;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    String token = null;
    String username = null;

    // Extract Bearer token and username
    if (header != null && header.startsWith("Bearer ")) {
      token = header.substring(7);
      try {
        username = jwtUtil.extractUsername(token);
        log.debug("Extracted username from token: {}", username);
      } catch (Exception e) {
        // If token parsing fails, let OAuth2 Resource Server handle it
        log.debug("Failed to extract username from token, OAuth2 will handle validation", e);
      }
    }

    // Pre-initialize IAM user context if we have valid token and username
    // This caches the user data for faster access in PermissionEvaluator and other components
    // Note: OAuth2 Resource Server will handle JWT validation and set authentication
    if (username != null && token != null) {
      try {
        log.debug("Pre-initializing IamUserContext for username: {}", username);
        userService.initIamUserContext(username, token);
        log.debug("IamUserContext initialized and cached successfully");
      } catch (Exception e) {
        // Log error but don't fail the request - OAuth2 will handle authentication
        // The IamUserContext will be fetched later if needed
        log.warn(
            "Error pre-initializing IamUserContext, OAuth2 will handle authentication. Error: {}",
            e.getMessage());
      }
    }

    // Continue filter chain - OAuth2 Resource Server will validate JWT
    filterChain.doFilter(request, response);
  }
}
