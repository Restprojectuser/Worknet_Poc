package com.cherrywork.worknet.parser.repo;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface JdbcRepository {

	String executeQuery(String query);

	List<Map<String, Object>> executeQueryByList(String query);

	Map<String, String> getUserIdMapping(String origin);

	Map<String, List<Map<String, Object>>> convertJsonToTableData(Map<Integer, Map<String, Object>> taskMap,
			Map<String, Map<String, String>> tableMapObject, Boolean isCustomAttributePresent, String origin);

	List<Map<String, Object>> getTaskAttributesByTaskIds(List<String> taskIds);

	List<Map<String, Object>> getArchivedTasks(String origin, Set<String> processNames, List<String> taskIds);

	void deleteArchivedTasks(String origin, Set<String> processNames, List<String> archivedTaskIds);

	List<Map<String, Object>> getUserIdForTasks(List<String> taskIds);

	Map<String, Object> getWorkflowBySystemIdAndProcessName(String systemId, String processName);

	List<String> getPendingTasks(String processId, String taskId);

	List<String> getCompletedTasks(String origin, Set<String> processNames, List<String> taskIds);

	List<String> getReadyReservedTasks(String origin, Set<String> processNames, List<String> taskIds);

	void changeStatusOfTasks(List<String> tasks);

	Map<String, Object> getUserSystemAttributes(String userId, String systemId);

	Map<String, Object> getLastJobResponse(String origin);

	int[] clearOldJobResponse(String id, String origin);

	Map<String, Object> getBBCPostActionConfig(String processName, String systemId, String taskType, String taskAction);

}
