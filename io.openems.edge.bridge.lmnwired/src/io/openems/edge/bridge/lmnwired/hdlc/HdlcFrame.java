package io.openems.edge.bridge.lmnwired.hdlc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class HdlcFrame {
	HdlcAddress hdlcSourceAddress;
	HdlcAddress hdlcDestinationAddress;
	HdlcAddressPair addressPair;
	int sendSequence;
	int receiveSequence;
	byte[] data;
	boolean segmented;
	boolean addLcc;

	byte[] retData;
	int length;

	public byte[] getBytes() {
		byte[] a = addressPair.getBytes();
		byte[] b = new byte[] { (byte) sendSequence, (byte) receiveSequence };
		byte[] c = data;
		byte[] d = new byte[] { (byte) (segmented ? 1 : 0), (byte) (addLcc ? 1 : 0) };

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			output.write(a);
			output.write(b);
			output.write(c);
			output.write(d);
		} catch (IOException e) {
			e.printStackTrace();
		}

		retData = output.toByteArray();

		length = retData.length;

		return retData;
	}

	public int getLength() {
		return length;
	}

}
