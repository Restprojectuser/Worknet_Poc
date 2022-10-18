package com.cherrywork.worknet.parser.dto;

import lombok.Data;

@Data
public class SignUrlPayloadDto {

	private String envelopeId;
	private String systemId;
	private String userId;
	private String taskId;
	private String signUrl;

}
