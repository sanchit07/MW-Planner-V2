package com.mw.planner.config;

import com.mw.planner.domain.Inventory;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

/**
 * Custom Mongo read conversions.
 *
 * <p>Inventory documents in Mongo store {@code operatingTimes} map keys either as weekday enum
 * names ("SUNDAY".."SATURDAY") or as numeric day indexes ("0".."6", 0 = Sunday) — the latter come
 * from external feeds (e.g. IMS sync). The default Spring conversion only understands enum names
 * and blows up entity hydration with "No enum constant Inventory.Weekday.0", turning every filter
 * request that touches such a document into a 500. This converter accepts both representations.
 *
 * <p>Similarly, {@code orientation} and {@code size} arrive from external feeds in lowercase (and
 * occasionally with values outside the enum, e.g. "square"); those are read case-insensitively and
 * unknown values map to {@code null} instead of failing the whole document.
 */
@Configuration
public class MongoConversionConfig {

  @Bean
  public MongoCustomConversions mongoCustomConversions() {
    return new MongoCustomConversions(
        List.of(
            new StringToWeekdayConverter(),
            new StringToOrientationConverter(),
            new StringToSizeConverter()));
  }

  @ReadingConverter
  static class StringToOrientationConverter implements Converter<String, Inventory.Orientation> {
    @Override
    public Inventory.Orientation convert(String source) {
      try {
        return Inventory.Orientation.valueOf(source.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        return null; // unknown value (e.g. "square") — leave unset rather than 500
      }
    }
  }

  @ReadingConverter
  static class StringToSizeConverter implements Converter<String, Inventory.Size> {
    @Override
    public Inventory.Size convert(String source) {
      try {
        return Inventory.Size.valueOf(source.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        return null;
      }
    }
  }

  @ReadingConverter
  static class StringToWeekdayConverter implements Converter<String, Inventory.Weekday> {
    @Override
    public Inventory.Weekday convert(String source) {
      String value = source.trim();
      if (value.chars().allMatch(Character::isDigit)) {
        int idx = Integer.parseInt(value);
        Inventory.Weekday[] days = Inventory.Weekday.values();
        return days[idx % days.length];
      }
      return Inventory.Weekday.valueOf(value.toUpperCase());
    }
  }
}
