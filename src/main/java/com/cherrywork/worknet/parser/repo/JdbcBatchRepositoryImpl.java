package com.cherrywork.worknet.parser.repo;

import java.io.StringReader;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cherrywork.worknet.composite.utils.ServicesUtil;
import com.cherrywork.worknet.config.ApplicationConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class JdbcBatchRepositoryImpl implements JdbcBatchRepository {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	String prefix, comma;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Value("${app.database}")
	private String dbName;

	private List<Map<String, Object>> prefixConfigMap;

	@Override
	public void prepareUpsertStatementBatch(Map<String, List<Map<String, Object>>> map) {
		prefixConfigMap = jdbcTemplate.queryForList("select * from CW_ITM_PREFIX_CONFIG ");
		ObjectMapper mapper = new ObjectMapper();

		map.entrySet().stream().forEach(mapEntry -> {

			String tableName = mapEntry.getKey();

			StringBuilder str = new StringBuilder();
			StringBuilder columnList = new StringBuilder();
			List<String> columns = new ArrayList<>();
			StringBuilder paramList = new StringBuilder();
			List<Map<String, Object>> insertDataList = new ArrayList<>();
			List<Map<String, Object>> updateDataList = new ArrayList<>();
			prefix = "";

			List<String> primaryKeys = getPrimaryKey(tableName);

			String concatKeys = concatKeys(primaryKeys);

			Map<String, Map<String, Object>> concatDataMap = getConcatDataMap(mapEntry.getValue(), primaryKeys);
			Map<String, Map<String, Object>> metaDataList;
			if ("hana".equalsIgnoreCase(dbName)) {
				metaDataList = getMetaDataHana(tableName);
			} else {
				metaDataList = getMetaData(tableName);
			}

			try {
				logger.info("metaDataList : " + mapper.writeValueAsString(metaDataList));
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}

			List<String> updateList = checkDataExists(concatKeys, concatDataMap, tableName);
			logger.info("updateList : " + updateList);

			concatDataMap.entrySet().stream().forEach(dataMap -> {
				if (updateList.contains(dataMap.getKey())) {
					updateDataList.add(dataMap.getValue());
				} else {
					insertDataList.add(dataMap.getValue());
				}
			});

			if (updateDataList != null && !updateDataList.isEmpty()) {

				Map<String, Object> tableMap = mapEntry.getValue().get(0);
				Set<String> tableColumns = tableMap.keySet();
				if (primaryKeys.size() != tableColumns.size()) {
					tableMap.entrySet().stream().forEach(entry -> {

						if (!primaryKeys.contains(entry.getKey()) && (!"CW_ITM_WN_TASKS".equalsIgnoreCase(tableName)
								|| !"PROCESS_NAME".equalsIgnoreCase(entry.getKey()))) {

							columnList.append(prefix);
							paramList.append(prefix);
							prefix = ",";
							columnList.append(entry.getKey() + "=?");
							paramList.append("?");
							columns.add(entry.getKey());

						}
					});
					prefix = "";

					str.append("  UPDATE " + tableName);
					str.append("  SET  ");
					str.append(columnList.toString());
					str.append("  WHERE  ");
					primaryKeys.stream().forEach(primaryKey -> {
						str.append(prefix);
						str.append(primaryKey + "=?");
						prefix = " AND "; // ","
						columns.add(primaryKey);
					});

					logger.info("update statement " + str.toString());
					jdbcTemplate.batchUpdate(str.toString(), updateDataList, updateDataList.size(),
							(ps, dataMap) -> setDataToPreparedStmt(ps, dataMap, metaDataList, columns));
				}
			}

			if (insertDataList != null && !insertDataList.isEmpty()) {
				columnList.setLength(0);
				paramList.setLength(0);
				columns.clear();
				str.setLength(0);
				prefix = "";

				if ("CW_ITM_WN_TASKS".equalsIgnoreCase(tableName)) {
					mapEntry.getValue().forEach(obj -> {

						try {
							if (obj.get("SYSTEM_ID") != null && obj.get("PROCESS_NAME") != null
									&& obj.get("TASK_TYPE") != null) {

								String prefix = prefixConfigMap.stream()
										.filter(a -> (a.get("SYSTEM_ID").toString()
												.equalsIgnoreCase(obj.get("SYSTEM_ID").toString())
												&& a.get("PROCESS_NAME").toString()
														.equalsIgnoreCase(obj.get("PROCESS_NAME").toString())
												&& a.get("TASK_TYPE").toString()
														.equalsIgnoreCase(obj.get("TASK_TYPE").toString())))
										.map(s -> s.get("TASK_PREFIX")).findAny().get().toString();

								if ("hana".equalsIgnoreCase(dbName)) {
									Long id = incrementTaskIdAndGetForHana();
									obj.put("ITM_TASK_ID", prefix + "_" + id);
								} else {
									int id = incrementTaskIdAndGetForMysql();
									obj.put("ITM_TASK_ID", prefix + "_" + id);

								}

							}
						} catch (Exception e) {
							logger.error("Error in Task Prefix" + e.getMessage());
						}

						if (obj.get("CREATED_ON") != null) {
							Date creadedOn = new Date();
							try {
								if ("Ariba".equalsIgnoreCase(obj.get("SYSTEM_ID").toString())) {
									creadedOn = new Date(Long.parseLong(obj.get("CREATED_ON").toString()));

								} else {
									creadedOn = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
											.parse(obj.get("CREATED_ON").toString());
								}
							} catch (ParseException e) {
								e.printStackTrace();
							}
							if (obj.get("COMP_DEADLINE") != null && obj.get("COMP_DEADLINE") != ""
									&& ServicesUtil.isInteger(obj.get("COMP_DEADLINE").toString())) {
								obj.put("COMP_DEADLINE", ServicesUtil.addDays(creadedOn,
										Integer.parseInt(obj.get("COMP_DEADLINE").toString())));
							}
							if (obj.get("CRITICAL_DEADLINE") != null && obj.get("CRITICAL_DEADLINE") != ""
									&& ServicesUtil.isInteger(obj.get("CRITICAL_DEADLINE").toString())) {
								obj.put("CRITICAL_DEADLINE", ServicesUtil.addDays(creadedOn,
										Integer.parseInt(obj.get("CRITICAL_DEADLINE").toString())));
							}
						} else {
							if (obj.get("COMP_DEADLINE") != null && obj.get("COMP_DEADLINE") != ""
									&& ServicesUtil.isInteger(obj.get("COMP_DEADLINE").toString())) {
								obj.put("COMP_DEADLINE", ServicesUtil.addDays(new Date(),
										Integer.parseInt(obj.get("COMP_DEADLINE").toString())));
							}

							if (obj.get("CRITICAL_DEADLINE") != null && obj.get("CRITICAL_DEADLINE") != ""
									&& ServicesUtil.isInteger(obj.get("CRITICAL_DEADLINE").toString())) {
								obj.put("CRITICAL_DEADLINE", ServicesUtil.addDays(new Date(),
										Integer.parseInt(obj.get("CRITICAL_DEADLINE").toString())));
							}
						}
					});

				} else if ("CW_ITM_WN_PROCESS".equalsIgnoreCase(tableName)) {
					mapEntry.getValue().forEach(obj -> {
						try {
							String prefix = prefixConfigMap.stream()
									.filter(a -> (a.get("SYSTEM_ID").toString()
											.equalsIgnoreCase(obj.get("SYSTEM_ID").toString())
											&& a.get("PROCESS_NAME").toString()
													.equalsIgnoreCase(obj.get("PROCESS_NAME").toString())))
									.map(s -> s.get("PROCESS_PREFIX")).findAny().get().toString();

							if ("hana".equalsIgnoreCase(dbName)) {
								Long id = incrementProcessIdAndGetForHana();
								obj.put("ITM_PROCESS_ID", prefix + "_" + id);
							} else {
								int id = incrementTaskIdAndGetForMysql();
								obj.put("ITM_PROCESS_ID", prefix + "_" + id);
							}
						} catch (Exception e) {
							logger.error("Error in Setting ProcessId: " + e.getMessage());
						}
					});

				}

				str.append(" INSERT INTO " + tableName);
				str.append(" (");

				Map<String, Object> tableMap = mapEntry.getValue().get(0);
				tableMap.entrySet().stream().forEach(entry -> {
					if (!"CW_ITM_WN_TASKS".equalsIgnoreCase(tableName)
							|| !"PROCESS_NAME".equalsIgnoreCase(entry.getKey())) {
						columnList.append(prefix);
						paramList.append(prefix);
						prefix = ",";
						columnList.append(entry.getKey());
						paramList.append("?");
						columns.add(entry.getKey());
					}
				});

				str.append(columnList);
				str.append(") VALUES(");
				str.append(paramList);
				str.append(")   ");

				logger.info("query statement " + str.toString());
				List<Map<String, Object>> list = insertDataList.stream()
						.filter(obj -> "CW_ITM_WN_TASKS".equalsIgnoreCase(tableName) && obj.get("TASK_ID") != null
								&& obj.get("STATUS") != null
								|| "CW_ITM_WN_PROCESS".equalsIgnoreCase(tableName) && obj.get("PROCESS_ID") != null
								|| "CW_ITM_WN_TASK_OWNERS".equalsIgnoreCase(tableName) && obj.get("OWNER_ID") != null
								|| "CW_ITM_WN_TASK_ATTRIBUTES".equalsIgnoreCase(tableName) && obj.get("TASK_ID") != null
										&& obj.get("ATTR_KEY") != null)
						.map(obj -> obj).collect(Collectors.toList());
				jdbcTemplate.batchUpdate(str.toString(), list, insertDataList.size(),
						(ps, dataMap) -> setDataToPreparedStmt(ps, dataMap, metaDataList, columns));
			}

		});
	}

	private Map<String, Map<String, Object>> getConcatDataMap(List<Map<String, Object>> dataList,
			List<String> primaryKeys) {
		Map<String, Map<String, Object>> concatDataMap = new HashMap<>();
		dataList.stream().forEach(dataMap -> {
			StringBuilder concatValues = new StringBuilder();

			for (String pk : primaryKeys) {
				if (dataMap.containsKey(pk)) {
					concatValues.append(dataMap.get(pk) != null ? dataMap.get(pk).toString() : "");
				}
			}
			concatDataMap.put(concatValues.toString(), dataMap);

		});
		return concatDataMap;
	}

	private String concatKeys(List<String> primaryKeys) {
		int n = primaryKeys.size();
		StringBuilder str = new StringBuilder();

		if (primaryKeys != null && !primaryKeys.isEmpty()) {

			if (primaryKeys.size() == 1) {
				return primaryKeys.get(0);
			} else if (primaryKeys.size() == 2) {
				str.append("CONCAT(");
				str.append(primaryKeys.get(0));
				str.append(",");
				str.append(primaryKeys.get(1));
				str.append(")");
			} else {
				StringBuilder sb1 = new StringBuilder();
				sb1.append("CONCAT(");
				sb1.append(primaryKeys.get(n - 2));
				sb1.append(",");
				sb1.append(primaryKeys.get(n - 1));
				sb1.append(")");
				for (int i = primaryKeys.size() - 3; i >= 0; i--) {
					str.append("CONCAT(");
					str.append(primaryKeys.get(i));
					str.append(",");
					str.append(sb1);
					str.append(")");
					sb1 = new StringBuilder(str);
					str.setLength(0);
				}
				return sb1.toString();
			}
		}
		return str.toString();
	}

	private Map<String, Map<String, Object>> getMetaDataHana(String tableName) {
		Map<String, Map<String, Object>> map = new HashMap<>();

		StringBuilder str = new StringBuilder(
				"Select distinct COLUMN_NAME,DATA_TYPE_NAME,LENGTH,COLUMN_ID from TABLE_COLUMNS WHERE table_name='"
						+ tableName + "'");

		List<Map<String, Object>> localList = jdbcTemplate.queryForList(str.toString());

		if (localList != null && !localList.isEmpty()) {
			localList.forEach(object -> {
				map.put(object.get("COLUMN_NAME").toString(), object);
			});
		}
		return map;
	}

	private List<String> checkDataExists(String concatKeys, Map<String, Map<String, Object>> concatDataMap,
			String tableName) {
		List<String> updateList = new ArrayList<>();
		comma = "";
		StringBuilder str = new StringBuilder();
		str.append("SELECT ");
		str.append(concatKeys);
		str.append(" FROM ");
		str.append(tableName);
		str.append(" WHERE ");
		str.append(concatKeys);
		str.append(" IN (");

		concatDataMap.entrySet().stream().forEach(map -> {
			str.append(comma);
			str.append("'");
			str.append(map.getKey());
			str.append("'");
			comma = ",";
		});
		str.append(")");

		logger.info("in query to check primary key value : " + str.toString());

		List<Map<String, Object>> localList = jdbcTemplate.queryForList(str.toString());
		if (localList != null && !localList.isEmpty()) {
			localList.stream().forEach(map -> {
				updateList.add(map.get(concatKeys).toString());
			});
		}
		return updateList;

	}

	private List<String> getPrimaryKey(String tableName) {
		List<String> primaryKeys = new ArrayList<>();
		String str = null;
		if ("hana".equalsIgnoreCase(dbName)) {
			str = "SELECT COLUMN_NAME FROM SYS.CONSTRAINTS WHERE TABLE_NAME = '" + tableName
					+ "' AND IS_PRIMARY_KEY='TRUE'";
		} else {
			str = " SHOW KEYS FROM " + tableName + " WHERE Key_name = 'PRIMARY'";
		}

		List<Map<String, Object>> localList = jdbcTemplate.queryForList(str);

		if (localList != null && !localList.isEmpty()) {
			localList.stream().forEach(object -> {
				primaryKeys.add(object.get("COLUMN_NAME").toString());
			});
		}
		return primaryKeys;
	}

	private Map<String, Map<String, Object>> getMetaData(String tableName) {
		Map<String, Map<String, Object>> map = new HashMap<>();

		StringBuilder str = new StringBuilder(
				"Select distinct UPPER(COLUMN_NAME) as COLUMN_NAME, UPPER(DATA_TYPE) as DATA_TYPE, CHARACTER_MAXIMUM_LENGTH as LENGTH from INFORMATION_SCHEMA.COLUMNS WHERE table_name='"
						+ tableName + "'");

		List<Map<String, Object>> localList = jdbcTemplate.queryForList(str.toString());

		if (localList != null && !localList.isEmpty()) {
			localList.forEach(object -> {
				map.put(object.get("COLUMN_NAME").toString(), object);
			});
		}
		return map;
	}

	public void setDataToPreparedStmt(PreparedStatement ps, Map<String, Object> dataMap,
			Map<String, Map<String, Object>> metaDataList, List<String> columns) throws SQLException {
		Integer index = 1;

		try {

			for (String column : columns) {
				Map<String, Object> metaData = metaDataList.get(column);
				Object columnValue = dataMap.get(column);
				String dataType = null;
				if ("hana".equalsIgnoreCase(dbName)) {
					dataType = metaData.get("DATA_TYPE_NAME") != null ? metaData.get("DATA_TYPE_NAME").toString()
							: "varchar";
				} else {
					dataType = metaData.get("DATA_TYPE") != null ? metaData.get("DATA_TYPE").toString() : "varchar";
				}
				String length = metaData.get("LENGTH") != null ? metaData.get("LENGTH").toString() : "255";

				if (ApplicationConstants.NVARCHAR.equalsIgnoreCase(dataType)
						|| ApplicationConstants.STRING.equalsIgnoreCase(dataType)
						|| ApplicationConstants.VARCHAR.equalsIgnoreCase(dataType)
						|| ApplicationConstants.CHAR.equalsIgnoreCase(dataType)) {

					if (columnValue != null && length != null
							&& Integer.parseInt(length) >= columnValue.toString().length()) {
						ps.setString(index, columnValue.toString());
					} else {
						ps.setString(index, "");

					}

				} else if (ApplicationConstants.INTEGER.equalsIgnoreCase(dataType)
						|| ApplicationConstants.INT.equalsIgnoreCase(dataType)) {
					if (columnValue != null && !"".equals(columnValue)) {
						ps.setInt(index, (Integer) columnValue);

					} else {
						ps.setNull(index, java.sql.Types.INTEGER);

					}
				} else if (ApplicationConstants.TINYINT.equalsIgnoreCase(dataType)) {
					if (columnValue != null && !"".equals(columnValue)) {
						if ("true".equals(columnValue)) {
							ps.setInt(index, 1);
						} else {
							ps.setInt(index, 0);
						}

					}
				} else if (ApplicationConstants.DATE.equalsIgnoreCase(dataType)) {
					if (columnValue != null && !"".equals(columnValue)) {
						Long time = null;

						if (columnValue.getClass().equals(Long.class)) {
							time = (Long) columnValue;

						} else if (columnValue.getClass().equals(Integer.class)) {
							time = (long) (Integer) columnValue;

						} else if (LocalDateTime.class.equals(columnValue.getClass())) {
							time = ((LocalDateTime) columnValue).atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
						} else if (Date.class.equals(columnValue.getClass())) {
							time = ((Date) columnValue).getTime();
						} else if (String.class.equals(columnValue.getClass())) {
							String dateString = (String) columnValue;
							DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
							try {
								Date date = format.parse(dateString);
								time = date.getTime();
							} catch (ParseException e) {
								DateFormat secondFormat = new SimpleDateFormat("MM/dd/yyyy");
								try {
									Date date = secondFormat.parse(dateString);
									time = date.getTime();
								} catch (ParseException ex) {
									logger.error("Exception" + e.getMessage());

								}
							}
						}

						if (time != null) {

							ps.setDate(index, new java.sql.Date(time));
						} else if (java.sql.Date.class.equals(columnValue.getClass())) {

							ps.setDate(index, (java.sql.Date) columnValue);

						} else {

							ps.setDate(index, null);

						}
					} else {
						ps.setDate(index, null);
					}
				} else if (ApplicationConstants.TIMESTAMP.equalsIgnoreCase(dataType)
						|| ApplicationConstants.DATETIME.equalsIgnoreCase(dataType)) {
					if (columnValue != null && !"".equals(columnValue)) {
						Long time = null;

						if (columnValue.getClass().equals(Long.class)) {
							time = (Long) columnValue;

						} else if (columnValue.getClass().equals(Integer.class)) {
							time = (long) (Integer) columnValue;

						} else if (LocalDateTime.class.equals(columnValue.getClass())) {
							time = ((LocalDateTime) columnValue).atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
						} else if (Date.class.equals(columnValue.getClass())) {
							time = ((Date) columnValue).getTime();
						} else if (columnValue.getClass().equals(String.class)) {
							try {
								time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(columnValue.toString())
										.getTime();
							} catch (Exception e) {
								time = Long.parseLong(columnValue.toString());
							}
						}

						if (time != null) {

							ps.setTimestamp(index, new java.sql.Timestamp(time));

						} else if (java.sql.Timestamp.class.equals(columnValue.getClass())) {

							ps.setTimestamp(index, (java.sql.Timestamp) columnValue);

						} else {

							ps.setTimestamp(index, null);

						}
					} else {
						ps.setTimestamp(index, null);
					}
				} else if (ApplicationConstants.BOOLEAN.equalsIgnoreCase(dataType)) {

					Boolean value = columnValue != null && !"".equals(columnValue) ? (Boolean) columnValue : null;
					if (value != null) {
						ps.setBoolean(index, value);
					} else {
						ps.setNull(index, java.sql.Types.BOOLEAN);
					}

				} else if (ApplicationConstants.DECIMAL.equalsIgnoreCase(dataType)) {

					if (columnValue != null && !"".equals(columnValue)) {

						Double originalValue = Double.valueOf(columnValue.toString());

						ps.setDouble(index,

								originalValue);
					} else {
						ps.setNull(index, java.sql.Types.DECIMAL);
					}

				} else if (ApplicationConstants.FLOAT.equalsIgnoreCase(dataType)) {

					if (columnValue != null && !"".equals(columnValue)) {

						Float originalValue = Float.valueOf(columnValue.toString());
						ps.setFloat(index,

								originalValue);
					} else {
						ps.setNull(index, java.sql.Types.FLOAT);
					}

				} else if (ApplicationConstants.DOUBLE.equalsIgnoreCase(dataType)) {

					if (columnValue != null && !"".equals(columnValue)) {
						Double originalValue = Double.valueOf(columnValue.toString());
						ps.setDouble(index,

								originalValue);
					} else {
						ps.setNull(index, java.sql.Types.DOUBLE);
					}

				} else if (ApplicationConstants.CLOB.equalsIgnoreCase(dataType)) {

					if (columnValue != null && !"".equals(columnValue)) {
						ps.setCharacterStream(1, new StringReader(columnValue.toString()),
								columnValue.toString().length());
					} else {
						ps.setNull(index, java.sql.Types.CLOB);
					}
				}
				index++;
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Exception" + e.getMessage());
		}

	}

	public int incrementProcessIdAndGetForMysql() {
		String query = "SELECT NextVal('CW_ITM_PROCESS_SEQUENCE') as ID";
		Map<String, Object> result = jdbcTemplate.queryForMap(query);
		return Integer.parseInt(result.get("ID").toString());

	}

	public Long incrementProcessIdAndGetForHana() {
		return jdbcTemplate.queryForObject("SELECT CW_ITM_PROCESS_SEQUENCE.NEXTVAL FROM DUMMY", Long.class);
	}

	public int incrementTaskIdAndGetForMysql() {
		String query = "SELECT NextVal('CW_ITM_TASK_SEQUENCE') as ID";
		Map<String, Object> result = jdbcTemplate.queryForMap(query);
		return Integer.parseInt(result.get("ID").toString());
	}

	public Long incrementTaskIdAndGetForHana() {
		return jdbcTemplate.queryForObject("SELECT CW_ITM_TASK_SEQUENCE.NEXTVAL FROM DUMMY", Long.class);
	}
}
