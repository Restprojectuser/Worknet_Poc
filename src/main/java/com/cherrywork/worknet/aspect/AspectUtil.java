package com.cherrywork.worknet.aspect;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.Entity;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.tomcat.util.http.parser.Authorization;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.cherrywork.worknet.parser.helper.ActionDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Logger;

@Component
@Aspect
public class AspectUtil {
	private final Logger logger= (Logger) LoggerFactory.getLogger(this.getClass());
	
	
	@Autowired
	AsyncExecuter asyncExecuter;
	
//	@Pointcut("within(@org.springframework.web.bind.annotation.DetailController *)")
	public void executeScript(ActionDto dto, String authorization) throws JsonMappingException, JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		
		String uri ="https://crudservicesdev.cherryworkproducts.com/crud/api/fetchQuery?converterName=map";
		
		RestTemplate restTemplate= new RestTemplate();


		JSONObject json = null;
		HttpEntity<String> httpRequest = null;

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Accept", "application/json");
		headers.add("env", "itm_dev");
		headers.add("Authorization", authorization);
		
		List<String> args = new ArrayList<String>();
		args.add(dto.getTasks().get(0).getTaskId());
		args.add(dto.getTasks().get(0).getSystemId());
		args.add(dto.getTasks().get(0).getProcessId());
		args.add(dto.getTasks().get(0).getAction());
		
		

		json = new JSONObject();
		json.put("query", "fetchTestTableByIDs");
		json.put("args", args);

		
		 try{
		httpRequest = new HttpEntity<String>(json.toString(), headers);
		String jsonString = restTemplate.postForObject(uri, httpRequest, String.class);
		List<Map<String, Object>> resultList = mapper.readValue(jsonString,
				new TypeReference<List<Map<String, Object>>>() {
				});
		
		System.out.println(resultList.get(0).get("j_scripts").toString());
		

	            ScriptEngine ee = new ScriptEngineManager().getEngineByName("nashorn");
	            ee.eval(resultList.get(0).get("j_scripts").toString());

	        }catch (ScriptException e) {
	        }
		
		
	}

	@After(value = "execution(* com.cherrywork.worknet.parser.controller.TaskActionController.*(..))")
	public void afterAdvice(JoinPoint joinPoint) throws JsonMappingException, JsonProcessingException {
		ActionDto dto = (ActionDto) joinPoint.getArgs()[1];
		String auth = (String) joinPoint.getArgs()[2];
		executeScript(dto, auth);
		asyncExecuter.asyncMethodWithReturnType(dto,auth);
//		executeScript(dto);
//		System.out.print(false);
	}

//	@After(value= "execution(* com.cherrywork.worknet.parser.controller.TaskActionController.*(..))")
//	public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable{
//		if(logger.isInfoEnabled()) {
//			logger.info("Enter: {},{} with argument[s]={}",
//					joinPoint.getSignature().getDeclaringType(),
//					joinPoint.getSignature().getName(),
//					Arrays.toString(joinPoint.getArgs()));
//		}
//		Object result = joinPoint.proceed();
//		if(logger.isInfoEnabled()) {
//			logger.info("Exit: {},{} with result ={}",
//					joinPoint.getSignature().getDeclaringType(),
//					joinPoint.getSignature().getName(),result);
//		}
//		return result;
//	
//		
//	}
}

