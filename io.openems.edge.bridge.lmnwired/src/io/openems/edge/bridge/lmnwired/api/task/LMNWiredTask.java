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
	String serialNumber;
	byte hdlcData[];
	int hdlcDataLength;
	SymmetricMeter.ChannelId channelId;
	int millisForTimeout = 5;
	private final Logger log = LoggerFactory.getLogger(LMNWiredTask.class);

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

	public boolean getRequest() {

		for (Device tmpDevice : bridgeLMNWired.getDeviceList()) {
			if (new String(tmpDevice.getSerialNumber()).equals(serialNumber)) {
				device = tmpDevice;
				HdlcFrameDeviceDataRequest hdlcFrameDeviceDataRequest = new HdlcFrameDeviceDataRequest(device,
						obisPart);
				device.setCurrentTask(this);
				hdlcData = hdlcFrameDeviceDataRequest.getBytes();
				hdlcDataLength = hdlcFrameDeviceDataRequest.getLength();
				bridgeLMNWired.getAddressing().addHdlcDataRequest(this);
			}
		}

		return true;
	}

	public void setResponse(HdlcFrame hdlcFrame) {
		String[] arrData = new String(hdlcFrame.getData()).split("\\*");
		Float fData = Float.parseFloat(arrData[0]);
		
		log.debug("Set channel data: " + fData);
		log.debug("For device: " + new String(device.getSerialNumber()));
		//abstractOpenEmsLMNWiredComponent.channel(channel).setNextValue(new String(hdlcFrame.getData()));
		Channel<Float> channel = abstractOpenEmsLMNWiredComponent.channel(channelId);
		
		channel.setNextValue(fData);		
	}

}
