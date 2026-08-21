package com.mw.planner.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Custom deserializer to handle NaN values in audienceSegments and other numeric fields */
public class NanHandlingDeserializer extends JsonDeserializer<Map<String, Object>> {

  @Override
  public Map<String, Object> deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    ObjectMapper mapper = (ObjectMapper) parser.getCodec();
    JsonNode node = mapper.readTree(parser);

    if (node.isNull()) {
      return null;
    }

    Map<String, Object> result = new HashMap<>();

    if (node.isObject()) {
      ObjectNode objectNode = (ObjectNode) node;
      Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();

      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        String key = field.getKey();
        JsonNode value = field.getValue();

        if (value.isNumber()) {
          // Handle numeric values, including NaN
          if (value.isDouble()) {
            double doubleValue = value.asDouble();
            if (Double.isNaN(doubleValue)) {
              result.put(key, null); // Convert NaN to null
            } else if (Double.isInfinite(doubleValue)) {
              result.put(key, null); // Convert Infinity to null
            } else {
              result.put(key, doubleValue);
            }
          } else if (value.isInt()) {
            result.put(key, value.asInt());
          } else if (value.isLong()) {
            result.put(key, value.asLong());
          } else {
            result.put(key, value.asText());
          }
        } else if (value.isTextual()) {
          result.put(key, value.asText());
        } else if (value.isBoolean()) {
          result.put(key, value.asBoolean());
        } else if (value.isNull()) {
          result.put(key, null);
        } else if (value.isArray()) {
          result.put(key, mapper.convertValue(value, Object.class));
        } else if (value.isObject()) {
          result.put(key, mapper.convertValue(value, Object.class));
        } else {
          result.put(key, value.asText());
        }
      }
    }

    return result;
  }
}
