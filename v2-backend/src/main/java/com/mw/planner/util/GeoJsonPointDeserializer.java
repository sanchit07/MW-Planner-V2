package com.mw.planner.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import java.io.IOException;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

/**
 * Custom Jackson deserializer for GeoJsonPoint to handle Redis cache deserialization. GeoJsonPoint
 * doesn't have a default constructor, so we need to provide custom deserialization.
 */
public class GeoJsonPointDeserializer extends JsonDeserializer<GeoJsonPoint> {

  @Override
  public GeoJsonPoint deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException, JsonProcessingException {
    JsonNode node = p.getCodec().readTree(p);

    // Handle different JSON structures that might represent GeoJsonPoint
    if (node.isObject()) {
      // Check if it's a GeoJSON format with coordinates array
      if (node.has("coordinates") && node.get("coordinates").isArray()) {
        JsonNode coordinates = node.get("coordinates");
        if (coordinates.size() >= 2) {
          double x = coordinates.get(0).asDouble();
          double y = coordinates.get(1).asDouble();
          return new GeoJsonPoint(x, y);
        }
      }
      // Check if it has x and y properties directly
      else if (node.has("x") && node.has("y")) {
        double x = node.get("x").asDouble();
        double y = node.get("y").asDouble();
        return new GeoJsonPoint(x, y);
      }
      // Check if it has longitude and latitude properties
      else if (node.has("longitude") && node.has("latitude")) {
        double longitude = node.get("longitude").asDouble();
        double latitude = node.get("latitude").asDouble();
        return new GeoJsonPoint(longitude, latitude);
      }
    }
    // Handle array format [longitude, latitude]
    else if (node.isArray() && node.size() >= 2) {
      double x = node.get(0).asDouble();
      double y = node.get(1).asDouble();
      return new GeoJsonPoint(x, y);
    }

    // If we can't parse it, return a default point
    return new GeoJsonPoint(0.0, 0.0);
  }

  @Override
  public GeoJsonPoint deserializeWithType(
      JsonParser p, DeserializationContext ctxt, TypeDeserializer typeDeserializer)
      throws IOException, JsonProcessingException {
    // Handle type information for Redis caching
    // For type-aware deserialization, just delegate to the regular deserialize method
    return deserialize(p, ctxt);
  }
}
