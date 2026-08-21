package com.mw.planner.exception.configuration;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class ConfigurationValidationException extends BaseException {

  public ConfigurationValidationException(String reason) {
    super(ErrorCode.CONFIGURATION_VALIDATION_FAILED, "Invalid configuration: " + reason, reason);
  }
}
