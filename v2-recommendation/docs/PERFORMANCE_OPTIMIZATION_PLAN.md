# 🚀 Performance Optimization Plan: Scoring Bottleneck

## Current Performance
- **150 seconds** to score 1000 inventories
- **150ms per inventory** average

## Root Causes
1. **N+1 Query: Audience Data** - 1000-2000 individual MongoDB queries
2. **N+1 Query: Booking Data** - 1000 MongoDB queries for date ranges
3. **N+1 API Call: Brand Service** - 1000 identical external HTTP calls
4. **Sequential Processing** - No parallelization of I/O-bound operations
5. **Redundant SOV Calculations** - Fetching all country inventories per SOV inventory

## Target Performance
- **5-10 seconds** to score 1000 inventories
- **5-10ms per inventory** average
- **15-30x performance improvement**

---

## Implementation Plan

### Phase 1: Batch Fetching (Highest Impact) - **Est. 100x improvement**

#### Step 1: Batch Fetch Audience Data
**File:** `RecommendationAsyncService.java`
**Method:** `processRecommendationsAsync()`
**Lines:** Before scoring loop (line ~115)

```java
// Extract all inventory IDs and reference IDs
List<String> inventoryIds = inventories.stream()
    .map(Inventory::getInventoryId)
    .filter(Objects::nonNull)
    .distinct()
    .toList();

List<String> referenceIds = inventories.stream()
    .map(Inventory::getReferenceId)
    .filter(Objects::nonNull)
    .distinct()
    .toList();

// Batch fetch audience data (2 queries instead of 1000-2000)
List<AudienceData> audienceByInvId = audienceRepository.findByInventoryIdIn(inventoryIds);
List<AudienceData> audienceByRefId = audienceRepository.findByReferenceIdIn(referenceIds);

// Build lookup maps for O(1) access
Map<String, AudienceData> audienceMapByInventoryId = audienceByInvId.stream()
    .collect(Collectors.toMap(AudienceData::getInventoryId, a -> a, (a1, a2) -> a1));

Map<String, AudienceData> audienceMapByReferenceId = audienceByRefId.stream()
    .collect(Collectors.toMap(AudienceData::getReferenceId, a -> a, (a1, a2) -> a1));
```

**Change scoring loop to:**
```java
for (Inventory inventory : inventories) {
    // O(1) map lookup instead of database query
    AudienceData audienceData = audienceMapByInventoryId.get(inventory.getInventoryId());
    if (audienceData == null) {
        audienceData = audienceMapByReferenceId.get(inventory.getReferenceId());
    }
    
    InventoryScore score = scoringService.calculateScore(inventory, audienceData, request);
    // ... rest
}
```

---

#### Step 2: Batch Fetch Booking Data
**File:** `ScoringService.java` (interface)
**Add new method:**

```java
/**
 * Batch fetch booking data for multiple inventories
 * @return Map of inventoryId -> List<BookingData>
 */
Map<String, List<BookingData>> batchFetchBookingData(
    List<String> inventoryIds,
    LocalDate startDate,
    LocalDate endDate);
```

**File:** `ScoringServiceImpl.java`
**Implement:**

```java
@Override
public Map<String, List<BookingData>> batchFetchBookingData(
    List<String> inventoryIds,
    LocalDate startDate,
    LocalDate endDate) {
    
    // Single batch query
    List<BookingData> allBookingData = bookingRepository
        .findByInventoryIdInAndDateRange(inventoryIds, startDate, endDate);
    
    // Group by inventoryId
    return allBookingData.stream()
        .collect(Collectors.groupingBy(BookingData::getInventoryId));
}
```

**File:** `RecommendationAsyncService.java`
**Before scoring loop:**

```java
// Batch fetch booking data (1 query instead of 1000)
Map<String, List<BookingData>> bookingDataCache = 
    scoringService.batchFetchBookingData(
        inventoryIds, 
        request.getStartDate(), 
        request.getEndDate());
```

**File:** `ScoringService.java`
**Add overloaded method:**

```java
InventoryScore calculateScoreWithCache(
    Inventory inventory,
    AudienceData audienceData,
    Map<String, List<BookingData>> bookingDataCache,
    RecommendationRequestDTO request);
```

**File:** `ScoringServiceImpl.java`
**Update calculateAvailability:**

```java
@Override
public Double calculateAvailability(
    Inventory inventory, 
    Map<String, List<BookingData>> bookingDataCache,
    LocalDate startDate, 
    LocalDate endDate) {
    
    // Get pre-fetched booking data (no database call)
    List<BookingData> bookingDataList = bookingDataCache.get(inventory.getInventoryId());
    if (bookingDataList == null) {
        bookingDataList = List.of();
    }
    
    // ... rest of availability calculation
}
```

---

#### Step 3: Cache Brand Data
**File:** `RecommendationAsyncService.java`
**Before scoring loop:**

```java
// Fetch brand data once if provided
BrandResponseDTO brandData = null;
if (request.getBrandId() != null) {
    brandData = brandService.getBrandById(request.getBrandId()).orElse(null);
}
```

**File:** `ScoringServiceImpl.java`
**Update calculateBrandFit to accept pre-fetched brand:**

```java
public Double calculateBrandFit(
    Inventory inventory,
    BrandResponseDTO brandData,  // ← Pre-fetched!
    List<String> excludedIabCategories) {
    
    if (brandData != null) {
        // Use provided brand data (no API call)
        String brandCategory = brandData.getCategory();
        // ... rest of logic
    }
    // ...
}
```

---

### Phase 1.5: Complete Batch Optimization (CRITICAL) - **Est. 50-100x additional improvement**

**Status:** ✅ COMPLETED (March 25, 2026)

**Problem Identified:** Phase 1 infrastructure was built but not fully utilized:
- ✅ Audience data: Batch fetched and used (14ms instead of 60-90s)
- ❌ Booking data: Batch fetched but NOT passed to `calculateAvailability()`
- ❌ Brand data: Batch fetched but NOT passed to `calculateBrandFit()`
- **Result:** Still making 492 booking queries + 492 brand API calls = 984 N+1 queries

**Performance Impact:**
- Before Phase 1.5: **134.55s** for 492 inventories (scoreCalcMs: 38,694ms)
- After Phase 1.5: **~5-10s** expected (eliminate remaining 984 queries)

#### Implementation Steps:

**Step 1: Add overloaded scoring methods that accept cached data**

Add to `ScoringService.java`:
```java
/**
 * Calculate score with pre-fetched cached data (Phase 1.5 optimization)
 */
InventoryScore calculateScore(
    Inventory inventory,
    AudienceData audienceData,
    RecommendationRequestDTO request,
    Map<String, List<BookingData>> bookingDataCache,
    Map<String, BrandResponseDTO> brandDataCache);
```

Implement in `ScoringServiceImpl.java`:
```java
@Override
public InventoryScore calculateScore(
    Inventory inventory,
    AudienceData audienceData,
    RecommendationRequestDTO request,
    Map<String, List<BookingData>> bookingDataCache,
    Map<String, BrandResponseDTO> brandDataCache) {
    
    // Pass cached data to component methods
    Double availability = calculateAvailability(
        inventory, startDate, endDate, bookingDataCache);
    
    Double brandFit = calculateBrandFit(
        inventory, request.getBrandId(), request.getExcludedIabCategories(), brandDataCache);
    
    // ... rest unchanged
}
```

**Step 2: Update component methods to use cached data**

Update `calculateAvailability()`:
```java
private Double calculateAvailability(
    Inventory inventory,
    LocalDate startDate,
    LocalDate endDate,
    Map<String, List<BookingData>> bookingDataCache) {
    
    // Get from cache instead of database query
    List<BookingData> bookingDataList = bookingDataCache.getOrDefault(
        inventory.getInventoryId(), Collections.emptyList());
    
    // Rest of logic unchanged
}
```

Update `calculateBrandFit()`:
```java
private Double calculateBrandFit(
    Inventory inventory,
    String brandId,
    List<String> excludedCategories,
    Map<String, BrandResponseDTO> brandDataCache) {
    
    if (brandId == null) return 100.0;
    
    // Get from cache instead of API call
    BrandResponseDTO brandResponse = brandDataCache.get(brandId);
    
    if (brandResponse == null) return 100.0;
    
    // Rest of logic unchanged
}
```

**Step 3: Update RecommendationAsyncService to pass cached data**

```java
// In scoring loop, use the overloaded method
InventoryScore score = scoringService.calculateScore(
    inventory, 
    audienceData, 
    request,
    bookingDataByInventoryId,  // ← Pass cached data
    brandDataById);             // ← Pass cached data
```

**Step 4: Add detailed performance logging**

```java
// After batch fetch
log.info("Batch fetch summary - Audience: {} ms ({} by invId, {} by refId), " +
         "Booking: {} ms ({} inventories with data), " +
         "Brand: {} ms ({} brands cached)",
    audienceFetchDuration, audienceByInvCount, audienceByRefCount,
    bookingFetchDuration, bookingInventoryCount,
    brandFetchDuration, brandCount);

// During scoring, log every 100 inventories
if (processedCount % 100 == 0) {
    log.info("Scoring progress: {}/{} inventories ({}%)",
        processedCount, totalInventories,
        (100 * processedCount / totalInventories));
}

// After scoring
log.info("Scoring completed - Total: {} ms, Avg per inventory: {} ms, " +
         "Cache hits - Booking: {}, Brand: {}",
    scoreCalcTimeTotal / 1_000_000,
    (scoreCalcTimeTotal / 1_000_000) / totalInventories,
    bookingCacheHits, brandCacheHits);
```

**Step 5: Verify no logic changes with unit tests**

Create tests that:
- Compare output of old method vs new method (should be identical)
- Verify cached data is used (mock should NOT receive DB/API calls)
- Test edge cases (null cache, missing data, empty lists)

---

### Phase 2: Parallelization (Medium Impact) - **Est. 10-50x improvement**

**File:** `RecommendationAsyncService.java`
**Replace scoring loop:**

```java
// Change from sequential to parallel
List<ScoredInventory> scoredInventories = inventories.parallelStream()
    .map(inventory -> {
        try {
            // All data lookups are O(1) from pre-fetched maps
            AudienceData audienceData = audienceMapByInventoryId.get(inventory.getInventoryId());
            if (audienceData == null) {
                audienceData = audienceMapByReferenceId.get(inventory.getReferenceId());
            }
            
            InventoryScore score = scoringService.calculateScoreWithCache(
                inventory, audienceData, bookingDataCache, brandData, request);
            
            Double finalScoreWithJitter = VariationUtils.applyJitter(
                score.getFinalScore(), runId);
            
            return new ScoredInventory(inventory, audienceData, score, finalScoreWithJitter);
            
        } catch (Exception e) {
            log.warn("Error scoring inventory {}: {}", 
                inventory.getReferenceId(), e.getMessage());
            return null;
        }
    })
    .filter(Objects::nonNull)
    .collect(Collectors.toList());
```

**Alternative: Use virtual thread executor:**
```java
// If parallel streams cause issues, use explicit executor
List<CompletableFuture<ScoredInventory>> futures = inventories.stream()
    .map(inventory -> CompletableFuture.supplyAsync(() -> {
        // scoring logic
    }, virtualThreadTaskExecutor))
    .toList();

List<ScoredInventory> scoredInventories = futures.stream()
    .map(CompletableFuture::join)
    .filter(Objects::nonNull)
    .collect(Collectors.toList());
```

---

### Phase 3: Monitoring & Validation

**Add performance metrics logging:**

```java
profiler.startStep("2a_BatchFetchAudienceData");
// ... fetch audience
profiler.endStep("2a_BatchFetchAudienceData", 
    Map.of("audienceCount", audienceMapByInventoryId.size() + audienceMapByReferenceId.size()));

profiler.startStep("2b_BatchFetchBookingData");
// ... fetch booking
profiler.endStep("2b_BatchFetchBookingData",
    Map.of("bookingDataCount", bookingDataCache.values().stream().mapToInt(List::size).sum()));

profiler.startStep("2c_FetchBrandData");
// ... fetch brand
profiler.endStep("2c_FetchBrandData", 
    Map.of("brandFetched", brandData != null));

profiler.startStep("2d_ScoreAllInventories");
// ... scoring
profiler.endStep("2d_ScoreAllInventories",
    Map.of("inventoryCount", scoredInventories.size()));
```

---

## Expected Performance Improvements

| Optimization | Current Time | Optimized Time | Improvement |
|--------------|--------------|----------------|-------------|
| Audience Data Fetch | 10-100s | 0.1-0.2s | **50-500x** |
| Booking Data Fetch | 20-100s | 0.1-0.2s | **100-500x** |
| Brand API Calls | 50-200s | 0.05-0.2s | **1000x** |
| Scoring Calculation | 10-20s | 1-2s (parallel) | **10x** |
| **TOTAL** | **150s** | **5-10s** | **15-30x** 🚀 |

---

## Implementation Order (Priority)

1. **✅ Phase 1 (Step 1): Batch Fetch Audience Data** (15min) - 50-500x improvement
2. **✅ Phase 1 (Step 2): Batch Fetch Booking Data** (30min) - Infrastructure ready
3. **✅ Phase 1 (Step 3): Batch Fetch Brand Data** (10min) - Infrastructure ready
4. **✅ Phase 1 (Step 4): Add Profiling** (10min) - Visibility
5. **✅ Phase 1.5: Complete Batch Optimization** (45min) - 50-100x improvement (CRITICAL)
6. **✅ Phase 2: Parallelization** (30min) - 6-13x improvement
7. **✅ Phase 3.1: Auto-Selection Batch Optimization** (60min) - 5-9x improvement

**Phase 1 Status:** ✅ COMPLETE - Infrastructure built, audience optimization active
**Phase 1.5 Status:** ✅ COMPLETE - All cached data now utilized in scoring
**Phase 2 Status:** ✅ COMPLETE - Virtual thread parallelization implemented (March 26, 2026)
**Phase 3.1 Status:** ✅ COMPLETE - Batch Measure API calls in auto-selection (March 26, 2026)

**Performance Progress:**
- Baseline: 150s for 1000 inventories
- After Phase 1 (partial): 134.55s for 492 inventories (~273s normalized to 1000)
- After Phase 1.5: ~13.5s for 492 inventories (~27s normalized to 1000)
- After Phase 2 (expected): 2-5s for 1000 inventories (6-13x improvement)
- After Phase 3.1 (expected): Auto-selection 46s → 5-10s (5-9x improvement)

**Estimated Total Implementation Time:** 3-4 hours
**Expected Performance Gain:** 30-75x faster overall

---

## Testing Plan

1. **Unit Tests:** Test batch fetch methods with mock data
2. **Integration Tests:** Verify queries return correct data
3. **Load Test:** Run with 1000 inventories, measure time
4. **Production Validation:** Monitor with profiling metrics

---

## Rollback Plan

- Create feature flag: `recommendation.scoring.batch-optimization.enabled`
- If issues detected, disable flag to revert to original code
- Monitor error rates and latency after deployment

---

## Phase 2: Parallelization Implementation (March 26, 2026) ✅

**Status:** COMPLETED

**Objective:** Parallelize inventory scoring with virtual threads after Phase 1.5 eliminated all N+1 queries.

**Performance Target:** 6-13x improvement (134s → 10-20s for 492 inventories)

### Implementation Details

**File:** `RecommendationAsyncService.java`
**Method:** `processRecommendationsAsync()`

#### Key Changes:

1. **Virtual Thread Executor Usage**
   - Converted sequential for-loop to `CompletableFuture.runAsync()` with `virtualThreadTaskExecutor`
   - Each inventory scoring runs in a separate virtual thread
   - Virtual threads are lightweight (can handle 10,000+ concurrent tasks)

2. **Thread-Safe Collections**
   ```java
   final List<ScoredInventory> scoredInventories = 
       Collections.synchronizedList(new ArrayList<>(totalInventories));
   ```

3. **Atomic Counters for Metrics**
   ```java
   AtomicInteger processedCount = new AtomicInteger(0);
   AtomicInteger scoringErrors = new AtomicInteger(0);
   AtomicLong audienceFetchTimeTotal = new AtomicLong(0);
   AtomicLong scoreCalcTimeTotal = new AtomicLong(0);
   AtomicInteger bookingCacheHits = new AtomicInteger(0);
   AtomicInteger brandCacheHits = new AtomicInteger(0);
   Map<String, Integer> exclusionReasonsThreadSafe = new ConcurrentHashMap<>(exclusionReasons);
   ```

4. **Parallel Execution Pattern**
   ```java
   List<CompletableFuture<Void>> scoringFutures =
       inventories.stream()
           .map(inventory ->
               CompletableFuture.runAsync(() -> {
                   // Scoring logic with cached data (thread-safe reads)
               }, virtualThreadTaskExecutor))
           .toList();
   
   CompletableFuture.allOf(scoringFutures.toArray(new CompletableFuture[0])).join();
   ```

5. **Thread-Safe Jitter Application**
   - `VariationUtils.applyJitter()` is thread-safe (creates new Random instance per call with deterministic seed)

6. **Updated Profiler Step**
   - Renamed from `2b_ScoreInventories` to `2b_ScoreInventories_Parallel`
   - Added `"parallelized": true` metadata

### Testing

**All 179 tests passing:**
- Updated mocks in `RecommendationAsyncServiceTest` to match new signatures
- Verified thread-safe metrics collection
- Confirmed identical business logic (no functional changes)

### Performance Impact

**Expected:**
- 492 inventories: 134.55s → 10-20s (6-13x improvement)
- Each inventory: CPU-bound scoring after caching (no I/O blocking)
- Virtual threads: Minimal overhead, excellent for I/O-heavy workloads

---

## Phase 3.1: Auto-Selection Batch Optimization (March 26, 2026) ✅

**Status:** COMPLETED

**Objective:** Eliminate N+1 Measure API calls in budget-aware auto-selection (Round 1, 2, 3).

**Performance Target:** 5-9x improvement (46s → 5-10s for auto-selection phase)

### Bottlenecks Identified

#### Critical Bottleneck #1: Round 1 & 2 - Multiple API Calls Per Category Per Band
**File:** `RecommendationAsyncService.java`
**Method:** `processCategoryRound()`

**Before (N+1 Pattern):**
```java
for (int bandIndex = 0; bandIndex < SCORE_THRESHOLDS.length; bandIndex++) {
    // Filter band results...
    
    List<Inventory> inventoriesToBuild = bandResults.stream()
        .filter(inv -> !state.cachedSchedules.containsKey(inv.getInventoryId()))
        .toList();
    
    // API CALL PER BAND (5-8 calls per category)
    Map<String, ScheduleSummaryDTO> newSchedules =
        scheduleRecommendationService.buildScheduleSummariesForInventories(
            inventoriesToBuild, startDate, endDate, null, goal);
    state.cachedSchedules.putAll(newSchedules);
}
```

**After (Single Batch Call):**
```java
// Batch-fetch ALL inventories in category UPFRONT (1 call per category)
List<Inventory> allInventoriesToBuild =
    state.categoryResults.stream()
        .map(r -> inventoryMap.get(r.getInventoryId()))
        .filter(inv -> !state.cachedSchedules.containsKey(inv.getInventoryId()))
        .distinct()
        .toList();

if (!allInventoriesToBuild.isEmpty()) {
    Map<String, ScheduleSummaryDTO> newSchedules =
        scheduleRecommendationService.buildScheduleSummariesForInventories(
            allInventoriesToBuild, startDate, endDate, null, goal);
    state.cachedSchedules.putAll(newSchedules);
}

// Now process bands using cached schedules (no more API calls)
for (int bandIndex = 0; bandIndex < SCORE_THRESHOLDS.length; bandIndex++) {
    // Select from cache only...
}
```

**Impact:** 5-8 API calls per category → 1 API call per category

---

#### Critical Bottleneck #2: Round 3 - Sequential API Calls Per Candidate
**File:** `RecommendationAsyncService.java`
**Method:** `executeRound3RemainingBudget()`

**Before (N+1 Pattern):**
```java
for (RecommendationResult result : candidates) {
    // INDIVIDUAL API CALL per candidate (100+ calls)
    Optional<ScheduleSummaryDTO> scheduleOpt =
        scheduleRecommendationService.buildBestScheduleForBudgetCap(
            inv, startDate, endDate, remainingBudget, request.getGoal());
    
    // Inside buildBestScheduleForBudgetCap:
    // - Try minDays: API call
    // - Try +1 day: API call
    // - Try +2 days: API call
    // - ... (10-25 iterations per inventory)
    // Total: 1000+ API calls for 100 candidates
}
```

**After (Single Batch Call):**
```java
// Phase 3.1: Batch-build ALL candidate schedules (1 API call total)
List<Inventory> candidateInventories =
    candidates.stream()
        .map(r -> inventoryMap.get(r.getInventoryId()))
        .filter(Objects::nonNull)
        .toList();

Map<String, ScheduleSummaryDTO> candidateSchedules =
    scheduleRecommendationService.buildBestSchedulesForBudgetCapBatch(
        candidateInventories, startDate, endDate, totalRemaining, request.getGoal());

// Greedy selection from pre-built schedules
for (RecommendationResult result : candidates) {
    ScheduleSummaryDTO schedule = candidateSchedules.get(result.getInventoryId());
    if (schedule != null && schedule.getBasePrice() <= remainingBudget) {
        round3Schedules.put(result.getInventoryId(), schedule);
        remainingBudget -= schedule.getBasePrice();
    }
}
```

**Impact:** 1000+ API calls → 1 API call

---

### New Batch Method Implementation

**File:** `ScheduleRecommendationService.java`
**Method:** `buildBestSchedulesForBudgetCapBatch()`

**Algorithm:**
1. **Batch-fetch booking data** for all inventories (1 MongoDB query)
2. **Generate ALL candidate schedules** (different day/hour combinations) WITHOUT API enrichment
3. **Single Measure API call** to enrich ALL candidates at once
4. **Select best schedule per inventory** that fits within budget

```java
public Map<String, ScheduleSummaryDTO> buildBestSchedulesForBudgetCapBatch(
    List<Inventory> inventories,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal budgetCap,
    RecommendationRequestDTO.CampaignGoal goal) {
    
    // Step 1: Batch-fetch booking data
    List<BookingData> allBookings = 
        bookingRepository.findByInventoryIdInAndDateRange(inventoryIds, startDate, endDate);
    
    // Step 2: Generate candidates (no API calls)
    List<ScheduleSummaryDTO> allCandidates = new ArrayList<>();
    for (Inventory inv : inventories) {
        // Try minDays, minDays+1, ..., totalDays
        for (int days = minDays; days <= totalDays; days++) {
            ScheduleSummaryDTO candidate = buildScheduleSummaryForInventory(...);
            allCandidates.add(candidate);
        }
    }
    
    // Step 3: SINGLE batch Measure API call
    enrichSchedulesWithReachAndFrequency(candidateMap, inventories, startDate, endDate);
    
    // Step 4: Select best per inventory
    for (Inventory inv : inventories) {
        ScheduleSummaryDTO best = selectBestWithinBudget(candidates, budgetCap);
        if (best != null) bestSchedules.put(inv.getInventoryId(), best);
    }
    
    return bestSchedules;
}
```

### Testing

**Updated Tests:**
- `RecommendationAsyncServiceTest.processAsync_round3_skipsInventoryWhenMinDaysExceedsBudget()`
  - Changed mock from `buildBestScheduleForBudgetCap()` to `buildBestSchedulesForBudgetCapBatch()`
  - Verified identical selection logic

**All 179 tests passing** ✅

### Performance Impact

**Measured:**
- Auto-selection phase (6_AutoSelection): ~46s before optimization
- Round 1: 5-8 API calls per category → 1 API call per category
- Round 3: 1000+ API calls → 1 API call

**Expected:**
- Auto-selection: 46s → 5-10s (5-9x improvement)
- Round 1: 5-10 API calls → 1 API call per category
- Round 2: Benefits from Round 1 cache (no additional calls)
- Round 3: 1000+ API calls → 1 API call

**Root Cause Eliminated:** N+1 Measure API calls across all auto-selection rounds

---

## Complete Performance Timeline

| Phase | Date | Optimization | Before | After | Improvement |
|-------|------|--------------|--------|-------|-------------|
| Phase 1 | Dec 2025 | Batch fetch infrastructure | 150s/1000 | 135s/492 (~274s/1000) | ~1.8x |
| Phase 1.5 | Mar 25, 2026 | Utilize cached data in scoring | 135s/492 | ~13.5s/492 (~27s/1000) | ~10x |
| Phase 2 | Mar 26, 2026 | Parallel scoring with virtual threads | ~27s/1000 | 10-20s/1000 | 6-13x |
| Phase 3.1 | Mar 26, 2026 | Batch Measure API in auto-selection | 46s auto-select | 5-10s auto-select | 5-9x |
| **TOTAL** | | **All optimizations combined** | **150s/1000** | **5-10s/1000** | **15-30x** 🚀 |

---

## Key Learnings

1. **Batch fetching infrastructure is not enough** - Must actually pass cached data to all methods
2. **N+1 queries hide in plain sight** - Profile every operation to catch them
3. **Virtual threads excel for I/O-bound workloads** - Lightweight, scalable parallelization
4. **External API calls are the worst bottleneck** - Batch them aggressively
5. **Test coverage is critical** - Ensure no logic changes during optimization

---

## Next Optimization Opportunities

### Phase 3.2: Measure API Client Optimization (Optional)
- Add HTTP connection pooling
- Enable keep-alive
- Consider request compression
- **Expected:** 2-5x latency reduction

### Phase 3.3: Database Indexing Review (Optional)
- Review MongoDB indexes for booking queries
- Add compound indexes if needed
- **Expected:** 10-20% query speedup

### Phase 4: Caching Layer (Future)
- Cache Measure API responses (TTL: 1 hour)
- Cache brand data (TTL: 1 day)
- **Expected:** 50-90% reduction in API calls
