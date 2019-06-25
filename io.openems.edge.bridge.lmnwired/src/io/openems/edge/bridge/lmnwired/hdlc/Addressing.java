package io.openems.edge.bridge.lmnwired.hdlc;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazecast.jSerialComm.SerialPort;

import io.openems.edge.bridge.lmnwired.api.Device;

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
	private String testRequestDeviceData = "Request device data";
	private HdlcFrameAddressingOnEmptyList hdlcFrameAddressingOnEmptyList;
	private HdlcFrameAddressingOnDevicesInList hdlcFrameAddressingOnDevicesInList;
	private HdlcFrameCheckDevicesInList hdlcFrameCheckDevicesInList;
	protected long timeStampAddressing;
	protected long timeStampAddressingEnd;
	protected long timeStampCheckPresence;
	protected long timeStampCheckPresenceEnd;
	protected int timeslotsTime;
	List<Device> deviceList;

	/**
	 * 
	 * @param serialPort
	 */
	public Addressing(SerialPort serialPort, int timeSlots, int timeslotsTime, List<Device> deviceList) {
		this.serialPort = serialPort;
		hdlcFrameAddressingOnEmptyList = new HdlcFrameAddressingOnEmptyList((byte)timeSlots);
		this.timeslotsTime = timeslotsTime;
		this.deviceList = deviceList;
		
		if(!serialPort.isOpen()) {
			log.info("SerialPort not open.");
			return;
		}

		// Lookup new devices
		Runnable runnableInviteNewDevices = new Runnable() {
			public void run() {

				// Noch kein Teilnehmer vorhanden
				if (deviceList.isEmpty()) {
//					log.info(testRequestDevicesOnZero);
					serialPort.writeBytes(hdlcFrameAddressingOnEmptyList.getBytes(),
								hdlcFrameAddressingOnEmptyList.getLength());
//					log.info("timeStampAddressingOnEmptyList after data send: " + timeStampAddressing);
				} else { // Mindestens 1 Teilnehmer vorhanden
//					log.info(testRequestDevicesWithExisting);
					
					hdlcFrameAddressingOnDevicesInList = new HdlcFrameAddressingOnDevicesInList((byte)timeSlots, deviceList);
					
					serialPort.writeBytes(hdlcFrameAddressingOnDevicesInList.getBytes(), hdlcFrameAddressingOnDevicesInList.getLength());
				}
				
				setTimeStampAddressing();
			}

		};

		// Check device presence
		Runnable runnableCheckDevicePresence = new Runnable() {
			
			

			public void run() {
				
				if (!isAddressingInProgress() && !deviceList.isEmpty()) {
					
					
					for(Device tmpDevice: deviceList) {
						if(!tmpDevice.isPresent()) {
							deviceList.remove(tmpDevice);
						}
					}
					
//					log.info(testRequestDevicePresenceCheck + " for " + deviceList.size() + " devices");
					
					hdlcFrameCheckDevicesInList = new HdlcFrameCheckDevicesInList((byte)timeSlots, deviceList);
					
					serialPort.writeBytes(hdlcFrameCheckDevicesInList.getBytes(),
							hdlcFrameCheckDevicesInList.getLength());
				}
				
				setTimeStampCheckPresence();

			}

		};
		
		// Get Test Data from Device
		Runnable runnableGetDeviceData = new Runnable() {

			public void run() {
				//Request some data if nothing else in progress
				if(!deviceList.isEmpty() && !isAddressingInProgress() && !isCheckupInProgress()) {
					
					log.info(testRequestDeviceData);
					
					Device device = deviceList.get(0);
					HdlcFrameDeviceDataRequest hdlcFrameDeviceDataRequest = new HdlcFrameDeviceDataRequest(device, "1.8.0");
					
					//Debug output
//					for(int i=0;i<hdlcFrameDeviceDataRequest.getBytes().length;i++) {
//						log.info(Integer.toHexString(hdlcFrameDeviceDataRequest.getBytes()[i]  & 0xff ));
//					}
					
					serialPort.writeBytes(hdlcFrameDeviceDataRequest.getBytes(),
							hdlcFrameDeviceDataRequest.getLength());
					
				}
			}

		};

		service = Executors.newSingleThreadScheduledExecutor();

		service.scheduleAtFixedRate(runnableInviteNewDevices, 0, 30, TimeUnit.SECONDS);
		service.scheduleAtFixedRate(runnableCheckDevicePresence, 15, 30, TimeUnit.SECONDS);
		
		service.scheduleAtFixedRate(runnableGetDeviceData, 0, 5, TimeUnit.SECONDS);
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
		return (setTimeStampAddressingEnd() - getTimeStampAddressing() <= timeslotsTime * 1000000 );
	}
	
	public boolean isCheckupInProgress() {
		return (setTimeStampCheckPresenceEnd() - getTimeStampCheckPresence() <= timeslotsTime * 1000000 );
	}

}
