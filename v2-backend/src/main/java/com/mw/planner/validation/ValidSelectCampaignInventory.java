package com.mw.planner.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/** Custom validation annotation for SelectCampaignInventoryRequestDTO */
@Documented
@Constraint(validatedBy = SelectCampaignInventoryValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSelectCampaignInventory {
  String message() default "validation.invalid_operation";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
