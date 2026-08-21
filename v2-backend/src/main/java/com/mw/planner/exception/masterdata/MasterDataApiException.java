package com.mw.planner.exception.masterdata;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;
import lombok.Getter;

@Getter
public class MasterDataApiException extends BaseException {

  private final ErrorCode errorCode;

  public MasterDataApiException(ErrorCode errorCode, String message) {
    super(errorCode, message);
    this.errorCode = errorCode;
  }

  public MasterDataApiException(ErrorCode errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
    this.errorCode = errorCode;
  }
}
