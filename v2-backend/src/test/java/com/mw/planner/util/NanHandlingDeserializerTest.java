package com.mw.planner.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NanHandlingDeserializerTest {

  private NanHandlingDeserializer deserializer;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    deserializer = new NanHandlingDeserializer();
    objectMapper = new ObjectMapper();
    objectMapper.enable(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS);
  }

  @Test
  void deserialize_WithNull_ReturnsNull() throws Exception {
    JsonParser parser = objectMapper.getFactory().createParser("null");
    parser.nextToken();
    DeserializationContext context = objectMapper.getDeserializationContext();

    Map<String, Object> result = deserializer.deserialize(parser, context);

    assertThat(result).isNull();
  }

  @Test
  void deserialize_WithEmptyObject_ReturnsEmptyMap() throws Exception {
    JsonParser parser = objectMapper.getFactory().createParser("{}");
    parser.nextToken();
    DeserializationContext context = objectMapper.getDeserializationContext();

    Map<String, Object> result = deserializer.deserialize(parser, context);

    assertThat(result).isEmpty();
  }

  @Test
  void deserialize_WithDouble_ReturnsDouble() throws Exception {
    JsonParser parser = objectMapper.getFactory().createParser("{\"a\": 1.5}");
    parser.nextToken();
    DeserializationContext context = objectMapper.getDeserializationContext();

    Map<String, Object> result = deserializer.deserialize(parser, context);

    assertThat(result).containsEntry("a", 1.5);
  }

  @Test
  void deserialize_WithNaN_ConvertsToNull() throws Exception {
    JsonParser parser = objectMapper.getFactory().createParser("{\"a\": NaN}");
    parser.nextToken();
    DeserializationContext context = objectMapper.getDeserializationContext();

    Map<String, Object> result = deserializer.deserialize(parser, context);

    assertThat(result).containsEntry("a", null);
  }

  @Test
  void deserialize_WithInfinity_ConvertsToNull() throws Exception {
    JsonParser parser = objectMapper.getFactory().createParser("{\"a\": Infinity}");
    parser.nextToken();
    DeserializationContext context = objectMapper.getDeserializationContext();

    Map<String, Object> result = deserializer.deserialize(parser, context);

    assertThat(result).containsEntry("a", null);
  }

  @Test
  void deserialize_WithInt_ReturnsInt() throws Exception {
    JsonParser parser = objectMapper.getFactory().createParser("{\"a\": 42}");
    parser.nextToken();
    DeserializationContext context = objectMapper.getDeserializationContext();

    Map<String, Object> result = deserializer.deserialize(parser, context);

    assertThat(result).containsEntry("a", 42);
  }

  @Test
  void deserialize_WithLong_ReturnsLong() throws Exception {
    JsonParser parser = objectMapper.getFactory().createParser("{\"a\": 9999999999}");
    parser.nextToken();
    DeserializationContext context = objectMapper.getDeserializationContext();

    Map<String, Object> result = deserializer.deserialize(parser, context);

    assertThat(result).containsKey("a");
    assertThat(result.get("a")).isInstanceOf(Long.class);
  }

  @Test
  void deserialize_WithText_ReturnsString() throws Exception {
    JsonParser parser = objectMapper.getFactory().createParser("{\"a\": \"hello\"}");
    parser.nextToken();
    DeserializationContext context = objectMapper.getDeserializationContext();

    Map<String, Object> result = deserializer.deserialize(parser, context);

    assertThat(result).containsEntry("a", "hello");
  }

  @Test
  void deserialize_WithBoolean_ReturnsBoolean() throws Exception {
    JsonParser parser = objectMapper.getFactory().createParser("{\"a\": true}");
    parser.nextToken();
    DeserializationContext context = objectMapper.getDeserializationContext();

    Map<String, Object> result = deserializer.deserialize(parser, context);

    assertThat(result).containsEntry("a", true);
  }

  @Test
  void deserialize_WithArray_ReturnsList() throws Exception {
    JsonParser parser = objectMapper.getFactory().createParser("{\"a\": [1, 2]}");
    parser.nextToken();
    DeserializationContext context = objectMapper.getDeserializationContext();

    Map<String, Object> result = deserializer.deserialize(parser, context);

    assertThat(result).containsKey("a");
    assertThat(result.get("a")).isInstanceOf(java.util.List.class);
  }

  @Test
  void deserialize_WithNestedObject_ReturnsMap() throws Exception {
    JsonParser parser = objectMapper.getFactory().createParser("{\"a\": {\"b\": 1}}");
    parser.nextToken();
    DeserializationContext context = objectMapper.getDeserializationContext();

    Map<String, Object> result = deserializer.deserialize(parser, context);

    assertThat(result).containsKey("a");
    assertThat(result.get("a")).isInstanceOf(Map.class);
  }

  @Test
  void deserialize_WithMixedTypes_HandlesAll() throws Exception {
    JsonParser parser =
        objectMapper
            .getFactory()
            .createParser(
                "{\"num\": 3.14, \"int\": 10, \"str\": \"x\", \"bool\": false, \"nullVal\": null}");
    parser.nextToken();
    DeserializationContext context = objectMapper.getDeserializationContext();

    Map<String, Object> result = deserializer.deserialize(parser, context);

    assertThat(result).containsEntry("num", 3.14);
    assertThat(result).containsEntry("int", 10);
    assertThat(result).containsEntry("str", "x");
    assertThat(result).containsEntry("bool", false);
    assertThat(result).containsEntry("nullVal", null);
  }
}
