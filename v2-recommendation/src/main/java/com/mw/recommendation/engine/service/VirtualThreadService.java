package com.mw.recommendation.engine.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Service for managing virtual thread operations. Provides methods to run async tasks using virtual
 * threads for optimal performance.
 */
@Getter
@Slf4j
@Service
public class VirtualThreadService {

  /**
   * -- GETTER -- Get the virtual thread executor for direct use.
   *
   * @return The virtual thread executor
   */
  @Autowired
  @Qualifier("virtualThreadTaskExecutor")
  private Executor virtualThreadExecutor;

  /**
   * -- GETTER -- Get the transcoding executor for direct use.
   *
   * @return The transcoding virtual thread executor
   */
  @Autowired
  @Qualifier("transcodingTaskExecutor")
  private Executor transcodingExecutor;

  /**
   * -- GETTER -- Get the RabbitMQ executor for direct use.
   *
   * @return The RabbitMQ virtual thread executor
   */
  @Autowired
  @Qualifier("rabbitmqTaskExecutor")
  private Executor rabbitmqExecutor;

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
   * Run a transcoding task asynchronously using virtual threads. Optimized for CPU-intensive
   * transcoding operations.
   *
   * @param task The transcoding task to run
   * @return CompletableFuture for the task
   */
  public CompletableFuture<Void> runTranscodingAsync(Runnable task) {
    log.debug("Running transcoding task asynchronously on virtual thread");
    return CompletableFuture.runAsync(task, transcodingExecutor);
  }

  /**
   * Run a RabbitMQ message processing task asynchronously using virtual threads. Optimized for
   * message consumption and processing.
   *
   * @param task The message processing task to run
   * @return CompletableFuture for the task
   */
  public CompletableFuture<Void> runRabbitmqAsync(Runnable task) {
    log.debug("Running RabbitMQ task asynchronously on virtual thread");
    return CompletableFuture.runAsync(task, rabbitmqExecutor);
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
}
