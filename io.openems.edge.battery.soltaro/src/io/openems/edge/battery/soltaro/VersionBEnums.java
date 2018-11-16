package io.openems.edge.battery.soltaro;

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
}
