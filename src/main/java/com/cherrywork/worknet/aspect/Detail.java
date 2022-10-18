package com.cherrywork.worknet.aspect;

public class Detail {
	private String system_id, process_id, task_id, action;

	public Detail(String system_id, String process_id, String task_id, String action) {
		super();
		this.system_id = system_id;
		this.process_id = process_id;
		this.task_id = task_id;
		this.action = action;
	}

	public Detail() {
		super();
		// TODO Auto-generated constructor stub
	}

	public String getSystem_id() {
		return system_id;
	}

	public void setSystem_id(String system_id) {
		this.system_id = system_id;
	}

	public String getProcess_id() {
		return process_id;
	}

	public void setProcess_id(String process_id) {
		this.process_id = process_id;
	}

	public String getTask_id() {
		return task_id;
	}

	public void setTask_id(String task_id) {
		this.task_id = task_id;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}
}


	