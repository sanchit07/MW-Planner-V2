package com.mw.planner.service.config;

import com.mw.planner.domain.Campaign;
import com.mw.planner.dto.BrandIabCategory;
import com.mw.planner.dto.DemographicItemDTO;
import com.mw.planner.dto.DemographicsGroupedResponseDTO;
import com.mw.planner.dto.VenueItemDTO;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service providing default configuration data when database is empty or unavailable. This ensures
 * the application always returns meaningful configuration data.
 */
@Slf4j
@Service
public class DefaultConfigurationService {

  /**
   * Get default configuration data including demographics and campaign status. This method provides
   * hardcoded default values that match the expected API structure.
   */
  public Map<String, Object> getDefaultConfigurationData() {
    log.debug("Providing default configuration data");

    DemographicsGroupedResponseDTO demographics = getDefaultDemographics();

    return Map.of("demographics", demographics, "campaign_status", Campaign.Status.values());
  }

  /** Get default demographics data with all required categories. */
  public DemographicsGroupedResponseDTO getDefaultDemographics() {
    log.debug("Providing default demographics data");

    List<DemographicItemDTO> age = getDefaultAgeDemographics();
    List<DemographicItemDTO> gender = getDefaultGenderDemographics();
    List<DemographicItemDTO> income = getDefaultIncomeDemographics();
    List<DemographicItemDTO> interests = getDefaultInterestsDemographics();
    List<DemographicItemDTO> behavior = getDefaultBehaviorDemographics();
    List<VenueItemDTO> venues = getDefaultVenues();

    return new DemographicsGroupedResponseDTO(age, gender, income, interests, behavior, venues);
  }

  private List<DemographicItemDTO> getDefaultAgeDemographics() {
    return Arrays.asList(
        new DemographicItemDTO("18_24", "18-24 Years", ""),
        new DemographicItemDTO("25_34", "25–34 Years", ""),
        new DemographicItemDTO("35_44", "35–44 Years", ""),
        new DemographicItemDTO("45_54", "45–54 Years", ""),
        new DemographicItemDTO("55_64", "55–64 Years", ""),
        new DemographicItemDTO("65+", "65+ Years", ""));
  }

  private List<DemographicItemDTO> getDefaultGenderDemographics() {
    return Arrays.asList(
        new DemographicItemDTO("male", "Male", ""),
        new DemographicItemDTO("female", "Female", ""),
        new DemographicItemDTO("other", "Other", ""));
  }

  private List<DemographicItemDTO> getDefaultIncomeDemographics() {
    return Arrays.asList(
        new DemographicItemDTO("low", "Low income", "<30,000"),
        new DemographicItemDTO("lower_middle", "Lower-middle income", "30,000–50,000"),
        new DemographicItemDTO("middle", "Middle income", "50,000–100,000"),
        new DemographicItemDTO("upper_middle", "Upper-middle income", "100,000–150,000"),
        new DemographicItemDTO("high", "High income", ">150,000"));
  }

  private List<DemographicItemDTO> getDefaultInterestsDemographics() {
    return Arrays.asList(
        new DemographicItemDTO("Sports & Fitness", "Sports & Fitness", ""),
        new DemographicItemDTO("Technology", "Technology", ""),
        new DemographicItemDTO("Food & Dining", "Food & Dining", ""),
        new DemographicItemDTO("Travel", "Travel", ""),
        new DemographicItemDTO("Music", "Music", ""),
        new DemographicItemDTO("Entertainment", "Entertainment", ""),
        new DemographicItemDTO("Home & Garden", "Home & Garden", ""),
        new DemographicItemDTO("Health & Wellness", "Health & Wellness", ""),
        new DemographicItemDTO("Automotive", "Automotive", ""));
  }

  private List<DemographicItemDTO> getDefaultBehaviorDemographics() {
    return Arrays.asList(
        new DemographicItemDTO(
            "Commuters", "Commuters", "People traveling to or from work during peak hours"),
        new DemographicItemDTO("Tourists", "Tourists", "Visitors and tourists in key destinations"),
        new DemographicItemDTO(
            "Business Travelers",
            "Business Travelers",
            "Professional travelers in airports, hotels, and business districts"),
        new DemographicItemDTO(
            "Students", "Students", "University and college students in educational areas"),
        new DemographicItemDTO(
            "Families", "Families", "Family groups in entertainment and recreational venues"),
        new DemographicItemDTO(
            "Health Seekers",
            "Health Seekers",
            "Individuals visiting medical facilities and wellness centers"),
        new DemographicItemDTO(
            "Fitness Enthusiasts",
            "Fitness Enthusiasts",
            "Active individuals near gyms and sports facilities"),
        new DemographicItemDTO("Shoppers", "Shoppers", "Active shoppers in retail environments"));
  }

  public List<VenueItemDTO> getDefaultVenues() {
    return List.of(
        createTransitVenues(),
        createRetailVenues(),
        createOutdoorVenues(),
        createHealthBeautyVenues(),
        createPointOfCareVenues(),
        createEducationVenues(),
        createOfficeBuildingsVenues(),
        createLeisureVenues(),
        createGovernmentVenues(),
        createFinancialVenues(),
        createResidentialVenues());
  }

  private VenueItemDTO createTransitVenues() {
    VenueItemDTO transit = new VenueItemDTO();
    transit.setEnumerationId(1);
    transit.setTier(1);
    transit.setName("Transit");
    transit.setStringValue("transit");

    VenueItemDTO airports =
        venue(
            101,
            2,
            "Airports",
            "transit.airports",
            "Signage located throughout terminals in arrival and departure areas, ticketing areas, baggage claim, gate-hold rooms, concourses, retail shops, and VIP lounges.",
            List.of(
                venue(
                    10101,
                    3,
                    "Arrival Hall",
                    "transit.airports.arrivals_hall",
                    "Locations for meeting passengers arriving on flights"),
                venue(
                    10102,
                    3,
                    "Baggage Claim",
                    "transit.airports.baggage_claim",
                    "Locations to retrieve baggage not carried during a flight"),
                venue(
                    10103,
                    3,
                    "Departures Hall",
                    "transit.airports.departures_hall",
                    "Location for dropping off passengers leaving on flights"),
                venue(
                    10104,
                    3,
                    "Food Court",
                    "transit.airports.food_court",
                    "Location within an airport for food, typically casual"),
                venue(
                    10105,
                    3,
                    "Gates",
                    "transit.airports.gates",
                    "Location to wait for or embark or disembark from a specific plane"),
                venue(
                    10106,
                    3,
                    "Lounges",
                    "transit.airports.lounges",
                    "(typically private) places to wait for flights, separate from public spaces"),
                venue(
                    10107,
                    3,
                    "Shopping Area",
                    "transit.airports.shopping_area",
                    "Retail areas contained within facilities primarily used for servicing flights")));

    VenueItemDTO buses =
        venue(
            102,
            2,
            "Buses",
            "transit.buses",
            "Displays located on or in city or intercity buses.",
            List.of(
                venue(
                    10201,
                    3,
                    "Bus (Inside)",
                    "transit.buses.bus",
                    "Advertising inside a bus, primarily visible to bus passengers"),
                venue(
                    10202,
                    3,
                    "Terminal",
                    "transit.buses.terminal",
                    "Advertising at facilities for embarking or disembarking from a bus"),
                venue(
                    10203,
                    3,
                    "Bus (Outside)",
                    "transit.buses.bus_outside",
                    "Advertising outside a bus, primarily visible to people not riding the bus")));

    VenueItemDTO taxiRideshareTv =
        venue(
            103,
            2,
            "Taxi & Rideshare TV",
            "transit.taxi_rideshare_tv",
            "Advertising displays placed inside taxis and rideshare vehicles visible to passengers in the back seat.");
    VenueItemDTO taxiRideshareTop =
        venue(
            104,
            2,
            "Taxi & Rideshare Top",
            "transit.taxi_rideshare_top",
            "Advertising displays placed on top of taxi and rideshare vehicles visible to nearby pedestrian and drivers.");

    VenueItemDTO subway =
        venue(
            105,
            2,
            "Subway",
            "transit.subway",
            "Advertising displays placed inside subway trains or inside stations or on subway platforms.",
            List.of(
                venue(
                    10501,
                    3,
                    "Subway Train",
                    "transit.subway.train",
                    "A (typical municipal area) train that travels primarily underground"),
                venue(
                    10502,
                    3,
                    "Platform",
                    "transit.subway.platform",
                    "Areas to wait for, board, or unboard a subway")));

    VenueItemDTO trainStations =
        venue(
            106,
            2,
            "Train Stations",
            "transit.train_stations",
            "Advertising displays placed inside train stations or on platforms.",
            List.of(
                venue(
                    10601,
                    3,
                    "Train",
                    "transit.train_stations.train",
                    "A train that travels primarily above ground, on rails"),
                venue(
                    10602,
                    3,
                    "Platform",
                    "transit.train_stations.platform",
                    "Areas to wait for, board, or unboard a train")));

    VenueItemDTO ferry =
        venue(
            107,
            2,
            "Ferry",
            "transit.ferry",
            "Advertising displays placed inside a passenger water transport.");

    transit.setChildren(
        List.of(airports, buses, taxiRideshareTv, taxiRideshareTop, subway, trainStations, ferry));
    return transit;
  }

  private VenueItemDTO createRetailVenues() {
    VenueItemDTO retail = new VenueItemDTO();
    retail.setEnumerationId(2);
    retail.setTier(1);
    retail.setName("Retail");
    retail.setStringValue("retail");

    VenueItemDTO fuelingStations =
        venue(
            201,
            2,
            "Fueling Stations",
            "retail.gas_stations",
            "An establishment beside a road selling fuel for motor vehicles.",
            List.of(
                venue(
                    20101,
                    3,
                    "Fuel Dispenser",
                    "retail.gas_stations.pump",
                    "A (typically self-service) device for dispensing fuel to vehicles."),
                venue(
                    20102,
                    3,
                    "Shop",
                    "retail.gas_stations.shop",
                    "A store attached to a location who's primary audience is people fueling vehicles")));

    VenueItemDTO convenienceStores =
        venue(
            202,
            2,
            "Convenience Stores",
            "retail.convenience_store",
            "A store with extended opening hours and in a convenient location, stocking a limited range of household goods and groceries.");

    VenueItemDTO grocery =
        venue(
            203,
            2,
            "Grocery",
            "retail.grocery",
            "A retail shop that primarily sells food, either fresh or preserved.",
            List.of(
                venue(
                    20301,
                    3,
                    "Shop Entrance",
                    "retail.grocery.shop_entrance",
                    "Areas near the entrance to a store, often (but not always) visible from outside"),
                venue(
                    20302,
                    3,
                    "Check Out",
                    "retail.grocery.check_out",
                    "Areas primarily dedicated to paying for purchased goods"),
                venue(
                    20303,
                    3,
                    "Aisles",
                    "retail.grocery.aisles",
                    "Areas primarily dedicated to the display or retrieval of goods")));

    VenueItemDTO liquorStores =
        venue(
            204,
            2,
            "Liquor Stores",
            "retail.liquor_stores",
            "A retail shop that predominantly sells prepackaged alcoholic beverages, typically in bottles, intended to be consumed off the store's premises.");

    VenueItemDTO mall =
        venue(
            205,
            2,
            "Mall",
            "retail.malls",
            "A large building or series of connected buildings containing a variety of retail stores and typically also restaurants.",
            List.of(
                venue(
                    20501,
                    3,
                    "Concourse",
                    "retail.malls.concourse",
                    "A large open area (including hallways and escalators)"),
                venue(
                    20502,
                    3,
                    "Food Court",
                    "retail.malls.food_court",
                    "A Common area with multiple food vendors and common tables."),
                venue(
                    20503,
                    3,
                    "Spectacular",
                    "retail.malls.spectacular",
                    "Large and impactful screen(s) at a prime location. It often utilizes special embellishments.")));

    VenueItemDTO dispensaries =
        venue(
            206,
            2,
            "Cannabis Dispensaries",
            "retail.dispensaries",
            "A store that sells and dispenses cannabis and CBD products.");
    VenueItemDTO pharmacies =
        venue(
            207,
            2,
            "Pharmacies",
            "retail.pharmacies",
            "A store where medicinal drugs are dispensed and sold.");
    VenueItemDTO parkingGarages =
        venue(
            208,
            2,
            "Parking Garages",
            "retail.parking_garages",
            "A building in which people usually pay to park their cars, trucks and other vehicles.");

    retail.setChildren(
        List.of(
            fuelingStations,
            convenienceStores,
            grocery,
            liquorStores,
            mall,
            dispensaries,
            pharmacies,
            parkingGarages));
    return retail;
  }

  private VenueItemDTO createOutdoorVenues() {
    VenueItemDTO outdoor = new VenueItemDTO();
    outdoor.setEnumerationId(3);
    outdoor.setTier(1);
    outdoor.setName("Outdoor");
    outdoor.setStringValue("outdoor");

    VenueItemDTO billboards =
        venue(
            301,
            2,
            "Billboards",
            "outdoor.billboards",
            "Located primarily on major roads, they attract high-density consumer exposure (mostly to vehicular traffic, but often to pedestrians).",
            List.of(
                venue(
                    30101,
                    3,
                    "Roadside",
                    "outdoor.billboards.roadside",
                    "Primarily vehicular environments."),
                venue(
                    30102,
                    3,
                    "Highway",
                    "outdoor.billboards.highway",
                    "High-speed vehicular environments, typically with controlled entrance/exit (e.g. \"exits\" or \"interchanges\")"),
                venue(
                    30103,
                    3,
                    "Spectacular",
                    "outdoor.billboards.spectacular",
                    "A bulletin that is usually larger than 14' x 48' and is positioned at a prime location in a market. A spectacular often utilizes special embellishments.")));

    VenueItemDTO urbanPanels =
        venue(
            302,
            2,
            "Urban Panels",
            "outdoor.urban_panels",
            "Digital screens in urban environments, typically providing a public amenity. Typically visible to pedestrians and in some cases, vehicular traffic as well.");
    VenueItemDTO busShelters =
        venue(
            303,
            2,
            "Bus Shelters",
            "outdoor.bus_shelters",
            "Enclosures where individuals may wait for buses in an urban environment. Signage may be attached to the interior or exterior of the enclosure.");

    outdoor.setChildren(List.of(billboards, urbanPanels, busShelters));
    return outdoor;
  }

  private VenueItemDTO createHealthBeautyVenues() {
    VenueItemDTO healthBeauty = new VenueItemDTO();
    healthBeauty.setEnumerationId(4);
    healthBeauty.setTier(1);
    healthBeauty.setName("Health & Beauty");
    healthBeauty.setStringValue("health_beauty");

    VenueItemDTO gyms =
        venue(
            401,
            2,
            "Gyms",
            "health_beauty.gyms",
            "A club, building, or large room, usually containing special equipment, where people go to do physical exercise and get fit.",
            List.of(
                venue(
                    40101,
                    3,
                    "Lobby",
                    "health_beauty.gyms.lobby",
                    "Area for waiting or meeting guests"),
                venue(
                    40102,
                    3,
                    "Fitness Equipment",
                    "health_beauty.gyms.equipment",
                    "Area primarily for exercise or the usage of fitness equipment")));

    VenueItemDTO salons =
        venue(
            402,
            2,
            "Salons",
            "health_beauty.salons",
            "An establishment where a hairdresser, beautician, or couturier conducts business.",
            List.of(
                venue(
                    40201,
                    3,
                    "Unisex Salon",
                    "health_beauty.salons.unisex",
                    "Salon catering to clients of any sex"),
                venue(
                    40202,
                    3,
                    "Men's Salon",
                    "health_beauty.salons.mens",
                    "Salon primarily catering towards men"),
                venue(
                    40203,
                    3,
                    "Women's Salon",
                    "health_beauty.salons.womens",
                    "Salon primarily catering towards women")));

    VenueItemDTO spas =
        venue(
            403,
            2,
            "Spas",
            "health_beauty.spas",
            "A commercial establishment offering health and beauty treatment through such means as steam baths, exercise equipment, and massage.");

    healthBeauty.setChildren(List.of(gyms, salons, spas));
    return healthBeauty;
  }

  private VenueItemDTO createPointOfCareVenues() {
    VenueItemDTO pointOfCare = new VenueItemDTO();
    pointOfCare.setEnumerationId(5);
    pointOfCare.setTier(1);
    pointOfCare.setName("Point of Care");
    pointOfCare.setStringValue("point_care");

    pointOfCare.setChildren(
        List.of(
            venue(
                501,
                2,
                "Doctor's Offices",
                "point_care.doctor_offices",
                "Non-hospital facility run by a physician - for treatment of people."),
            venue(
                502,
                2,
                "Veterinary Offices",
                "point_care.veterinary_offices",
                "Non-hospital facility run by a veterinarian - for treatment of animals.")));
    return pointOfCare;
  }

  private VenueItemDTO createEducationVenues() {
    VenueItemDTO education = new VenueItemDTO();
    education.setEnumerationId(6);
    education.setTier(1);
    education.setName("Education");
    education.setStringValue("education");

    VenueItemDTO colleges =
        venue(
            602,
            2,
            "Colleges and Universities",
            "education.colleges",
            "An education institution designed for instruction, examination, or both, of students in many branches of advanced learning.",
            List.of(
                venue(
                    60201,
                    3,
                    "Residences",
                    "education.colleges.residences",
                    "Places where faculty or students live"),
                venue(
                    60202,
                    3,
                    "Common Areas",
                    "education.colleges.common",
                    "Shared spaces for study, dining, or leisure activities"),
                venue(
                    60203,
                    3,
                    "Athletic Facilities",
                    "education.colleges.athletics",
                    "Facilities or stadiums for sporting competition")));

    education.setChildren(
        List.of(
            venue(
                601,
                2,
                "Schools",
                "education.schools",
                "An educational institution designed to provide learning spaces for the teaching of students between K-12 under the direction of teachers."),
            colleges));
    return education;
  }

  private VenueItemDTO createOfficeBuildingsVenues() {
    VenueItemDTO officeBuildings = new VenueItemDTO();
    officeBuildings.setEnumerationId(7);
    officeBuildings.setTier(1);
    officeBuildings.setName("Office Buildings");
    officeBuildings.setStringValue("office_buildings");

    VenueItemDTO officeBuildingsInner =
        venue(
            701,
            2,
            "Office Buildings",
            "office_buildings.office_buildings",
            "An office building, also known as an office block or business center is a form of commercial building which contains spaces mainly designed to be used for offices.",
            List.of(
                venue(
                    70101,
                    3,
                    "Elevator",
                    "office_buildings.office_buildings.elevator",
                    "Enclosed, vertical conveyance for people and goods"),
                venue(
                    70102,
                    3,
                    "Lobby",
                    "office_buildings.office_buildings.lobby",
                    "Common space for tenants to meet and greet visitors and guests, typically near entrances")));

    officeBuildings.setChildren(List.of(officeBuildingsInner));
    return officeBuildings;
  }

  private VenueItemDTO createLeisureVenues() {
    VenueItemDTO leisure = new VenueItemDTO();
    leisure.setEnumerationId(8);
    leisure.setTier(1);
    leisure.setName("Leisure");
    leisure.setStringValue("entertainment");

    VenueItemDTO recreational =
        venue(
            801,
            2,
            "Recreational Locations",
            "entertainment.recreational",
            "Location where recreational and/or leisure activities take place.",
            List.of(
                venue(
                    80101,
                    3,
                    "Theme Parks",
                    "entertainment.recreational.theme_parks",
                    "An amusement park with a unifying setting or idea."),
                venue(
                    80102,
                    3,
                    "Museums and Galleries",
                    "entertainment.recreational.museums_galleries",
                    "A building in which objects of historical, scientific, artistic, or cultural interest are stored and exhibited."),
                venue(
                    80103,
                    3,
                    "Concert Venues",
                    "entertainment.recreational.concert_venues",
                    "Any location used for a concert or musical performance")));

    VenueItemDTO movieTheaters =
        venue(
            802,
            2,
            "Movie Theaters",
            "entertainment.movie_theaters",
            "Location for displaying long-format content on large screens.",
            List.of(
                venue(
                    80201,
                    3,
                    "Lobby",
                    "entertainment.movie_theaters.lobby",
                    "A corridor or hall connected with a larger room or series of rooms and used as a passageway or waiting room"),
                venue(
                    80202,
                    3,
                    "Food Court",
                    "entertainment.movie_theaters.food_court",
                    "An area within a building set apart for food concessions.")));

    VenueItemDTO sports =
        venue(
            803,
            2,
            "Sports Entertainment",
            "entertainment.sports",
            "A venue that individuals or groups can play an active sport or activity.",
            List.of(
                venue(
                    80301,
                    3,
                    "Sport Arena",
                    "entertainment.sports.arena",
                    "A central area used for sports or other forms of entertainment and surrounded by seats for spectators."),
                venue(
                    80302,
                    3,
                    "Club House",
                    "entertainment.sports.club_house",
                    "Locker rooms used by an athletic team")));

    VenueItemDTO hotels =
        venue(
            807,
            2,
            "Hotels",
            "entertainment.hotels",
            "An establishment providing accommodations, means, and other services for travelers and tourists.",
            List.of(
                venue(
                    80701,
                    3,
                    "Lobby",
                    "entertainment.hotels.lobby",
                    "Commonly accessible shared spaces for guests at a hotel"),
                venue(
                    80702,
                    3,
                    "Elevator",
                    "entertainment.hotels.elevator",
                    "Commonly accessible, enclosed spaces used to move between floors."),
                venue(
                    80703,
                    3,
                    "Room",
                    "entertainment.hotels.room",
                    "Locations occupied and restricted to a single guest")));

    leisure.setChildren(
        List.of(
            recreational,
            movieTheaters,
            sports,
            venue(
                804,
                2,
                "Bars",
                "entertainment.bars",
                "A retail business that serves alcoholic beverages."),
            venue(
                805,
                2,
                "Casual Dining",
                "entertainment.casual_dining",
                "A restaurant that serves moderately priced food in a casual atmosphere."),
            venue(
                806,
                2,
                "QSR",
                "entertainment.qsr",
                "A fast food restaurant, also known as a quick service restaurant within the industry."),
            hotels,
            venue(
                808,
                2,
                "Golf Carts",
                "entertainment.golf_cart",
                "A small motorized vehicle for golfers and their equipment."),
            venue(
                809,
                2,
                "Night Clubs",
                "entertainment.night_club",
                "An establishment for nighttime entertainment, typically serving drinks and offering music, dancing, etc."),
            venue(
                810,
                2,
                "High-End Dining",
                "entertainment.high_end_dining",
                "A restaurant that serves expensive food. Often in a more formal atmosphere, and accepting or requiring reservations")));
    return leisure;
  }

  private VenueItemDTO createGovernmentVenues() {
    VenueItemDTO government = new VenueItemDTO();
    government.setEnumerationId(9);
    government.setTier(1);
    government.setName("Government");
    government.setStringValue("government");

    government.setChildren(
        List.of(
            venue(
                901,
                2,
                "DMVs",
                "government.dmv",
                "A government facility for motor vehicle-related services including licensing and registration."),
            venue(
                902,
                2,
                "Military Bases",
                "government.military_bases",
                "A facility that houses and facilitates training for military personnel and operations."),
            venue(
                903,
                2,
                "Post Offices",
                "government.postal",
                "A facility that handles the receipt, delivery, and processing of mail, packages, or other postal services.")));
    return government;
  }

  private VenueItemDTO createFinancialVenues() {
    VenueItemDTO financial = new VenueItemDTO();
    financial.setEnumerationId(10);
    financial.setTier(1);
    financial.setName("Financial");
    financial.setStringValue("financial");

    financial.setChildren(
        List.of(
            venue(
                1001,
                2,
                "Banks",
                "financial.banks",
                "A bank is a financial institution licensed to store or invest accountholders money")));
    return financial;
  }

  private VenueItemDTO createResidentialVenues() {
    VenueItemDTO residential = new VenueItemDTO();
    residential.setEnumerationId(11);
    residential.setTier(1);
    residential.setName("Residential");
    residential.setStringValue("residential");

    VenueItemDTO apartments =
        venue(
            1101,
            2,
            "Apartment Buildings and Condominiums",
            "residential.apartment",
            "A building that contains different residential units.",
            List.of(
                venue(
                    110101,
                    3,
                    "Lobby",
                    "residential.apartment_buildings.lobby",
                    "A corridor or hall connected with a larger room or series of rooms and used as a passageway or waiting room"),
                venue(
                    110102,
                    3,
                    "Elevator",
                    "residential.apartment_buildings.elevator",
                    "Enclosed, vertical conveyance for people and goods")));

    residential.setChildren(List.of(apartments));
    return residential;
  }

  private VenueItemDTO venue(
      int enumId, int tier, String name, String stringValue, String definition) {
    return venue(enumId, tier, name, stringValue, definition, List.of());
  }

  private VenueItemDTO venue(
      int enumId,
      int tier,
      String name,
      String stringValue,
      String definition,
      List<VenueItemDTO> children) {
    VenueItemDTO item = new VenueItemDTO();
    item.setEnumerationId(enumId);
    item.setTier(tier);
    item.setName(name);
    item.setStringValue(stringValue);
    item.setDefinition(definition);
    item.setChildren(children);
    return item;
  }

  /**
   * Create the IAB category mapping. This method provides the mapping between IAB codes and their
   * corresponding category names.
   */
  private List<BrandIabCategory> createCategoryMap() {
    return List.of(
        BrandIabCategory.builder().code("IAB1").name("Arts & Entertainment").build(),
        BrandIabCategory.builder().code("IAB2").name("Automotive").build(),
        BrandIabCategory.builder().code("IAB3").name("Business").build(),
        BrandIabCategory.builder().code("IAB4").name("Careers").build(),
        BrandIabCategory.builder().code("IAB5").name("Education").build(),
        BrandIabCategory.builder().code("IAB6").name("Family & Parenting").build(),
        BrandIabCategory.builder().code("IAB7").name("Health & Fitness").build(),
        BrandIabCategory.builder().code("IAB8").name("Food & Drink").build(),
        BrandIabCategory.builder().code("IAB9").name("Hobbies & Interests").build(),
        BrandIabCategory.builder().code("IAB10").name("Home & Garden").build(),
        BrandIabCategory.builder().code("IAB11").name("Law Government & Politics").build(),
        BrandIabCategory.builder().code("IAB12").name("News").build(),
        BrandIabCategory.builder().code("IAB13").name("Personal Finance").build(),
        BrandIabCategory.builder().code("IAB14").name("Society").build(),
        BrandIabCategory.builder().code("IAB15").name("Science").build(),
        BrandIabCategory.builder().code("IAB16").name("Pets").build(),
        BrandIabCategory.builder().code("IAB17").name("Sports").build(),
        BrandIabCategory.builder().code("IAB18").name("Style & Fashion").build(),
        BrandIabCategory.builder().code("IAB19").name("Technology & Computing").build(),
        BrandIabCategory.builder().code("IAB20").name("Travel").build(),
        BrandIabCategory.builder().code("IAB21").name("Real Estate").build(),
        BrandIabCategory.builder().code("IAB23").name("Religion & Spirituality").build(),
        BrandIabCategory.builder().code("IAB24").name("Uncategorized").build(),
        BrandIabCategory.builder().code("IAB25").name("Non-Standard Content").build(),
        BrandIabCategory.builder().code("IAB26").name("Illegal Content").build());
  }

  /**
   * Get all IAB category mappings sorted by IAB code in ascending order.
   *
   * @return a sorted map of IAB codes to category names
   */
  public List<BrandIabCategory> getAllIabMappings() {
    return createCategoryMap();
  }

  public String getCategoryNameByCode(String code) {
    return createCategoryMap().stream()
        .filter(category -> category.code().equals(code))
        .map(BrandIabCategory::name)
        .findFirst()
        .orElse(null);
  }

  /**
   * Default platform configuration for a tenant with no saved {@link
   * com.mw.planner.domain.PlannerConfiguration}. Values are sane platform defaults, not derived
   * from any tenant data.
   */
  public com.mw.planner.domain.PlannerConfiguration getDefaultPlannerConfiguration(
      String companyId) {
    return com.mw.planner.domain.PlannerConfiguration.builder()
        .companyId(companyId)
        .general(
            com.mw.planner.domain.PlannerConfiguration.General.builder()
                .dateFormat("yyyy-MM-dd")
                .timeFormat("HH:mm")
                .currencyDisplay("CODE")
                .decimalPlaces(2)
                .fiscalYearStartMonth(1)
                .helpBubblesEnabled(true)
                .tourEnabled(true)
                .build())
        .terminology(
            com.mw.planner.domain.PlannerConfiguration.Terminology.builder()
                .customTerms(Map.of("campaign", "Plan"))
                .build())
        .targeting(
            com.mw.planner.domain.PlannerConfiguration.Targeting.builder()
                .ageGroupRanges(List.of("18-24", "25-34", "35-44", "45-54", "55-64", "65+"))
                .incomeBrackets(List.of("low", "lower_middle", "middle", "upper_middle", "high"))
                .geographyLevels(List.of("country", "state", "city", "district"))
                .radiusUnit("km")
                .defaultRadius(5.0)
                .build())
        .numberFormats(
            com.mw.planner.domain.PlannerConfiguration.NumberFormats.builder()
                .thousandsSeparator(",")
                .decimalSeparator(".")
                .compactNotation(true)
                .build())
        .dashboard(
            com.mw.planner.domain.PlannerConfiguration.Dashboard.builder()
                .visibleWidgetKeys(List.of())
                .defaultView("overview")
                .build())
        .campaign(
            com.mw.planner.domain.PlannerConfiguration.CampaignToggles.builder()
                .setupFeaturesEnabled(true)
                .targetingFeaturesEnabled(true)
                .advancedFeaturesEnabled(true)
                .build())
        .inventory(
            com.mw.planner.domain.PlannerConfiguration.InventoryToggles.builder()
                .visibleColumns(List.of())
                .visibleFilters(List.of())
                .build())
        .poi(
            com.mw.planner.domain.PlannerConfiguration.PoiSettings.builder()
                .maxPoiPerCampaign(20)
                .radiusOptions(List.of(0.5, 1.0, 2.0, 5.0, 10.0))
                .visibilityScope("COMPANY")
                .build())
        .schedule(
            com.mw.planner.domain.PlannerConfiguration.ScheduleSettings.builder()
                .frequencyCap(null)
                .shareOfVoiceDefault(100.0)
                .spotDurationSeconds(15)
                .build())
        .reports(
            com.mw.planner.domain.PlannerConfiguration.ReportsSettings.builder()
                .defaultColumns(List.of())
                .defaultExportFormat("xlsx")
                .build())
        .filters(
            com.mw.planner.domain.PlannerConfiguration.FiltersSettings.builder()
                .pinnedFilterKeys(List.of())
                .build())
        .approvals(
            com.mw.planner.domain.PlannerConfiguration.ApprovalsSettings.builder()
                .mediaOwnerAutoApproveHours(72)
                .reminderBeforeHours(48)
                .build())
        .bonusWorkflow(
            com.mw.planner.domain.PlannerConfiguration.BonusWorkflowSettings.builder()
                .enabled(false)
                .allowedBonusTypes(List.of("SAB", "GB"))
                .build())
        .build();
  }
}
