package com.mw.planner.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Programmatic deal type. Enum constants in CAPS; API and DB use lowercase. */
@Getter
@RequiredArgsConstructor
public enum ProgrammaticDealType {
  GUARANTEED("guaranteed"),
  PREFERRED_DEAL("preferred_deal"),
  PRIVATE_AUCTION("private_auction"),
  OPEN_AUCTION("open_auction"),
  EVERGREEN_PMP("evergreen_pmp");

  private final String value;

  @JsonValue
  public String toValue() {
    return value;
  }

  @JsonCreator
  public static ProgrammaticDealType fromValue(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toLowerCase().replace("-", "_");
    return Arrays.stream(values())
        .filter(e -> e.value.equals(normalized))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown ProgrammaticDealType: " + value));
  }
}
