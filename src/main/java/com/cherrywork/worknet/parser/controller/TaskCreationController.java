package com.cherrywork.worknet.parser.controller;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.cherrywork.worknet.parser.dto.ResponseMessage;
import com.cherrywork.worknet.parser.dto.TaskCreationDto;
import com.cherrywork.worknet.parser.service.TaskCreationService;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Shashwat Vashimkar(INC01613)
 *
 */
@RestController
@CrossOrigin
@ComponentScan("com.cherrywork")
@RequestMapping(value = "/task", produces = "application/json")
@Slf4j
public class TaskCreationController {

	@Autowired
	private TaskCreationService taskCreationService;

	@RequestMapping(value = "/create", method = RequestMethod.POST, produces = "application/json")
	public ResponseMessage taskActions(HttpServletRequest request, @RequestBody TaskCreationDto dto) {
		log.debug("[WBP-Dev][WORKBOX][WorkboxRest][action][dto]" + dto.toString());
		return taskCreationService.createTask(request, dto);
	}
}
