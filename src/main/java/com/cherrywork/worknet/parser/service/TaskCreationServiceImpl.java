package com.cherrywork.worknet.parser.service;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cherrywork.worknet.composite.config.ApplicationConstant;
import com.cherrywork.worknet.composite.dto.CompositeInput;
import com.cherrywork.worknet.composite.helpers.ResponseDto;
import com.cherrywork.worknet.composite.service.CompositeApiServiceNew;
import com.cherrywork.worknet.parser.dto.ResponseMessage;
import com.cherrywork.worknet.parser.dto.TaskCreationDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TaskCreationServiceImpl implements TaskCreationService {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private CompositeApiParserService compositeApiParserService;

	@Autowired
	private CompositeApiServiceNew compositeApiServiceNew;

	@Autowired
	private TaskActionServiceImpl taskActionServiceImpl;

	@Override
	public ResponseMessage createTask(HttpServletRequest request, TaskCreationDto dto) {
		logger.info("[" + this.getClass().getSimpleName() + "| createTask() |" + " Execution start  input ");
		ResponseMessage responseDto = new ResponseMessage();

		ObjectMapper obj = new ObjectMapper();
		try {
			responseDto.setStatus(ApplicationConstant.SUCCESS);
			responseDto.setStatusCode(ApplicationConstant.CODE_SUCCESS);
			responseDto.setMessage("Tasks created successfully!");

			JsonNode jsonNode = obj.valueToTree(dto.getAttributes());
			dto.setContext(jsonNode.toString());

			Map<String, CompositeInput> compositeTableMap = null;
			try {
				compositeTableMap = compositeApiParserService.readYamlFile("applicationConfig/actionConfig",
						new TypeReference<Map<String, CompositeInput>>() {
						});
			} catch (IOException e) {
				logger.info("[" + this.getClass().getSimpleName() + "| createTask() |"
						+ "Exception Occured message is  " + e.getMessage());
			}

			CompositeInput compositeInputPayload = null;

			if (compositeTableMap.containsKey(dto.getSystemId() + "_" + "CREATE")) {
				compositeInputPayload = compositeTableMap.get(dto.getSystemId() + "_" + "CREATE");
			} else {

				responseDto.setStatus(ApplicationConstant.FAILURE);
				responseDto.setStatusCode(ApplicationConstant.CODE_FAILURE);
				responseDto.setMessage("Tasks creation failed.");

				return responseDto;

			}
			if (compositeInputPayload != null && compositeInputPayload.getCompositeRequest() != null
					&& !compositeInputPayload.getCompositeRequest().isEmpty()) {

				compositeInputPayload.getCompositeRequest().get(compositeInputPayload.getCompositeRequest().size() - 1)
						.setCommonRequestBody(new ObjectMapper().valueToTree(dto));
			}

			responseDto = performeActionOnTask(dto, compositeInputPayload);

		} catch (Exception e) {
			logger.info("[" + this.getClass().getSimpleName() + "| createTask() |" + "Exception Occured message is  "
					+ e.getMessage());
			responseDto.setStatus(ApplicationConstant.FAILURE);
			responseDto.setStatusCode(ApplicationConstant.CODE_FAILURE);
			responseDto.setMessage(e.getMessage());

		}
		logger.info("[" + this.getClass().getSimpleName() + "| createTask() |"
				+ " Execution end  Output  is ResponseDto - " + responseDto);
		return responseDto;

	}

	private ResponseMessage performeActionOnTask(TaskCreationDto dto, CompositeInput compositeInputPayload) {
		ResponseMessage responseMessage = new ResponseMessage();

		try {
			if (dto != null && dto.getAttributes() != null && compositeInputPayload != null) {

				CompositeInput compositeInputFileData = (CompositeInput) compositeApiParserService.readYamlFile(
						"composite/" + compositeInputPayload.getCompositeApiName(),
						new TypeReference<CompositeInput>() {
						});

				ResponseDto compositeResponseDto = compositeApiServiceNew.getCompositeResponse(compositeInputPayload,
						compositeInputFileData);

				responseMessage = taskActionServiceImpl.checkCompositeResponse(compositeInputFileData,
						compositeResponseDto);

				if (ApplicationConstant.STATUS_SUCCESS.equalsIgnoreCase(responseMessage.getStatus())) {

					responseMessage.setMessage("Tasks created successfully!");
					responseMessage.setStatus(ApplicationConstant.SUCCESS);
					responseMessage.setStatusCode(ApplicationConstant.SUCCESS);
				} else {
					responseMessage.setStatus(ApplicationConstant.FAILURE);
					responseMessage.setStatusCode(ApplicationConstant.FAILURE);
					responseMessage.setMessage("Tasks creation failed." + responseMessage.getMessage());
				}

			}
		} catch (Exception e) {
			responseMessage.setMessage(e.getMessage());
			responseMessage.setStatus(ApplicationConstant.FAILURE);
			responseMessage.setStatusCode(ApplicationConstant.CODE_FAILURE);
		}

		return responseMessage;
	}

}
