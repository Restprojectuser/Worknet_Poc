package com.cherrywork.worknet.parser.service;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.cherrywork.worknet.aspect.AsyncExecuter;
import com.cherrywork.worknet.composite.config.ApplicationConstant;
import com.cherrywork.worknet.composite.dto.CompositeInput;
import com.cherrywork.worknet.composite.dto.CompositeOutput;
import com.cherrywork.worknet.composite.dto.CompositeResponse;
import com.cherrywork.worknet.composite.helpers.ResponseDto;
import com.cherrywork.worknet.composite.service.CompositeApiServiceNew;
import com.cherrywork.worknet.composite.utils.ServicesUtil;
import com.cherrywork.worknet.parser.dto.CompositeDto;
import com.cherrywork.worknet.parser.dto.SignUrlPayloadDto;
import com.cherrywork.worknet.parser.dto.SignUrlResponseDto;
import com.cherrywork.worknet.parser.entity.JobLogDo;
import com.cherrywork.worknet.parser.repo.JdbcBatchRepository;
import com.cherrywork.worknet.parser.repo.JdbcRepository;
import com.cherrywork.worknet.parser.repo.JobLogRepo;
import com.cherrywork.worknet.parser.util.CrudApiRest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CompositeApiParserServiceImpl implements CompositeApiParserService {

	@Autowired
	private CompositeApiServiceNew compositeApiServiceNew;

	@Autowired
	private JdbcRepository jdbcRepository;

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private JdbcBatchRepository jdbcBatchRepository;

	@Autowired
	private Environment environment;

	@Autowired
	private AsyncExecuter asyncExecuter;

	@Autowired
	private CrudApiRest crudApiRest;

	@Autowired
	private JobLogRepo jobLogRepo;

	@Value("${app.database}")
	private String dbName;

	@Override
	@Transactional
	public void processResult(CompositeDto dto) {
		Long start = System.currentTimeMillis();
		ObjectMapper mapper = new ObjectMapper();

		JobLogDo jobLogDto = jobLogRepo.getById(dto.getJobLogId());

		try {

			log.info("Number of active threads in SchedulerServiceImpl " + Thread.activeCount());
			log.info("Total Number of threads in SchedulerServiceImpl "
					+ ManagementFactory.getThreadMXBean().getThreadCount());
			CompositeInput compositeInputPayload = dto.getCompositeInput();
			CompositeOutput compositeOutput = dto.getCompositeOutput();

			if (!compositeOutput.getCompositeResponse().isEmpty()) {

				Map<Integer, Map<String, Object>> refJsonMap = new HashMap<>();

				Map<Integer, Map<String, Object>> oldRefJsonMap = new HashMap<>();
				Map<Integer, Map<String, Object>> modifiedMap = new HashMap<>();
				boolean isCustomAttributePresent = false;

				CompletableFuture<Map<String, Map<String, String>>> tablesFuture = CompletableFuture
						.supplyAsync(() -> getTables(compositeInputPayload));
				CompletableFuture<Map<String, Map<String, String>>> customAttributesMapFuture = CompletableFuture
						.supplyAsync(this::getcustomAttributesMap);

				Map<String, Map<String, String>> tables = tablesFuture.get();
				CompletableFuture<String[]> reqKeysFuture = CompletableFuture.supplyAsync(() -> getReqKeys(tables));
				Map<String, Map<String, String>> customAttributesMap = customAttributesMapFuture.get();
				String reqKeys[] = reqKeysFuture.get();

				for (CompositeResponse compositeResponse : compositeOutput.getCompositeResponse()) {
					boolean isArray = false;
					String jsonString = compositeResponse.getBody().toString();
					if (jsonString.startsWith("[")) {
						jsonString = "{" + "\"dummy\":" + jsonString + "}";
						isArray = true;
					}

					ObjectNode node = (ObjectNode) new ObjectMapper().readTree(jsonString);
					Object obj = new ObjectMapper().readTree(node.toString());
					TypeReference<Map<String, Object>> type = new TypeReference<Map<String, Object>>() {
					};
					Map<String, Object> leftMap = mapper.readValue(obj.toString(), type);

					Map<String, Object> leftFlatMap = ServicesUtil.flatten(leftMap, compositeResponse.getReferenceId());
					if (isArray) {
						Map<String, Object> tempMap = new HashMap<>();
						leftFlatMap.entrySet().stream().forEach(entry -> {
							tempMap.put(entry.getKey().replace("/dummy", ""), entry.getValue());
						});
						leftFlatMap = tempMap;
					}

					Map<String, Object> rightFlatMap = ServicesUtil.filterMap(leftFlatMap, reqKeys);

					refJsonMap = ServicesUtil.createHashMap(rightFlatMap);

					if ("SuccessFactors".equals(compositeInputPayload.getOrigin())) {
						isCustomAttributePresent = true;
						AtomicInteger atomicInteger = new AtomicInteger();
						refJsonMap.entrySet().forEach(itemEntry -> {

							if (itemEntry.getValue()
									.containsKey("success_factor./d/results/" + atomicInteger.get()
											+ "/wfRequestNav/wfRequestUINav/changedData")
									&& itemEntry.getValue().get("success_factor./d/results/" + atomicInteger.get()
											+ "/wfRequestNav/wfRequestUINav/changedData") != null
									&& !"".equals(
											itemEntry.getValue().get("success_factor./d/results/" + atomicInteger.get()
													+ "/wfRequestNav/wfRequestUINav/changedData").toString())) {
								String value = itemEntry.getValue().get("success_factor./d/results/"
										+ atomicInteger.getAndIncrement() + "/wfRequestNav/wfRequestUINav/changedData")
										.toString();
								try {
									JSONArray jsonArr = new JSONArray(value);

									for (int i = 0; i < jsonArr.length(); i++) {
										JSONObject jsonObj = jsonArr.getJSONObject(i);
										itemEntry.getValue().put(jsonObj.getString("label").replace(" ", ""),
												jsonObj.getString("newValue"));
									}
								} catch (JSONException e) {
									e.printStackTrace();
								}
							}
						});
					}

					if (customAttributesMap.containsKey(compositeInputPayload.getKeyConfigName())) {
						isCustomAttributePresent = true;
						refJsonMap = ServicesUtil.handleCustomAttribute(refJsonMap,
								customAttributesMap.get(compositeInputPayload.getKeyConfigName()));
					}

					if (!oldRefJsonMap.isEmpty() && !refJsonMap.isEmpty()) {
						if ("SCP".equalsIgnoreCase(compositeInputPayload.getOrigin())
								|| "SCP_BTP".equalsIgnoreCase(compositeInputPayload.getOrigin())) {
							ServicesUtil.mergeCustomMaps(refJsonMap, oldRefJsonMap);
						} else if ("DocuSign".equalsIgnoreCase(compositeInputPayload.getOrigin())) {
							ServicesUtil.mergeDocuSignMaps(refJsonMap, oldRefJsonMap);
						} else if ("Ariba".equalsIgnoreCase(compositeInputPayload.getOrigin())) {
							ServicesUtil.mergeAribaMaps(refJsonMap, oldRefJsonMap);
						} else {
							ServicesUtil.mergeMaps(refJsonMap, oldRefJsonMap);
						}
					}
					if ("Ariba".equalsIgnoreCase(compositeInputPayload.getOrigin())) {
						Map<Integer, List<Map<String, Object>>> newMapping = ServicesUtil.cleanMap(refJsonMap);
						modifiedMap = ServicesUtil.modifyNewMapping(newMapping);
						refJsonMap = modifiedMap;
						log.info("refJsonMap: " + new JSONObject(refJsonMap).toString());
					}
					refJsonMap = ServicesUtil.cleanFlatMap(refJsonMap);
					oldRefJsonMap = refJsonMap;
				}
				Map<String, List<Map<String, Object>>> map = jdbcRepository.convertJsonToTableData(refJsonMap, tables,
						isCustomAttributePresent, compositeInputPayload.getOrigin());

				CompletableFuture<Map<String, List<Map<String, Object>>>> futureMap = CompletableFuture
						.supplyAsync(() -> prepareUpsertStatements(map))
						.thenApply(m -> deltaChanges(m, compositeInputPayload.getOrigin()));

				futureMap.get();

				jobLogDto.setEndTime(new Date());
				jobLogDto.setJobState("Completed");
				jobLogDto.setMessage("Task Insert Completed");

			} else {
				jobLogDto.setEndTime(new Date());
				jobLogDto.setJobState("Completed");
				jobLogDto.setMessage("No Change from previos Job");

			}
			log.info("[" + this.getClass().getSimpleName() + "| getResult() | Completed" + LocalDateTime.now());

		} catch (Exception e) {
			e.printStackTrace();
			log.info("[" + this.getClass().getSimpleName() + "| getResult() |" + "Exception Occured message is  "
					+ e.getMessage() + " Line no: "
					+ (e.getStackTrace() != null && e.getStackTrace()[0] != null ? e.getStackTrace()[0].getLineNumber()
							: null)
					+ " or Line : " + (e.getCause() != null ? e.getCause().getStackTrace() : "no cause"));
			jobLogDto.setEndTime(new Date());
			jobLogDto.setJobState("Failed");
			jobLogDto.setMessage(e.getMessage());
		}
		Long end = System.currentTimeMillis();
		jobLogDto.setTimeTaken(end - start);
		jobLogRepo.save(jobLogDto);
	}

	private Map<String, List<Map<String, Object>>> prepareUpsertStatements(Map<String, List<Map<String, Object>>> map) {
		jdbcBatchRepository.prepareUpsertStatementBatch(map);
		return map;
	}

	private String[] getReqKeys(Map<String, Map<String, String>> tables) {
		Set<String> filterKeys = new HashSet<>();

		for (Map.Entry<String, Map<String, String>> entry : tables.entrySet()) {
			for (Map.Entry<String, String> entry2 : entry.getValue().entrySet()) {
				if (entry2.getValue() != null) {
					filterKeys.add(entry2.getValue());
				}
			}
		}
		int size = filterKeys.size();
		if (filterKeys.contains("@(Ariba)")) {
			size += 1;
			filterKeys.add("get_process_task_details./approvalRequests/approvers/group/uniqueName");
		}
		String reqKeys[] = new String[size];
		return filterKeys.toArray(reqKeys);
	}

	private Map<String, Map<String, String>> getTables(CompositeInput compositeInputPayload) {
		try {
			return readYamlFile("dbStructureConfig/" + compositeInputPayload.getDbStructureFileName(),
					new TypeReference<Map<String, Map<String, String>>>() {
					});
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private Map<String, Map<String, String>> getcustomAttributesMap() {
		try {
			return readYamlFile("applicationConfig/" + ApplicationConstant.CUSTOM_ATTRIBUTE_CONFIG,
					new TypeReference<Map<String, Map<String, String>>>() {
					});
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public String getAssertion(Map<String, String> docuSignAccDetails) {

		String assertionToken = "";
		try {
			String strPk = docuSignAccDetails.get("privateKey");

			byte[] bytes = Base64.getDecoder().decode(strPk);
			Security.addProvider(new BouncyCastleProvider());
			KeyFactory kf = KeyFactory.getInstance("RSA", "BC");
			EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(bytes);
			RSAPrivateKey privateKey = (RSAPrivateKey) kf.generatePrivate(keySpec);

			strPk = docuSignAccDetails.get("publicKey");

			bytes = Base64.getDecoder().decode(strPk);

			kf = KeyFactory.getInstance("RSA");
			keySpec = new X509EncodedKeySpec(bytes);
			RSAPublicKey publicKey = (RSAPublicKey) kf.generatePublic(keySpec);

			Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
			long now = System.currentTimeMillis();
			assertionToken = JWT.create().withIssuer(docuSignAccDetails.get("integrationKey"))
					.withSubject(docuSignAccDetails.get("userIdAdmin"))
					.withAudience(docuSignAccDetails.get("oAuthBasePath")).withNotBefore(new Date(now))
					.withExpiresAt(new Date(now + 3600 + 1000)).withClaim("scope", "signature").sign(algorithm);

		} catch (Exception e) {
			log.debug("AccessToken.getJwtAccessToken() error" + e.getMessage());
		}
		return assertionToken;
	}

	private Map<String, List<Map<String, Object>>> deltaChanges(Map<String, List<Map<String, Object>>> map,
			String origin) {

		if (map.containsKey("CW_ITM_WN_TASKS") && map.containsKey("CW_ITM_WN_PROCESS")) {

			// collect taskIds from origin
			List<String> taskIds = map.get("CW_ITM_WN_TASKS").stream()
					.filter(obj -> obj.get("TASK_ID") != null && obj.get("STATUS") != null)
					.map(tasks -> tasks.get("TASK_ID").toString()).collect(Collectors.toList());

			// collect process name from origin
			Set<String> processNames = map.get("CW_ITM_WN_PROCESS").stream()
					.map(tasks -> tasks.get("PROCESS_NAME").toString()).collect(Collectors.toSet());

			// fetch completed tasks from task table
			List<String> completedTaskIds = jdbcRepository.getCompletedTasks(origin, processNames, taskIds);

			List<Map<String, Object>> newTaskList = map.get("CW_ITM_WN_TASKS").stream()
					.filter(obj -> obj.get("TASK_ID") != null && obj.get("STATUS") != null)
					.filter(obj -> !completedTaskIds.contains(obj.get("TASK_ID").toString())).map(obj -> obj)
					.collect(Collectors.toList());

			map.put("CW_ITM_WN_TASKS", newTaskList);
			// get ready reserved tasks
			List<String> tasks = jdbcRepository.getReadyReservedTasks(origin, processNames, taskIds);

			// change status to completed
			jdbcRepository.changeStatusOfTasks(tasks);

			// Fetch tasks which are already completed from source system
//			List<Map<String, Object>> archivedTasks = jdbcRepository.getArchivedTasks(origin, processNames, taskIds);

//			if (archivedTasks != null && !archivedTasks.isEmpty()) {
//
//				List<String> archivedTaskIds = archivedTasks.stream().map(tasks -> {
//					return tasks.get("TASK_ID").toString();
//				}).collect(Collectors.toList());
//
//				// delete the archived task
//				jdbcRepository.deleteArchivedTasks(origin, processNames, archivedTaskIds);
//
//				// save archived tasks in archived table
//				map.put("CW_ITM_WN_ARCHIVED_TASKS", archivedTasks);
//			}
		}

		if (map.containsKey("CW_ITM_WN_TASK_OWNERS")) {
			List<String> ownerIdList = new ArrayList<>();
			map.get("CW_ITM_WN_TASK_OWNERS").forEach(obj -> {
				if (obj.get("OWNER_ID") != null) {
					ownerIdList.add(obj.get("OWNER_ID").toString());
				}
			});
			asyncExecuter.asyncMethodWithUserNameList(ownerIdList);
		}
		return map;
	}

	@Override
	public <T> T readYamlFile(String mappingFilePath, TypeReference<T> typeReference) throws IOException {
		ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
		String fooResourceUrl = environment.getProperty("spring.config.import").replace("optional:configserver:", "")
				+ "/application/default/main/" + environment.getProperty("spring.application.name") + "/"
				+ mappingFilePath + ".yaml";
		ResponseEntity<String> response = restTemplate.exchange(fooResourceUrl, HttpMethod.GET, null, String.class);
		return objectMapper.readValue(response.getBody(), typeReference);
	}

	@Override
	public ResponseDto flattenJson(JsonNode jsonNode, String referenceId) {
		ResponseDto responseDto = new ResponseDto();
		ObjectMapper mapper = new ObjectMapper();
		try {
			responseDto.setStatus(true);
			responseDto.setStatusCode(200);

			ObjectNode node = (ObjectNode) new ObjectMapper().readTree(jsonNode.toString());
			Object obj = new ObjectMapper().readTree(node.toString());
			TypeReference<Map<String, Object>> type = new TypeReference<Map<String, Object>>() {
			};
			Map<String, Object> leftMap = mapper.readValue(obj.toString(), type);

			Map<String, Object> leftFlatMap = ServicesUtil.flatten(leftMap, referenceId);
			responseDto.setData(leftFlatMap);
		} catch (Exception e) {
			log.debug(e.getMessage());
		}
		return responseDto;
	}

	public CompositeOutput readJsonFile(String fileName) throws IOException {
		ObjectMapper mapper = new ObjectMapper();

		File resource = new ClassPathResource(fileName).getFile();
		String payLoad = new String(Files.readAllBytes(resource.toPath()));
		return mapper.readValue(payLoad, CompositeOutput.class);
	}

	@Override
	public void testBatchQuery(Map<String, List<Map<String, Object>>> map, String tenantId) {
		try {

			jdbcBatchRepository.prepareUpsertStatementBatch(map);

		} catch (Exception e) {
			log.info("[" + this.getClass().getSimpleName() + "| getResult() |" + "Exception Occured message is  "
					+ e.getMessage() + " Line no: "
					+ (e.getStackTrace() != null && e.getStackTrace()[0] != null ? e.getStackTrace()[0].getLineNumber()
							: null)
					+ " or Line : " + e.getCause().getStackTrace());
		}

	}

	@Override
	public SignUrlResponseDto signUrl(SignUrlPayloadDto signDto) {
		SignUrlResponseDto responseDto = new SignUrlResponseDto();
		ObjectMapper mapper = new ObjectMapper();

		try {

			Map<String, CompositeInput> compositeTableMap = readYamlFile("applicationConfig/actionConfig",
					new TypeReference<Map<String, CompositeInput>>() {
					});

			CompositeInput fromAction = null;
			if (compositeTableMap.containsKey(signDto.getSystemId() + "_SIGN")) {
				fromAction = compositeTableMap.get(signDto.getSystemId() + "_SIGN");
			}

			try {

				Object[] args = new Object[1];
				args[0] = signDto.getTaskId();
				List<Map<String, Object>> resultList = crudApiRest.fetchForCrudApi("fetchAttrValueOfDocuSign", args,
						asyncExecuter.getAccessToken());

				if (resultList != null && !resultList.isEmpty()) {
					signDto.setUserId((String) resultList.get(0).get("attr_value"));
					signDto.setSignUrl("https://wnservices.cherryworkproducts.com/worknet/composite-api"
							+ "/parser/updateEnvelope?envelopeId=" + signDto.getEnvelopeId() + "&taskId="
							+ signDto.getTaskId());
				}

			} catch (Exception e) {
				log.error("Error fetching User Id For envelope: {}", e.getMessage());
			}

			fromAction.getCompositeRequest().get(fromAction.getCompositeRequest().size() - 1)
					.setCommonRequestBody(new ObjectMapper().valueToTree(signDto));

			CompositeInput wholeCompositeYaml = readYamlFile("composite/DocuSignApi",
					new TypeReference<CompositeInput>() {
					});

			Map<String, String> docuSignAccDetails = readYamlFile("DocusignConfig",
					new TypeReference<Map<String, String>>() {
					});

			String assertionToken = getAssertion(docuSignAccDetails);
			JsonNode assertionNode = mapper.readTree("{\"jwtAssertionToken\":\"" + assertionToken + "\"}");

			wholeCompositeYaml.getCompositeRequest().stream().forEach(request -> {
				if ("getAccessToken".equalsIgnoreCase(request.getReferenceId())) {
					request.setCommonRequestBody(assertionNode);
				}

			});

			ResponseDto compositeResponseDto = compositeApiServiceNew.getCompositeResponse(fromAction,
					wholeCompositeYaml);

			CompositeOutput compositeOutput = (CompositeOutput) compositeResponseDto.getData();

			JsonNode urlOutput = compositeOutput.getCompositeResponse().stream()
					.filter(request -> "getSigningUrl".equalsIgnoreCase(request.getReferenceId())).findFirst()
					.orElse(null).getBody().get("url");

			responseDto.setSigningUrl(urlOutput.asText());
			responseDto.setEnvelopeId(signDto.getEnvelopeId());

		} catch (IOException e) {
			log.error("Error in Preparing Signing URL :{}", e.getMessage());
			responseDto.setEnvelopeId(null);
			responseDto.setSigningUrl(null);
		}

		return responseDto;
	}

	@Override
	public void updateEnvelope(String envelopeId, String taskId) {
		ObjectMapper mapper = new ObjectMapper();
		SignUrlPayloadDto signDto = new SignUrlPayloadDto();
		try {
			Map<String, String> docuSignAccDetails = readYamlFile("DocusignConfig",
					new TypeReference<Map<String, String>>() {
					});

			String assertionToken = getAssertion(docuSignAccDetails);

			Map<String, CompositeInput> compositeTableMap = readYamlFile("applicationConfig/actionConfig",
					new TypeReference<Map<String, CompositeInput>>() {
					});

			CompositeInput fromAction = null;
			if (compositeTableMap.containsKey("DocuSign_UPDATE")) {
				fromAction = compositeTableMap.get("DocuSign_UPDATE");
			}

			signDto.setEnvelopeId(envelopeId);
			signDto.setTaskId(taskId);

			fromAction.getCompositeRequest().get(fromAction.getCompositeRequest().size() - 1)
					.setCommonRequestBody(new ObjectMapper().valueToTree(signDto));

			CompositeInput wholeCompositeYaml = readYamlFile("composite/DocuSignApi",
					new TypeReference<CompositeInput>() {
					});

			JsonNode assertionNode = mapper.readTree("{\"jwtAssertionToken\":\"" + assertionToken + "\"}");

			wholeCompositeYaml.getCompositeRequest().stream().forEach(request -> {
				if ("getAccessToken".equalsIgnoreCase(request.getReferenceId())) {
					request.setCommonRequestBody(assertionNode);
				}

			});

			ResponseDto compositeResponseDto = compositeApiServiceNew.getCompositeResponse(fromAction,
					wholeCompositeYaml);

			CompositeOutput compositeOutput = (CompositeOutput) compositeResponseDto.getData();

			JsonNode urlOutput = compositeOutput.getCompositeResponse().stream()
					.filter(request -> "fetchStatusOfSigner".equalsIgnoreCase(request.getReferenceId())).findFirst()
					.orElse(null).getBody().get("signers");

			log.info("{}", urlOutput);

			if (urlOutput.isArray()) {
				ArrayNode arrayNode = (ArrayNode) urlOutput;

				arrayNode.forEach(task -> {
					if (taskId.equalsIgnoreCase(task.get("recipientIdGuid").asText())
							&& "completed".equalsIgnoreCase(task.get("status").asText())) {

						try {
							crudApiRest.batchUpdateForCrudApi(
									"updateTaskStatus", new Object[] { new Object[] { "COMPLETED",
											task.get("email").asText(), task.get("email").asText(), taskId } },
									asyncExecuter.getAccessToken());
						} catch (Exception e) {
							log.error("Error Updating Task {}", e.getMessage());
						}
					}
				});
			}

		} catch (Exception e) {
			log.error("Error updating Envelope {}", e.getMessage());
		}
	}
}
