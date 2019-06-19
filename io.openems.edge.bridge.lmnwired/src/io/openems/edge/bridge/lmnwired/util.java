package io.openems.edge.bridge.lmnwired;

public class util {
	// Benötigt zum Füllen der Nullen in frametype-Variable der Klasse Frame

	public static String FillNull(int l, int x) // l=Zahl (dezimal), x Stellen sind von den 16 Bit noch frei
	{
		int i = (Integer.toBinaryString(l)).length(); // Anzahl der Bits der binären Zahl der Framelänge
		String n = new String(); // Nullen-String
		for (int j = 0; j < (x - i); j++) {
			n += "0"; // übrige Plätze mit Nullen füllen
		}
		return n;
	}

	public static String UnsignedByteToBit(byte byteSubFrame) {
		String byteSubString = util.FillNull(Byte.toUnsignedInt((byte) byteSubFrame), 8)
				+ Integer.toBinaryString(Byte.toUnsignedInt((byte) byteSubFrame));
		return byteSubString;
	}

	public static byte BitToByte(String byteStr) {
		int re, len;
		if (null == byteStr) {
			return 0;
		}
		len = byteStr.length();

		if (len != 4 && len != 8) {
			return 0;
		}
		if (len == 8) {
			if (byteStr.charAt(0) == '0') // 8 Bit Verarbeitung
			{
				re = Integer.parseInt(byteStr, 2); // Positive Zahl
			} else {
				re = Integer.parseInt(byteStr, 2) - 256; // Negative Zahl
			}
		} else {
			re = Integer.parseInt(byteStr, 2); // 4 Bit Verarbeitung
		}
		return (byte) re;
	}

	public static byte[] createByteFrame(String[] frameEinkommend) // Umwandlung Binary Frame in Byte-Array
	{
		String outputFrame = util.getOutputFrame(frameEinkommend); // gesamter Frame in einer Zeile(String)
		byte[] byteFrame = new byte[(outputFrame.length()) / 8]; // gesamter Frame in bytes(Array)

		int i = 0;
		int j = 0;
		String byteSubstring;
		while (i + 8 <= (outputFrame.length())) {
			byteSubstring = outputFrame.substring(i, i + 8);
			byteFrame[j] = util.BitToByte(byteSubstring);
			i += 8;
			j++;
		}
		return byteFrame;
	}

	public static long BytesToWrite(String[] frame) {
		String outputFrame = util.getOutputFrame(frame); // gesamter Frame in einer Zeile(String)
		return (outputFrame.length() / 8);
	}

	public static String getAddressMask(String s) {
		String address = "";
		for (int i = 0; i < 8; i++) {
			address += Character.toString(s.charAt(i));
		}
		return address;
	}

	public static String get7ByteAddress(String s) {
		String address = "";
		for (int i = 0; i < 8; i++) {
			if (i != 0) {
				address += Character.toString(s.charAt(i));
			}
		}
		return address;
	}

	public static String getDeviceIdMask(String s) {
		String id = "";
		for (int i = 16; i < 128; i++) {
			id += Character.toString(s.charAt(i));
		}
		return id;
	}

	public static String getTimeslotMask(String s) {
		String ti = "";
		for (int i = 8; i < 16; i++) {
			ti += Character.toString(s.charAt(i));
		}
		return ti;
	}

	public static int getFrameLengthMask(String s1, String s2) {
		String buffer = s1 + s2;
		int length = (Integer.parseInt(buffer.substring(4, 16), 2)) + 2; // + 2 Flags
		return length;
	}

	public static String setTimeslot(int numberOfSlaves) {
		return Integer.toBinaryString(numberOfSlaves);
	}

	public static String[] getNewFrame(String newDataString) // neue Frames auslesen, die in Textdatei stehen
	{
		String[] newDataFrame = new String[9];

		// newData-String in Subframes zerlegen (binary):
		newDataFrame[0] = newDataString.substring(0, 8);
		newDataFrame[1] = newDataString.substring(8, 24);
		newDataFrame[2] = newDataString.substring(24, 40);
		newDataFrame[3] = newDataString.substring(40, 48);
		newDataFrame[4] = newDataString.substring(48, 56);
		newDataFrame[5] = newDataString.substring(56, 72);
		newDataFrame[6] = newDataString.substring(72, newDataString.length() - 24);
		newDataFrame[7] = newDataString.substring(newDataString.length() - 24, newDataString.length() - 8);
		newDataFrame[8] = newDataString.substring(newDataString.length() - 8, 352);

		// Test: Ausgabe emfangener Frame
//	        for(int i=0;i<9;i++)
//	        {
//	        	System.out.println(newDataFrame[i]);
//	        }

		return newDataFrame;
	}

	public static String getOutputFrame(String[] frame) {
		String outputFrame = "";
		for (int i = 0; i < frame.length; i++) // alle Teilnehmer(+ Teilnehmerinfos) werden aneinandergereiht in einem
												// String übergeben
		{
			outputFrame += frame[i];
		}
		return outputFrame;
	}

	public static String[] getCrcFrame(String[] frame, int c) {
		// Wird lokal definiert:

		String[] newframe = new String[c];
		for (int i = 0; i < c; i++) {
			newframe[i] = frame[i + 1];
		}
		return newframe;
	}

	public static String[] convertFrame(String frame) {
		String[] cframe = new String[9];
		cframe[0] = frame.substring(0, 8);
		cframe[1] = frame.substring(8, 24);
		cframe[2] = frame.substring(24, 40);
		cframe[3] = frame.substring(40, 48);
		cframe[4] = frame.substring(48, 56);
		cframe[5] = frame.substring(56, 72);
		cframe[6] = frame.substring(72, frame.length() - 24);
		cframe[7] = frame.substring(frame.length() - 24, frame.length() - 8);
		cframe[8] = frame.substring(frame.length() - 8, frame.length());
		return cframe;
	}

	// Nur Test
	public static String convertToAscii(String frame) {
		StringBuilder StringAscii = new StringBuilder();
		char[] letters = new char[frame.length() / 8];
		for (int i = 0; i < (frame.length() / 8); i++) {
			letters[i] = (char) Integer.parseInt(frame.substring(8 * i, 8 + (i * 8)), 2);
		}

		for (int i = 0; i < letters.length; i++) {
			StringAscii.append(Character.toString(letters[i]));
		}
		StringBuilder ret = StringAscii;
		return ret.toString();
	}

	public static String asciiToBinary(String asciiString) {
		byte[] obisRequest = asciiString.getBytes();
		StringBuilder stringBuffer = new StringBuilder();
		for (int i = 0; i < obisRequest.length; i++) {
			stringBuffer.append(util.UnsignedByteToBit(obisRequest[i]));
		}
		return stringBuffer.toString();
	}
}
