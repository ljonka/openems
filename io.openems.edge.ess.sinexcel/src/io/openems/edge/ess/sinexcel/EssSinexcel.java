package io.openems.edge.ess.sinexcel;

import java.util.Optional;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.StringWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.doc.Doc;
import io.openems.edge.common.channel.doc.Level;
import io.openems.edge.common.channel.doc.Unit;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.power.api.Power;


@Designate(ocd = Config.class, factory = true)
@Component( //
		name = "Ess.Sinexcel", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE) //
//
public class EssSinexcel extends AbstractOpenemsModbusComponent
		implements SymmetricEss, ManagedSymmetricEss, EventHandler, OpenemsComponent {

	private final Logger log = LoggerFactory.getLogger(EssSinexcel.class);

	public static final int DEFAULT_UNIT_ID = 1;
	public static final int MAX_APPARENT_POWER = 30000; // [VA]

	@Reference
	protected ConfigurationAdmin cm;

	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.service_pid(), config.id(), config.enabled(), DEFAULT_UNIT_ID, this.cm, "Modbus",
				config.modbus_id());

	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	public EssSinexcel() {
		Utils.initializeChannels(this).forEach(channel -> this.addChannel(channel));
	}

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus); // Bridge Modbus
	}

	public enum ChannelId implements io.openems.edge.common.channel.doc.ChannelId {
		SUNSPEC_DID_0103(new Doc()),//
		
		SETDATA_MOD_ON_CMD(new Doc().unit(Unit.ON_OFF)),
		SETDATA_MOD_OFF_CMD(new Doc().unit(Unit.ON_OFF)),
		
		SETDATA_GRID_ON_CMD(new Doc().unit(Unit.ON_OFF)),
		SETDATA_GRID_OFF_CMD(new Doc().unit(Unit.ON_OFF)),
		SET_ANTI_ISLANDING(new Doc().unit(Unit.ON_OFF)),
		
		SET_CHARGE_DISCHARGE_ACTIVE(new Doc().unit(Unit.KILOWATT_HOURS)),//
		SET_CHARGE_DISCHARGE_REACTIVE(new Doc().unit(Unit.KILO_VOLT_AMPERE_REACTIVE)),//
		
		SET_CHARGE_CURRENT(new Doc().unit(Unit.AMPERE)),
		SET_DISCHARGE_CURRENT(new Doc().unit(Unit.AMPERE)),
		
		SET_SLOW_CHARGE_VOLTAGE(new Doc().unit(Unit.VOLT)),
		SET_FLOAT_CHARGE_VOLTAGE(new Doc().unit(Unit.VOLT)),
		
		SET_UPPER_VOLTAGE(new Doc().unit(Unit.VOLT)),
		SET_LOWER_VOLTAGE(new Doc().unit(Unit.VOLT)),

		SET_IP_ADDRESS_1(new Doc().unit(Unit.NONE)),
		SET_IP_ADDRESS_2(new Doc().unit(Unit.NONE)),
		SET_IP_ADDRESS_3(new Doc().unit(Unit.NONE)),
		SET_IP_ADDRESS_4(new Doc().unit(Unit.NONE)),
		
		IP_ADDRESS_1(new Doc().unit(Unit.NONE)),
		IP_ADDRESS_2(new Doc().unit(Unit.NONE)),
		IP_ADDRESS_3(new Doc().unit(Unit.NONE)),
		IP_ADDRESS_4(new Doc().unit(Unit.NONE)),
		
		ANTI_ISLANDING(new Doc().unit(Unit.ON_OFF)),

		MOD_ON_CMD(new Doc().unit(Unit.ON_OFF)), //
		MOD_OFF_CMD(new Doc().unit(Unit.ON_OFF)),//
		
		GRID_ON_CMD(new Doc().unit(Unit.ON_OFF)),// 
		GRID_OFF_CMD(new Doc().unit(Unit.ON_OFF)),//
		
		Frequency(new Doc().unit(Unit.HERTZ)),//
		Temperature(new Doc().unit(Unit.DEGREE_CELSIUS)),//
		Serial(new Doc().unit(Unit.NONE)),
		Model(new Doc().unit(Unit.NONE)),
		Manufacturer(new Doc().unit(Unit.NONE)),
		Model_2(new Doc().unit(Unit.NONE)),
		Version(new Doc().unit(Unit.NONE)),
		Serial_Number(new Doc().unit(Unit.NONE)),
		
		Analog_GridCurrent_Freq(new Doc().unit(Unit.HERTZ)),
		
		Analog_ActivePower_Rms_Value_L1(new Doc().unit(Unit.KILO_WATT)),
		Analog_ActivePower_Rms_Value_L2(new Doc().unit(Unit.KILO_WATT)),
		Analog_ActivePower_Rms_Value_L3(new Doc().unit(Unit.KILO_WATT)),
		
		Analog_ReactivePower_Rms_Value_L1(new Doc().unit(Unit.KILO_VOLT_AMPERE_REACTIVE)),
		Analog_ReactivePower_Rms_Value_L2(new Doc().unit(Unit.KILO_VOLT_AMPERE_REACTIVE)),
		Analog_ReactivePower_Rms_Value_L3(new Doc().unit(Unit.KILO_VOLT_AMPERE_REACTIVE)),
		
		Analog_ApparentPower_L1(new Doc().unit(Unit.KILO_VOLT_AMPERE)),
		Analog_ApparentPower_L2(new Doc().unit(Unit.KILO_VOLT_AMPERE)),
		Analog_ApparentPower_L3(new Doc().unit(Unit.KILO_VOLT_AMPERE)),
		
		Analog_PF_RMS_Value_L1(new Doc().unit(Unit.NONE)),
		Analog_PF_RMS_Value_L2(new Doc().unit(Unit.NONE)),
		Analog_PF_RMS_Value_L3(new Doc().unit(Unit.NONE)),
		
		Analog_ActivePower_3Phase(new Doc().unit(Unit.KILO_WATT)),
		Analog_ReactivePower_3Phase(new Doc().unit(Unit.KILO_VOLT_AMPERE_REACTIVE)),
		Analog_ApparentPower_3Phase(new Doc().unit(Unit.KILO_VOLT_AMPERE)),
		Analog_PowerFactor_3Phase(new Doc().unit(Unit.NONE)),
		
		Analog_CHARGE_Energy(new Doc().unit(Unit.KILOWATT_HOURS)),
		Analog_DISCHARGE_Energy(new Doc().unit(Unit.KILOWATT_HOURS)),
		Analog_REACTIVE_Energy(new Doc().unit(Unit.KILO_VOLT_AMPERE_REACTIVE_HOURS)),
		
		Analog_Reactive_Energy_2(new Doc().unit(Unit.KILO_VOLT_AMPERE_REACTIVE_HOURS)),
		Target_OffGrid_Voltage(new Doc().unit(Unit.NONE)),
		Target_OffGrid_Frequency(new Doc().unit(Unit.HERTZ)),
		
		Analog_DC_CHARGE_Energy(new Doc().unit(Unit.KILO_VOLT_AMPERE)),
		Analog_DC_DISCHARGE_Energy(new Doc().unit(Unit.KILO_VOLT_AMPERE)),
		
		
		AC_Apparent_Power(new Doc().unit(Unit.VOLT_AMPERE)),//
		AC_Reactive_Power(new Doc().unit(Unit.VOLT_AMPERE_REACTIVE)),//
		AC_Power(new Doc().unit(Unit.WATT)),// 
		
		InvOutVolt_L1(new Doc() .unit(Unit.VOLT)),//
		InvOutVolt_L2(new Doc() .unit(Unit.VOLT)),//
		InvOutVolt_L3(new Doc().unit(Unit.VOLT)),//
		InvOutCurrent_L1(new Doc().unit(Unit.AMPERE)),//
		InvOutCurrent_L2(new Doc() .unit(Unit.AMPERE)),//
		InvOutCurrent_L3(new Doc().unit(Unit.AMPERE)),//

		DC_Power(new Doc().unit(Unit.KILO_WATT)),//
		DC_Current(new Doc().unit(Unit.AMPERE)),//
		DC_Voltage(new Doc().unit(Unit.VOLT)),//


		EVENT_1(new Doc().unit(Unit.NONE)),//
		Sinexcel_State(new Doc().unit(Unit.NONE)),//
		
		Test_Register(new Doc().unit(Unit.NONE)),
		
		Target_Active_Power(new Doc() .unit(Unit.KILO_WATT)),//
		Target_Reactive_Power(new Doc().unit(Unit.KILO_WATT)),//
		Max_Charge_Current(new Doc() .unit(Unit.AMPERE)),
		Max_Discharge_Current(new Doc() .unit(Unit.AMPERE)),
		Lower_Voltage_Limit(new Doc().unit(Unit.VOLT)),
		Upper_Voltage_Limit(new Doc().unit(Unit.VOLT)),
//---------------------------------------STATES------------------------------------------------		
		Sinexcel_STATE_1(new Doc().level(Level.INFO).text("OFF")),//
		Sinexcel_STATE_2(new Doc().level(Level.INFO).text("Sleeping")),//
		Sinexcel_STATE_3(new Doc().level(Level.INFO).text("Starting")),//
		Sinexcel_STATE_4(new Doc().level(Level.INFO).text("MPPT")),//
		Sinexcel_STATE_5(new Doc().level(Level.INFO).text("Throttled")),//
		Sinexcel_STATE_6(new Doc().level(Level.INFO).text("Shutting down")),//
		Sinexcel_STATE_7(new Doc().level(Level.INFO).text("Fault")),//
		Sinexcel_STATE_8(new Doc().level(Level.INFO).text("Standby")),//
		Sinexcel_STATE_9(new Doc().level(Level.INFO).text("Started")),//
		
//-----------------------------------EVENT Bitfield 32-----------------------------------
		STATE_0(new Doc().level(Level.FAULT).text("Ground fault")),//
		STATE_1(new Doc().level(Level.WARNING).text("DC over Voltage")),//
		STATE_2(new Doc().level(Level.WARNING).text("AC disconnect open")),//
		STATE_3(new Doc().level(Level.WARNING).text("DC disconnect open")),//
		STATE_4(new Doc().level(Level.WARNING).text("Grid shutdown")),//
		STATE_5(new Doc().level(Level.WARNING).text("Cabinet open")),//
		STATE_6(new Doc().level(Level.WARNING).text("Manual shutdown")),//
		STATE_7(new Doc().level(Level.WARNING).text("Over temperature")),//
		STATE_8(new Doc().level(Level.WARNING).text("AC Frequency above limit")),//
		STATE_9(new Doc().level(Level.WARNING).text("AC Frequnecy under limit")),//
		STATE_10(new Doc().level(Level.WARNING).text("AC Voltage above limit")),//
		STATE_11(new Doc().level(Level.WARNING).text("AC Voltage under limit")),//
		STATE_12(new Doc().level(Level.WARNING).text("Blown String fuse on input")),//
		STATE_13(new Doc().level(Level.WARNING).text("Under temperature")), //
		STATE_14(new Doc().level(Level.WARNING).text("Generic Memory or Communication error (internal)")),//
		STATE_15(new Doc().level(Level.FAULT).text("Hardware test failure")),//
//---------------------------------------------------FAULT LIST------------------------------------------------------------
		STATE_16(new Doc().level(Level.FAULT).text("Fault Status")),//
		STATE_17(new Doc().level(Level.WARNING).text("Alert Status")),//
		STATE_18(new Doc().level(Level.INFO).text("On/Off Status")),//
		STATE_19(new Doc().level(Level.INFO).text("On Grid")),//
		STATE_20(new Doc().level(Level.INFO).text("Off Grid")),//
		STATE_21(new Doc().level(Level.WARNING).text("AC OVP")),//
		STATE_22(new Doc().level(Level.WARNING).text("AC UVP")),//
		STATE_23(new Doc().level(Level.WARNING).text("AC OFP")),//
		STATE_24(new Doc().level(Level.WARNING).text("AC UFP")),//
		STATE_25(new Doc().level(Level.WARNING).text("Grid Voltage Unbalance")),//
		STATE_26(new Doc().level(Level.WARNING).text("Grid Phase reserve")),//
		STATE_27(new Doc().level(Level.INFO).text("Islanding")),//
		STATE_28(new Doc().level(Level.WARNING).text("On/ Off Grid Switching Error")),//
		STATE_29(new Doc().level(Level.WARNING).text("Output Grounding Error")), //
		STATE_30(new Doc().level(Level.WARNING).text("Output Current Abnormal")),//
		STATE_31(new Doc().level(Level.WARNING).text("Grid Phase Lock Fails")),//
		STATE_32(new Doc().level(Level.WARNING).text("Internal Air Over-Temp")),//
		STATE_33(new Doc().level(Level.WARNING).text("Zeitüberschreitung der Netzverbindung")),//
		STATE_34(new Doc().level(Level.INFO).text("EPO")),//
		STATE_35(new Doc().level(Level.FAULT).text("HMI Parameters Fault")),//
		STATE_36(new Doc().level(Level.WARNING).text("DSP Version Error")),//
		STATE_37(new Doc().level(Level.WARNING).text("CPLD Version Error")),//
		STATE_38(new Doc().level(Level.WARNING).text("Hardware Version Error")),//
		STATE_39(new Doc().level(Level.WARNING).text("Communication Error")),//
		STATE_40(new Doc().level(Level.WARNING).text("AUX Power Error")),//
		STATE_41(new Doc().level(Level.FAULT).text("Fan Failure")),//
		STATE_42(new Doc().level(Level.WARNING).text("BUS Over Voltage")),//
		STATE_43(new Doc().level(Level.WARNING).text("BUS Low Voltage")),//
		STATE_44(new Doc().level(Level.WARNING).text("BUS Voltage Unbalanced")),//
		STATE_45(new Doc().level(Level.WARNING).text("AC Soft Start Failure")), //
		STATE_46(new Doc().level(Level.WARNING).text("Reserved")),//
		STATE_47(new Doc().level(Level.WARNING).text("Output Voltage Abnormal")),//
		STATE_48(new Doc().level(Level.WARNING).text("Output Current Unbalanced")),//
		STATE_49(new Doc().level(Level.WARNING).text("Over Temperature of Heat Sink")),//
		STATE_50(new Doc().level(Level.WARNING).text("Output Overload")),//
		STATE_51(new Doc().level(Level.WARNING).text("Reserved")),//
		STATE_52(new Doc().level(Level.WARNING).text("AC Breaker Short-Circuit")),//
		STATE_53(new Doc().level(Level.WARNING).text("Inverter Start Failure")),//
		STATE_54(new Doc().level(Level.WARNING).text("AC Breaker is open")),//
		STATE_55(new Doc().level(Level.WARNING).text("EE Reading Error 1")),//
		STATE_56(new Doc().level(Level.WARNING).text("EE Reading Error 2")),//
		STATE_57(new Doc().level(Level.FAULT).text("SPD Failure  ")),//
		STATE_58(new Doc().level(Level.WARNING).text("Inverter over load")),//
		STATE_59(new Doc().level(Level.INFO).text("DC Charging")),//
		STATE_60(new Doc().level(Level.INFO).text("DC Discharging")),//
		STATE_61(new Doc().level(Level.INFO).text("Battery fully charged")), //
		STATE_62(new Doc().level(Level.INFO).text("Battery empty")),//
		STATE_63(new Doc().level(Level.FAULT).text("Fault Status")),//
		STATE_64(new Doc().level(Level.WARNING).text("Alert Status")),//
		STATE_65(new Doc().level(Level.WARNING).text("DC input OVP")),//
		STATE_66(new Doc().level(Level.WARNING).text("DC input UVP")),//
		STATE_67(new Doc().level(Level.WARNING).text("DC Groundig Error")),//
		STATE_68(new Doc().level(Level.WARNING).text("BMS alerts")),//
		STATE_69(new Doc().level(Level.FAULT).text("DC Soft-Start failure")),//
		STATE_70(new Doc().level(Level.WARNING).text("DC relay short-circuit")),//
		STATE_71(new Doc().level(Level.WARNING).text("DC realy short open")),//
		STATE_72(new Doc().level(Level.WARNING).text("Battery power over load")),//
		STATE_73(new Doc().level(Level.FAULT).text("BUS start fails")),//
		STATE_74(new Doc().level(Level.WARNING).text("DC OCP"));//
	

//----------------------------------------------------------------------------------------------------------------------		
		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;

		}
	}
//------------------------------------------START AND STOP-------------------------------------------------
	public void startSystem() {
		IntegerWriteChannel SETDATA_ModOnCmd = this.channel(ChannelId.SETDATA_MOD_ON_CMD);
		try {
			SETDATA_ModOnCmd.setNextWriteValue(START);					//Here: START = 1

		} catch (OpenemsException e) {
			log.error("problem occurred while trying to start inverter" + e.getMessage());
		}

	}

	public void stopSystem() {
		IntegerWriteChannel SETDATA_ModOffCmd = this.channel(ChannelId.SETDATA_MOD_OFF_CMD);

		try {
			SETDATA_ModOffCmd.setNextWriteValue(STOP); 		// Here: STOP = 1
		} catch (OpenemsException e) {
			log.error("problem occurred while trying to stop system" + e.getMessage());
		}
	}

	public void doHandling_ON() {
		startSystem();
	}

	public void doHandling_OFF() {
		stopSystem();
	}
//-------------------------------------------------------ISLANDING-----------------------------------------------------------
	/**
	 * At first the PCS needs a stop command, then is required to remove the AC connection, after that the Grid OFF command.
	 */
	
	public void ISLANDING_ON() {
		IntegerWriteChannel SET_ANTI_ISLANDING = this.channel(ChannelId.SET_ANTI_ISLANDING);
		IntegerWriteChannel SETDATA_GridOffCmd = this.channel(ChannelId.SETDATA_GRID_OFF_CMD);
		
		try {
			
			SET_ANTI_ISLANDING.setNextWriteValue(DISABLED_ANTI_ISLANDING);
			SETDATA_GridOffCmd.setNextWriteValue(STOP);
		}
		catch (OpenemsException e) {
			log.error("problem occurred while trying to activate" + e.getMessage());
		}
	}

	
	/**
	 * At first the PCS needs a stop command, then is required to plug in the AC connection, after that the Grid ON command.
	 */
	public void ISLANDING_OFF() {
		IntegerWriteChannel SET_ANTI_ISLANDING = this.channel(ChannelId.SET_ANTI_ISLANDING);
		IntegerWriteChannel SETDATA_GridOnCmd = this.channel(ChannelId.SETDATA_GRID_ON_CMD);
		try {
			SET_ANTI_ISLANDING.setNextWriteValue(ENABLED_ANTI_ISLANDING);
			SETDATA_GridOnCmd.setNextWriteValue(START);
		}
		catch (OpenemsException e) {
			log.error("problem occurred while trying to deactivate islanding" + e.getMessage());
		}
	}
	
	public void doHandling_ISLANDING_ON() {
		ISLANDING_ON();
	}
	public void doHandling_ISLANDING_OFF() {
		ISLANDING_OFF();
	}
//-----------------------------------------------------MAX CHARGE AND DISCHARGE CURRENT-----------------------------------------------
	
	public void SET_CHARGE_DISCHARGE_CURRENT() {
		
		IntegerWriteChannel SET_DISCHARGE_CURRENT = this.channel(ChannelId.SET_DISCHARGE_CURRENT);
		IntegerWriteChannel SET_CHARGE_CURRENT = this.channel(ChannelId.SET_CHARGE_CURRENT);
		try {
			SET_DISCHARGE_CURRENT.setNextWriteValue(DISCHARGE_CURRENT);
			SET_CHARGE_CURRENT.setNextWriteValue(CHARGE_CURRENT);
			

		} catch (OpenemsException e) {
			log.error("problem occurred while trying to write the charge and discharge current value" + e.getMessage());
		}

	}
	
	public void doHandling_CHARGE_DISCHARGE_CURRENT() {
		SET_CHARGE_DISCHARGE_CURRENT();
	}
	
	public void IP_ADDRESS() {
		IntegerWriteChannel SET_IP_ADDRESS_1 = this.channel(ChannelId.SET_IP_ADDRESS_1);
		IntegerWriteChannel SET_IP_ADDRESS_2 = this.channel(ChannelId.SET_IP_ADDRESS_2);
		IntegerWriteChannel SET_IP_ADDRESS_3 = this.channel(ChannelId.SET_IP_ADDRESS_3);
		IntegerWriteChannel SET_IP_ADDRESS_4 = this.channel(ChannelId.SET_IP_ADDRESS_4);
		try {
			SET_IP_ADDRESS_1.setNextWriteValue(ADDRESS_1);
			SET_IP_ADDRESS_2.setNextWriteValue(ADDRESS_2);
			SET_IP_ADDRESS_3.setNextWriteValue(ADDRESS_3);
			SET_IP_ADDRESS_4.setNextWriteValue(ADDRESS_4);
		}
		catch (OpenemsException e) {
			log.error("problem occurred while trying to write the IP Address" + e.getMessage());
		}
	}
	public void doHandling_IP_ADDRESS() {
		IP_ADDRESS();
	}
//---------------------------------------------SET UPPER/LOWER BATTERY VOLTAGE --------------------------------------------	
	public void SET_UPPER_LOWER_BATTERY_VOLTAGE() {
		
		IntegerWriteChannel SET_LOWER_VOLTAGE = this.channel(ChannelId.SET_LOWER_VOLTAGE);
		IntegerWriteChannel SET_UPPER_VOLTAGE = this.channel(ChannelId.SET_UPPER_VOLTAGE);
		IntegerWriteChannel SET_SLOW_CHARGE_VOLTAGE = this.channel(ChannelId.SET_SLOW_CHARGE_VOLTAGE);
		IntegerWriteChannel SET_FLOAT_CHARGE_VOLTAGE = this.channel(ChannelId.SET_FLOAT_CHARGE_VOLTAGE);
		try {
			SET_UPPER_VOLTAGE.setNextWriteValue(UPPER_BAT_VOLTAGE);
			SET_LOWER_VOLTAGE.setNextWriteValue(LOWER_BAT_VOLTAGE);
			SET_SLOW_CHARGE_VOLTAGE.setNextWriteValue(SLOW_CHARGE_VOLTAGE);
			SET_FLOAT_CHARGE_VOLTAGE.setNextWriteValue(FLOAT_CHARGE_VOLTAGE);
			

		} catch (OpenemsException e) {
			log.error("problem occurred while trying to write the voltage limits" + e.getMessage());
		}

	}
	
	public void doHandling_UPPER_LOWER_VOLTAGE() {
		SET_UPPER_LOWER_BATTERY_VOLTAGE();
	}
	
//------------------------------------------------------GET VALUE--------------------------------------------------
	public boolean Fault_Islanding() {
		StateChannel i = this.channel(ChannelId.STATE_4);
		Optional<Boolean> islanding = i.getNextValue().asOptional();
		return islanding.isPresent() && islanding.get();
	}
	
//------------------------------------------------------------------------------------------------------------------	
	
	@Override
	protected ModbusProtocol defineModbusProtocol() {
		return new ModbusProtocol(this, //
//------------------------------------------------------------WRITE-----------------------------------------------------------
				new FC6WriteRegisterTask(0x028A, 
						m(EssSinexcel.ChannelId.SETDATA_MOD_ON_CMD, new UnsignedWordElement(0x028A))), // Start// SETDATA_ModOnCmd				
				new FC6WriteRegisterTask(0x028B, 
						m(EssSinexcel.ChannelId.SETDATA_MOD_OFF_CMD, new UnsignedWordElement(0x028B))), // Stop// SETDATA_ModOffCmd
																													
				new FC6WriteRegisterTask(0x028D, 
						m(EssSinexcel.ChannelId.SETDATA_GRID_ON_CMD, new UnsignedWordElement(0x028D))), // Start// SETDATA_GridOnCmd
				new FC6WriteRegisterTask(0x028E, 
						m(EssSinexcel.ChannelId.SETDATA_GRID_OFF_CMD, new UnsignedWordElement(0x028E))), // Stop// SETDATA_GridOffCmd
				
				new FC6WriteRegisterTask(0x0087, 
						m(EssSinexcel.ChannelId.SET_CHARGE_DISCHARGE_ACTIVE, new SignedWordElement(0x0087))), // Target ACTIVE Power //Line65
				new FC6WriteRegisterTask(0x0088, 
						m(EssSinexcel.ChannelId.SET_CHARGE_DISCHARGE_REACTIVE, new SignedWordElement(0x0088))), // Target ACTIVE Power //Line65
				
				new FC6WriteRegisterTask(0x032B, 
						m(EssSinexcel.ChannelId.SET_CHARGE_CURRENT, new UnsignedWordElement(0x032B))), // MAX_CHARGING_CURRENT //Line217
				new FC6WriteRegisterTask(0x032C, 
						m(EssSinexcel.ChannelId.SET_DISCHARGE_CURRENT, new UnsignedWordElement(0x032C))), // MAX_DISCHARGING_CURRENT //Line218
				
				new FC6WriteRegisterTask(0x0329, 
						m(EssSinexcel.ChannelId.SET_SLOW_CHARGE_VOLTAGE, new UnsignedWordElement(0x0329))), // Slow charge Voltage // Line215 
				new FC6WriteRegisterTask(0x0328, 
						m(EssSinexcel.ChannelId.SET_FLOAT_CHARGE_VOLTAGE, new UnsignedWordElement(0x0328))),
				
				new FC6WriteRegisterTask(0x032E, 
						m(EssSinexcel.ChannelId.SET_UPPER_VOLTAGE, new UnsignedWordElement(0x032E))), // Upper voltage limit of battery protection //Line220
				new FC6WriteRegisterTask(0x032D, 
						m(EssSinexcel.ChannelId.SET_LOWER_VOLTAGE, new UnsignedWordElement(0x032D))), // LOWER voltage limit of battery protection //Line219
				
				new FC6WriteRegisterTask(0x0316, 
						m(EssSinexcel.ChannelId.SET_ANTI_ISLANDING, new UnsignedWordElement(0x0316))), // Line194
				
				new FC6WriteRegisterTask(0x012C, 
						m(EssSinexcel.ChannelId.SET_IP_ADDRESS_1, new UnsignedWordElement(0x012C))),
				new FC6WriteRegisterTask(0x012D, 
						m(EssSinexcel.ChannelId.SET_IP_ADDRESS_2, new UnsignedWordElement(0x012D))),
				new FC6WriteRegisterTask(0x012E, 
						m(EssSinexcel.ChannelId.SET_IP_ADDRESS_3, new UnsignedWordElement(0x012E))),
				new FC6WriteRegisterTask(0x012F, 
						m(EssSinexcel.ChannelId.SET_IP_ADDRESS_4, new UnsignedWordElement(0x012F))),
				
//----------------------------------------------------------READ------------------------------------------------------
				new FC3ReadRegistersTask(0x024A, Priority.HIGH, //
						m(EssSinexcel.ChannelId.Frequency, new SignedWordElement(0x024A),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),						// int16	//Line 132 // Magnification = 100
				
				new FC3ReadRegistersTask(0x0084, Priority.HIGH, //
						m(EssSinexcel.ChannelId.Temperature, new SignedWordElement(0x0084))), 					// int16 // Line 62	Magnification = 0
			
				new FC3ReadRegistersTask(0x0260, Priority.HIGH,
						m(EssSinexcel.ChannelId.Sinexcel_State, new UnsignedWordElement(0x0260))),
				
				new FC3ReadRegistersTask(0x0011, Priority.LOW,
						m(EssSinexcel.ChannelId.Serial, new StringWordElement(0x0011, 8))),
				new FC3ReadRegistersTask(0x0001, Priority.LOW,
						m(EssSinexcel.ChannelId.Model, new StringWordElement(0x0001, 16))),
				new FC3ReadRegistersTask(0x01F8, Priority.LOW,
						m(EssSinexcel.ChannelId.Manufacturer, new StringWordElement(0x01F8, 16))),				//String // Line109
				new FC3ReadRegistersTask(0x0208, Priority.LOW,
						m(EssSinexcel.ChannelId.Model_2, new StringWordElement(0x0208, 16))),						//String (32Char) // line110
				new FC3ReadRegistersTask(0x0220, Priority.LOW,
						m(EssSinexcel.ChannelId.Version, new StringWordElement(0x0220, 8))),						//String (16Char) // Line112
				new FC3ReadRegistersTask(0x0228, Priority.LOW,
						m(EssSinexcel.ChannelId.Serial_Number, new StringWordElement(0x0228, 16))),				//String (32Char) // Line113
				
				new FC3ReadRegistersTask(0x006B, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_GridCurrent_Freq, new UnsignedWordElement(0x006B),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_1)),										// 10
				
				new FC3ReadRegistersTask(0x006E, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_ActivePower_Rms_Value_L1, new SignedWordElement(0x006E),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),										//L1 // kW //100
				new FC3ReadRegistersTask(0x006F, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_ActivePower_Rms_Value_L2, new SignedWordElement(0x006F),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),										//L2 // kW // 100
				new FC3ReadRegistersTask(0x0070, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_ActivePower_Rms_Value_L3, new SignedWordElement(0x0070),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),										//L3 // kW // 100
				
				new FC3ReadRegistersTask(0x0071, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_ReactivePower_Rms_Value_L1, new SignedWordElement(0x0071),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),										//L1 // kVAr // 100
				new FC3ReadRegistersTask(0x0072, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_ReactivePower_Rms_Value_L2, new SignedWordElement(0x0072),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),										//L2 // kVAr // 100
				new FC3ReadRegistersTask(0x0073, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_ReactivePower_Rms_Value_L3, new SignedWordElement(0x0073),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),										//L3 // kVAr // 100
				
				new FC3ReadRegistersTask(0x0074, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_ApparentPower_L1, new SignedWordElement(0x0074),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),										//L1 // kVA // 100
				new FC3ReadRegistersTask(0x0075, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_ApparentPower_L2, new SignedWordElement(0x0075),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),										//L2 // kVA // 100
				new FC3ReadRegistersTask(0x0076, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_ApparentPower_L3, new SignedWordElement(0x0076),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),										//L3 // kVA // 100
				
				new FC3ReadRegistersTask(0x0077, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_PF_RMS_Value_L1, new SignedWordElement(0x0077),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),										// 100
				new FC3ReadRegistersTask(0x0078, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_PF_RMS_Value_L2, new SignedWordElement(0x0078),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),										// 100
				new FC3ReadRegistersTask(0x0079, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_PF_RMS_Value_L3, new SignedWordElement(0x0079),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),										// 100
				
				new FC3ReadRegistersTask(0x007A, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_ActivePower_3Phase, new SignedWordElement(0x007A),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),				// 1 
				new FC3ReadRegistersTask(0x007B, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_ReactivePower_3Phase, new SignedWordElement(0x007B))),			// 1
				new FC3ReadRegistersTask(0x007C, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_ApparentPower_3Phase, new UnsignedWordElement(0x007C))),			// 1
				new FC3ReadRegistersTask(0x007D, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_PowerFactor_3Phase, new SignedWordElement(0x007D))),				// 1
				
				new FC3ReadRegistersTask(0x007E, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_CHARGE_Energy, new UnsignedDoublewordElement(0x007E))),				// 1
				new FC3ReadRegistersTask(0x0080, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_DISCHARGE_Energy, new UnsignedDoublewordElement(0x0080))),			// 1
				new FC3ReadRegistersTask(0x0082, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_REACTIVE_Energy, new UnsignedDoublewordElement(0x0082))),			// 1
				
				new FC3ReadRegistersTask(0x0082, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_Reactive_Energy_2, new UnsignedDoublewordElement(0x0082))),			//1 //Line61
				
				new FC3ReadRegistersTask(0x0089, Priority.LOW,
						m(EssSinexcel.ChannelId.Target_OffGrid_Voltage, new UnsignedWordElement(0x0089))),					//Range: -0,1 ... 0,1 (to rated Voltage) // 100
				new FC3ReadRegistersTask(0x008A, Priority.LOW,
						m(EssSinexcel.ChannelId.Target_OffGrid_Frequency, new SignedWordElement(0x008A))),						//Range: -2 ... 2Hz		//100
				
				new FC3ReadRegistersTask(0x0090, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_DC_CHARGE_Energy, new UnsignedDoublewordElement(0x0090))),			//1
				new FC3ReadRegistersTask(0x0092, Priority.HIGH,
						m(EssSinexcel.ChannelId.Analog_DC_DISCHARGE_Energy, new UnsignedDoublewordElement(0x0092))),		//1
//----------------------------------------------------------START and STOP--------------------------------------------------------------------				
//				new FC3ReadRegistersTask(0x023A, Priority.LOW, //
//						m(EssSinexcel.ChannelId.SUNSPEC_DID_0103, new UnsignedWordElement(0x023A))), //
//
				new FC3ReadRegistersTask(0x028A, Priority.LOW,
						m(EssSinexcel.ChannelId.MOD_ON_CMD, new UnsignedWordElement(0x028A))),

				new FC3ReadRegistersTask(0x028B, Priority.LOW,
						m(EssSinexcel.ChannelId.MOD_OFF_CMD, new UnsignedWordElement(0x028B))),

				new FC3ReadRegistersTask(0x028D, Priority.LOW,
						m(EssSinexcel.ChannelId.GRID_ON_CMD, new UnsignedWordElement(0x028D))),

				new FC3ReadRegistersTask(0x028E, Priority.LOW,
						m(EssSinexcel.ChannelId.GRID_OFF_CMD, new UnsignedWordElement(0x028E))),
				
				new FC3ReadRegistersTask(0x0316, Priority.LOW,
						m(EssSinexcel.ChannelId.ANTI_ISLANDING, new UnsignedWordElement(0x0316))),
				

//-----------------------------------------DC Parameter-----------------------------------------------------------------
				new FC3ReadRegistersTask(0x008D, Priority.HIGH,
						m(EssSinexcel.ChannelId.DC_Power, new SignedWordElement(0x008D),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),				// int16 // Line69 // Magnification = 100
				
				new FC3ReadRegistersTask(0x0255, Priority.HIGH,
						m(EssSinexcel.ChannelId.DC_Current, new SignedWordElement(0x0255),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_1)),			// int16 // Line142 // Magnification = 10
				
				new FC3ReadRegistersTask(0x0257, Priority.HIGH, //
						m(EssSinexcel.ChannelId.DC_Voltage, new UnsignedWordElement(0x0257),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_1)), 			// NennSpannung // uint16 // Line144 // Magnification = 10
				
//-----------------------------------------AC Parameter-----------------------------------------------------------------
				new FC3ReadRegistersTask(0x0065, Priority.HIGH, //
						m(EssSinexcel.ChannelId.InvOutVolt_L1, new UnsignedWordElement(0x0065),
							ElementToChannelConverter.SCALE_FACTOR_MINUS_1)), 									//	uint16 // Line36 // Magnification = 10//				
				new FC3ReadRegistersTask(0x0066, Priority.HIGH, //
						m(EssSinexcel.ChannelId.InvOutVolt_L2, new UnsignedWordElement(0x0066),
							ElementToChannelConverter.SCALE_FACTOR_MINUS_1)),				 					//	uint16 // Line37 // Magnification = 10//
				new FC3ReadRegistersTask(0x0067, Priority.HIGH, //
						m(EssSinexcel.ChannelId.InvOutVolt_L3, new UnsignedWordElement(0x0067),
							ElementToChannelConverter.SCALE_FACTOR_MINUS_1)), 				 					//	uint16 // Line38 // Magnification = 10
				
				new FC3ReadRegistersTask(0x0068, Priority.HIGH, //
						m(EssSinexcel.ChannelId.InvOutCurrent_L1, new UnsignedWordElement(0x0068),
							ElementToChannelConverter.SCALE_FACTOR_MINUS_1)), 			// uint16 // Line39// Magnification = 10																			
				new FC3ReadRegistersTask(0x0069, Priority.HIGH, //
						m(EssSinexcel.ChannelId.InvOutCurrent_L2, new UnsignedWordElement(0x0069),
							ElementToChannelConverter.SCALE_FACTOR_MINUS_1)), 			// uint16 // Line40// Magnification= 10												
				new FC3ReadRegistersTask(0x006A, Priority.HIGH, //
						m(EssSinexcel.ChannelId.InvOutCurrent_L3, new UnsignedWordElement(0x006A),
							ElementToChannelConverter.SCALE_FACTOR_MINUS_1)),			// uint16 // Line41// Magnification= 10
				
				new FC3ReadRegistersTask(0x0248, Priority.HIGH, //
					m(EssSinexcel.ChannelId.AC_Power, new SignedWordElement(0x0248))), 						//	int16 // Line130 // Magnification = 0
				
				new FC3ReadRegistersTask(0x024C, Priority.HIGH, //
					m(EssSinexcel.ChannelId.AC_Apparent_Power, new SignedWordElement(0x024C))), 			//	int16 // Line134 // Magnification = 0//

				new FC3ReadRegistersTask(0x024E, Priority.HIGH, //
					m(EssSinexcel.ChannelId.AC_Reactive_Power, new SignedWordElement(0x024E))), 			// int16 // Line136 // Magnification = 0


//-----------------------------------------EVENT Bitfield 32------------------------------------------------------------		
				new FC3ReadRegistersTask(0x0262, Priority.LOW, //
						bm(new UnsignedWordElement(0x0262)) //
								.m(EssSinexcel.ChannelId.STATE_0, 0) //
								.m(EssSinexcel.ChannelId.STATE_1, 1) //
								.m(EssSinexcel.ChannelId.STATE_2, 2) //
								.m(EssSinexcel.ChannelId.STATE_3, 3) //
								.m(EssSinexcel.ChannelId.STATE_4, 4) // Grid shutdown
								.m(EssSinexcel.ChannelId.STATE_5, 5) //
								.m(EssSinexcel.ChannelId.STATE_6, 6) //
								.m(EssSinexcel.ChannelId.STATE_7, 7) //
								.m(EssSinexcel.ChannelId.STATE_8, 8) //
								.m(EssSinexcel.ChannelId.STATE_9, 9) //
								.m(EssSinexcel.ChannelId.STATE_10, 10) //
								.m(EssSinexcel.ChannelId.STATE_11, 11) //
								.m(EssSinexcel.ChannelId.STATE_12, 12) //
								.m(EssSinexcel.ChannelId.STATE_13, 13) //
								.m(EssSinexcel.ChannelId.STATE_14, 14) //
								.m(EssSinexcel.ChannelId.STATE_15, 15) //
								.build()), //
//---------------------------------------------STATES---------------------------------------------------------
				new FC3ReadRegistersTask(0x0260, Priority.LOW, //
						bm(new UnsignedWordElement(0x0260)) //
								 
								.m(EssSinexcel.ChannelId.Sinexcel_STATE_1, 1) //
								.m(EssSinexcel.ChannelId.Sinexcel_STATE_2, 2) //
								.m(EssSinexcel.ChannelId.Sinexcel_STATE_3, 3) //
								.m(EssSinexcel.ChannelId.Sinexcel_STATE_4, 4) //
								.m(EssSinexcel.ChannelId.Sinexcel_STATE_5, 5) //
								.m(EssSinexcel.ChannelId.Sinexcel_STATE_6, 6) //
								.m(EssSinexcel.ChannelId.Sinexcel_STATE_7, 7) //
								.m(EssSinexcel.ChannelId.Sinexcel_STATE_8, 8) //
								.m(EssSinexcel.ChannelId.Sinexcel_STATE_9, 9) //
								.build()), //
//---------------------------------------------------FAULT LIST--------------------------------------------------------------------
				new FC3ReadRegistersTask(0x0020, Priority.LOW, //
						bm(new UnsignedWordElement(0x0020)) //
								.m(EssSinexcel.ChannelId.STATE_16, 0) //
								.m(EssSinexcel.ChannelId.STATE_17, 1) //
								.m(EssSinexcel.ChannelId.STATE_18, 2) //
								.m(EssSinexcel.ChannelId.STATE_19, 3) //
								.m(EssSinexcel.ChannelId.STATE_20, 4) //
								.build()), //
				
				new FC3ReadRegistersTask(0x0024, Priority.LOW, //
						bm(new UnsignedWordElement(0x0024)) //
								.m(EssSinexcel.ChannelId.STATE_21, 0) //
								.m(EssSinexcel.ChannelId.STATE_22, 1) //
								.m(EssSinexcel.ChannelId.STATE_23, 2) //
								.m(EssSinexcel.ChannelId.STATE_24, 3) //
								.m(EssSinexcel.ChannelId.STATE_25, 4) //
								.m(EssSinexcel.ChannelId.STATE_26, 5) //
								.m(EssSinexcel.ChannelId.STATE_27, 6) //
								.m(EssSinexcel.ChannelId.STATE_28, 7) //
								.m(EssSinexcel.ChannelId.STATE_29, 8) //
								.m(EssSinexcel.ChannelId.STATE_30, 9) //
								.m(EssSinexcel.ChannelId.STATE_31, 10) //
								.m(EssSinexcel.ChannelId.STATE_32, 11) //
								.m(EssSinexcel.ChannelId.STATE_33, 12) //
								.build()), //
				
				new FC3ReadRegistersTask(0x0025, Priority.LOW, //
						bm(new UnsignedWordElement(0x0025)) //
								.m(EssSinexcel.ChannelId.STATE_34, 0) //
								.m(EssSinexcel.ChannelId.STATE_35, 1) //
								.m(EssSinexcel.ChannelId.STATE_36, 2) //
								.m(EssSinexcel.ChannelId.STATE_37, 3) //
								.m(EssSinexcel.ChannelId.STATE_38, 4) //
								.m(EssSinexcel.ChannelId.STATE_39, 5) //
								.m(EssSinexcel.ChannelId.STATE_40, 6) //
								.m(EssSinexcel.ChannelId.STATE_41, 7) //
								.m(EssSinexcel.ChannelId.STATE_42, 8) //
								.m(EssSinexcel.ChannelId.STATE_43, 9) //
								.m(EssSinexcel.ChannelId.STATE_44, 10) //
								.m(EssSinexcel.ChannelId.STATE_45, 11) //
								.m(EssSinexcel.ChannelId.STATE_47, 13) //
								.m(EssSinexcel.ChannelId.STATE_48, 14) //
								.m(EssSinexcel.ChannelId.STATE_49, 15) //
								.build()), //
				
				new FC3ReadRegistersTask(0x0026, Priority.LOW, //
						bm(new UnsignedWordElement(0x0026)) //
								.m(EssSinexcel.ChannelId.STATE_50, 0) //
								.m(EssSinexcel.ChannelId.STATE_52, 2) //
								.m(EssSinexcel.ChannelId.STATE_53, 3) //
								.m(EssSinexcel.ChannelId.STATE_54, 4) //
								.build()), //
				
				new FC3ReadRegistersTask(0x0027, Priority.LOW, //
						bm(new UnsignedWordElement(0x0027)) //
								.m(EssSinexcel.ChannelId.STATE_55, 0) //
								.m(EssSinexcel.ChannelId.STATE_56, 1) // 
								.m(EssSinexcel.ChannelId.STATE_57, 2) //
								.m(EssSinexcel.ChannelId.STATE_58, 3) //
								.build()), //
				
				new FC3ReadRegistersTask(0x0028, Priority.LOW, //
						bm(new UnsignedWordElement(0x0028)) //
								.m(EssSinexcel.ChannelId.STATE_59, 0) //
								.m(EssSinexcel.ChannelId.STATE_60, 1) // 
								.m(EssSinexcel.ChannelId.STATE_61, 2) //
								.m(EssSinexcel.ChannelId.STATE_62, 3) //
								.m(EssSinexcel.ChannelId.STATE_63, 4) //
								.m(EssSinexcel.ChannelId.STATE_64, 5) //
								.build()), //
				
				new FC3ReadRegistersTask(0x002B, Priority.LOW, //
						bm(new UnsignedWordElement(0x002B)) //
								.m(EssSinexcel.ChannelId.STATE_65, 0) //
								.m(EssSinexcel.ChannelId.STATE_66, 1) // 
								.m(EssSinexcel.ChannelId.STATE_67, 2) //
								.m(EssSinexcel.ChannelId.STATE_68, 3) //
								.build()), //
				
				new FC3ReadRegistersTask(0x002C, Priority.LOW, //
						bm(new UnsignedWordElement(0x002C)) //
								.m(EssSinexcel.ChannelId.STATE_69, 0) //
								.m(EssSinexcel.ChannelId.STATE_70, 1) // 
								.m(EssSinexcel.ChannelId.STATE_71, 2) //
								.m(EssSinexcel.ChannelId.STATE_72, 3) //
								.m(EssSinexcel.ChannelId.STATE_73, 4) //
								.build()), //
				
				new FC3ReadRegistersTask(0x002F, Priority.LOW, //
						bm(new UnsignedWordElement(0x002F)) //
								.m(EssSinexcel.ChannelId.STATE_74, 0) //
								.build()),//
//----------------------------------------------------GENERAL SETTINGS--------------------------------------------------------------
				new FC3ReadRegistersTask(0x0087, Priority.LOW, //
						m(EssSinexcel.ChannelId.Target_Active_Power, new SignedWordElement(0x0087),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_1)), 					// int 16 // Line65 // Magnification = 10																				
				new FC3ReadRegistersTask(0x0088, Priority.LOW, //
						m(EssSinexcel.ChannelId.Target_Reactive_Power, new SignedWordElement(0x0088),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_1)),
				
//				new FC3ReadRegistersTask(0x032C, Priority.LOW, //
//						m(EssSinexcel.ChannelId.Max_Discharge_Current, new UnsignedWordElement(0x032C),
//								ElementToChannelConverter.SCALE_FACTOR_MINUS_1)),				// uint 16 // Line217 // Magnifiaction = 10
//				new FC3ReadRegistersTask(0x032B, Priority.LOW, //
//						m(EssSinexcel.ChannelId.Max_Charge_Current, new UnsignedWordElement(0x032B),
//								ElementToChannelConverter.SCALE_FACTOR_MINUS_1)),					// uint 16 // Line217 // Magnifiaction = 10
				
				new FC3ReadRegistersTask(0x032D, Priority.LOW, //
						m(EssSinexcel.ChannelId.Lower_Voltage_Limit, new UnsignedWordElement(0x032D),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_1)),					//uint 16 // Line219 // 10
				new FC3ReadRegistersTask(0x032E, Priority.LOW, //
						m(EssSinexcel.ChannelId.Upper_Voltage_Limit, new UnsignedWordElement(0x032E),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_1)),					//uint16 // line220 // 10
				
//				new FC3ReadRegistersTask(0x02EE, Priority.HIGH, //
//						m(EssSinexcel.ChannelId.Test_Register, new UnsignedWordElement(0x02EE)))				// TESTOBJEKT
				
				new FC3ReadRegistersTask(0x012C, Priority.HIGH, //
						m(EssSinexcel.ChannelId.IP_ADDRESS_1, new UnsignedWordElement(0x012C))),
				new FC3ReadRegistersTask(0x012D, Priority.HIGH, //
						m(EssSinexcel.ChannelId.IP_ADDRESS_2, new UnsignedWordElement(0x012D))),
				new FC3ReadRegistersTask(0x012E, Priority.HIGH, //
						m(EssSinexcel.ChannelId.IP_ADDRESS_3, new UnsignedWordElement(0x012E))),
				new FC3ReadRegistersTask(0x012F, Priority.HIGH, //
						m(EssSinexcel.ChannelId.IP_ADDRESS_4, new UnsignedWordElement(0x012F)))
		);
	

	}
//------------------------------------------------------------------------------------------------------------------------
	private void LIMITS() {					//Watch KACO initialize
		doHandling_UPPER_LOWER_VOLTAGE();
		doHandling_CHARGE_DISCHARGE_CURRENT();
		
	}
	
	
	@Override
	public void applyPower(int activePower, int reactivePower) {
		
		IntegerWriteChannel SET_ACTIVE_POWER = this.channel(ChannelId.SET_CHARGE_DISCHARGE_ACTIVE);
		IntegerWriteChannel SET_REACTIVE_POWER = this.channel(ChannelId.SET_CHARGE_DISCHARGE_REACTIVE);
		
		int reactiveValue = (int)((reactivePower/100));
		if((reactiveValue < MAX_REACTIVE_POWER) && (reactiveValue > (MAX_REACTIVE_POWER*(-1)))) {
			try {
				SET_REACTIVE_POWER.setNextWriteValue(reactiveValue);
			}
			catch (OpenemsException e) {
				log.error("EssSinexcel.applyPower(): Problem occurred while trying so set reactive power" + e.getMessage());
			    }
		}
		else {
			reactiveValue = 0;
			log.error("Reactive power limit exceeded");
		}
		
		
		int activeValue = (int) ((activePower/100));
		if((activeValue < MAX_ACTIVE_POWER) && (activeValue > (MAX_ACTIVE_POWER*(-1)))) {
			try {
				SET_ACTIVE_POWER.setNextWriteValue(activeValue);
			}
			catch (OpenemsException e) {
				log.error("EssSinexcel.applyPower(): Problem occurred while trying so set active power" + e.getMessage());
			    }
		}
		else {
			activeValue = 0;
			log.error("active power limit exceeded");
		}
		
	}

	
	
/**
 * Example: Value 3000 means 300; Value 3001 means 300,1
 */
	
	@Reference
	private Power power;
	
	private int ADDRESS_1 = 192;
	private int ADDRESS_2 = 168;
	private int ADDRESS_3 = 1;
	private int ADDRESS_4 = 11;
	
	private int MAX_REACTIVE_POWER = 300;	// 30 kW
	private int MAX_ACTIVE_POWER = 300;	// 30 kW
	
	private int DISABLED_ANTI_ISLANDING = 0;
	private int ENABLED_ANTI_ISLANDING = 1;
	private int START = 1;
	private int STOP = 1;
	
	private int SLOW_CHARGE_VOLTAGE = 3900;		// Slow and Float Charge Voltage must be the same for the Lithium Ion battery. 
	private int FLOAT_CHARGE_VOLTAGE = 3900;		
	
	private int LOWER_BAT_VOLTAGE = 2900;
	private int UPPER_BAT_VOLTAGE = 3950;
	
	private int CHARGE_CURRENT = 900;				// [CHARGE_CURRENT] = A // Range = 0 A ... 90 A
	private int DISCHARGE_CURRENT = 900;			// [DISCHARGE_CURRENT] = A	// Range = 0 A ... 90 A
//	private int ACTIVE = 0;							// [ACTIVE] = kW	// Range = -30 kW ... 30 kW	// ACTIVE < 0 -> CHARGE //	ACTIVE > 0 ->DISCHARGE 
//	private int REACTIVE = 0;						// [REACTIVE] = kVAr	// Range = -30 kW ... 30 //REACTIVE < 0 -> inductive // REACTIVE > 0 -> capacitive 
	
	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		
		
//	boolean island = Fault_Islanding();
		
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE:
			doHandling_ON();
			LIMITS();
//			doHandling_IP_ADDRESS();
			
//			if(island = true) {
//				doHandling_ISLANDING_ON();
//			}
//			else if(island = false) {
//				doHandling_ISLANDING_OFF();
//			}
			
			break;
		}
	}

	@Override
	public Power getPower() {					//Siehe KACO
		return this.power;		
	}

	@Override								// Leistungsstufen des Wechselrichters
	public int getPowerPrecision() {
		return (int) (MAX_ACTIVE_POWER*0.02);
	}
	

}
