package com.cherrywork.worknet.parser.entity;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

import lombok.Data;

/**
 * @author Shruti.Ghanshyam
 *
 */
@Embeddable
@Data
public class SystemSchedularConfigDoPk implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "SYSTEM_ID")
	private String systemId;

	@Column(name = "PROCESS_NAME")
	private String processName;
}
