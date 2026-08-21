package com.mw.planner.exception.inventory;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class InventoryAlreadyExistsException extends BaseException {
  public InventoryAlreadyExistsException(String inventoryName) {
    super(
        ErrorCode.INVENTORY_ALREADY_EXISTS,
        "Inventory already exists: " + inventoryName,
        inventoryName);
  }
}
