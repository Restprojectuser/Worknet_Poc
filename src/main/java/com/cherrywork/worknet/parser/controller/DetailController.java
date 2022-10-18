package com.cherrywork.worknet.parser.controller;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.cherrywork.securityAdapter.util.TokenUtil;
import com.cherrywork.worknet.parser.dto.ResponseMessage;
import com.cherrywork.worknet.parser.helper.ActionDto;
import com.cherrywork.worknet.parser.service.TaskActionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import lombok.extern.slf4j.Slf4j;


	@RestController
	@CrossOrigin
	@ComponentScan("com.cherrywork")
	@RequestMapping(value = "/updateScripts", produces = "application/json")
	@Slf4j
	public class DetailController {
		
		@Autowired
		private TaskActionService taskActionService;

		@Autowired
		private TokenUtil tokenUtil;

		@RequestMapping(value = "/updateScripts", method = RequestMethod.POST, produces = "application/json")
		public ResponseMessage taskActions(HttpServletRequest request, @RequestBody ActionDto dto,
				@RequestHeader(required = false) String authorization)
				throws JsonMappingException, JsonProcessingException {
			Jwt jwt = tokenUtil.getFinalJwtToken(authorization);
			String userId = jwt.getClaims().get("user_name").toString();
			String auth = "Bearer " + jwt.getTokenValue().toString();
			log.debug("[WBP-Dev][WORKBOX][WorkboxRest][action][dto]" + dto.toString());
			return taskActionService.taskAction(request, dto, auth, userId);
		}


}
