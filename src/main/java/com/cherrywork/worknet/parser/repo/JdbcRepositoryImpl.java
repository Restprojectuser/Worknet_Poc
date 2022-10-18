package com.cherrywork.worknet.parser.repo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import com.cherrywork.worknet.composite.utils.ServicesUtil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

@Repository
public class JdbcRepositoryImpl implements JdbcRepository {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	String prefix;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private Environment environment;

	Map<String, Map<String, String>> statusMap;

	@Override
	public Map<String, List<Map<String, Object>>> convertJsonToTableData(Map<Integer, Map<String, Object>> taskMap,
			Map<String, Map<String, String>> tableMapObject, Boolean isCustomAttributePresent, String origin) {
		List<String> keyValues = new ArrayList<>();
		Map<String, List<Map<String, Object>>> tableMap = new HashMap<>();
		ObjectMapper mapper = new ObjectMapper();
		Map<String, String> userMapping = getUserIdMapping(origin);

		// fetch prefix
		String userSystemPrefix = getSystemPrefix(origin);

		try {
			statusMap = readYamlFile("applicationConfig/systemStatusConfig",
					new TypeReference<Map<String, Map<String, String>>>() {
					});

			logger.debug("statusMap" + statusMap);

		} catch (Exception e) {
			logger.error("ERROR reading Staus Config" + e.getMessage());
		}

		taskMap.entrySet().stream().forEach(taskDataEntry -> {
			tableMapObject.entrySet().stream().forEach(tablesMapEntry -> {

				if (isCustomAttributePresent && "CW_ITM_WN_TASK_ATTRIBUTES".equals(tablesMapEntry.getKey())) {
					taskDataEntry.getValue().entrySet().stream().filter(entry -> !entry.getKey().contains("/"))
							.forEach(entry -> {

								final Map<String, Object> map = new HashMap<>();

								tablesMapEntry.getValue().entrySet().stream().forEach(tableEntry -> {

									Object value = taskDataEntry.getValue().get(tableEntry.getValue());
									if ("ATTR_KEY".equals(tableEntry.getKey())) {
										value = entry.getKey();
									} else if ("ATTR_VALUE".equals(tableEntry.getKey())) {
										if (entry.getValue() != null && !"".equals(entry.getValue().toString())
												&& entry.getValue().toString().contains("[")) {
											JsonNode attrValue = null;
											try {
												attrValue = mapper.readTree(entry.getValue().toString());
												if (attrValue != null
														&& JsonNodeType.ARRAY == attrValue.getNodeType()) {
													value = "";
												} else {
													value = entry.getValue() != null
															&& !"".equals(entry.getValue().toString())
																	? entry.getValue()
																	: "";
												}
											} catch (Exception e) {
												logger.error("Error converting to JsonNode {}", e.getMessage());
											}
										} else {
											value = entry.getValue() != null && !"".equals(entry.getValue().toString())
													? entry.getValue()
													: "";
										}
									} else if (tableEntry.getValue().startsWith("@")) {

										value = tableEntry.getValue().substring(2, tableEntry.getValue().length() - 1);

									} else if ("TASK_ID".equals(tableEntry.getKey())) {
										value = taskDataEntry.getValue().get(tableEntry.getValue());
									} else if (("COMPLETED_AT".equals(tableEntry.getKey())
											|| "CREATED_ON".equals(tableEntry.getKey())
											|| "UPDATED_ON".equals(tableEntry.getKey())
											|| "CRITICAL_DEADLINE".equals(tableEntry.getKey())
											|| "COMP_DEADLINE".equals(tableEntry.getKey()))
											&& !tableEntry.getValue().startsWith("@")) {

										if (value != null && !"".equals(value.toString())) {

											if (value.toString().contains("Date(")) {
												value = ServicesUtil.getFormattedDate(value.toString());
											} else {
												value = ServicesUtil.getFormattedDateAzure(value.toString());
											}
										}
									} else if ("CREATED_BY".equals(tableEntry.getKey())
											|| "UPDATED_BY".equals(tableEntry.getKey())) {
										String userName = taskDataEntry.getValue().get(tableEntry.getValue()) != null
												? taskDataEntry.getValue().get(tableEntry.getValue()).toString()
												: "";
										if (userSystemPrefix != null && userSystemPrefix != ""
												&& userName.startsWith(userSystemPrefix)) {
											userName = userName.replaceFirst(userSystemPrefix, "");
										}
										String user = userMapping.get(userName);
										value = user == null ? userName : user;
									} else {
										value = taskDataEntry.getValue().get(tableEntry.getValue());
									}

									if (value == null || "".equals(value.toString())) {
										map.put(tableEntry.getKey(), null);
									} else {
										map.put(tableEntry.getKey(), value);
									}
								});
								if (tableMap.containsKey(tablesMapEntry.getKey())) {
									tableMap.get(tablesMapEntry.getKey()).add(map);
								} else {
									List<Map<String, Object>> list = new ArrayList<>();
									list.add(map);
									tableMap.put(tablesMapEntry.getKey(), list);

								}
							});

				} else if (isCustomAttributePresent
						&& "CW_ITM_WN_TASK_SUB_ATTRIBUTES".equals(tablesMapEntry.getKey())) {
					taskDataEntry.getValue().entrySet().stream().filter(entry -> !entry.getKey().contains("/"))
							.forEach(entry -> {
								if (entry.getValue() != null && !"".equals(entry.getValue().toString())
										&& entry.getValue().toString().contains("[")) {
									JsonNode attrValue = null;
									try {
										attrValue = mapper.readTree(entry.getValue().toString());
										if (attrValue != null && JsonNodeType.ARRAY == attrValue.getNodeType()) {
											ArrayNode arrayNode = (ArrayNode) attrValue;
											final Map<String, Object> map = new HashMap<>();

											tablesMapEntry.getValue().entrySet().stream().forEach(tableEntry -> {

												Object value = taskDataEntry.getValue().get(tableEntry.getValue());
												if ("ATTR_KEY".equals(tableEntry.getKey())) {
													value = entry.getKey();
												} else if (tableEntry.getValue().startsWith("@")) {

													value = tableEntry.getValue().substring(2,
															tableEntry.getValue().length() - 1);

												} else if ("TASK_ID".equals(tableEntry.getKey())) {
													value = taskDataEntry.getValue().get(tableEntry.getValue());
												} else if (("COMPLETED_AT".equals(tableEntry.getKey())
														|| "CREATED_ON".equals(tableEntry.getKey())
														|| "UPDATED_ON".equals(tableEntry.getKey())
														|| "CRITICAL_DEADLINE".equals(tableEntry.getKey())
														|| "COMP_DEADLINE".equals(tableEntry.getKey()))
														&& !tableEntry.getValue().startsWith("@")) {

													if (value != null && !"".equals(value.toString())) {

														if (value.toString().contains("Date(")) {
															value = ServicesUtil.getFormattedDate(value.toString());
														} else {
															value = ServicesUtil
																	.getFormattedDateAzure(value.toString());
														}
													}
												} else if ("CREATED_BY".equals(tableEntry.getKey())
														|| "UPDATED_BY".equals(tableEntry.getKey())) {
													String userName = taskDataEntry.getValue()
															.get(tableEntry.getValue()) != null
																	? taskDataEntry.getValue()
																			.get(tableEntry.getValue()).toString()
																	: "";
													if (userSystemPrefix != null && userSystemPrefix != ""
															&& userName.startsWith(userSystemPrefix)) {
														userName = userName.replaceFirst(userSystemPrefix, "");
													}
													String user = userMapping.get(userName);
													value = user == null ? userName : user;
												} else {
													value = taskDataEntry.getValue().get(tableEntry.getValue());
												}

												if (value == null || "".equals(value.toString())) {
													map.put(tableEntry.getKey(), null);
												} else {
													map.put(tableEntry.getKey(), value);
												}
											});

											for (int i = 0; i < arrayNode.size(); i++) {
												JsonParser jsonParser = arrayNode.get(i).traverse();
												Map<String, Object> treeMap = mapper.readValue(jsonParser,
														new TypeReference<Map<String, Object>>() {
														});

												for (Entry<String, Object> jsonNode : treeMap.entrySet()) {
													Map<String, Object> subAttrMap = new HashMap<>(map);
													subAttrMap.put("CHILD_ATTR_KEY", jsonNode.getKey());
													subAttrMap.put("CHILD_ATTR_SEQ", i);
													subAttrMap.put("CHILD_ATTR_VALUE", jsonNode.getValue());
													if (tableMap.containsKey(tablesMapEntry.getKey())) {
														tableMap.get(tablesMapEntry.getKey()).add(subAttrMap);
													} else {
														List<Map<String, Object>> list = new ArrayList<>();
														list.add(subAttrMap);
														tableMap.put(tablesMapEntry.getKey(), list);

													}
												}

											}

										}
									} catch (Exception e) {
										logger.error("Error converting to JsonNode {}", e.getMessage());
									}
								}
							});

				} else {

					final Map<String, Object> map = new HashMap<>();
					final Map<String, Object> ownerMap = new HashMap<>();
					StringBuilder key = new StringBuilder();
					key.append(tablesMapEntry.getKey());

					tablesMapEntry.getValue().entrySet().stream().forEach(tableEntry -> {

						Object value = taskDataEntry.getValue().get(tableEntry.getValue());

						if (("PROCESS_ID".equals(tableEntry.getKey()) || "TASK_ID".equals(tableEntry.getKey())
								|| "PROCESS_NAME".equals(tableEntry.getKey())) && value != null) {
							key.append(value.toString());
						}
						if ("OWNER_ID".equals(tableEntry.getKey())) {
							if ("Ariba".equalsIgnoreCase(origin) && "group".equalsIgnoreCase((String) taskDataEntry
									.getValue().get(tablesMapEntry.getValue().get("OWNER_TYPE")))) {
								String userName = taskDataEntry.getValue().get(
										"get_process_task_details./approvalRequests/approvers/group/uniqueName") != null
												? taskDataEntry.getValue().get(
														"get_process_task_details./approvalRequests/approvers/group/uniqueName")
														.toString()
												: "";
								if (userSystemPrefix != null && userSystemPrefix != ""
										&& userName.startsWith(userSystemPrefix)) {
									userName = userName.replaceFirst(userSystemPrefix, "");
								}
								String user = userMapping.get(userName);
								value = user == null ? userName : user;
							} else {
								String userName = taskDataEntry.getValue().get(tableEntry.getValue()) != null
										? taskDataEntry.getValue().get(tableEntry.getValue()).toString()
										: "";
								if (userSystemPrefix != null && userSystemPrefix != ""
										&& userName.startsWith(userSystemPrefix)) {
									userName = userName.replaceFirst(userSystemPrefix, "");
								}
								String user = userMapping.get(userName);
								value = user == null ? userName : user;
							}
						} else if ("OWNER_TYPE".equals(tableEntry.getKey()) && "Ariba".equalsIgnoreCase(origin)) {
							value = taskDataEntry.getValue().get(tableEntry.getValue());
						} else if (tableEntry.getValue() != null && tableEntry.getValue().startsWith("@")) {

							value = tableEntry.getValue().substring(2, tableEntry.getValue().length() - 1);

						} else if (("COMPLETED_AT".equals(tableEntry.getKey())
								|| "COMP_DEADLINE".equals(tableEntry.getKey())
								|| "CREATED_ON".equals(tableEntry.getKey()) || "UPDATED_ON".equals(tableEntry.getKey())
								|| "CRITICAL_DEADLINE".equals(tableEntry.getKey()))
								&& !tableEntry.getValue().startsWith("@")) {

							if (value != null && !"".equals(value.toString())) {

								if (value.toString().contains("Date(")) {
									value = ServicesUtil.getFormattedDate(value.toString());
								} else {
									value = ServicesUtil.getFormattedDateAzure(value.toString());
								}
							}
						} else if ("CREATED_BY".equals(tableEntry.getKey())
								|| "UPDATED_BY".equals(tableEntry.getKey())) {
							String userName = taskDataEntry.getValue().get(tableEntry.getValue()) != null
									? taskDataEntry.getValue().get(tableEntry.getValue()).toString()
									: "";
							if (userSystemPrefix != null && userSystemPrefix != ""
									&& userName.startsWith(userSystemPrefix)) {
								userName = userName.replaceFirst(userSystemPrefix, "");
							}
							String user = userMapping.get(userName);
							value = user == null ? userName : user;
						} else {
							value = taskDataEntry.getValue().get(tableEntry.getValue());
						}

						// replace system status to ITM status if available in statusMap
						if (statusMap.containsKey(origin)
								&& "CW_ITM_WN_TASKS".equalsIgnoreCase((String) tablesMapEntry.getKey())
								&& "STATUS".equalsIgnoreCase(tableEntry.getKey())) {
							value = statusMap.get(origin).get(value) != null ? statusMap.get(origin).get(value) : value;
						}

						if ("CW_ITM_WN_TASK_OWNERS".equalsIgnoreCase(tablesMapEntry.getKey())
								&& tableEntry.getValue().contains("recipientUsers")) {
							logger.debug("recipientUsers");

							taskDataEntry.getValue().forEach((jsonKey, data) -> {
								if (tableEntry.getValue().equalsIgnoreCase(jsonKey.replaceAll("\\/[0-9]+", ""))) {
									ownerMap.put(jsonKey, data);
								}
							});
						}

						if (value == null || "".equals(value.toString())) {
							map.put(tableEntry.getKey(), null);
						} else {
							map.put(tableEntry.getKey(), value);
						}
					});

					if (!keyValues.contains(key.toString())) {
						if (tableMap.containsKey(tablesMapEntry.getKey())) {
							if ("CW_ITM_WN_TASK_OWNERS".equalsIgnoreCase(tablesMapEntry.getKey()) && map != null
									&& ("SCP".equalsIgnoreCase(map.get("SYSTEM_ID").toString())
											|| "SCP_BTP".equalsIgnoreCase(map.get("SYSTEM_ID").toString()))) {
								ownerMap.entrySet().stream().forEach(owner -> {
									Map<String, Object> maptemp = new HashMap<>(map);
									if (userMapping.containsKey(owner.getValue())) {
										maptemp.put("OWNER_ID", userMapping.get(owner.getValue()));

									} else {
										maptemp.put("OWNER_ID", owner.getValue());
									}
									tableMap.get(tablesMapEntry.getKey()).add(maptemp);
								});
							} else {
								tableMap.get(tablesMapEntry.getKey()).add(map);
							}
						} else {
							List<Map<String, Object>> list = new ArrayList<>();
							if ("CW_ITM_WN_TASK_OWNERS".equalsIgnoreCase(tablesMapEntry.getKey()) && map != null
									&& ("SCP".equalsIgnoreCase(map.get("SYSTEM_ID").toString())
											|| "SCP_BTP".equalsIgnoreCase(map.get("SYSTEM_ID").toString()))) {
								ownerMap.entrySet().stream().forEach(owner -> {

									Map<String, Object> maptemp = new HashMap<>(map);
									if (userMapping.containsKey(owner.getValue())) {
										maptemp.put("OWNER_ID", userMapping.get(owner.getValue()));

									} else {
										maptemp.put("OWNER_ID", owner.getValue());
									}
									list.add(maptemp);
								});
							} else {
								list.add(map);
							}
							tableMap.put(tablesMapEntry.getKey(), list);

						}
					}
					keyValues.add(key.toString());

					key.setLength(0);
				}
			});
		});
		return tableMap;

	}

	@Override
	public String executeQuery(String query) {

		jdbcTemplate.execute(query);

		return "Success";
	}

	@Override
	public List<Map<String, Object>> executeQueryByList(String query) {
		return jdbcTemplate.queryForList(query);
	}

	@Override
	public Map<String, String> getUserIdMapping(String origin) {
		Map<String, String> userIdMap = new HashMap<>();
		StringBuilder builder = new StringBuilder();

		builder.append("SELECT USER_ID,SYSTEM_USER_ID FROM CW_ITM_WN_USER_SYSTEM_MAP WHERE SYSTEM_ID='" + origin + "'");
		List<Map<String, Object>> resultList = jdbcTemplate.queryForList(builder.toString());
		if (resultList != null && !resultList.isEmpty()) {
			resultList.forEach(map -> {
				userIdMap.put(map.get("SYSTEM_USER_ID") != null ? map.get("SYSTEM_USER_ID").toString() : null,
						map.get("USER_ID") != null ? map.get("USER_ID").toString() : null);
			});
		}
		return userIdMap;
	}

	private String getSystemPrefix(String origin) {
		StringBuilder builder = new StringBuilder();

		builder.append("SELECT USER_PREFIX FROM CW_ITM_WN_SYSTEM_MASTER WHERE SYSTEM_ID='" + origin + "'");
		return jdbcTemplate.queryForObject(builder.toString(), String.class);
	}

	@Override
	public List<Map<String, Object>> getTaskAttributesByTaskIds(List<String> taskIds) {
		boolean isFirst = true;
		StringBuilder query = new StringBuilder();
		query.append("SELECT ATTR_KEY,ATTR_VALUE,TASK_ID FROM CW_ITM_WN_TASK_ATTRIBUTES WHERE TASK_ID IN ( ");
		for (String taskId : taskIds) {
			if (!isFirst) {
				query.append(",");
			}
			query.append("'" + taskId + "'");
			isFirst = false;
		}
		query.append(" )");
		return jdbcTemplate.queryForList(query.toString());

	}

	@Override
	public List<Map<String, Object>> getArchivedTasks(String origin, Set<String> processNames, List<String> taskIds) {
		AtomicBoolean isFirst = new AtomicBoolean();
		isFirst.set(true);
		StringBuilder query = new StringBuilder();
		query.append(
				"SELECT  T.TASK_ID , T.SYSTEM_ID , T.PROCESS_ID , T.TASK_TYPE , T.TASK_DESC , T.STATUS , T.PRIORITY , (UNIX_TIMESTAMP(T.COMP_DEADLINE) * 1000) as COMP_DEADLINE, (UNIX_TIMESTAMP(T.CRITICAL_DEADLINE) * 1000) as CRITICAL_DEADLINE , T.JOB_RUN_ID , T.UPDATED_BY , (UNIX_TIMESTAMP(T.UPDATED_ON) * 1000) as UPDATED_ON , T.CREATED_BY , (UNIX_TIMESTAMP(T.CREATED_ON) * 1000) as CREATED_ON , (UNIX_TIMESTAMP(T.COMPLETED_AT) * 1000) as COMPLETED_AT , T.COMPLETED_BY ");
		query.append(
				" FROM CW_ITM_WN_TASKS T INNER JOIN CW_ITM_WN_PROCESS P ON P.PROCESS_ID=T.PROCESS_ID WHERE T.TASK_ID NOT IN ( ");
		// task
		taskIds.forEach(taskId -> {
			if (!isFirst.get()) {
				query.append(",");
			}
			query.append("'" + taskId + "'");
			isFirst.set(false);
		});
		query.append(" ) AND ");

		// process
		isFirst.set(true);
		query.append(" P.PROCESS_NAME IN ( ");
		processNames.forEach(processName -> {
			if (!isFirst.get()) {
				query.append(",");
			}
			query.append("'" + processName + "'");
			isFirst.set(false);
		});
		query.append(" ) AND ");
		query.append(" T.SYSTEM_ID='" + origin + "' AND T.STATUS IN ('READY','RESERVED') ");
		return jdbcTemplate.queryForList(query.toString());
	}

	@Override
	public void deleteArchivedTasks(String origin, Set<String> processNames, List<String> archivedTaskIds) {
		AtomicBoolean isFirst = new AtomicBoolean();
		isFirst.set(true);
		StringBuilder query = new StringBuilder();
		query.append(
				"delete T from CW_ITM_WN_TASKS T INNER JOIN CW_ITM_WN_PROCESS P ON P.PROCESS_ID=T.PROCESS_ID WHERE T.TASK_ID IN ( ");
		// task
		archivedTaskIds.forEach(taskId -> {
			if (!isFirst.get()) {
				query.append(",");
			}
			query.append("'" + taskId + "'");
			isFirst.set(false);
		});
		query.append(" ) AND ");

		// process
		isFirst.set(true);
		query.append(" P.PROCESS_NAME IN ( ");
		processNames.forEach(processName -> {
			if (!isFirst.get()) {
				query.append(",");
			}
			query.append("'" + processName + "'");
			isFirst.set(false);
		});
		query.append(" ) AND ");
		query.append(" T.SYSTEM_ID='" + origin + "' AND T.STATUS IN ('READY','RESERVED') ");
		jdbcTemplate.execute(query.toString());

	}

	@Override
	public List<Map<String, Object>> getUserIdForTasks(List<String> taskIds) {
		// String inSql = String.join(",", Collections.nCopies(taskIds.size(), "?"));

		boolean isFirst = true;
		StringBuilder query = new StringBuilder();
		query.append("SELECT OWNER_ID, OWNER_TYPE FROM CW_ITM_WN_task_owners WHERE task_id IN  ( ");
		for (String taskId : taskIds) {
			if (!isFirst) {
				query.append(",");
			}
			query.append("'" + taskId + "'");
			isFirst = false;
		}
		query.append(" )");
		return jdbcTemplate.queryForList(query.toString());

	}

	@Override
	public Map<String, Object> getWorkflowBySystemIdAndProcessName(String systemId, String processName) {
		List<Map<String, Object>> resultList = null;
		StringBuilder builder = new StringBuilder("select * from cw_itm_native_workflow_config where SYSTEM_ID ='"
				+ systemId + "' and PROCESS_NAME ='" + processName + "'");

		resultList = jdbcTemplate.queryForList(builder.toString());
		if (resultList != null && !resultList.isEmpty()) {
			return resultList.get(0);
		}
		return null;
	}

	@Override
	public List<String> getPendingTasks(String processId, String taskId) {
		StringBuilder builder = new StringBuilder("select TASK_ID from cw_itm_wn_tasks t where t.PROCESS_ID ='"
				+ processId + "' and t.TASK_ID !='" + taskId + "'");

		List<Map<String, Object>> resultList = jdbcTemplate.queryForList(builder.toString());
		if (resultList != null && !resultList.isEmpty()) {
			return resultList.stream().map(obj -> obj.get("TASK_ID").toString()).collect(Collectors.toList());
		}
		return null;
	}

	public <T> T readYamlFile(String mappingFilePath, TypeReference<T> typeReference) throws IOException {
		ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

		String fooResourceUrl = environment.getProperty("spring.config.import").replace("optional:configserver:", "")
				+ "/application/default/main/" + environment.getProperty("spring.application.name") + "/"
				+ mappingFilePath + ".yaml";
		ResponseEntity<String> response = restTemplate.exchange(fooResourceUrl, HttpMethod.GET, null, String.class);
		return objectMapper.readValue(response.getBody(), typeReference);
	}

	@Override
	public List<String> getCompletedTasks(String origin, Set<String> processNames, List<String> taskIds) {

		List<String> completedTaskIds = new ArrayList<>();

		if (origin == null || "".equals(origin) || processNames == null || processNames.isEmpty() || taskIds == null
				|| taskIds.isEmpty()) {
			return completedTaskIds;
		}

		AtomicBoolean isFirst = new AtomicBoolean();
		isFirst.set(true);
		StringBuilder query = new StringBuilder();
		query.append(
				"SELECT  T.TASK_ID  FROM CW_ITM_WN_TASKS T INNER JOIN CW_ITM_WN_PROCESS P ON P.PROCESS_ID=T.PROCESS_ID "
						+ "WHERE T.TASK_ID IN ( ");
		// task
		taskIds.forEach(taskId -> {
			if (!isFirst.get()) {
				query.append(",");
			}
			query.append("'" + taskId + "'");
			isFirst.set(false);
		});
		query.append(" ) AND ");

		// process
		isFirst.set(true);
		query.append(" P.PROCESS_NAME IN ( ");
		processNames.forEach(processName -> {
			if (!isFirst.get()) {
				query.append(",");
			}
			query.append("'" + processName + "'");
			isFirst.set(false);
		});
		query.append(" ) AND ");
		query.append(" T.SYSTEM_ID='" + origin + "' AND T.STATUS ='COMPLETED' ");
		completedTaskIds = jdbcTemplate.queryForList(query.toString()).stream()
				.map(tasks -> tasks.get("TASK_ID").toString()).collect(Collectors.toList());

		logger.info(this.getClass().getSimpleName() + " | getCompletedTasks() | query - > " + query.toString());
		logger.info(this.getClass().getSimpleName() + " | getCompletedTasks() | completedTaskIds -> "
				+ completedTaskIds.toString());

		return completedTaskIds;
	}

	@Override
	public List<String> getReadyReservedTasks(String origin, Set<String> processNames, List<String> taskIds) {

		List<String> tasks = new ArrayList<>();

		if (origin == null || "".equals(origin) || processNames == null || processNames.isEmpty() || taskIds == null
				|| taskIds.isEmpty()) {
			return tasks;
		}
		AtomicBoolean isFirst = new AtomicBoolean();
		isFirst.set(true);
		StringBuilder query = new StringBuilder();
		query.append(
				"SELECT  T.TASK_ID  FROM CW_ITM_WN_TASKS T INNER JOIN CW_ITM_WN_PROCESS P ON P.PROCESS_ID=T.PROCESS_ID "
						+ "WHERE T.TASK_ID NOT IN ( ");
		// task
		taskIds.forEach(taskId -> {
			if (!isFirst.get()) {
				query.append(",");
			}
			query.append("'" + taskId + "'");
			isFirst.set(false);
		});
		query.append(" ) AND ");

		// process
		isFirst.set(true);
		query.append(" P.PROCESS_NAME IN ( ");
		processNames.forEach(processName -> {
			if (!isFirst.get()) {
				query.append(",");
			}
			query.append("'" + processName + "'");
			isFirst.set(false);
		});
		query.append(" ) AND ");
		query.append(" T.SYSTEM_ID='" + origin + "' AND T.STATUS IN ('READY','RESERVED') ");

		tasks = jdbcTemplate.queryForList(query.toString()).stream().map(task -> task.get("TASK_ID").toString())
				.collect(Collectors.toList());

		logger.info(this.getClass().getSimpleName() + " | getReadyReservedTasks() | query - > " + query.toString());
		logger.info(this.getClass().getSimpleName() + " | getReadyReservedTasks() | readyReservedTasks -> "
				+ tasks.toString());

		return tasks;

	}

	@Override
	public void changeStatusOfTasks(List<String> tasks) {

		if (tasks == null || tasks.isEmpty()) {
			return;
		}
		AtomicBoolean isFirst = new AtomicBoolean();
		isFirst.set(true);
		StringBuilder query = new StringBuilder();
		query.append("UPDATE CW_ITM_WN_TASKS SET STATUS='COMPLETED' WHERE TASK_ID IN ( ");

		// task
		tasks.forEach(taskId -> {
			if (!isFirst.get()) {
				query.append(",");
			}
			query.append("'" + taskId + "'");
			isFirst.set(false);
		});
		query.append(" ) ");
		jdbcTemplate.execute(query.toString());
		logger.info(this.getClass().getSimpleName() + " | changeStatusOfTasks() | ended");
	}

	@Override
	public Map<String, Object> getUserSystemAttributes(String userId, String systemId) {
		StringBuilder query = new StringBuilder();
		query.append("SELECT ATTR_KEY,ATTR_VALUE FROM CW_ITM_WN_USER_SYSTEM_ATTRIBUTES WHERE ");
		query.append("SYSTEM_ID = '" + systemId + "' AND ");
		query.append("USER_ID = '" + userId + "'");
		List<Map<String, Object>> result = jdbcTemplate.queryForList(query.toString());
		return result.stream().collect(Collectors.toMap(s -> (String) s.get("ATTR_KEY"), s -> s.get("ATTR_VALUE")));
	}

	@Override
	public Map<String, Object> getLastJobResponse(String origin) {
		String query = "SELECT JOB_OUTPUT from CW_ITM_WN_JOB_LOG where ORIGIN = '" + origin
				+ "' and JOB_STATE='Completed' order by END_TIME desc limit 1";
		return jdbcTemplate.queryForMap(query);

	}

	@Override
	public int[] clearOldJobResponse(String id, String origin) {
		String query = "UPDATE CW_ITM_WN_JOB_LOG set JOB_OUTPUT = null,JOB_INPUT= null  where ORIGIN = '" + origin
				+ "' and JOB_STATE != 'Running' and ID !='" + id + "'";
		return jdbcTemplate.batchUpdate(query);
	}

	@Override
	public Map<String, Object> getBBCPostActionConfig(String processName, String systemId, String taskType,
			String taskAction) {

		String query = "SELECT * from CW_ITM_BBC_POST_ACTION_CONFIG where PROCESS_NAME = '" + processName
				+ "' and SYSTEM_ID='" + systemId + "'  and TASK_TYPE='" + taskType + "' and TASK_ACTION='" + taskAction
				+ "'";
		List<Map<String, Object>> result = jdbcTemplate.queryForList(query);
		return result.size() > 0 ? result.get(0) : null;
	}

}
