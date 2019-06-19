package io.openems.edge.bridge.lmnwired;

public class Crc16 {
	
	public static String getCrc16(String[] frame, String s) {

		// Byte-Array für CRC-Berechnung wird erstellt
		byte[] crcBytes = (util.createByteFrame(frame));

		
		// Ausgabe Test
		StringBuilder ausgabeString = new StringBuilder();
		for (int i = 0; i < crcBytes.length; i++) {
			ausgabeString.append(util.UnsignedByteToBit(crcBytes[i]));
		}
//		System.out.println(util.convertToAscii(ausgabeString.toString()));
//		System.out.println(ausgabeString.toString());

		
		// Berechnung und Ausgabe
		long ccittCrc = Crc16.calculateCRC(CrcParameters.CCITT, crcBytes);
		
		String buffer = Long.toBinaryString(ccittCrc);
		String ccittCrcString = util.FillNull(Integer.parseInt(buffer,2), 16) + buffer;
		
//		System.out.printf("CRC ist 0x%04X\n", ccittCrc); 
//		System.out.print(" und binär: "+ccittCrcString+"\n");
		return ccittCrcString;
		
	}


	public static boolean checkCrc16(String[] frame, String s) {
		
		if(s.equals("HCS")) {
			if(frame[5].equals(getCrc16(util.getCrcFrame(frame, 4),s))) {
				return true;
			}
			else
				return false;
		}
		else if(s.equals("CRC")) {
			if(frame[7].equals(getCrc16(util.getCrcFrame(frame, 6),s))) {
				return true;
			}
			else
				return false;
		}
		else
			System.out.println("Unklar, welche Art von Crc-Berechnung benötigt wird");
			return false;
	}

	private static long reflect(long in, int count) {

		long ret = in;
		for (int idx = 0; idx < count; idx++) {
			long srcbit = 1L << idx;
			long dstbit = 1L << (count - idx - 1);
			if ((in & srcbit) != 0) {
				ret |= dstbit;
			} else {
				ret = ret & (~dstbit);
			}
		}
		return ret;
	}

	/**
	 * This method implements simple straight forward bit by bit calculation. It is
	 * relatively slow for large amounts of data, but does not require any
	 * preparation steps. As a result, it might be faster in some cases then
	 * building a table required for faster calculation.
	 *
	 * Note: this implementation follows section 8 ("A Straightforward CRC
	 * Implementation") of Ross N. Williams paper as even though final/sample
	 * implementation of this algorithm provided near the end of that paper (and
	 * followed by most other implementations) is a bit faster, it does not work for
	 * polynomials shorter then 8 bits.
	 *
	 * @param crcParams CRC algorithm parameters
	 * @param data      data for the CRC calculation
	 * @return the CRC value of the data provided
	 */
	public static long calculateCRC(CrcParameters crcParams, byte[] data) {
		long curValue = crcParams.getInit();
		long topBit = 1L << (crcParams.getWidth() - 1);
		long mask = (topBit << 1) - 1;

		for (int i = 0; i < data.length; i++) {
			long curByte = ((long) (data[i])) & 0x00FFL;
			if (crcParams.isReflectIn()) {
				curByte = reflect(curByte, 8);
			}

			for (int j = 0x80; j != 0; j >>= 1) {
				long bit = curValue & topBit;
				curValue <<= 1;

				if ((curByte & j) != 0) {
					bit ^= topBit;
				}

				if (bit != 0) {
					curValue ^= crcParams.getPolynomial();
				}
			}

		}

		if (crcParams.isReflectOut()) {
			curValue = reflect(curValue, crcParams.getWidth());
		}

		curValue = curValue ^ crcParams.getFinalXor();

		return curValue & mask;
	}
}