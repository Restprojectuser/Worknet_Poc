package com.cherrywork.worknet.parser.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.cherrywork.worknet.composite.helpers.ResponseDto;
import com.cherrywork.worknet.parser.dto.SystemMasterDto;
import com.cherrywork.worknet.parser.entity.SystemMasterDo;
import com.cherrywork.worknet.parser.repo.SystemMasterRepo;

@Service
@Transactional
public class SystemMasterServiceImpl implements SystemMasterService {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private SystemMasterRepo systemMasterRepo;

	@Override
	public ResponseDto getSystemMaster(String systemId, PageRequest pageable, String searchString) {
		logger.info(
				"Worknet-composite-connector | SystemSchedularMasterServiceImpl | getSystemMaster | Execution start : Input : id : "
						+ systemId);

		ResponseDto responseDto = new ResponseDto();

		responseDto.setStatus(Boolean.TRUE);
		responseDto.setStatusCode(200);
		try {

			if (systemId != null && !systemId.trim().isEmpty()) {
				responseDto.setData(systemMasterRepo.exportDto(systemMasterRepo.findById(systemId).orElse(null)));
			} else {
				List<SystemMasterDo> schedularMasterDos = systemMasterRepo.findAll();
				if (schedularMasterDos != null && !schedularMasterDos.isEmpty()) {
					List<SystemMasterDto> dtos = schedularMasterDos.stream().map(entity -> {
						return systemMasterRepo.exportDto(entity);
					}).collect(Collectors.toList());
					responseDto.setData(dtos);
				}
			}
			responseDto.setMessage("System schedules fetched successfully");

		} catch (Exception e) {
			logger.error("Worknet-composite-connector | SystemSchedularMasterServiceImpl | getSystemMaster | Message : "
					+ responseDto.getMessage());
			responseDto.setStatus(Boolean.FALSE);
			responseDto.setStatusCode(500);
			responseDto.setMessage(e.getMessage());

		}
		logger.info(
				"Worknet-composite-connector | SystemSchedularMasterServiceImpl | getSystemMaster | Execution end :Output : Response Status Code "
						+ "[" + responseDto.getStatusCode() + "], Reponse Message [" + responseDto.getMessage() + "]");

		return responseDto;
	}

	@Override
	public ResponseDto createSystemMaster(SystemMasterDto systemMasterDto) {
		logger.info(
				"Worknet-composite-connector | SystemSchedularMasterServiceImpl | createSystemMaster | Execution start : Input : ApplicationUiDto = "
						+ systemMasterDto);

		ResponseDto responseDto = new ResponseDto();

		try {

			responseDto.setStatus(Boolean.TRUE);
			responseDto.setStatusCode(200);

			if (systemMasterDto != null && systemMasterDto.getSystemName() != null
					&& !systemMasterDto.getSystemName().trim().isEmpty()) {

				systemMasterDto.setSystemId(UUID.randomUUID().toString());

				// Validate duplicate name
				String name = systemMasterRepo.validateSystemName(systemMasterDto.getSystemName(), null);

				if (name == null) {
					responseDto.setData(systemMasterRepo.save(systemMasterRepo.importDto(systemMasterDto)));
					responseDto.setMessage("Sytem schedular created successfully");

				} else {
					responseDto.setStatus(Boolean.FALSE);
					responseDto.setMessage("System name [" + systemMasterDto.getSystemName()
							+ "] already exists. Please provide unique name!");
				}

			} else {
				responseDto.setStatus(Boolean.FALSE);
				responseDto.setMessage("Sytem data is required for creating the system schedule");
			}

		} catch (Exception e) {
			logger.error(
					"Worknet-composite-connector | SystemSchedularMasterServiceImpl | createSystemMaster | Message : "
							+ responseDto.getMessage());
			responseDto.setStatus(Boolean.FALSE);
			responseDto.setStatusCode(500);
			responseDto.setMessage(e.getMessage());

		}
		logger.info(
				"Worknet-composite-connector | SystemSchedularMasterServiceImpl | createSystemMaster | Execution end : Output : Response Status Code "
						+ "[" + responseDto.getStatusCode() + "], Reponse Message [" + responseDto.getMessage() + "]");

		return responseDto;
	}

	@Override
	public ResponseDto updateSystemMaster(String systemId, SystemMasterDto systemMasterDto) {
		logger.info(
				"Worknet-composite-connector | SystemSchedularMasterServiceImpl | updateSystemMaster | Execution start : Input : ApplicationUiDto = "
						+ systemMasterDto);

		ResponseDto responseDto = new ResponseDto();

		try {

			responseDto.setStatus(Boolean.TRUE);
			responseDto.setStatusCode(200);

			if (systemMasterDto != null && systemMasterDto.getSystemName() != null
					&& !systemMasterDto.getSystemName().trim().isEmpty()) {

				// Validate duplicate name
				String name = systemMasterRepo.validateSystemName(systemMasterDto.getSystemName(),
						systemMasterDto.getSystemId());

				if (name == null) {
					responseDto.setData(systemMasterRepo.save(systemMasterRepo.importDto(systemMasterDto)));
					responseDto.setMessage("Sytem schedular updated successfully");

				} else {
					responseDto.setStatus(Boolean.FALSE);
					responseDto.setMessage("System name [" + systemMasterDto.getSystemName()
							+ "] already exists. Please provide unique name!");
				}

			} else {
				responseDto.setStatus(Boolean.FALSE);
				responseDto.setMessage("Sytem data is required for updating the system schedule");
			}

		} catch (Exception e) {
			logger.error(
					"Worknet-composite-connector | SystemSchedularMasterServiceImpl | updateSystemMaster | Message : "
							+ responseDto.getMessage());
			responseDto.setStatus(Boolean.FALSE);
			responseDto.setStatusCode(500);
			responseDto.setMessage(e.getMessage());

		}
		logger.info(
				"Worknet-composite-connector | SystemSchedularMasterServiceImpl | updateSystemMaster | Execution end : Output : Response Status Code "
						+ "[" + responseDto.getStatusCode() + "], Reponse Message [" + responseDto.getMessage() + "]");

		return responseDto;
	}

	@Override
	public ResponseDto deleteSystemMaster(String systemId) {
		logger.info(
				"Worknet-composite-connector | SystemSchedularMasterServiceImpl | deleteSystemMaster | Execution start : Input : id : "
						+ systemId);
		ResponseDto responseDto = new ResponseDto();

		try {

			responseDto.setStatus(Boolean.TRUE);
			responseDto.setStatusCode(200);
			if (systemId != null && !systemId.trim().isEmpty()) {

				systemMasterRepo.updateStatusOfSystem(systemId, Boolean.FALSE);
				responseDto.setMessage("System schedular deactivated successfully!");
			}

		} catch (Exception e) {
			logger.error(
					"Worknet-composite-connector | SystemSchedularMasterServiceImpl | deleteSystemMaster | Message : "
							+ responseDto.getMessage());
			responseDto.setStatus(Boolean.FALSE);
			responseDto.setStatusCode(500);
			responseDto.setMessage(e.getMessage());

		}
		logger.info(
				"Worknet-composite-connector | SystemSchedularMasterServiceImpl | deleteSystemMaster | Execution end : Output : Response Status Code "
						+ "[" + responseDto.getStatusCode() + "], Reponse Message [" + responseDto.getMessage() + "]");

		return responseDto;
	}

}
