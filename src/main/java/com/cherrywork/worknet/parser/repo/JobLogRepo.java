package com.cherrywork.worknet.parser.repo;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cherrywork.worknet.parser.dto.JobLogDto;
import com.cherrywork.worknet.parser.entity.JobLogDo;

@Repository
public interface JobLogRepo extends JpaRepository<JobLogDo, String> {

	default List<JobLogDo> importDto(List<JobLogDto> jobConfigurationDtoList) {
		if (jobConfigurationDtoList != null && !jobConfigurationDtoList.isEmpty()) {
			List<JobLogDo> jobconfigurationDoList = new ArrayList<>();
			jobConfigurationDtoList.forEach(dto -> {

				JobLogDo entity = new JobLogDo();
				BeanUtils.copyProperties(dto, entity);
				jobconfigurationDoList.add(entity);
			});

			return jobconfigurationDoList;
		}
		return null;

	}

	default List<JobLogDto> exportDto(List<JobLogDo> jobConfigurationDoList) {

		if (jobConfigurationDoList != null && !jobConfigurationDoList.isEmpty()) {
			List<JobLogDto> jobConfigurationDtoList = new ArrayList<>();
			jobConfigurationDoList.forEach(entity -> {

				JobLogDto dto = new JobLogDto();
				BeanUtils.copyProperties(entity, dto);
				jobConfigurationDtoList.add(dto);
			});

			return jobConfigurationDtoList;
		}
		return null;
	}

	default JobLogDo importDto(JobLogDto dto) {
		JobLogDo entity = null;
		if (dto != null) {
			entity = new JobLogDo();
			BeanUtils.copyProperties(dto, entity);
		}
		return entity;
	}

	default JobLogDto exportDto(JobLogDo entity) {
		JobLogDto dto = null;
		if (entity != null) {
			dto = new JobLogDto();
			BeanUtils.copyProperties(entity, dto);
		}
		return dto;
	}

}
