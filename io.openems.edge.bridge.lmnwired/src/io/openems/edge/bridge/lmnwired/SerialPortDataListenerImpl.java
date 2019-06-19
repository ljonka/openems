package io.openems.edge.bridge.lmnwired;

import java.util.Arrays;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.util.concurrent.Semaphore;

public class SerialPortDataListenerImpl implements SerialPortDataListener {

	private static StringBuilder newDataString;
//	private static List<DataEvent> listeners2 = new ArrayList<DataEvent>();
	private PortManager parent;
//	private ManageEventListeners el;
	private SlaveManager sm = new SlaveManager();;
	private int length = 0;
	private BridgeLMNWiredImpl main;
	private long time = 0;
	private int c = 0;
	private int added = 0;
	
	// Semaphore maintains a set of permits.
    // Each acquire blocks if necessary until a permit is available, and then takes it.
    // Each release adds a permit, potentially releasing a blocking acquirer.
    static Semaphore semaphore = new Semaphore(0);
    static Semaphore mutex = new Semaphore(1);
//	private static Starter st;

//	public static void addListener2(DataEvent d) {
//		listeners2.add(d);
//	}

//	public static void Event2_Data() {
//		String frame = newDataString.toString();
////		System.out.println("einkommendes Event, Daten wurden empfangen");
//		for (DataEvent d : listeners2) {
//			d.dataEvent(frame);
//			break;// sonst mehrfaches Ausgeben des einkommenden Datenframes
//		}
//	}

	public SerialPortDataListenerImpl(PortManager parent, StringBuilder nDataString, SerialPort comPort,
			SlaveManager sm, BridgeLMNWiredImpl main) {
		newDataString = nDataString;
		this.main = main;
		this.parent = parent;
		this.sm = sm;
	}

	
	@Override
	public int getListeningEvents() {
		return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
	}

	public void byteReceived() {

//		Testausgabe:
//		System.out.println(parent.state.get()+ " bei "+(System.currentTimeMillis()-main.getTime())+" Millisekunden");
		try {
			mutex.acquire();

			if (this.parent.state.get().equals(DataAvailability.INITIAL)
					|| this.parent.state.get() == DataAvailability.CORRUPT_DATA
					|| this.parent.state.get() == DataAvailability.DATA_AVAILABLE) {
//				System.out.println(parent.state.get());
				added = 0;
				this.parent.state.set(DataAvailability.TIME_SET);
				time = System.currentTimeMillis();
				System.out.println(time - main.getTime() + " Millisekunden");
				//// wie oft passt Timeslot in Zeitdifferenz zw. ankommendem Frame und
				//// ausgesendetem Broadcast rein?
				c = ((int) (time - main.getTime())) / main.getTimeslotLength();
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			mutex.release();
		}
	}

	@Override
	public void serialEvent(SerialPortEvent event) {

		boolean check = false;
		try {
			mutex.acquire();
			//Problem: Thread wird manchmal vor Methode byteReceived aufgerufen. Vorrübergehende Lösung:
			if (parent.state.get() == DataAvailability.INITIAL || parent.state.get() == DataAvailability.CORRUPT_DATA) {
//				// FIXME
//				this.byteReceived();
				this.parent.state.set(DataAvailability.TIME_SET);
				time = System.currentTimeMillis();
				System.out.println(time - main.getTime() + " Millisekunden");
				c = ((int) (time - main.getTime())) / main.getTimeslotLength();
			}

			if (parent.state.get().equals(DataAvailability.TIME_SET)
					|| parent.state.get().equals(DataAvailability.TIME_SET_FETCHING_DATA)) {

//						System.out.println(parent.state.get());

				parent.state.set(DataAvailability.TIME_SET_FETCHING_DATA);

//						System.out.println(parent.state.get());

				byte[] newData = event.getReceivedData();
				System.out.println("\nReceived data of size: " + newData.length);

				for (int i = 0; i < newData.length; ++i) {
					byte currentByte = newData[i];

					if ((newDataString.length() <= 0)) {
						if (currentByte == 0b01111110) {

							newDataString.append(util.UnsignedByteToBit(currentByte));
							// Länge mit Flags
							if ((!(newData.length < 3)) && (newData.length >= (i + 2))) {
								length = util.getFrameLengthMask(util.UnsignedByteToBit(newData[i + 1]),
										util.UnsignedByteToBit(newData[i + 2]));
								// Framelänge darf höchstens 2047 sein
								if (length > 2047) {
									handleCorruptData(newData);
								}
							}
						} else {
							System.out
									.println("Frame fehlerhaft angekommen.Startflag ist falsch. Frame wird verworfen.");
							System.out.println("data (" + Arrays.toString(event.getReceivedData()) + ")");
							handleCorruptData(newData);
							// Prüft, ob vorher im selben Timeslot bereits jemand hinzugefügt wurde, Falls
							// ja, wird dieser Teilnehmer wieder entfernt
							CheckFrame.checkOldFrame(c, main.getMessageType(), added);
							break;
						}
					} else if ((currentByte == 0x7E) && newDataString.length() == (length - 1) * 8) {
						if ((newDataString.substring(0, 8).equals("01111110"))) {
							newDataString.append(util.UnsignedByteToBit(currentByte));
							parent.state.set(DataAvailability.DATA_AVAILABLE);
//									System.out.println(parent.state.get());
							if (check == false) {
								System.out.println("Frame vollständig. Frame wird zur Verarbeitung weitergeschickt");
								added = CheckFrame.checkFrame(util.convertFrame(newDataString.toString()), main, c);
								check = true;
							}
							handleInitialData(newData);
							break;
						}
					} else if ((newDataString.length() == length * 8) && ((!((newDataString.substring(0, 8))
							.equals("01111110")))
							|| (!((newDataString.substring((length - 1) * 8, length * 8)).equals("01111110"))))) {
//								System.out.println("Framelength aus Frame gelesen ist: " + length / 8
//										+ " und Framelength bei jetzigem Stand ist: " + newDataString.length());
						System.out.println(
								"Frame fehlerhaft angekommen. Stop-&/oder Startflag oder framelength ist falsch");
//								System.out.println("Frame ist aktuell: " + newDataString);
//								System.out.println("Und in Ascii: " + util.convertToAscii(newDataString.toString()));
						handleCorruptData(newData);
//								break;
					} else 
						newDataString.append(util.UnsignedByteToBit(currentByte));
							
				}
			} else {
				System.out.println("Ignoring data (" + Arrays.toString(event.getReceivedData()) + ")");
				CheckFrame.setReady();
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
//				}
		} finally {
			mutex.release();
		}
	}
	

	private void handleInitialData(byte[] newData) {
		newDataString = new StringBuilder();
		newData = new byte[0];
		parent.state.set(DataAvailability.INITIAL);
//		System.out.println(parent.state.get());
	}

	private void handleCorruptData(byte[] newData) {
		newDataString = new StringBuilder();
		newData = new byte[0];
		parent.state.set(DataAvailability.CORRUPT_DATA);
//		System.out.println(parent.state.get());
	}

}