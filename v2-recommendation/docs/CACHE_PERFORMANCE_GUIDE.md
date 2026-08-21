# Cache Performance Guide - Smart Inventory-Based Caching

## ✅ NEW: Normalized Cache Key Strategy

**The cache now intelligently caches based on what actually affects reach/frequency:**

### Cache Key Components
```
sorted_inventory_IDs + start_date + end_date + duration + aggregate_flag
```

### What This Means

✅ **Cache HITS even when these change:**
- Budget amounts
- Budget allocation percentages  
- Campaign name/ID
- Geographic targeting parameters (already filtered at query time)
- Audience criteria (post-processing filters)

✅ **Cache MISS only when these change:**
- Different set of inventories
- Different date ranges
- Different duration values

---

## 🎯 Real-World Example

### Scenario: Optimizing the Same Inventory Set

**Request 1:** (Cache Miss - 18s)
```json
{
  "inventories": ["INV-001", "INV-002", "INV-003"],
  "startDate": "2026-04-01",
  "endDate": "2026-04-07",  
  "duration": 7,
  "budget": 10000,
  "allocation": {"digital": 60, "classic": 40}
}
```
→ **Time: ~18 seconds** (populating cache)

**Request 2:** Different budget, same inventories/dates (Cache Hit - 5s!)
```json
{
  "inventories": ["INV-001", "INV-002", "INV-003"],
  "startDate": "2026-04-01",
  "endDate": "2026-04-07",
  "duration": 7,
  "budget": 15000,  // ← CHANGED
  "allocation": {"digital": 70, "classic": 30}  // ← CHANGED
}
```
→ **Time: ~5-8 seconds** ✅ **Cache hit!**

**Request 3:** Different dates (Cache Miss - 18s)
```json
{
  "inventories": ["INV-001", "INV-002", "INV-003"],
  "startDate": "2026-04-08",  // ← CHANGED
  "endDate": "2026-04-14",    // ← CHANGED
  "duration": 7,
  "budget": 10000
}
```
→ **Time: ~18 seconds** (new date range = new cache entry)

---

## 💼 Common Workflow Benefits

### Workflow 1: Budget Optimization
**User iteratively adjusts budget to see different inventory recommendations**

1. First request: $10,000 budget → **18s** (cache miss)
2. Try $12,000 budget (same inventories, dates) → **5s** ✅
3. Try $8,000 budget (same inventories, dates) → **5s** ✅
4. Try $15,000 budget (same inventories, dates) → **5s** ✅

**Result:** 3 out of 4 requests are fast! 

### Workflow 2: Budget Allocation Tuning
**User adjusts digital vs classic budget split**

1. First request: 60% digital, 40% classic → **18s** (cache miss)
2. Try 70% digital, 30% classic → **5s** ✅
3. Try 50% digital, 50% classic → **5s** ✅
4. Try 80% digital, 20% classic → **5s** ✅

**Result:** 3 out of 4 requests are fast!

### Workflow 3: Multiple Campaigns, Same Inventory Pool
**Multiple users/campaigns targeting the same market segment**

1. Campaign A: Inventories in Singapore, Apr 1-7 → **18s** (cache miss)
2. Campaign B: Same inventories, Apr 1-7, different budget → **5s** ✅
3. Campaign C: Same inventories, Apr 1-7, different allocation → **5s** ✅

**Result:** Shared cache pool benefits all users!

### Workflow 4: Date Range Changes (Expected Cache Misses)
**User explores different campaign periods**

1. April 1-7  → **18s** (miss - new date range)
2. April 8-14 → **18s** (miss - new date range)  
3. April 1-7 again → **5s** ✅ (hit - in cache from step 1!)
4. April 15-21 → **18s** (miss - new date range)

**Result:** Each unique date range is cached separately

---

## Verifying Cache Performance

### 1. Check Application Logs

Look for these log patterns:

#### Cache Miss (First Request)
```
[CACHE-MISS] Calling Measure API for request hash: <hash>, aggregate: true
[MEASURE-API] Call completed in <time>ms for <n> inventories
```

#### Cache Hit (Subsequent Requests)
```
DEBUG o.s.cache.interceptor.CacheInterceptor : Cache hit for key '<key>' on cache 'measureReachFrequency'
```
*Note: This message appears only when `org.springframework.cache` is set to DEBUG level*

### 2. Monitor Redis Cache

Check if cache entries exist:
```bash
# Connect to Redis
redis-cli

# List all cache keys for this service
KEYS mw-recommendation-engine:measureReachFrequency::*

# Check TTL for a specific key
TTL "mw-recommendation-engine:measureReachFrequency::<key>"

# Check memory usage
INFO memory
```

### 3. Performance Profiler Output

The profiler summary includes timing breakdowns:
```
Step 6: Auto-select inventories - 18432ms (82%)
  └─ Measure API calls dominate this step
```

---

## Cache Configuration

**Cache Name:** `measureReachFrequency`  
**TTL:** 10 minutes  
**Prefix:** `mw-recommendation-engine:`  
**Key Pattern:** `<sorted_inventory_refs>_<start_date>_<end_date>_<duration>_<aggregate>`  

**Example Cache Key:**
```
mw-recommendation-engine:measureReachFrequency::INV-001,INV-002,INV-003_2026-04-01_2026-04-07_7_true
```

### Why This Key Strategy?

1. **Inventory IDs are sorted** → Order doesn't matter (INV-001,INV-002 = INV-002,INV-001)
2. **Date-specific** → Different dates = different availability/reach
3. **Duration matters** → 7-day vs 14-day campaigns have different reach curves
4. **Aggregate flag** → Aggregated vs detailed responses differ

### Why 10 Minutes TTL?
- Balances freshness with performance
- Availability data changes as new bookings come in
- Most campaign planning workflows complete within 10 minutes
- Prevents stale data from affecting recommendations
- Enough time for iterative budget/allocation optimization

---

## Troubleshooting

### Issue: All Requests Take 18+ Seconds

**Possible Causes:**

1. **Redis Not Connected**
   ```bash
   # Check Redis connection
   redis-cli PING
   # Expected: PONG
   ```
   
   **Solution:** Start Redis or check `application.yaml` configuration:
   ```yaml
   spring:
     data:
       redis:
         host: localhost
         port: 6379
   ```

2. **Cache Key Not Matching**
   - Campaign parameters changed between requests
   - Request payload differs (even slightly)
   - Different aggregate flag values
   
   **Verification:**
   ```java
   // Check cache keys in logs
   log.debug("Cache key: {}", request.hashCode() + "_" + aggregate);
   ```

3. **TTL Expired**
   - Last request was >10 minutes ago
   - Cache was manually flushed
   
   **Solution:** This is expected behavior. First request after TTL = cache miss.

4. **Redis Memory Full**
   ```bash
   redis-cli INFO memory | grep maxmemory
   ```
   
   **Solution:** 
   - Configure `maxmemory-policy` to `allkeys-lru` in redis.conf
   - Increase Redis memory limit

### Issue: Cache Not Being Used at All

**Diagnostic Steps:**

1. **Verify @EnableCaching is Active**
   ```bash
   # Look for this in startup logs:
   grep "EnableCaching" logs/application.log
   ```

2. **Check Cache Bean Registration**
   ```bash
   # Look for CacheManager bean
   curl localhost:8200/actuator/beans | jq '.contexts.application.beans.cacheManager'
   ```

3. **Verify Redis Connection**
   ```bash
   # Check application metrics
   curl localhost:8200/actuator/metrics/cache.gets
   ```

---

## Performance Optimization Tips

### 1. Batch Size Tuning
Current batch size for Round 1: **500 inventories per Measure API call**

Adjust in `RecommendationAsyncService`:
```java
private static final int MEASURE_BATCH_SIZE = 500; // Increase if API can handle more
```

### 2. Parallel Batch Execution
Current: Categories processed in parallel (Round 1)

Already optimized with:
- `VirtualThreadTaskExecutor` for parallel category processing
- Parallel batch execution via `CompletableFuture.allOf()`

### 3. Global Schedule Cache
Current: Enabled ✅

Prevents duplicate Measure API calls across rounds:
- Round 1: Fetches and caches schedules
- Round 3: Reuses cached schedules, only fetches new ones

### 4. Goal-Only Optimization
Current: Enabled ✅

Batches all qualified inventories in single call instead of 9 sequential calls per score band.

---

## Monitoring in Production

### Key Metrics to Track

1. **Cache Hit Rate**
   ```bash
   curl localhost:8200/actuator/metrics/cache.gets?tag=name:measureReachFrequency
   ```

2. **Average Response Time**
   - Cache hits: Should be ~5-8 seconds
   - Cache misses: ~18-20 seconds

3. **Redis Memory Usage**
   ```bash
   redis-cli INFO memory | grep used_memory_human
   ```

4. **Cache Evictions**
   ```bash
   redis-cli INFO stats | grep evicted_keys
   ```

### Recommended Alerts

- Cache hit rate drops below 50%
- Average response time exceeds 25 seconds
- Redis memory usage exceeds 80%
- Cache eviction rate spikes

---

## Expected Behavior Summary

| Scenario | Expected Time | Cache Status |
|----------|--------------|--------------|
| First request for campaign | 18-20s | MISS - Populating |
| Same request within 10min | 5-8s | HIT - Using cache |
| Request after 10min TTL | 18-20s | MISS - Expired |
| Different campaign params | 18-20s | MISS - Different key |
| Request with different aggregate flag | 18-20s | MISS - Different key |

---

## Additional Resources

- **Redis Configuration:** `/src/main/resources/application.yaml`
- **Cache Config:** `/src/main/java/com/mw/recommendation/engine/config/CacheConfig.java`
- **Measure API Client:** `/src/main/java/com/mw/recommendation/engine/service/MeasureApiClient.java`
- **Performance Profiler:** `/src/main/java/com/mw/recommendation/engine/util/PerformanceProfiler.java`
