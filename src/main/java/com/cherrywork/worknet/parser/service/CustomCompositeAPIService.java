package com.cherrywork.worknet.parser.service;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cherrywork.worknet.composite.dto.CompositeInput;
import com.cherrywork.worknet.composite.helpers.ResponseDto;
import com.cherrywork.worknet.composite.service.CompositeApiServiceNew;
import com.cherrywork.worknet.parser.repo.JdbcRepository;
import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.slf4j.Slf4j;

//@Service
@Slf4j
@RestController
public class CustomCompositeAPIService {

	@Autowired
	private CompositeApiServiceNew compositeApiServiceNew;

	@Autowired
	private CompositeApiParserService compositeApiParserService;

	@Autowired
	private JdbcRepository jdbcRepository;

	@GetMapping("/call")
	private ResponseDto getParsedCompositeOutput(String processName, String systemId, String taskType,
			String taskAction) {
		CompositeInput compositeInputFileData = null;
		ResponseDto compositeResponseDto = null;

		try {

			Map<String, Object> postConfigMap = jdbcRepository.getBBCPostActionConfig(processName, systemId, taskType,
					taskAction);

			compositeInputFileData = compositeApiParserService.readYamlFile("custom/customAPIConfig",
					new TypeReference<CompositeInput>() {
					});

			compositeInputFileData.getCompositeRequest().get(1).setCommonRequestBody(null);

			compositeResponseDto = compositeApiServiceNew.getCompositeResponse(null, compositeInputFileData);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return compositeResponseDto;
	}
}
