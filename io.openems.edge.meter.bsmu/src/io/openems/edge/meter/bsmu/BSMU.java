package io.openems.edge.meter.bsmu;

import java.util.Collection;
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
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.doc.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.common.channel.doc.Unit;
import org.osgi.service.metatype.annotations.Designate;


@Designate( ocd=Config.class, factory=true)
@Component(name="io.openems.edge.meter.bsmu",
			immediate = true,
			configurationPolicy = ConfigurationPolicy.REQUIRE)

public class BSMU extends AbstractOpenemsModbusComponent implements Battery, OpenemsComponent, EventHandler {

	private final Logger log = LoggerFactory.getLogger(BSMU.class);
	public static final int DEFAULT_UNIT_ID = 5;
	
	private BatteryState batteryState;
	private Battery battery;
	private String modbusBridgeId;
	public int Start = 1;
	public int Stop = 0;
	private boolean END_CHARGE_1 = END_OF_CHARGE_1();
	private boolean END_CHARGE_2 = END_OF_CHARGE_2();

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setBattery(Battery battery) {
		this.battery = battery;
	}

	public BSMU() {
		Utils.initializeChannels(this).forEach(channel -> this.addChannel(channel));
	}
	
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}
	
	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.service_pid(), config.id(), config.enabled(), config.modbusUnitId(), this.cm, "Modbus",
				config.modbus_id());
		this.modbusBridgeId = config.modbus_id();
		this.batteryState = config.batteryState();
		
		this.channel(ChannelId.USER_SOC_1).onChange(value -> { this.channel(Battery.ChannelId.SOC).setNextValue(value); });	// USER_SOC wird in Battery.SOC übergeben.
		this.channel(ChannelId.USER_SOC_2).onChange(value -> { this.channel(Battery.ChannelId.SOC_2).setNextValue(value); });	// Im Sinexcel muss Battery aufgerufen werden.
		this.channel(ChannelId.HV_BAT_HEALTH_1).onChange(value -> { this.channel(Battery.ChannelId.SOH).setNextValue(value); });
		this.channel(ChannelId.HV_BAT_HEALTH_2).onChange(value -> { this.channel(Battery.ChannelId.SOH_2).setNextValue(value); });
		this.channel(ChannelId.HV_BAT_TEMP_1).onChange(value -> { this.channel(Battery.ChannelId.BATTERY_TEMP).setNextValue(value); });
		this.channel(ChannelId.HV_BAT_TEMP_2).onChange(value -> { this.channel(Battery.ChannelId.BATTERY_TEMP_2).setNextValue(value); });
		
	}																														

	@Deactivate
	protected
	void deactivate() {
		super.deactivate();
	}

	@Reference
	protected ConfigurationAdmin cm;
private void HandleBatteryState() {
	
	if((END_CHARGE_1 = true) && (END_CHARGE_2 = true)) {
		stopBAT_1();
		stopBAT_2();
	}
	else if((END_CHARGE_1 = true) && (END_CHARGE_2 = false)) {
		stopBAT_1();
	}
	else if((END_CHARGE_2 = true) && (END_CHARGE_1 = false)) {
		stopBAT_2();
	}
	else {
		startBAT_1();
		startBAT_2();
	}
	
	
	switch (this.batteryState) {
	case OFF:
		stopBAT_1();
		stopBAT_2();
		break;
	case ON:
		startBAT_1();
		startBAT_2();
		break;
	}
}

private void startBAT_1() {
	IntegerWriteChannel SET_START_STOP_1 = this.channel(ChannelId.SET_START_STOP_STRING_1);

	try {
	SET_START_STOP_1.setNextWriteValue(Start);
	} 

	catch (OpenemsException e) {
	log.error("Error while trying to start string 1\n" + e.getMessage());
	}
}
private void startBAT_2() {
	IntegerWriteChannel SET_START_STOP_2 = this.channel(ChannelId.SET_START_STOP_STRING_2);

	try {
	SET_START_STOP_2.setNextWriteValue(Start);
	} 

	catch (OpenemsException e) {
	log.error("Error while trying to start string 2\n" + e.getMessage());
	}
}

private void stopBAT_1() {
		IntegerWriteChannel SET_START_STOP_1 = this.channel(ChannelId.SET_START_STOP_STRING_1);

	
	try {
		SET_START_STOP_1.setNextWriteValue(Stop);
	} 
	
	catch (OpenemsException e) {
		log.error("Error while trying to stop string 1\n" + e.getMessage());
	}
}
private void stopBAT_2() {
	IntegerWriteChannel SET_START_STOP_2 = this.channel(ChannelId.SET_START_STOP_STRING_2);


	try {
	SET_START_STOP_2.setNextWriteValue(Stop);
	} 

	catch (OpenemsException e) {
	log.error("Error while trying to stop string 2\n" + e.getMessage());
	}
}

public boolean END_OF_CHARGE_1() {				// get End of Charge Value
	StateChannel i = this.channel(ChannelId.END_OF_CHARGE_REQUEST_1);
	Optional<Boolean> END_CHARGE = i.getNextValue().asOptional();
	return END_CHARGE.isPresent() && END_CHARGE.get();
}
public boolean END_OF_CHARGE_2() {				// get End of Charge Value
	StateChannel i = this.channel(ChannelId.END_OF_CHARGE_REQUEST_2);
	Optional<Boolean> END_CHARGE = i.getNextValue().asOptional();
	return END_CHARGE.isPresent() && END_CHARGE.get();
}


public enum ChannelId implements io.openems.edge.common.channel.doc.ChannelId {
	
	SET_ENABLE_STRING_1(new Doc().unit(Unit.NONE)),
	SET_START_STOP_STRING_1(new Doc().unit(Unit.NONE)),
	
	END_OF_CHARGE_REQUEST_1(new Doc().unit(Unit.NONE)),
	AVAILABLE_POWER_1(new Doc().unit(Unit.KILOWATT)),
	HV_BAT_TEMP_1(new Doc().unit(Unit.DEGREE_CELSIUS)),
	HV_BAT_HEALTH_1(new Doc().unit(Unit.PERCENT)),
	LBC_PRUN_ANSWER_1(new Doc().unit(Unit.NONE)),
	HV_BAT_MAX_TEMP_1(new Doc().unit(Unit.DEGREE_CELSIUS)),
	HV_BAT_STATE_1(new Doc().unit(Unit.NONE)),
	LBC_REFUSE_TO_SLEEP_1(new Doc().unit(Unit.NONE)),
	AVAILABLE_ENERGY_1(new Doc().unit(Unit.KILOWATT_HOURS)),
	ISOL_DIAG_AUTHORISATION_1(new Doc().unit(Unit.NONE)),
	SAFETY_MODE_1_FLAG_1(new Doc().unit(Unit.NONE)),
	HV_ISOLATION_IMPEDANCE_1(new Doc().unit(Unit.OHM)),
	CELL_HIGHEST_VOLTAGE_1(new Doc().unit(Unit.MILLIVOLT)),
	CELL_LOWEST_VOLTAGE_1(new Doc().unit(Unit.MILLIVOLT)),
	CHARGING_POWER_1(new Doc().unit(Unit.KILOWATT)),
	HV_BAT_INSTANT_CURRENT_1(new Doc().unit(Unit.AMPERE)),
	HV_POWER_CONNECTION_1(new Doc().unit(Unit.NONE)),
	HV_BAT_LEVEL_2_FAILURE_1(new Doc().unit(Unit.NONE)),
	HV_BAT_LEVEL_1_FAILURE_1(new Doc().unit(Unit.NONE)),
	USER_SOC_1(new Doc().unit(Unit.PERCENT)),
	HV_NETWORK_VOLTAGE_1(new Doc().unit(Unit.VOLT)),
	HV_BAT_SERIAL_NUMBER_1(new Doc().unit(Unit.NONE)),
	CELL_LOWEST_VOLTAGE_RCY_1(new Doc().unit(Unit.MILLIVOLT)),
	CELL_HIGHEST_VOLTAGE_RCY_1(new Doc().unit(Unit.MILLIVOLT)),
	HV_BAT_MAX_TEMP_RCY_1(new Doc().unit(Unit.DEGREE_CELSIUS)),
	LBC_PRUN_ANSWER_RCY_1(new Doc().unit(Unit.NONE)),
	HV_POWER_CONNECTION_RCY_1(new Doc().unit(Unit.NONE)),
	HV_BAT_LEVEL_2_FAILURE_RCY_1(new Doc().unit(Unit.NONE)),
	SAFETY_MODE_1_FLAG_RCY_1(new Doc().unit(Unit.NONE)),
	LBC2_REFUSE_TO_SLEEP_1(new Doc().unit(Unit.NONE)),
	ELEC_MACHINE_SPEED_1(new Doc().unit(Unit.NONE)),
	ETS_SLEEP_MODE_1(new Doc().unit(Unit.NONE)),
	SCH_WAKE_UP_SLEEP_COMMAND_1(new Doc().unit(Unit.NONE)),
	WAKE_UP_TYPE_1(new Doc().unit(Unit.NONE)),
	LBC_PRUN_KEY_1(new Doc().unit(Unit.NONE)),
	LBC_PRUN_KEY_RCY_1(new Doc().unit(Unit.NONE)),
	OPERATING_TYPE_1(new Doc().unit(Unit.NONE)),
	POWER_RELAY_STATE_1(new Doc().unit(Unit.NONE)),
	DISTANCE_TOTALIZER_COPY_1(new Doc().unit(Unit.NONE)),
	ABSOLUTE_TIME_SINCE_1RST_IGNITION_1(new Doc().unit(Unit.NONE)),
	VEHICLE_ID_1(new Doc().unit(Unit.NONE)),
	STRING_STATUS_1(new Doc().unit(Unit.NONE)),
	ENABLE_STRING_1(new Doc().unit(Unit.NONE)),
	START_STOP_STRING_1(new Doc().unit(Unit.NONE)),
	ALARMS_1(new Doc().unit(Unit.NONE)),
	FAULTS_1(new Doc().unit(Unit.NONE)),
	
	SET_ENABLE_STRING_2(new Doc().unit(Unit.NONE)),
	SET_START_STOP_STRING_2(new Doc().unit(Unit.NONE)),
	
	END_OF_CHARGE_REQUEST_2(new Doc().unit(Unit.NONE)),
	AVAILABLE_POWER_2(new Doc().unit(Unit.KILOWATT)),
	HV_BAT_TEMP_2(new Doc().unit(Unit.DEGREE_CELSIUS)),
	HV_BAT_HEALTH_2(new Doc().unit(Unit.PERCENT)),
	LBC_PRUN_ANSWER_2(new Doc().unit(Unit.NONE)),
	HV_BAT_MAX_TEMP_2(new Doc().unit(Unit.DEGREE_CELSIUS)),
	HV_BAT_STATE_2(new Doc().unit(Unit.NONE)),
	LBC_REFUSE_TO_SLEEP_2(new Doc().unit(Unit.NONE)),
	AVAILABLE_ENERGY_2(new Doc().unit(Unit.KILOWATT_HOURS)),
	ISOL_DIAG_AUTHORISATION_2(new Doc().unit(Unit.NONE)),
	SAFETY_MODE_1_FLAG_2(new Doc().unit(Unit.NONE)),
	HV_ISOLATION_IMPEDANCE_2(new Doc().unit(Unit.OHM)),
	CELL_HIGHEST_VOLTAGE_2(new Doc().unit(Unit.MILLIVOLT)),
	CELL_LOWEST_VOLTAGE_2(new Doc().unit(Unit.MILLIVOLT)),
	CHARGING_POWER_2(new Doc().unit(Unit.KILOWATT)),
	HV_BAT_INSTANT_CURRENT_2(new Doc().unit(Unit.AMPERE)),
	HV_POWER_CONNECTION_2(new Doc().unit(Unit.NONE)),
	HV_BAT_LEVEL_2_FAILURE_2(new Doc().unit(Unit.NONE)),
	HV_BAT_LEVEL_1_FAILURE_2(new Doc().unit(Unit.NONE)),
	USER_SOC_2(new Doc().unit(Unit.PERCENT)),
	HV_NETWORK_VOLTAGE_2(new Doc().unit(Unit.VOLT)),
	HV_BAT_SERIAL_NUMBER_2(new Doc().unit(Unit.NONE)),
	CELL_LOWEST_VOLTAGE_RCY_2(new Doc().unit(Unit.MILLIVOLT)),
	CELL_HIGHEST_VOLTAGE_RCY_2(new Doc().unit(Unit.MILLIVOLT)),
	HV_BAT_MAX_TEMP_RCY_2(new Doc().unit(Unit.DEGREE_CELSIUS)),
	LBC_PRUN_ANSWER_RCY_2(new Doc().unit(Unit.NONE)),
	HV_POWER_CONNECTION_RCY_2(new Doc().unit(Unit.NONE)),
	HV_BAT_LEVEL_2_FAILURE_RCY_2(new Doc().unit(Unit.NONE)),
	SAFETY_MODE_1_FLAG_RCY_2(new Doc().unit(Unit.NONE)),
	LBC2_REFUSE_TO_SLEEP_2(new Doc().unit(Unit.NONE)),
	ELEC_MACHINE_SPEED_2(new Doc().unit(Unit.NONE)),
	ETS_SLEEP_MODE_2(new Doc().unit(Unit.NONE)),
	SCH_WAKE_UP_SLEEP_COMMAND_2(new Doc().unit(Unit.NONE)),
	WAKE_UP_TYPE_2(new Doc().unit(Unit.NONE)),
	LBC_PRUN_KEY_2(new Doc().unit(Unit.NONE)),
	LBC_PRUN_KEY_RCY_2(new Doc().unit(Unit.NONE)),
	OPERATING_TYPE_2(new Doc().unit(Unit.NONE)),
	POWER_RELAY_STATE_2(new Doc().unit(Unit.NONE)),
	DISTANCE_TOTALIZER_COPY_2(new Doc().unit(Unit.NONE)),
	ABSOLUTE_TIME_SINCE_1RST_IGNITION_2(new Doc().unit(Unit.NONE)),
	VEHICLE_ID_2(new Doc().unit(Unit.NONE)),
	STRING_STATUS_2(new Doc().unit(Unit.NONE)),
	ENABLE_STRING_2(new Doc().unit(Unit.NONE)),
	START_STOP_STRING_2(new Doc().unit(Unit.NONE)),
	ALARMS_2(new Doc().unit(Unit.NONE)),
	FAULTS_2(new Doc().unit(Unit.NONE)),
	SERIAL_NUMBER(new Doc().unit(Unit.NONE)),
	MODBUS_VERSION(new Doc().unit(Unit.NONE)),
	BATTERY_TYP(new Doc().unit(Unit.NONE)),
	BATTERY_MANUFACTURER(new Doc().unit(Unit.NONE)),
	BATTERY_MODEL(new Doc().unit(Unit.NONE)),
	STATE_OF_BATTERY_STACK(new Doc().unit(Unit.NONE)),
	WARRANTY_DATE(new Doc().unit(Unit.NONE)),
	INSTALLATION_DATE(new Doc().unit(Unit.NONE)),
	NAMEPLATE_CHARGE_CAPACITY(new Doc().unit(Unit.AMPERE_HOURS)),
	NAMEPLATE_ENERGY_CAPACITY(new Doc().unit(Unit.WATT_HOURS)),
	NAMEPLATE_MAX_CHARGE_RATE(new Doc().unit(Unit.WATT)),
	NAMEPLATE_MAX_DISCHARGE_RATE(new Doc().unit(Unit.WATT)),
	MAX_BATTERY_VOLTAGE(new Doc().unit(Unit.VOLT)),
	MIN_BATTERY_VOLTAGE(new Doc().unit(Unit.VOLT)),
	MAX_CELL_VOLTAGE(new Doc().unit(Unit.VOLT)),
	MAX_CELL_VOLTAGE_STRING(new Doc().unit(Unit.VOLT)),
	MIN_CELL_VOLTAGE(new Doc().unit(Unit.VOLT)),
	MIN_CELL_VOLTAGE_STRING(new Doc().unit(Unit.VOLT)),
	TOTAL_CHARGE_ENERGY(new Doc().unit(Unit.WATT_HOURS)),
	TOTAL_DISCHARGE_ENERGY(new Doc().unit(Unit.WATT_HOURS)),
	CURRENT_TOTAL_CAPACITY(new Doc().unit(Unit.WATT_HOURS)),
	REST_CAPACITY(new Doc().unit(Unit.WATT_HOURS)),
	TOTAL_DC_CURRENT(new Doc().unit(Unit.AMPERE)),
	TOTAL_DC_VOLTAGE(new Doc().unit(Unit.VOLT)),
	MAX_CHARGE_CURRENT(new Doc().unit(Unit.AMPERE)),
	MAX_DISCHARGE_CURRENT(new Doc().unit(Unit.AMPERE)),
	TOTAL_POWER(new Doc().unit(Unit.WATT)),				// UNIT: KILOWATT
	INVERTER_STATE_REQUEST(new Doc().unit(Unit.NONE)),
	BATTERY_POWER_REQUEST(new Doc().unit(Unit.NONE)),
	BATTERY_CHARGE_DISCHARGE_REQUEST(new Doc().unit(Unit.NONE)),
	CONTROLE_MODE(new Doc().unit(Unit.NONE)),
	START_STOP_BATTERY_STACK(new Doc().unit(Unit.NONE)),
	STRING_COUNT(new Doc().unit(Unit.NONE)),
	CONNECTED_STRING_COUNT(new Doc().unit(Unit.NONE)),
	MAX_STRING_VOLTAGE(new Doc().unit(Unit.VOLT)),
	MAX_STRING_VOLTAGE_STRING(new Doc().unit(Unit.VOLT)),
	MIN_STRING_VOLTAGE(new Doc().unit(Unit.VOLT)),
	MIN_STRING_VOLTAGE_STRING(new Doc().unit(Unit.VOLT)),
	AVERAGE_STRING_VOLTAGE(new Doc().unit(Unit.VOLT)),
	MAX_STRING_CURRENT(new Doc().unit(Unit.AMPERE)),
	MAX_STRING_CURRENT_STRING(new Doc().unit(Unit.AMPERE)),
	MIN_STRING_CURRENT(new Doc().unit(Unit.AMPERE)),
	MIN_STRING_CURRENT_STRING(new Doc().unit(Unit.AMPERE)),
	AVERAGE_STRING_CURRENT(new Doc().unit(Unit.AMPERE)),
	ALARMS_STRING(new Doc().unit(Unit.NONE)),
	FAULTS_STRING(new Doc().unit(Unit.NONE)),
	SET_BATTERY_CHARGE_DISCHARGE_REQUEST(new Doc().unit(Unit.NONE)),
	SET_START_STOP_BATTERY_STACK(new Doc().unit(Unit.NONE)),
	
	;
	
	private final Doc doc;

	private ChannelId(Doc doc) {
		this.doc = doc;
	}
	
	@Override
	public Doc doc() {
		// TODO Auto-generated method stub
		return null;
	} 
	
	
}

@Override
protected ModbusProtocol defineModbusProtocol() {
//-------------------------------------------------BATTERY 1----------------------------------------------------------
	return new ModbusProtocol(this,
			new FC3ReadRegistersTask(0x100, Priority.HIGH, //
					m(BSMU.ChannelId.END_OF_CHARGE_REQUEST_1, new UnsignedWordElement(0x100))),
			new FC3ReadRegistersTask(0x101, Priority.HIGH, //
					m(BSMU.ChannelId.AVAILABLE_POWER_1, new UnsignedWordElement(0x101))),
			new FC3ReadRegistersTask(0x102, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_TEMP_1, new UnsignedWordElement(0x102))),
			new FC3ReadRegistersTask(0x103, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_HEALTH_1, new UnsignedWordElement(0x103))),
			new FC3ReadRegistersTask(0x104, Priority.HIGH, //
					m(BSMU.ChannelId.LBC_PRUN_ANSWER_1, new UnsignedWordElement(0x104))),
			new FC3ReadRegistersTask(0x105, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_MAX_TEMP_1, new UnsignedWordElement(0x105))),
			new FC3ReadRegistersTask(0x106, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_STATE_1, new UnsignedWordElement(0x106))),
			new FC3ReadRegistersTask(0x107, Priority.HIGH, //
					m(BSMU.ChannelId.LBC_REFUSE_TO_SLEEP_1, new UnsignedWordElement(0x107))),
			new FC3ReadRegistersTask(0x108, Priority.HIGH, //
					m(BSMU.ChannelId.AVAILABLE_ENERGY_1, new UnsignedWordElement(0x108))),
			new FC3ReadRegistersTask(0x109, Priority.HIGH, //
					m(BSMU.ChannelId.ISOL_DIAG_AUTHORISATION_1, new UnsignedWordElement(0x109))),
			new FC3ReadRegistersTask(0x110, Priority.HIGH, //
					m(BSMU.ChannelId.SAFETY_MODE_1_FLAG_1, new UnsignedWordElement(0x110))),
			new FC3ReadRegistersTask(0x111, Priority.HIGH, //
					m(BSMU.ChannelId.HV_ISOLATION_IMPEDANCE_1, new UnsignedWordElement(0x111))),
			new FC3ReadRegistersTask(0x112, Priority.HIGH, //
					m(BSMU.ChannelId.CELL_HIGHEST_VOLTAGE_1, new UnsignedWordElement(0x112))),
			new FC3ReadRegistersTask(0x113, Priority.HIGH, //
					m(BSMU.ChannelId.CELL_LOWEST_VOLTAGE_1, new UnsignedWordElement(0x113))),
			new FC3ReadRegistersTask(0x114, Priority.HIGH, //
					m(BSMU.ChannelId.CHARGING_POWER_1, new UnsignedWordElement(0x114))),
			new FC3ReadRegistersTask(0x115, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_INSTANT_CURRENT_1, new UnsignedWordElement(0x115))),
			new FC3ReadRegistersTask(0x116, Priority.HIGH, //
					m(BSMU.ChannelId.HV_POWER_CONNECTION_1, new UnsignedWordElement(0x116))),
			new FC3ReadRegistersTask(0x117, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_LEVEL_2_FAILURE_1, new UnsignedWordElement(0x117))),
			new FC3ReadRegistersTask(0x118, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_LEVEL_1_FAILURE_1, new UnsignedWordElement(0x118))),
			new FC3ReadRegistersTask(0x119, Priority.HIGH, //
					m(BSMU.ChannelId.USER_SOC_1, new UnsignedWordElement(0x119))),
			new FC3ReadRegistersTask(0x120, Priority.HIGH, //
					m(BSMU.ChannelId.HV_NETWORK_VOLTAGE_1, new UnsignedWordElement(0x120))),
			new FC3ReadRegistersTask(0x121, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_SERIAL_NUMBER_1, new UnsignedWordElement(0x121))),
			new FC3ReadRegistersTask(0x122, Priority.HIGH, //
					m(BSMU.ChannelId.CELL_LOWEST_VOLTAGE_RCY_1, new UnsignedWordElement(0x122))),
			new FC3ReadRegistersTask(0x123, Priority.HIGH, //
					m(BSMU.ChannelId.CELL_HIGHEST_VOLTAGE_RCY_1, new UnsignedWordElement(0x123))),
			new FC3ReadRegistersTask(0x124, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_MAX_TEMP_RCY_1, new UnsignedWordElement(0x124))),
			new FC3ReadRegistersTask(0x125, Priority.HIGH, //
					m(BSMU.ChannelId.LBC_PRUN_ANSWER_RCY_1, new UnsignedWordElement(0x125))),
			new FC3ReadRegistersTask(0x126, Priority.HIGH, //
					m(BSMU.ChannelId.HV_POWER_CONNECTION_RCY_1, new UnsignedWordElement(0x126))),
			new FC3ReadRegistersTask(0x127, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_LEVEL_2_FAILURE_RCY_1, new UnsignedWordElement(0x127))),
			new FC3ReadRegistersTask(0x128, Priority.HIGH, //
					m(BSMU.ChannelId.SAFETY_MODE_1_FLAG_RCY_1, new UnsignedWordElement(0x128))),
			new FC3ReadRegistersTask(0x129, Priority.HIGH, //
					m(BSMU.ChannelId.LBC2_REFUSE_TO_SLEEP_1, new UnsignedWordElement(0x129))),
			new FC3ReadRegistersTask(0x130, Priority.HIGH, //
					m(BSMU.ChannelId.ELEC_MACHINE_SPEED_1, new UnsignedWordElement(0x130))),
			new FC3ReadRegistersTask(0x131, Priority.HIGH, //
					m(BSMU.ChannelId.ETS_SLEEP_MODE_1, new UnsignedWordElement(0x131))),
			new FC3ReadRegistersTask(0x132, Priority.HIGH, //
					m(BSMU.ChannelId.SCH_WAKE_UP_SLEEP_COMMAND_1, new UnsignedWordElement(0x132))),
			new FC3ReadRegistersTask(0x133, Priority.HIGH, //
					m(BSMU.ChannelId.WAKE_UP_TYPE_1, new UnsignedWordElement(0x133))),
			new FC3ReadRegistersTask(0x134, Priority.HIGH, //
					m(BSMU.ChannelId.LBC_PRUN_KEY_1, new UnsignedWordElement(0x134))),
			new FC3ReadRegistersTask(0x135, Priority.HIGH, //
					m(BSMU.ChannelId.LBC_PRUN_KEY_RCY_1, new UnsignedWordElement(0x135))),
			new FC3ReadRegistersTask(0x136, Priority.HIGH, //
					m(BSMU.ChannelId.OPERATING_TYPE_1, new UnsignedWordElement(0x136))),
			new FC3ReadRegistersTask(0x137, Priority.HIGH, //
					m(BSMU.ChannelId.POWER_RELAY_STATE_1, new UnsignedWordElement(0x137))),
			new FC3ReadRegistersTask(0x138, Priority.HIGH, //
					m(BSMU.ChannelId.DISTANCE_TOTALIZER_COPY_1, new UnsignedWordElement(0x138))),
			new FC3ReadRegistersTask(0x139, Priority.HIGH, //
					m(BSMU.ChannelId.ABSOLUTE_TIME_SINCE_1RST_IGNITION_1, new UnsignedWordElement(0x139))),
			new FC3ReadRegistersTask(0x140, Priority.HIGH, //
					m(BSMU.ChannelId.VEHICLE_ID_1, new UnsignedWordElement(0x140))),
			new FC3ReadRegistersTask(0x160, Priority.HIGH, //
					m(BSMU.ChannelId.STRING_STATUS_1, new UnsignedWordElement(0x160))),
			new FC6WriteRegisterTask(0x161,  //
					m(BSMU.ChannelId.ENABLE_STRING_1, new UnsignedWordElement(0x161))),
			new FC6WriteRegisterTask(0x162,  //
					m(BSMU.ChannelId.START_STOP_STRING_1, new UnsignedWordElement(0x162))),
			new FC3ReadRegistersTask(0x163, Priority.HIGH,
					m(BSMU.ChannelId.ALARMS_1, new UnsignedWordElement(0x163))),
			new FC3ReadRegistersTask(0x164, Priority.HIGH, //
					m(BSMU.ChannelId.FAULTS_1, new UnsignedWordElement(0x164))),
//---------------------------------------------WRITE BATTERY 1-------------------------------------------------------
			new FC6WriteRegisterTask(0x161,  //
					m(BSMU.ChannelId.SET_ENABLE_STRING_1, new UnsignedWordElement(0x161))),
			new FC6WriteRegisterTask(0x162,  //
					m(BSMU.ChannelId.SET_START_STOP_STRING_1, new UnsignedWordElement(0x162))),
			 
//---------------------------------------------BATTERY 2-------------------------------------------------------------		

			new FC3ReadRegistersTask(0x200, Priority.HIGH, //
					m(BSMU.ChannelId.END_OF_CHARGE_REQUEST_2, new UnsignedWordElement(0x200))),
			new FC3ReadRegistersTask(0x201, Priority.HIGH, //
					m(BSMU.ChannelId.AVAILABLE_POWER_2, new UnsignedWordElement(0x201))),
			new FC3ReadRegistersTask(0x202, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_TEMP_2, new UnsignedWordElement(0x202))),
			new FC3ReadRegistersTask(0x203, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_HEALTH_2, new UnsignedWordElement(0x203))),
			new FC3ReadRegistersTask(0x204, Priority.HIGH, //
					m(BSMU.ChannelId.LBC_PRUN_ANSWER_2, new UnsignedWordElement(0x204))),
			new FC3ReadRegistersTask(0x205, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_MAX_TEMP_2, new UnsignedWordElement(0x205))),
			new FC3ReadRegistersTask(0x206, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_STATE_2, new UnsignedWordElement(0x206))),
			new FC3ReadRegistersTask(0x207, Priority.HIGH, //
					m(BSMU.ChannelId.LBC_REFUSE_TO_SLEEP_2, new UnsignedWordElement(0x207))),
			new FC3ReadRegistersTask(0x208, Priority.HIGH, //
					m(BSMU.ChannelId.AVAILABLE_ENERGY_2, new UnsignedWordElement(0x208))),
			new FC3ReadRegistersTask(0x209, Priority.HIGH, //
					m(BSMU.ChannelId.ISOL_DIAG_AUTHORISATION_2, new UnsignedWordElement(0x209))),
			new FC3ReadRegistersTask(0x210, Priority.HIGH, //
					m(BSMU.ChannelId.SAFETY_MODE_1_FLAG_2, new UnsignedWordElement(0x210))),
			new FC3ReadRegistersTask(0x211, Priority.HIGH, //
					m(BSMU.ChannelId.HV_ISOLATION_IMPEDANCE_2, new UnsignedWordElement(0x211))),
			new FC3ReadRegistersTask(0x212, Priority.HIGH, //
					m(BSMU.ChannelId.CELL_HIGHEST_VOLTAGE_2, new UnsignedWordElement(0x212))),
			new FC3ReadRegistersTask(0x213, Priority.HIGH, //
					m(BSMU.ChannelId.CELL_LOWEST_VOLTAGE_2, new UnsignedWordElement(0x213))),
			new FC3ReadRegistersTask(0x214, Priority.HIGH, //
					m(BSMU.ChannelId.CHARGING_POWER_2, new UnsignedWordElement(0x214))),
			new FC3ReadRegistersTask(0x215, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_INSTANT_CURRENT_2, new UnsignedWordElement(0x215))),
			new FC3ReadRegistersTask(0x216, Priority.HIGH, //
					m(BSMU.ChannelId.HV_POWER_CONNECTION_2, new UnsignedWordElement(0x216))),
			new FC3ReadRegistersTask(0x217, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_LEVEL_2_FAILURE_2, new UnsignedWordElement(0x217))),
			new FC3ReadRegistersTask(0x218, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_LEVEL_1_FAILURE_2, new UnsignedWordElement(0x218))),
			new FC3ReadRegistersTask(0x219, Priority.HIGH, //
					m(BSMU.ChannelId.USER_SOC_2, new UnsignedWordElement(0x219))),
			new FC3ReadRegistersTask(0x220, Priority.HIGH, //
					m(BSMU.ChannelId.HV_NETWORK_VOLTAGE_2, new UnsignedWordElement(0x220))),
			new FC3ReadRegistersTask(0x221, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_SERIAL_NUMBER_2, new UnsignedWordElement(0x221))),
			new FC3ReadRegistersTask(0x222, Priority.HIGH, //
					m(BSMU.ChannelId.CELL_LOWEST_VOLTAGE_RCY_2, new UnsignedWordElement(0x222))),
			new FC3ReadRegistersTask(0x223, Priority.HIGH, //
					m(BSMU.ChannelId.CELL_HIGHEST_VOLTAGE_RCY_2, new UnsignedWordElement(0x223))),
			new FC3ReadRegistersTask(0x224, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_MAX_TEMP_RCY_2, new UnsignedWordElement(0x224))),
			new FC3ReadRegistersTask(0x225, Priority.HIGH, //
					m(BSMU.ChannelId.LBC_PRUN_ANSWER_RCY_2, new UnsignedWordElement(0x225))),
			new FC3ReadRegistersTask(0x226, Priority.HIGH, //
					m(BSMU.ChannelId.HV_POWER_CONNECTION_RCY_2, new UnsignedWordElement(0x226))),
			new FC3ReadRegistersTask(0x227, Priority.HIGH, //
					m(BSMU.ChannelId.HV_BAT_LEVEL_2_FAILURE_RCY_2, new UnsignedWordElement(0x227))),
			new FC3ReadRegistersTask(0x228, Priority.HIGH, //
					m(BSMU.ChannelId.SAFETY_MODE_1_FLAG_RCY_2, new UnsignedWordElement(0x228))),
			new FC3ReadRegistersTask(0x229, Priority.HIGH, //
					m(BSMU.ChannelId.LBC2_REFUSE_TO_SLEEP_2, new UnsignedWordElement(0x229))),
			new FC3ReadRegistersTask(0x230, Priority.HIGH, //
					m(BSMU.ChannelId.ELEC_MACHINE_SPEED_2, new UnsignedWordElement(0x230))),
			new FC3ReadRegistersTask(0x231, Priority.HIGH, //
					m(BSMU.ChannelId.ETS_SLEEP_MODE_2, new UnsignedWordElement(0x231))),
			new FC3ReadRegistersTask(0x232, Priority.HIGH, //
					m(BSMU.ChannelId.SCH_WAKE_UP_SLEEP_COMMAND_2, new UnsignedWordElement(0x232))),
			new FC3ReadRegistersTask(0x233, Priority.HIGH, //
					m(BSMU.ChannelId.WAKE_UP_TYPE_2, new UnsignedWordElement(0x233))),
			new FC3ReadRegistersTask(0x234, Priority.HIGH, //
					m(BSMU.ChannelId.LBC_PRUN_KEY_2, new UnsignedWordElement(0x234))),
			new FC3ReadRegistersTask(0x235, Priority.HIGH, //
					m(BSMU.ChannelId.LBC_PRUN_KEY_RCY_2, new UnsignedWordElement(0x235))),
			new FC3ReadRegistersTask(0x236, Priority.HIGH, //
					m(BSMU.ChannelId.OPERATING_TYPE_2, new UnsignedWordElement(0x236))),
			new FC3ReadRegistersTask(0x237, Priority.HIGH, //
					m(BSMU.ChannelId.POWER_RELAY_STATE_2, new UnsignedWordElement(0x237))),
			new FC3ReadRegistersTask(0x238, Priority.HIGH, //
					m(BSMU.ChannelId.DISTANCE_TOTALIZER_COPY_2, new UnsignedWordElement(0x238))),
			new FC3ReadRegistersTask(0x239, Priority.HIGH, //
					m(BSMU.ChannelId.ABSOLUTE_TIME_SINCE_1RST_IGNITION_2, new UnsignedWordElement(0x239))),
			new FC3ReadRegistersTask(0x240, Priority.HIGH, //
					m(BSMU.ChannelId.VEHICLE_ID_2, new UnsignedWordElement(0x240))),
			new FC3ReadRegistersTask(0x260, Priority.HIGH, //
					m(BSMU.ChannelId.STRING_STATUS_2, new UnsignedWordElement(0x260))),
			new FC6WriteRegisterTask(0x261, //
					m(BSMU.ChannelId.ENABLE_STRING_2, new UnsignedWordElement(0x261))),
			new FC6WriteRegisterTask(0x262, //
					m(BSMU.ChannelId.START_STOP_STRING_2, new UnsignedWordElement(0x262))),
			new FC3ReadRegistersTask(0x263, Priority.HIGH, //
					m(BSMU.ChannelId.ALARMS_2, new UnsignedWordElement(0x263))),
			new FC3ReadRegistersTask(0x264, Priority.HIGH, //
					m(BSMU.ChannelId.FAULTS_2, new UnsignedWordElement(0x264))),
//----------------------------------------WRITE BATTERY 2--------------------------------------------------------------
			new FC6WriteRegisterTask(0x261, //
					m(BSMU.ChannelId.SET_ENABLE_STRING_2, new UnsignedWordElement(0x261))),
			new FC6WriteRegisterTask(0x262, //
					m(BSMU.ChannelId.SET_START_STOP_STRING_2, new UnsignedWordElement(0x262))),
//----------------------------------------READ STRING------------------------------------------------------------------			
			new FC3ReadRegistersTask(0x500, Priority.LOW,
					m(BSMU.ChannelId.SERIAL_NUMBER, new UnsignedWordElement(0x500))),
			new FC3ReadRegistersTask(0x501, Priority.LOW,
					m(BSMU.ChannelId.MODBUS_VERSION, new UnsignedWordElement(0x501))),
			new FC3ReadRegistersTask(0x502, Priority.LOW,
					m(BSMU.ChannelId.BATTERY_TYP, new UnsignedWordElement(0x502))),
			new FC3ReadRegistersTask(0x503, Priority.LOW,
					m(BSMU.ChannelId.BATTERY_MANUFACTURER, new UnsignedWordElement(0x503))),
			new FC3ReadRegistersTask(0x504, Priority.LOW,
					m(BSMU.ChannelId.BATTERY_MODEL, new UnsignedWordElement(0x504))),
			new FC3ReadRegistersTask(0x505, Priority.HIGH,
					m(BSMU.ChannelId.STATE_OF_BATTERY_STACK, new UnsignedWordElement(0x505))),
			new FC3ReadRegistersTask(0x506, Priority.LOW,
					m(BSMU.ChannelId.WARRANTY_DATE, new UnsignedWordElement(0x506))),
			new FC3ReadRegistersTask(0x507, Priority.LOW,
					m(BSMU.ChannelId.INSTALLATION_DATE, new UnsignedWordElement(0x507))),
			new FC3ReadRegistersTask(0x508, Priority.HIGH,
					m(BSMU.ChannelId.NAMEPLATE_CHARGE_CAPACITY, new UnsignedWordElement(0x508))),
			new FC3ReadRegistersTask(0x509, Priority.HIGH,
					m(BSMU.ChannelId.NAMEPLATE_ENERGY_CAPACITY, new UnsignedWordElement(0x509))),
			new FC3ReadRegistersTask(0x510, Priority.HIGH,
					m(BSMU.ChannelId.NAMEPLATE_MAX_CHARGE_RATE, new UnsignedWordElement(0x510))),
			new FC3ReadRegistersTask(0x511, Priority.HIGH,
					m(BSMU.ChannelId.NAMEPLATE_MAX_DISCHARGE_RATE, new UnsignedWordElement(0x511))),
			new FC3ReadRegistersTask(0x512, Priority.HIGH,
					m(BSMU.ChannelId.MAX_BATTERY_VOLTAGE, new UnsignedWordElement(0x512))),
			new FC3ReadRegistersTask(0x513, Priority.HIGH,
					m(BSMU.ChannelId.MIN_BATTERY_VOLTAGE, new UnsignedWordElement(0x513))),
			new FC3ReadRegistersTask(0x514, Priority.HIGH,
					m(BSMU.ChannelId.MAX_CELL_VOLTAGE, new UnsignedWordElement(0x514))),
			new FC3ReadRegistersTask(0x515, Priority.HIGH,
					m(BSMU.ChannelId.MAX_CELL_VOLTAGE_STRING, new UnsignedWordElement(0x515))),
			new FC3ReadRegistersTask(0x516, Priority.HIGH,
					m(BSMU.ChannelId.MIN_CELL_VOLTAGE, new UnsignedWordElement(0x516))),
			new FC3ReadRegistersTask(0x517, Priority.HIGH,
					m(BSMU.ChannelId.MIN_CELL_VOLTAGE_STRING, new UnsignedWordElement(0x517))),
			new FC3ReadRegistersTask(0x518, Priority.HIGH,
					m(BSMU.ChannelId.TOTAL_CHARGE_ENERGY, new UnsignedWordElement(0x518))),
			new FC3ReadRegistersTask(0x519, Priority.HIGH,
					m(BSMU.ChannelId.TOTAL_DISCHARGE_ENERGY, new UnsignedWordElement(0x519))),
			new FC3ReadRegistersTask(0x520, Priority.HIGH,
					m(BSMU.ChannelId.CURRENT_TOTAL_CAPACITY, new UnsignedWordElement(0x520))),
			new FC3ReadRegistersTask(0x521, Priority.HIGH,
					m(BSMU.ChannelId.REST_CAPACITY, new UnsignedWordElement(0x521))),
			new FC3ReadRegistersTask(0x522, Priority.HIGH,
					m(BSMU.ChannelId.TOTAL_DC_CURRENT, new UnsignedWordElement(0x522))),
			new FC3ReadRegistersTask(0x523, Priority.HIGH,
					m(BSMU.ChannelId.TOTAL_DC_VOLTAGE, new UnsignedWordElement(0x523))),
			new FC3ReadRegistersTask(0x524, Priority.HIGH,
					m(BSMU.ChannelId.MAX_CHARGE_CURRENT, new UnsignedWordElement(0x524))),
			new FC3ReadRegistersTask(0x525, Priority.HIGH,
					m(BSMU.ChannelId.MAX_DISCHARGE_CURRENT, new UnsignedWordElement(0x525))),
			new FC3ReadRegistersTask(0x526, Priority.HIGH,
					m(BSMU.ChannelId.TOTAL_POWER, new UnsignedWordElement(0x526))),
			new FC3ReadRegistersTask(0x527, Priority.HIGH,
					m(BSMU.ChannelId.INVERTER_STATE_REQUEST, new UnsignedWordElement(0x527))),
			new FC3ReadRegistersTask(0x528, Priority.HIGH,
					m(BSMU.ChannelId.BATTERY_POWER_REQUEST, new UnsignedWordElement(0x528))),
			new FC3ReadRegistersTask(0x529, Priority.HIGH,
					m(BSMU.ChannelId.BATTERY_CHARGE_DISCHARGE_REQUEST, new UnsignedWordElement(0x529))),
			new FC3ReadRegistersTask(0x530, Priority.LOW,
					m(BSMU.ChannelId.CONTROLE_MODE, new UnsignedWordElement(0x530))),
			new FC3ReadRegistersTask(0x531, Priority.HIGH,
					m(BSMU.ChannelId.START_STOP_BATTERY_STACK, new UnsignedWordElement(0x531))),
			new FC3ReadRegistersTask(0x532, Priority.HIGH,
					m(BSMU.ChannelId.STRING_COUNT, new UnsignedWordElement(0x532))),
			new FC3ReadRegistersTask(0x533, Priority.LOW,
					m(BSMU.ChannelId.CONNECTED_STRING_COUNT, new UnsignedWordElement(0x533))),
			new FC3ReadRegistersTask(0x534, Priority.HIGH,
					m(BSMU.ChannelId.MAX_STRING_VOLTAGE, new UnsignedWordElement(0x534))),
			new FC3ReadRegistersTask(0x535, Priority.HIGH,
					m(BSMU.ChannelId.MAX_STRING_VOLTAGE_STRING, new UnsignedWordElement(0x535))),
			new FC3ReadRegistersTask(0x536, Priority.HIGH,
					m(BSMU.ChannelId.MIN_STRING_VOLTAGE, new UnsignedWordElement(0x536))),
			new FC3ReadRegistersTask(0x537, Priority.HIGH,
					m(BSMU.ChannelId.MIN_STRING_VOLTAGE_STRING, new UnsignedWordElement(0x537))),
			new FC3ReadRegistersTask(0x538, Priority.HIGH,
					m(BSMU.ChannelId.AVERAGE_STRING_VOLTAGE, new UnsignedWordElement(0x538))),
			new FC3ReadRegistersTask(0x539, Priority.HIGH,
					m(BSMU.ChannelId.MAX_STRING_CURRENT, new UnsignedWordElement(0x539))),
			new FC3ReadRegistersTask(0x540, Priority.HIGH,
					m(BSMU.ChannelId.MAX_STRING_CURRENT_STRING, new UnsignedWordElement(0x540))),
			new FC3ReadRegistersTask(0x541, Priority.HIGH,
					m(BSMU.ChannelId.MIN_STRING_CURRENT, new UnsignedWordElement(0x541))),
			new FC3ReadRegistersTask(0x542, Priority.HIGH,
					m(BSMU.ChannelId.MIN_STRING_CURRENT_STRING, new UnsignedWordElement(0x542))),
			new FC3ReadRegistersTask(0x543, Priority.HIGH,
					m(BSMU.ChannelId.AVERAGE_STRING_CURRENT, new UnsignedWordElement(0x543))),
			new FC3ReadRegistersTask(0x544, Priority.HIGH,
					m(BSMU.ChannelId.ALARMS_STRING, new UnsignedWordElement(0x544))),
			new FC3ReadRegistersTask(0x545, Priority.HIGH,
					m(BSMU.ChannelId.FAULTS_STRING, new UnsignedWordElement(0x545))),
//-----------------------------------------------WRITE STRING----------------------------------------------------
			new FC6WriteRegisterTask(0x529,
					m(BSMU.ChannelId.SET_BATTERY_CHARGE_DISCHARGE_REQUEST, new UnsignedWordElement(0x529))),
			new FC6WriteRegisterTask(0x531,
					m(BSMU.ChannelId.SET_START_STOP_BATTERY_STACK, new UnsignedWordElement(0x531)))
			
			
			);
	}

@Override
public void handleEvent(Event event) {
	if (!this.isEnabled()) {
		return;
	}
	switch (event.getTopic()) {

	case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE:
		HandleBatteryState();			
		break;
	}
}




@Override
public String id() {
	// TODO Auto-generated method stub
	return null;
}

@Override
public boolean isEnabled() {
	// TODO Auto-generated method stub
	return false;
}

@Override
public String servicePid() {
	// TODO Auto-generated method stub
	return null;
}

@Override
public ComponentContext componentContext() {
	// TODO Auto-generated method stub
	return null;
}

@Override
public Channel<?> _channel(String channelName) {
	// TODO Auto-generated method stub
	return null;
}

@Override
public Collection<Channel<?>> channels() {
	// TODO Auto-generated method stub
	return null;
}






}