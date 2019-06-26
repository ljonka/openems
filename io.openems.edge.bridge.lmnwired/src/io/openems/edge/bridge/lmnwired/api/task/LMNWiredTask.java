package io.openems.edge.bridge.lmnwired.api.task;

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
		abstractOpenEmsLMNWiredComponent.channel(channel).setNextValue(new String(hdlcFrame.getData()));
	}

}
