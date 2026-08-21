package com.mw.recommendation.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Feature caps for CSV inventory import. The Spring multipart limit is very permissive (1024MB), so
 * these enforce a sane per-feature bound. Bound from {@code recommendation.csv.*}.
 *
 * @param maxRows max data rows accepted before the parse aborts with FILE_TOO_LARGE
 * @param maxFileBytes max upload size in bytes before rejection with FILE_TOO_LARGE (default 5MB)
 * @param maxPageSize hard cap applied to page size on list/paged endpoints
 */
@ConfigurationProperties(prefix = "recommendation.csv")
public record CsvImportProperties(
    @DefaultValue("5000") int maxRows,
    @DefaultValue("5242880") long maxFileBytes,
    @DefaultValue("100") int maxPageSize) {}
