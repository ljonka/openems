package io.openems.edge.meter.bsmu;

import java.util.Arrays;
import java.util.stream.Stream;

import io.openems.edge.common.channel.AbstractReadChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.StateCollectorChannel;
import io.openems.edge.common.component.OpenemsComponent;


public class Utils {
	public static Stream<? extends AbstractReadChannel<?>> initializeChannels(BSMU ess) {
		// Define the channels. Using streams + switch enables Eclipse IDE to tell us if
		// we are missing an Enum value.
		return Stream.of( //
				Arrays.stream(OpenemsComponent.ChannelId.values()).map(channelId -> {
					switch (channelId) {
					case STATE:
						return new StateCollectorChannel(ess, channelId);
					}
					return null;
				}), 
					
//				}), Arrays.stream(ManagedSymmetricEss.ChannelId.values()).map(channelId -> {
//					switch (channelId) {
//					case DEBUG_SET_ACTIVE_POWER:
//					case DEBUG_SET_REACTIVE_POWER:
//					case ALLOWED_CHARGE_POWER:
//					case ALLOWED_DISCHARGE_POWER:
//						return new IntegerReadChannel(ess, channelId);
//					}
//					return null;
					Arrays.stream(BSMU.ChannelId.values()).map(channelId -> {
					switch (channelId) {
					case SET_ENABLE_STRING_1:
					case SET_ENABLE_STRING_2:
					case SET_START_STOP_STRING_2:
					case SET_START_STOP_STRING_1:	
					case SET_BATTERY_CHARGE_DISCHARGE_REQUEST:
					case SET_START_STOP_BATTERY_STACK:
						
						return new IntegerWriteChannel(ess, channelId);
					case USER_SOC_1:
					case ABSOLUTE_TIME_SINCE_1RST_IGNITION_1:
					case ABSOLUTE_TIME_SINCE_1RST_IGNITION_2:
					case ALARMS_1:
					case ALARMS_2:
					case AVAILABLE_ENERGY_1:
					case AVAILABLE_ENERGY_2:
					case AVAILABLE_POWER_1:
					case AVAILABLE_POWER_2:
					case CELL_HIGHEST_VOLTAGE_1:
					case CELL_HIGHEST_VOLTAGE_2:
					case CELL_HIGHEST_VOLTAGE_RCY_1:
					case CELL_HIGHEST_VOLTAGE_RCY_2:
					case CELL_LOWEST_VOLTAGE_1:
					case CELL_LOWEST_VOLTAGE_2:
					case CELL_LOWEST_VOLTAGE_RCY_1:
					case CELL_LOWEST_VOLTAGE_RCY_2:
					case DC_POWER_1:
					case DC_POWER_2:
					case DISTANCE_TOTALIZER_COPY_1:
					case DISTANCE_TOTALIZER_COPY_2:
					case ELEC_MACHINE_SPEED_1:
					case ELEC_MACHINE_SPEED_2:
					case END_OF_CHARGE_REQUEST_1:
					case END_OF_CHARGE_REQUEST_2:
					case END_OF_DISCHARGE_REQUEST_1:
					case END_OF_DISCHARGE_REQUEST_2:
					case ETS_SLEEP_MODE_1:
					case ETS_SLEEP_MODE_2:
					case FAULTS_1:
					case FAULTS_2:
					case HV_BAT_HEALTH_1:
					case HV_BAT_HEALTH_2:
					case HV_BAT_INSTANT_CURRENT_1:
					case HV_BAT_INSTANT_CURRENT_2:
					case HV_BAT_LEVEL_1_FAILURE_1:
					case HV_BAT_LEVEL_1_FAILURE_2:
					case HV_BAT_LEVEL_2_FAILURE_1:
					case HV_BAT_LEVEL_2_FAILURE_2:
					case HV_BAT_LEVEL_2_FAILURE_RCY_1:
					case HV_BAT_LEVEL_2_FAILURE_RCY_2:
					case HV_BAT_MAX_TEMP_1:
					case HV_BAT_MAX_TEMP_2:
					case HV_BAT_MAX_TEMP_RCY_1:
					case HV_BAT_MAX_TEMP_RCY_2:
					case HV_BAT_SERIAL_NUMBER_1:
					case HV_BAT_SERIAL_NUMBER_2:
					case HV_BAT_STATE_1:
					case HV_BAT_STATE_2:
					case HV_BAT_TEMP_1:
					case HV_BAT_TEMP_2:
					case HV_ISOLATION_IMPEDANCE_1:
					case HV_ISOLATION_IMPEDANCE_2:
					case HV_NETWORK_VOLTAGE_1:
					case HV_NETWORK_VOLTAGE_2:
					case HV_POWER_CONNECTION_1:
					case HV_POWER_CONNECTION_2:
					case HV_POWER_CONNECTION_RCY_1:
					case HV_POWER_CONNECTION_RCY_2:
					case ISOL_DIAG_AUTHORISATION_1:
					case ISOL_DIAG_AUTHORISATION_2:
					case LBC2_REFUSE_TO_SLEEP_1:
					case LBC2_REFUSE_TO_SLEEP_2:
					case LBC_PRUN_ANSWER_1:
					case LBC_PRUN_ANSWER_2:
					case LBC_PRUN_ANSWER_RCY_1:
					case LBC_PRUN_ANSWER_RCY_2:
					case LBC_PRUN_KEY_1:
					case LBC_PRUN_KEY_RCY_1:
					case LBC_PRUN_KEY_RCY_2:
					case LBC_REFUSE_TO_SLEEP_1:
					case LBC_REFUSE_TO_SLEEP_2:
					case OPERATING_TYPE_1:
					case OPERATING_TYPE_2:
					case POWER_RELAY_STATE_1:
					case POWER_RELAY_STATE_2:
					case SAFETY_MODE_1_FLAG_1:
					case SAFETY_MODE_1_FLAG_2:
					case SAFETY_MODE_1_FLAG_RCY_2:
					case SCH_WAKE_UP_SLEEP_COMMAND_1:
					case SCH_WAKE_UP_SLEEP_COMMAND_2:
					case STRING_STATUS_1:
					case STRING_STATUS_2:
					case USER_SOC_2:
					case VEHICLE_ID_1:
					case VEHICLE_ID_2:
					case WAKE_UP_TYPE_1:
					case WAKE_UP_TYPE_2:
					case LBC_PRUN_KEY_2:
					case SAFETY_MODE_1_FLAG_RCY_1:
					case ALARMS_STACK:
					case AVERAGE_STRING_CURRENT:
					case AVERAGE_STRING_VOLTAGE:
					case BATTERY_CHARGE_DISCHARGE_REQUEST:
					case BATTERY_MANUFACTURER:
					case BATTERY_MODEL:
					case BATTERY_POWER_REQUEST:
					case BATTERY_TYP:
					case CONNECTED_STRING_COUNT:
					case CONTROLE_MODE:
					case CURRENT_TOTAL_CAPACITY:
					case ENABLE_STRING_1:
					case ENABLE_STRING_2:
					case FAULTS_STACK:
					case INSTALLATION_DATE:
					case INVERTER_STATE_REQUEST:
					case MAX_BATTERY_VOLTAGE:
					case MAX_CELL_VOLTAGE:
					case MAX_CELL_VOLTAGE_STRING:
					case MAX_CHARGE_CURRENT:
					case MAX_DISCHARGE_CURRENT:
					case MAX_STRING_CURRENT:
					case MAX_STRING_CURRENT_STRING:
					case MAX_STRING_VOLTAGE:
					case MAX_STRING_VOLTAGE_STRING:
					case MIN_BATTERY_VOLTAGE:
					case MIN_CELL_VOLTAGE:
					case MIN_CELL_VOLTAGE_STRING:
					case MIN_STRING_CURRENT:
					case MIN_STRING_CURRENT_STRING:
					case MIN_STRING_VOLTAGE:
					case MIN_STRING_VOLTAGE_STRING:
					case MODBUS_VERSION:
					case NAMEPLATE_CHARGE_CAPACITY:
					case NAMEPLATE_ENERGY_CAPACITY:
					case NAMEPLATE_MAX_CHARGE_RATE:
					case NAMEPLATE_MAX_DISCHARGE_RATE:
					case REST_CAPACITY:
					case SERIAL_NUMBER:
					case START_STOP_BATTERY_STACK:
					case START_STOP_STRING_1:
					case START_STOP_STRING_2:
					case STATE_OF_BATTERY_STACK:
					case STRING_COUNT:
					case TOTAL_CHARGE_ENERGY:
					case TOTAL_DC_CURRENT:
					case TOTAL_DC_VOLTAGE:
					case TOTAL_DISCHARGE_ENERGY:
					case TOTAL_DC_POWER:
					case WARRANTY_DATE:
					case WATCHDOG:
						return new IntegerReadChannel(ess, channelId);
					
					}
					return null;
				}) //
		).flatMap(channel -> channel);
	}
}