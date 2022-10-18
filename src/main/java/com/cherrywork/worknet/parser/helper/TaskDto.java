package com.cherrywork.worknet.parser.helper;

import java.util.List;
import java.util.Map;

import com.cherrywork.worknet.parser.dto.ForwardOwnerDto;

import lombok.Data;

/**
 * @author Shruti.Ghanshyam
 *
 */
@Data
public class TaskDto {

	private String systemId;
	private String processName;
	private String processDesc;
	private String taskType;
	private String taskDesc;
	private String taskId;
	private String processId;
	private String requestId;
	private String ownerId;
	private String action;
	private String comment;
	private String subject;
	private Boolean isAdmin;
	private String userId;
	private List<ForwardOwnerDto> forwardOwners;
	private Map<String, Object> attributes;
	private String userAccessToken;
	private String ownerIds;
	private String referenceId;

}
