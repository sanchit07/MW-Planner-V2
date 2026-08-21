package com.mw.planner.repository;

import com.mw.planner.domain.AvailabilitySyncStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/** Repository for availability sync status documents. */
@Repository
public interface AvailabilitySyncStatusRepository
    extends MongoRepository<AvailabilitySyncStatus, String> {}
