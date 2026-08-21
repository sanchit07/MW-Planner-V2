package com.mw.planner.exception.inventory;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

/**
 * Exception thrown when inventory import operations fail. Used for all inventory import related
 * operations including use, download, and delete.
 */
public class InventoryImportException extends BaseException {

  public InventoryImportException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }

  public InventoryImportException(ErrorCode errorCode, String message, Object... args) {
    super(errorCode, message, args);
  }

  public InventoryImportException(ErrorCode errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
  }

  public InventoryImportException(
      ErrorCode errorCode, String message, Throwable cause, Object... args) {
    super(errorCode, message, cause, args);
  }

  /**
   * Creates an exception for when inventory import is not found.
   *
   * @param importId Import ID that was not found
   * @return InventoryImportException
   */
  public static InventoryImportException notFound(String importId) {
    return new InventoryImportException(
        ErrorCode.INVENTORY_IMPORT_NOT_FOUND,
        "Inventory import not found with ID: " + importId,
        importId);
  }

  /**
   * Creates an exception for when inventory import is empty (no reference IDs).
   *
   * @param importId Import ID that is empty
   * @return InventoryImportException
   */
  public static InventoryImportException empty(String importId) {
    return new InventoryImportException(
        ErrorCode.INVENTORY_IMPORT_EMPTY,
        "No inventory reference IDs found in import: " + importId,
        importId);
  }

  /**
   * Creates an exception for when inventory is already selected in the campaign.
   *
   * @param referenceId Reference ID that is already selected
   * @return InventoryImportException
   */
  public static InventoryImportException alreadySelected(String referenceId) {
    return new InventoryImportException(
        ErrorCode.INVENTORY_IMPORT_ALREADY_SELECTED,
        "Inventory reference ID is already selected in the campaign: " + referenceId,
        referenceId);
  }

  /**
   * Creates an exception for when inventory import validation fails.
   *
   * @param message Validation error message
   * @return InventoryImportException
   */
  public static InventoryImportException validationFailed(String message) {
    return new InventoryImportException(
        ErrorCode.INVENTORY_IMPORT_VALIDATION_FAILED, "Validation failed: " + message, message);
  }

  /**
   * Creates an exception for when using inventory import fails.
   *
   * @param importId Import ID that failed to be used
   * @param cause The cause of the failure
   * @return InventoryImportException
   */
  public static InventoryImportException useFailed(String importId, Throwable cause) {
    return new InventoryImportException(
        ErrorCode.INVENTORY_IMPORT_USE_FAILED,
        "Failed to use inventory import with ID: " + importId,
        cause,
        importId);
  }

  /**
   * Creates an exception for when downloading inventory import CSV fails.
   *
   * @param importId Import ID that failed to be downloaded
   * @param cause The cause of the failure
   * @return InventoryImportException
   */
  public static InventoryImportException downloadFailed(String importId, Throwable cause) {
    return new InventoryImportException(
        ErrorCode.INVENTORY_IMPORT_DOWNLOAD_FAILED,
        "Failed to download inventory import CSV for ID: " + importId,
        cause,
        importId);
  }

  /**
   * Creates an exception for when deleting inventory import fails.
   *
   * @param importId Import ID that failed to be deleted
   * @param cause The cause of the failure
   * @return InventoryImportException
   */
  public static InventoryImportException deleteFailed(String importId, Throwable cause) {
    return new InventoryImportException(
        ErrorCode.INVENTORY_IMPORT_DELETE_FAILED,
        "Failed to delete inventory import with ID: " + importId,
        cause,
        importId);
  }
}
