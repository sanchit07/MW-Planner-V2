package com.mw.planner.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CostSplit {
  MEDIA_OWNER("mediaOwnerId", false),
  SIZE("size", false),
  INVENTORY_TYPE("type", false),
  COUNTRY("country", true),
  STATE("state", true),
  CITY("city", true),
  VENUE_TYPE("venueType", false),
  CHANNEL("classification", false);

  private final String costSplitField;
  private final boolean supportsPopulation;

  public boolean supportsPopulation() {
    return supportsPopulation;
  }
}
