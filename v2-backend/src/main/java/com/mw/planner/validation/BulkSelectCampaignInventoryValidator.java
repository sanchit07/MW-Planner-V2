package com.mw.planner.validation;

import com.mw.planner.dto.BulkSelectCampaignInventoryRequestDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Custom validator for BulkSelectCampaignInventoryRequestDTO. Enforces that exactly one of {@code
 * inventoryIds} or {@code referenceIds} is provided - both present or neither present is invalid.
 */
public class BulkSelectCampaignInventoryValidator
    implements ConstraintValidator<
        ValidBulkSelectCampaignInventory, BulkSelectCampaignInventoryRequestDTO> {

  @Override
  public void initialize(ValidBulkSelectCampaignInventory constraintAnnotation) {
    // No initialization needed
  }

  @Override
  public boolean isValid(
      BulkSelectCampaignInventoryRequestDTO request, ConstraintValidatorContext context) {
    if (request == null) {
      return true; // Let other validators handle null checks
    }

    boolean hasInventoryIds =
        request.getInventoryIds() != null && !request.getInventoryIds().isEmpty();
    boolean hasReferenceIds =
        request.getReferenceIds() != null && !request.getReferenceIds().isEmpty();

    if (hasInventoryIds && hasReferenceIds) {
      buildViolation(context, "{validation.bulk_select_ids_choose_one}");
      return false;
    }

    if (!hasInventoryIds && !hasReferenceIds) {
      buildViolation(context, "{validation.bulk_select_ids_required}");
      return false;
    }

    return true;
  }

  private void buildViolation(ConstraintValidatorContext context, String messageTemplate) {
    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate(messageTemplate).addConstraintViolation();
  }
}
