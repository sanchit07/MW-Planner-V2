package com.mw.recommendation.engine.exception.auth;

import com.mw.recommendation.engine.enums.ErrorCode;
import com.mw.recommendation.engine.exception.BaseException;
import java.util.List;

public class JwtSubscriptionMismatchException extends BaseException {

  public JwtSubscriptionMismatchException(String productId, List<String> subscriptions) {
    super(
        ErrorCode.JWT_SUBSCRIPTION_MISMATCH,
        "No subscription to product: " + productId + ". User subscriptions: " + subscriptions,
        productId,
        subscriptions);
  }
}
