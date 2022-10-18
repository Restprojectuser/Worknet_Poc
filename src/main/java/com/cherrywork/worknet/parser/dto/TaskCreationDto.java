package com.cherrywork.worknet.parser.dto;

import java.util.Date;
import java.util.Map;

import lombok.Data;

@Data
public class TaskCreationDto {

	private String systemId;
	private String systemName;
	private String processName;
	private String taskType;
	private String taskId;
	private String processId;
	private String requestId;
	private String taskDesc;
	private String processDesc;
	private String subject;
	private String status;
	private String priority;
	private String resourceId;
	private Date compDeadline;
	private Date criticalDeadline;
	private String updatedBy;
	private Date updateOn;
	private String createdBy;
	private Date createdOn;
	private Long isPrimaryOwner;
	private String ownerType;
	private String createdByName;
	private String updatedByName;

	private Map<String, Object> attributes;
	private String context;

}
