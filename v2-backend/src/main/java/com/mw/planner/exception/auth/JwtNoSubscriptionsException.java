package com.mw.planner.exception.auth;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class JwtNoSubscriptionsException extends BaseException {

  public JwtNoSubscriptionsException(String productId) {
    super(
        ErrorCode.JWT_NO_SUBSCRIPTIONS,
        "No subscription to product: " + productId + ". User has no subscriptions.",
        productId);
  }
}
