package com.mw.planner.repository;

import com.mw.planner.domain.Creative;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CreativeRepository extends MongoRepository<Creative, String> {
  List<Creative> findByCompanyIdAndIsActiveTrue(String companyId);
}
