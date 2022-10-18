package com.cherrywork.worknet.parser.dto;

import lombok.Data;

/**
 * @author Shruti.Ghanshyam
 *
 */
@Data
public class SystemMasterDto {

	private String systemId;

	private String systemName;

	private boolean isActive;

	private String systemLogo;
}
