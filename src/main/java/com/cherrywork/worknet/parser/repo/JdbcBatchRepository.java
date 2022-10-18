package com.cherrywork.worknet.parser.repo;

import java.util.List;
import java.util.Map;

public interface JdbcBatchRepository {

	void prepareUpsertStatementBatch(Map<String, List<Map<String, Object>>> map);

}
