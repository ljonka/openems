package io.openems.edge.bridge.lmnwired.hdlc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class HdlcFrame {
	HdlcAddress hdlcSourceAddress;
	HdlcAddress hdlcDestinationAddress;
	HdlcAddressPair addressPair;
	int sendSequence;
	int receiveSequence;
	boolean segmented;
	boolean addLcc;

	byte flag = 0x7E;
	byte format[] = new byte[2]; // 2 bytes
	byte dest[] = new byte[2];
	byte source = 0;
	byte ctrl = 0;
	byte hcs[];
	byte data[];
	byte fcs[] = new byte[2];

	byte[] retData;
	int length;

	public static HdlcFrame createHdlcFrameFromByte(byte data[]) {
		HdlcFrame hdlcFrame = new HdlcFrame();

		// Format
		int length = 0x07 & data[1];
		length <<= 8;
		length |= data[2];

		hdlcFrame.setFormat(length);

		// Dest
		byte type = (byte) ((byte) (0x80 & data[3]) >> 7);
		byte destination = (byte) (0x7F & data[3]);
		byte timeslots = (byte) ((byte) (0xFC & data[4]) >> 2);
		byte protocol = (byte) (0x03 & data[4]);

		hdlcFrame.setDestination(type, destination, timeslots, protocol);

		// Source
		hdlcFrame.setSource(data[5]);

		// Ctrl
		hdlcFrame.setCtrl(data[6]);
		
		// HCS
		hdlcFrame.setHCS(Arrays.copyOfRange(data, 7, 8));

		// Data
		int dataLength = length - 10 + 9;
		hdlcFrame.setData(Arrays.copyOfRange(data, 9, dataLength));
		
		if(hdlcFrame.getHCS() != hdlcFrame.hcs) {
			System.out.println("Fehler in HCS Prüfung!");
		}

		// FCS
		hdlcFrame.setFCS(Arrays.copyOfRange(data, length - 1, length));

		// Check FCS

		return hdlcFrame;
	}

	public byte[] getHCS() {
		if (data != null) {
			byte hcsData[] = getHCSData();
			long calcHCS = calculateCRC(hcsData);
			byte tmpHcs[] = new byte[2];
			tmpHcs[1] = (byte) calcHCS;
			tmpHcs[0] = (byte) (calcHCS >> 8);
			return tmpHcs;
		} else {
			return null;
		}
		
	}

	public boolean checkFCS() {

		return true;
	}

	/**
	 * 
	 * @param formatBits
	 * @return
	 */
	public boolean setFormat(int length) {
		format[0] = (byte) 0xA0;

		format[0] |= (byte) (length >> 8);
		format[1] = (byte) length;

		return true;
	}

	/**
	 * 
	 * @param type
	 * @param destination
	 * @param timeslots
	 * @param protocol
	 * @return
	 */
	public boolean setDestination(byte type, byte destination, byte timeslots, byte protocol) {

		dest[1] |= timeslots << 2;
		dest[1] |= protocol;
		dest[0] |= type << 7;
		dest[0] |= destination;

		return true;
	}

	public boolean setSource(byte source) {
		this.source = source;

		return true;
	}

	public boolean setCtrl(byte ctrl) {
		this.ctrl = ctrl;

		return true;
	}

	public boolean setHCS(byte hcs[]) {
		this.hcs = hcs;

		return true;
	}

	public boolean setData(byte data[]) {
		this.data = data;

		return true;
	}

	public boolean setFCS(byte fcs[]) {
		this.fcs = fcs;

		return true;
	}

	public byte[] getHCSData() {
		ByteArrayOutputStream hcsOutput = new ByteArrayOutputStream();
		try {
			hcsOutput.write(format);
			hcsOutput.write(dest);
			hcsOutput.write(new byte[] { source });
			hcsOutput.write(new byte[] { ctrl });

		} catch (IOException e) {
			e.printStackTrace();
		}
		return hcsOutput.toByteArray();
	}

	public byte[] getBytes() {

		// First Step: Combine Ctrl, Source, Dest and Format
		byte hcsData[] = getHCSData();

		// Second Step: Calc and add HCS if needed
		hcs = getHCS();

		ByteArrayOutputStream fcsOutput = new ByteArrayOutputStream();
		try {
			fcsOutput.write(hcsData);
			if (hcs != null)
				fcsOutput.write(hcs);
			if (data != null)
				fcsOutput.write(data);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Third Step: Add FCS and add combine with data

		short calcFCS = (short) calculateCRC(fcsOutput.toByteArray());

		fcs[1] = (byte) calcFCS;
		fcs[0] = (byte) (calcFCS >> 8);

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			output.write(new byte[] { flag });
			output.write(fcsOutput.toByteArray());
			output.write(fcs);
			output.write(new byte[] { flag });
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

	/**
	 * CRC16-CCITT
	 */
	public int calculateCRC(byte[] bytes) {
		int crc = 0xFFFF; // initial value
		int polynomial = 0x1021; // 0001 0000 0010 0001 (0, 5, 12)

		for (byte b : bytes) {
			for (int i = 0; i < 8; i++) {
				boolean bit = ((b >> (7 - i) & 1) == 1);
				boolean c15 = ((crc >> 15 & 1) == 1);
				crc <<= 1;
				if (c15 ^ bit)
					crc ^= polynomial;
			}
		}

		crc &= 0xffff;
		return crc;
	}

}
