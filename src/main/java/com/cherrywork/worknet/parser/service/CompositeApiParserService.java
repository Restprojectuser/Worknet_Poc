package com.cherrywork.worknet.parser.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.cherrywork.worknet.composite.helpers.ResponseDto;
import com.cherrywork.worknet.parser.dto.CompositeDto;
import com.cherrywork.worknet.parser.dto.SignUrlPayloadDto;
import com.cherrywork.worknet.parser.dto.SignUrlResponseDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

public interface CompositeApiParserService {

	ResponseDto flattenJson(JsonNode jsonNode, String referenceId);

	<T> T readYamlFile(String mappingFilePath, TypeReference<T> typeReference) throws IOException;

	void testBatchQuery(Map<String, List<Map<String, Object>>> map, String tenantId);

	SignUrlResponseDto signUrl(SignUrlPayloadDto signDto);

	void updateEnvelope(String envelopeId, String taskId);

	String getAssertion(Map<String, String> docuSignAccDetails);

	void processResult(CompositeDto dto);

}
