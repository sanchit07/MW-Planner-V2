package com.mw.planner.exception.statement;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

/** A statement a finance integration has confirmed is Locked in Planner (PRD §12.3). */
public class StatementLockedException extends BaseException {
  public StatementLockedException(String statementId) {
    super(
        ErrorCode.STATEMENT_LOCKED,
        "Statement " + statementId + " is locked — edit it in the finance system of record",
        statementId);
  }
}
