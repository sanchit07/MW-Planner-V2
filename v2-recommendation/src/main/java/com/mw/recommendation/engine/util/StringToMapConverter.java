package com.mw.recommendation.engine.util;

import java.util.HashMap;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;

public class StringToMapConverter implements Converter<String, Map<String, String>> {

  @Override
  public Map<String, String> convert(String source) {
    Map<String, String> map = new HashMap<>();
    if (!source.isEmpty()) {
      String[] pairs = source.split(";"); // Assuming semicolon as delimiter
      for (String pair : pairs) {
        String[] keyValue = pair.split("="); // Assuming equals sign as delimiter
        if (keyValue.length == 2) {
          map.put(keyValue[0].trim(), keyValue[1].trim());
        }
      }
    }
    return map;
  }
}
