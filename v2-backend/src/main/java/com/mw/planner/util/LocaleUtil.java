package com.mw.planner.util;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.http.HttpHeaders;

public final class LocaleUtil {

  private LocaleUtil() {}

  public static Locale resolve(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
    if (header != null && !header.isBlank()) {
      return parse(header);
    }
    return Locale.ENGLISH;
  }

  private static Locale parse(String value) {
    String primaryTag = value.split(",")[0].split(";")[0].trim();
    Locale locale = Locale.forLanguageTag(primaryTag);
    return Locale.ROOT.equals(locale) ? Locale.ENGLISH : locale;
  }
}
