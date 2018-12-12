package io.openems.edge.battery.soltaro.master;

import io.openems.edge.common.channel.doc.OptionsEnum;

public enum State implements OptionsEnum {
	
	OFF("Off", 0),
	INIT("Initializing", 0),
	RUNNING("Running", 0),
	STOPPING("Stopping", 0),
	ERROR("Error", 0),
	ERRORDELAY("Errordelay", 0),
	UNDEFINED("Undefined", 0),;

	private State(String option, int value) {
		this.option = option;
		this.value = value;
	}
	
	private int value;
	private String option;
	
	@Override
	public int getValue() {
		return this.value;
	}

	@Override
	public String getOption() {
		return this.option;
	}
	
}
