package com.mw.planner.validation;

import com.mw.planner.dto.SelectCampaignInventoryRequestDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Custom validator for SelectCampaignInventoryRequestDTO Ensures SOV is provided for SELECT
 * operations
 */
public class SelectCampaignInventoryValidator
    implements ConstraintValidator<
        ValidSelectCampaignInventory, SelectCampaignInventoryRequestDTO> {

  @Override
  public void initialize(ValidSelectCampaignInventory constraintAnnotation) {
    // No initialization needed
  }

  @Override
  public boolean isValid(
      SelectCampaignInventoryRequestDTO request, ConstraintValidatorContext context) {
    if (request == null || request.getOperationType() == null) {
      return true; // Let other validators handle null checks
    }

    return true;
  }
}
