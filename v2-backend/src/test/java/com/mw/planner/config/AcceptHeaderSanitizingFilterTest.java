package com.mw.planner.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AcceptHeaderSanitizingFilterTest {

  private final AcceptHeaderSanitizingFilter filter = new AcceptHeaderSanitizingFilter();

  private String acceptSeenByChain(String acceptHeader) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (acceptHeader != null) {
      request.addHeader(HttpHeaders.ACCEPT, acceptHeader);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
    return forwarded.getHeader(HttpHeaders.ACCEPT);
  }

  @Test
  void malformedWildcardTokenIsRewrittenToWildcard() throws Exception {
    String result = acceptSeenByChain("application/json, text/plain, /");
    assertThat(result).isEqualTo("application/json, text/plain, */*");
  }

  @Test
  void wellFormedHeaderPassesThroughUntouched() throws Exception {
    String header = "application/json, text/plain, */*";
    String result = acceptSeenByChain(header);
    assertThat(result).isEqualTo(header);
  }

  @Test
  void allGarbageHeaderCollapsesToWildcard() throws Exception {
    String result = acceptSeenByChain("/, /, /");
    assertThat(result).isEqualTo("*/*");
  }

  @Test
  void absentAcceptHeaderIsLeftUnset() throws Exception {
    String result = acceptSeenByChain(null);
    assertThat(result).isNull();
  }
}
