package com.mw.recommendation.engine.rabbitmq;

import static org.mockito.Mockito.*;

import com.mw.recommendation.engine.dto.ExternalBookingMessageDTO;
import com.mw.recommendation.engine.service.BookingProcessingService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class BookingMessageConsumerTest {

  @Mock private BookingProcessingService bookingProcessingService;

  @Mock private Validator validator;

  @Mock private RedisTemplate<String, String> redisTemplate;

  @Mock private ValueOperations<String, String> valueOperations;

  @InjectMocks private BookingMessageConsumer bookingMessageConsumer;

  private ExternalBookingMessageDTO testMessage;

  @BeforeEach
  void setUp() {
    testMessage = ExternalBookingMessageDTO.builder().id("message-1").dealId("deal-123").build();
  }

  @Test
  void testProcess_WithValidMessage_CallsService() {
    // Act
    bookingMessageConsumer.process(testMessage);

    // Assert
    verify(bookingProcessingService).processBookingMessage(testMessage);
  }

  @Test
  void testProcess_AlwaysCallsService() {
    // Note: The process() method doesn't validate - it just delegates to the service.
    // Validation happens in the consume() method. This test verifies that process()
    // directly calls the service.
    // Act
    bookingMessageConsumer.process(testMessage);

    // Assert - process() should call the service directly
    verify(bookingProcessingService).processBookingMessage(testMessage);
  }
}
