package io.openems.edge.battery.soltaro.versionb;

import io.openems.edge.common.channel.doc.OptionsEnum;

public class VersionBEnums {

	public enum FanStatus implements OptionsEnum {

		OPEN(0x1, "Open"), CLOSE(0x2, "Close");

		int value;
		String option;

		private FanStatus(int value, String option) {
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

	public enum ContactorState implements OptionsEnum {

		START(0x1, "Start"), STOP(0x2, "Stop");

		int value;
		String option;

		private ContactorState(int value, String option) {
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

	public enum ContactExport implements OptionsEnum {

		HIGH(0x1, "High"), LOW(0x2, "Low");

		int value;
		String option;

		private ContactExport(int value, String option) {
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

	public enum SystemRunMode implements OptionsEnum {

		NORMAL(0x1, "Normal"), DEBUG(0x2, "Debug");

		int value;
		String option;

		private SystemRunMode(int value, String option) {
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
	
	public enum PreContactorState implements OptionsEnum {

		START(0x1, "Start"), STOP(0x2, "Stop");

		int value;
		String option;

		private PreContactorState(int value, String option) {
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
	
	public enum ShortCircuitFunction implements OptionsEnum {

		ENABLE(0x1, "Enable"), DISABLE(0x2, "Disable");

		int value;
		String option;

		private ShortCircuitFunction(int value, String option) {
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
	
	public enum AutoSetFunction implements OptionsEnum {

		INIT_MODE(0x1, "Init mode"), 
		START_AUTO_SETTING(0x2, "Start auto setting"),
		SUCCES(0x2, "Success"),
		FAILURE(0x3, "Failure");

		int value;
		String option;

		private AutoSetFunction(int value, String option) {
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

		STANDING(0, "Standing"), DISCHARGING(1, "Discharging"), CHARGING(2, "Charging");

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

	public enum ContactorControl implements OptionsEnum {

		CUT_OFF(0, "Cut off"), CONNECTION_INITIATING(1, "Connection initiating"), ON_GRID(3, "On grid");

		int value;
		String option;

		private ContactorControl(int value, String option) {
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

	public enum ClusterRunState implements OptionsEnum {

		NORMAL(0, "Normal"), FULL(1, "Full"), EMPTY(2, "Empty"),
		STANDBY(3, "Standby"), STOP(4, "Stop");

		private int value;
		private String option;

		private ClusterRunState(int value, String option) {
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