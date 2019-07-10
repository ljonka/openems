package io.openems.edge.bridge.lmnwired.hdlc;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazecast.jSerialComm.SerialPort;

import io.openems.edge.bridge.lmnwired.BridgeLMNWiredImpl;
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
	protected int timeslotsTime;
	protected int timeSlots;
	protected boolean addressingInProgress = false;
	protected boolean presenceCheckInProgress = false;
	protected Queue<LMNWiredTask> dataRequestQueue = new LinkedList<LMNWiredTask>();
	protected LMNWiredTask currentDataRequestTask;

	protected BridgeLMNWiredImpl bridgeLMNWiredImpl;

	@Reference
	protected ConfigurationAdmin cm;

	/**
	 * 
	 * @param serialPort
	 */
	public PackageHandler(SerialPort serialPort, int timeSlots, int timeslotsTime,
			BridgeLMNWiredImpl bridgeLMNWiredImpl) {
		this.bridgeLMNWiredImpl = bridgeLMNWiredImpl;
		this.serialPort = serialPort;
		hdlcFrameAddressingOnEmptyList = new HdlcFrameAddressingOnEmptyList((byte) timeSlots);
		this.timeslotsTime = timeslotsTime;
		this.timeSlots = timeSlots;

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

			if (!isAddressingInProgress() && !isCheckupInProgress()) {

				bridgeLMNWiredImpl.deactivateSerialDataListener();
				bridgeLMNWiredImpl.activateSerialDataListener();

				// Noch kein Teilnehmer vorhanden
				if (bridgeLMNWiredImpl.getDeviceList().isEmpty()) {
					log.info(testRequestDevicesOnZero);

					// Debug output
//					log.info("Current time before send: " + System.nanoTime());

					serialPort.writeBytes(hdlcFrameAddressingOnEmptyList.getBytes(),
							hdlcFrameAddressingOnEmptyList.getLength());
				} else { // Mindestens 1 Teilnehmer vorhanden
					log.info(testRequestDevicesWithExisting + bridgeLMNWiredImpl.getDeviceList().size());

					hdlcFrameAddressingOnDevicesInList = new HdlcFrameAddressingOnDevicesInList((byte) timeSlots,
							bridgeLMNWiredImpl.getDeviceList());

					serialPort.writeBytes(hdlcFrameAddressingOnDevicesInList.getBytes(),
							hdlcFrameAddressingOnDevicesInList.getLength());
				}
				
				bridgeLMNWiredImpl.resetCurrentPackage();

				setTimeStampAddressing();

				addressingInProgress = true;

				serviceAddressingEnd.schedule(runnableInviteNewDevicesEnd, timeslotsTime, TimeUnit.MILLISECONDS);
			}
		}

	};

	Runnable runnableCheckDevicePresenceEnd = new Runnable() {

		public void run() {
			presenceCheckInProgress = false;
			clearSilentDevices();
			// bridgeLMNWiredImpl.updateConfigDevices();
		}
	};

	// Check device presence
	Runnable runnableCheckDevicePresence = new Runnable() {

		public void run() {

			if (!bridgeLMNWiredImpl.getDeviceList().isEmpty() && !isCheckupInProgress() && !isAddressingInProgress()) {

				log.info("Inside Condition30s: " + testRequestDevicePresenceCheck + " for "
						+ bridgeLMNWiredImpl.getDeviceList().size() + " devices");
				hdlcFrameCheckDevicesInList = new HdlcFrameCheckDevicesInList((byte) timeSlots,
						bridgeLMNWiredImpl.getDeviceList());

//				bridgeLMNWiredImpl.deactivateSerialDataListener();

				if (serialPort.isOpen())
					serialPort.writeBytes(hdlcFrameCheckDevicesInList.getBytes(),
							hdlcFrameCheckDevicesInList.getLength());
				else {
					log.info("Error: Serial Port is not available.");
				}

//				bridgeLMNWiredImpl.activateSerialDataListener();

				bridgeLMNWiredImpl.resetCurrentPackage();

				setTimeStampCheckPresence();

				presenceCheckInProgress = true;

				servicePresenceCheckEnd.schedule(runnableCheckDevicePresenceEnd, timeslotsTime, TimeUnit.MILLISECONDS);
			}

		}

	};

	// Get Data from Device
	Runnable runnableDataRequest = new Runnable() {

		public void run() {

			if (!bridgeLMNWiredImpl.getDeviceList().isEmpty() && !dataRequestQueue.isEmpty() && !isCheckupInProgress()
					&& !isAddressingInProgress()) {

				currentDataRequestTask = dataRequestQueue.poll();

				log.info("Request obis data for device");

				currentDataRequestTask.timeOutOccured = true;

//				bridgeLMNWiredImpl.deactivateSerialDataListener();
//				bridgeLMNWiredImpl.activateSerialDataListener();

				serialPort.writeBytes(currentDataRequestTask.getHdlcData(), currentDataRequestTask.getHdlcDataLength());

				bridgeLMNWiredImpl.resetCurrentPackage();

			}

		}

	};

	public LMNWiredTask getCurrentTask() {
		return currentDataRequestTask;
	}

	protected void startServiceHandler() {
		// Live

		// TODO: Activate again
		service.scheduleAtFixedRate(runnableInviteNewDevices, 0, 30, TimeUnit.SECONDS);
		service.scheduleAtFixedRate(runnableCheckDevicePresence, 15, 30, TimeUnit.SECONDS);
		service.scheduleAtFixedRate(runnableDataRequest, 0, 200, TimeUnit.MILLISECONDS);
//		

		// Testing
//		service.scheduleAtFixedRate(runnableInviteNewDevices, 0, 200, TimeUnit.MILLISECONDS);
//				service.schedule(runnableInviteNewDevices, 0,TimeUnit.SECONDS);
//				service.schedule(runnableCheckDevicePresence, 5, TimeUnit.SECONDS);
//				service.scheduleAtFixedRate(runnableTestDataRequest, 0, 1, TimeUnit.SECONDS);
//		service.scheduleAtFixedRate(runnableRestSerialDataListener, 0, 10, TimeUnit.MILLISECONDS);

	}

	public void addHdlcDataRequest(LMNWiredTask lMNWiredTask) {
		dataRequestQueue.add(lMNWiredTask);
	}

	public boolean clearSilentDevices() {
		boolean deviceRemoved = false;
		if (!bridgeLMNWiredImpl.getDeviceList().isEmpty())
			for (Device tmpDevice : bridgeLMNWiredImpl.getDeviceList()) {
				if (!tmpDevice.isPresent()) {
					bridgeLMNWiredImpl.getDeviceList().remove(tmpDevice);
					deviceRemoved = true;
				}
			}

		return deviceRemoved;
	}

	public long setTimeStampAddressing() {
		timeStampAddressing = System.nanoTime();
		return timeStampAddressing;
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

}
