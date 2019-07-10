package io.openems.edge.bridge.lmnwired.api.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.edge.bridge.lmnwired.api.AbstractOpenEmsLMNWiredComponent;
import io.openems.edge.bridge.lmnwired.api.BridgeLMNWired;
import io.openems.edge.bridge.lmnwired.api.Device;
import io.openems.edge.bridge.lmnwired.hdlc.HdlcFrame;
import io.openems.edge.bridge.lmnwired.hdlc.HdlcFrameDeviceDataRequest;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.meter.api.SymmetricMeter;

public class LMNWiredTask {

	protected AbstractOpenEmsLMNWiredComponent abstractOpenEmsLMNWiredComponent;
	protected BridgeLMNWired bridgeLMNWired;
	Device device;
	String obisPart;
	String obis;
	String serialNumber;
	byte hdlcData[];
	int hdlcDataLength;
	SymmetricMeter.ChannelId channelId;
	int millisForTimeout = 5;
	private final Logger log = LoggerFactory.getLogger(LMNWiredTask.class);
	public boolean timeOutOccured = true;
	Float fData;

	public LMNWiredTask(AbstractOpenEmsLMNWiredComponent abstractOpenEmsLMNWiredComponent,
			BridgeLMNWired bridgeLMNWired, String serialNumber, String obisPart, SymmetricMeter.ChannelId channelId) {
		this.abstractOpenEmsLMNWiredComponent = abstractOpenEmsLMNWiredComponent;
		this.bridgeLMNWired = bridgeLMNWired;
		this.obisPart = obisPart;
		this.serialNumber = serialNumber;
		this.channelId = channelId;
	}

	public Device getDevice() {
		return device;
	}

	public byte[] getHdlcData() {
		return hdlcData;
	}

	public int getHdlcDataLength() {
		return hdlcDataLength;
	}
	
	public String getObis() {
		return obis;
	}

	public boolean getRequest() {

		if (!bridgeLMNWired.getDeviceList().isEmpty())
			for (Device tmpDevice : bridgeLMNWired.getDeviceList()) {
				if (new String(tmpDevice.getSerialNumber()).equals(serialNumber)) {
					device = tmpDevice;
					obis = new String(device.getBytesForObisRequest(obisPart));

					log.info("Reqeust Data for channel: " + obisPart);					

					HdlcFrameDeviceDataRequest hdlcFrameDeviceDataRequest = new HdlcFrameDeviceDataRequest(device,
							obisPart);
					hdlcData = hdlcFrameDeviceDataRequest.getBytes();
					hdlcDataLength = hdlcFrameDeviceDataRequest.getLength();
					bridgeLMNWired.getAddressing().addHdlcDataRequest(this);

					return true;
				}
			}

		return false;
	}

	/**
	 * 
	 * @param hdlcFrame Raw Data Frame
	 */
	public void setResponse(HdlcFrame hdlcFrame) {

		Channel<Float> channel = abstractOpenEmsLMNWiredComponent.channel(channelId);
		String tmpString = new String(hdlcFrame.getData()).replace(obis + ";","");
		String[] arrData = tmpString.split("\\*");
		
		try {
			fData = (Float.parseFloat(arrData[0])) * 1000;
		} catch (Exception e) {
			log.info(tmpString);
			return;
		}

		log.info("Set channel data: " + fData);
		log.info("For device: " + new String(device.getSerialNumber()));
		log.info("For channel: " + obisPart);

		channel.setNextValue(fData);
	}

}
