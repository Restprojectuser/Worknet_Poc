package com.cherrywork.worknet.parser.service;

import javax.servlet.http.HttpServletRequest;

import com.cherrywork.worknet.parser.dto.ResponseMessage;
import com.cherrywork.worknet.parser.helper.ActionDto;

public interface TaskActionService {

	ResponseMessage taskAction(HttpServletRequest request, ActionDto dto, String authorization, String userId);

}
