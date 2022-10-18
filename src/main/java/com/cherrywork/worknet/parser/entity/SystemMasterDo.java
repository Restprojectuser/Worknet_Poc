package com.cherrywork.worknet.parser.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;

/**
 * @author Shruti.Ghanshyam
 *
 */
@Entity
@Table(name = "CW_ITM_WN_SYSTEM_MASTER")
@Data
public class SystemMasterDo {

	@Id
	@Column(name = "SYSTEM_ID")
	private String systemId;

	@Column(name = "SYSTEM_NAME")
	private String systemName;

	@Column(name = "IS_ACTIVE")
	private boolean isActive;

	@Column(name = "SYSTEM_LOGO")
	private String systemLogo;

}
