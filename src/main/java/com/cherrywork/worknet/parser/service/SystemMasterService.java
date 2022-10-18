package com.cherrywork.worknet.parser.service;

import org.springframework.data.domain.PageRequest;

import com.cherrywork.worknet.composite.helpers.ResponseDto;
import com.cherrywork.worknet.parser.dto.SystemMasterDto;

public interface SystemMasterService {

	ResponseDto deleteSystemMaster(String systemId);

	ResponseDto getSystemMaster(String systemId, PageRequest pageable, String searchString);

	ResponseDto updateSystemMaster(String systemId, SystemMasterDto systemSchedularMasterDto);

	ResponseDto createSystemMaster(SystemMasterDto systemSchedularMasterDto);

}
