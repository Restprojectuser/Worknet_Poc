package com.cherrywork.worknet.parser.util;

import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class CrudApiRest {

	@Autowired
	private Environment environment;

	@Autowired
	RestTemplate restTemplate;

	public List<Map<String, Object>> fetchForCrudApi(String queryName, Object[] args, String authorization)
			throws JsonMappingException, JsonProcessingException, JSONException {

		ObjectMapper mapper = new ObjectMapper();
		JSONObject json = null;
		HttpEntity<String> httpRequest = null;

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Accept", "application/json");
		headers.add("env", environment.getProperty("crud.env"));
		headers.add("Authorization", authorization);

		json = new JSONObject();
		json.put("query", queryName);
		json.put("args", args);

		httpRequest = new HttpEntity<>(json.toString(), headers);
		String jsonString = restTemplate.postForObject(

				environment.getProperty("crud-api-url") + "/fetchQuery?converterName=map", httpRequest, String.class);

		return mapper.readValue(jsonString, new TypeReference<List<Map<String, Object>>>() {
		});
	}

	public void batchUpdateForCrudApi(String queryName, Object[] args, String authorization)
			throws JsonMappingException, JsonProcessingException, JSONException {

		JSONObject json = null;
		HttpEntity<String> httpRequest = null;

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Accept", "application/json");
		headers.add("env", environment.getProperty("crud.env"));
		headers.add("Authorization", authorization);

		json = new JSONObject();
		json.put("query", queryName);
		json.put("args", args);

		httpRequest = new HttpEntity<>(json.toString(), headers);

		String res = restTemplate.postForObject(environment.getProperty("crud-api-url") + "/batchUpdate", httpRequest,
				String.class);

	}

	public void updateCrudApi(String queryName, Object[] args, String authorization)
			throws JsonMappingException, JsonProcessingException, JSONException, InterruptedException {

//		logger.info("[" + this.getClass().getSimpleName() + "| deleteForCrudApi() |" + " Execution start  input ");

		JSONObject json = null;
		HttpEntity<String> httpRequest = null;

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Accept", "application/json");
		headers.add("env", environment.getProperty("crud.env"));
		headers.add("Authorization", authorization);

		json = new JSONObject();
		json.put("query", queryName);
		json.put("args", args);

		httpRequest = new HttpEntity<>(json.toString(), headers);
		String jsonResponse = restTemplate.postForObject(environment.getProperty("crudapi-url") + "/updateQuery",
				httpRequest, String.class);

//		logger.info(
//				"[" + this.getClass().getSimpleName() + "| deleteForCrudApi() |" + " Execution ended " + jsonResponse);

	}
}
