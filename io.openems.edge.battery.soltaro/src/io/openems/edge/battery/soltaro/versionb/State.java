package io.openems.edge.battery.soltaro.versionb;

import io.openems.edge.common.channel.doc.OptionsEnum;

public enum State implements OptionsEnum {
	
	UNDEFINED("Undefined", -1),
//	PENDING("Pending", 0),
	OFF("Off", 1),
	INIT("Initializing", 2),
	RUNNING("Running", 3),
	STOPPING("Stopping", 4),
	ERROR("Error", 5),
	ERRORDELAY("Errordelay", 6),
//	CONFIGURING("Configuring", 7);
	;

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
