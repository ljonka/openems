package io.openems.edge.bridge.lmnwired.api.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.edge.bridge.lmnwired.api.AbstractOpenEmsLMNWiredComponent;
import io.openems.edge.bridge.lmnwired.api.BridgeLMNWired;
import io.openems.edge.bridge.lmnwired.api.Device;
import io.openems.edge.bridge.lmnwired.hdlc.HdlcFrame;
import io.openems.edge.bridge.lmnwired.hdlc.HdlcFrameDeviceDataRequest;

import io.openems.edge.meter.api.SymmetricMeter;

public class LMNWiredTask {

	protected AbstractOpenEmsLMNWiredComponent abstractOpenEmsLMNWiredComponent;
	protected BridgeLMNWired bridgeLMNWired;
	Device device;
	String obisPart;
	String serialNumber;
	byte hdlcData[];
	int hdlcDataLength;
	SymmetricMeter.ChannelId channel;
	int millisForTimeout = 5;	
	private final Logger log = LoggerFactory.getLogger(LMNWiredTask.class);

	public LMNWiredTask(AbstractOpenEmsLMNWiredComponent abstractOpenEmsLMNWiredComponent,
			BridgeLMNWired bridgeLMNWired, Device device, String obisPart, SymmetricMeter.ChannelId channel) {
		this.abstractOpenEmsLMNWiredComponent = abstractOpenEmsLMNWiredComponent;
		this.bridgeLMNWired = bridgeLMNWired;
		this.obisPart = obisPart;
		this.device = device;
		this.channel = channel;
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
		HdlcFrameDeviceDataRequest hdlcFrameDeviceDataRequest = new HdlcFrameDeviceDataRequest(device, obisPart);
		hdlcData = hdlcFrameDeviceDataRequest.getBytes();
		hdlcDataLength = hdlcFrameDeviceDataRequest.getLength();
		bridgeLMNWired.getAddressing().addHdlcDataRequest(this);	

		return true;
	}

	public void setResponse(HdlcFrame hdlcFrame) {
		log.debug("Set channel data: " + new String(hdlcFrame.getData()));
		log.debug("For device: " + new String(device.getSerialNumber()));
		abstractOpenEmsLMNWiredComponent.channel(channel).setNextValue(new String(hdlcFrame.getData()));
	}

}
