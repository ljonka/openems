package io.openems.edge.bridge.lmnwired.hdlc;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
	private ScheduledExecutorService service;
	private final Logger log = LoggerFactory.getLogger(PackageHandler.class);
	private String testRequestDevicesOnZero = "Request device registration with zero devices in list.";
	private String testRequestDevicesWithExisting = "Request device registration with devices in list.";
	private String testRequestDevicePresenceCheck = "Request device presence checkup.";
	private HdlcFrameAddressingOnEmptyList hdlcFrameAddressingOnEmptyList;
	private HdlcFrameAddressingOnDevicesInList hdlcFrameAddressingOnDevicesInList;
	private HdlcFrameCheckDevicesInList hdlcFrameCheckDevicesInList;
	protected long timeStampAddressing;
	protected long timeStampAddressingEnd;
	protected long timeStampCheckPresence;
	protected long timeStampCheckPresenceEnd;
	protected int timeslotsTime;
	List<Device> deviceList;
	protected boolean addressingInProgress = false;
	protected boolean presenceCheckInProgress = false;
	protected Queue<LMNWiredTask> dataRequestQueue = new LinkedList<LMNWiredTask>();
	protected LMNWiredTask currentDataRequestTask;

	protected BridgeLMNWiredImpl bridgeLMNWiredImpl;

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
		this.deviceList = deviceList;

		service = Executors.newSingleThreadScheduledExecutor();

		if (!serialPort.isOpen()) {
			log.debug("SerialPort not open.");
			return;
		}

		Runnable runnableAfterAddressingTimeEnd = new Runnable() {

			public void run() {
				addressingInProgress = false;
			}

		};

		// Lookup new devices
		Runnable runnableInviteNewDevices = new Runnable() {
			public void run() {

				if (!isCheckupInProgress()) {

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

					service.schedule(runnableAfterAddressingTimeEnd, timeslotsTime, TimeUnit.MILLISECONDS);
				}
			}

		};

		Runnable runnableAfterPresenceCheckEnd = new Runnable() {

			public void run() {
				clearSilentDevices();

				presenceCheckInProgress = false;
			}

		};

		// Check device presence
		Runnable runnableCheckDevicePresence = new Runnable() {

			public void run() {

				log.info("Before Conditions: " + testRequestDevicePresenceCheck + " for " + deviceList.size()
						+ " devices");

				if (!bridgeLMNWiredImpl.getDeviceList().isEmpty()) {

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

					presenceCheckInProgress = true;

					service.schedule(runnableAfterPresenceCheckEnd, timeslotsTime, TimeUnit.MILLISECONDS);
				}

			}

		};

		// Check device presence
		Runnable runnableDataRequest = new Runnable() {

			public void run() {

				if (!bridgeLMNWiredImpl.getDeviceList().isEmpty() && !dataRequestQueue.isEmpty()
						&& !isCheckupInProgress() && !isAddressingInProgress()) {
					
					currentDataRequestTask = dataRequestQueue.poll();

					log.info("Request obis data for device");

					bridgeLMNWiredImpl.deactivateSerialDataListener();

					serialPort.writeBytes(currentDataRequestTask.getHdlcData(),
							currentDataRequestTask.getHdlcDataLength());

					bridgeLMNWiredImpl.activateSerialDataListener();

				}

			}

		};

		// Live
		service.scheduleAtFixedRate(runnableInviteNewDevices, 0, 10, TimeUnit.SECONDS);
		service.scheduleAtFixedRate(runnableCheckDevicePresence, 5, 10, TimeUnit.SECONDS);
		service.scheduleAtFixedRate(runnableDataRequest, 0, 1, TimeUnit.SECONDS);

		// Testing
//		service.schedule(runnableInviteNewDevices, 0,TimeUnit.SECONDS);
//		service.schedule(runnableCheckDevicePresence, 5, TimeUnit.SECONDS);
//		service.scheduleAtFixedRate(runnableTestDataRequest, 0, 1, TimeUnit.SECONDS);
	}

	public void addHdlcDataRequest(LMNWiredTask lMNWiredTask) {
		dataRequestQueue.add(lMNWiredTask);
	}

	public void clearSilentDevices() {
		if (!deviceList.isEmpty())
			for (Device tmpDevice : deviceList) {
				if (!tmpDevice.isPresent()) {
					deviceList.remove(tmpDevice);
					bridgeLMNWiredImpl.updateConfigDevices();
				}
			}
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
