package com.mw.planner.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.planner.domain.Inventory;
import com.mw.planner.dto.ExternalInventoryMessageDTO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExternalInventoryMessageConverterTest {

  private ExternalInventoryMessageConverter converter;

  @BeforeEach
  void setUp() {
    converter = new ExternalInventoryMessageConverter();
  }

  @Test
  void convertToInventory_WithMinimalDto_ReturnsInventoryWithBasicFields() {
    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("ref-123");
    externalId.setPlatform("platform");

    ExternalInventoryMessageDTO dto = new ExternalInventoryMessageDTO();
    dto.setId("ext-id-1");
    dto.setName("Test Inventory");
    dto.setTypeName("Digital > OOH");
    dto.setDisplayFormatName("Billboard");
    dto.setReferenceId("ref-123");
    dto.setExternalIds(List.of(externalId));
    dto.setMediaOwnerId("mo-1");
    dto.setArchived(false);
    dto.setSize("48x14");

    Inventory result = converter.convertToInventory(dto);

    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("Test Inventory");
    assertThat(result.getExternalId()).isEqualTo("ext-id-1");
    assertThat(result.getReferenceId()).isEqualTo("ref-123");
    assertThat(result.getClassification()).isEqualTo("Digital");
    assertThat(result.getType()).isEqualTo("OOH");
    assertThat(result.getFormat()).isEqualTo("Billboard");
    assertThat(result.getMediaOwnerId()).isEqualTo("mo-1");
    assertThat(result.getArchived()).isFalse();
    assertThat(result.getSize()).isEqualTo("48x14");
    assertThat(result.getExternalIds()).hasSize(1);
    assertThat(result.getExternalIds().getFirst().getExternalId()).isEqualTo("ref-123");
    assertThat(result.getExternalIds().getFirst().getPlatform()).isEqualTo("platform");
  }

  @Test
  void convertToInventory_WithNullSize_SetsNullSize() {
    ExternalInventoryMessageDTO dto = new ExternalInventoryMessageDTO();
    dto.setId("ext-id-null-size");
    dto.setName("No Size Inventory");

    Inventory result = converter.convertToInventory(dto);

    assertThat(result.getSize()).isNull();
  }

  @Test
  void convertToInventory_WithPointGeometry_SetsLocationCoordinates() {
    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("ref-456");

    ExternalInventoryMessageDTO dto = new ExternalInventoryMessageDTO();
    dto.setId("ext-2");
    dto.setName("Inventory with location");
    dto.setReferenceId("ref-456");
    dto.setExternalIds(List.of(externalId));
    dto.setGeoms(List.of("POINT(139.775694 35.71204)"));
    dto.setAddress("Tokyo Address");
    dto.setAdminLevel0Name("Japan");
    dto.setAdminLevel1Name("Tokyo");
    dto.setAdminLevel2Name("Shibuya");

    Inventory result = converter.convertToInventory(dto);

    assertThat(result).isNotNull();
    assertThat(result.getLocation()).isNotNull();
    assertThat(result.getLocation().getLocationCoordinates()).isNotNull();
    assertThat(result.getLocation().getAddress()).isEqualTo("Tokyo Address");
    assertThat(result.getLocation().getCountry()).isEqualTo("Japan");
    assertThat(result.getLocation().getState()).isEqualTo("Tokyo");
    assertThat(result.getLocation().getCity()).isEqualTo("Shibuya");
  }

  @Test
  void convertToInventory_WithVenues_SetsVenueType() {
    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("ref-789");
    ExternalInventoryMessageDTO.Venue venue = new ExternalInventoryMessageDTO.Venue();
    venue.setName("Mall");

    ExternalInventoryMessageDTO dto = new ExternalInventoryMessageDTO();
    dto.setId("ext-3");
    dto.setName("Venue Inventory");
    dto.setReferenceId("ref-789");
    dto.setExternalIds(List.of(externalId));
    dto.setVenues(List.of(venue));

    Inventory result = converter.convertToInventory(dto);

    assertThat(result).isNotNull();
    assertThat(result.getVenueType()).containsExactly("Mall");
    assertThat(result.getVenueTypeIds()).isEmpty();
  }

  @Test
  void convertToInventory_WithVenuesHavingTaxonomyId_SetsVenueTypeIds() {
    ExternalInventoryMessageDTO.Venue venue = new ExternalInventoryMessageDTO.Venue();
    venue.setName("Mall");
    venue.setTaxonomyId("401");

    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("ref-tax");
    ExternalInventoryMessageDTO dto = new ExternalInventoryMessageDTO();
    dto.setId("ext-tax");
    dto.setName("Venue With TaxonomyId");
    dto.setExternalIds(List.of(externalId));
    dto.setVenues(List.of(venue));

    Inventory result = converter.convertToInventory(dto);

    assertThat(result.getVenueType()).containsExactly("Mall");
    assertThat(result.getVenueTypeIds()).containsExactly("401");
  }

  @Test
  void convertToInventory_WithVenueHavingNullName_FiltersNullFromVenueType() {
    ExternalInventoryMessageDTO.Venue venueWithName = new ExternalInventoryMessageDTO.Venue();
    venueWithName.setName("Mall");
    venueWithName.setTaxonomyId("401");
    ExternalInventoryMessageDTO.Venue venueNullName = new ExternalInventoryMessageDTO.Venue();
    venueNullName.setName(null);
    venueNullName.setTaxonomyId("402");

    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("ref-null-name");
    ExternalInventoryMessageDTO dto = new ExternalInventoryMessageDTO();
    dto.setId("ext-null-name");
    dto.setName("Null Name Venue");
    dto.setExternalIds(List.of(externalId));
    dto.setVenues(List.of(venueWithName, venueNullName));

    Inventory result = converter.convertToInventory(dto);

    assertThat(result.getVenueType()).containsExactly("Mall");
    assertThat(result.getVenueTypeIds()).containsExactly("401", "402");
  }

  @Test
  void convertToInventory_WithVenueHavingNullTaxonomyId_FiltersNullFromVenueTypeIds() {
    ExternalInventoryMessageDTO.Venue venueWithId = new ExternalInventoryMessageDTO.Venue();
    venueWithId.setName("Mall");
    venueWithId.setTaxonomyId("401");
    ExternalInventoryMessageDTO.Venue venueNullId = new ExternalInventoryMessageDTO.Venue();
    venueNullId.setName("Transit");
    venueNullId.setTaxonomyId(null);

    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("ref-null-tax");
    ExternalInventoryMessageDTO dto = new ExternalInventoryMessageDTO();
    dto.setId("ext-null-tax");
    dto.setName("Null TaxonomyId Venue");
    dto.setExternalIds(List.of(externalId));
    dto.setVenues(List.of(venueWithId, venueNullId));

    Inventory result = converter.convertToInventory(dto);

    assertThat(result.getVenueType()).containsExactly("Mall", "Transit");
    assertThat(result.getVenueTypeIds()).containsExactly("401");
  }

  @Test
  void convertToInventory_WithNullExternalIds_SetsReferenceIdNull() {
    ExternalInventoryMessageDTO dto = new ExternalInventoryMessageDTO();
    dto.setId("ext-id");
    dto.setName("No External Ids");
    dto.setExternalIds(null);

    Inventory result = converter.convertToInventory(dto);

    assertThat(result).isNotNull();
    assertThat(result.getReferenceId()).isNull();
    assertThat(result.getExternalIds()).isNull();
    assertThat(result.getName()).isEqualTo("No External Ids");
  }

  @Test
  void convertToInventory_WithEmptyExternalIds_SetsExternalIdsNull() {
    ExternalInventoryMessageDTO dto = new ExternalInventoryMessageDTO();
    dto.setId("ext-id");
    dto.setName("Empty External Ids");
    dto.setExternalIds(List.of());

    Inventory result = converter.convertToInventory(dto);

    assertThat(result).isNotNull();
    assertThat(result.getReferenceId()).isNull();
    assertThat(result.getExternalIds()).isNull();
  }

  @Test
  void convertToInventory_WithTypeNameSinglePart_SetsClassificationOnly() {
    ExternalInventoryMessageDTO.ExternalId externalId =
        new ExternalInventoryMessageDTO.ExternalId();
    externalId.setExternalId("ref-single");
    ExternalInventoryMessageDTO dto = new ExternalInventoryMessageDTO();
    dto.setId("ext-4");
    dto.setName("Single Type");
    dto.setExternalIds(List.of(externalId));
    dto.setTypeName("Digital");

    Inventory result = converter.convertToInventory(dto);

    assertThat(result).isNotNull();
    assertThat(result.getClassification()).isEqualTo("Digital");
    assertThat(result.getType()).isNull();
  }

  // ---- priceTypes derivation -------------------------------------------------------------------

  /** Build a DTO price; spot maps from the external "cps" field. */
  private static ExternalInventoryMessageDTO.Price price(Double cpm, Double cps, Double monthly) {
    ExternalInventoryMessageDTO.Price p = new ExternalInventoryMessageDTO.Price();
    p.setCpm(cpm);
    p.setCps(cps);
    p.setMonthly(monthly);
    return p;
  }

  private Inventory convertWithPrices(List<ExternalInventoryMessageDTO.Price> prices) {
    ExternalInventoryMessageDTO dto = new ExternalInventoryMessageDTO();
    dto.setId("ext-price");
    dto.setName("Price Inventory");
    dto.setPrices(prices);
    return converter.convertToInventory(dto);
  }

  @Test
  void generatePriceTypes_WithOnlyCpm_ReturnsCpmOnly() {
    Inventory result = convertWithPrices(List.of(price(2.5, null, null)));

    assertThat(result.getPriceTypes()).containsExactly("cpm");
  }

  @Test
  void generatePriceTypes_WithAllThree_ReturnsAllInFixedOrder() {
    Inventory result = convertWithPrices(List.of(price(2.5, 1.0, 100.0)));

    assertThat(result.getPriceTypes()).containsExactly("cpm", "spot", "monthly");
  }

  @Test
  void generatePriceTypes_WithZeroOrNegativeValues_ReturnsEmpty() {
    Inventory result = convertWithPrices(List.of(price(0.0, -1.0, 0.0)));

    assertThat(result.getPriceTypes()).isEmpty();
  }

  @Test
  void generatePriceTypes_WithNullPrices_ReturnsEmptyNotNull() {
    Inventory result = convertWithPrices(null);

    assertThat(result.getPriceTypes()).isNotNull().isEmpty();
  }

  @Test
  void generatePriceTypes_WithEmptyPrices_ReturnsEmpty() {
    Inventory result = convertWithPrices(List.of());

    assertThat(result.getPriceTypes()).isNotNull().isEmpty();
  }

  @Test
  void generatePriceTypes_WithValuesAcrossMultiplePriceElements_ReturnsUnion() {
    Inventory result =
        convertWithPrices(
            List.of(
                price(2.5, null, null), // cpm on first element
                price(null, null, 100.0), // monthly on second element
                price(null, 0.0, null))); // spot zero -> excluded

    assertThat(result.getPriceTypes()).containsExactly("cpm", "monthly");
  }

  @Test
  void generatePriceTypes_AlwaysOrdersCpmSpotMonthly_RegardlessOfInputOrder() {
    Inventory result =
        convertWithPrices(
            List.of(
                price(null, null, 100.0), // monthly first
                price(null, 1.0, null), // spot second
                price(2.5, null, null))); // cpm third

    assertThat(result.getPriceTypes()).containsExactly("cpm", "spot", "monthly");
  }
}
