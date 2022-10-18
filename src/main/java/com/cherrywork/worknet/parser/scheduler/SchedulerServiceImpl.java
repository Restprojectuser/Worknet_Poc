package com.cherrywork.worknet.parser.scheduler;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import com.cherrywork.worknet.aspect.AsyncExecuter;
import com.cherrywork.worknet.composite.dto.CompositeInput;
import com.cherrywork.worknet.composite.dto.CompositeOutput;
import com.cherrywork.worknet.composite.helpers.ResponseDto;
import com.cherrywork.worknet.composite.service.CompositeApiServiceNew;
import com.cherrywork.worknet.parser.dto.CompositeDto;
import com.cherrywork.worknet.parser.dto.SystemSchedularConfigDto;
import com.cherrywork.worknet.parser.entity.JobLogDo;
import com.cherrywork.worknet.parser.entity.SystemSchedularConfigDo;
import com.cherrywork.worknet.parser.entity.SystemSchedularConfigDoPk;
import com.cherrywork.worknet.parser.repo.JobLogRepo;
import com.cherrywork.worknet.parser.repo.SystemSchedularConfigRepo;
import com.cherrywork.worknet.parser.service.CompositeApiParserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SchedulerServiceImpl {

	@Autowired
	private CompositeApiParserService compositeApiParserService;

	@Autowired
	private ThreadPoolTaskScheduler taskScheduler;

	@Autowired
	private SystemSchedularConfigRepo systemSchedularConfigRepo;

	@Autowired
	private CompositeApiServiceNew compositeApiServiceNew;

	@Autowired
	AsyncExecuter asyncExecuter;

	@Autowired
	private JobLogRepo jobLogRepo;

	private Map<SystemSchedularConfigDoPk, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

	public ScheduledFuture<?> triggerJob(SystemSchedularConfigDto schedularMasterDto) {

		return taskScheduler.schedule(() -> {

			try {

				if (schedularMasterDto != null && schedularMasterDto.getPullTasks() != null
						&& schedularMasterDto.getPullTasks()) {

					Long start = System.currentTimeMillis();
					runJob(schedularMasterDto);
					log.info("Time taken for running " + schedularMasterDto.getSystemId() + " Job in seconds is : "
							+ (System.currentTimeMillis() - start) / 1000);
				}

			} catch (Exception e) {
				log.error(e.getMessage());
			}

		}, new CronTrigger(schedularMasterDto.getFrequencyOfPull(), TimeZone.getTimeZone("IST")));

	}

	@PostConstruct
	private void postConstruct() {
		List<SystemSchedularConfigDo> schedularConfigDos = systemSchedularConfigRepo.findAll();
		if (schedularConfigDos != null && !schedularConfigDos.isEmpty()) {
			schedularConfigDos.stream().forEach(scheduler -> {
				if (scheduler.getIsActive() != null && scheduler.getIsActive() && scheduler.getPullTasks() != null
						&& scheduler.getPullTasks()) {
					scheduledTasks.put(scheduler.getId(), triggerJob(systemSchedularConfigRepo.exportDto(scheduler)));

				}
			});
		}
	}

	public void updateScheduledJobsFrequency() {
		List<SystemSchedularConfigDo> schedularConfigDos = systemSchedularConfigRepo.findAll();
		if (schedularConfigDos != null && !schedularConfigDos.isEmpty()) {
			schedularConfigDos.stream().forEach(scheduler -> {
				if (scheduledTasks.get(scheduler.getId()) != null) {
					scheduledTasks.get(scheduler.getId()).cancel(true);
					scheduledTasks.remove(scheduler.getId());
				}

				if (scheduler.getIsActive() != null && scheduler.getIsActive() && scheduler.getPullTasks() != null
						&& scheduler.getPullTasks()) {
					scheduledTasks.put(scheduler.getId(), triggerJob(systemSchedularConfigRepo.exportDto(scheduler)));
				}
			});
		}

	}

	public void runJob(SystemSchedularConfigDto schedularMasterDto) throws Exception {

		JobLogDo jobLogDto = new JobLogDo();
		jobLogDto.setJobState("Running");
		jobLogDto.setId(UUID.randomUUID().toString());
		jobLogDto.setStartTime(new Date());
		jobLogDto.setOrigin(schedularMasterDto.getSystemId());
		jobLogRepo.save(jobLogDto);

		CompletableFuture<CompositeDto> future = CompletableFuture.supplyAsync(this::getCompositeTableMap)
				.thenApply(compositeTableMap -> getCompositeInput(schedularMasterDto, compositeTableMap))
				.thenApply(compositeInput -> compositeApiParserServiceParsedResult(schedularMasterDto, compositeInput));

		CompositeDto dto = future.get();
		dto.setOrigin(schedularMasterDto.getSystemId());
		dto.setJobLogId(jobLogDto.getId());

//		asyncExecuter.sendJobDetailsToMessagingService(jobLogDto.getId());

		compositeApiParserService.processResult(dto);

	}

	private Map<String, CompositeInput> getCompositeTableMap() {
		try {
			return compositeApiParserService.readYamlFile("applicationConfig/systemJobConfig",
					new TypeReference<Map<String, CompositeInput>>() {
					});
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private CompositeInput getCompositeInput(SystemSchedularConfigDto schedularMasterDto,
			Map<String, CompositeInput> compositeTableMap) {
		CompositeInput compositeInput = null;
		if (compositeTableMap
				.containsKey(schedularMasterDto.getSystemId() + "_" + schedularMasterDto.getProcessName())) {
			compositeInput = compositeTableMap
					.get(schedularMasterDto.getSystemId() + "_" + schedularMasterDto.getProcessName());
		} else if (compositeTableMap.containsKey(schedularMasterDto.getSystemId())) {
			compositeInput = compositeTableMap.get(schedularMasterDto.getSystemId());
		}
		return compositeInput;

	}

	private CompositeDto compositeApiParserServiceParsedResult(SystemSchedularConfigDto schedularMasterDto,
			CompositeInput compositeInput) {
		if (compositeInput != null) {
			if (compositeInput.getCompositeRequest() != null && !compositeInput.getCompositeRequest().isEmpty()) {
				ObjectMapper mapper = new ObjectMapper();
				compositeInput.getCompositeRequest().get(0)
						.setCommonRequestBody(mapper.valueToTree(schedularMasterDto));
			}
			return getParsedCompositeOutput(compositeInput);
		}
		return null;
	}

	private CompositeDto getParsedCompositeOutput(CompositeInput compositeInputPayload) {
		CompositeInput compositeInputFileData = null;
		CompositeOutput compositeOutput = null;

		try {
			compositeInputFileData = compositeApiParserService.readYamlFile(
					"composite/" + compositeInputPayload.getCompositeApiName(), new TypeReference<CompositeInput>() {
					});

			// code for docusign
			if ("DocuSign".equalsIgnoreCase(compositeInputPayload.getOrigin())) {
				Map<String, String> docuSignAccDetails = compositeApiParserService.readYamlFile("DocusignConfig",
						new TypeReference<Map<String, String>>() {
						});

				String assertionToken = compositeApiParserService.getAssertion(docuSignAccDetails);
				JsonNode assertionNode = new ObjectMapper()
						.readTree("{\"jwtAssertionToken\":\"" + assertionToken + "\"}");

				compositeInputFileData.getCompositeRequest().stream().forEach(request -> {
					if ("getAccessToken".equalsIgnoreCase(request.getReferenceId())) {
						request.setCommonRequestBody(assertionNode);
					}

				});

			}

			ResponseDto compositeResponseDto = compositeApiServiceNew.getCompositeResponse(compositeInputPayload,
					compositeInputFileData);
			compositeOutput = (CompositeOutput) compositeResponseDto.getData();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		CompositeDto dto = new CompositeDto();
		dto.setCompositeInput(compositeInputPayload);
		dto.setCompositeOutput(compositeOutput);
		return dto;
	}
}
