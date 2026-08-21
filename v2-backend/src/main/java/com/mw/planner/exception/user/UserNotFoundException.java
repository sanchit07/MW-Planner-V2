package com.mw.planner.exception.user;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class UserNotFoundException extends BaseException {
  public UserNotFoundException(String username) {
    super(ErrorCode.USER_NOT_FOUND, "User not found with ID/Username: " + username, username);
  }

  public UserNotFoundException(String username, Throwable cause) {
    super(
        ErrorCode.USER_NOT_FOUND, "User not found with ID/Username: " + username, cause, username);
  }
}
