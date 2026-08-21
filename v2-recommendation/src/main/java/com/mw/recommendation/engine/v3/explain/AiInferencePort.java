package com.mw.recommendation.engine.v3.explain;

import java.util.Optional;

/**
 * Port for the PRD §3.4/§7 AI layer (Gemini with OpenAI fallback). v3 ships only the keyword-map
 * default adapter — the LLM adapters are a separate epic requiring vendor keys and security review.
 * Implementations must be classification-only: the PRD forbids AI-computed numbers (impressions,
 * pricing, availability).
 */
public interface AiInferencePort {

  /** Infers a venue type from inventory naming/metadata. Empty when confidence is too low. */
  Optional<Inference> inferVenueType(String inventoryName, String address, String metadata);

  /** Maps a brand name to a probable IAB category. Empty when confidence is too low. */
  Optional<Inference> mapBrandToCategory(String brandName);

  record Inference(String value, double confidence, String source) {}
}
