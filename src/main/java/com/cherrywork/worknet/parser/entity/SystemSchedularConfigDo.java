package com.cherrywork.worknet.parser.entity;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

import lombok.Data;

/**
 * @author Shruti.Ghanshyam
 *
 */
@Entity
@Table(name = "CW_ITM_WN_SYSTEM_SCHEDULER_CONFIG")
@Data
public class SystemSchedularConfigDo {

	@EmbeddedId
	private SystemSchedularConfigDoPk id;

	@Column(name = "IS_PULL")
	private Boolean pullTasks;

	@Column(name = "IS_APPROVE")
	private Boolean approveTasks;

	@Column(name = "IS_ACTIVE")
	private Boolean isActive;

	@Column(name = "PULL_FREQUENCY")
	private String frequencyOfPull;

	@Column(name = "STATUS")
	private String status;

	@Column(name = "CREATED_BY")
	private String createdBy;

	@Column(name = "UPDATED_BY")
	private String updatedBy;

	@Column(name = "CREATED_ON")
	private Date createdOn;

	@Column(name = "UPDATED_ON")
	private Date updatedOn;

}
