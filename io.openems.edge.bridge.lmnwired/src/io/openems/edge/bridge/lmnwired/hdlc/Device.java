package io.openems.edge.bridge.lmnwired.hdlc;

/**
 * Device Data for HDLC Layer
 * @author Leonid Verhovskij
 *
 */
public class Device {
	private int hdlcAddress;
	private String serialNumber;
	private int timeSlot;
	
	public Device(int hdlcAddress, String serialNumber, int timeSlot) {
		this.hdlcAddress = hdlcAddress;
		this.serialNumber = serialNumber;
		this.timeSlot = timeSlot;
	}
	public int getHdlcAddress() {
		return hdlcAddress;
	}
	public void setHdlcAddress(int hdlcAddress) {
		this.hdlcAddress = hdlcAddress;
	}
	public int getTimeSlot() {
		return timeSlot;
	}
	public void setTimeSlot(int timeSlot) {
		this.timeSlot = timeSlot;
	}
	public String getSerialNumber() {
		return serialNumber;
	}
	public void setSerialNumber(String serialNumber) {
		this.serialNumber = serialNumber;
	}
	
}
