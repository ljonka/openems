package io.openems.edge.battery.soltaro.master;

import io.openems.edge.common.channel.doc.OptionsEnum;

public class Enums {


	public enum StartStop implements OptionsEnum {

		START(1, "Start"), STOP(2, "Stop");

		int value;
		String option;

		private StartStop(int value, String option) {
			this.value = value;
			this.option = option;
		}

		@Override
		public int getValue() {
			return value;
		}

		@Override
		public String getOption() {
			return option;
		}
	}
	
	public enum RackUsage implements OptionsEnum {

		USED(1, "Rack is used"), UNUSED(2, "Rack is not used");

		int value;
		String option;

		private RackUsage(int value, String option) {
			this.value = value;
			this.option = option;
		}

		@Override
		public int getValue() {
			return value;
		}

		@Override
		public String getOption() {
			return option;
		}
	}
	
	public enum ChargeIndication implements OptionsEnum {

		STANDING(0, "Standby"), DISCHARGING(1, "Discharging"), CHARGING(2, "Charging");

		private int value;
		private String option;

		private ChargeIndication(int value, String option) {
			this.value = value;
			this.option = option;
		}

		@Override
		public int getValue() {
			return value;
		}

		@Override
		public String getOption() {
			return option;
		}
	}
	
	public enum RunningState implements OptionsEnum {

		NORMAL(0, "Normal"),
		FULLY_CHARGED(1, "Fully charged"),
		EMPTY(2, "Empty"),
		STANDBY(3, "Standby"),
		STOPPED(4, "Stopped");

		int value;
		String option;

		private RunningState(int value, String option) {
			this.value = value;
			this.option = option;
		}

		@Override
		public int getValue() {
			return value;
		}

		@Override
		public String getOption() {
			return option;
		}
	}
	
	
}
