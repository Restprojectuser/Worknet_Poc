package com.cherrywork.worknet.parser.service;

import java.util.List;

import com.cherrywork.worknet.composite.helpers.ResponseDto;
import com.cherrywork.worknet.parser.dto.SystemSchedularConfigDto;

/**
 * @author Shruti.Ghanshyam
 *
 */
public interface SystemSchedularConfigService {

	ResponseDto saveOrUpdateSystemSchedular(List<SystemSchedularConfigDto> systemSchedularConfigDtos);

}
