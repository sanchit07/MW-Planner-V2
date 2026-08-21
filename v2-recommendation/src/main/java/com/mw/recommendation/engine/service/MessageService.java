package com.mw.recommendation.engine.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

  private final MessageSource messageSource;

  public String getMessage(String key, Locale locale, Object... args) {
    try {
      return messageSource.getMessage(key, args, locale);
    } catch (Exception e) {
      log.warn("Failed to translate key '{}'", key);
      return key; // fallback if missing
    }
  }
}
