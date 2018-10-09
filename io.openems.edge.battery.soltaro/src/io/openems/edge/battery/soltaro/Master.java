package io.openems.edge.battery.soltaro;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
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

import io.openems.edge.battery.api.Battery;
import io.openems.edge.battery.soltaro.SoltaroRack.ClusterRunState;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC16WriteRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.channel.doc.Doc;
import io.openems.edge.common.channel.doc.Level;
import io.openems.edge.common.channel.doc.OptionsEnum;
import io.openems.edge.common.channel.doc.Unit;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.taskmanager.Priority;

@Designate(ocd = MasterConfig.class, factory = true)
@Component( //
		name = "Bms.Fenecon.SoltaroMaster", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE //
)
public class Master extends AbstractOpenemsModbusComponent implements Battery, OpenemsComponent, EventHandler {

	public static final int DISCHARGE_MIN_V = 696;
	public static final int CHARGE_MAX_V = 854;
	public static final int DISCHARGE_MAX_A = 20;
	public static final int CHARGE_MAX_A = 20;
	public static final Integer CAPACITY_KWH = 150;
	
	private final Logger log = LoggerFactory.getLogger(Master.class);	
	private String modbusBridgeId;
	private BatteryState batteryState;
	@Reference
	protected ConfigurationAdmin cm;
	
	public Master() {
		Utils.initializeMasterChannels(this).forEach(channel -> this.addChannel(channel));
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
	}
	
	@Override
	public void handleEvent(Event event) {
		

	}

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
	
	public enum ChannelId implements io.openems.edge.common.channel.doc.ChannelId {
		START_STOP(new Doc().options(StartStop.values())), //
		RACK_1_USAGE(new Doc().options(RackUsage.values())), //
		RACK_2_USAGE(new Doc().options(RackUsage.values())), //
		RACK_3_USAGE(new Doc().options(RackUsage.values())), //
		CHARGE_INDICATION(new Doc().options(ChargeIndication.values())), //
		CURRENT(new Doc().unit(Unit.MILLIAMPERE)), //
		SYSTEM_RUNNING_STATE(new Doc().options(RunningState.values())), //
		VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		MASTER_ALARM_PCS_OUT_OF_CONTROL(new Doc().level(Level.FAULT).text("PCS out of control alarm")),
		MASTER_ALARM_PCS_COMMUNICATION_FAULT(new Doc().level(Level.FAULT).text("PCS communication fault alarm")),
		SUB_MASTER_COMMUNICATION_FAULT_ALARM_MASTER_3(new Doc().level(Level.FAULT).text("Communication to sub master 3 fault")),
		SUB_MASTER_COMMUNICATION_FAULT_ALARM_MASTER_2(new Doc().level(Level.FAULT).text("Communication to sub master 2 fault")),
		SUB_MASTER_COMMUNICATION_FAULT_ALARM_MASTER_1(new Doc().level(Level.FAULT).text("Communication to sub master 1 fault")),
		
		
		RACK_1_STATE(new Doc().unit(Unit.NONE)), //
		RACK_1_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_CURRENT(new Doc().unit(Unit.MILLIAMPERE)), //
		RACK_1_CHARGE_INDICATION(new Doc().options(ChargeIndication.values())), //
		RACK_1_SOC(new Doc().unit(Unit.PERCENT)), //
		RACK_1_SOH(new Doc().unit(Unit.PERCENT)), //
		RACK_1_MAX_CELL_VOLTAGE_ID(new Doc().unit(Unit.NONE)), //
		RACK_1_MAX_CELL_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_MIN_CELL_VOLTAGE_ID(new Doc().unit(Unit.NONE)), //
		RACK_1_MIN_CELL_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_MAX_CELL_TEMPERATURE_ID(new Doc().unit(Unit.NONE)), //
		RACK_1_MAX_CELL_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_MIN_CELL_TEMPERATURE_ID(new Doc().unit(Unit.NONE)), //
		RACK_1_MIN_CELL_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_ALARM_LEVEL_2_CELL_DISCHA_TEMP_LOW(
				new Doc().level(Level.WARNING).text("Cluster 1 Cell Discharge Temperature Low Alarm Level 2")), //
		RACK_1_ALARM_LEVEL_2_CELL_DISCHA_TEMP_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 1 Cell Discharge Temperature High Alarm Level 2")), //
		RACK_1_ALARM_LEVEL_2_INSULATION_LOW(new Doc().level(Level.WARNING).text("Cluster 3Insulation Low Alarm Level 2")), //
		RACK_1_ALARM_LEVEL_2_CELL_CHA_TEMP_LOW(
				new Doc().level(Level.WARNING).text("Cluster 3 Cell Charge Temperature Low Alarm Level 2")), //
		RACK_1_ALARM_LEVEL_2_CELL_CHA_TEMP_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 3 Cell Charge Temperature High Alarm Level 2")), //
		RACK_1_ALARM_LEVEL_2_DISCHA_CURRENT_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 1 Discharge Current High Alarm Level 2")), //
		RACK_1_ALARM_LEVEL_2_TOTAL_VOLTAGE_LOW(
				new Doc().level(Level.WARNING).text("Cluster 1 Total Voltage Low Alarm Level 2")), //
		RACK_1_ALARM_LEVEL_2_CELL_VOLTAGE_LOW(new Doc().level(Level.WARNING).text("Cluster 1 Cell Voltage Low Alarm Level 2")), //
		RACK_1_ALARM_LEVEL_2_CHA_CURRENT_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 1 Charge Current High Alarm Level 2")), //
		RACK_1_ALARM_LEVEL_2_TOTAL_VOLTAGE_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 1 Total Voltage High Alarm Level 2")), //
		RACK_1_ALARM_LEVEL_2_CELL_VOLTAGE_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 1 Cell Voltage High Alarm Level 2")), //
		RACK_1_ALARM_LEVEL_1_CELL_DISCHA_TEMP_LOW(
				new Doc().level(Level.WARNING).text("Cluster 1 Cell Discharge Temperature Low Alarm Level 1")), //
		RACK_1_ALARM_LEVEL_1_CELL_DISCHA_TEMP_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 1 Cell Discharge Temperature High Alarm Level 1")), //
		RACK_1_ALARM_LEVEL_1_TOTAL_VOLTAGE_DIFF_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 3 Total Voltage Diff High Alarm Level 1")), //
		RACK_1_ALARM_LEVEL_1_INSULATION_LOW(new Doc().level(Level.WARNING).text("Cluster 3 Insulation Low Alarm Level1")), //
		RACK_1_ALARM_LEVEL_1_CELL_VOLTAGE_DIFF_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 1 Cell Voltage Diff High Alarm Level 1")), //
		RACK_1_ALARM_LEVEL_1_CELL_TEMP_DIFF_HIGH(
				new Doc().level(Level.WARNING).text("Cluster X Cell temperature Diff High Alarm Level 1")), //
		RACK_1_ALARM_LEVEL_1_SOC_LOW(new Doc().level(Level.WARNING).text("Cluster 1 SOC Low Alarm Level 1")), //
		RACK_1_ALARM_LEVEL_1_CELL_CHA_TEMP_LOW(
				new Doc().level(Level.WARNING).text("Cluster 1 Cell Charge Temperature Low Alarm Level 1")), //
		RACK_1_ALARM_LEVEL_1_CELL_CHA_TEMP_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 1 Cell Charge Temperature High Alarm Level 1")), //
		RACK_1_ALARM_LEVEL_1_DISCHA_CURRENT_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 1 Discharge Current High Alarm Level 1")), //
		RACK_1_ALARM_LEVEL_1_TOTAL_VOLTAGE_LOW(
				new Doc().level(Level.WARNING).text("Cluster 1 Total Voltage Low Alarm Level 1")), //
		RACK_1_ALARM_LEVEL_1_CELL_VOLTAGE_LOW(new Doc().level(Level.WARNING).text("Cluster 1 Cell Voltage Low Alarm Level 1")), //
		RACK_1_ALARM_LEVEL_1_CHA_CURRENT_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 1 Charge Current High Alarm Level 1")), //
		RACK_1_ALARM_LEVEL_1_TOTAL_VOLTAGE_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 1 Total Voltage High Alarm Level 1")), //
		RACK_1_ALARM_LEVEL_1_CELL_VOLTAGE_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 1 Cell Voltage High Alarm Level 1")), //
		RACK_1_RUN_STATE(new Doc().options(ClusterRunState.values())), //
		RACK_1_FAILURE_INITIALIZATION(new Doc().level(Level.FAULT).text("Initialization failure")), //
		RACK_1_FAILURE_EEPROM(new Doc().level(Level.FAULT).text("EEPROM fault")), //
		RACK_1_FAILURE_INTRANET_COMMUNICATION(new Doc().level(Level.FAULT).text("Intranet communication fault")), //
		RACK_1_FAILURE_TEMP_SAMPLING_LINE(new Doc().level(Level.FAULT).text("Temperature sampling line fault")), //
		RACK_1_FAILURE_BALANCING_MODULE(new Doc().level(Level.FAULT).text("Balancing module fault")), //
		RACK_1_FAILURE_TEMP_SENSOR(new Doc().level(Level.FAULT).text("Temperature sensor fault")), //
		RACK_1_FAILURE_TEMP_SAMPLING(new Doc().level(Level.FAULT).text("Temperature sampling fault")), //
		RACK_1_FAILURE_VOLTAGE_SAMPLING(new Doc().level(Level.FAULT).text("Voltage sampling fault")), //
		RACK_1_FAILURE_LTC6803(new Doc().level(Level.FAULT).text("LTC6803 fault")), //
		RACK_1_FAILURE_CONNECTOR_WIRE(new Doc().level(Level.FAULT).text("connector wire fault")), //
		RACK_1_FAILURE_SAMPLING_WIRE(new Doc().level(Level.FAULT).text("sampling wire fault")), //
	
		RACK_1_BATTERY_000_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_001_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_002_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_003_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_004_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_005_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_006_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_007_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_008_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_009_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_010_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_011_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_012_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_013_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_014_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_015_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_016_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_017_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_018_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_019_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_020_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_021_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_022_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_023_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_024_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_025_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_026_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_027_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_028_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_029_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_030_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_031_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_032_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_033_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_034_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_035_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_036_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_037_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_038_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_039_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_040_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_041_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_042_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_043_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_044_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_045_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_046_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_047_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_048_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_049_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_050_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_051_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_052_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_053_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_054_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_055_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_056_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_057_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_058_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_059_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_060_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_061_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_062_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_063_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_064_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_065_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_066_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_067_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_068_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_069_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_070_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_071_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_072_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_073_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_074_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_075_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_076_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_077_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_078_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_079_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_080_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_081_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_082_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_083_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_084_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_085_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_086_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_087_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_088_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_089_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_090_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_091_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_092_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_093_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_094_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_095_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_096_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_097_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_098_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_099_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_100_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_101_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_102_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_103_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_104_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_105_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_106_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_107_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_108_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_109_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_110_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_111_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_112_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_113_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_114_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_115_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_116_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_117_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_118_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_119_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_120_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_121_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_122_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_123_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_124_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_125_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_126_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_127_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_128_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_129_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_130_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_131_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_132_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_133_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_134_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_135_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_136_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_137_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_138_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_139_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_140_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_141_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_142_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_143_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_144_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_145_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_146_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_147_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_148_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_149_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_150_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_151_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_152_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_153_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_154_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_155_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_156_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_157_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_158_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_159_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_160_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_161_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_162_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_163_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_164_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_165_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_166_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_167_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_168_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_169_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_170_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_171_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_172_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_173_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_174_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_175_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_176_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_177_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_178_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_179_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_180_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_181_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_182_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_183_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_184_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_185_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_186_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_187_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_188_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_189_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_190_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_191_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_192_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_193_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_194_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_195_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_196_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_197_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_198_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_199_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_200_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_201_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_202_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_203_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_204_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_205_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_206_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_207_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_208_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_209_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_210_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_211_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_212_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_213_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_214_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_1_BATTERY_215_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		
		RACK_1_BATTERY_000_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_001_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_002_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_003_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_004_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_005_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_006_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_007_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_008_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_009_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_010_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_011_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_012_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_013_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_014_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_015_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_016_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_017_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_018_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_019_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_020_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_021_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_022_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_023_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_024_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_025_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_026_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_027_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_028_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_029_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_030_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_031_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_032_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_033_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_034_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_035_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_036_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_037_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_038_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_039_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_040_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_041_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_042_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_043_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_044_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_045_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_046_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_047_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_048_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_049_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_050_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_051_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_052_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_053_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_054_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_055_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_056_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_057_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_058_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_059_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_060_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_061_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_062_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_063_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_064_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_065_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_066_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_067_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_068_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_069_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_070_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_071_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_072_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_073_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_074_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_075_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_076_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_077_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_078_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_079_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_080_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_081_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_082_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_083_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_084_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_085_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_086_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_087_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_088_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_089_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_090_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_091_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_092_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_093_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_094_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_095_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_096_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_097_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_098_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_099_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_100_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_101_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_102_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_103_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_104_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_105_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_106_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_1_BATTERY_107_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		
		RACK_2_STATE(new Doc().unit(Unit.NONE)), //
		RACK_2_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_CURRENT(new Doc().unit(Unit.MILLIAMPERE)), //
		RACK_2_CHARGE_INDICATION(new Doc().options(ChargeIndication.values())), //
		RACK_2_SOC(new Doc().unit(Unit.PERCENT)), //
		RACK_2_SOH(new Doc().unit(Unit.PERCENT)), //
		RACK_2_MAX_CELL_VOLTAGE_ID(new Doc().unit(Unit.NONE)), //
		RACK_2_MAX_CELL_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_MIN_CELL_VOLTAGE_ID(new Doc().unit(Unit.NONE)), //
		RACK_2_MIN_CELL_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_MAX_CELL_TEMPERATURE_ID(new Doc().unit(Unit.NONE)), //
		RACK_2_MAX_CELL_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_MIN_CELL_TEMPERATURE_ID(new Doc().unit(Unit.NONE)), //
		RACK_2_MIN_CELL_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_ALARM_LEVEL_2_CELL_DISCHA_TEMP_LOW(
				new Doc().level(Level.WARNING).text("Cluster 2 Cell Discharge Temperature Low Alarm Level 2")), //
		RACK_2_ALARM_LEVEL_2_CELL_DISCHA_TEMP_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 2 Cell Discharge Temperature High Alarm Level 2")), //
		RACK_2_ALARM_LEVEL_2_INSULATION_LOW(new Doc().level(Level.WARNING).text("Cluster 2Insulation Low Alarm Level 2")), //
		RACK_2_ALARM_LEVEL_2_CELL_CHA_TEMP_LOW(
				new Doc().level(Level.WARNING).text("Cluster 2 Cell Charge Temperature Low Alarm Level 2")), //
		RACK_2_ALARM_LEVEL_2_CELL_CHA_TEMP_HIGH(
				new Doc().level(Level.WARNING).text("Cluster2 Cell Charge Temperature High Alarm Level 2")), //
		RACK_2_ALARM_LEVEL_2_DISCHA_CURRENT_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 2 Discharge Current High Alarm Level 2")), //
		RACK_2_ALARM_LEVEL_2_TOTAL_VOLTAGE_LOW(
				new Doc().level(Level.WARNING).text("Cluster 2 Total Voltage Low Alarm Level 2")), //
		RACK_2_ALARM_LEVEL_2_CELL_VOLTAGE_LOW(new Doc().level(Level.WARNING).text("Cluster 2 Cell Voltage Low Alarm Level 2")), //
		RACK_2_ALARM_LEVEL_2_CHA_CURRENT_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 2 Charge Current High Alarm Level 2")), //
		RACK_2_ALARM_LEVEL_2_TOTAL_VOLTAGE_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 2 Total Voltage High Alarm Level 2")), //
		RACK_2_ALARM_LEVEL_2_CELL_VOLTAGE_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 2 Cell Voltage High Alarm Level 2")), //
		RACK_2_ALARM_LEVEL_1_CELL_DISCHA_TEMP_LOW(
				new Doc().level(Level.WARNING).text("Cluster 2 Cell Discharge Temperature Low Alarm Level 1")), //
		RACK_2_ALARM_LEVEL_1_CELL_DISCHA_TEMP_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 2 Cell Discharge Temperature High Alarm Level 1")), //
		RACK_2_ALARM_LEVEL_1_TOTAL_VOLTAGE_DIFF_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 2 Total Voltage Diff High Alarm Level 1")), //
		RACK_2_ALARM_LEVEL_1_INSULATION_LOW(new Doc().level(Level.WARNING).text("Cluster 2 Insulation Low Alarm Level1")), //
		RACK_2_ALARM_LEVEL_1_CELL_VOLTAGE_DIFF_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 2 Cell Voltage Diff High Alarm Level 1")), //
		RACK_2_ALARM_LEVEL_1_CELL_TEMP_DIFF_HIGH(
				new Doc().level(Level.WARNING).text("Cluster X Cell temperature Diff High Alarm Level 1")), //
		RACK_2_ALARM_LEVEL_1_SOC_LOW(new Doc().level(Level.WARNING).text("Cluster 2 SOC Low Alarm Level 1")), //
		RACK_2_ALARM_LEVEL_1_CELL_CHA_TEMP_LOW(
				new Doc().level(Level.WARNING).text("Cluster 2 Cell Charge Temperature Low Alarm Level 1")), //
		RACK_2_ALARM_LEVEL_1_CELL_CHA_TEMP_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 2 Cell Charge Temperature High Alarm Level 1")), //
		RACK_2_ALARM_LEVEL_1_DISCHA_CURRENT_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 2 Discharge Current High Alarm Level 1")), //
		RACK_2_ALARM_LEVEL_1_TOTAL_VOLTAGE_LOW(
				new Doc().level(Level.WARNING).text("Cluster 2 Total Voltage Low Alarm Level 1")), //
		RACK_2_ALARM_LEVEL_1_CELL_VOLTAGE_LOW(new Doc().level(Level.WARNING).text("Cluster 2 Cell Voltage Low Alarm Level 1")), //
		RACK_2_ALARM_LEVEL_1_CHA_CURRENT_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 2 Charge Current High Alarm Level 1")), //
		RACK_2_ALARM_LEVEL_1_TOTAL_VOLTAGE_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 2 Total Voltage High Alarm Level 1")), //
		RACK_2_ALARM_LEVEL_1_CELL_VOLTAGE_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 2 Cell Voltage High Alarm Level 1")), //
		RACK_2_RUN_STATE(new Doc().options(ClusterRunState.values())), //
		RACK_2_FAILURE_INITIALIZATION(new Doc().level(Level.FAULT).text("Initialization failure")), //
		RACK_2_FAILURE_EEPROM(new Doc().level(Level.FAULT).text("EEPROM fault")), //
		RACK_2_FAILURE_INTRANET_COMMUNICATION(new Doc().level(Level.FAULT).text("Intranet communication fault")), //
		RACK_2_FAILURE_TEMP_SAMPLING_LINE(new Doc().level(Level.FAULT).text("Temperature sampling line fault")), //
		RACK_2_FAILURE_BALANCING_MODULE(new Doc().level(Level.FAULT).text("Balancing module fault")), //
		RACK_2_FAILURE_TEMP_SENSOR(new Doc().level(Level.FAULT).text("Temperature sensor fault")), //
		RACK_2_FAILURE_TEMP_SAMPLING(new Doc().level(Level.FAULT).text("Temperature sampling fault")), //
		RACK_2_FAILURE_VOLTAGE_SAMPLING(new Doc().level(Level.FAULT).text("Voltage sampling fault")), //
		RACK_2_FAILURE_LTC6803(new Doc().level(Level.FAULT).text("LTC6803 fault")), //
		RACK_2_FAILURE_CONNECTOR_WIRE(new Doc().level(Level.FAULT).text("connector wire fault")), //
		RACK_2_FAILURE_SAMPLING_WIRE(new Doc().level(Level.FAULT).text("sampling wire fault")), //
	
		RACK_2_BATTERY_000_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_001_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_002_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_003_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_004_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_005_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_006_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_007_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_008_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_009_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_010_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_011_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_012_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_013_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_014_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_015_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_016_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_017_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_018_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_019_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_020_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_021_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_022_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_023_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_024_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_025_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_026_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_027_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_028_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_029_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_030_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_031_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_032_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_033_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_034_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_035_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_036_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_037_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_038_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_039_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_040_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_041_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_042_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_043_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_044_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_045_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_046_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_047_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_048_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_049_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_050_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_051_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_052_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_053_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_054_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_055_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_056_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_057_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_058_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_059_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_060_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_061_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_062_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_063_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_064_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_065_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_066_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_067_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_068_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_069_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_070_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_071_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_072_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_073_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_074_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_075_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_076_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_077_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_078_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_079_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_080_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_081_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_082_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_083_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_084_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_085_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_086_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_087_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_088_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_089_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_090_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_091_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_092_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_093_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_094_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_095_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_096_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_097_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_098_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_099_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_100_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_101_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_102_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_103_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_104_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_105_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_106_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_107_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_108_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_109_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_110_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_111_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_112_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_113_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_114_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_115_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_116_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_117_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_118_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_119_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_120_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_121_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_122_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_123_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_124_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_125_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_126_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_127_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_128_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_129_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_130_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_131_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_132_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_133_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_134_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_135_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_136_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_137_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_138_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_139_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_140_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_141_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_142_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_143_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_144_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_145_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_146_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_147_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_148_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_149_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_150_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_151_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_152_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_153_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_154_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_155_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_156_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_157_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_158_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_159_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_160_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_161_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_162_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_163_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_164_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_165_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_166_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_167_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_168_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_169_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_170_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_171_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_172_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_173_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_174_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_175_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_176_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_177_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_178_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_179_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_180_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_181_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_182_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_183_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_184_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_185_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_186_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_187_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_188_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_189_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_190_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_191_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_192_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_193_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_194_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_195_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_196_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_197_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_198_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_199_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_200_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_201_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_202_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_203_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_204_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_205_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_206_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_207_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_208_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_209_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_210_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_211_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_212_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_213_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_214_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_2_BATTERY_215_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		
		RACK_2_BATTERY_000_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_001_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_002_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_003_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_004_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_005_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_006_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_007_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_008_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_009_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_010_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_011_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_012_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_013_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_014_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_015_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_016_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_017_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_018_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_019_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_020_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_021_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_022_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_023_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_024_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_025_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_026_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_027_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_028_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_029_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_030_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_031_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_032_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_033_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_034_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_035_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_036_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_037_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_038_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_039_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_040_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_041_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_042_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_043_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_044_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_045_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_046_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_047_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_048_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_049_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_050_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_051_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_052_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_053_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_054_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_055_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_056_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_057_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_058_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_059_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_060_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_061_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_062_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_063_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_064_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_065_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_066_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_067_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_068_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_069_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_070_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_071_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_072_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_073_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_074_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_075_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_076_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_077_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_078_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_079_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_080_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_081_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_082_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_083_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_084_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_085_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_086_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_087_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_088_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_089_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_090_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_091_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_092_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_093_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_094_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_095_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_096_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_097_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_098_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_099_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_100_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_101_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_102_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_103_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_104_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_105_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_106_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_2_BATTERY_107_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		
		RACK_3_STATE(new Doc().unit(Unit.NONE)), //
		RACK_3_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_CURRENT(new Doc().unit(Unit.MILLIAMPERE)), //
		RACK_3_CHARGE_INDICATION(new Doc().options(ChargeIndication.values())), //
		RACK_3_SOC(new Doc().unit(Unit.PERCENT)), //
		RACK_3_SOH(new Doc().unit(Unit.PERCENT)), //
		RACK_3_MAX_CELL_VOLTAGE_ID(new Doc().unit(Unit.NONE)), //
		RACK_3_MAX_CELL_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_MIN_CELL_VOLTAGE_ID(new Doc().unit(Unit.NONE)), //
		RACK_3_MIN_CELL_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_MAX_CELL_TEMPERATURE_ID(new Doc().unit(Unit.NONE)), //
		RACK_3_MAX_CELL_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_MIN_CELL_TEMPERATURE_ID(new Doc().unit(Unit.NONE)), //
		RACK_3_MIN_CELL_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_ALARM_LEVEL_2_CELL_DISCHA_TEMP_LOW(
				new Doc().level(Level.WARNING).text("Cluster 3 Cell Discharge Temperature Low Alarm Level 2")), //
		RACK_3_ALARM_LEVEL_2_CELL_DISCHA_TEMP_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 3 Cell Discharge Temperature High Alarm Level 2")), //
		RACK_3_ALARM_LEVEL_2_INSULATION_LOW(new Doc().level(Level.WARNING).text("Cluster 3Insulation Low Alarm Level 2")), //
		RACK_3_ALARM_LEVEL_2_CELL_CHA_TEMP_LOW(
				new Doc().level(Level.WARNING).text("Cluster 3 Cell Charge Temperature Low Alarm Level 2")), //
		RACK_3_ALARM_LEVEL_2_CELL_CHA_TEMP_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 3 Cell Charge Temperature High Alarm Level 2")), //
		RACK_3_ALARM_LEVEL_2_DISCHA_CURRENT_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 3 Discharge Current High Alarm Level 2")), //
		RACK_3_ALARM_LEVEL_2_TOTAL_VOLTAGE_LOW(
				new Doc().level(Level.WARNING).text("Cluster 3 Total Voltage Low Alarm Level 2")), //
		RACK_3_ALARM_LEVEL_2_CELL_VOLTAGE_LOW(new Doc().level(Level.WARNING).text("Cluster 3 Cell Voltage Low Alarm Level 2")), //
		RACK_3_ALARM_LEVEL_2_CHA_CURRENT_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 3 Charge Current High Alarm Level 2")), //
		RACK_3_ALARM_LEVEL_2_TOTAL_VOLTAGE_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 3 Total Voltage High Alarm Level 2")), //
		RACK_3_ALARM_LEVEL_2_CELL_VOLTAGE_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 3 Cell Voltage High Alarm Level 2")), //
		RACK_3_ALARM_LEVEL_1_CELL_DISCHA_TEMP_LOW(
				new Doc().level(Level.WARNING).text("Cluster 3 Cell Discharge Temperature Low Alarm Level 1")), //
		RACK_3_ALARM_LEVEL_1_CELL_DISCHA_TEMP_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 3 Cell Discharge Temperature High Alarm Level 1")), //
		RACK_3_ALARM_LEVEL_1_TOTAL_VOLTAGE_DIFF_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 3 Total Voltage Diff High Alarm Level 1")), //
		RACK_3_ALARM_LEVEL_1_INSULATION_LOW(new Doc().level(Level.WARNING).text("Cluster 3 Insulation Low Alarm Level1")), //
		RACK_3_ALARM_LEVEL_1_CELL_VOLTAGE_DIFF_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 3 Cell Voltage Diff High Alarm Level 1")), //
		RACK_3_ALARM_LEVEL_1_CELL_TEMP_DIFF_HIGH(
				new Doc().level(Level.WARNING).text("Cluster X Cell temperature Diff High Alarm Level 1")), //
		RACK_3_ALARM_LEVEL_1_SOC_LOW(new Doc().level(Level.WARNING).text("Cluster 3 SOC Low Alarm Level 1")), //
		RACK_3_ALARM_LEVEL_1_CELL_CHA_TEMP_LOW(
				new Doc().level(Level.WARNING).text("Cluster 3 Cell Charge Temperature Low Alarm Level 1")), //
		RACK_3_ALARM_LEVEL_1_CELL_CHA_TEMP_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 3 Cell Charge Temperature High Alarm Level 1")), //
		RACK_3_ALARM_LEVEL_1_DISCHA_CURRENT_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 3 Discharge Current High Alarm Level 1")), //
		RACK_3_ALARM_LEVEL_1_TOTAL_VOLTAGE_LOW(
				new Doc().level(Level.WARNING).text("Cluster 3 Total Voltage Low Alarm Level 1")), //
		RACK_3_ALARM_LEVEL_1_CELL_VOLTAGE_LOW(new Doc().level(Level.WARNING).text("Cluster 3 Cell Voltage Low Alarm Level 1")), //
		RACK_3_ALARM_LEVEL_1_CHA_CURRENT_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 3 Charge Current High Alarm Level 1")), //
		RACK_3_ALARM_LEVEL_1_TOTAL_VOLTAGE_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 3 Total Voltage High Alarm Level 1")), //
		RACK_3_ALARM_LEVEL_1_CELL_VOLTAGE_HIGH(
				new Doc().level(Level.WARNING).text("Cluster 3 Cell Voltage High Alarm Level 1")), //
		RACK_3_RUN_STATE(new Doc().options(ClusterRunState.values())), //
		RACK_3_FAILURE_INITIALIZATION(new Doc().level(Level.FAULT).text("Initialization failure")), //
		RACK_3_FAILURE_EEPROM(new Doc().level(Level.FAULT).text("EEPROM fault")), //
		RACK_3_FAILURE_INTRANET_COMMUNICATION(new Doc().level(Level.FAULT).text("Intranet communication fault")), //
		RACK_3_FAILURE_TEMP_SAMPLING_LINE(new Doc().level(Level.FAULT).text("Temperature sampling line fault")), //
		RACK_3_FAILURE_BALANCING_MODULE(new Doc().level(Level.FAULT).text("Balancing module fault")), //
		RACK_3_FAILURE_TEMP_SENSOR(new Doc().level(Level.FAULT).text("Temperature sensor fault")), //
		RACK_3_FAILURE_TEMP_SAMPLING(new Doc().level(Level.FAULT).text("Temperature sampling fault")), //
		RACK_3_FAILURE_VOLTAGE_SAMPLING(new Doc().level(Level.FAULT).text("Voltage sampling fault")), //
		RACK_3_FAILURE_LTC6803(new Doc().level(Level.FAULT).text("LTC6803 fault")), //
		RACK_3_FAILURE_CONNECTOR_WIRE(new Doc().level(Level.FAULT).text("connector wire fault")), //
		RACK_3_FAILURE_SAMPLING_WIRE(new Doc().level(Level.FAULT).text("sampling wire fault")), //
	
		RACK_3_BATTERY_000_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_001_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_002_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_003_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_004_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_005_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_006_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_007_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_008_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_009_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_010_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_011_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_012_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_013_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_014_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_015_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_016_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_017_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_018_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_019_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_020_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_021_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_022_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_023_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_024_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_025_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_026_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_027_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_028_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_029_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_030_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_031_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_032_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_033_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_034_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_035_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_036_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_037_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_038_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_039_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_040_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_041_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_042_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_043_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_044_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_045_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_046_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_047_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_048_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_049_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_050_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_051_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_052_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_053_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_054_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_055_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_056_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_057_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_058_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_059_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_060_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_061_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_062_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_063_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_064_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_065_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_066_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_067_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_068_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_069_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_070_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_071_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_072_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_073_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_074_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_075_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_076_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_077_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_078_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_079_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_080_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_081_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_082_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_083_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_084_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_085_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_086_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_087_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_088_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_089_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_090_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_091_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_092_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_093_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_094_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_095_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_096_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_097_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_098_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_099_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_100_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_101_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_102_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_103_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_104_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_105_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_106_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_107_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_108_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_109_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_110_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_111_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_112_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_113_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_114_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_115_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_116_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_117_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_118_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_119_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_120_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_121_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_122_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_123_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_124_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_125_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_126_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_127_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_128_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_129_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_130_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_131_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_132_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_133_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_134_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_135_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_136_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_137_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_138_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_139_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_140_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_141_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_142_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_143_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_144_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_145_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_146_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_147_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_148_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_149_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_150_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_151_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_152_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_153_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_154_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_155_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_156_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_157_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_158_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_159_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_160_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_161_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_162_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_163_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_164_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_165_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_166_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_167_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_168_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_169_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_170_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_171_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_172_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_173_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_174_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_175_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_176_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_177_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_178_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_179_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_180_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_181_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_182_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_183_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_184_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_185_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_186_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_187_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_188_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_189_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_190_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_191_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_192_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_193_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_194_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_195_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_196_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_197_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_198_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_199_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_200_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_201_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_202_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_203_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_204_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_205_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_206_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_207_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_208_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_209_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_210_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_211_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_212_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_213_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_214_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		RACK_3_BATTERY_215_VOLTAGE(new Doc().unit(Unit.MILLIVOLT)), //
		
		RACK_3_BATTERY_000_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_001_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_002_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_003_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_004_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_005_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_006_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_007_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_008_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_009_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_010_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_011_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_012_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_013_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_014_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_015_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_016_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_017_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_018_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_019_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_020_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_021_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_022_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_023_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_024_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_025_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_026_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_027_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_028_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_029_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_030_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_031_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_032_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_033_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_034_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_035_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_036_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_037_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_038_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_039_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_040_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_041_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_042_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_043_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_044_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_045_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_046_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_047_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_048_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_049_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_050_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_051_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_052_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_053_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_054_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_055_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_056_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_057_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_058_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_059_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_060_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_061_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_062_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_063_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_064_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_065_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_066_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_067_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_068_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_069_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_070_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_071_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_072_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_073_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_074_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_075_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_076_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_077_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_078_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_079_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_080_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_081_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_082_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_083_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_084_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_085_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_086_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_087_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_088_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_089_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_090_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_091_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_092_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_093_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_094_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_095_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_096_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_097_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_098_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_099_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_100_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_101_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_102_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_103_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_104_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_105_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_106_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		RACK_3_BATTERY_107_TEMPERATURE(new Doc().unit(Unit.DEZIDEGREE_CELSIUS)), //
		
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
	
	private static final int BASE_ADDRESS_RACK_1 = 0x2000;
	private static final int BASE_ADDRESS_RACK_2 = 0x3000;
	private static final int BASE_ADDRESS_RACK_3 = 0x4000;
	
	@Override
	protected ModbusProtocol defineModbusProtocol() {
		
		return new ModbusProtocol(this, //
				// -------- Registers of master --------------------------------------
				new FC16WriteRegistersTask(0x1017,  
						m(ChannelId.START_STOP, new UnsignedWordElement(0x1017)), //
						m(ChannelId.RACK_1_USAGE, new UnsignedWordElement(0x1018)), //
						m(ChannelId.RACK_2_USAGE, new UnsignedWordElement(0x1019)), //
						m(ChannelId.RACK_3_USAGE, new UnsignedWordElement(0x101A)) //						
				) , //
				new FC3ReadRegistersTask(0x1017, Priority.HIGH,  
						m(ChannelId.START_STOP, new UnsignedWordElement(0x1017)), //
						m(ChannelId.RACK_1_USAGE, new UnsignedWordElement(0x1018)), //
						m(ChannelId.RACK_2_USAGE, new UnsignedWordElement(0x1019)), //
						m(ChannelId.RACK_3_USAGE, new UnsignedWordElement(0x101A)) //						
				) , //
				
				new FC3ReadRegistersTask(0x1044, Priority.LOW, //
						m(ChannelId.CHARGE_INDICATION, new UnsignedWordElement(0x1044)), //
						m(ChannelId.CURRENT, new UnsignedWordElement(0x1045), //
								ElementToChannelConverter.SCALE_FACTOR_2), //
						new DummyRegisterElement(0x1046),
						m(Battery.ChannelId.SOC, new UnsignedWordElement(0x1047)), //
						m(ChannelId.SYSTEM_RUNNING_STATE, new UnsignedWordElement(0x1048)), //
						m(ChannelId.VOLTAGE, new UnsignedWordElement(0x1049), //
								ElementToChannelConverter.SCALE_FACTOR_2) //
				), //
				new FC3ReadRegistersTask(0x1081, Priority.LOW, //
						bm(new UnsignedWordElement(0x1081)) //
								.m(ChannelId.MASTER_ALARM_PCS_OUT_OF_CONTROL, 1) //
								.m(ChannelId.MASTER_ALARM_PCS_COMMUNICATION_FAULT, 0) //								
								.build(), //
						bm(new UnsignedWordElement(0x1082)) //
								.m(ChannelId.SUB_MASTER_COMMUNICATION_FAULT_ALARM_MASTER_1, 0) //
								.m(ChannelId.SUB_MASTER_COMMUNICATION_FAULT_ALARM_MASTER_2, 1) //
								.m(ChannelId.SUB_MASTER_COMMUNICATION_FAULT_ALARM_MASTER_3, 2) //								
								.build() //						
				), //
				// ---------------- registers of rack 1 -----------------------------
				new FC16WriteRegistersTask(BASE_ADDRESS_RACK_1 + 0x1, //
						m(ChannelId.RACK_1_STATE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x1)) //
				),
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_1 + 0x1, Priority.HIGH, //
						m(ChannelId.RACK_1_STATE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x1)) //
				),
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_1 + 0x100, Priority.LOW, //
						m(ChannelId.RACK_1_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x100), //
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(ChannelId.RACK_1_CURRENT, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x101), //
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(ChannelId.RACK_1_CHARGE_INDICATION, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x102)), //
						m(ChannelId.RACK_1_SOC, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x103)), //
						m(ChannelId.RACK_1_SOH, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x104)), //
						m(ChannelId.RACK_1_MAX_CELL_VOLTAGE_ID, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x105)), //
						m(ChannelId.RACK_1_MAX_CELL_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x106)), //
						m(ChannelId.RACK_1_MIN_CELL_VOLTAGE_ID, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x107)), //
						m(ChannelId.RACK_1_MIN_CELL_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x108)), //
						m(ChannelId.RACK_1_MAX_CELL_TEMPERATURE_ID, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x109)), //
						m(ChannelId.RACK_1_MAX_CELL_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x10A)), //
						m(ChannelId.RACK_1_MIN_CELL_TEMPERATURE_ID, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x10B)), //
						m(ChannelId.RACK_1_MIN_CELL_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x10C)) //						
				),
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_1 + 0x140, Priority.LOW, //
						bm(new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x140)) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_2_CELL_VOLTAGE_HIGH, 0) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_2_TOTAL_VOLTAGE_HIGH, 1) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_2_CHA_CURRENT_HIGH, 2) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_2_CELL_VOLTAGE_LOW, 3) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_2_TOTAL_VOLTAGE_LOW, 4) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_2_DISCHA_CURRENT_HIGH, 5) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_2_CELL_CHA_TEMP_HIGH, 6) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_2_CELL_CHA_TEMP_LOW, 7) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_2_INSULATION_LOW, 12) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_2_CELL_DISCHA_TEMP_HIGH, 14) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_2_CELL_DISCHA_TEMP_LOW, 15) //
								.build(), //
						bm(new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x141)) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_1_CELL_VOLTAGE_HIGH, 0) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_1_TOTAL_VOLTAGE_HIGH, 1) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_1_CHA_CURRENT_HIGH, 2) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_1_CELL_VOLTAGE_LOW, 3) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_1_TOTAL_VOLTAGE_LOW, 4) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_1_DISCHA_CURRENT_HIGH, 5) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_1_CELL_CHA_TEMP_HIGH, 6) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_1_CELL_CHA_TEMP_LOW, 7) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_1_SOC_LOW, 8) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_1_CELL_TEMP_DIFF_HIGH, 9) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_1_CELL_VOLTAGE_DIFF_HIGH, 11) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_1_INSULATION_LOW, 12) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_1_TOTAL_VOLTAGE_DIFF_HIGH, 13) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_1_CELL_DISCHA_TEMP_HIGH, 14) //
								.m(ChannelId.RACK_1_ALARM_LEVEL_1_CELL_DISCHA_TEMP_LOW, 15) //
								.build(), //
						m(ChannelId.RACK_1_RUN_STATE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x142)) //
				), //
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_1 + 0x185, Priority.LOW, //
						bm(new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x185)) //
								.m(ChannelId.RACK_1_FAILURE_SAMPLING_WIRE, 0)//
								.m(ChannelId.RACK_1_FAILURE_CONNECTOR_WIRE, 1)//
								.m(ChannelId.RACK_1_FAILURE_LTC6803, 2)//
								.m(ChannelId.RACK_1_FAILURE_VOLTAGE_SAMPLING, 3)//
								.m(ChannelId.RACK_1_FAILURE_TEMP_SAMPLING, 4)//
								.m(ChannelId.RACK_1_FAILURE_TEMP_SENSOR, 5)//
								.m(ChannelId.RACK_1_FAILURE_BALANCING_MODULE, 8)//
								.m(ChannelId.RACK_1_FAILURE_TEMP_SAMPLING_LINE, 9)//
								.m(ChannelId.RACK_1_FAILURE_INTRANET_COMMUNICATION, 10)//
								.m(ChannelId.RACK_1_FAILURE_EEPROM, 11)//
								.m(ChannelId.RACK_1_FAILURE_INITIALIZATION, 12)//
								.build() //
				), //
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_1 + 0x800, Priority.LOW, //
						m(ChannelId.RACK_1_BATTERY_000_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x800)), //
						m(ChannelId.RACK_1_BATTERY_001_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x801)), //
						m(ChannelId.RACK_1_BATTERY_002_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x802)), //
						m(ChannelId.RACK_1_BATTERY_003_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x803)), //
						m(ChannelId.RACK_1_BATTERY_004_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x804)), //
						m(ChannelId.RACK_1_BATTERY_005_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x805)), //
						m(ChannelId.RACK_1_BATTERY_006_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x806)), //
						m(ChannelId.RACK_1_BATTERY_007_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x807)), //
						m(ChannelId.RACK_1_BATTERY_008_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x808)), //
						m(ChannelId.RACK_1_BATTERY_009_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x809)), //
						m(ChannelId.RACK_1_BATTERY_010_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x80A)), //
						m(ChannelId.RACK_1_BATTERY_011_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x80B)), //
						m(ChannelId.RACK_1_BATTERY_012_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x80C)), //
						m(ChannelId.RACK_1_BATTERY_013_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x80D)), //
						m(ChannelId.RACK_1_BATTERY_014_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x80E)), //
						m(ChannelId.RACK_1_BATTERY_015_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x80F)), //
						m(ChannelId.RACK_1_BATTERY_016_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x810)), //
						m(ChannelId.RACK_1_BATTERY_017_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x811)), //
						m(ChannelId.RACK_1_BATTERY_018_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x812)), //
						m(ChannelId.RACK_1_BATTERY_019_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x813)), //
						m(ChannelId.RACK_1_BATTERY_020_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x814)), //
						m(ChannelId.RACK_1_BATTERY_021_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x815)), //
						m(ChannelId.RACK_1_BATTERY_022_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x816)), //
						m(ChannelId.RACK_1_BATTERY_023_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x817)), //
						m(ChannelId.RACK_1_BATTERY_024_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x818)), //
						m(ChannelId.RACK_1_BATTERY_025_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x819)), //
						m(ChannelId.RACK_1_BATTERY_026_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x81A)), //
						m(ChannelId.RACK_1_BATTERY_027_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x81B)), //
						m(ChannelId.RACK_1_BATTERY_028_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x81C)), //
						m(ChannelId.RACK_1_BATTERY_029_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x81D)), //
						m(ChannelId.RACK_1_BATTERY_030_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x81E)), //
						m(ChannelId.RACK_1_BATTERY_031_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x81F)), //
						m(ChannelId.RACK_1_BATTERY_032_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x820)), //
						m(ChannelId.RACK_1_BATTERY_033_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x821)), //
						m(ChannelId.RACK_1_BATTERY_034_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x822)), //
						m(ChannelId.RACK_1_BATTERY_035_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x823)), //
						m(ChannelId.RACK_1_BATTERY_036_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x824)), //
						m(ChannelId.RACK_1_BATTERY_037_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x825)), //
						m(ChannelId.RACK_1_BATTERY_038_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x826)), //
						m(ChannelId.RACK_1_BATTERY_039_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x827)), //
						m(ChannelId.RACK_1_BATTERY_040_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x828)), //
						m(ChannelId.RACK_1_BATTERY_041_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x829)), //
						m(ChannelId.RACK_1_BATTERY_042_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x82A)), //
						m(ChannelId.RACK_1_BATTERY_043_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x82B)), //
						m(ChannelId.RACK_1_BATTERY_044_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x82C)), //
						m(ChannelId.RACK_1_BATTERY_045_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x82D)), //
						m(ChannelId.RACK_1_BATTERY_046_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x82E)), //
						m(ChannelId.RACK_1_BATTERY_047_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x82F)), //
						m(ChannelId.RACK_1_BATTERY_048_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x830)), //
						m(ChannelId.RACK_1_BATTERY_049_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x831)), //
						m(ChannelId.RACK_1_BATTERY_050_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x832)), //
						m(ChannelId.RACK_1_BATTERY_051_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x833)), //
						m(ChannelId.RACK_1_BATTERY_052_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x834)), //
						m(ChannelId.RACK_1_BATTERY_053_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x835)), //
						m(ChannelId.RACK_1_BATTERY_054_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x836)), //
						m(ChannelId.RACK_1_BATTERY_055_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x837)), //
						m(ChannelId.RACK_1_BATTERY_056_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x838)), //
						m(ChannelId.RACK_1_BATTERY_057_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x839)), //
						m(ChannelId.RACK_1_BATTERY_058_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x83A)), //
						m(ChannelId.RACK_1_BATTERY_059_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x83B)), //
						m(ChannelId.RACK_1_BATTERY_060_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x83C)), //
						m(ChannelId.RACK_1_BATTERY_061_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x83D)), //
						m(ChannelId.RACK_1_BATTERY_062_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x83E)), //
						m(ChannelId.RACK_1_BATTERY_063_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x83F)), //
						m(ChannelId.RACK_1_BATTERY_064_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x840)), //
						m(ChannelId.RACK_1_BATTERY_065_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x841)), //
						m(ChannelId.RACK_1_BATTERY_066_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x842)), //
						m(ChannelId.RACK_1_BATTERY_067_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x843)), //
						m(ChannelId.RACK_1_BATTERY_068_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x844)), //
						m(ChannelId.RACK_1_BATTERY_069_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x845)), //
						m(ChannelId.RACK_1_BATTERY_070_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x846)), //
						m(ChannelId.RACK_1_BATTERY_071_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x847)), //
						m(ChannelId.RACK_1_BATTERY_072_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x848)), //
						m(ChannelId.RACK_1_BATTERY_073_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x849)), //
						m(ChannelId.RACK_1_BATTERY_074_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x84A)), //
						m(ChannelId.RACK_1_BATTERY_075_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x84B)), //
						m(ChannelId.RACK_1_BATTERY_076_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x84C)), //
						m(ChannelId.RACK_1_BATTERY_077_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x84D)), //
						m(ChannelId.RACK_1_BATTERY_078_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x84E)), //
						m(ChannelId.RACK_1_BATTERY_079_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x84F)), //
						m(ChannelId.RACK_1_BATTERY_080_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x850)), //
						m(ChannelId.RACK_1_BATTERY_081_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x851)), //
						m(ChannelId.RACK_1_BATTERY_082_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x852)), //
						m(ChannelId.RACK_1_BATTERY_083_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x853)), //
						m(ChannelId.RACK_1_BATTERY_084_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x854)), //
						m(ChannelId.RACK_1_BATTERY_085_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x855)), //
						m(ChannelId.RACK_1_BATTERY_086_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x856)), //
						m(ChannelId.RACK_1_BATTERY_087_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x857)), //
						m(ChannelId.RACK_1_BATTERY_088_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x858)), //
						m(ChannelId.RACK_1_BATTERY_089_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x859)), //
						m(ChannelId.RACK_1_BATTERY_090_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x85A)), //
						m(ChannelId.RACK_1_BATTERY_091_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x85B)), //
						m(ChannelId.RACK_1_BATTERY_092_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x85C)), //
						m(ChannelId.RACK_1_BATTERY_093_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x85D)), //
						m(ChannelId.RACK_1_BATTERY_094_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x85E)), //
						m(ChannelId.RACK_1_BATTERY_095_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x85F)), //
						m(ChannelId.RACK_1_BATTERY_096_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x860)), //
						m(ChannelId.RACK_1_BATTERY_097_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x861)), //
						m(ChannelId.RACK_1_BATTERY_098_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x862)), //
						m(ChannelId.RACK_1_BATTERY_099_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x863)), //
						m(ChannelId.RACK_1_BATTERY_100_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x864)), //
						m(ChannelId.RACK_1_BATTERY_101_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x865)), //
						m(ChannelId.RACK_1_BATTERY_102_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x866)), //
						m(ChannelId.RACK_1_BATTERY_103_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x867)), //
						m(ChannelId.RACK_1_BATTERY_104_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x868)), //
						m(ChannelId.RACK_1_BATTERY_105_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x869)), //
						m(ChannelId.RACK_1_BATTERY_106_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x86A)), //
						m(ChannelId.RACK_1_BATTERY_107_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x86B)), //
						m(ChannelId.RACK_1_BATTERY_108_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x86C)), //
						m(ChannelId.RACK_1_BATTERY_109_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x86D)), //
						m(ChannelId.RACK_1_BATTERY_110_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x86E)), //
						m(ChannelId.RACK_1_BATTERY_111_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x86F)), //
						m(ChannelId.RACK_1_BATTERY_112_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x870)), //
						m(ChannelId.RACK_1_BATTERY_113_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x871)), //
						m(ChannelId.RACK_1_BATTERY_114_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x872)), //
						m(ChannelId.RACK_1_BATTERY_115_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x873)), //
						m(ChannelId.RACK_1_BATTERY_116_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x874)), //
						m(ChannelId.RACK_1_BATTERY_117_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x875)), //
						m(ChannelId.RACK_1_BATTERY_118_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x876)), //
						m(ChannelId.RACK_1_BATTERY_119_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x877)) //
				), //
				new FC3ReadRegistersTask(0x2878, Priority.LOW, //
						m(ChannelId.RACK_1_BATTERY_120_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x878)), //
						m(ChannelId.RACK_1_BATTERY_121_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x879)), //
						m(ChannelId.RACK_1_BATTERY_122_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x87A)), //
						m(ChannelId.RACK_1_BATTERY_123_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x87B)), //
						m(ChannelId.RACK_1_BATTERY_124_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x87C)), //
						m(ChannelId.RACK_1_BATTERY_125_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x87D)), //
						m(ChannelId.RACK_1_BATTERY_126_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x87E)), //
						m(ChannelId.RACK_1_BATTERY_127_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x87F)), //
						m(ChannelId.RACK_1_BATTERY_128_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x880)), //
						m(ChannelId.RACK_1_BATTERY_129_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x881)), //
						m(ChannelId.RACK_1_BATTERY_130_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x882)), //
						m(ChannelId.RACK_1_BATTERY_131_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x883)), //
						m(ChannelId.RACK_1_BATTERY_132_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x884)), //
						m(ChannelId.RACK_1_BATTERY_133_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x885)), //
						m(ChannelId.RACK_1_BATTERY_134_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x886)), //
						m(ChannelId.RACK_1_BATTERY_135_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x887)), //
						m(ChannelId.RACK_1_BATTERY_136_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x888)), //
						m(ChannelId.RACK_1_BATTERY_137_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x889)), //
						m(ChannelId.RACK_1_BATTERY_138_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x88A)), //
						m(ChannelId.RACK_1_BATTERY_139_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x88B)), //
						m(ChannelId.RACK_1_BATTERY_140_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x88C)), //
						m(ChannelId.RACK_1_BATTERY_141_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x88D)), //
						m(ChannelId.RACK_1_BATTERY_142_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x88E)), //
						m(ChannelId.RACK_1_BATTERY_143_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x88F)), //
						m(ChannelId.RACK_1_BATTERY_144_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x890)), //
						m(ChannelId.RACK_1_BATTERY_145_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x891)), //
						m(ChannelId.RACK_1_BATTERY_146_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x892)), //
						m(ChannelId.RACK_1_BATTERY_147_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x893)), //
						m(ChannelId.RACK_1_BATTERY_148_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x894)), //
						m(ChannelId.RACK_1_BATTERY_149_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x895)), //
						m(ChannelId.RACK_1_BATTERY_150_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x896)), //
						m(ChannelId.RACK_1_BATTERY_151_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x897)), //
						m(ChannelId.RACK_1_BATTERY_152_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x898)), //
						m(ChannelId.RACK_1_BATTERY_153_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x899)), //
						m(ChannelId.RACK_1_BATTERY_154_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x89A)), //
						m(ChannelId.RACK_1_BATTERY_155_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x89B)), //
						m(ChannelId.RACK_1_BATTERY_156_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x89C)), //
						m(ChannelId.RACK_1_BATTERY_157_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x89D)), //
						m(ChannelId.RACK_1_BATTERY_158_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x89E)), //
						m(ChannelId.RACK_1_BATTERY_159_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x89F)), //
						m(ChannelId.RACK_1_BATTERY_160_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8A0)), //
						m(ChannelId.RACK_1_BATTERY_161_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8A1)), //
						m(ChannelId.RACK_1_BATTERY_162_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8A2)), //
						m(ChannelId.RACK_1_BATTERY_163_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8A3)), //
						m(ChannelId.RACK_1_BATTERY_164_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8A4)), //
						m(ChannelId.RACK_1_BATTERY_165_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8A5)), //
						m(ChannelId.RACK_1_BATTERY_166_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8A6)), //
						m(ChannelId.RACK_1_BATTERY_167_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8A7)), //
						m(ChannelId.RACK_1_BATTERY_168_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8A8)), //
						m(ChannelId.RACK_1_BATTERY_169_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8A9)), //
						m(ChannelId.RACK_1_BATTERY_170_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8AA)), //
						m(ChannelId.RACK_1_BATTERY_171_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8AB)), //
						m(ChannelId.RACK_1_BATTERY_172_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8AC)), //
						m(ChannelId.RACK_1_BATTERY_173_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8AD)), //
						m(ChannelId.RACK_1_BATTERY_174_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8AE)), //
						m(ChannelId.RACK_1_BATTERY_175_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8AF)), //
						m(ChannelId.RACK_1_BATTERY_176_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8B0)), //
						m(ChannelId.RACK_1_BATTERY_177_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8B1)), //
						m(ChannelId.RACK_1_BATTERY_178_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8B2)), //
						m(ChannelId.RACK_1_BATTERY_179_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8B3)), //
						m(ChannelId.RACK_1_BATTERY_180_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8B4)), //
						m(ChannelId.RACK_1_BATTERY_181_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8B5)), //
						m(ChannelId.RACK_1_BATTERY_182_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8B6)), //
						m(ChannelId.RACK_1_BATTERY_183_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8B7)), //
						m(ChannelId.RACK_1_BATTERY_184_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8B8)), //
						m(ChannelId.RACK_1_BATTERY_185_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8B9)), //
						m(ChannelId.RACK_1_BATTERY_186_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8BA)), //
						m(ChannelId.RACK_1_BATTERY_187_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8BB)), //
						m(ChannelId.RACK_1_BATTERY_188_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8BC)), //
						m(ChannelId.RACK_1_BATTERY_189_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8BD)), //
						m(ChannelId.RACK_1_BATTERY_190_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8BE)), //
						m(ChannelId.RACK_1_BATTERY_191_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8BF)), //
						m(ChannelId.RACK_1_BATTERY_192_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8C0)), //
						m(ChannelId.RACK_1_BATTERY_193_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8C1)), //
						m(ChannelId.RACK_1_BATTERY_194_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8C2)), //
						m(ChannelId.RACK_1_BATTERY_195_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8C3)), //
						m(ChannelId.RACK_1_BATTERY_196_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8C4)), //
						m(ChannelId.RACK_1_BATTERY_197_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8C5)), //
						m(ChannelId.RACK_1_BATTERY_198_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8C6)), //
						m(ChannelId.RACK_1_BATTERY_199_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8C7)), //
						m(ChannelId.RACK_1_BATTERY_200_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8C8)), //
						m(ChannelId.RACK_1_BATTERY_201_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8C9)), //
						m(ChannelId.RACK_1_BATTERY_202_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8CA)), //
						m(ChannelId.RACK_1_BATTERY_203_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8CB)), //
						m(ChannelId.RACK_1_BATTERY_204_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8CC)), //
						m(ChannelId.RACK_1_BATTERY_205_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8CD)), //
						m(ChannelId.RACK_1_BATTERY_206_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8CE)), //
						m(ChannelId.RACK_1_BATTERY_207_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8CF)), //
						m(ChannelId.RACK_1_BATTERY_208_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8D0)), //
						m(ChannelId.RACK_1_BATTERY_209_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8D1)), //
						m(ChannelId.RACK_1_BATTERY_210_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8D2)), //
						m(ChannelId.RACK_1_BATTERY_211_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8D3)), //
						m(ChannelId.RACK_1_BATTERY_212_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8D4)), //
						m(ChannelId.RACK_1_BATTERY_213_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8D5)), //
						m(ChannelId.RACK_1_BATTERY_214_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8D6)), //
						m(ChannelId.RACK_1_BATTERY_215_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0x8D7)) //
				),
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_1 + 0xC00, Priority.LOW, //
						m(ChannelId.RACK_1_BATTERY_000_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC00)), //
						m(ChannelId.RACK_1_BATTERY_001_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC01)), //
						m(ChannelId.RACK_1_BATTERY_002_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC02)), //
						m(ChannelId.RACK_1_BATTERY_003_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC03)), //
						m(ChannelId.RACK_1_BATTERY_004_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC04)), //
						m(ChannelId.RACK_1_BATTERY_005_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC05)), //
						m(ChannelId.RACK_1_BATTERY_006_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC06)), //
						m(ChannelId.RACK_1_BATTERY_007_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC07)), //
						m(ChannelId.RACK_1_BATTERY_008_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC08)), //
						m(ChannelId.RACK_1_BATTERY_009_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC09)), //
						m(ChannelId.RACK_1_BATTERY_010_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC0A)), //
						m(ChannelId.RACK_1_BATTERY_011_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC0B)), //
						m(ChannelId.RACK_1_BATTERY_012_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC0C)), //
						m(ChannelId.RACK_1_BATTERY_013_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC0D)), //
						m(ChannelId.RACK_1_BATTERY_014_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC0E)), //
						m(ChannelId.RACK_1_BATTERY_015_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC0F)), //
						m(ChannelId.RACK_1_BATTERY_016_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC10)), //
						m(ChannelId.RACK_1_BATTERY_017_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC11)), //
						m(ChannelId.RACK_1_BATTERY_018_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC12)), //
						m(ChannelId.RACK_1_BATTERY_019_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC13)), //
						m(ChannelId.RACK_1_BATTERY_020_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC14)), //
						m(ChannelId.RACK_1_BATTERY_021_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC15)), //
						m(ChannelId.RACK_1_BATTERY_022_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC16)), //
						m(ChannelId.RACK_1_BATTERY_023_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC17)), //
						m(ChannelId.RACK_1_BATTERY_024_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC18)), //
						m(ChannelId.RACK_1_BATTERY_025_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC19)), //
						m(ChannelId.RACK_1_BATTERY_026_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC1A)), //
						m(ChannelId.RACK_1_BATTERY_027_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC1B)), //
						m(ChannelId.RACK_1_BATTERY_028_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC1C)), //
						m(ChannelId.RACK_1_BATTERY_029_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC1D)), //
						m(ChannelId.RACK_1_BATTERY_030_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC1E)), //
						m(ChannelId.RACK_1_BATTERY_031_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC1F)), //
						m(ChannelId.RACK_1_BATTERY_032_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC20)), //
						m(ChannelId.RACK_1_BATTERY_033_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC21)), //
						m(ChannelId.RACK_1_BATTERY_034_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC22)), //
						m(ChannelId.RACK_1_BATTERY_035_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC23)), //
						m(ChannelId.RACK_1_BATTERY_036_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC24)), //
						m(ChannelId.RACK_1_BATTERY_037_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC25)), //
						m(ChannelId.RACK_1_BATTERY_038_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC26)), //
						m(ChannelId.RACK_1_BATTERY_039_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC27)), //
						m(ChannelId.RACK_1_BATTERY_040_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC28)), //
						m(ChannelId.RACK_1_BATTERY_041_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC29)), //
						m(ChannelId.RACK_1_BATTERY_042_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC2A)), //
						m(ChannelId.RACK_1_BATTERY_043_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC2B)), //
						m(ChannelId.RACK_1_BATTERY_044_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC2C)), //
						m(ChannelId.RACK_1_BATTERY_045_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC2D)), //
						m(ChannelId.RACK_1_BATTERY_046_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC2E)), //
						m(ChannelId.RACK_1_BATTERY_047_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC2F)), //
						m(ChannelId.RACK_1_BATTERY_048_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC30)), //
						m(ChannelId.RACK_1_BATTERY_049_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC31)), //
						m(ChannelId.RACK_1_BATTERY_050_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC32)), //
						m(ChannelId.RACK_1_BATTERY_051_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC33)), //
						m(ChannelId.RACK_1_BATTERY_052_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC34)), //
						m(ChannelId.RACK_1_BATTERY_053_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC35)), //
						m(ChannelId.RACK_1_BATTERY_054_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC36)), //
						m(ChannelId.RACK_1_BATTERY_055_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC37)), //
						m(ChannelId.RACK_1_BATTERY_056_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC38)), //
						m(ChannelId.RACK_1_BATTERY_057_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC39)), //
						m(ChannelId.RACK_1_BATTERY_058_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC3A)), //
						m(ChannelId.RACK_1_BATTERY_059_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC3B)), //
						m(ChannelId.RACK_1_BATTERY_060_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC3C)), //
						m(ChannelId.RACK_1_BATTERY_061_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC3D)), //
						m(ChannelId.RACK_1_BATTERY_062_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC3E)), //
						m(ChannelId.RACK_1_BATTERY_063_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC3F)), //
						m(ChannelId.RACK_1_BATTERY_064_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC40)), //
						m(ChannelId.RACK_1_BATTERY_065_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC41)), //
						m(ChannelId.RACK_1_BATTERY_066_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC42)), //
						m(ChannelId.RACK_1_BATTERY_067_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC43)), //
						m(ChannelId.RACK_1_BATTERY_068_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC44)), //
						m(ChannelId.RACK_1_BATTERY_069_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC45)), //
						m(ChannelId.RACK_1_BATTERY_070_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC46)), //
						m(ChannelId.RACK_1_BATTERY_071_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC47)), //
						m(ChannelId.RACK_1_BATTERY_072_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC48)), //
						m(ChannelId.RACK_1_BATTERY_073_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC49)), //
						m(ChannelId.RACK_1_BATTERY_074_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC4A)), //
						m(ChannelId.RACK_1_BATTERY_075_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC4B)), //
						m(ChannelId.RACK_1_BATTERY_076_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC4C)), //
						m(ChannelId.RACK_1_BATTERY_077_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC4D)), //
						m(ChannelId.RACK_1_BATTERY_078_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC4E)), //
						m(ChannelId.RACK_1_BATTERY_079_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC4F)), //
						m(ChannelId.RACK_1_BATTERY_080_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC50)), //
						m(ChannelId.RACK_1_BATTERY_081_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC51)), //
						m(ChannelId.RACK_1_BATTERY_082_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC52)), //
						m(ChannelId.RACK_1_BATTERY_083_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC53)), //
						m(ChannelId.RACK_1_BATTERY_084_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC54)), //
						m(ChannelId.RACK_1_BATTERY_085_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC55)), //
						m(ChannelId.RACK_1_BATTERY_086_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC56)), //
						m(ChannelId.RACK_1_BATTERY_087_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC57)), //
						m(ChannelId.RACK_1_BATTERY_088_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC58)), //
						m(ChannelId.RACK_1_BATTERY_089_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC59)), //
						m(ChannelId.RACK_1_BATTERY_090_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC5A)), //
						m(ChannelId.RACK_1_BATTERY_091_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC5B)), //
						m(ChannelId.RACK_1_BATTERY_092_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC5C)), //
						m(ChannelId.RACK_1_BATTERY_093_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC5D)), //
						m(ChannelId.RACK_1_BATTERY_094_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC5E)), //
						m(ChannelId.RACK_1_BATTERY_095_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC5F)), //
						m(ChannelId.RACK_1_BATTERY_096_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC60)), //
						m(ChannelId.RACK_1_BATTERY_097_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC61)), //
						m(ChannelId.RACK_1_BATTERY_098_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC62)), //
						m(ChannelId.RACK_1_BATTERY_099_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC63)), //
						m(ChannelId.RACK_1_BATTERY_100_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC64)), //
						m(ChannelId.RACK_1_BATTERY_101_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC65)), //
						m(ChannelId.RACK_1_BATTERY_102_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC66)), //
						m(ChannelId.RACK_1_BATTERY_103_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC67)), //
						m(ChannelId.RACK_1_BATTERY_104_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC68)), //
						m(ChannelId.RACK_1_BATTERY_105_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC69)), //
						m(ChannelId.RACK_1_BATTERY_106_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC6A)), //
						m(ChannelId.RACK_1_BATTERY_107_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_1 + 0xC6B)) //						
				), //
				// ---------------- registers of rack 2 -----------------------------
				new FC16WriteRegistersTask(BASE_ADDRESS_RACK_2 + 0x1, //
						m(ChannelId.RACK_2_STATE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x1)) //
				),
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_2 + 0x1, Priority.HIGH, //
						m(ChannelId.RACK_2_STATE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x1)) //
				),
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_2 + 0x100, Priority.LOW, //
						m(ChannelId.RACK_2_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x100), //
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(ChannelId.RACK_2_CURRENT, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x101), //
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(ChannelId.RACK_2_CHARGE_INDICATION, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x102)), //
						m(ChannelId.RACK_2_SOC, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x103)), //
						m(ChannelId.RACK_2_SOH, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x104)), //
						m(ChannelId.RACK_2_MAX_CELL_VOLTAGE_ID, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x105)), //
						m(ChannelId.RACK_2_MAX_CELL_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x106)), //
						m(ChannelId.RACK_2_MIN_CELL_VOLTAGE_ID, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x107)), //
						m(ChannelId.RACK_2_MIN_CELL_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x108)), //
						m(ChannelId.RACK_2_MAX_CELL_TEMPERATURE_ID, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x109)), //
						m(ChannelId.RACK_2_MAX_CELL_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x10A)), //
						m(ChannelId.RACK_2_MIN_CELL_TEMPERATURE_ID, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x10B)), //
						m(ChannelId.RACK_2_MIN_CELL_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x10C)) //						
				),
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_2 + 0x140, Priority.LOW, //
						bm(new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x140)) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_2_CELL_VOLTAGE_HIGH, 0) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_2_TOTAL_VOLTAGE_HIGH, 1) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_2_CHA_CURRENT_HIGH, 2) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_2_CELL_VOLTAGE_LOW, 3) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_2_TOTAL_VOLTAGE_LOW, 4) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_2_DISCHA_CURRENT_HIGH, 5) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_2_CELL_CHA_TEMP_HIGH, 6) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_2_CELL_CHA_TEMP_LOW, 7) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_2_INSULATION_LOW, 12) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_2_CELL_DISCHA_TEMP_HIGH, 14) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_2_CELL_DISCHA_TEMP_LOW, 15) //
								.build(), //
						bm(new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x141)) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_1_CELL_VOLTAGE_HIGH, 0) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_1_TOTAL_VOLTAGE_HIGH, 1) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_1_CHA_CURRENT_HIGH, 2) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_1_CELL_VOLTAGE_LOW, 3) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_1_TOTAL_VOLTAGE_LOW, 4) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_1_DISCHA_CURRENT_HIGH, 5) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_1_CELL_CHA_TEMP_HIGH, 6) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_1_CELL_CHA_TEMP_LOW, 7) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_1_SOC_LOW, 8) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_1_CELL_TEMP_DIFF_HIGH, 9) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_1_CELL_VOLTAGE_DIFF_HIGH, 11) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_1_INSULATION_LOW, 12) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_1_TOTAL_VOLTAGE_DIFF_HIGH, 13) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_1_CELL_DISCHA_TEMP_HIGH, 14) //
								.m(ChannelId.RACK_2_ALARM_LEVEL_1_CELL_DISCHA_TEMP_LOW, 15) //
								.build(), //
						m(ChannelId.RACK_2_RUN_STATE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x142)) //
				), //
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_2 + 0x185, Priority.LOW, //
						bm(new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x185)) //
								.m(ChannelId.RACK_2_FAILURE_SAMPLING_WIRE, 0)//
								.m(ChannelId.RACK_2_FAILURE_CONNECTOR_WIRE, 1)//
								.m(ChannelId.RACK_2_FAILURE_LTC6803, 2)//
								.m(ChannelId.RACK_2_FAILURE_VOLTAGE_SAMPLING, 3)//
								.m(ChannelId.RACK_2_FAILURE_TEMP_SAMPLING, 4)//
								.m(ChannelId.RACK_2_FAILURE_TEMP_SENSOR, 5)//
								.m(ChannelId.RACK_2_FAILURE_BALANCING_MODULE, 8)//
								.m(ChannelId.RACK_2_FAILURE_TEMP_SAMPLING_LINE, 9)//
								.m(ChannelId.RACK_2_FAILURE_INTRANET_COMMUNICATION, 10)//
								.m(ChannelId.RACK_2_FAILURE_EEPROM, 11)//
								.m(ChannelId.RACK_2_FAILURE_INITIALIZATION, 12)//
								.build() //
				), //
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_2 + 0x800, Priority.LOW, //
						m(ChannelId.RACK_2_BATTERY_000_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x800)), //
						m(ChannelId.RACK_2_BATTERY_001_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x801)), //
						m(ChannelId.RACK_2_BATTERY_002_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x802)), //
						m(ChannelId.RACK_2_BATTERY_003_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x803)), //
						m(ChannelId.RACK_2_BATTERY_004_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x804)), //
						m(ChannelId.RACK_2_BATTERY_005_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x805)), //
						m(ChannelId.RACK_2_BATTERY_006_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x806)), //
						m(ChannelId.RACK_2_BATTERY_007_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x807)), //
						m(ChannelId.RACK_2_BATTERY_008_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x808)), //
						m(ChannelId.RACK_2_BATTERY_009_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x809)), //
						m(ChannelId.RACK_2_BATTERY_010_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x80A)), //
						m(ChannelId.RACK_2_BATTERY_011_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x80B)), //
						m(ChannelId.RACK_2_BATTERY_012_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x80C)), //
						m(ChannelId.RACK_2_BATTERY_013_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x80D)), //
						m(ChannelId.RACK_2_BATTERY_014_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x80E)), //
						m(ChannelId.RACK_2_BATTERY_015_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x80F)), //
						m(ChannelId.RACK_2_BATTERY_016_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x810)), //
						m(ChannelId.RACK_2_BATTERY_017_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x811)), //
						m(ChannelId.RACK_2_BATTERY_018_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x812)), //
						m(ChannelId.RACK_2_BATTERY_019_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x813)), //
						m(ChannelId.RACK_2_BATTERY_020_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x814)), //
						m(ChannelId.RACK_2_BATTERY_021_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x815)), //
						m(ChannelId.RACK_2_BATTERY_022_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x816)), //
						m(ChannelId.RACK_2_BATTERY_023_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x817)), //
						m(ChannelId.RACK_2_BATTERY_024_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x818)), //
						m(ChannelId.RACK_2_BATTERY_025_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x819)), //
						m(ChannelId.RACK_2_BATTERY_026_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x81A)), //
						m(ChannelId.RACK_2_BATTERY_027_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x81B)), //
						m(ChannelId.RACK_2_BATTERY_028_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x81C)), //
						m(ChannelId.RACK_2_BATTERY_029_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x81D)), //
						m(ChannelId.RACK_2_BATTERY_030_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x81E)), //
						m(ChannelId.RACK_2_BATTERY_031_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x81F)), //
						m(ChannelId.RACK_2_BATTERY_032_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x820)), //
						m(ChannelId.RACK_2_BATTERY_033_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x821)), //
						m(ChannelId.RACK_2_BATTERY_034_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x822)), //
						m(ChannelId.RACK_2_BATTERY_035_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x823)), //
						m(ChannelId.RACK_2_BATTERY_036_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x824)), //
						m(ChannelId.RACK_2_BATTERY_037_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x825)), //
						m(ChannelId.RACK_2_BATTERY_038_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x826)), //
						m(ChannelId.RACK_2_BATTERY_039_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x827)), //
						m(ChannelId.RACK_2_BATTERY_040_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x828)), //
						m(ChannelId.RACK_2_BATTERY_041_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x829)), //
						m(ChannelId.RACK_2_BATTERY_042_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x82A)), //
						m(ChannelId.RACK_2_BATTERY_043_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x82B)), //
						m(ChannelId.RACK_2_BATTERY_044_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x82C)), //
						m(ChannelId.RACK_2_BATTERY_045_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x82D)), //
						m(ChannelId.RACK_2_BATTERY_046_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x82E)), //
						m(ChannelId.RACK_2_BATTERY_047_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x82F)), //
						m(ChannelId.RACK_2_BATTERY_048_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x830)), //
						m(ChannelId.RACK_2_BATTERY_049_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x831)), //
						m(ChannelId.RACK_2_BATTERY_050_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x832)), //
						m(ChannelId.RACK_2_BATTERY_051_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x833)), //
						m(ChannelId.RACK_2_BATTERY_052_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x834)), //
						m(ChannelId.RACK_2_BATTERY_053_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x835)), //
						m(ChannelId.RACK_2_BATTERY_054_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x836)), //
						m(ChannelId.RACK_2_BATTERY_055_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x837)), //
						m(ChannelId.RACK_2_BATTERY_056_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x838)), //
						m(ChannelId.RACK_2_BATTERY_057_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x839)), //
						m(ChannelId.RACK_2_BATTERY_058_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x83A)), //
						m(ChannelId.RACK_2_BATTERY_059_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x83B)), //
						m(ChannelId.RACK_2_BATTERY_060_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x83C)), //
						m(ChannelId.RACK_2_BATTERY_061_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x83D)), //
						m(ChannelId.RACK_2_BATTERY_062_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x83E)), //
						m(ChannelId.RACK_2_BATTERY_063_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x83F)), //
						m(ChannelId.RACK_2_BATTERY_064_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x840)), //
						m(ChannelId.RACK_2_BATTERY_065_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x841)), //
						m(ChannelId.RACK_2_BATTERY_066_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x842)), //
						m(ChannelId.RACK_2_BATTERY_067_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x843)), //
						m(ChannelId.RACK_2_BATTERY_068_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x844)), //
						m(ChannelId.RACK_2_BATTERY_069_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x845)), //
						m(ChannelId.RACK_2_BATTERY_070_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x846)), //
						m(ChannelId.RACK_2_BATTERY_071_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x847)), //
						m(ChannelId.RACK_2_BATTERY_072_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x848)), //
						m(ChannelId.RACK_2_BATTERY_073_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x849)), //
						m(ChannelId.RACK_2_BATTERY_074_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x84A)), //
						m(ChannelId.RACK_2_BATTERY_075_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x84B)), //
						m(ChannelId.RACK_2_BATTERY_076_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x84C)), //
						m(ChannelId.RACK_2_BATTERY_077_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x84D)), //
						m(ChannelId.RACK_2_BATTERY_078_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x84E)), //
						m(ChannelId.RACK_2_BATTERY_079_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x84F)), //
						m(ChannelId.RACK_2_BATTERY_080_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x850)), //
						m(ChannelId.RACK_2_BATTERY_081_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x851)), //
						m(ChannelId.RACK_2_BATTERY_082_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x852)), //
						m(ChannelId.RACK_2_BATTERY_083_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x853)), //
						m(ChannelId.RACK_2_BATTERY_084_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x854)), //
						m(ChannelId.RACK_2_BATTERY_085_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x855)), //
						m(ChannelId.RACK_2_BATTERY_086_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x856)), //
						m(ChannelId.RACK_2_BATTERY_087_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x857)), //
						m(ChannelId.RACK_2_BATTERY_088_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x858)), //
						m(ChannelId.RACK_2_BATTERY_089_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x859)), //
						m(ChannelId.RACK_2_BATTERY_090_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x85A)), //
						m(ChannelId.RACK_2_BATTERY_091_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x85B)), //
						m(ChannelId.RACK_2_BATTERY_092_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x85C)), //
						m(ChannelId.RACK_2_BATTERY_093_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x85D)), //
						m(ChannelId.RACK_2_BATTERY_094_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x85E)), //
						m(ChannelId.RACK_2_BATTERY_095_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x85F)), //
						m(ChannelId.RACK_2_BATTERY_096_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x860)), //
						m(ChannelId.RACK_2_BATTERY_097_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x861)), //
						m(ChannelId.RACK_2_BATTERY_098_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x862)), //
						m(ChannelId.RACK_2_BATTERY_099_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x863)), //
						m(ChannelId.RACK_2_BATTERY_100_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x864)), //
						m(ChannelId.RACK_2_BATTERY_101_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x865)), //
						m(ChannelId.RACK_2_BATTERY_102_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x866)), //
						m(ChannelId.RACK_2_BATTERY_103_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x867)), //
						m(ChannelId.RACK_2_BATTERY_104_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x868)), //
						m(ChannelId.RACK_2_BATTERY_105_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x869)), //
						m(ChannelId.RACK_2_BATTERY_106_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x86A)), //
						m(ChannelId.RACK_2_BATTERY_107_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x86B)), //
						m(ChannelId.RACK_2_BATTERY_108_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x86C)), //
						m(ChannelId.RACK_2_BATTERY_109_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x86D)), //
						m(ChannelId.RACK_2_BATTERY_110_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x86E)), //
						m(ChannelId.RACK_2_BATTERY_111_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x86F)), //
						m(ChannelId.RACK_2_BATTERY_112_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x870)), //
						m(ChannelId.RACK_2_BATTERY_113_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x871)), //
						m(ChannelId.RACK_2_BATTERY_114_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x872)), //
						m(ChannelId.RACK_2_BATTERY_115_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x873)), //
						m(ChannelId.RACK_2_BATTERY_116_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x874)), //
						m(ChannelId.RACK_2_BATTERY_117_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x875)), //
						m(ChannelId.RACK_2_BATTERY_118_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x876)), //
						m(ChannelId.RACK_2_BATTERY_119_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x877)) //
				), //
				new FC3ReadRegistersTask(0x2878, Priority.LOW, //
						m(ChannelId.RACK_2_BATTERY_120_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x878)), //
						m(ChannelId.RACK_2_BATTERY_121_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x879)), //
						m(ChannelId.RACK_2_BATTERY_122_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x87A)), //
						m(ChannelId.RACK_2_BATTERY_123_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x87B)), //
						m(ChannelId.RACK_2_BATTERY_124_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x87C)), //
						m(ChannelId.RACK_2_BATTERY_125_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x87D)), //
						m(ChannelId.RACK_2_BATTERY_126_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x87E)), //
						m(ChannelId.RACK_2_BATTERY_127_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x87F)), //
						m(ChannelId.RACK_2_BATTERY_128_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x880)), //
						m(ChannelId.RACK_2_BATTERY_129_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x881)), //
						m(ChannelId.RACK_2_BATTERY_130_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x882)), //
						m(ChannelId.RACK_2_BATTERY_131_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x883)), //
						m(ChannelId.RACK_2_BATTERY_132_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x884)), //
						m(ChannelId.RACK_2_BATTERY_133_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x885)), //
						m(ChannelId.RACK_2_BATTERY_134_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x886)), //
						m(ChannelId.RACK_2_BATTERY_135_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x887)), //
						m(ChannelId.RACK_2_BATTERY_136_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x888)), //
						m(ChannelId.RACK_2_BATTERY_137_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x889)), //
						m(ChannelId.RACK_2_BATTERY_138_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x88A)), //
						m(ChannelId.RACK_2_BATTERY_139_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x88B)), //
						m(ChannelId.RACK_2_BATTERY_140_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x88C)), //
						m(ChannelId.RACK_2_BATTERY_141_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x88D)), //
						m(ChannelId.RACK_2_BATTERY_142_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x88E)), //
						m(ChannelId.RACK_2_BATTERY_143_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x88F)), //
						m(ChannelId.RACK_2_BATTERY_144_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x890)), //
						m(ChannelId.RACK_2_BATTERY_145_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x891)), //
						m(ChannelId.RACK_2_BATTERY_146_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x892)), //
						m(ChannelId.RACK_2_BATTERY_147_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x893)), //
						m(ChannelId.RACK_2_BATTERY_148_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x894)), //
						m(ChannelId.RACK_2_BATTERY_149_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x895)), //
						m(ChannelId.RACK_2_BATTERY_150_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x896)), //
						m(ChannelId.RACK_2_BATTERY_151_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x897)), //
						m(ChannelId.RACK_2_BATTERY_152_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x898)), //
						m(ChannelId.RACK_2_BATTERY_153_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x899)), //
						m(ChannelId.RACK_2_BATTERY_154_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x89A)), //
						m(ChannelId.RACK_2_BATTERY_155_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x89B)), //
						m(ChannelId.RACK_2_BATTERY_156_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x89C)), //
						m(ChannelId.RACK_2_BATTERY_157_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x89D)), //
						m(ChannelId.RACK_2_BATTERY_158_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x89E)), //
						m(ChannelId.RACK_2_BATTERY_159_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x89F)), //
						m(ChannelId.RACK_2_BATTERY_160_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8A0)), //
						m(ChannelId.RACK_2_BATTERY_161_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8A1)), //
						m(ChannelId.RACK_2_BATTERY_162_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8A2)), //
						m(ChannelId.RACK_2_BATTERY_163_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8A3)), //
						m(ChannelId.RACK_2_BATTERY_164_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8A4)), //
						m(ChannelId.RACK_2_BATTERY_165_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8A5)), //
						m(ChannelId.RACK_2_BATTERY_166_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8A6)), //
						m(ChannelId.RACK_2_BATTERY_167_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8A7)), //
						m(ChannelId.RACK_2_BATTERY_168_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8A8)), //
						m(ChannelId.RACK_2_BATTERY_169_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8A9)), //
						m(ChannelId.RACK_2_BATTERY_170_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8AA)), //
						m(ChannelId.RACK_2_BATTERY_171_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8AB)), //
						m(ChannelId.RACK_2_BATTERY_172_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8AC)), //
						m(ChannelId.RACK_2_BATTERY_173_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8AD)), //
						m(ChannelId.RACK_2_BATTERY_174_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8AE)), //
						m(ChannelId.RACK_2_BATTERY_175_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8AF)), //
						m(ChannelId.RACK_2_BATTERY_176_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8B0)), //
						m(ChannelId.RACK_2_BATTERY_177_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8B1)), //
						m(ChannelId.RACK_2_BATTERY_178_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8B2)), //
						m(ChannelId.RACK_2_BATTERY_179_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8B3)), //
						m(ChannelId.RACK_2_BATTERY_180_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8B4)), //
						m(ChannelId.RACK_2_BATTERY_181_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8B5)), //
						m(ChannelId.RACK_2_BATTERY_182_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8B6)), //
						m(ChannelId.RACK_2_BATTERY_183_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8B7)), //
						m(ChannelId.RACK_2_BATTERY_184_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8B8)), //
						m(ChannelId.RACK_2_BATTERY_185_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8B9)), //
						m(ChannelId.RACK_2_BATTERY_186_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8BA)), //
						m(ChannelId.RACK_2_BATTERY_187_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8BB)), //
						m(ChannelId.RACK_2_BATTERY_188_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8BC)), //
						m(ChannelId.RACK_2_BATTERY_189_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8BD)), //
						m(ChannelId.RACK_2_BATTERY_190_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8BE)), //
						m(ChannelId.RACK_2_BATTERY_191_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8BF)), //
						m(ChannelId.RACK_2_BATTERY_192_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8C0)), //
						m(ChannelId.RACK_2_BATTERY_193_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8C1)), //
						m(ChannelId.RACK_2_BATTERY_194_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8C2)), //
						m(ChannelId.RACK_2_BATTERY_195_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8C3)), //
						m(ChannelId.RACK_2_BATTERY_196_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8C4)), //
						m(ChannelId.RACK_2_BATTERY_197_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8C5)), //
						m(ChannelId.RACK_2_BATTERY_198_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8C6)), //
						m(ChannelId.RACK_2_BATTERY_199_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8C7)), //
						m(ChannelId.RACK_2_BATTERY_200_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8C8)), //
						m(ChannelId.RACK_2_BATTERY_201_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8C9)), //
						m(ChannelId.RACK_2_BATTERY_202_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8CA)), //
						m(ChannelId.RACK_2_BATTERY_203_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8CB)), //
						m(ChannelId.RACK_2_BATTERY_204_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8CC)), //
						m(ChannelId.RACK_2_BATTERY_205_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8CD)), //
						m(ChannelId.RACK_2_BATTERY_206_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8CE)), //
						m(ChannelId.RACK_2_BATTERY_207_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8CF)), //
						m(ChannelId.RACK_2_BATTERY_208_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8D0)), //
						m(ChannelId.RACK_2_BATTERY_209_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8D1)), //
						m(ChannelId.RACK_2_BATTERY_210_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8D2)), //
						m(ChannelId.RACK_2_BATTERY_211_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8D3)), //
						m(ChannelId.RACK_2_BATTERY_212_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8D4)), //
						m(ChannelId.RACK_2_BATTERY_213_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8D5)), //
						m(ChannelId.RACK_2_BATTERY_214_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8D6)), //
						m(ChannelId.RACK_2_BATTERY_215_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0x8D7)) //
				),
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_2 + 0xC00, Priority.LOW, //
						m(ChannelId.RACK_2_BATTERY_000_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC00)), //
						m(ChannelId.RACK_2_BATTERY_001_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC01)), //
						m(ChannelId.RACK_2_BATTERY_002_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC02)), //
						m(ChannelId.RACK_2_BATTERY_003_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC03)), //
						m(ChannelId.RACK_2_BATTERY_004_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC04)), //
						m(ChannelId.RACK_2_BATTERY_005_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC05)), //
						m(ChannelId.RACK_2_BATTERY_006_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC06)), //
						m(ChannelId.RACK_2_BATTERY_007_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC07)), //
						m(ChannelId.RACK_2_BATTERY_008_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC08)), //
						m(ChannelId.RACK_2_BATTERY_009_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC09)), //
						m(ChannelId.RACK_2_BATTERY_010_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC0A)), //
						m(ChannelId.RACK_2_BATTERY_011_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC0B)), //
						m(ChannelId.RACK_2_BATTERY_012_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC0C)), //
						m(ChannelId.RACK_2_BATTERY_013_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC0D)), //
						m(ChannelId.RACK_2_BATTERY_014_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC0E)), //
						m(ChannelId.RACK_2_BATTERY_015_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC0F)), //
						m(ChannelId.RACK_2_BATTERY_016_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC10)), //
						m(ChannelId.RACK_2_BATTERY_017_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC11)), //
						m(ChannelId.RACK_2_BATTERY_018_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC12)), //
						m(ChannelId.RACK_2_BATTERY_019_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC13)), //
						m(ChannelId.RACK_2_BATTERY_020_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC14)), //
						m(ChannelId.RACK_2_BATTERY_021_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC15)), //
						m(ChannelId.RACK_2_BATTERY_022_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC16)), //
						m(ChannelId.RACK_2_BATTERY_023_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC17)), //
						m(ChannelId.RACK_2_BATTERY_024_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC18)), //
						m(ChannelId.RACK_2_BATTERY_025_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC19)), //
						m(ChannelId.RACK_2_BATTERY_026_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC1A)), //
						m(ChannelId.RACK_2_BATTERY_027_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC1B)), //
						m(ChannelId.RACK_2_BATTERY_028_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC1C)), //
						m(ChannelId.RACK_2_BATTERY_029_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC1D)), //
						m(ChannelId.RACK_2_BATTERY_030_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC1E)), //
						m(ChannelId.RACK_2_BATTERY_031_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC1F)), //
						m(ChannelId.RACK_2_BATTERY_032_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC20)), //
						m(ChannelId.RACK_2_BATTERY_033_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC21)), //
						m(ChannelId.RACK_2_BATTERY_034_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC22)), //
						m(ChannelId.RACK_2_BATTERY_035_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC23)), //
						m(ChannelId.RACK_2_BATTERY_036_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC24)), //
						m(ChannelId.RACK_2_BATTERY_037_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC25)), //
						m(ChannelId.RACK_2_BATTERY_038_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC26)), //
						m(ChannelId.RACK_2_BATTERY_039_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC27)), //
						m(ChannelId.RACK_2_BATTERY_040_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC28)), //
						m(ChannelId.RACK_2_BATTERY_041_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC29)), //
						m(ChannelId.RACK_2_BATTERY_042_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC2A)), //
						m(ChannelId.RACK_2_BATTERY_043_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC2B)), //
						m(ChannelId.RACK_2_BATTERY_044_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC2C)), //
						m(ChannelId.RACK_2_BATTERY_045_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC2D)), //
						m(ChannelId.RACK_2_BATTERY_046_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC2E)), //
						m(ChannelId.RACK_2_BATTERY_047_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC2F)), //
						m(ChannelId.RACK_2_BATTERY_048_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC30)), //
						m(ChannelId.RACK_2_BATTERY_049_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC31)), //
						m(ChannelId.RACK_2_BATTERY_050_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC32)), //
						m(ChannelId.RACK_2_BATTERY_051_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC33)), //
						m(ChannelId.RACK_2_BATTERY_052_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC34)), //
						m(ChannelId.RACK_2_BATTERY_053_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC35)), //
						m(ChannelId.RACK_2_BATTERY_054_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC36)), //
						m(ChannelId.RACK_2_BATTERY_055_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC37)), //
						m(ChannelId.RACK_2_BATTERY_056_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC38)), //
						m(ChannelId.RACK_2_BATTERY_057_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC39)), //
						m(ChannelId.RACK_2_BATTERY_058_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC3A)), //
						m(ChannelId.RACK_2_BATTERY_059_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC3B)), //
						m(ChannelId.RACK_2_BATTERY_060_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC3C)), //
						m(ChannelId.RACK_2_BATTERY_061_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC3D)), //
						m(ChannelId.RACK_2_BATTERY_062_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC3E)), //
						m(ChannelId.RACK_2_BATTERY_063_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC3F)), //
						m(ChannelId.RACK_2_BATTERY_064_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC40)), //
						m(ChannelId.RACK_2_BATTERY_065_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC41)), //
						m(ChannelId.RACK_2_BATTERY_066_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC42)), //
						m(ChannelId.RACK_2_BATTERY_067_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC43)), //
						m(ChannelId.RACK_2_BATTERY_068_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC44)), //
						m(ChannelId.RACK_2_BATTERY_069_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC45)), //
						m(ChannelId.RACK_2_BATTERY_070_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC46)), //
						m(ChannelId.RACK_2_BATTERY_071_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC47)), //
						m(ChannelId.RACK_2_BATTERY_072_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC48)), //
						m(ChannelId.RACK_2_BATTERY_073_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC49)), //
						m(ChannelId.RACK_2_BATTERY_074_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC4A)), //
						m(ChannelId.RACK_2_BATTERY_075_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC4B)), //
						m(ChannelId.RACK_2_BATTERY_076_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC4C)), //
						m(ChannelId.RACK_2_BATTERY_077_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC4D)), //
						m(ChannelId.RACK_2_BATTERY_078_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC4E)), //
						m(ChannelId.RACK_2_BATTERY_079_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC4F)), //
						m(ChannelId.RACK_2_BATTERY_080_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC50)), //
						m(ChannelId.RACK_2_BATTERY_081_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC51)), //
						m(ChannelId.RACK_2_BATTERY_082_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC52)), //
						m(ChannelId.RACK_2_BATTERY_083_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC53)), //
						m(ChannelId.RACK_2_BATTERY_084_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC54)), //
						m(ChannelId.RACK_2_BATTERY_085_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC55)), //
						m(ChannelId.RACK_2_BATTERY_086_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC56)), //
						m(ChannelId.RACK_2_BATTERY_087_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC57)), //
						m(ChannelId.RACK_2_BATTERY_088_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC58)), //
						m(ChannelId.RACK_2_BATTERY_089_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC59)), //
						m(ChannelId.RACK_2_BATTERY_090_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC5A)), //
						m(ChannelId.RACK_2_BATTERY_091_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC5B)), //
						m(ChannelId.RACK_2_BATTERY_092_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC5C)), //
						m(ChannelId.RACK_2_BATTERY_093_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC5D)), //
						m(ChannelId.RACK_2_BATTERY_094_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC5E)), //
						m(ChannelId.RACK_2_BATTERY_095_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC5F)), //
						m(ChannelId.RACK_2_BATTERY_096_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC60)), //
						m(ChannelId.RACK_2_BATTERY_097_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC61)), //
						m(ChannelId.RACK_2_BATTERY_098_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC62)), //
						m(ChannelId.RACK_2_BATTERY_099_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC63)), //
						m(ChannelId.RACK_2_BATTERY_100_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC64)), //
						m(ChannelId.RACK_2_BATTERY_101_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC65)), //
						m(ChannelId.RACK_2_BATTERY_102_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC66)), //
						m(ChannelId.RACK_2_BATTERY_103_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC67)), //
						m(ChannelId.RACK_2_BATTERY_104_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC68)), //
						m(ChannelId.RACK_2_BATTERY_105_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC69)), //
						m(ChannelId.RACK_2_BATTERY_106_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC6A)), //
						m(ChannelId.RACK_2_BATTERY_107_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_2 + 0xC6B)) //						
				), //
//				// ---------------- registers of rack 3 -----------------------------
				new FC16WriteRegistersTask(BASE_ADDRESS_RACK_3 + 0x1, //
						m(ChannelId.RACK_3_STATE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x1)) //
				),
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_3 + 0x1, Priority.HIGH, //
						m(ChannelId.RACK_3_STATE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x1)) //
				),
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_3 + 0x100, Priority.LOW, //
						m(ChannelId.RACK_3_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x100), //
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(ChannelId.RACK_3_CURRENT, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x101), //
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(ChannelId.RACK_3_CHARGE_INDICATION, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x102)), //
						m(ChannelId.RACK_3_SOC, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x103)), //
						m(ChannelId.RACK_3_SOH, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x104)), //
						m(ChannelId.RACK_3_MAX_CELL_VOLTAGE_ID, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x105)), //
						m(ChannelId.RACK_3_MAX_CELL_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x106)), //
						m(ChannelId.RACK_3_MIN_CELL_VOLTAGE_ID, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x107)), //
						m(ChannelId.RACK_3_MIN_CELL_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x108)), //
						m(ChannelId.RACK_3_MAX_CELL_TEMPERATURE_ID, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x109)), //
						m(ChannelId.RACK_3_MAX_CELL_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x10A)), //
						m(ChannelId.RACK_3_MIN_CELL_TEMPERATURE_ID, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x10B)), //
						m(ChannelId.RACK_3_MIN_CELL_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x10C)) //						
				),
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_3 + 0x140, Priority.LOW, //
						bm(new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x140)) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_2_CELL_VOLTAGE_HIGH, 0) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_2_TOTAL_VOLTAGE_HIGH, 1) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_2_CHA_CURRENT_HIGH, 2) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_2_CELL_VOLTAGE_LOW, 3) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_2_TOTAL_VOLTAGE_LOW, 4) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_2_DISCHA_CURRENT_HIGH, 5) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_2_CELL_CHA_TEMP_HIGH, 6) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_2_CELL_CHA_TEMP_LOW, 7) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_2_INSULATION_LOW, 12) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_2_CELL_DISCHA_TEMP_HIGH, 14) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_2_CELL_DISCHA_TEMP_LOW, 15) //
								.build(), //
						bm(new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x141)) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_1_CELL_VOLTAGE_HIGH, 0) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_1_TOTAL_VOLTAGE_HIGH, 1) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_1_CHA_CURRENT_HIGH, 2) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_1_CELL_VOLTAGE_LOW, 3) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_1_TOTAL_VOLTAGE_LOW, 4) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_1_DISCHA_CURRENT_HIGH, 5) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_1_CELL_CHA_TEMP_HIGH, 6) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_1_CELL_CHA_TEMP_LOW, 7) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_1_SOC_LOW, 8) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_1_CELL_TEMP_DIFF_HIGH, 9) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_1_CELL_VOLTAGE_DIFF_HIGH, 11) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_1_INSULATION_LOW, 12) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_1_TOTAL_VOLTAGE_DIFF_HIGH, 13) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_1_CELL_DISCHA_TEMP_HIGH, 14) //
								.m(ChannelId.RACK_3_ALARM_LEVEL_1_CELL_DISCHA_TEMP_LOW, 15) //
								.build(), //
						m(ChannelId.RACK_3_RUN_STATE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x142)) //
				), //
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_3 + 0x185, Priority.LOW, //
						bm(new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x185)) //
								.m(ChannelId.RACK_3_FAILURE_SAMPLING_WIRE, 0)//
								.m(ChannelId.RACK_3_FAILURE_CONNECTOR_WIRE, 1)//
								.m(ChannelId.RACK_3_FAILURE_LTC6803, 2)//
								.m(ChannelId.RACK_3_FAILURE_VOLTAGE_SAMPLING, 3)//
								.m(ChannelId.RACK_3_FAILURE_TEMP_SAMPLING, 4)//
								.m(ChannelId.RACK_3_FAILURE_TEMP_SENSOR, 5)//
								.m(ChannelId.RACK_3_FAILURE_BALANCING_MODULE, 8)//
								.m(ChannelId.RACK_3_FAILURE_TEMP_SAMPLING_LINE, 9)//
								.m(ChannelId.RACK_3_FAILURE_INTRANET_COMMUNICATION, 10)//
								.m(ChannelId.RACK_3_FAILURE_EEPROM, 11)//
								.m(ChannelId.RACK_3_FAILURE_INITIALIZATION, 12)//
								.build() //
				), //
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_3 + 0x800, Priority.LOW, //
						m(ChannelId.RACK_3_BATTERY_000_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x800)), //
						m(ChannelId.RACK_3_BATTERY_001_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x801)), //
						m(ChannelId.RACK_3_BATTERY_002_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x802)), //
						m(ChannelId.RACK_3_BATTERY_003_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x803)), //
						m(ChannelId.RACK_3_BATTERY_004_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x804)), //
						m(ChannelId.RACK_3_BATTERY_005_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x805)), //
						m(ChannelId.RACK_3_BATTERY_006_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x806)), //
						m(ChannelId.RACK_3_BATTERY_007_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x807)), //
						m(ChannelId.RACK_3_BATTERY_008_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x808)), //
						m(ChannelId.RACK_3_BATTERY_009_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x809)), //
						m(ChannelId.RACK_3_BATTERY_010_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x80A)), //
						m(ChannelId.RACK_3_BATTERY_011_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x80B)), //
						m(ChannelId.RACK_3_BATTERY_012_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x80C)), //
						m(ChannelId.RACK_3_BATTERY_013_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x80D)), //
						m(ChannelId.RACK_3_BATTERY_014_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x80E)), //
						m(ChannelId.RACK_3_BATTERY_015_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x80F)), //
						m(ChannelId.RACK_3_BATTERY_016_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x810)), //
						m(ChannelId.RACK_3_BATTERY_017_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x811)), //
						m(ChannelId.RACK_3_BATTERY_018_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x812)), //
						m(ChannelId.RACK_3_BATTERY_019_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x813)), //
						m(ChannelId.RACK_3_BATTERY_020_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x814)), //
						m(ChannelId.RACK_3_BATTERY_021_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x815)), //
						m(ChannelId.RACK_3_BATTERY_022_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x816)), //
						m(ChannelId.RACK_3_BATTERY_023_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x817)), //
						m(ChannelId.RACK_3_BATTERY_024_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x818)), //
						m(ChannelId.RACK_3_BATTERY_025_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x819)), //
						m(ChannelId.RACK_3_BATTERY_026_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x81A)), //
						m(ChannelId.RACK_3_BATTERY_027_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x81B)), //
						m(ChannelId.RACK_3_BATTERY_028_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x81C)), //
						m(ChannelId.RACK_3_BATTERY_029_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x81D)), //
						m(ChannelId.RACK_3_BATTERY_030_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x81E)), //
						m(ChannelId.RACK_3_BATTERY_031_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x81F)), //
						m(ChannelId.RACK_3_BATTERY_032_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x820)), //
						m(ChannelId.RACK_3_BATTERY_033_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x821)), //
						m(ChannelId.RACK_3_BATTERY_034_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x822)), //
						m(ChannelId.RACK_3_BATTERY_035_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x823)), //
						m(ChannelId.RACK_3_BATTERY_036_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x824)), //
						m(ChannelId.RACK_3_BATTERY_037_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x825)), //
						m(ChannelId.RACK_3_BATTERY_038_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x826)), //
						m(ChannelId.RACK_3_BATTERY_039_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x827)), //
						m(ChannelId.RACK_3_BATTERY_040_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x828)), //
						m(ChannelId.RACK_3_BATTERY_041_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x829)), //
						m(ChannelId.RACK_3_BATTERY_042_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x82A)), //
						m(ChannelId.RACK_3_BATTERY_043_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x82B)), //
						m(ChannelId.RACK_3_BATTERY_044_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x82C)), //
						m(ChannelId.RACK_3_BATTERY_045_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x82D)), //
						m(ChannelId.RACK_3_BATTERY_046_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x82E)), //
						m(ChannelId.RACK_3_BATTERY_047_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x82F)), //
						m(ChannelId.RACK_3_BATTERY_048_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x830)), //
						m(ChannelId.RACK_3_BATTERY_049_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x831)), //
						m(ChannelId.RACK_3_BATTERY_050_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x832)), //
						m(ChannelId.RACK_3_BATTERY_051_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x833)), //
						m(ChannelId.RACK_3_BATTERY_052_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x834)), //
						m(ChannelId.RACK_3_BATTERY_053_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x835)), //
						m(ChannelId.RACK_3_BATTERY_054_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x836)), //
						m(ChannelId.RACK_3_BATTERY_055_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x837)), //
						m(ChannelId.RACK_3_BATTERY_056_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x838)), //
						m(ChannelId.RACK_3_BATTERY_057_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x839)), //
						m(ChannelId.RACK_3_BATTERY_058_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x83A)), //
						m(ChannelId.RACK_3_BATTERY_059_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x83B)), //
						m(ChannelId.RACK_3_BATTERY_060_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x83C)), //
						m(ChannelId.RACK_3_BATTERY_061_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x83D)), //
						m(ChannelId.RACK_3_BATTERY_062_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x83E)), //
						m(ChannelId.RACK_3_BATTERY_063_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x83F)), //
						m(ChannelId.RACK_3_BATTERY_064_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x840)), //
						m(ChannelId.RACK_3_BATTERY_065_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x841)), //
						m(ChannelId.RACK_3_BATTERY_066_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x842)), //
						m(ChannelId.RACK_3_BATTERY_067_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x843)), //
						m(ChannelId.RACK_3_BATTERY_068_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x844)), //
						m(ChannelId.RACK_3_BATTERY_069_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x845)), //
						m(ChannelId.RACK_3_BATTERY_070_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x846)), //
						m(ChannelId.RACK_3_BATTERY_071_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x847)), //
						m(ChannelId.RACK_3_BATTERY_072_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x848)), //
						m(ChannelId.RACK_3_BATTERY_073_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x849)), //
						m(ChannelId.RACK_3_BATTERY_074_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x84A)), //
						m(ChannelId.RACK_3_BATTERY_075_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x84B)), //
						m(ChannelId.RACK_3_BATTERY_076_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x84C)), //
						m(ChannelId.RACK_3_BATTERY_077_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x84D)), //
						m(ChannelId.RACK_3_BATTERY_078_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x84E)), //
						m(ChannelId.RACK_3_BATTERY_079_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x84F)), //
						m(ChannelId.RACK_3_BATTERY_080_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x850)), //
						m(ChannelId.RACK_3_BATTERY_081_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x851)), //
						m(ChannelId.RACK_3_BATTERY_082_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x852)), //
						m(ChannelId.RACK_3_BATTERY_083_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x853)), //
						m(ChannelId.RACK_3_BATTERY_084_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x854)), //
						m(ChannelId.RACK_3_BATTERY_085_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x855)), //
						m(ChannelId.RACK_3_BATTERY_086_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x856)), //
						m(ChannelId.RACK_3_BATTERY_087_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x857)), //
						m(ChannelId.RACK_3_BATTERY_088_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x858)), //
						m(ChannelId.RACK_3_BATTERY_089_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x859)), //
						m(ChannelId.RACK_3_BATTERY_090_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x85A)), //
						m(ChannelId.RACK_3_BATTERY_091_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x85B)), //
						m(ChannelId.RACK_3_BATTERY_092_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x85C)), //
						m(ChannelId.RACK_3_BATTERY_093_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x85D)), //
						m(ChannelId.RACK_3_BATTERY_094_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x85E)), //
						m(ChannelId.RACK_3_BATTERY_095_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x85F)), //
						m(ChannelId.RACK_3_BATTERY_096_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x860)), //
						m(ChannelId.RACK_3_BATTERY_097_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x861)), //
						m(ChannelId.RACK_3_BATTERY_098_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x862)), //
						m(ChannelId.RACK_3_BATTERY_099_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x863)), //
						m(ChannelId.RACK_3_BATTERY_100_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x864)), //
						m(ChannelId.RACK_3_BATTERY_101_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x865)), //
						m(ChannelId.RACK_3_BATTERY_102_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x866)), //
						m(ChannelId.RACK_3_BATTERY_103_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x867)), //
						m(ChannelId.RACK_3_BATTERY_104_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x868)), //
						m(ChannelId.RACK_3_BATTERY_105_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x869)), //
						m(ChannelId.RACK_3_BATTERY_106_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x86A)), //
						m(ChannelId.RACK_3_BATTERY_107_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x86B)), //
						m(ChannelId.RACK_3_BATTERY_108_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x86C)), //
						m(ChannelId.RACK_3_BATTERY_109_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x86D)), //
						m(ChannelId.RACK_3_BATTERY_110_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x86E)), //
						m(ChannelId.RACK_3_BATTERY_111_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x86F)), //
						m(ChannelId.RACK_3_BATTERY_112_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x870)), //
						m(ChannelId.RACK_3_BATTERY_113_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x871)), //
						m(ChannelId.RACK_3_BATTERY_114_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x872)), //
						m(ChannelId.RACK_3_BATTERY_115_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x873)), //
						m(ChannelId.RACK_3_BATTERY_116_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x874)), //
						m(ChannelId.RACK_3_BATTERY_117_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x875)), //
						m(ChannelId.RACK_3_BATTERY_118_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x876)), //
						m(ChannelId.RACK_3_BATTERY_119_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x877)) //
				), //
				new FC3ReadRegistersTask(0x2878, Priority.LOW, //
						m(ChannelId.RACK_3_BATTERY_120_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x878)), //
						m(ChannelId.RACK_3_BATTERY_121_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x879)), //
						m(ChannelId.RACK_3_BATTERY_122_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x87A)), //
						m(ChannelId.RACK_3_BATTERY_123_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x87B)), //
						m(ChannelId.RACK_3_BATTERY_124_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x87C)), //
						m(ChannelId.RACK_3_BATTERY_125_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x87D)), //
						m(ChannelId.RACK_3_BATTERY_126_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x87E)), //
						m(ChannelId.RACK_3_BATTERY_127_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x87F)), //
						m(ChannelId.RACK_3_BATTERY_128_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x880)), //
						m(ChannelId.RACK_3_BATTERY_129_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x881)), //
						m(ChannelId.RACK_3_BATTERY_130_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x882)), //
						m(ChannelId.RACK_3_BATTERY_131_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x883)), //
						m(ChannelId.RACK_3_BATTERY_132_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x884)), //
						m(ChannelId.RACK_3_BATTERY_133_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x885)), //
						m(ChannelId.RACK_3_BATTERY_134_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x886)), //
						m(ChannelId.RACK_3_BATTERY_135_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x887)), //
						m(ChannelId.RACK_3_BATTERY_136_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x888)), //
						m(ChannelId.RACK_3_BATTERY_137_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x889)), //
						m(ChannelId.RACK_3_BATTERY_138_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x88A)), //
						m(ChannelId.RACK_3_BATTERY_139_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x88B)), //
						m(ChannelId.RACK_3_BATTERY_140_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x88C)), //
						m(ChannelId.RACK_3_BATTERY_141_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x88D)), //
						m(ChannelId.RACK_3_BATTERY_142_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x88E)), //
						m(ChannelId.RACK_3_BATTERY_143_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x88F)), //
						m(ChannelId.RACK_3_BATTERY_144_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x890)), //
						m(ChannelId.RACK_3_BATTERY_145_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x891)), //
						m(ChannelId.RACK_3_BATTERY_146_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x892)), //
						m(ChannelId.RACK_3_BATTERY_147_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x893)), //
						m(ChannelId.RACK_3_BATTERY_148_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x894)), //
						m(ChannelId.RACK_3_BATTERY_149_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x895)), //
						m(ChannelId.RACK_3_BATTERY_150_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x896)), //
						m(ChannelId.RACK_3_BATTERY_151_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x897)), //
						m(ChannelId.RACK_3_BATTERY_152_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x898)), //
						m(ChannelId.RACK_3_BATTERY_153_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x899)), //
						m(ChannelId.RACK_3_BATTERY_154_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x89A)), //
						m(ChannelId.RACK_3_BATTERY_155_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x89B)), //
						m(ChannelId.RACK_3_BATTERY_156_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x89C)), //
						m(ChannelId.RACK_3_BATTERY_157_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x89D)), //
						m(ChannelId.RACK_3_BATTERY_158_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x89E)), //
						m(ChannelId.RACK_3_BATTERY_159_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x89F)), //
						m(ChannelId.RACK_3_BATTERY_160_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8A0)), //
						m(ChannelId.RACK_3_BATTERY_161_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8A1)), //
						m(ChannelId.RACK_3_BATTERY_162_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8A2)), //
						m(ChannelId.RACK_3_BATTERY_163_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8A3)), //
						m(ChannelId.RACK_3_BATTERY_164_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8A4)), //
						m(ChannelId.RACK_3_BATTERY_165_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8A5)), //
						m(ChannelId.RACK_3_BATTERY_166_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8A6)), //
						m(ChannelId.RACK_3_BATTERY_167_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8A7)), //
						m(ChannelId.RACK_3_BATTERY_168_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8A8)), //
						m(ChannelId.RACK_3_BATTERY_169_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8A9)), //
						m(ChannelId.RACK_3_BATTERY_170_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8AA)), //
						m(ChannelId.RACK_3_BATTERY_171_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8AB)), //
						m(ChannelId.RACK_3_BATTERY_172_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8AC)), //
						m(ChannelId.RACK_3_BATTERY_173_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8AD)), //
						m(ChannelId.RACK_3_BATTERY_174_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8AE)), //
						m(ChannelId.RACK_3_BATTERY_175_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8AF)), //
						m(ChannelId.RACK_3_BATTERY_176_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8B0)), //
						m(ChannelId.RACK_3_BATTERY_177_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8B1)), //
						m(ChannelId.RACK_3_BATTERY_178_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8B2)), //
						m(ChannelId.RACK_3_BATTERY_179_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8B3)), //
						m(ChannelId.RACK_3_BATTERY_180_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8B4)), //
						m(ChannelId.RACK_3_BATTERY_181_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8B5)), //
						m(ChannelId.RACK_3_BATTERY_182_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8B6)), //
						m(ChannelId.RACK_3_BATTERY_183_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8B7)), //
						m(ChannelId.RACK_3_BATTERY_184_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8B8)), //
						m(ChannelId.RACK_3_BATTERY_185_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8B9)), //
						m(ChannelId.RACK_3_BATTERY_186_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8BA)), //
						m(ChannelId.RACK_3_BATTERY_187_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8BB)), //
						m(ChannelId.RACK_3_BATTERY_188_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8BC)), //
						m(ChannelId.RACK_3_BATTERY_189_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8BD)), //
						m(ChannelId.RACK_3_BATTERY_190_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8BE)), //
						m(ChannelId.RACK_3_BATTERY_191_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8BF)), //
						m(ChannelId.RACK_3_BATTERY_192_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8C0)), //
						m(ChannelId.RACK_3_BATTERY_193_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8C1)), //
						m(ChannelId.RACK_3_BATTERY_194_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8C2)), //
						m(ChannelId.RACK_3_BATTERY_195_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8C3)), //
						m(ChannelId.RACK_3_BATTERY_196_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8C4)), //
						m(ChannelId.RACK_3_BATTERY_197_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8C5)), //
						m(ChannelId.RACK_3_BATTERY_198_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8C6)), //
						m(ChannelId.RACK_3_BATTERY_199_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8C7)), //
						m(ChannelId.RACK_3_BATTERY_200_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8C8)), //
						m(ChannelId.RACK_3_BATTERY_201_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8C9)), //
						m(ChannelId.RACK_3_BATTERY_202_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8CA)), //
						m(ChannelId.RACK_3_BATTERY_203_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8CB)), //
						m(ChannelId.RACK_3_BATTERY_204_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8CC)), //
						m(ChannelId.RACK_3_BATTERY_205_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8CD)), //
						m(ChannelId.RACK_3_BATTERY_206_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8CE)), //
						m(ChannelId.RACK_3_BATTERY_207_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8CF)), //
						m(ChannelId.RACK_3_BATTERY_208_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8D0)), //
						m(ChannelId.RACK_3_BATTERY_209_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8D1)), //
						m(ChannelId.RACK_3_BATTERY_210_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8D2)), //
						m(ChannelId.RACK_3_BATTERY_211_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8D3)), //
						m(ChannelId.RACK_3_BATTERY_212_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8D4)), //
						m(ChannelId.RACK_3_BATTERY_213_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8D5)), //
						m(ChannelId.RACK_3_BATTERY_214_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8D6)), //
						m(ChannelId.RACK_3_BATTERY_215_VOLTAGE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0x8D7)) //
				),
				new FC3ReadRegistersTask(BASE_ADDRESS_RACK_3 + 0xC00, Priority.LOW, //
						m(ChannelId.RACK_3_BATTERY_000_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC00)), //
						m(ChannelId.RACK_3_BATTERY_001_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC01)), //
						m(ChannelId.RACK_3_BATTERY_002_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC02)), //
						m(ChannelId.RACK_3_BATTERY_003_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC03)), //
						m(ChannelId.RACK_3_BATTERY_004_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC04)), //
						m(ChannelId.RACK_3_BATTERY_005_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC05)), //
						m(ChannelId.RACK_3_BATTERY_006_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC06)), //
						m(ChannelId.RACK_3_BATTERY_007_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC07)), //
						m(ChannelId.RACK_3_BATTERY_008_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC08)), //
						m(ChannelId.RACK_3_BATTERY_009_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC09)), //
						m(ChannelId.RACK_3_BATTERY_010_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC0A)), //
						m(ChannelId.RACK_3_BATTERY_011_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC0B)), //
						m(ChannelId.RACK_3_BATTERY_012_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC0C)), //
						m(ChannelId.RACK_3_BATTERY_013_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC0D)), //
						m(ChannelId.RACK_3_BATTERY_014_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC0E)), //
						m(ChannelId.RACK_3_BATTERY_015_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC0F)), //
						m(ChannelId.RACK_3_BATTERY_016_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC10)), //
						m(ChannelId.RACK_3_BATTERY_017_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC11)), //
						m(ChannelId.RACK_3_BATTERY_018_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC12)), //
						m(ChannelId.RACK_3_BATTERY_019_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC13)), //
						m(ChannelId.RACK_3_BATTERY_020_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC14)), //
						m(ChannelId.RACK_3_BATTERY_021_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC15)), //
						m(ChannelId.RACK_3_BATTERY_022_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC16)), //
						m(ChannelId.RACK_3_BATTERY_023_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC17)), //
						m(ChannelId.RACK_3_BATTERY_024_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC18)), //
						m(ChannelId.RACK_3_BATTERY_025_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC19)), //
						m(ChannelId.RACK_3_BATTERY_026_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC1A)), //
						m(ChannelId.RACK_3_BATTERY_027_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC1B)), //
						m(ChannelId.RACK_3_BATTERY_028_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC1C)), //
						m(ChannelId.RACK_3_BATTERY_029_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC1D)), //
						m(ChannelId.RACK_3_BATTERY_030_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC1E)), //
						m(ChannelId.RACK_3_BATTERY_031_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC1F)), //
						m(ChannelId.RACK_3_BATTERY_032_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC20)), //
						m(ChannelId.RACK_3_BATTERY_033_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC21)), //
						m(ChannelId.RACK_3_BATTERY_034_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC22)), //
						m(ChannelId.RACK_3_BATTERY_035_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC23)), //
						m(ChannelId.RACK_3_BATTERY_036_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC24)), //
						m(ChannelId.RACK_3_BATTERY_037_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC25)), //
						m(ChannelId.RACK_3_BATTERY_038_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC26)), //
						m(ChannelId.RACK_3_BATTERY_039_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC27)), //
						m(ChannelId.RACK_3_BATTERY_040_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC28)), //
						m(ChannelId.RACK_3_BATTERY_041_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC29)), //
						m(ChannelId.RACK_3_BATTERY_042_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC2A)), //
						m(ChannelId.RACK_3_BATTERY_043_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC2B)), //
						m(ChannelId.RACK_3_BATTERY_044_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC2C)), //
						m(ChannelId.RACK_3_BATTERY_045_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC2D)), //
						m(ChannelId.RACK_3_BATTERY_046_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC2E)), //
						m(ChannelId.RACK_3_BATTERY_047_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC2F)), //
						m(ChannelId.RACK_3_BATTERY_048_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC30)), //
						m(ChannelId.RACK_3_BATTERY_049_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC31)), //
						m(ChannelId.RACK_3_BATTERY_050_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC32)), //
						m(ChannelId.RACK_3_BATTERY_051_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC33)), //
						m(ChannelId.RACK_3_BATTERY_052_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC34)), //
						m(ChannelId.RACK_3_BATTERY_053_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC35)), //
						m(ChannelId.RACK_3_BATTERY_054_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC36)), //
						m(ChannelId.RACK_3_BATTERY_055_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC37)), //
						m(ChannelId.RACK_3_BATTERY_056_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC38)), //
						m(ChannelId.RACK_3_BATTERY_057_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC39)), //
						m(ChannelId.RACK_3_BATTERY_058_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC3A)), //
						m(ChannelId.RACK_3_BATTERY_059_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC3B)), //
						m(ChannelId.RACK_3_BATTERY_060_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC3C)), //
						m(ChannelId.RACK_3_BATTERY_061_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC3D)), //
						m(ChannelId.RACK_3_BATTERY_062_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC3E)), //
						m(ChannelId.RACK_3_BATTERY_063_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC3F)), //
						m(ChannelId.RACK_3_BATTERY_064_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC40)), //
						m(ChannelId.RACK_3_BATTERY_065_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC41)), //
						m(ChannelId.RACK_3_BATTERY_066_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC42)), //
						m(ChannelId.RACK_3_BATTERY_067_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC43)), //
						m(ChannelId.RACK_3_BATTERY_068_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC44)), //
						m(ChannelId.RACK_3_BATTERY_069_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC45)), //
						m(ChannelId.RACK_3_BATTERY_070_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC46)), //
						m(ChannelId.RACK_3_BATTERY_071_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC47)), //
						m(ChannelId.RACK_3_BATTERY_072_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC48)), //
						m(ChannelId.RACK_3_BATTERY_073_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC49)), //
						m(ChannelId.RACK_3_BATTERY_074_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC4A)), //
						m(ChannelId.RACK_3_BATTERY_075_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC4B)), //
						m(ChannelId.RACK_3_BATTERY_076_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC4C)), //
						m(ChannelId.RACK_3_BATTERY_077_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC4D)), //
						m(ChannelId.RACK_3_BATTERY_078_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC4E)), //
						m(ChannelId.RACK_3_BATTERY_079_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC4F)), //
						m(ChannelId.RACK_3_BATTERY_080_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC50)), //
						m(ChannelId.RACK_3_BATTERY_081_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC51)), //
						m(ChannelId.RACK_3_BATTERY_082_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC52)), //
						m(ChannelId.RACK_3_BATTERY_083_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC53)), //
						m(ChannelId.RACK_3_BATTERY_084_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC54)), //
						m(ChannelId.RACK_3_BATTERY_085_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC55)), //
						m(ChannelId.RACK_3_BATTERY_086_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC56)), //
						m(ChannelId.RACK_3_BATTERY_087_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC57)), //
						m(ChannelId.RACK_3_BATTERY_088_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC58)), //
						m(ChannelId.RACK_3_BATTERY_089_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC59)), //
						m(ChannelId.RACK_3_BATTERY_090_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC5A)), //
						m(ChannelId.RACK_3_BATTERY_091_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC5B)), //
						m(ChannelId.RACK_3_BATTERY_092_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC5C)), //
						m(ChannelId.RACK_3_BATTERY_093_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC5D)), //
						m(ChannelId.RACK_3_BATTERY_094_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC5E)), //
						m(ChannelId.RACK_3_BATTERY_095_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC5F)), //
						m(ChannelId.RACK_3_BATTERY_096_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC60)), //
						m(ChannelId.RACK_3_BATTERY_097_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC61)), //
						m(ChannelId.RACK_3_BATTERY_098_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC62)), //
						m(ChannelId.RACK_3_BATTERY_099_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC63)), //
						m(ChannelId.RACK_3_BATTERY_100_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC64)), //
						m(ChannelId.RACK_3_BATTERY_101_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC65)), //
						m(ChannelId.RACK_3_BATTERY_102_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC66)), //
						m(ChannelId.RACK_3_BATTERY_103_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC67)), //
						m(ChannelId.RACK_3_BATTERY_104_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC68)), //
						m(ChannelId.RACK_3_BATTERY_105_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC69)), //
						m(ChannelId.RACK_3_BATTERY_106_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC6A)), //
						m(ChannelId.RACK_3_BATTERY_107_TEMPERATURE, new UnsignedWordElement(BASE_ADDRESS_RACK_3 + 0xC6B)) //						
				) //
		); //
	}

}
