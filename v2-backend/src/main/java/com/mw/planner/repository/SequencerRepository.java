package com.mw.planner.repository;

import com.mw.planner.domain.Sequencer;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SequencerRepository extends MongoRepository<Sequencer, String> {}
