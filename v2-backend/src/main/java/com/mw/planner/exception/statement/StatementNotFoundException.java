package com.mw.planner.exception.statement;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class StatementNotFoundException extends BaseException {
  public StatementNotFoundException(String statementId) {
    super(
        ErrorCode.STATEMENT_NOT_FOUND, "Statement not found with ID: " + statementId, statementId);
  }
}
