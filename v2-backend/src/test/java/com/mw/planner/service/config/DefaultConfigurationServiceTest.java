package com.mw.planner.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.planner.domain.Campaign;
import com.mw.planner.dto.DemographicsGroupedResponseDTO;
import com.mw.planner.dto.VenueItemDTO;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultConfigurationServiceTest {

  private final DefaultConfigurationService defaultConfigurationService =
      new DefaultConfigurationService();

  @Test
  void getDefaultConfigurationData_ShouldReturnCompleteConfiguration() {
    // When
    Map<String, Object> result = defaultConfigurationService.getDefaultConfigurationData();

    // Then
    assertThat(result).isNotNull();
    assertThat(result).containsKey("demographics");
    assertThat(result).containsKey("campaign_status");

    // Verify campaign status data
    Campaign.Status[] campaignStatuses = (Campaign.Status[]) result.get("campaign_status");
    assertThat(campaignStatuses).isNotNull();
    assertThat(campaignStatuses).hasSize(Campaign.Status.values().length);
    assertThat(campaignStatuses).containsExactly(Campaign.Status.values());
  }

  @Test
  void getDefaultDemographics_ShouldReturnCompleteDemographics() {
    // When
    DemographicsGroupedResponseDTO result = defaultConfigurationService.getDefaultDemographics();

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getAge()).hasSize(6);
    assertThat(result.getGender()).hasSize(3);
    assertThat(result.getIncome()).hasSize(5);
    assertThat(result.getInterests()).hasSize(9);
    assertThat(result.getBehavior()).hasSize(8);
    assertThat(result.getVenues()).hasSize(11);

    // Verify age demographics
    assertThat(result.getAge().get(0).getDemoKey()).isEqualTo("18_24");
    assertThat(result.getAge().get(0).getName()).isEqualTo("18-24 Years");
    assertThat(result.getAge().get(0).getDescription()).isEmpty();

    assertThat(result.getAge().get(5).getDemoKey()).isEqualTo("65+");
    assertThat(result.getAge().get(5).getName()).isEqualTo("65+ Years");
    assertThat(result.getAge().get(5).getDescription()).isEmpty();

    // Verify gender demographics
    assertThat(result.getGender().get(0).getDemoKey()).isEqualTo("male");
    assertThat(result.getGender().get(0).getName()).isEqualTo("Male");
    assertThat(result.getGender().get(0).getDescription()).isEmpty();

    assertThat(result.getGender().get(1).getDemoKey()).isEqualTo("female");
    assertThat(result.getGender().get(1).getName()).isEqualTo("Female");

    assertThat(result.getGender().get(2).getDemoKey()).isEqualTo("other");
    assertThat(result.getGender().get(2).getName()).isEqualTo("Other");

    // Verify income demographics
    assertThat(result.getIncome().get(0).getDemoKey()).isEqualTo("low");
    assertThat(result.getIncome().get(0).getName()).isEqualTo("Low income");
    assertThat(result.getIncome().get(0).getDescription()).isEqualTo("<30,000");

    assertThat(result.getIncome().get(4).getDemoKey()).isEqualTo("high");
    assertThat(result.getIncome().get(4).getName()).isEqualTo("High income");
    assertThat(result.getIncome().get(4).getDescription()).isEqualTo(">150,000");

    // Verify interests demographics
    assertThat(result.getInterests().get(0).getDemoKey()).isEqualTo("Sports & Fitness");
    assertThat(result.getInterests().get(0).getName()).isEqualTo("Sports & Fitness");
    assertThat(result.getInterests().get(0).getDescription()).isEmpty();

    assertThat(result.getInterests().get(8).getDemoKey()).isEqualTo("Automotive");
    assertThat(result.getInterests().get(8).getName()).isEqualTo("Automotive");
    assertThat(result.getInterests().get(8).getDescription()).isEmpty();

    // Verify behavior demographics
    assertThat(result.getBehavior().get(0).getDemoKey()).isEqualTo("Commuters");
    assertThat(result.getBehavior().get(0).getName()).isEqualTo("Commuters");
    assertThat(result.getBehavior().get(0).getDescription())
        .isEqualTo("People traveling to or from work during peak hours");

    assertThat(result.getBehavior().get(7).getDemoKey()).isEqualTo("Shoppers");
    assertThat(result.getBehavior().get(7).getName()).isEqualTo("Shoppers");
    assertThat(result.getBehavior().get(7).getDescription())
        .isEqualTo("Active shoppers in retail environments");

    // Verify venues
    VenueItemDTO transit = result.getVenues().get(0);
    assertThat(transit.getName()).isEqualTo("Transit");
    assertThat(transit.getEnumerationId()).isEqualTo(1);
    assertThat(transit.getTier()).isEqualTo(1);
    assertThat(transit.getStringValue()).isEqualTo("transit");
    assertThat(transit.getChildren()).hasSize(7);

    // Verify airport venue
    VenueItemDTO airport = transit.getChildren().get(0);
    assertThat(airport.getName()).isEqualTo("Airports");
    assertThat(airport.getEnumerationId()).isEqualTo(101);
    assertThat(airport.getTier()).isEqualTo(2);
    assertThat(airport.getStringValue()).isEqualTo("transit.airports");
    assertThat(airport.getChildren()).hasSize(7);

    // Verify airport children
    VenueItemDTO arrivalsHall = airport.getChildren().get(0);
    assertThat(arrivalsHall.getName()).isEqualTo("Arrival Hall");
    assertThat(arrivalsHall.getEnumerationId()).isEqualTo(10101);
    assertThat(arrivalsHall.getStringValue()).isEqualTo("transit.airports.arrivals_hall");
    assertThat(arrivalsHall.getChildren()).isEmpty();

    VenueItemDTO baggageClaim = airport.getChildren().get(1);
    assertThat(baggageClaim.getName()).isEqualTo("Baggage Claim");
    assertThat(baggageClaim.getEnumerationId()).isEqualTo(10102);
    assertThat(baggageClaim.getStringValue()).isEqualTo("transit.airports.baggage_claim");
    assertThat(baggageClaim.getChildren()).isEmpty();

    // Verify buses venue
    VenueItemDTO buses = transit.getChildren().get(1);
    assertThat(buses.getName()).isEqualTo("Buses");
    assertThat(buses.getEnumerationId()).isEqualTo(102);
    assertThat(buses.getStringValue()).isEqualTo("transit.buses");
    assertThat(buses.getChildren()).hasSize(3);

    // Verify bus children
    VenueItemDTO busInside = buses.getChildren().get(0);
    assertThat(busInside.getName()).isEqualTo("Bus (Inside)");
    assertThat(busInside.getEnumerationId()).isEqualTo(10201);
    assertThat(busInside.getStringValue()).isEqualTo("transit.buses.bus");
    assertThat(busInside.getChildren()).isEmpty();
  }
}
