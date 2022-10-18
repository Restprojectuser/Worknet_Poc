package com.cherrywork.worknet.parser.repo;

import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cherrywork.worknet.parser.dto.SystemSchedularConfigDto;
import com.cherrywork.worknet.parser.entity.SystemSchedularConfigDo;
import com.cherrywork.worknet.parser.entity.SystemSchedularConfigDoPk;

@Repository
public interface SystemSchedularConfigRepo extends JpaRepository<SystemSchedularConfigDo, String> {
	/**
	 * @param dto
	 * @return
	 */
	default SystemSchedularConfigDo importDto(SystemSchedularConfigDto dto) {
		SystemSchedularConfigDo entity = null;
		if (dto != null) {
			entity = new SystemSchedularConfigDo();
			BeanUtils.copyProperties(dto, entity);
			SystemSchedularConfigDoPk id = new SystemSchedularConfigDoPk();
			id.setProcessName(dto.getProcessName());
			id.setSystemId(dto.getSystemId());
			entity.setId(id);
		}
		return entity;
	}

	/**
	 * @param entity
	 * @return
	 */
	default SystemSchedularConfigDto exportDto(SystemSchedularConfigDo entity) {
		SystemSchedularConfigDto dto = null;
		if (entity != null) {
			dto = new SystemSchedularConfigDto();
			BeanUtils.copyProperties(entity, dto);
			dto.setProcessName(entity.getId().getProcessName());
			dto.setSystemId(entity.getId().getSystemId());

		}
		return dto;
	}

	@Modifying
	@Query("update SystemSchedularConfigDo s set s.status=:status where s.id.systemId=:systemId")
	void updateStatusOfSystemSchedular(@Param("systemId") String systemId, @Param("status") String status);

	@Query("select s from SystemSchedularConfigDo s where s.id.systemId=:systemId and s.id.processName=:processName")
	SystemSchedularConfigDo findByIdAndProcessName(@Param("systemId") String systemId,
			@Param("processName") String processName);

	@Query("select s from SystemSchedularConfigDo s where s.id.systemId=:systemId")
	List<SystemSchedularConfigDo> getDataById(@Param("systemId") String systemId);

}
