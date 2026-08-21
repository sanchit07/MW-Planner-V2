package com.mw.planner.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sanitizes malformed tokens in the incoming {@code Accept} header before Spring MVC content
 * negotiation runs. Some proxies/clients mangle the wildcard {@code *&#47;*} into a bare {@code /},
 * producing headers like {@code application/json, text/plain, /}. Spring's {@code
 * HeaderContentNegotiationStrategy} then throws {@link InvalidMediaTypeException} while parsing the
 * raw header, which surfaces as a generic HTTP 500 on every request.
 *
 * <p>This filter replaces any un-parseable token with {@code *&#47;*} (dropping duplicates,
 * preserving order) via an {@link HttpServletRequestWrapper}. Well-formed headers pass through
 * untouched with no wrapping. Registered at highest precedence in {@link AppConfig} so it wraps the
 * request for the entire chain.
 */
@Slf4j
public class AcceptHeaderSanitizingFilter extends OncePerRequestFilter {

  private static final String WILDCARD = "*/*";

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    String accept = request.getHeader(HttpHeaders.ACCEPT);
    String sanitized = sanitize(accept);

    if (sanitized != null && !sanitized.equals(accept)) {
      log.debug("Sanitized malformed Accept header [{}] -> [{}]", accept, sanitized);
      filterChain.doFilter(new SanitizedAcceptRequestWrapper(request, sanitized), response);
      return;
    }

    filterChain.doFilter(request, response);
  }

  /**
   * Returns a sanitized Accept header value, or {@code null} when the header is absent/blank so the
   * caller can leave the request untouched. Each comma-separated token that cannot be parsed as a
   * {@link MediaType} is replaced with {@code *&#47;*}. Falls back to {@code *&#47;*} if nothing
   * valid remains.
   */
  private String sanitize(String accept) {
    if (accept == null || accept.isBlank()) {
      return null;
    }

    Set<String> tokens = new LinkedHashSet<>();
    for (String raw : accept.split(",")) {
      String token = raw.trim();
      if (token.isEmpty()) {
        tokens.add(WILDCARD);
        continue;
      }
      try {
        MediaType.parseMediaType(token);
        tokens.add(token);
      } catch (InvalidMediaTypeException ex) {
        tokens.add(WILDCARD);
      }
    }

    if (tokens.isEmpty()) {
      tokens.add(WILDCARD);
    }
    return String.join(", ", tokens);
  }

  /** Wraps a request to serve a corrected value for the {@code Accept} header only. */
  private static final class SanitizedAcceptRequestWrapper extends HttpServletRequestWrapper {

    private final String sanitizedAccept;

    private SanitizedAcceptRequestWrapper(HttpServletRequest request, String sanitizedAccept) {
      super(request);
      this.sanitizedAccept = sanitizedAccept;
    }

    @Override
    public String getHeader(String name) {
      if (HttpHeaders.ACCEPT.equalsIgnoreCase(name)) {
        return sanitizedAccept;
      }
      return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      if (HttpHeaders.ACCEPT.equalsIgnoreCase(name)) {
        return Collections.enumeration(List.of(sanitizedAccept));
      }
      return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      Enumeration<String> original = super.getHeaderNames();
      if (original == null) {
        return null;
      }
      List<String> names = new ArrayList<>();
      boolean hasAccept = false;
      while (original.hasMoreElements()) {
        String name = original.nextElement();
        names.add(name);
        if (HttpHeaders.ACCEPT.equalsIgnoreCase(name)) {
          hasAccept = true;
        }
      }
      if (!hasAccept) {
        names.add(HttpHeaders.ACCEPT);
      }
      return Collections.enumeration(names);
    }
  }
}
