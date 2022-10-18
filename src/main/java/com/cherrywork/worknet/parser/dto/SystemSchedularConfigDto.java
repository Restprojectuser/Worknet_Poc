package com.cherrywork.worknet.parser.dto;

import java.util.Date;

import lombok.Data;

/**
 * @author Shruti.Ghanshyam
 *
 */
@Data
public class SystemSchedularConfigDto {

	private String systemId;

	private String processName;

	private Boolean pullTasks;

	private Boolean approveTasks;

	private Boolean isActive;

	private String frequencyOfPull;

	private String status;

	private String createdBy;

	private String updatedBy;

	private Date createdOn;

	private Date updatedOn;

	// Other properties
	private String systemName;

	private String systemLogo;

}
