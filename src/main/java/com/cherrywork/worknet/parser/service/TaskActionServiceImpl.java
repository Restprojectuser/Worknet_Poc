package com.cherrywork.worknet.parser.service;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.cherrywork.worknet.aspect.AsyncExecuter;
import com.cherrywork.worknet.composite.config.ApplicationConstant;
import com.cherrywork.worknet.composite.dto.CompositeInput;
import com.cherrywork.worknet.composite.dto.CompositeOutput;
import com.cherrywork.worknet.composite.helpers.ResponseDto;
import com.cherrywork.worknet.composite.service.CompositeApiServiceNew;
import com.cherrywork.worknet.config.ApplicationConstants;
import com.cherrywork.worknet.parser.dto.ForwardOwnerDto;
import com.cherrywork.worknet.parser.dto.ResponseMessage;
import com.cherrywork.worknet.parser.helper.ActionDto;
import com.cherrywork.worknet.parser.helper.TaskDto;
import com.cherrywork.worknet.parser.repo.JdbcRepository;
import com.cherrywork.worknet.parser.util.CrudApiRest;
import com.cherrywork.worknet.parser.util.ScpActionUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

@Service
public class TaskActionServiceImpl implements TaskActionService {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private CompositeApiServiceNew compositeApiServiceNew;

	@Autowired
	private CompositeApiParserService compositeApiParserService;

	@Autowired
	private CrudApiRest crudApiRest;

	@Autowired
	private JdbcRepository jdbcRepository;

	@Autowired
	private Environment environment;

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private AsyncExecuter asyncExecuter;

	@Autowired
	private Environment env;

	@Autowired
	private ScpActionUtil scpActionUtil;

	@Override
	public ResponseMessage taskAction(HttpServletRequest request, ActionDto dto, String authorization, String userId) {

		logger.info("[" + this.getClass().getSimpleName() + "| taskAction() |" + " Execution start  input ");
		ResponseMessage responseDto = new ResponseMessage();
		DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS");
		List<ResponseMessage> responseList = new ArrayList<>();
		Map<String, Map<String, Object>> attributeMap = new HashMap<>();
		try {
			responseDto.setStatus(ApplicationConstant.SUCCESS);
			responseDto.setStatusCode(ApplicationConstant.CODE_SUCCESS);
			responseDto.setMessage("Tasks action performed successfully!");
			ResponseMessage responseMessage = null;
			if (dto != null && !dto.getTasks().isEmpty()) {

				List<String> taskIds = dto.getTasks().stream()
						.filter(taskDto -> (!ApplicationConstants.ACTION_CLAIM.equals(taskDto.getAction())
								&& !ApplicationConstants.ACTION_FORWARD.equals(taskDto.getAction())
								&& !ApplicationConstants.ACTION_RELEASED.equals(taskDto.getAction())))
						.map(TaskDto::getTaskId).collect(Collectors.toList());
				if (taskIds != null && !taskIds.isEmpty()) {
					attributeMap = getTaskAttributesByTaskIds(taskIds);
				}

				for (TaskDto taskDto : dto.getTasks()) {
					responseMessage = new ResponseMessage();
					taskDto.setAttributes(attributeMap.get(taskDto.getTaskId()));

					switch (taskDto.getAction().toLowerCase()) {

					case ApplicationConstants.ACTION_CLAIM:
						try {
							Object[] inputArr = {};
							if ("SAP".equalsIgnoreCase(env.getProperty("app.platform"))) {
								if ("SCP".equalsIgnoreCase(taskDto.getSystemId())
										|| "SCP_BTP".equalsIgnoreCase(taskDto.getSystemId())) {
									responseMessage = scpActionUtil.actionOnTask(taskDto, authorization);

									if (ApplicationConstant.STATUS_FAILURE.equalsIgnoreCase(responseMessage.getStatus())) {
										return responseMessage;
									}
								}
							}
							responseMessage.setStatus(ApplicationConstant.SUCCESS);
							responseMessage.setStatusCode(ApplicationConstant.SUCCESS);
							if (env.getRequiredProperty("db.type").equals("hana")) {
								Object[] attr = new Object[] { new Object[] { taskDto.getTaskId(), taskDto.getSystemId(),
										userId, "USER", 1L, taskDto.getUserId(), formatter.format(new Date()),
										taskDto.getUserId(), formatter.format(new Date()), taskDto.getTaskId(), userId,
										taskDto.getSystemId() } };
								inputArr = mergeArray(inputArr, attr);
							} else {
								Object[] attr = new Object[] { new Object[] { taskDto.getTaskId(), taskDto.getSystemId(),
										userId, "USER", 1L, taskDto.getUserId(), formatter.format(new Date()),
										taskDto.getUserId(), formatter.format(new Date()) } };
								inputArr = mergeArray(inputArr, attr);
							}
							// change task status to RESERVED
							crudApiRest.batchUpdateForCrudApi("updateTaskStatus",
									new Object[] {
											new Object[] { "RESERVED", taskDto.getUserId(), userId, taskDto.getTaskId() } },
									authorization);

							// change other owners isPrimaryFlag to false
							crudApiRest.batchUpdateForCrudApi("updatePrimaryOwnerByOwnerIdNotIn",
									new Object[] { new Object[] { 0L, taskDto.getTaskId(), taskDto.getUserId() } },
									authorization);
							if (env.getRequiredProperty("db.type").equals("hana")) {
								crudApiRest.batchUpdateForCrudApi("insertOwnerHana", inputArr, authorization);
							} else {
								crudApiRest.batchUpdateForCrudApi("insertOwner", inputArr, authorization);

							}
						}catch(Exception e) {
							responseMessage.setStatus(ApplicationConstant.FAILURE);
							responseMessage.setStatusCode(ApplicationConstant.FAILURE);
						}
						

						break;

					case ApplicationConstants.ACTION_FORWARD:
						try {
							if ("SAP".equalsIgnoreCase(env.getProperty("app.platform"))) {
								if ("SCP".equalsIgnoreCase(taskDto.getSystemId())
										|| "SCP_BTP".equalsIgnoreCase(taskDto.getSystemId())) {
									responseMessage = scpActionUtil.actionOnTask(taskDto, authorization);

									if (ApplicationConstant.STATUS_FAILURE.equalsIgnoreCase(responseMessage.getStatus())) {
										return responseMessage;
									}
								}
							}
							responseMessage.setStatus(ApplicationConstant.SUCCESS);
							responseMessage.setStatusCode(ApplicationConstant.SUCCESS);
							// change task status to READY
							crudApiRest.batchUpdateForCrudApi("updateTaskStatus",
									new Object[] {
											new Object[] { "READY", taskDto.getUserId(), userId, taskDto.getTaskId() } },
									authorization);
							// change other owners isPrimaryFlag to false
							crudApiRest.batchUpdateForCrudApi("updatePrimaryOwnerByTaskId",
									new Object[] { new Object[] { 0L, taskDto.getTaskId() } }, authorization);
							Object[] inputArray = {};
							if (taskDto.getForwardOwners() != null && !taskDto.getForwardOwners().isEmpty()) {

								for (ForwardOwnerDto forwardOwner : taskDto.getForwardOwners()) {
									// if (forwardOwner.getGroupId() == null) {
									if (env.getRequiredProperty("db.type").equals("hana")) {
										Object[] attr = { new Object[] { taskDto.getTaskId(), taskDto.getSystemId(),
												forwardOwner.getOwnerId(), forwardOwner.getOwnerType(), 1L,
												taskDto.getUserId(), formatter.format(new Date()), taskDto.getUserId(),
												formatter.format(new Date()), taskDto.getTaskId(),
												forwardOwner.getOwnerId(), taskDto.getSystemId() } };
										inputArray = mergeArray(inputArray, attr);
									} else {
										Object[] attr = { new Object[] { taskDto.getTaskId(), taskDto.getSystemId(),
												forwardOwner.getOwnerId(), forwardOwner.getOwnerType(), 1L,
												taskDto.getUserId(), formatter.format(new Date()), taskDto.getUserId(),
												formatter.format(new Date()) } };
										inputArray = mergeArray(inputArray, attr);
									}

								}
								logger.debug(inputArray.toString());
								if (env.getRequiredProperty("db.type").equals("hana")) {
									crudApiRest.batchUpdateForCrudApi("insertOwnerHana", inputArray, authorization);
								} else {
									crudApiRest.batchUpdateForCrudApi("insertOwner", inputArray, authorization);
								}
							}
						}catch(Exception e){
							responseMessage.setStatus(ApplicationConstant.FAILURE);
							responseMessage.setStatusCode(ApplicationConstant.FAILURE);
						}
						
						break;

					case ApplicationConstants.ACTION_RELEASED:
						try {
							if ("SAP".equalsIgnoreCase(env.getProperty("app.platform"))) {
								if ("SCP".equalsIgnoreCase(taskDto.getSystemId())
										|| "SCP_BTP".equalsIgnoreCase(taskDto.getSystemId())) {
									responseMessage = scpActionUtil.actionOnTask(taskDto, authorization);

									if (ApplicationConstant.STATUS_FAILURE.equalsIgnoreCase(responseMessage.getStatus())) {
										return responseMessage;
									}
								}
							}
							responseMessage.setStatus(ApplicationConstant.SUCCESS);
							responseMessage.setStatusCode(ApplicationConstant.SUCCESS);
							// change task status to READY
							crudApiRest.batchUpdateForCrudApi("updateTaskStatus",
									new Object[] {
											new Object[] { "READY", taskDto.getUserId(), userId, taskDto.getTaskId() } },
									authorization);
							// change other owners isPrimaryFlag to true
							crudApiRest.batchUpdateForCrudApi("updatePrimaryOwnerByOwnerIdNotIn",
									new Object[] { new Object[] { 1L, taskDto.getTaskId(), userId } }, authorization);
						}catch(Exception e) {
							responseMessage.setStatus(ApplicationConstant.FAILURE);
							responseMessage.setStatusCode(ApplicationConstant.FAILURE);
						}
						
						break;

					default:
						Map<String, Object> workflowObject = jdbcRepository
								.getWorkflowBySystemIdAndProcessName(taskDto.getSystemId(), taskDto.getProcessName());

						if (workflowObject != null && !workflowObject.isEmpty()) {
							if (workflowObject != null && "APPROVE".equals(taskDto.getAction())) {
								saveNextTask(taskDto, workflowObject, authorization);
							}
							crudApiRest.batchUpdateForCrudApi("updateTaskStatus",
									new Object[] { "COMPLETED", taskDto.getUserId(), userId, taskDto.getTaskId() },
									authorization);
						} else // this code is for sapphire to be deleted
						if (taskDto.getProcessName() != null && (ApplicationConstants.TRAVELANDEXPENSEAPPROVAL
								.equals(taskDto.getProcessName())
								|| ApplicationConstants.MSOUTLOOKTASKS.equals(taskDto.getProcessName())
								|| ApplicationConstants.ASSETREQUEST.equals(taskDto.getProcessName())
								|| ApplicationConstants.EXPENSESUBMISSIONS.equals(taskDto.getProcessName())
								|| ApplicationConstants.LEAVEREQUESTS.equals(taskDto.getProcessName())
								|| ApplicationConstants.CHANGEPERSONALINFORMATION.equals(taskDto.getProcessName()))) {
							crudApiRest.updateCrudApi("updateTaskStatus",
									new Object[] { "COMPLETED", taskDto.getUserId(), userId, taskDto.getTaskId() },
									authorization);
						} else {
							if ("SAP".equalsIgnoreCase(env.getProperty("app.platform"))) {
								if ("SCP".equalsIgnoreCase(taskDto.getSystemId())
										|| "SCP_BTP".equalsIgnoreCase(taskDto.getSystemId())) {
									responseMessage = scpActionUtil.actionOnTask(taskDto, authorization);

									if (ApplicationConstant.STATUS_FAILURE.equalsIgnoreCase(responseMessage.getStatus())) {
										return responseMessage;
									}
									
									String id = taskDto.getReferenceId() != null ? taskDto.getReferenceId() : taskDto.getTaskId();

									if (ApplicationConstant.STATUS_SUCCESS.equalsIgnoreCase(responseMessage.getStatus())) {
										try {

											crudApiRest.batchUpdateForCrudApi("updateTaskStatus", new Object[] {
													new Object[] { "COMPLETED", taskDto.getUserId(), userId, taskDto.getTaskId() } },
													authorization);

										} catch (Exception e) {
											logger.debug("[WBP-Dev]Error Adding Audit" + e.getMessage());
										}

										String status = "";
										if ("approve".equals(taskDto.getAction().toLowerCase())
												|| "release".equals(taskDto.getAction().toLowerCase())) {
											status = taskDto.getAction().toLowerCase() + "d";
										} else {
											status = taskDto.getAction().toLowerCase() + "ed";
										}
										responseMessage.setMessage("Task(" + id + ") " + status + " successfully!");
										responseMessage.setStatus(ApplicationConstant.SUCCESS);
										responseMessage.setStatusCode(ApplicationConstant.SUCCESS);
									} else {
										responseMessage.setStatus(ApplicationConstant.FAILURE);
										responseMessage.setStatusCode(ApplicationConstant.FAILURE);
										responseMessage.setMessage(
												"Task(" + id + ") action was unsuccessful due to " + responseMessage.getMessage());
									}
								}
							}else {
							
							responseMessage = performAction(taskDto, attributeMap.get(taskDto.getTaskId()),
									authorization, userId);
							}
						}
						break;
					}
					String status = "";
					if ("approve".equals(taskDto.getAction().toLowerCase())
							|| "release".equals(taskDto.getAction().toLowerCase())) {
						status = taskDto.getAction().toLowerCase() + "d";
					} else {
						status = taskDto.getAction().toLowerCase() + "ed";
					}

					if (responseMessage.getStatus() == null) {
						String id = taskDto.getReferenceId() != null ? taskDto.getReferenceId() : taskDto.getTaskId();
						responseMessage.setMessage("Task(" + id + ") " + status + " successfully!");
						responseMessage.setStatus(ApplicationConstant.SUCCESS);
						responseMessage.setStatusCode(ApplicationConstant.SUCCESS);

					} else if (ApplicationConstant.SUCCESS.equals(responseMessage.getStatus())) {
						Object[] OuterObjArray = new Object[1];
						OuterObjArray[0] = new Object[] { UUID.randomUUID().toString().replaceAll("-", ""),
								taskDto.getSystemId(), taskDto.getTaskId(), taskDto.getAction(), taskDto.getUserId(),
								taskDto.getForwardOwners() != null ? String.join(",",
										taskDto.getForwardOwners().stream().map(ForwardOwnerDto::getOwnerId)
												.collect(Collectors.toList()))
										: null,
								taskDto.getComment(), taskDto.getUserId(), formatter.format(new Date()),
								taskDto.getUserId(), formatter.format(new Date()) };
						crudApiRest.batchUpdateForCrudApi("insertAudit", OuterObjArray, authorization);
					}
					responseList.add(responseMessage);
				}
				responseDto.setData(responseList);
			}
			asyncExecuter.callNotificationService(dto);
		} catch (Exception e) {
			logger.info("[" + this.getClass().getSimpleName() + "| taskAction() |" + "Exception Occured message is  "
					+ e.getMessage());
			responseDto.setStatus(ApplicationConstant.FAILURE);
			responseDto.setStatusCode(ApplicationConstant.CODE_FAILURE);
			responseDto.setMessage(e.getMessage());

		}
		logger.info("[" + this.getClass().getSimpleName() + "| taskAction() |"
				+ " Execution end  Output  is ResponseDto - " + responseDto);
		return responseDto;
	}

	private void saveNextTask(TaskDto taskDto, Map<String, Object> workflowObject, String authorization)
			throws JsonMappingException, JsonProcessingException, JSONException {
		ObjectMapper mapper = new ObjectMapper();

		DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS");
		List<Object[]> taskAttributes = new ArrayList<>();
		List<Object[]> ownerAttributes = new ArrayList<>();
		List<Object[]> taskAttributesArray = new ArrayList<>();

		Map<String, String> attrMap = getAttributeMap(taskDto.getTaskType(), authorization);

		JsonNode node = mapper.readTree(workflowObject.get("WF_NODES").toString());
		JsonNode edges = mapper.readTree(workflowObject.get("WF_EDGES").toString());
		Map<String, JsonNode> nodeMap = new HashMap<>();
		for (JsonNode nodeObj : node) {
			nodeMap.put(nodeObj.get("id").asText(), nodeObj);
		}
		Map<String, List<JsonNode>> edgesMap = new HashMap<>();
		edges.forEach(edgeObj -> {
			if (edgesMap.containsKey(edgeObj.get("source").asText())) {
				edgesMap.get(edgeObj.get("source").asText()).add(edgeObj);
			} else {
				List<JsonNode> targetList = new ArrayList<>();
				targetList.add(edgeObj);
				edgesMap.put(edgeObj.get("source").asText(), targetList);
			}
		});
		List<JsonNode> targetList = edgesMap.get(taskDto.getTaskType());
		if (targetList != null && !targetList.isEmpty()) {
			String processId = taskDto.getProcessId();
			List<JsonNode> targetNodes = new ArrayList<>();

			for (JsonNode target : targetList) {
				getTargetNodeByRecursion(nodeMap, edgesMap, targetNodes, taskDto, target.get("target").asText());
			}
			for (JsonNode targetnode : targetNodes) {
				String description = null;
				String taskId = UUID.randomUUID().toString().replaceAll("-", "");
				String taskType = targetnode.get("id").asText();
				if (targetnode.get("data") != null && targetnode.get("data").get("props") != null) {
					description = targetnode.get("data").get("props").get("description") != null
							? targetnode.get("data").get("props").get("description").asText()
							: targetnode.get("data").get("props").get("label").asText();
				}
				Object[] taskArgs = { taskId, taskDto.getSystemId(), processId, taskType,
						description != null ? description : taskType, "READY", "0", formatter.format(new Date()),
						formatter.format(new Date()), taskDto.getUserId(), formatter.format(new Date()),
						taskDto.getUserId(), formatter.format(new Date()) };
				taskAttributes.add(taskArgs);

				for (JsonNode owners : targetnode.get("data").get("props").get("assignees")) {
					Object[] ownerArgs = { taskId, taskDto.getSystemId(), owners.get("id").asText(),
							owners.get("type").asText(), 1L, taskDto.getUserId(), formatter.format(new Date()),
							taskDto.getUserId(), formatter.format(new Date()) };
					ownerAttributes.add(ownerArgs);
				}

				if (taskDto.getAttributes() != null && !taskDto.getAttributes().isEmpty()
						&& taskDto.getAttributes() != null && !taskDto.getAttributes().isEmpty()) {
					Object[] attrbuteArray = null;
					StringBuilder subInsertQuery = new StringBuilder(
							"INSERT INTO cw_itm_task_child_attributes (TASK_ID,SYSTEM_ID,ATTR_KEY,CHILD_VALUE,"
									+ "UPDATED_BY,UPDATED_ON, CREATED_BY,CREATED_ON) VALUES ");
					for (Map.Entry<String, Object> entry : taskDto.getAttributes().entrySet()) {

						if ("TableType".equals(attrMap.get(entry.getKey()).toString()) && entry.getValue() != null) {
							attrbuteArray = new Object[] { taskId, taskDto.getSystemId(), entry.getKey().toString(),
									null, taskDto.getUserId(), formatter.format(new Date()), taskDto.getUserId(),
									formatter.format(new Date()) };

							ObjectMapper objectMapper = new ObjectMapper();
							String blobData = objectMapper.writeValueAsString(entry.getValue());
							StringBuilder attrValueQuery = new StringBuilder();

							attrValueQuery.append("('" + taskId + "','");
							attrValueQuery.append(taskDto.getSystemId() + "','");
							attrValueQuery.append(entry.getKey().toString() + "',to_base64('" + blobData + "'),'");
							attrValueQuery.append(taskDto.getUserId() + "','");
							attrValueQuery.append(formatter.format(new Date()));
							attrValueQuery.append("','" + taskDto.getUserId() + "',");
							attrValueQuery.append("'" + formatter.format(new Date()) + "'");
							attrValueQuery.append(")");

							jdbcRepository.executeQuery(subInsertQuery.toString() + attrValueQuery.toString());

						} else {
							attrbuteArray = new Object[] { taskId, taskDto.getSystemId(), entry.getKey().toString(),
									entry.getValue().toString(), taskDto.getUserId(), formatter.format(new Date()),
									taskDto.getUserId(), formatter.format(new Date()) };
						}
						taskAttributesArray.add(attrbuteArray);
					}

				} // targetNode is user
			} // target for loop
			if (taskAttributes != null && !taskAttributes.isEmpty()) {
				crudApiRest.batchUpdateForCrudApi("insertTask", taskAttributes.toArray(), authorization);
			}
			if (ownerAttributes != null && !ownerAttributes.isEmpty()) {
				crudApiRest.batchUpdateForCrudApi("insertOwner", ownerAttributes.toArray(), authorization);
			}
			if (taskAttributesArray != null && !taskAttributesArray.isEmpty()) {
				crudApiRest.batchUpdateForCrudApi("insertAttributes", taskAttributesArray.toArray(), authorization);
			}

		}

	}

	private void getTargetNodeByRecursion(Map<String, JsonNode> nodeMap, Map<String, List<JsonNode>> edgesMap,
			List<JsonNode> targetNodes, TaskDto taskDto, String target) {
		JsonNode targetnode = nodeMap.get(target);
		if ("user".equals(targetnode.get("type").asText())) {
			targetNodes.add(targetnode);
		} else if ("decision".equals(targetnode.get("type").asText())) {
			String branchName = invokeTextRuleExecutionService(targetnode, taskDto.getAttributes(),
					taskDto.getSystemId() + taskDto.getProcessName() + taskDto.getTaskType());
			List<JsonNode> targetList = null;
			for (JsonNode jsonNode : edgesMap.get(targetnode.get("id").asText())) {
				if (jsonNode.get("data") != null && jsonNode.get("data").get("label") != null
						&& jsonNode.get("data").get("label").asText().equals(branchName)) {
					targetList = edgesMap.get(jsonNode.get("target").asText());
					break;
				}
			}
			if (targetList != null && !targetList.isEmpty()) {
				for (JsonNode tar : targetList) {
					getTargetNodeByRecursion(nodeMap, edgesMap, targetNodes, taskDto, tar.get("source").asText());
				}
			}
		}
	}

	private String invokeTextRuleExecutionService(JsonNode targetnode, Map<String, Object> attributes,
			String sourceId) {
		ObjectMapper mapper = new ObjectMapper();
		String branchName = "";
		try {
			ObjectNode body = mapper.createObjectNode();
			body.put("applicationName", "ITM");
			body.put("ruleId", targetnode.get("data").get("props").get("ruleId").asText());
			List<Map<String, Object>> conditions = new ArrayList<>();
			conditions.add(attributes);
			body.put("conditions", mapper.readTree(mapper.writeValueAsString(conditions)));

			HttpHeaders headers = new HttpHeaders();
			headers.add("Content-Type", "application/json");
			headers.add("Accept", "application/json");

			HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
			ResponseEntity<String> response = restTemplate.exchange(environment.getProperty("workrules-api-url")
					+ "/v4/textrule/invokeRule?source=ITM&sourceId=" + sourceId, HttpMethod.POST, entity, String.class);

			JsonNode responseNode = mapper.readTree(response.getBody());
			if (responseNode.get("data") != null && responseNode.get("data").get("result") != null
					&& responseNode.get("data").get("result").isArray()
					&& responseNode.get("data").get("result").size() > 0
					&& responseNode.get("data").get("result").get(0) != null) {
				branchName = responseNode.get("data").get("result").get(0).get("BRANCH").asText();
			}
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}

		return branchName;
	}

	private ResponseMessage performAction(TaskDto taskDto, Map<String, Object> attributeMap, String authorization,
			String userId) {

		Map<String, CompositeInput> compositeTableMap = null;
		try {
			compositeTableMap = compositeApiParserService.readYamlFile("applicationConfig/actionConfig",
					new TypeReference<Map<String, CompositeInput>>() {
					});
		} catch (IOException e) {
			logger.info("[" + this.getClass().getSimpleName() + "| performAction() |" + "Exception Occured message is  "
					+ e.getMessage());
		}

		CompositeInput compositeInputPayload = null;
		if (compositeTableMap
				.containsKey(taskDto.getSystemId() + "_" + taskDto.getProcessName() + "_" + taskDto.getAction())) {
			compositeInputPayload = compositeTableMap
					.get(taskDto.getSystemId() + "_" + taskDto.getProcessName() + "_" + taskDto.getAction());
		} else if (compositeTableMap.containsKey(taskDto.getSystemId() + "_" + taskDto.getAction())) {
			compositeInputPayload = compositeTableMap.get(taskDto.getSystemId() + "_" + taskDto.getAction());
		}
		if (compositeInputPayload != null && compositeInputPayload.getCompositeRequest() != null
				&& !compositeInputPayload.getCompositeRequest().isEmpty()) {
			taskDto.setAttributes(attributeMap);
			ObjectMapper taskDtoMapper = new ObjectMapper();
			Map<String, Object> taskDtoMap = taskDtoMapper.convertValue(taskDto, Map.class);
			if ("Ariba".equalsIgnoreCase(taskDto.getSystemId())) {
				Map<String, Object> getSystemAttributeMap = jdbcRepository.getUserSystemAttributes(taskDto.getUserId(),
						taskDto.getSystemId());
				taskDtoMap.putAll(getSystemAttributeMap);
			}
			compositeInputPayload.getCompositeRequest().get(compositeInputPayload.getCompositeRequest().size() - 1)
					.setCommonRequestBody(new ObjectMapper().valueToTree(taskDtoMap));
		}
		return performeActionOnTask(taskDto, compositeInputPayload, authorization, userId);
	}

	private ResponseMessage performeActionOnTask(TaskDto taskDto, CompositeInput compositeInputPayload,
			String authorization, String userId) {
		ResponseMessage responseMessage = new ResponseMessage();

		try {
			if (taskDto.getAction() != null) {
				if (compositeInputPayload != null) {

					CompositeInput compositeInputFileData = (CompositeInput) compositeApiParserService.readYamlFile(
							"composite/" + compositeInputPayload.getCompositeApiName(),
							new TypeReference<CompositeInput>() {
							});

					ResponseDto compositeResponseDto = compositeApiServiceNew
							.getCompositeResponse(compositeInputPayload, compositeInputFileData);

					responseMessage = checkCompositeResponse(compositeInputFileData, compositeResponseDto);

					logger.debug("responseMessage" + responseMessage);

					String id = taskDto.getReferenceId() != null ? taskDto.getReferenceId() : taskDto.getTaskId();

					if (ApplicationConstant.STATUS_SUCCESS.equalsIgnoreCase(responseMessage.getStatus())) {
						try {

							crudApiRest.batchUpdateForCrudApi("updateTaskStatus", new Object[] {
									new Object[] { "COMPLETED", taskDto.getUserId(), userId, taskDto.getTaskId() } },
									authorization);

						} catch (Exception e) {
							logger.debug("[WBP-Dev]Error Adding Audit" + e.getMessage());
						}

						String status = "";
						if ("approve".equals(taskDto.getAction().toLowerCase())
								|| "release".equals(taskDto.getAction().toLowerCase())) {
							status = taskDto.getAction().toLowerCase() + "d";
						} else {
							status = taskDto.getAction().toLowerCase() + "ed";
						}
						responseMessage.setMessage("Task(" + id + ") " + status + " successfully!");
						responseMessage.setStatus(ApplicationConstant.SUCCESS);
						responseMessage.setStatusCode(ApplicationConstant.SUCCESS);
					} else {
						responseMessage.setStatus(ApplicationConstant.FAILURE);
						responseMessage.setStatusCode(ApplicationConstant.FAILURE);
						responseMessage.setMessage(
								"Task(" + id + ") action was unsuccessful due to " + responseMessage.getMessage());
					}

				} else {
					responseMessage.setMessage("Action mapping is not maintained in config file!");
					responseMessage.setStatus(ApplicationConstant.FAILURE);
					responseMessage.setStatusCode(ApplicationConstant.CODE_FAILURE);
				}
			} else {
				responseMessage.setMessage("Action Type is Null");
				responseMessage.setStatus(ApplicationConstant.FAILURE);
				responseMessage.setStatusCode(ApplicationConstant.CODE_FAILURE);
			}

		} catch (Exception e) {
			responseMessage.setMessage(e.getMessage());
			responseMessage.setStatus(ApplicationConstant.FAILURE);
			responseMessage.setStatusCode(ApplicationConstant.CODE_FAILURE);
		}

		return responseMessage;
	}

	public ResponseMessage checkCompositeResponse(CompositeInput compositeInputFileData,
			ResponseDto compositeResponseDto) {
		ResponseMessage responseMessage = new ResponseMessage();
		compositeInputFileData.getCompositeRequest().parallelStream().forEach(compositeInput -> {
			CompositeOutput compositeOutput = (CompositeOutput) compositeResponseDto.getData();
			compositeOutput.getCompositeResponse().stream().forEach(output -> {
				if (!compositeInput.getReferenceId().equals(output.getReferenceId())
						|| compositeInput.getIsAction() == null || !compositeInput.getIsAction()
						|| compositeInput.getActionSuccessCode().equals(output.getStatus())) {
					responseMessage.setStatus(ApplicationConstant.SUCCESS);
					responseMessage.setStatusCode(ApplicationConstant.SUCCESS);
				} else if (output.getStatus() != null && output.getStatus() >= 200 && output.getStatus() <= 299) {
					responseMessage.setStatus(ApplicationConstant.SUCCESS);
					responseMessage.setStatusCode(ApplicationConstant.SUCCESS);
				} else {
					responseMessage.setStatus(ApplicationConstant.FAILURE);
					responseMessage.setStatusCode(ApplicationConstant.FAILURE);
					responseMessage.setMessage(output.getBody().toString());
				}
			});
		});

		return responseMessage;
	}

	public <T> T readYamlFile(String mappingFilePath, TypeReference<T> typeReference) throws IOException {
		ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
		ResourceLoader resourceLoader = new DefaultResourceLoader();
		Resource mappingResource = resourceLoader.getResource(mappingFilePath + ".yaml");
		return objectMapper.readValue(mappingResource.getInputStream(), typeReference);
	}

	public Map<String, Map<String, Object>> getTaskAttributesByTaskIds(List<String> taskIds) {
		Map<String, Map<String, Object>> map = new HashMap<>();
		List<Map<String, Object>> resultList = jdbcRepository.getTaskAttributesByTaskIds(taskIds);
		if (resultList != null && !resultList.isEmpty()) {
			resultList.forEach(obj -> {
				if (map.containsKey(obj.get("TASK_ID").toString())) {
					map.get(obj.get("TASK_ID").toString()).put(obj.get("ATTR_KEY").toString(), obj.get("ATTR_VALUE"));
				} else {
					Map<String, Object> objMap = new HashMap<>();
					objMap.put(obj.get("ATTR_KEY").toString(), obj.get("ATTR_VALUE"));
					map.put(obj.get("TASK_ID").toString(), objMap);
				}
			});
		}
		return map;
	}

	public Object[] mergeArray(Object[] arr1, Object[] arr2) {
		return Stream.of(arr1, arr2).flatMap(Stream::of).toArray();
	}

	private Map<String, String> getAttributeMap(String taskType, String authorization)
			throws JsonMappingException, JsonProcessingException, JSONException {
		List<Map<String, Object>> attrMapList = crudApiRest.fetchForCrudApi("fetchAttributeListFromTaskType",
				new Object[] { taskType }, authorization);
		Map<String, String> attrMap = attrMapList.stream().collect(
				Collectors.toMap(attr -> attr.get("attr_key").toString(), attr -> attr.get("data_type").toString()));

		logger.debug("attrMap->" + attrMap.toString());
		return attrMap;
	}

	public List<String> getUserListFromWorkAccessByGroupId(String groupId, String authorization)
			throws JsonMappingException, JsonProcessingException {
		logger.info(
				"[" + this.getClass().getSimpleName() + "| getUserListFromWorkAccessByGroupId() | Execution start ");

		ObjectMapper mapper = new ObjectMapper();

		HttpHeaders headers = new HttpHeaders();
		headers.add("authorization", authorization);
		headers.add("Content-Type", "application/json");

		HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(groupId).toString(), headers);

		ResponseEntity<String> response = restTemplate.exchange(
				environment.getProperty("workaccess-api-url") + "/api/v1/groups/users?id={groupId}", HttpMethod.GET,
				entity, String.class);

		JsonNode actualObj = mapper.readTree(response.getBody());
		actualObj = actualObj.get("data").get("userDetails");
		List<String> userIds = new ArrayList<>();
		if (actualObj != null) {
			actualObj.forEach(jsonNode -> {
				userIds.add(jsonNode.get("userId").toString());

			});
		}
		logger.info("[" + this.getClass().getSimpleName() + "| getWorkboxFilterData() | Execution end ");
		return userIds;
	}
}
