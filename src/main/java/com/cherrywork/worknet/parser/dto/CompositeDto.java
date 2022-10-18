package com.cherrywork.worknet.parser.dto;

import java.io.Serializable;

import com.cherrywork.worknet.composite.dto.CompositeInput;
import com.cherrywork.worknet.composite.dto.CompositeOutput;

import lombok.Data;

@Data
public class CompositeDto implements Serializable {
	private String origin;
	private CompositeInput compositeInput;
	private CompositeOutput compositeOutput;
	private String jobLogId;
}
