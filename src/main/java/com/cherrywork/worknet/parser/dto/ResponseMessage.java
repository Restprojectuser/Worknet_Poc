package com.cherrywork.worknet.parser.dto;

import lombok.Data;

/**
 * @author Shruti.Ghanshyam
 *
 */
@Data
public class ResponseMessage {

	private String status;
	private String statusCode;
	private String message;
	private Object data;

}
