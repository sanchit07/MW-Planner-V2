package com.mw.planner.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Service for managing virtual thread operations. Provides methods to run async tasks using virtual
 * threads for optimal performance. Virtual threads (Project Loom) allow high concurrency with
 * minimal memory overhead.
 */
@Getter
@Slf4j
@Service
public class VirtualThreadService {

  @Autowired
  @Qualifier("virtualThreadTaskExecutor")
  private Executor virtualThreadExecutor;

  @Autowired
  @Qualifier("districtSyncTaskExecutor")
  private Executor districtSyncExecutor;

  /**
   * Run a task asynchronously using virtual threads.
   *
   * @param task The task to run
   * @return CompletableFuture for the task
   */
  public CompletableFuture<Void> runAsync(Runnable task) {
    log.debug("Running task asynchronously on virtual thread");
    return CompletableFuture.runAsync(task, virtualThreadExecutor);
  }

  /**
   * Run a district sync task asynchronously using virtual threads. Optimized for large-scale
   * district synchronization operations.
   *
   * @param task The district sync task to run
   * @return CompletableFuture for the task
   */
  public CompletableFuture<Void> runDistrictSyncAsync(Runnable task) {
    log.debug("Running district sync task asynchronously on virtual thread");
    return CompletableFuture.runAsync(task, districtSyncExecutor);
  }

  /**
   * Run a task asynchronously with custom executor.
   *
   * @param task The task to run
   * @param executor The executor to use
   * @return CompletableFuture for the task
   */
  public CompletableFuture<Void> runAsync(Runnable task, Executor executor) {
    log.debug("Running task asynchronously on custom virtual thread executor");
    return CompletableFuture.runAsync(task, executor);
  }

  /**
   * Run a supplier task asynchronously using virtual threads and return a result.
   *
   * @param <T> The result type
   * @param task The task to run
   * @return CompletableFuture with the result
   */
  public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
    log.debug("Running supplier task asynchronously on virtual thread");
    return CompletableFuture.supplyAsync(task, virtualThreadExecutor);
  }

  /**
   * Run a supplier task asynchronously with custom executor.
   *
   * @param <T> The result type
   * @param task The task to run
   * @param executor The executor to use
   * @return CompletableFuture with the result
   */
  public <T> CompletableFuture<T> supplyAsync(Supplier<T> task, Executor executor) {
    log.debug("Running supplier task asynchronously on custom virtual thread executor");
    return CompletableFuture.supplyAsync(task, executor);
  }
}
