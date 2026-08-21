package com.mw.recommendation.engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Test class for VirtualThreadService to verify virtual thread functionality. */
@ExtendWith(MockitoExtension.class)
class VirtualThreadServiceTest {

  @Mock private Executor virtualThreadExecutor;

  @Mock private Executor transcodingExecutor;

  @Mock private Executor rabbitmqExecutor;

  @InjectMocks VirtualThreadService virtualThreadService;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(virtualThreadExecutor, transcodingExecutor, rabbitmqExecutor);
  }

  @Test
  void testRunAsync() {
    // Given
    Runnable task = () -> System.out.println("Test task");
    doAnswer(
            invocation -> {
              Runnable runnable = invocation.getArgument(0);
              runnable.run();
              return null;
            })
        .when(virtualThreadExecutor)
        .execute(any());

    // When
    CompletableFuture<Void> future = virtualThreadService.runAsync(task);

    // Then
    assertNotNull(future);
    verify(virtualThreadExecutor).execute(any());
  }

  @Test
  void testRunTranscodingAsync() {
    // Given
    Runnable task = () -> System.out.println("Transcoding task");
    doAnswer(
            invocation -> {
              Runnable runnable = invocation.getArgument(0);
              runnable.run();
              return null;
            })
        .when(transcodingExecutor)
        .execute(any());

    // When
    CompletableFuture<Void> future = virtualThreadService.runTranscodingAsync(task);

    // Then
    assertNotNull(future);
    verify(transcodingExecutor).execute(any());
  }

  @Test
  void testRunRabbitmqAsync() {
    // Given
    Runnable task = () -> System.out.println("RabbitMQ task");
    doAnswer(
            invocation -> {
              Runnable runnable = invocation.getArgument(0);
              runnable.run();
              return null;
            })
        .when(rabbitmqExecutor)
        .execute(any());

    // When
    CompletableFuture<Void> future = virtualThreadService.runRabbitmqAsync(task);

    // Then
    assertNotNull(future);
    verify(rabbitmqExecutor).execute(any());
  }

  @Test
  void testRunAsyncWithCustomExecutor() {
    // Given
    Executor customExecutor = mock(Executor.class);
    Runnable task = () -> System.out.println("Custom task");
    doAnswer(
            invocation -> {
              Runnable runnable = invocation.getArgument(0);
              runnable.run();
              return null;
            })
        .when(customExecutor)
        .execute(any());

    // When
    CompletableFuture<Void> future = virtualThreadService.runAsync(task, customExecutor);

    // Then
    assertNotNull(future);
    verify(customExecutor).execute(any());
  }

  @Test
  void testGetExecutors() {
    // When & Then
    assertEquals(virtualThreadExecutor, virtualThreadService.getVirtualThreadExecutor());
    assertEquals(transcodingExecutor, virtualThreadService.getTranscodingExecutor());
    assertEquals(rabbitmqExecutor, virtualThreadService.getRabbitmqExecutor());
  }
}
