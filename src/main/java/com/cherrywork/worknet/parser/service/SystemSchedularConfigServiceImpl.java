package com.cherrywork.worknet.parser.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cherrywork.worknet.composite.helpers.ResponseDto;
import com.cherrywork.worknet.parser.dto.SystemSchedularConfigDto;
import com.cherrywork.worknet.parser.entity.SystemSchedularConfigDo;
import com.cherrywork.worknet.parser.repo.SystemSchedularConfigRepo;
import com.cherrywork.worknet.parser.scheduler.SchedulerServiceImpl;

/**
 * @author Shruti.Ghanshyam
 *
 */
@Service
@Transactional
public class SystemSchedularConfigServiceImpl implements SystemSchedularConfigService {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private SystemSchedularConfigRepo schedularMasterRepo;

	@Autowired
	private SchedulerServiceImpl schedulerServiceImpl;

	@Override
	public ResponseDto saveOrUpdateSystemSchedular(List<SystemSchedularConfigDto> systemSchedularConfigDtos) {
		logger.info(
				"Worknet-composite-connector | SystemSchedularMasterServiceImpl | createSystemSchedular | Execution start : Input : systemSchedularConfigDtos = "
						+ systemSchedularConfigDtos);

		ResponseDto responseDto = new ResponseDto();

		try {

			responseDto.setStatus(true);
			responseDto.setStatusCode(200);

			if (systemSchedularConfigDtos != null && !systemSchedularConfigDtos.isEmpty()) {

//				Map<String, SystemSchedularConfigDto> systemMap = getSystemScheduleMap(
//						systemSchedularConfigDtos.get(0).getSystemId());
//
//				for (SystemSchedularConfigDto dto : systemSchedularConfigDtos) {
//					Boolean runJob = false;
//
//					SystemSchedularConfigDto schedularMasterDto = systemMap.get(dto.getProcessName());
//
//					if (schedularMasterDto != null) {
//						if (dto.getFrequencyOfPull() != null && schedularMasterDto.getFrequencyOfPull() != null
//								&& !dto.getFrequencyOfPull().equals(schedularMasterDto.getFrequencyOfPull())) {
//							runJob = true;
//						}
//					} else {
//						runJob = true;
//					}
//					if (runJob) {
//						schedulerServiceImpl.runJob(dto);
//					}
//				}
				List<SystemSchedularConfigDo> schedularConfigDos = new ArrayList<>();
				systemSchedularConfigDtos.forEach(dto -> schedularConfigDos.add(schedularMasterRepo.importDto(dto)));
				schedularMasterRepo.saveAll(schedularConfigDos);
				schedularConfigDos.clear();
				schedulerServiceImpl.updateScheduledJobsFrequency();
				responseDto.setMessage("Sytem schedular created successfully");

			} else {
				responseDto.setStatus(false);
				responseDto.setMessage("Sytem data is required for scheduling the request!");
			}

		} catch (Exception e) {
			logger.error(
					"Worknet-composite-connector | SystemSchedularMasterServiceImpl | createSystemSchedular | Message : "
							+ responseDto.getMessage());
			responseDto.setStatus(false);
			responseDto.setStatusCode(500);
			responseDto.setMessage(e.getMessage());

		}
		logger.info(
				"Worknet-composite-connector | SystemSchedularMasterServiceImpl | createSystemSchedular | Execution end : Output : Response Status Code "
						+ "[" + responseDto.getStatusCode() + "], Reponse Message [" + responseDto.getMessage() + "]");

		return responseDto;
	}

	private Map<String, SystemSchedularConfigDto> getSystemScheduleMap(String systemId) {
		Map<String, SystemSchedularConfigDto> systemMap = new HashMap<>();
		List<SystemSchedularConfigDo> schedularConfigDos = schedularMasterRepo.getDataById(systemId);
		if (schedularConfigDos != null && !schedularConfigDos.isEmpty()) {
			schedularConfigDos.forEach(
					entity -> systemMap.put(entity.getId().getProcessName(), schedularMasterRepo.exportDto(entity)));
		}
		return systemMap;
	}

}
