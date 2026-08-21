package com.mw.recommendation.engine.exception.auth;

import com.mw.recommendation.engine.enums.ErrorCode;
import com.mw.recommendation.engine.exception.BaseException;

public class JwtNoSubscriptionsException extends BaseException {

  public JwtNoSubscriptionsException(String productId) {
    super(
        ErrorCode.JWT_NO_SUBSCRIPTIONS,
        "No subscription to product: " + productId + ". User has no subscriptions.",
        productId);
  }
}
