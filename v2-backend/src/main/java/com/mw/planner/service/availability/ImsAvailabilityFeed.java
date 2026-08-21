package com.mw.planner.service.availability;

import com.mw.planner.domain.Inventory;
import java.util.Map;

/**
 * Client for the IMS availability feed.
 *
 * <p>Production wires this to the real IMS availability endpoint; in environments without IMS
 * credentials, a deterministic feed simulation is used (same approach as the cinema IMS weekly
 * feed). The ingestion pipeline is identical either way.
 */
public interface ImsAvailabilityFeed {

  /**
   * Fetch the availability document for one inventory from IMS.
   *
   * @param externalId external inventory id
   * @param inventory the matching planner inventory record, or {@code null} when unknown locally
   * @return availability payload in the inventory-api response shape
   * @throws ImsFeedException when the feed cannot deliver data for this inventory
   */
  Map<String, Object> fetchAvailability(String externalId, Inventory inventory)
      throws ImsFeedException;

  /** Feed failure — surfaced in the sync status, never swallowed. */
  class ImsFeedException extends RuntimeException {
    public ImsFeedException(String message, Throwable cause) {
      super(message, cause);
    }

    public ImsFeedException(String message) {
      super(message);
    }
  }
}
