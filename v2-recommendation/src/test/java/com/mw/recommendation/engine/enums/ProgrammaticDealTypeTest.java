package com.mw.recommendation.engine.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProgrammaticDealTypeTest {

  @Test
  void toValue_returnsLowercase() {
    assertEquals("guaranteed", ProgrammaticDealType.GUARANTEED.toValue());
    assertEquals("preferred_deal", ProgrammaticDealType.PREFERRED_DEAL.toValue());
    assertEquals("private_auction", ProgrammaticDealType.PRIVATE_AUCTION.toValue());
    assertEquals("open_auction", ProgrammaticDealType.OPEN_AUCTION.toValue());
    assertEquals("evergreen_pmp", ProgrammaticDealType.EVERGREEN_PMP.toValue());
  }

  @Test
  void fromValue_acceptsLowercase() {
    assertEquals(ProgrammaticDealType.GUARANTEED, ProgrammaticDealType.fromValue("guaranteed"));
    assertEquals(
        ProgrammaticDealType.PREFERRED_DEAL, ProgrammaticDealType.fromValue("preferred_deal"));
    assertEquals(
        ProgrammaticDealType.PRIVATE_AUCTION, ProgrammaticDealType.fromValue("private_auction"));
    assertEquals(ProgrammaticDealType.OPEN_AUCTION, ProgrammaticDealType.fromValue("open_auction"));
    assertEquals(
        ProgrammaticDealType.EVERGREEN_PMP, ProgrammaticDealType.fromValue("evergreen_pmp"));
  }

  @Test
  void fromValue_acceptsUppercase() {
    assertEquals(ProgrammaticDealType.GUARANTEED, ProgrammaticDealType.fromValue("GUARANTEED"));
  }

  @Test
  void fromValue_nullOrBlank_returnsNull() {
    assertEquals(null, ProgrammaticDealType.fromValue(null));
    assertEquals(null, ProgrammaticDealType.fromValue(""));
    assertEquals(null, ProgrammaticDealType.fromValue("   "));
  }

  @Test
  void fromValue_invalid_throws() {
    assertThrows(IllegalArgumentException.class, () -> ProgrammaticDealType.fromValue("unknown"));
  }
}
