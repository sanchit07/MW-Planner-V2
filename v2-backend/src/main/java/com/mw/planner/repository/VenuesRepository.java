package com.mw.planner.repository;

import com.mw.planner.domain.Venues;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VenuesRepository extends MongoRepository<Venues, String> {}
