package com.mw.planner.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

/**
 * Test class to verify that GeoJsonPoint serialization and deserialization works correctly with the
 * custom Jackson serializers and deserializers.
 */
@ExtendWith(MockitoExtension.class)
class GeoJsonPointSerializationTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();

    // Register custom serializers and deserializers for GeoJsonPoint
    com.fasterxml.jackson.databind.module.SimpleModule geoJsonModule =
        new com.fasterxml.jackson.databind.module.SimpleModule();
    geoJsonModule.addSerializer(GeoJsonPoint.class, new GeoJsonPointSerializer());
    geoJsonModule.addDeserializer(GeoJsonPoint.class, new GeoJsonPointDeserializer());
    objectMapper.registerModule(geoJsonModule);
  }

  @Test
  @DisplayName("Should serialize and deserialize GeoJsonPoint correctly")
  void shouldSerializeAndDeserializeGeoJsonPoint() throws Exception {
    // Given
    GeoJsonPoint originalPoint = new GeoJsonPoint(12.345, 67.890);

    // When
    String json = objectMapper.writeValueAsString(originalPoint);
    GeoJsonPoint deserializedPoint = objectMapper.readValue(json, GeoJsonPoint.class);

    // Then
    assertThat(deserializedPoint).isNotNull();
    assertThat(deserializedPoint.getX()).isEqualTo(originalPoint.getX());
    assertThat(deserializedPoint.getY()).isEqualTo(originalPoint.getY());
  }

  @Test
  @DisplayName("Should handle null GeoJsonPoint")
  void shouldHandleNullGeoJsonPoint() throws Exception {
    // Given
    GeoJsonPoint originalPoint = null;

    // When
    String json = objectMapper.writeValueAsString(originalPoint);
    GeoJsonPoint deserializedPoint = objectMapper.readValue(json, GeoJsonPoint.class);

    // Then
    assertThat(deserializedPoint).isNull();
  }

  @Test
  @DisplayName("Should serialize null via serializeWithType")
  void shouldSerializeNullViaSerializeWithType() throws Exception {
    GeoJsonPointSerializer serializer = new GeoJsonPointSerializer();
    JsonFactory factory = new JsonFactory();
    java.io.StringWriter sw = new java.io.StringWriter();
    com.fasterxml.jackson.core.JsonGenerator gen = factory.createGenerator(sw);
    SerializerProvider provider = objectMapper.getSerializerProvider();
    TypeSerializer typeSer = mock(TypeSerializer.class);
    serializer.serializeWithType(null, gen, provider, typeSer);
    gen.close();
    assertThat(sw.toString()).isEqualTo("null");
  }

  @Test
  @DisplayName("Should serialize non-null GeoJsonPoint via serializeWithType")
  void shouldSerializeNonNullViaSerializeWithType() throws Exception {
    GeoJsonPoint point = new GeoJsonPoint(1.0, 2.0);
    GeoJsonPointSerializer serializer = new GeoJsonPointSerializer();
    JsonFactory factory = new JsonFactory();
    java.io.StringWriter sw = new java.io.StringWriter();
    com.fasterxml.jackson.core.JsonGenerator gen = factory.createGenerator(sw);
    SerializerProvider provider = objectMapper.getSerializerProvider();
    TypeSerializer typeSer = mock(TypeSerializer.class);
    serializer.serializeWithType(point, gen, provider, typeSer);
    gen.close();
    assertThat(sw.toString()).contains("\"type\":\"Point\"");
    assertThat(sw.toString()).contains("\"coordinates\"");
  }

  @Test
  @DisplayName("Should deserialize from different JSON formats")
  void shouldDeserializeFromDifferentJsonFormats() throws Exception {
    // Test GeoJSON format
    String geoJsonFormat = "{\"type\":\"Point\",\"coordinates\":[12.345,67.890]}";
    GeoJsonPoint point1 = objectMapper.readValue(geoJsonFormat, GeoJsonPoint.class);
    assertThat(point1.getX()).isEqualTo(12.345);
    assertThat(point1.getY()).isEqualTo(67.890);

    // Test x,y format
    String xyFormat = "{\"x\":12.345,\"y\":67.890}";
    GeoJsonPoint point2 = objectMapper.readValue(xyFormat, GeoJsonPoint.class);
    assertThat(point2.getX()).isEqualTo(12.345);
    assertThat(point2.getY()).isEqualTo(67.890);

    // Test longitude,latitude format
    String lngLatFormat = "{\"longitude\":12.345,\"latitude\":67.890}";
    GeoJsonPoint point3 = objectMapper.readValue(lngLatFormat, GeoJsonPoint.class);
    assertThat(point3.getX()).isEqualTo(12.345);
    assertThat(point3.getY()).isEqualTo(67.890);

    // Test array format
    String arrayFormat = "[12.345,67.890]";
    GeoJsonPoint point4 = objectMapper.readValue(arrayFormat, GeoJsonPoint.class);
    assertThat(point4.getX()).isEqualTo(12.345);
    assertThat(point4.getY()).isEqualTo(67.890);
  }
}
