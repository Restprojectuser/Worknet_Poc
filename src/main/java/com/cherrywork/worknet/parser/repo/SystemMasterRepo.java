package com.cherrywork.worknet.parser.repo;

import org.springframework.beans.BeanUtils;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cherrywork.worknet.parser.dto.SystemMasterDto;
import com.cherrywork.worknet.parser.entity.SystemMasterDo;

@Repository
public interface SystemMasterRepo extends JpaRepository<SystemMasterDo, String> {
	/**
	 * @param dto
	 * @return
	 */
	default SystemMasterDo importDto(SystemMasterDto dto) {
		SystemMasterDo entity = null;
		if (dto != null) {
			entity = new SystemMasterDo();
			BeanUtils.copyProperties(dto, entity);
		}
		return entity;
	}

	/**
	 * @param entity
	 * @return
	 */
	default SystemMasterDto exportDto(SystemMasterDo entity) {
		SystemMasterDto dto = null;
		if (entity != null) {
			dto = new SystemMasterDto();
			BeanUtils.copyProperties(entity, dto);

		}
		return dto;
	}

	@Query(value = "select distinct upper(s.systemName) from SystemMasterDo s where upper(s.systemName)=upper(:systemName) and (:systemId is null or s.systemId!=:systemId) ")
	String validateSystemName(@Param("systemName") String systemName, @Param("systemId") String systemId);

	@Modifying
	@Query("update SystemMasterDo s set s.isActive=:isActive where s.systemId=:systemId")
	void updateStatusOfSystem(@Param("systemId") String systemId, @Param("isActive") Boolean isActive);

	SystemMasterDo findBySystemName(String systemName);

}
