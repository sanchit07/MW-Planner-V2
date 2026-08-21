package com.mw.recommendation.engine.util;

import static org.junit.jupiter.api.Assertions.*;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.domain.RecommendationResult;
import com.mw.recommendation.engine.dto.RecommendationRequestDTO;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BudgetAllocationUtilsTest {

  @Test
  void getEffectiveBudgetAllocation_whenRequestHasNoAllocation_returnsDefaultByGoal() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setGoal(RecommendationRequestDTO.CampaignGoal.IMPRESSIONS);
    Map<String, Double> allocation = BudgetAllocationUtils.getEffectiveBudgetAllocation(request);
    assertNotNull(allocation);
    assertEquals(35.0, allocation.get("digital"));
    assertEquals(25.0, allocation.get("classic"));
    assertEquals(20.0, allocation.get("transit"));
  }

  @Test
  void getEffectiveBudgetAllocation_whenRequestHasNullGoal_returnsImpressionsDefault() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setGoal(null);
    Map<String, Double> allocation = BudgetAllocationUtils.getEffectiveBudgetAllocation(request);
    assertNotNull(allocation);
    assertEquals(35.0, allocation.get("digital"));
  }

  @Test
  void getEffectiveBudgetAllocation_whenUserProvidesAllocation_returnsUserMap() {
    RecommendationRequestDTO request = new RecommendationRequestDTO();
    request.setBudgetAllocation(Map.of("digital", 50.0, "classic", 30.0, "transit", 20.0));
    Map<String, Double> allocation = BudgetAllocationUtils.getEffectiveBudgetAllocation(request);
    assertNotNull(allocation);
    assertEquals(50.0, allocation.get("digital"));
    assertEquals(30.0, allocation.get("classic"));
    assertEquals(20.0, allocation.get("transit"));
  }

  @Test
  void getDefaultAllocationByGoal_reach_returnsReachDefaults() {
    Map<String, Double> allocation =
        BudgetAllocationUtils.getDefaultAllocationByGoal(
            RecommendationRequestDTO.CampaignGoal.REACH);
    assertNotNull(allocation);
    assertEquals(30.0, allocation.get("classic"));
    assertEquals(25.0, allocation.get("transit"));
    assertEquals(15.0, allocation.get("digital"));
  }

  @Test
  void getDefaultAllocationByGoal_adPlays_returnsAdPlaysDefaults() {
    Map<String, Double> allocation =
        BudgetAllocationUtils.getDefaultAllocationByGoal(
            RecommendationRequestDTO.CampaignGoal.AD_PLAYS);
    assertNotNull(allocation);
    assertEquals(40.0, allocation.get("digital"));
    assertEquals(30.0, allocation.get("network"));
  }

  @Test
  void getDefaultAllocationByGoal_sov_returnsSovDefaults() {
    Map<String, Double> allocation =
        BudgetAllocationUtils.getDefaultAllocationByGoal(RecommendationRequestDTO.CampaignGoal.SOV);
    assertNotNull(allocation);
    assertEquals(40.0, allocation.get("digital"));
    assertEquals(25.0, allocation.get("network"));
  }

  @Test
  void getDefaultAllocationByGoal_carbon_returnsCarbonDefaults() {
    Map<String, Double> allocation =
        BudgetAllocationUtils.getDefaultAllocationByGoal(
            RecommendationRequestDTO.CampaignGoal.CARBON);
    assertNotNull(allocation);
    assertEquals(45.0, allocation.get("classic"));
    assertEquals(30.0, allocation.get("transit"));
  }

  @Test
  void getAllocationKey_inventory_transitType_returnsTransit() {
    Inventory inv = new Inventory();
    inv.setType("Transit");
    inv.setClassification("OOH");
    assertEquals("transit", BudgetAllocationUtils.getAllocationKey(inv));
  }

  @Test
  void getAllocationKey_inventory_retailType_returnsRetail() {
    Inventory inv = new Inventory();
    inv.setType("Retail");
    assertEquals("retail", BudgetAllocationUtils.getAllocationKey(inv));
  }

  @Test
  void getAllocationKey_inventory_classicClassification_returnsClassic() {
    Inventory inv = new Inventory();
    inv.setType("OOH");
    inv.setClassification("Classic");
    assertEquals("classic", BudgetAllocationUtils.getAllocationKey(inv));
  }

  @Test
  void getAllocationKey_inventory_digitalClassification_returnsDigital() {
    Inventory inv = new Inventory();
    inv.setType("OOH");
    inv.setClassification("Digital");
    assertEquals("digital", BudgetAllocationUtils.getAllocationKey(inv));
  }

  @Test
  void getAllocationKey_inventory_null_returnsDigitalFallback() {
    assertEquals("digital", BudgetAllocationUtils.getAllocationKey((Inventory) null));
  }

  @Test
  void getAllocationKey_inventoryDetails_transitType_returnsTransit() {
    RecommendationResult.InventoryDetails details =
        RecommendationResult.InventoryDetails.builder()
            .type("Transit")
            .classification("Transit")
            .build();
    assertEquals("transit", BudgetAllocationUtils.getAllocationKey(details));
  }

  @Test
  void getAllocationKey_inventoryDetails_classicClassification_returnsClassic() {
    RecommendationResult.InventoryDetails details =
        RecommendationResult.InventoryDetails.builder()
            .type("OOH")
            .classification("Classic")
            .build();
    assertEquals("classic", BudgetAllocationUtils.getAllocationKey(details));
  }

  @Test
  void getAllocationKey_inventoryDetails_null_returnsDigitalFallback() {
    assertEquals(
        "digital",
        BudgetAllocationUtils.getAllocationKey((RecommendationResult.InventoryDetails) null));
  }

  @Test
  void calculateCategoryBudgets_withValidInputs_returnsCorrectBudgets() {
    BigDecimal totalBudget = new BigDecimal("10000.00");
    Map<String, Double> allocation = new LinkedHashMap<>();
    allocation.put("digital", 35.0);
    allocation.put("classic", 25.0);
    allocation.put("transit", 20.0);
    allocation.put("retail", 10.0);
    allocation.put("network", 7.0);
    allocation.put("audio", 0.0);
    allocation.put("experiential", 3.0);

    Map<String, BigDecimal> budgets =
        BudgetAllocationUtils.calculateCategoryBudgets(totalBudget, allocation);

    assertNotNull(budgets);
    assertEquals(7, budgets.size());
    assertEquals(new BigDecimal("3500.00"), budgets.get("digital"));
    assertEquals(new BigDecimal("2500.00"), budgets.get("classic"));
    assertEquals(new BigDecimal("2000.00"), budgets.get("transit"));
    assertEquals(new BigDecimal("1000.00"), budgets.get("retail"));
    assertEquals(new BigDecimal("700.00"), budgets.get("network"));
    assertEquals(new BigDecimal("0.00"), budgets.get("audio"));
    assertEquals(new BigDecimal("300.00"), budgets.get("experiential"));
  }

  @Test
  void calculateCategoryBudgets_withNullTotalBudget_returnsEmptyMap() {
    Map<String, BigDecimal> budgets =
        BudgetAllocationUtils.calculateCategoryBudgets(null, Map.of("digital", 35.0));
    assertTrue(budgets.isEmpty());
  }

  @Test
  void calculateCategoryBudgets_withNullAllocation_returnsEmptyMap() {
    Map<String, BigDecimal> budgets =
        BudgetAllocationUtils.calculateCategoryBudgets(new BigDecimal("10000"), null);
    assertTrue(budgets.isEmpty());
  }

  @Test
  void calculateCategoryBudgets_withEmptyAllocation_returnsEmptyMap() {
    Map<String, BigDecimal> budgets =
        BudgetAllocationUtils.calculateCategoryBudgets(new BigDecimal("10000"), Map.of());
    assertTrue(budgets.isEmpty());
  }

  @Test
  void calculateCategoryBudgets_withZeroPercentage_returnsZeroBudget() {
    Map<String, BigDecimal> budgets =
        BudgetAllocationUtils.calculateCategoryBudgets(
            new BigDecimal("10000"), Map.of("audio", 0.0));
    assertEquals(new BigDecimal("0.00"), budgets.get("audio"));
  }
}
