package com.mw.recommendation.engine.config;

import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Configuration for virtual threads to handle async tasks efficiently. Virtual threads provide high
 * concurrency with minimal memory overhead.
 */
@Slf4j
@EnableAsync
@Configuration
public class VirtualThreadConfig implements AsyncConfigurer {

  /**
   * Virtual thread task executor for general async operations. Uses virtual threads for high
   * concurrency with minimal memory overhead.
   */
  @Bean("virtualThreadTaskExecutor")
  public VirtualThreadTaskExecutor virtualThreadTaskExecutor() {
    return new VirtualThreadTaskExecutor("creative-vt-");
  }

  /**
   * Virtual thread executor used for @Async methods (e.g. recommendation processing). Tasks are run
   * on virtual threads; wrapped with SecurityContext propagation in getAsyncExecutor().
   */
  @Bean("asyncVirtualThreadExecutor")
  public VirtualThreadTaskExecutor asyncVirtualThreadExecutor() {
    return new VirtualThreadTaskExecutor("async-vt-");
  }

  /**
   * Virtual thread task executor specifically for transcoding operations. Optimized for
   * CPU-intensive transcoding tasks.
   */
  @Bean("transcodingTaskExecutor")
  public VirtualThreadTaskExecutor transcodingTaskExecutor() {
    return new VirtualThreadTaskExecutor("transcoding-vt-");
  }

  /**
   * Virtual thread task executor for RabbitMQ message processing. Handles message consumption and
   * processing with virtual threads.
   */
  @Bean("rabbitmqTaskExecutor")
  public VirtualThreadTaskExecutor rabbitmqTaskExecutor() {
    return new VirtualThreadTaskExecutor("rabbitmq-vt-");
  }

  /**
   * Configure the default async executor: tasks run on virtual threads (asyncVirtualThreadExecutor)
   * with SecurityContext propagated from the calling thread so @Async methods see the same auth.
   */
  @Override
  public Executor getAsyncExecutor() {
    return wrapWithSecurityContextPropagation(asyncVirtualThreadExecutor());
  }

  /**
   * Wraps an executor so that the current SecurityContext is set on the thread that runs the task.
   * The task itself runs on whatever thread the delegate uses (here, a virtual thread from
   * asyncVirtualThreadExecutor).
   */
  private static Executor wrapWithSecurityContextPropagation(Executor delegate) {
    return runnable -> {
      SecurityContext context = SecurityContextHolder.getContext();
      delegate.execute(
          () -> {
            try {
              SecurityContextHolder.setContext(context);
              runnable.run();
            } finally {
              SecurityContextHolder.clearContext();
            }
          });
    };
  }

  /**
   * Configure exception handler for async operations. Provides centralized error handling for
   * virtual thread operations.
   */
  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return (ex, method, params) -> {
      log.error("Async method '{}' threw exception: {}", method.getName(), ex.getMessage(), ex);
    };
  }
}
