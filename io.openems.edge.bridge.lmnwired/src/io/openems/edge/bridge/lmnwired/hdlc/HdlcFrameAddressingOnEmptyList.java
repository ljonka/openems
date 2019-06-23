package io.openems.edge.bridge.lmnwired.hdlc;

public class HdlcFrameAddressingOnEmptyList extends HdlcFrame {

	public HdlcFrameAddressingOnEmptyList() {
		try {
			hdlcSourceAddress = new HdlcAddress(1);
			hdlcDestinationAddress = new HdlcAddress(2);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		addressPair = new HdlcAddressPair(hdlcSourceAddress, hdlcDestinationAddress);
		sendSequence = 1;
		receiveSequence = 1;
		data = new byte[] { (byte) 0x00, (byte) 0xFF, (byte) 0xFF };
		segmented = false;
		addLcc = false;
	}

}
