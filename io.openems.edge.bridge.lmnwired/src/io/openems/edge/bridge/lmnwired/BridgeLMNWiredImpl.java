package io.openems.edge.bridge.lmnwired;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import io.openems.common.worker.AbstractCycleWorker;
import io.openems.edge.bridge.lmnwired.api.BridgeLMNWired;
import io.openems.edge.bridge.lmnwired.api.Device;
import io.openems.edge.bridge.lmnwired.api.task.LMNWiredTask;
import io.openems.edge.bridge.lmnwired.hdlc.Addressing;
import io.openems.edge.bridge.lmnwired.hdlc.HdlcFrame;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;

@Designate(ocd = Config.class, factory = true)
@Component(name = "io.openems.edge.bridge.lmnwired", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = { //
				EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE, //
				EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE //
		})

public class BridgeLMNWiredImpl extends AbstractOpenemsComponent
		implements BridgeLMNWired, OpenemsComponent, EventHandler {

	SerialPort serialPort;
	SerialPort serialPort2;

	private final Logger log = LoggerFactory.getLogger(BridgeLMNWiredImpl.class);

	private final LMNWiredWorker worker = new LMNWiredWorker();
	private final Map<String, LMNWiredTask> tasks = new HashMap<>();

	private Addressing addressing;

	private byte currentPackage[];

	long receiveTimeMeasure;
	int timeslotsTime;
	int timeslots;
	int timeSlotDurationInMs;
	NumberFormat numberFormat = new DecimalFormat("0.0");
	List<Device> deviceList;

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
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

	public BridgeLMNWiredImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				BridgeLMNWired.ChannelId.values(), //
				ChannelId.values() //
		);
	}

	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());

		this.worker.activate(config.id());

		serialPort = SerialPort.getCommPort(config.portName());

		serialPort.setNumDataBits(8);
		serialPort.setNumStopBits(1);
		serialPort.setParity(0);
		serialPort.setBaudRate(config.baudRate());
		serialPort.openPort();
		
		timeslotsTime = config.timeSlots() * 2 * config.timeSlotDurationInMs();
		timeslots = config.timeSlots();
		timeSlotDurationInMs = config.timeSlotDurationInMs();
		deviceList = new ArrayList<Device>();

	    numberFormat.setRoundingMode(RoundingMode.DOWN);

		activateSerialDataListener(serialPort);

		addressing = new Addressing(serialPort, config.timeSlots(), timeslotsTime, deviceList);
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
		addressing.shutdown();
		serialPort.removeDataListener();
		serialPort.closePort();
		worker.deactivate();
	}

	@Override
	public void handleEvent(Event event) {
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE:
			this.worker.triggerNextRun();
			break;
		}
	}

	@Override
	public void addTask(String sourceId, LMNWiredTask task) {
		this.tasks.put(sourceId, task);
	}

	@Override
	public void removeTask(String sourceId) {
		this.tasks.remove(sourceId);
	}

	private class LMNWiredWorker extends AbstractCycleWorker {

		@Override
		public void activate(String name) {
			super.activate(name);
		}

		@Override
		public void deactivate() {
			super.deactivate();
		}

		@Override
		protected void forever() {

			for (LMNWiredTask task : tasks.values()) {
				Object data = null;
				data = task.getRequest();
				task.setResponse(data);
			}

		}
	}

	/**
	 * Activate data listener for incoming packages
	 * 
	 * @param serialPort
	 */
	private void activateSerialDataListener(SerialPort serialPort) {

		if (!serialPort.isOpen()) {
			log.info("SerialPort not available.");
			return;
		}

		serialPort.addDataListener(new SerialPortDataListener() {
			@Override
			public int getListeningEvents() {
				return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
			}

			@Override
			public void serialEvent(SerialPortEvent event) {

				byte[] newData = event.getReceivedData();

				// Lookup start or end byte flag 0x7e
				if (newData[0] == 0x7e) { // means start
					// Measure Time
					receiveTimeMeasure = addressing.setTimeStampAddressingEnd();
					currentPackage = newData;
				} else if (newData[newData.length - 1] == 0x7e) { // means end
					ByteArrayOutputStream concatData = new ByteArrayOutputStream();
					try {
						concatData.write(currentPackage);
						concatData.write(newData);
						handleReturn(concatData.toByteArray(), receiveTimeMeasure);
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					ByteArrayOutputStream concatData = new ByteArrayOutputStream();
					try {
						concatData.write(currentPackage);
						concatData.write(newData);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		});
	}

	protected void handleReturn(byte data[], long receiveTimeMeasure) {

		HdlcFrame hdlcFrame = HdlcFrame.createHdlcFrameFromByte(data);
		if (hdlcFrame != null) {
//			log.info("HDLC Frame received, party hard!");
			long timeDiff = receiveTimeMeasure - addressing.getTimeStampAddressing();
			long usedTimeSlotBeta = (timeDiff - addressing.getTimeStampAddressing() + timeSlotDurationInMs)
					/ (2 * timeSlotDurationInMs);
			double usedTimeSlot = Math.floor(usedTimeSlotBeta);
			double tmpTimeSlotFactor = usedTimeSlotBeta - usedTimeSlot;
			if (addressing.isAddressingInProgress()) {
				if(tmpTimeSlotFactor <= 0.5) { //in data time window
					log.info("HDLC Frame is new device data!");
					//Add device to List
					Device device = new Device(hdlcFrame.getSource(), Arrays.copyOfRange(hdlcFrame.getData(), 2, 16));
					//Check if device is already in list
					boolean deviceInList = false;
					for(Device tmpDevice: deviceList) {
						if(Arrays.equals(tmpDevice.getSerialNumber(), device.getSerialNumber())) {
							deviceInList = true;
						}
					}
					if(!deviceInList) { //Finally add to list
						deviceList.add(device);
					}
				}else { //in guard time, ignore package
					log.info("HDLC Frame is new device data but in guard time!");
				}

			} else if (addressing.isCheckupInProgress()){
				log.info("HDLC Frame presence check response");
				Device device = new Device(hdlcFrame.getSource(), Arrays.copyOfRange(hdlcFrame.getData(), 2, 16));
				for(Device tmpDevice: deviceList) {
					if(Arrays.equals(tmpDevice.getSerialNumber(), device.getSerialNumber())) {
						tmpDevice.setPresent();
					}
				}
				
			} else{				
				log.info("HDLC Frame is data");
			}
		} else {
			log.info("HDLC Frame received, check HCS or FCS");
		}
	}

	@Override
	public SerialPort getSerialConnection() {
		return serialPort;
	}

}
