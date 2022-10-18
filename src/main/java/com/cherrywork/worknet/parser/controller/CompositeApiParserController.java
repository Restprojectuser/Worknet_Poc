package com.cherrywork.worknet.parser.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cherrywork.worknet.composite.helpers.ResponseDto;
import com.cherrywork.worknet.parser.dto.SignUrlPayloadDto;
import com.cherrywork.worknet.parser.dto.SignUrlResponseDto;
import com.cherrywork.worknet.parser.dto.SystemSchedularConfigDto;
import com.cherrywork.worknet.parser.repo.JdbcRepository;
import com.cherrywork.worknet.parser.scheduler.SchedulerServiceImpl;
import com.cherrywork.worknet.parser.service.CompositeApiParserService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author Shruti.Ghanshyam
 *
 */
@RestController
@CrossOrigin
@ComponentScan("com.cherrywork")
@RequestMapping(value = "/worknet/composite-api/parser", produces = "application/json")
public class CompositeApiParserController {

	@Autowired
	private CompositeApiParserService compositeApiParserService;

	@Autowired
	private JdbcRepository jdbcRepository;

	@Autowired
	private SchedulerServiceImpl schedulerServiceImpl;

	@RequestMapping(value = "/runJob", method = RequestMethod.POST)
	public ResponseEntity<String> runJob(@RequestBody SystemSchedularConfigDto dto) {
		try {
			schedulerServiceImpl.runJob(dto);
		} catch (Exception e) {
			return ResponseEntity.ok().body(e.getMessage());
		}
		return ResponseEntity.ok().body("Running Job");
	}

	@RequestMapping(value = "/execute-sql", method = RequestMethod.POST)
	public ResponseEntity<Object> parseSuccessFator(@RequestBody String sql,
			@RequestParam(defaultValue = "false") Boolean isSelect) {
		if (isSelect) {
			return ResponseEntity.ok().body(jdbcRepository.executeQueryByList(sql));
		}
		return ResponseEntity.ok().body(jdbcRepository.executeQuery(sql));
	}

	@RequestMapping(value = "/flatten-json", method = RequestMethod.POST)
	public ResponseEntity<ResponseDto> parseSuccessFator(@RequestBody JsonNode jsonNode,
			@RequestParam String referenceId) {
		return ResponseEntity.ok().body(compositeApiParserService.flattenJson(jsonNode, referenceId));
	}

	@RequestMapping(value = "/signUrl", method = RequestMethod.POST)
	public ResponseEntity<SignUrlResponseDto> signUrl(@RequestBody SignUrlPayloadDto signDto) {
		return ResponseEntity.ok().body(compositeApiParserService.signUrl(signDto));
	}

	@RequestMapping(value = "/updateEnvelope", method = RequestMethod.GET)
	public void updateEnvelope(@RequestParam String envelopeId, @RequestParam String taskId) {
		compositeApiParserService.updateEnvelope(envelopeId, taskId);
	}

}