package com.mw.recommendation.engine.v3.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.recommendation.engine.domain.Inventory;
import org.junit.jupiter.api.Test;

class SellingTermsValidatorTest {

  private final SellingTermsValidator validator = new SellingTermsValidator();

  @Test
  void givenNoSellingTerm_whenValidate_thenValidWithDaysUnchanged() {
    Inventory inventory = new Inventory();

    SellingTermsValidator.Validation validation = validator.validate(inventory, 5, 30);

    assertThat(validation.valid()).isTrue();
    assertThat(validation.adjustedDays()).isEqualTo(5);
    assertThat(validation.minHoursPerDay()).isEqualTo(0);
    assertThat(validation.adjustments()).isEmpty();
  }

  @Test
  void givenMinDays7AndPlanned3_whenValidate_thenExtendedTo7WithAdjustment() {
    Inventory inventory = new Inventory();
    inventory.setSellingTerm(Inventory.SellingTerm.builder().minDays(7).build());

    SellingTermsValidator.Validation validation = validator.validate(inventory, 3, 30);

    assertThat(validation.valid()).isTrue();
    assertThat(validation.adjustedDays()).isEqualTo(7);
    assertThat(validation.adjustments()).isNotEmpty();
  }

  @Test
  void givenMinDaysExceedCampaignWindow_whenValidate_thenInvalid() {
    Inventory inventory = new Inventory();
    inventory.setSellingTerm(Inventory.SellingTerm.builder().minDays(40).build());

    SellingTermsValidator.Validation validation = validator.validate(inventory, 30, 30);

    assertThat(validation.valid()).isFalse();
    assertThat(validation.adjustedDays()).isEqualTo(0);
  }

  @Test
  void givenMinHours4_whenValidate_thenMinHoursPerDayIs4() {
    Inventory inventory = new Inventory();
    inventory.setSellingTerm(Inventory.SellingTerm.builder().minHours(4).build());

    SellingTermsValidator.Validation validation = validator.validate(inventory, 10, 30);

    assertThat(validation.valid()).isTrue();
    assertThat(validation.minHoursPerDay()).isEqualTo(4);
  }
}
