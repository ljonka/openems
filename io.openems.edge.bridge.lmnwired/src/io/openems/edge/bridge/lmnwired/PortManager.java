package io.openems.edge.bridge.lmnwired;

import java.util.concurrent.atomic.AtomicReference;

import com.fazecast.jSerialComm.*;

public class PortManager {

	private static SerialPort comPort;
	private StringBuilder newDataString = new StringBuilder();
	private SerialPortDataListenerImpl serialHandler;
	protected AtomicReference<DataAvailability> state = new AtomicReference<DataAvailability>(DataAvailability.INITIAL);
	


	public PortManager(SerialPort comPort, SlaveManager sm, BridgeLMNWiredImpl main) {
		PortManager.comPort = comPort;
		serialHandler = new SerialPortDataListenerImpl(this, newDataString, comPort, sm, main);
		comPort.addDataListener(serialHandler);
		
		Thread thread = new Thread() {
			public void run() {
				while(true) {
					int bytesAvailable = comPort.bytesAvailable();
					if (bytesAvailable > 0) {
						serialHandler.byteReceived();
					}
//					try {
//						Thread.sleep(0, 100);
//					} catch (InterruptedException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
				}
			}
		};
		thread.start();
	}


	// schickt Bytes aus OutputFrame(=Byte-Array) auf Port
	public static void setPortData(String[] frame) {
		comPort.writeBytes(util.createByteFrame(frame), util.BytesToWrite(frame)); // schickt Bytes aus
	}
	
}
