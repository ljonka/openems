package io.openems.edge.bridge.lmnwired.hdlc;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazecast.jSerialComm.SerialPort;

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
	private int registeredDevices = 0;
	private String testRequestDevicesOnZero = "Request device registration with zero devices in list.";
	private String testRequestDevicesWithExisting = "Request device registration with devices in list.";
	private String testRequestDevicePresenceCheck = "Request device presence checkup.";
	private HdlcFrameAddressingOnEmptyList hdlcFrameAddressingOnEmptyList = new HdlcFrameAddressingOnEmptyList();
	protected long timeStampAddressingOnEmptyList; 

	/**
	 * 
	 * @param serialPort
	 */
	public Addressing(SerialPort serialPort) {
		this.serialPort = serialPort;		

		// Lookup new devices
		Runnable runnableInviteNewDevices = new Runnable() {
			public void run() {

				// Noch kein Teilnehmer vorhanden
				if (registeredDevices <= 0) {
					log.info(testRequestDevicesOnZero);
					serialPort.writeBytes(hdlcFrameAddressingOnEmptyList.getBytes(),
								hdlcFrameAddressingOnEmptyList.getLength());
					updateTimeStampAddressingOnEmptyList();
					log.info("timeStampAddressingOnEmptyList after data send: " + timeStampAddressingOnEmptyList);
				} else { // Mindestens 1 Teilnehmer vorhanden
					log.info(testRequestDevicesWithExisting);
//					serialPort.writeBytes(testRequestDevicesWithExisting.getBytes(),
//							testRequestDevicesWithExisting.length());
				}

			}

		};

		// Check device presence
		Runnable runnableCheckDevicePresence = new Runnable() {

			public void run() {

				if (registeredDevices > 0) {
					log.info(testRequestDevicePresenceCheck);
					serialPort.writeBytes(testRequestDevicePresenceCheck.getBytes(),
							testRequestDevicePresenceCheck.length());
				}

			}

		};

		service = Executors.newSingleThreadScheduledExecutor();

		service.scheduleAtFixedRate(runnableInviteNewDevices, 0, 5, TimeUnit.SECONDS);
		service.scheduleAtFixedRate(runnableCheckDevicePresence, 15, 30, TimeUnit.SECONDS);
	}
	
	public long getTimeStampAddressingOnEmptyList() {
		return timeStampAddressingOnEmptyList;
	}
	
	public long updateTimeStampAddressingOnEmptyList() {
		timeStampAddressingOnEmptyList = System.nanoTime();
		return timeStampAddressingOnEmptyList;
	}

	public void shutdown() {
		service.shutdown();
	}
}
