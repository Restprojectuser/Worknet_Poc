package com.cherrywork.worknet.parser.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cherrywork.worknet.composite.helpers.ResponseDto;
import com.cherrywork.worknet.parser.dto.SystemSchedularConfigDto;
import com.cherrywork.worknet.parser.service.SystemSchedularConfigService;

/**
 * @author Shruti.Ghanshyam
 *
 */
@RestController
@CrossOrigin
@ComponentScan("com.cherrywork")
@RequestMapping(value = "/system-schedule", produces = "application/json")
public class SystemSchedularMasterController {

	@Autowired
	private SystemSchedularConfigService schedularMasterService;

	@PostMapping("/config")
	public ResponseDto saveOrUpdateSystemSchedulars(
			@RequestBody List<SystemSchedularConfigDto> systemSchedularConfigDtos) {

		return schedularMasterService.saveOrUpdateSystemSchedular(systemSchedularConfigDtos);

	}

}
