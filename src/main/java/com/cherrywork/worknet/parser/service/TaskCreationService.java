package com.cherrywork.worknet.parser.service;

import javax.servlet.http.HttpServletRequest;

import com.cherrywork.worknet.parser.dto.ResponseMessage;
import com.cherrywork.worknet.parser.dto.TaskCreationDto;
import com.cherrywork.worknet.parser.helper.ActionDto;

public interface TaskCreationService {
	ResponseMessage createTask(HttpServletRequest request, TaskCreationDto dto);
}
