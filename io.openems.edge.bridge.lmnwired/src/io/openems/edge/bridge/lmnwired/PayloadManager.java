package io.openems.edge.bridge.lmnwired;

public class PayloadManager {

	//Payload des einkommenden Frames
	public static String getCurrentPayload(String[] frame) {
		String currentPayload = frame[6].toString();
		return currentPayload;
		}

	//Payload fr Broadcasts, enthält Liste der Teilnehmer
	public String getPayloadBroadcast(SlaveManager m) {
		StringBuilder payloadBr = new StringBuilder();

		for (int i = 0; i < m.getNumberOfSlaves(); i++) {
			payloadBr.append(m.getAddress(i) + m.getTimesl(i) + m.getDeviceID(i) + m.getDeviceID(i) + m.getState(i));
		}
		
		return payloadBr.toString();
	}
}
