package com.mw.planner.exception.proposal;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class ProposalNotFoundException extends BaseException {
  public ProposalNotFoundException(String mediaOwnerId) {
    super(ErrorCode.PROPOSAL_NOT_FOUND, "Proposal not found with Media Owner ID: " + mediaOwnerId);
  }
}
