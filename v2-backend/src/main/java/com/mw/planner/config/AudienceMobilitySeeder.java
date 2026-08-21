package com.mw.planner.config;

import com.mw.planner.domain.AudienceMobility;
import com.mw.planner.repository.AudienceMobilityRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a representative audience-mobility dataset on first boot (only when the collection is
 * empty), so the planning-map heatmap has realistic data to render. A production vendor feed would
 * replace this by ingesting into the same {@code audience_mobility} collection/shape; the read API
 * does not care where the documents came from.
 *
 * <p>Data model: per country, a set of urban hotspots (city centers, transit hubs, retail
 * districts). Around each hotspot we generate gaussian-scattered geo cells whose weight decays with
 * distance and varies by time-of-day bucket (retail peaks afternoon/evening, transit peaks
 * morning/evening, nightlife peaks night). Deterministic RNG so reseeding a wiped database yields
 * identical data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudienceMobilitySeeder implements ApplicationRunner {

  private final AudienceMobilityRepository repository;

  private record Hotspot(
      String country, double lat, double lng, double strength, double spreadKm, Profile profile) {}

  /** Time-of-day multipliers: morning, afternoon, evening, night. */
  private enum Profile {
    COMMERCIAL(0.7, 1.0, 0.9, 0.25),
    TRANSIT(1.0, 0.6, 1.0, 0.3),
    RETAIL(0.4, 0.9, 1.0, 0.5),
    NIGHTLIFE(0.15, 0.4, 0.8, 1.0),
    RESIDENTIAL(0.8, 0.5, 0.9, 0.6);

    final double[] multipliers;

    Profile(double morning, double afternoon, double evening, double night) {
      this.multipliers = new double[] {morning, afternoon, evening, night};
    }
  }

  private static final String[] BUCKETS = {"MORNING", "AFTERNOON", "EVENING", "NIGHT"};

  private static final List<Hotspot> HOTSPOTS =
      List.of(
          // Malaysia — Kuala Lumpur & Klang Valley, Penang, Johor Bahru
          new Hotspot("malaysia", 3.1478, 101.6953, 1.0, 3.0, Profile.COMMERCIAL), // KLCC
          new Hotspot("malaysia", 3.1420, 101.7115, 0.9, 2.0, Profile.RETAIL), // Bukit Bintang
          new Hotspot("malaysia", 3.1340, 101.6869, 0.85, 2.5, Profile.TRANSIT), // KL Sentral
          new Hotspot("malaysia", 3.0738, 101.6060, 0.6, 4.0, Profile.RESIDENTIAL), // Petaling Jaya
          new Hotspot("malaysia", 3.1579, 101.7123, 0.5, 2.0, Profile.NIGHTLIFE), // Ampang
          new Hotspot("malaysia", 5.4141, 100.3288, 0.65, 3.0, Profile.COMMERCIAL), // George Town
          new Hotspot("malaysia", 5.3416, 100.2830, 0.4, 3.0, Profile.RETAIL), // Bayan Lepas
          new Hotspot("malaysia", 1.4655, 103.7578, 0.6, 3.5, Profile.COMMERCIAL), // Johor Bahru
          new Hotspot("malaysia", 3.0448, 101.7058, 0.45, 3.0, Profile.RESIDENTIAL), // Cheras
          new Hotspot("malaysia", 2.7456, 101.7072, 0.35, 4.0, Profile.TRANSIT), // KLIA corridor
          // Sri Lanka — Colombo, Kandy, Galle
          new Hotspot("sri-lanka", 6.9271, 79.8612, 1.0, 3.0, Profile.COMMERCIAL), // Colombo Fort
          new Hotspot("sri-lanka", 6.9147, 79.8730, 0.8, 2.0, Profile.RETAIL), // Kollupitiya
          new Hotspot("sri-lanka", 6.9350, 79.8500, 0.7, 2.0, Profile.TRANSIT), // Pettah
          new Hotspot("sri-lanka", 6.9061, 79.8730, 0.5, 2.5, Profile.NIGHTLIFE), // Bambalapitiya
          new Hotspot("sri-lanka", 7.2906, 80.6337, 0.5, 2.5, Profile.COMMERCIAL), // Kandy
          new Hotspot("sri-lanka", 6.0535, 80.2210, 0.4, 2.5, Profile.RETAIL), // Galle
          // India — Mumbai, Delhi, Bengaluru
          new Hotspot("india", 19.0760, 72.8777, 1.0, 4.0, Profile.COMMERCIAL), // Mumbai
          new Hotspot("india", 19.1197, 72.8464, 0.7, 3.0, Profile.RETAIL), // Andheri
          new Hotspot("india", 28.6329, 77.2195, 0.95, 4.0, Profile.COMMERCIAL), // Connaught Place
          new Hotspot("india", 28.5562, 77.1000, 0.6, 3.0, Profile.TRANSIT), // IGI corridor
          new Hotspot("india", 12.9716, 77.5946, 0.9, 4.0, Profile.COMMERCIAL), // Bengaluru
          new Hotspot("india", 12.9352, 77.6245, 0.6, 2.5, Profile.NIGHTLIFE), // Koramangala
          // UAE — Dubai, Abu Dhabi
          new Hotspot(
              "united-arab-emirates", 25.1972, 55.2744, 1.0, 3.0, Profile.RETAIL), // Dubai Mall
          new Hotspot("united-arab-emirates", 25.2532, 55.3657, 0.8, 3.0, Profile.TRANSIT), // DXB
          new Hotspot(
              "united-arab-emirates", 25.0805, 55.1403, 0.7, 2.5, Profile.NIGHTLIFE), // Marina
          new Hotspot(
              "united-arab-emirates", 24.4539, 54.3773, 0.7, 3.5, Profile.COMMERCIAL), // Abu Dhabi
          // Singapore
          new Hotspot("singapore", 1.3040, 103.8318, 1.0, 1.5, Profile.RETAIL), // Orchard
          new Hotspot("singapore", 1.2839, 103.8515, 0.9, 1.5, Profile.COMMERCIAL), // CBD
          new Hotspot("singapore", 1.3644, 103.9915, 0.6, 2.0, Profile.TRANSIT), // Changi
          new Hotspot("singapore", 1.2804, 103.8443, 0.5, 1.0, Profile.NIGHTLIFE)); // Clarke Quay

  private static final int POINTS_PER_HOTSPOT = 60;
  private static final double KM_PER_DEG_LAT = 110.574;

  @Override
  public void run(ApplicationArguments args) {
    try {
      if (repository.count() > 0) {
        return;
      }
      List<AudienceMobility> docs = new ArrayList<>();
      Random rng = new Random(42); // deterministic
      for (Hotspot h : HOTSPOTS) {
        double kmPerDegLng = 111.320 * Math.cos(Math.toRadians(h.lat()));
        for (int i = 0; i < POINTS_PER_HOTSPOT; i++) {
          double dLatKm = rng.nextGaussian() * h.spreadKm();
          double dLngKm = rng.nextGaussian() * h.spreadKm();
          double distKm = Math.hypot(dLatKm, dLngKm);
          double decay = Math.exp(-(distKm * distKm) / (2 * h.spreadKm() * h.spreadKm()));
          double lat = round5(h.lat() + dLatKm / KM_PER_DEG_LAT);
          double lng = round5(h.lng() + dLngKm / kmPerDegLng);
          for (int b = 0; b < BUCKETS.length; b++) {
            double noise = 0.85 + rng.nextDouble() * 0.3;
            double weight = Math.min(1d, h.strength() * decay * h.profile().multipliers[b] * noise);
            if (weight < 0.02) continue; // drop negligible cells
            docs.add(
                AudienceMobility.builder()
                    .countryId(h.country())
                    .lat(lat)
                    .lng(lng)
                    .weight(Math.round(weight * 1000d) / 1000d)
                    .timeBucket(BUCKETS[b])
                    .source("seed")
                    .build());
          }
        }
      }
      repository.saveAll(docs);
      log.info(
          "Seeded {} audience mobility points across {} hotspots", docs.size(), HOTSPOTS.size());
    } catch (Exception e) {
      // Never block application startup on seed data.
      log.warn("Audience mobility seeding skipped due to error: {}", e.getMessage());
    }
  }

  private static double round5(double v) {
    return Math.round(v * 100000d) / 100000d;
  }
}
