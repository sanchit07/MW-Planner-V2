package com.mw.planner.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Custom validation annotation for BulkSelectCampaignInventoryRequestDTO. Ensures exactly one of
 * {@code inventoryIds} or {@code referenceIds} is provided.
 */
@Documented
@Constraint(validatedBy = BulkSelectCampaignInventoryValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidBulkSelectCampaignInventory {
  String message() default "validation.bulk_select_ids_choose_one";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
