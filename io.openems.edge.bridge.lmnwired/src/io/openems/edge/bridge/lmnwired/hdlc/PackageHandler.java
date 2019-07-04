package io.openems.edge.bridge.lmnwired.hdlc;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazecast.jSerialComm.SerialPort;

import io.openems.edge.bridge.lmnwired.BridgeLMNWiredImpl;
import io.openems.edge.bridge.lmnwired.api.BridgeLMNWired;
import io.openems.edge.bridge.lmnwired.api.Device;
import io.openems.edge.bridge.lmnwired.api.task.LMNWiredTask;

/**
 * Handle Addressing and keep alive packages
 * 
 * @author Leonid Verhovskij
 *
 */
public class PackageHandler {

	protected SerialPort serialPort;
	private ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
	private ScheduledExecutorService serviceAddressingEnd = Executors.newSingleThreadScheduledExecutor();
	private ScheduledExecutorService servicePresenceCheckEnd = Executors.newSingleThreadScheduledExecutor();
	private ScheduledExecutorService serviceDataRequestTimout = Executors.newSingleThreadScheduledExecutor();
	private ScheduledExecutorService serviceAddressing = Executors.newSingleThreadScheduledExecutor();
	private ScheduledExecutorService servicePresenceCheck = Executors.newSingleThreadScheduledExecutor();
	private ScheduledExecutorService serviceDataRequest = Executors.newSingleThreadScheduledExecutor();
	private final Logger log = LoggerFactory.getLogger(PackageHandler.class);
	private String testRequestDevicesOnZero = "Request device registration with zero devices in list.";
	private String testRequestDevicesWithExisting = "Request device registration with devices in list.";
	private String testRequestDevicePresenceCheck = "Request device presence checkup.";
	private HdlcFrameAddressingOnEmptyList hdlcFrameAddressingOnEmptyList;
	private HdlcFrameAddressingOnDevicesInList hdlcFrameAddressingOnDevicesInList;
	private HdlcFrameCheckDevicesInList hdlcFrameCheckDevicesInList;
	protected long timeStampAddressing = 0;
	protected long timeStampAddressingEnd;
	protected long timeStampCheckPresence = 0;
	protected long timeStampCheckPresenceEnd;
	protected long timeStampLastDataRequest = 0;
	protected int timeslotsTime;
	protected int timeSlots;
	List<Device> deviceList;
	protected boolean addressingInProgress = false;
	protected boolean presenceCheckInProgress = false;
	protected boolean dataRequestInProgress = false;
	protected Queue<LMNWiredTask> dataRequestQueue = new LinkedList<LMNWiredTask>();
	protected LMNWiredTask currentDataRequestTask;

	protected BridgeLMNWiredImpl bridgeLMNWiredImpl;

	@Reference
	protected ConfigurationAdmin cm;
	
	/**
	 * 
	 * @param serialPort
	 */
	public PackageHandler(SerialPort serialPort, int timeSlots, int timeslotsTime, List<Device> deviceList,
			BridgeLMNWiredImpl bridgeLMNWiredImpl) {
		this.bridgeLMNWiredImpl = bridgeLMNWiredImpl;
		this.serialPort = serialPort;
		hdlcFrameAddressingOnEmptyList = new HdlcFrameAddressingOnEmptyList((byte) timeSlots);
		this.timeslotsTime = timeslotsTime;
		this.timeSlots = timeSlots;
		this.deviceList = deviceList;

		if (!serialPort.isOpen()) {
			log.debug("SerialPort not open.");
			return;
		}

		startServiceHandler();
	}

	Runnable runnableInviteNewDevicesEnd = new Runnable() {

		public void run() {
			addressingInProgress = false;
		}
	};

	// Lookup new devices
	Runnable runnableInviteNewDevices = new Runnable() {
		public void run() {

			if (!isAddressingInProgress() && !isCheckupInProgress() && !isDataRequestInProgress()) {
				
				bridgeLMNWiredImpl.deactivateSerialDataListener();

				// Noch kein Teilnehmer vorhanden
				if (deviceList.isEmpty()) {
					log.info(testRequestDevicesOnZero);
					serialPort.writeBytes(hdlcFrameAddressingOnEmptyList.getBytes(),
							hdlcFrameAddressingOnEmptyList.getLength());
				} else { // Mindestens 1 Teilnehmer vorhanden
					log.info(testRequestDevicesWithExisting);

					hdlcFrameAddressingOnDevicesInList = new HdlcFrameAddressingOnDevicesInList((byte) timeSlots,
							deviceList);

					serialPort.writeBytes(hdlcFrameAddressingOnDevicesInList.getBytes(),
							hdlcFrameAddressingOnDevicesInList.getLength());
				}

				bridgeLMNWiredImpl.activateSerialDataListener();

				setTimeStampAddressing();

				addressingInProgress = true;

				serviceAddressingEnd.schedule(runnableInviteNewDevicesEnd, timeslotsTime, TimeUnit.MILLISECONDS);
			}
		}

	};

	Runnable runnableCheckDevicePresenceEnd = new Runnable() {

		public void run() {
			presenceCheckInProgress = false;
			if(clearSilentDevices()) {
				bridgeLMNWiredImpl.updateConfigDevices();
			}
		}
	};

	// Check device presence
	Runnable runnableCheckDevicePresence = new Runnable() {

		public void run() {

			if (!bridgeLMNWiredImpl.getDeviceList().isEmpty() && !isCheckupInProgress() && !isAddressingInProgress() && !isDataRequestInProgress()) {

				log.info("Inside Conditions: " + testRequestDevicePresenceCheck + " for " + deviceList.size()
						+ " devices");
				hdlcFrameCheckDevicesInList = new HdlcFrameCheckDevicesInList((byte) timeSlots, deviceList);
				
				bridgeLMNWiredImpl.deactivateSerialDataListener();

				if (serialPort.isOpen())
					serialPort.writeBytes(hdlcFrameCheckDevicesInList.getBytes(),
							hdlcFrameCheckDevicesInList.getLength());
				else {
					log.info("Error: Serial Port is not available.");
				}

				bridgeLMNWiredImpl.activateSerialDataListener();

				setTimeStampCheckPresence();

				presenceCheckInProgress = true;

				servicePresenceCheckEnd.schedule(runnableCheckDevicePresenceEnd, timeslotsTime, TimeUnit.MILLISECONDS);
			}

		}

	};

	// Get Data from Device
	Runnable runnableDataRequestEnd = new Runnable() {

		public void run() {
			timeOutLastDataRequest();
			dataRequestInProgress = false;
		}

	};

	// Get Data from Device
	Runnable runnableDataRequest = new Runnable() {

		public void run() {

			if (!bridgeLMNWiredImpl.getDeviceList().isEmpty() && !dataRequestQueue.isEmpty() && !isCheckupInProgress()
					&& !isAddressingInProgress() && !isDataRequestInProgress() && !isDataRequestInProgress()) {

				currentDataRequestTask = dataRequestQueue.poll();

				log.info("Request obis data for device");
				
				currentDataRequestTask.timeOutOccured = true;

				serialPort.writeBytes(currentDataRequestTask.getHdlcData(), currentDataRequestTask.getHdlcDataLength());

				setTimeStampLastDataRequest();
				
				dataRequestInProgress = true;
				
				serviceDataRequestTimout.schedule(runnableDataRequestEnd, 20, TimeUnit.MILLISECONDS);
			}

		}

	};

	protected void startServiceHandler() {
		// Live
		service.scheduleAtFixedRate(runnableInviteNewDevices, 0, 10, TimeUnit.SECONDS);
		service.scheduleAtFixedRate(runnableCheckDevicePresence, 5, 10, TimeUnit.SECONDS);
		service.scheduleAtFixedRate(runnableDataRequest, 0, 127, TimeUnit.MILLISECONDS);

		// Testing
//				service.schedule(runnableInviteNewDevices, 0,TimeUnit.SECONDS);
//				service.schedule(runnableCheckDevicePresence, 5, TimeUnit.SECONDS);
//				service.scheduleAtFixedRate(runnableTestDataRequest, 0, 1, TimeUnit.SECONDS);
	}

	public void addHdlcDataRequest(LMNWiredTask lMNWiredTask) {
		dataRequestQueue.add(lMNWiredTask);
	}

	public boolean clearSilentDevices() {
		boolean deviceRemoved = false;
		if (!deviceList.isEmpty())
			for (Device tmpDevice : deviceList) {
				if (!tmpDevice.isPresent()) {
					deviceList.remove(tmpDevice);
					deviceRemoved = true;
				}
			}
		
		return deviceRemoved;
	}

	public void timeOutLastDataRequest() {
		if (currentDataRequestTask.timeOutOccured) 
			currentDataRequestTask.getDevice().getFirstTask();
	}

	public long setTimeStampAddressing() {
		timeStampAddressing = System.nanoTime();
		return timeStampAddressing;
	}

	public long setTimeStampLastDataRequest() {
		timeStampLastDataRequest = System.nanoTime();
		return timeStampLastDataRequest;
	}

	public long getTimeStampAddressing() {
		return timeStampAddressing;
	}

	public long setTimeStampAddressingEnd() {
		timeStampAddressingEnd = System.nanoTime();
		return timeStampAddressingEnd;
	}

	public long setTimeStampCheckPresence() {
		timeStampCheckPresence = System.nanoTime();
		return timeStampCheckPresence;
	}

	public long getTimeStampCheckPresence() {
		return timeStampCheckPresence;
	}

	public long setTimeStampCheckPresenceEnd() {
		timeStampCheckPresenceEnd = System.nanoTime();
		return timeStampCheckPresenceEnd;
	}

	public void shutdown() {
		service.shutdown();
	}

	public boolean isAddressingInProgress() {
		return addressingInProgress;
	}

	public boolean isCheckupInProgress() {
		return presenceCheckInProgress;
	}

	public boolean isDataRequestInProgress() {
		return dataRequestInProgress;
	}

}
