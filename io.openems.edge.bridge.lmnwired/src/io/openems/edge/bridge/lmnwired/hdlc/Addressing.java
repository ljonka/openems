package io.openems.edge.bridge.lmnwired.hdlc;

import java.util.ArrayList;
import java.util.List;
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
public class Addressing {

	protected SerialPort serialPort;
	private ScheduledExecutorService service;
	private final Logger log = LoggerFactory.getLogger(Addressing.class);
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
	protected List<LMNWiredTask> dataRequestQueue = new ArrayList<LMNWiredTask>();
	protected LMNWiredTask currentDataRequestTask;

	/**
	 * 
	 * @param serialPort
	 */
	public Addressing(SerialPort serialPort, int timeSlots, int timeslotsTime, List<Device> deviceList,
			BridgeLMNWiredImpl bridgeLMNWiredImpl) {
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
					// Noch kein Teilnehmer vorhanden
					if (deviceList.isEmpty()) {
						log.debug(testRequestDevicesOnZero);
						serialPort.writeBytes(hdlcFrameAddressingOnEmptyList.getBytes(),
								hdlcFrameAddressingOnEmptyList.getLength());
					} else { // Mindestens 1 Teilnehmer vorhanden
						log.debug(testRequestDevicesWithExisting);

//					for(Device tmpDevice: deviceList) {
//						log.debug(new String(tmpDevice.getSerialNumber()));
//						log.debug(Integer.toString(tmpDevice.getHdlcAddress()));
//					}

						hdlcFrameAddressingOnDevicesInList = new HdlcFrameAddressingOnDevicesInList((byte) timeSlots,
								deviceList);

						serialPort.writeBytes(hdlcFrameAddressingOnDevicesInList.getBytes(),
								hdlcFrameAddressingOnDevicesInList.getLength());
					}

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

				if (!deviceList.isEmpty() && !isAddressingInProgress()) {
					log.debug(testRequestDevicePresenceCheck + " for " + deviceList.size() + " devices");

					for (Device tmpDevice : deviceList) {
						log.debug(new String(tmpDevice.getSerialNumber()));
//						log.debug(Integer.toString(tmpDevice.getHdlcAddress()));
					}

					hdlcFrameCheckDevicesInList = new HdlcFrameCheckDevicesInList((byte) timeSlots, deviceList);

					serialPort.writeBytes(hdlcFrameCheckDevicesInList.getBytes(),
							hdlcFrameCheckDevicesInList.getLength());

					setTimeStampCheckPresence();

					presenceCheckInProgress = true;

					service.schedule(runnableAfterPresenceCheckEnd, timeslotsTime, TimeUnit.MILLISECONDS);
				}

			}

		};

		// Check device presence
		Runnable runnableDataRequest = new Runnable() {

			public void run() {

				if (!dataRequestQueue.isEmpty() && !isAddressingInProgress() && !isCheckupInProgress()) {
					currentDataRequestTask = dataRequestQueue.get(0);
					currentDataRequestTask.getDevice().setCurrentTask(currentDataRequestTask);
					log.debug("Request obis data for device");

					serialPort.writeBytes(currentDataRequestTask.getHdlcData(),
							currentDataRequestTask.getHdlcDataLength());

					dataRequestQueue.remove(currentDataRequestTask);
				}

			}

		};

		// Check device presence
//		Runnable runnableTestDataRequest = new Runnable() {
//
//			public void run() {
//
//				if (!deviceList.isEmpty() && !isAddressingInProgress() && !isCheckupInProgress()) {
//					log.debug("Request obis data for device");
//					HdlcFrameDeviceDataRequest hdlcFrameDeviceDataRequest = new HdlcFrameDeviceDataRequest(
//							deviceList.get(0), "2.8.0");
//
//					serialPort.writeBytes(hdlcFrameDeviceDataRequest.getBytes(),
//							hdlcFrameDeviceDataRequest.getLength());
//					
//				}
//
//			}
//
//		};

		// Live
		service.scheduleAtFixedRate(runnableInviteNewDevices, 0, 10, TimeUnit.SECONDS);
		service.scheduleAtFixedRate(runnableCheckDevicePresence, 5, 10, TimeUnit.SECONDS);
		service.scheduleAtFixedRate(runnableDataRequest, 0, 10, TimeUnit.MILLISECONDS);

		// Testing
//		service.schedule(runnableInviteNewDevices, 0,TimeUnit.SECONDS);
//		service.schedule(runnableCheckDevicePresence, 5, TimeUnit.SECONDS);
//		service.scheduleAtFixedRate(runnableTestDataRequest, 0, 1, TimeUnit.SECONDS);
	}

	public void addHdlcDataRequest(LMNWiredTask lMNWiredTask) {
		dataRequestQueue.add(lMNWiredTask);
	}

	public void clearSilentDevices() {
		for (Device tmpDevice : deviceList) {
			if (!tmpDevice.isPresent()) {
				deviceList.remove(tmpDevice);
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
		// return (setTimeStampAddressingEnd() - getTimeStampAddressing() <=
		// timeslotsTime * 1000000);
		return addressingInProgress;
	}

	public boolean isCheckupInProgress() {
		// return (setTimeStampCheckPresenceEnd() - getTimeStampCheckPresence() <=
		// timeslotsTime * 1000000);
		return presenceCheckInProgress;
	}

}
