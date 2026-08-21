package com.mw.planner.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import java.io.IOException;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

/**
 * Custom Jackson serializer for GeoJsonPoint to handle Redis cache serialization. This ensures
 * consistent serialization format for GeoJsonPoint objects and handles type information for Redis
 * caching.
 */
public class GeoJsonPointSerializer extends JsonSerializer<GeoJsonPoint> {

  @Override
  public void serialize(GeoJsonPoint value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    if (value == null) {
      gen.writeNull();
      return;
    }

    // Serialize as GeoJSON format with coordinates array
    gen.writeStartObject();
    gen.writeStringField("type", "Point");
    gen.writeFieldName("coordinates");
    gen.writeStartArray();
    gen.writeNumber(value.getX());
    gen.writeNumber(value.getY());
    gen.writeEndArray();
    gen.writeEndObject();
  }

  @Override
  public void serializeWithType(
      GeoJsonPoint value, JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer)
      throws IOException {
    // Handle type information for Redis caching
    if (value == null) {
      gen.writeNull();
      return;
    }

    // For type-aware serialization, just delegate to the regular serialize method
    // The type information will be handled by the TypeSerializer automatically
    serialize(value, gen, serializers);
  }
}
