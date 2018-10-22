package io.openems.edge.battery.api;

import org.osgi.annotation.versioning.ProviderType;

import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.doc.Doc;
import io.openems.edge.common.channel.doc.Unit;
import io.openems.edge.common.component.OpenemsComponent;

@ProviderType
public interface Battery extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.doc.ChannelId {
		/**
		 * State of Charge
		 * 
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * <li>Unit: %
		 * <li>Range: 0..100
		 * </ul>
		 */
		SOC(new Doc().type(OpenemsType.INTEGER).unit(Unit.PERCENT)),
		/**
		 * State of Charge
		 * 
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * <li>Unit: %
		 * <li>Range: 0..100
		 * </ul>
		 */
		SOC_2(new Doc().type(OpenemsType.INTEGER).unit(Unit.PERCENT)),
		/**
		 * State of Health
		 *
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * <li>Unit: %
		 * <li>Range: 0..100
		 * </ul>
		 */
		SOH(new Doc().type(OpenemsType.INTEGER).unit(Unit.PERCENT)),
		/**
		 * State of Health
		 *
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * <li>Unit: %
		 * <li>Range: 0..100
		 * </ul>
		 */
		SOH_2(new Doc().type(OpenemsType.INTEGER).unit(Unit.PERCENT)),
		/**
		 * Max capacity
		 *
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * <li>Unit: Wh
		 * </ul>
		 */
		MAX_CAPACITY(new Doc().type(OpenemsType.INTEGER).unit(Unit.WATT_HOURS)),
		/**
		 * Min voltage for discharging
		 * 
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * <li>Unit: V
		 * </ul>
		 */
		DISCHARGE_MIN_VOLTAGE(new Doc().type(OpenemsType.INTEGER).unit(Unit.VOLT)),
		/**
		 * Max current for discharging
		 * 
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * <li>Unit: A
		 * </ul>
		 */
		DISCHARGE_MAX_CURRENT(new Doc().type(OpenemsType.INTEGER).unit(Unit.AMPERE)),
		/**
		 * Maximal voltage for charging
		 * 
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * <li>Unit: V
		 * </ul>
		 */
		CHARGE_MAX_VOLTAGE(new Doc().type(OpenemsType.INTEGER).unit(Unit.VOLT)),
		/**
		 * Max current for charging
		 * 
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * <li>Unit: A
		 * </ul>
		 */
		CHARGE_MAX_CURRENT(new Doc().type(OpenemsType.INTEGER).unit(Unit.AMPERE)),
		/**
		 * Battery Temperature
		 *
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * <li>Unit: Celsius
		 * <li>Range: (-50)..100
		 * </ul>
		 */
		BATTERY_TEMP(new Doc().type(OpenemsType.INTEGER).unit(Unit.DEGREE_CELSIUS)),
		/**
		 * Battery Temperature
		 *
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * <li>Unit: Celsius
		 * <li>Range: (-50)..100
		 * </ul>
		 */
		BATTERY_TEMP_2(new Doc().type(OpenemsType.INTEGER).unit(Unit.DEGREE_CELSIUS)),
		/**
		 * Indicates that the battery has started and is ready for charging/discharging
		 * 
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Boolean
		 * </ul>
		 */
		READY_FOR_WORKING(new Doc().type(OpenemsType.BOOLEAN)),
		
		/**
		 * Indicates the faults of battery 2
		 * 
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * </ul>
		 */
		FAULTS_2(new Doc().type(OpenemsType.INTEGER)),
		
		/**
		 * Indicates the faults of battery 1
		 * 
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * </ul>
		 */
		FAULTS_1(new Doc().type(OpenemsType.INTEGER)),
		
		/**
		 * Indicates the alarms of battery 2
		 * 
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * </ul>
		 */
		ALARMS_2(new Doc().type(OpenemsType.INTEGER)),
		
		/**
		 * Indicates the alarms of battery 1
		 * 
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * </ul>
		 */
		ALARMS_1(new Doc().type(OpenemsType.INTEGER)),
		
		/**
		 * Indicates the operating state of battery 1
		 * 
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * </ul>
		 */
		OPERATING_STATE_1(new Doc().type(OpenemsType.INTEGER)),
		
		/**
		 * Indicates the operating state of battery 2
		 * 
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * </ul>
		 */
		OPERATING_STATE_2(new Doc().type(OpenemsType.INTEGER)),
		
		/**
		 * Highest Voltage of Battery stack
		 * <ul>
		 * <li>Interface: Battery
		 * </ul>
		 */
		MAX_STRING_VOLTAGE(new Doc().type(OpenemsType.INTEGER).unit(Unit.VOLT)),
		
		/**
		 * Highest Voltage of Battery stack
		 * <ul>
		 * <li>Interface: Battery
		 * </ul>
		 */
		MIN_STRING_VOLTAGE(new Doc().type(OpenemsType.INTEGER).unit(Unit.VOLT)),
		
		/**
		 * Highest Cell Voltage of Battery 1
		 * <ul>
		 * <li>Interface: Battery
		 * </ul>
		 */
		HIGHEST_CELL_VOLTAGE_1(new Doc().type(OpenemsType.INTEGER).unit(Unit.MILLIVOLT)),

		/**
		 * Highest Cell Voltage of Battery 2
		 * <ul>
		 * <li>Interface: Battery
		 * </ul>
		 */
		HIGHEST_CELL_VOLTAGE_2(new Doc().type(OpenemsType.INTEGER).unit(Unit.MILLIVOLT)),
		
		/**
		 * Lowest Cell Voltage of Battery 1
		 * <ul>
		 * <li>Interface: Battery
		 * </ul>
		 */
		LOWEST_CELL_VOLTAGE_1(new Doc().type(OpenemsType.INTEGER).unit(Unit.MILLIVOLT)),

		/**
		 * Lowest Cell Voltage of Battery 2
		 * <ul>
		 * <li>Interface: Battery
		 * </ul>
		 */
		LOWEST_CELL_VOLTAGE_2(new Doc().type(OpenemsType.INTEGER).unit(Unit.MILLIVOLT)),
		
		/**
		 * Lowest Cell Voltage of Battery stack
		 * <ul>
		 * <li>Interface: Battery
		 * </ul>
		 */
		LOWEST_CELL_VOLTAGE_STACK(new Doc().type(OpenemsType.INTEGER).unit(Unit.MILLIVOLT)),
		
		/**
		 * Highest Cell Voltage of Battery stack
		 * <ul>
		 * <li>Interface: Battery
		 * </ul>
		 */
		HIGHEST_CELL_VOLTAGE_STACK(new Doc().type(OpenemsType.INTEGER).unit(Unit.MILLIVOLT)),
		
		/**
		 * Capacity of battery
		 * 
		 * <ul>
		 * <li>Interface: Battery
		 * <li>Type: Integer
		 * </ul>
		 */
		CAPACITY_KWH(new Doc().type(OpenemsType.INTEGER).unit(Unit.KILOWATT_HOURS)),
		;

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}
		
		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	/**
	 * Gets the State of Charge in [%], range 0..100 %
	 * 
	 * @return
	 */
	default Channel<Integer> getSoc() {
		return this.channel(ChannelId.SOC);
	}
	/**
	 * Gets the State of Charge in [%], range 0..100 %
	 * 
	 * @return
	 */
	default Channel<Integer> getSoc_2() {
		return this.channel(ChannelId.SOC_2);
	}
	/**
	 * Gets the State of Health in [%], range 0..100 %
	 *
	 * @return
	 */
	default Channel<Integer> getSoh() {
		return this.channel(ChannelId.SOH);
	}
	/**
	 * Gets the State of Health in [%], range 0..100 %
	 *
	 * @return
	 */
	default Channel<Integer> getSoh_2() {
		return this.channel(ChannelId.SOH_2);
	}
	/**
	 * Gets the maximum capacity
	 *
	 * @return
	 */
	default Channel<Integer> getMaxCapacity() {
		return this.channel(ChannelId.MAX_CAPACITY);
	}

	/**
	 * Gets the Battery Temperature in [degC], range (-50)..100
	 *
	 * @return
	 */
	default Channel<Integer> getBatteryTemp() {
		return this.channel(ChannelId.BATTERY_TEMP);
	}
	/**
	 * Gets the Battery Temperature in [degC], range (-50)..100
	 *
	 * @return
	 */
	default Channel<Integer> getBatteryTemp_2() {
		return this.channel(ChannelId.BATTERY_TEMP_2);
	}
	/**
	 * Gets the min voltage for discharging
	 * 
	 * @return
	 */
	default Channel<Integer> getDischargeMinVoltage() {
		return this.channel(ChannelId.DISCHARGE_MIN_VOLTAGE);
	}
	
	/**
	 * Gets the max current for discharging
	 * 
	 * @return
	 */
	default Channel<Integer> getDischargeMaxCurrent() {
		return this.channel(ChannelId.DISCHARGE_MAX_CURRENT);
	}
	
	/**
	 * Gets the max voltage for charging
	 * 
	 * @return
	 */
	default Channel<Integer> getChargeMaxVoltage() {
		return this.channel(ChannelId.CHARGE_MAX_VOLTAGE);
	}
	
	/**
	 * Gets the max current for charging
	 * 
	 * @return
	 */
	default Channel<Integer> getChargeMaxCurrent() {
		return this.channel(ChannelId.CHARGE_MAX_CURRENT);
	}
	
	/**
	 * Gets the indicator if ready to charge/discharge
	 * 
	 * @return
	 */
	default Channel<Boolean> getReadyForWorking() {
		return this.channel(ChannelId.READY_FOR_WORKING);
	}
	
	/**
	 * Gets the indicator about alarms of battery 1
	 * 
	 * @return
	 */
	default Channel<Integer> getAlarms_1() {
		return this.channel(ChannelId.ALARMS_1);
	}
	
	/**
	 * Gets the indicator about alarms of battery 2
	 * 
	 * @return
	 */
	default Channel<Integer> getAlarms_2() {
		return this.channel(ChannelId.ALARMS_2);
	}
	
	/**
	 * Gets the indicator about faults of battery 1
	 * 
	 * @return
	 */
	default Channel<Integer> getFaults_1() {
		return this.channel(ChannelId.FAULTS_1);
	}
	
	/**
	 * Gets the indicator about faults of battery 2
	 * 
	 * @return
	 */
	default Channel<Integer> getFaults_2() {
		return this.channel(ChannelId.FAULTS_2);
	}
	
	/**
	 * Gets the indicator about operating state of battery 1
	 * 
	 * @return
	 */
	default Channel<Integer> getOperatingState_1() {
		return this.channel(ChannelId.OPERATING_STATE_1);
	}
	
	/**
	 * Gets the indicator about operating state of battery 2
	 * 
	 * @return
	 */
	default Channel<Integer> getOperatingState_2() {
		return this.channel(ChannelId.OPERATING_STATE_2);
	}
	
	/**
	 * Gets the highest voltage of battery stack
	 * 
	 * @return
	 */
	default Channel<Integer> getMaxStringVoltage() {
		return this.channel(ChannelId.MAX_STRING_VOLTAGE);
	}
	
	/**
	 * Gets the lowest voltage of battery stack
	 * 
	 * @return
	 */
	default Channel<Integer> getMinStringVoltage() {
		return this.channel(ChannelId.MIN_STRING_VOLTAGE);
	}
	
	/**
	 * Gets the highest cell voltage of battery stack
	 * 
	 * @return
	 */
	default Channel<Integer> getHighestCellVoltage_STACK() {
		return this.channel(ChannelId.HIGHEST_CELL_VOLTAGE_STACK);
	}
	
	/**
	 * Gets the highest cell voltage of battery stack
	 * 
	 * @return
	 */
	default Channel<Integer> getLOWESTCellVoltage_STACK() {
		return this.channel(ChannelId.LOWEST_CELL_VOLTAGE_STACK);
	}
	
	/**
	 * Gets the highest cell voltage of battery 1
	 * 
	 * @return
	 */
	default Channel<Integer> getHighestCellVoltage_1() {
		return this.channel(ChannelId.HIGHEST_CELL_VOLTAGE_1);
	}
	
	/**
	 * Gets the highest cell voltage of battery 2
	 * 
	 * @return
	 */
	default Channel<Integer> getHighestCellVoltage_2() {
		return this.channel(ChannelId.HIGHEST_CELL_VOLTAGE_2);
	}
	
	/**
	 * Gets the lowest cell voltage of battery 1
	 * 
	 * @return
	 */
	default Channel<Integer> getLowestCellVoltage_1() {
		return this.channel(ChannelId.LOWEST_CELL_VOLTAGE_1);
	}
	
	/**
	 * Gets the lowest cell voltage of battery 2
	 * 
	 * @return
	 */
	default Channel<Integer> getLowestCellVoltage_2() {
		return this.channel(ChannelId.LOWEST_CELL_VOLTAGE_2);
	}
	
	/**
	 * Gets the capacity of this battery
	 * 
	 * @return
	 */
	default Channel<Integer> getCapacity() {
		return this.channel(ChannelId.CAPACITY_KWH);
	}
}
