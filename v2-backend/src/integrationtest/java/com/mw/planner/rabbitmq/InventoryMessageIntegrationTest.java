package com.mw.planner.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.planner.TestcontainersConfiguration;
import com.mw.planner.dto.ExternalInventoryMessageDTO;
import com.mw.planner.dto.InventoryUpdateMessageDTO;
import com.mw.planner.repository.InventoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({TestcontainersConfiguration.class})
class InventoryMessageIntegrationTest {

  @Autowired private RabbitTemplate rabbitTemplate;

  @Autowired private InventoryRepository inventoryRepository;

  @Test
  void testInventoryMessageProcessing() throws InterruptedException {
    // Given
    InventoryUpdateMessageDTO message = createTestMessage();

    // When (fanout exchanges ignore routing keys, so using empty string)
    rabbitTemplate.convertAndSend("inventory.test.exchange", "", message);

    // Then
    // Wait for message processing with retry mechanism
    int maxAttempts = 15; // Increased timeout
    int attempt = 0;
    boolean inventoryFound = false;

    while (attempt < maxAttempts && !inventoryFound) {
      Thread.sleep(2000); // Increased sleep time
      inventoryFound = inventoryRepository.findFirstByReferenceId("TEST-REF-001").isPresent();
      attempt++;
    }

    // Verify inventory was saved
    assertThat(inventoryFound).isTrue();

    // Additional verification - check the inventory details
    var savedInventory = inventoryRepository.findFirstByReferenceId("TEST-REF-001");
    assertThat(savedInventory).isPresent();
    assertThat(savedInventory.get().getName()).isEqualTo("Test Billboard");
    assertThat(savedInventory.get().getReferenceId()).isEqualTo("TEST-REF-001");
  }

  private InventoryUpdateMessageDTO createTestMessage() {
    // Create the wrapper message
    InventoryUpdateMessageDTO wrapper = new InventoryUpdateMessageDTO();
    wrapper.setId("6ad1f3b5-833b-4774-989e-e746c8a14c19");
    wrapper.setInventoryId("4df21370-0bf6-4581-90bf-24e8c2d585db");
    wrapper.setOperation("refresh");
    wrapper.setOccurredAt(Instant.now().toString());

    // Create the detail snapshot (the actual inventory message)
    ExternalInventoryMessageDTO message = new ExternalInventoryMessageDTO();

    // Set required fields
    message.setId("507f1f77bcf86cd799439011");
    message.setName("Test Billboard");
    message.setTypeName("DIGITAL > LED");
    message.setEnvironment("OUTDOOR");
    message.setArchived(false);
    message.setCreatedAt(Instant.now().toString());
    message.setUpdatedAt(Instant.now().toString());
    message.setAddress("Test Address");
    message.setTimeZone("Asia/Jakarta");
    message.setOrientation("LANDSCAPE");
    message.setRequiresContentApproval(true);
    message.setAdminLevel0Name("Indonesia");
    message.setAdminLevel1Name("Jakarta");
    message.setAdminLevel2Name("Central Jakarta");
    message.setMediaOwnerId("media-owner-123");
    message.setMediaOwnerName("Test Media Owner");

    // Set external IDs and referenceId
    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setPlatform("TEST_PLATFORM");
    externalId.setExternalId("TEST-REF-001");
    message.setReferenceId("TEST-REF-001");
    message.setExternalIds(List.of(externalId));

    // Set geoms (location coordinates)
    message.setGeoms(List.of("POINT(106.740704 -6.10595)"));

    // Set panels
    ExternalInventoryMessageDTO.Panel panel = new ExternalInventoryMessageDTO.Panel();
    panel.setPixelWidth(1920);
    panel.setPixelHeight(1080);
    panel.setPhysicalWidth(10.0);
    panel.setPhysicalHeight(5.0);
    panel.setPanelCount(1);
    message.setPanels(List.of(panel));

    // Set schedule (for operating times)
    // Day numbers: 0 = Sunday, 1 = Monday, ..., 6 = Saturday
    ExternalInventoryMessageDTO.Schedule schedule = new ExternalInventoryMessageDTO.Schedule();
    ExternalInventoryMessageDTO.OperatingTime operatingTime =
        new ExternalInventoryMessageDTO.OperatingTime();
    operatingTime.setStart("06:00:00");
    operatingTime.setEnd("22:00:00");
    schedule.setOperatingTimes(
        Map.of(
            "0", List.of(operatingTime), // Sunday
            "1", List.of(operatingTime), // Monday
            "2", List.of(operatingTime), // Tuesday
            "3", List.of(operatingTime), // Wednesday
            "4", List.of(operatingTime), // Thursday
            "5", List.of(operatingTime), // Friday
            "6", List.of(operatingTime))); // Saturday
    message.setSchedule(schedule);

    // Set selling term
    ExternalInventoryMessageDTO.SellingTerm sellingTerm =
        new ExternalInventoryMessageDTO.SellingTerm();
    sellingTerm.setMinHours(1);
    sellingTerm.setMinDays(1);
    message.setSellingTerm(sellingTerm);

    // Set digital fields
    ExternalInventoryMessageDTO.DigitalFields digitalFields =
        new ExternalInventoryMessageDTO.DigitalFields();
    digitalFields.setSpotDuration(30);
    digitalFields.setSpotsPerLoop(10);
    digitalFields.setBookingMode("loop");
    message.setDigitalFields(digitalFields);

    // Set prices
    ExternalInventoryMessageDTO.Price price = new ExternalInventoryMessageDTO.Price();
    price.setCpm(100.0);
    price.setCps(10.0);
    message.setPrices(List.of(price));

    // Set the detail snapshot in the wrapper
    wrapper.setDetailSnapshot(message);

    return wrapper;
  }
}
