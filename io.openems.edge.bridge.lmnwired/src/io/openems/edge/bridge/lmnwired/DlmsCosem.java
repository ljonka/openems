package io.openems.edge.bridge.lmnwired;

import gurux.dlms.GXReplyData;
import gurux.dlms.objects.GXDLMSObjectCollection;

public class DlmsCosem {

	public static String getObisCode() {

		
		String s = new String();
//		s = "1-0:0.0.0*255";	//serverID
		s = "1-0:1.8.0*255";	//Wirkenergie Total
//		s = "1-0:21.7.0*255";	//Wirkleistung Phase 1
//		s = "1-0:41.7.0*255";	//Wirkleistung Phase 2
//		s = "1-0:61.7.0*255";	//Wirkleistung Phase 3
//		s = "1-0:1.7.0*255";
//		s = "11-0:96.5.5*255";
//		s = "0-0:96.1.255*255";
		
		//Ausgabe Obis-Code (binär oder Ascii)
//		System.out.println(stringBuffer.toString());
//		System.out.println(util.convertToAscii(stringBuffer.toString()));

		return util.asciiToBinary(s);
	}

	private static void readDataBlock(GXDLMSObjectCollection objects, GXReplyData reply) {
		// TODO Auto-generated method stub
		
	}

}