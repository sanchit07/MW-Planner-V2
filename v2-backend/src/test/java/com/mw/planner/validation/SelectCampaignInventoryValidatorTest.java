package com.mw.planner.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.planner.dto.SelectCampaignInventoryRequestDTO;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SelectCampaignInventoryValidatorTest {

  private SelectCampaignInventoryValidator validator;

  @Mock private ConstraintValidatorContext context;

  @BeforeEach
  void setUp() {
    validator = new SelectCampaignInventoryValidator();
    validator.initialize(null);
  }

  @Test
  void isValid_WhenRequestIsNull_ReturnsTrue() {
    assertThat(validator.isValid(null, context)).isTrue();
  }

  @Test
  void isValid_WhenOperationTypeIsNull_ReturnsTrue() {
    SelectCampaignInventoryRequestDTO request = new SelectCampaignInventoryRequestDTO();
    request.setOperationType(null);

    assertThat(validator.isValid(request, context)).isTrue();
  }

  @Test
  void isValid_WhenRequestIsValid_ReturnsTrue() {
    SelectCampaignInventoryRequestDTO request = new SelectCampaignInventoryRequestDTO();
    request.setOperationType(SelectCampaignInventoryRequestDTO.OperationType.SELECT);

    assertThat(validator.isValid(request, context)).isTrue();
  }
}
