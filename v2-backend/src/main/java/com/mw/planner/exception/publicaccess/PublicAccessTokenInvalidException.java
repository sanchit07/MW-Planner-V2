package com.mw.planner.exception.publicaccess;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class PublicAccessTokenInvalidException extends BaseException {

  public PublicAccessTokenInvalidException(String message) {
    super(ErrorCode.PUBLIC_ACCESS_TOKEN_INVALID, message);
  }

  public PublicAccessTokenInvalidException(String message, Throwable cause) {
    super(ErrorCode.PUBLIC_ACCESS_TOKEN_INVALID, message, cause);
  }
}
