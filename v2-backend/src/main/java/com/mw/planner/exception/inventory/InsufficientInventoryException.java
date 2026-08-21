package com.mw.planner.exception.inventory;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class InsufficientInventoryException extends BaseException {
  public InsufficientInventoryException(String inventoryId, int requested, int available) {
    super(
        ErrorCode.INVENTORY_INSUFFICIENT_QUANTITY,
        "Insufficient inventory. Requested: " + requested + ", Available: " + available,
        inventoryId,
        requested,
        available);
  }
}
