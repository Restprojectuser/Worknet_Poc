package com.cherrywork.worknet.aspect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.cherrywork.worknet.parser.helper.ActionDto;
import com.cherrywork.worknet.parser.helper.TaskDto;
import com.cherrywork.worknet.parser.repo.JdbcRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AsyncExecuter {
	@Autowired
	JdbcRepository jdbcRepository;
	@Autowired
	SSEController sSEController;

	private WebClient itmClient, tokenClient, notifyClient;

	@Value("${app.platform}")
	private String appPlatform;

	@Value("${itm-core-url}")
	private String itmurl;

	@Value("${workaccess-api-url}")
	private String waurl;

	@Value("${notification-api-url}")
	private String nyurl;

	@Value("${sap.token.clientid}")
	private String sapTokenClientId;

	@Value("${sap.token.clientsecret}")
	private String sapTokenClientSecret;

	@Value("${sap.token.url}")
	private String sapTokenURL;
	
	@Autowired
	private Environment environment;
	
	@Autowired
	private RestTemplate restTemplate;

	@PostConstruct
	public void postConstruct() {
		itmClient = WebClient.create(itmurl);
		notifyClient = WebClient.create(nyurl);
		if ("WORK_ACCESS".equalsIgnoreCase(appPlatform)) {
			tokenClient = WebClient.create(waurl);
		} else {
			tokenClient = WebClient.create(sapTokenURL);
		}
	}

	@Async
	public CompletableFuture<Void> asyncMethodWithReturnType(ActionDto dto,String authorization) {
		
		log.debug("Execute method asynchronously - " + Thread.currentThread().getName());
		return CompletableFuture.runAsync(() -> {
			List<String> taskIdList = dto.getTasks().parallelStream().map(TaskDto::getTaskId)
					.collect(Collectors.toList());
			List<Map<String,Object>> ownersList = jdbcRepository.getUserIdForTasks(taskIdList);
			
			List<String> groupList = ownersList.stream()
									.filter(owner -> "GROUP".equalsIgnoreCase(owner.get("OWNER_TYPE").toString()))
									.map(owner -> owner.get("OWNER_ID").toString()).collect(Collectors.toList());
			List<String> groupUserIdList = new ArrayList<>();
			if("WORK_ACCESS".equals(environment.getProperty("app.platform"))) {
				for (String groupId : groupList) {
					groupUserIdList.addAll(getUserIdFromWorkAccessByGroupId(
							groupId, authorization));
				}
				
			}else if("SAP".equals(environment.getProperty("app.platform"))){
				 groupUserIdList = getUserIdFromITMByGroupId(
						 groupList, authorization);
	        }
			List<String> userIdList = ownersList.stream()
					.filter(owner -> "USER".equalsIgnoreCase(owner.get("OWNER_TYPE").toString()))
					.map(owner -> owner.get("OWNER_ID").toString()).collect(Collectors.toList());
			userIdList.addAll(groupUserIdList);
			log.info("userIdList for cache {}",userIdList);
			evictCacheInITMforUserList(userIdList);
		});
	}


	@Async
	public CompletableFuture<Void> callNotificationService(ActionDto dto) {
		log.debug("Execute method asynchronously - " + Thread.currentThread().getName());
		return CompletableFuture.runAsync(() -> {
			dto.getTasks().forEach(task -> {
				try {

					notifyClient.post().uri("v1/notification/manageNotification")
							.contentType(MediaType.APPLICATION_JSON).bodyValue(task).retrieve().bodyToMono(String.class)
							.subscribe(System.out::println);
				} catch (Exception e) {
					log.error(e.getMessage());
				}
			});
		});
	}

	@Async
	public CompletableFuture<Void> asyncMethodWithUserNameList(List<String> userIdList) {
		log.debug("Execute method asynchronously - " + Thread.currentThread().getName());
		return CompletableFuture.runAsync(() -> {
			evictCacheInITMforUserList(userIdList);
		});
	}

	public void evictCacheInITMforUser(String userName) {
		itmClient.get().uri("/cacheEvict/getWorkboxFilterDataEvict/{userName}", userName).retrieve()
				.bodyToMono(String.class).subscribe(System.out::println);
	}

	public void evictCacheInITMforUserList(List<String> userIdList) {
		try {
			String token = getAccessToken();

			itmClient.post().uri("/cacheEvict/evictForUserList").header("Authorization", token)
					.contentType(MediaType.APPLICATION_JSON).bodyValue(userIdList).retrieve().bodyToMono(String.class)
					.subscribe(System.out::println);
		} catch (Exception e) {
			log.error(e.getMessage());
		}
	}

	@Async
	public CompletableFuture<Void> sendJobDetailsToMessagingService(String id) {
		log.debug("Execute method asynchronously - " + Thread.currentThread().getName());
		return CompletableFuture.runAsync(() -> {

			try {
				notifyClient.post().uri("v1/job/processJob").contentType(MediaType.APPLICATION_JSON).bodyValue(id)
						.retrieve().bodyToMono(String.class).subscribe(System.out::println);
			} catch (Exception e) {
				log.error(e.getMessage());
			}

		});
	}

	public String getAccessToken() {
		String token = null;

		if ("WORK_ACCESS".equalsIgnoreCase(appPlatform)) {
			token = tokenClient.get().uri("/api/v1/keycloak/userToken").retrieve().bodyToMono(String.class).block();
		} else {
			MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
			formData.add("client_id", sapTokenClientId);
			formData.add("client_secret", sapTokenClientSecret);
			formData.add("grant_type", "client_credentials");
			formData.add("response_type", "token");

			token = tokenClient.post().uri("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(BodyInserters.fromFormData(formData)).retrieve().bodyToMono(JsonNode.class).block()
					.get("access_token").textValue();
			token = "Bearer " + token;

		}
		return token;
	}
	
	public List<String> getUserIdFromWorkAccessByGroupId(String groupId, String authorization){
		List<String> userIds = new ArrayList<>();
		ObjectMapper mapper = new ObjectMapper();

		HttpHeaders headers = new HttpHeaders();
		headers.add("authorization", authorization);
		headers.add("Content-Type", "application/json");
		try {
		HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(groupId).toString(), headers);

//		ResponseEntity<String> response = restTemplate.exchange(
//				environment.getProperty("workaccess-api-url") + "/api/v1/groups/users?id="+groupId, HttpMethod.GET,
//				entity, String.class);
		ResponseEntity<String> response = restTemplate.exchange(
				environment.getProperty("workaccess-api-url") + "/api/v1/groups/users?id="+groupId, HttpMethod.GET,
				entity, String.class);
		JsonNode actualObj;
			actualObj = mapper.readTree(response.getBody());
			
			actualObj = actualObj.get("data").get("userDetails");
			
			if (actualObj != null) {
				actualObj.forEach(jsonNode -> {
					userIds.add(jsonNode.get("userId").toString().replace("\"", ""));

				});
			}
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		
		return userIds;
	}
	
	public List<String> getUserIdFromITMByGroupId(List<String> groupList, String authorization){
		String groupIds = String.join("','", groupList);
		String query = "SELECT ROLE as \"userId\" FROM cw_itm_user_role where ROLE in ('"
                + groupIds + "')";


       List<Map<String, Object>> userIds = jdbcRepository.executeQueryByList(query);
       List<String> users=new ArrayList<>();
       users=userIds.stream().map(user->{return user.get("userId").toString();}).collect(Collectors.toList());
		return users;
	}

}
