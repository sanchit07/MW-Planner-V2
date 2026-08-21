package com.mw.planner.service;

import com.mw.planner.dto.sales.SalesPerformanceCompanyItemDTO;
import com.mw.planner.dto.sales.SalesPerformanceLocationItemDTO;
import com.mw.planner.dto.sales.SalesPerformanceTeamItemDTO;
import java.util.Comparator;

/**
 * Centralized, reusable sorting for Sales Performance Summary API.
 *
 * <p>Maps sort field names (including "advertiser name", "agency name") to a single comparator that
 * works across Location, Company, and Team item DTOs. No duplication of sort logic; one in-memory
 * sort, no extra DB calls.
 */
public final class SalesPerformanceSummarySort {

  private static final String DEFAULT_SORT_FIELD = "revenue";
  private static final String DEFAULT_SORT_DIR = "desc";

  private SalesPerformanceSummarySort() {}

  /**
   * Returns comparator for sales summary items (Location / Company / Team DTOs).
   *
   * @param sortBy API sort field (e.g. revenue, cost, country, name, adPlays, impressions, share,
   *     sov, conversion, utilization, countCampaigns, inventories, city). "advertiser name" and
   *     "agency name" are normalized to "name".
   * @param sortDir "asc" or "desc"; null/blank treated as desc.
   */
  public static Comparator<Object> getComparator(String sortBy, String sortDir) {
    String field = normalizeSortField(sortBy);
    boolean descending = isDescending(sortDir);

    Comparator<Object> byKey = comparatorForField(field);
    Comparator<Object> withNulls = Comparator.nullsLast(byKey);
    return descending ? withNulls.reversed() : withNulls;
  }

  public static String defaultSortField() {
    return DEFAULT_SORT_FIELD;
  }

  public static String defaultSortDir() {
    return DEFAULT_SORT_DIR;
  }

  private static String normalizeSortField(String sortBy) {
    if (sortBy == null || sortBy.isBlank()) {
      return DEFAULT_SORT_FIELD;
    }
    String n = sortBy.trim().toLowerCase().replaceAll("\\s+", "");
    if ("advertisername".equals(n) || "agencyname".equals(n)) {
      return "name";
    }
    return n;
  }

  private static boolean isDescending(String sortDir) {
    return sortDir == null || sortDir.isBlank() || "desc".equalsIgnoreCase(sortDir.trim());
  }

  private static Comparator<Object> comparatorForField(String field) {
    return Comparator.comparing(
        item -> sortKey(item, field), SalesPerformanceSummarySort::compareKeys);
  }

  /**
   * Extracts a comparable key for the given item and field. Returns null if the DTO does not
   * support the field (those items will be ordered last when using nullsLast).
   */
  private static Object sortKey(Object item, String field) {
    if (item instanceof SalesPerformanceLocationItemDTO loc) {
      return locationKey(loc, field);
    }
    if (item instanceof SalesPerformanceCompanyItemDTO comp) {
      return companyKey(comp, field);
    }
    if (item instanceof SalesPerformanceTeamItemDTO team) {
      return teamKey(team, field);
    }
    return null;
  }

  private static Object locationKey(SalesPerformanceLocationItemDTO loc, String field) {
    return switch (field) {
      case "conversion" -> loc.getConversion();
      case "cost" -> loc.getCost();
      case "countcampaigns" -> loc.getCountCampaigns();
      case "country" -> nullToEmpty(loc.getCountry());
      case "inventories" -> loc.getInventories();
      case "revenue" -> loc.getRevenue();
      case "utilization" -> loc.getUtilization();
      case "city" -> nullToEmpty(loc.getCity());
      default -> loc.getRevenue();
    };
  }

  private static Object companyKey(SalesPerformanceCompanyItemDTO comp, String field) {
    return switch (field) {
      case "name" -> nullToEmpty(comp.getName());
      case "revenue" -> comp.getRevenue();
      case "countcampaigns" -> comp.getCountCampaigns();
      case "share" -> comp.getShare();
      case "adplays" -> comp.getAdPlays() != null ? comp.getAdPlays() : null;
      case "impressions" -> comp.getImpressions() != null ? comp.getImpressions() : null;
      case "sov" -> comp.getSov();
      default -> comp.getRevenue();
    };
  }

  private static Object teamKey(SalesPerformanceTeamItemDTO team, String field) {
    return switch (field) {
      case "name" -> nullToEmpty(team.getName());
      case "countcampaigns" -> team.getCountCampaigns();
      case "revenue" -> team.getRevenue();
      case "conversion" -> team.getConversion();
      case "share" -> team.getShare();
      default -> team.getRevenue();
    };
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static int compareKeys(Object a, Object b) {
    if (a == null && b == null) return 0;
    if (a == null) return 1;
    if (b == null) return -1;
    if (a instanceof Number na && b instanceof Number nb) {
      return Double.compare(na.doubleValue(), nb.doubleValue());
    }
    if (a instanceof String sa && b instanceof String sb) {
      return sa.compareToIgnoreCase(sb);
    }
    if (a instanceof Comparable ca && b instanceof Comparable cb) {
      return ca.compareTo(cb);
    }
    return 0;
  }
}
