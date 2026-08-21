package com.mw.planner.exception.publicaccess;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class PublicAccessTokenNotFoundException extends BaseException {

  public PublicAccessTokenNotFoundException(String message) {
    super(ErrorCode.PUBLIC_ACCESS_TOKEN_NOT_FOUND, message);
  }

  public PublicAccessTokenNotFoundException(String tokenId, Throwable cause) {
    super(
        ErrorCode.PUBLIC_ACCESS_TOKEN_NOT_FOUND,
        "Public access token not found: " + tokenId,
        cause);
  }
}
