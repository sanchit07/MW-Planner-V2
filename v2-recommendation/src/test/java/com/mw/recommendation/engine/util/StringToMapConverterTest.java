package com.mw.recommendation.engine.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StringToMapConverterTest {

  private final StringToMapConverter converter = new StringToMapConverter();

  @Test
  void testConvert_WithValidString_ReturnsMap() {
    String source = "key1=value1;key2=value2;key3=value3";
    Map<String, String> result = converter.convert(source);

    assertNotNull(result);
    assertEquals(3, result.size());
    assertEquals("value1", result.get("key1"));
    assertEquals("value2", result.get("key2"));
    assertEquals("value3", result.get("key3"));
  }

  @Test
  void testConvert_WithEmptyString_ReturnsEmptyMap() {
    Map<String, String> result = converter.convert("");
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void testConvert_WithSinglePair_ReturnsMapWithOneEntry() {
    String source = "key1=value1";
    Map<String, String> result = converter.convert(source);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("value1", result.get("key1"));
  }

  @Test
  void testConvert_WithWhitespace_TrimsKeysAndValues() {
    String source = " key1 = value1 ; key2 = value2 ";
    Map<String, String> result = converter.convert(source);

    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals("value1", result.get("key1"));
    assertEquals("value2", result.get("key2"));
  }

  @Test
  void testConvert_WithInvalidPair_IgnoresInvalidPairs() {
    String source = "key1=value1;invalid;key2=value2";
    Map<String, String> result = converter.convert(source);

    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals("value1", result.get("key1"));
    assertEquals("value2", result.get("key2"));
  }

  @Test
  void testConvert_WithMissingValue_IgnoresPair() {
    String source = "key1=value1;key2=";
    Map<String, String> result = converter.convert(source);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("value1", result.get("key1"));
  }
}
