package com.cherrywork.worknet.parser.dto;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @author Shruti.Ghanshyam
 *
 */
@Getter
@Setter
@ToString
public class JobLogDto {

	private String id;

	private String jobState;

	private String message;

	private Date startTime;

	private Date endTime;

	private Long timeTaken;

	private String origin;

}
