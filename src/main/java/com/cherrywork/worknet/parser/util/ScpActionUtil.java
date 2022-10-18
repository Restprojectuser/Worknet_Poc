package com.cherrywork.worknet.parser.util;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.cherrywork.worknet.composite.config.ApplicationConstant;
import com.cherrywork.worknet.composite.dto.CompositeInput;
import com.cherrywork.worknet.composite.dto.CompositeOutput;
import com.cherrywork.worknet.composite.helpers.ResponseDto;
import com.cherrywork.worknet.composite.service.CompositeApiServiceNew;
import com.cherrywork.worknet.parser.dto.ResponseMessage;
import com.cherrywork.worknet.parser.helper.TaskDto;
import com.cherrywork.worknet.parser.service.CompositeApiParserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ScpActionUtil {

	@Autowired
	private CompositeApiParserService compositeApiParserService;

	@Autowired
	private CompositeApiServiceNew compositeApiServiceNew;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	public ResponseMessage actionOnTask(TaskDto taskDto, String authorization) {

		Map<String, CompositeInput> compositeTableMap = null;
		try {
			compositeTableMap = compositeApiParserService.readYamlFile("applicationConfig/actionConfig",
					new TypeReference<Map<String, CompositeInput>>() {
			});
		} catch (IOException e) {
			log.error("[" + this.getClass().getSimpleName() + "| claimTask() |" + "Exception Occured message is  "
					+ e.getMessage());
		}

		CompositeInput compositeInputPayload = null;
		if (compositeTableMap
				.containsKey(taskDto.getSystemId() + "_" + taskDto.getProcessName() + "_" + taskDto.getAction())) {
			compositeInputPayload = compositeTableMap
					.get(taskDto.getSystemId() + "_" + taskDto.getProcessName() + "_" + taskDto.getAction());
		} else if (compositeTableMap.containsKey(taskDto.getSystemId() + "_" + taskDto.getAction())) {
			compositeInputPayload = compositeTableMap.get(taskDto.getSystemId() + "_" + taskDto.getAction());
		}

		if (compositeInputPayload != null && compositeInputPayload.getCompositeRequest() != null
				&& !compositeInputPayload.getCompositeRequest().isEmpty()) {
			authorization = authorization.replace("Bearer ", "");
			taskDto.setUserAccessToken(authorization);
			if (taskDto.getForwardOwners() != null && !taskDto.getForwardOwners().isEmpty()) {
				List<String> ownerIds = jdbcTemplate.queryForList(
						"SELECT OWNER_ID FROM CW_ITM_WN_TASK_OWNERS WHERE TASK_ID = '" + taskDto.getTaskId() + "'",
						String.class);

				taskDto.getForwardOwners().stream().forEach(owner -> {
					if ("USER".equalsIgnoreCase(owner.getOwnerType()))
						ownerIds.add(owner.getOwnerId());
					else {

					}
				});
				taskDto.setOwnerIds(String.join(",", new HashSet<>(ownerIds)));

			}
			ObjectMapper taskDtoMapper = new ObjectMapper();
			Map<String, Object> taskDtoMap = taskDtoMapper.convertValue(taskDto, Map.class);
			if(compositeInputPayload.getCompositeRequest().size()>=2) {
				compositeInputPayload.getCompositeRequest().get(compositeInputPayload.getCompositeRequest().size() - 2)
				.setCommonRequestBody(new ObjectMapper().valueToTree(taskDtoMap));
			}
			compositeInputPayload.getCompositeRequest().get(compositeInputPayload.getCompositeRequest().size() - 1)
			.setCommonRequestBody(new ObjectMapper().valueToTree(taskDtoMap));
		} else {
			ResponseMessage responseMessage = new ResponseMessage();
			responseMessage.setMessage("SCP Workflow Engine Destination Configuration is missing");
			responseMessage.setStatus(ApplicationConstant.SUCCESS);
			responseMessage.setStatusCode(ApplicationConstant.SUCCESS);
			return responseMessage;
		}

		return performeActionOnTask(taskDto, compositeInputPayload, authorization);
	}

	private ResponseMessage performeActionOnTask(TaskDto taskDto, CompositeInput compositeInputPayload,
			String authorization) {
		ResponseMessage responseMessage = new ResponseMessage();

		try {
			if (taskDto.getAction() != null) {
				if (compositeInputPayload != null) {

					CompositeInput compositeInputFileData = (CompositeInput) compositeApiParserService.readYamlFile(
							"composite/" + compositeInputPayload.getCompositeApiName(),
							new TypeReference<CompositeInput>() {
							});

					ResponseDto compositeResponseDto = compositeApiServiceNew
							.getCompositeResponse(compositeInputPayload, compositeInputFileData);

					log.debug("responseMessage" + compositeResponseDto);
					responseMessage = checkCompositeResponse(compositeInputFileData, compositeResponseDto);

					String id = taskDto.getReferenceId() != null ? taskDto.getReferenceId() : taskDto.getTaskId();
					if (ApplicationConstant.STATUS_SUCCESS.equalsIgnoreCase(responseMessage.getStatus())) {

						responseMessage.setStatus(ApplicationConstant.SUCCESS);
						responseMessage.setStatusCode(ApplicationConstant.SUCCESS);
						responseMessage.setMessage("Task(" + id + ") action was successfull.");
					} else {
						responseMessage.setStatus(ApplicationConstant.FAILURE);
						responseMessage.setStatusCode(ApplicationConstant.FAILURE);
						responseMessage.setMessage(
								"Task(" + id + ") action was unsuccessfull due to " + responseMessage.getMessage());
					}

				} else {
					responseMessage.setMessage("Action mapping is not maintained in config file!");
					responseMessage.setStatus(ApplicationConstant.FAILURE);
					responseMessage.setStatusCode(ApplicationConstant.CODE_FAILURE);
				}
			} else {
				responseMessage.setMessage("Action Type is Null");
				responseMessage.setStatus(ApplicationConstant.FAILURE);
				responseMessage.setStatusCode(ApplicationConstant.CODE_FAILURE);
			}

		} catch (Exception e) {
			responseMessage.setMessage(e.getMessage());
			responseMessage.setStatus(ApplicationConstant.FAILURE);
			responseMessage.setStatusCode(ApplicationConstant.CODE_FAILURE);
		}

		return responseMessage;
	}

	public ResponseMessage checkCompositeResponse(CompositeInput compositeInputFileData,
			ResponseDto compositeResponseDto) {
		ResponseMessage responseMessage = new ResponseMessage();
		compositeInputFileData.getCompositeRequest().parallelStream().forEach(compositeInput -> {
			CompositeOutput compositeOutput = (CompositeOutput) compositeResponseDto.getData();
			compositeOutput.getCompositeResponse().stream().forEach(output -> {
				if (!compositeInput.getReferenceId().equals(output.getReferenceId())
						|| compositeInput.getIsAction() == null || !compositeInput.getIsAction()
						|| compositeInput.getActionSuccessCode().equals(output.getStatus())) {
					responseMessage.setStatus(ApplicationConstant.SUCCESS);
					responseMessage.setStatusCode(ApplicationConstant.SUCCESS);
				} else if (output.getStatus() != null && output.getStatus() >= 200 && output.getStatus() <= 299) {
					responseMessage.setStatus(ApplicationConstant.SUCCESS);
					responseMessage.setStatusCode(ApplicationConstant.SUCCESS);
				} else {
					responseMessage.setStatus(ApplicationConstant.FAILURE);
					responseMessage.setStatusCode(ApplicationConstant.FAILURE);
					responseMessage.setMessage(output.getBody().toString());
				}
			});
		});

		return responseMessage;
	}

}
