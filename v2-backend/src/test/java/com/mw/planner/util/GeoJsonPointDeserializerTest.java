package com.mw.planner.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

/** Unit tests for GeoJsonPointDeserializer to ensure all branches are covered. */
class GeoJsonPointDeserializerTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    module.addDeserializer(GeoJsonPoint.class, new GeoJsonPointDeserializer());
    objectMapper.registerModule(module);
  }

  @Test
  @DisplayName("deserialize with GeoJSON coordinates array")
  void deserialize_WithCoordinatesArray_ReturnsGeoJsonPoint() throws Exception {
    String json = "{\"type\":\"Point\",\"coordinates\":[106.8,-6.2]}";
    GeoJsonPoint point = objectMapper.readValue(json, GeoJsonPoint.class);
    assertThat(point).isNotNull();
    assertThat(point.getX()).isEqualTo(106.8);
    assertThat(point.getY()).isEqualTo(-6.2);
  }

  @Test
  @DisplayName("deserialize with x and y properties")
  void deserialize_WithXAndY_ReturnsGeoJsonPoint() throws Exception {
    String json = "{\"x\":10.5,\"y\":20.3}";
    GeoJsonPoint point = objectMapper.readValue(json, GeoJsonPoint.class);
    assertThat(point).isNotNull();
    assertThat(point.getX()).isEqualTo(10.5);
    assertThat(point.getY()).isEqualTo(20.3);
  }

  @Test
  @DisplayName("deserialize with longitude and latitude properties")
  void deserialize_WithLongitudeLatitude_ReturnsGeoJsonPoint() throws Exception {
    String json = "{\"longitude\":139.77,\"latitude\":35.71}";
    GeoJsonPoint point = objectMapper.readValue(json, GeoJsonPoint.class);
    assertThat(point).isNotNull();
    assertThat(point.getX()).isEqualTo(139.77);
    assertThat(point.getY()).isEqualTo(35.71);
  }

  @Test
  @DisplayName("deserialize with array format [longitude, latitude]")
  void deserialize_WithArrayFormat_ReturnsGeoJsonPoint() throws Exception {
    String json = "[100.0, -5.0]";
    GeoJsonPoint point = objectMapper.readValue(json, GeoJsonPoint.class);
    assertThat(point).isNotNull();
    assertThat(point.getX()).isEqualTo(100.0);
    assertThat(point.getY()).isEqualTo(-5.0);
  }

  @Test
  @DisplayName("deserialize with unparseable structure returns default point")
  void deserialize_WithUnparseable_ReturnsDefaultPoint() throws Exception {
    String json = "{\"other\":\"value\"}";
    GeoJsonPoint point = objectMapper.readValue(json, GeoJsonPoint.class);
    assertThat(point).isNotNull();
    assertThat(point.getX()).isEqualTo(0.0);
    assertThat(point.getY()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("deserialize with coordinates array size < 2 returns default point")
  void deserialize_WithCoordinatesArraySizeOne_ReturnsDefaultPoint() throws Exception {
    String json = "{\"coordinates\":[1.0]}";
    GeoJsonPoint point = objectMapper.readValue(json, GeoJsonPoint.class);
    assertThat(point).isNotNull();
    assertThat(point.getX()).isEqualTo(0.0);
    assertThat(point.getY()).isEqualTo(0.0);
  }
}
