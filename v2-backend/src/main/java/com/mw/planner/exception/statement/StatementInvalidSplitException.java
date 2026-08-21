package com.mw.planner.exception.statement;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class StatementInvalidSplitException extends BaseException {
  public StatementInvalidSplitException(String reason) {
    super(ErrorCode.STATEMENT_INVALID_SPLIT, "Invalid statement split: " + reason, reason);
  }
}
