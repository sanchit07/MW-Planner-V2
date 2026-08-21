package com.mw.planner.repository;

import com.mw.planner.domain.Statement;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StatementRepository extends MongoRepository<Statement, String> {
  List<Statement> findByCompanyId(String companyId);

  List<Statement> findByParentStatementId(String parentStatementId);
}
