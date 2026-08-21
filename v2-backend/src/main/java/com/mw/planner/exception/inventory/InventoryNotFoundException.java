package com.mw.planner.exception.inventory;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class InventoryNotFoundException extends BaseException {
  public InventoryNotFoundException(String inventoryId) {
    super(
        ErrorCode.INVENTORY_NOT_FOUND, "Inventory not found with ID: " + inventoryId, inventoryId);
  }
}
