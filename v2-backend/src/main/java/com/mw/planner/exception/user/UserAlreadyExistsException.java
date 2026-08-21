package com.mw.planner.exception.user;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class UserAlreadyExistsException extends BaseException {
  public UserAlreadyExistsException(String username) {
    super(
        ErrorCode.USER_ALREADY_EXISTS,
        "User with username " + username + " already exists",
        username);
  }

  public UserAlreadyExistsException(String field, String value) {
    super(
        ErrorCode.USER_ALREADY_EXISTS,
        "User with " + field + " " + value + " already exists",
        field,
        value);
  }
}
