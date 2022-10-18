package com.cherrywork.worknet.parser.entity;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

import lombok.Data;

/**
 * @author Shruti.Ghanshyam
 *
 */
@Entity
@Table(name = "CW_ITM_WN_JOB_LOG")
@Data
public class JobLogDo {

	@Id
	@Column(name = "ID")
	private String id;

	@Column(name = "JOB_STATE")
	private String jobState;

	@Column(name = "MESSAGE")
	private String message;

	@Column(name = "START_TIME")
	private Date startTime;

	@Column(name = "END_TIME")
	private Date endTime;

	@Column(name = "TIME_TAKEN")
	private Long timeTaken;

	@Column(name = "ORIGIN")
	private String origin;

	@Lob
	@Column(name = "JOB_INPUT")
	private String jobInput;

	@Lob
	@Column(name = "JOB_OUTPUT")
	private String jobOutput;

}
