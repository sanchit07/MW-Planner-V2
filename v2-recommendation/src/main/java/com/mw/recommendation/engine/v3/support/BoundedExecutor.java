package com.mw.recommendation.engine.v3.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Wraps an (unbounded, virtual-thread) executor with a semaphore so at most {@code permits} tasks
 * run concurrently. Used to gate DB-bound and external-API fan-out — unbounded virtual threads
 * against the finite MongoDB connection pool is the documented v1/v2 scalability risk.
 */
public class BoundedExecutor {

  private final Executor delegate;
  private final Semaphore permits;

  public BoundedExecutor(Executor delegate, int maxConcurrency) {
    this.delegate = delegate;
    this.permits = new Semaphore(Math.max(1, maxConcurrency));
  }

  /** Submits a task; the permit is acquired on the worker thread so callers never block. */
  public <T> CompletableFuture<T> submit(Supplier<T> task) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            permits.acquire();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted awaiting executor permit", e);
          }
          try {
            return task.get();
          } finally {
            permits.release();
          }
        },
        delegate);
  }

  /** Runs all suppliers with bounded concurrency and returns their results in order. */
  public <T> List<T> invokeAll(List<Supplier<T>> tasks) {
    List<CompletableFuture<T>> futures = new ArrayList<>(tasks.size());
    for (Supplier<T> task : tasks) {
      futures.add(submit(task));
    }
    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    List<T> results = new ArrayList<>(futures.size());
    for (CompletableFuture<T> f : futures) {
      results.add(f.join());
    }
    return results;
  }
}
