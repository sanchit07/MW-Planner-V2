package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

  private MessageService messageService;

  @BeforeEach
  void setUp() {
    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("i18n/messages");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);

    messageService = new MessageService(messageSource);
  }

  @Test
  void testGreetingInEnglish() {
    String result = messageService.getMessage("greeting", Locale.ENGLISH);
    assertThat(result).isEqualTo("Hello");
  }

  @Test
  void testGreetingInFrench() {
    String result = messageService.getMessage("greeting", Locale.FRENCH);
    assertThat(result).isEqualTo("Bonjour");
  }

  @Test
  void testGreetingInJapanese() {
    String result = messageService.getMessage("greeting", Locale.JAPANESE);
    assertThat(result).isEqualTo("こんにちは");
  }

  @Test
  void testSimplifiedChineseMessages() {
    Locale locale = Locale.SIMPLIFIED_CHINESE; // zh_CN
    String result = messageService.getMessage("welcome", locale, "Alex", 2);
    assertThat(result).isEqualTo("Alex，你有2条新消息。");
  }

  @Test
  void testKoreanMessages() {
    Locale locale = Locale.KOREAN; // ko
    String result = messageService.getMessage("welcome", locale, "Alex", 2);
    assertThat(result).isEqualTo("Alex님, 2개의 새 메시지가 있습니다.");
  }

  @Test
  void testGermanMessages() {
    Locale locale = Locale.GERMAN; // de
    String result = messageService.getMessage("welcome", locale, "Alex", 2);
    assertThat(result).isEqualTo("Alex, Sie haben 2 neue Nachrichten.");
  }

  @Test
  void testSpanishMessages() {
    Locale locale = Locale.forLanguageTag("es"); // es
    String result = messageService.getMessage("welcome", locale, "Alex", 2);
    assertThat(result).isEqualTo("Alex, tienes 2 mensajes nuevos.");
  }

  @Test
  void testWelcomeMessageEnglish() {
    String result = messageService.getMessage("welcome", Locale.ENGLISH, "Alex", 3);
    assertThat(result).isEqualTo("Welcome, Alex! You have 3 new messages.");
  }

  @Test
  void testWelcomeMessageFrench() {
    String result = messageService.getMessage("welcome", Locale.FRENCH, "Alex", 5);
    assertThat(result).isEqualTo("Bienvenue, Alex! Vous avez 5 nouveaux messages.");
  }

  @Test
  void testWelcomeMessageJapanese() {
    String result = messageService.getMessage("welcome", Locale.JAPANESE, "Alex", 2);
    assertThat(result).isEqualTo("Alexさん、2件の新しいメッセージがあります。");
  }

  @Test
  void testFallbackForMissingKey() {
    String result = messageService.getMessage("nonexistent.key", Locale.ENGLISH);
    assertThat(result).isEqualTo("nonexistent.key"); // fallback to key
  }
}
