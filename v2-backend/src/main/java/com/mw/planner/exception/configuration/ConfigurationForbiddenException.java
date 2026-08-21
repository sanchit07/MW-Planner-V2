package com.mw.planner.exception.configuration;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

/** Raised when a caller without the required authority edits a gated configuration section. */
public class ConfigurationForbiddenException extends BaseException {

  public ConfigurationForbiddenException(ErrorCode errorCode, String reason) {
    super(errorCode, reason);
  }
}
