package io.openems.edge.bridge.lmnwired;

import java.util.ArrayList;
import java.util.List;

public class CheckFrame implements TimerStopEvent {

	private static boolean[] aCheck;
	private static SlaveManager sm = new SlaveManager();
	private static int nrOfSlavesBeforeBroadcast = 0;
	private static int timeslotOccupied;
	private static boolean ready = false; // true, wenn Verarbeitung abgeschlossen ist
	private static List<EndOfDataRequest> listeners = new ArrayList<EndOfDataRequest>();

	public CheckFrame(boolean[] acheck, int nrOSlavesBeforeBroadcast, SlaveManager s) {
		System.out.println("Zählerzahl vor Broadcast war: " + s.getNumberOfSlaves());
		this.nrOfSlavesBeforeBroadcast = nrOSlavesBeforeBroadcast;
		this.sm = s;
		this.aCheck = acheck;
		for (int i = 0; i < nrOfSlavesBeforeBroadcast; i++) {
			aCheck[i] = false;
		}
	}

	public CheckFrame() {
	}

	public void addListener(EndOfDataRequest e) {
		listeners.add(e);
	}

	public void Event_DataEnd() {
		for (EndOfDataRequest e : listeners) {
			e.EndOfData();
		}
	}

	public void stopTimer(String messageType) {
		// Verarbeitung nach Adressprüfung
//		System.out.println(messageType);
		while (!ready) {
			// Warte, bis Verarbeitung fertig ist
		}
		timeslotOccupied = 0;
		if (messageType.equals("check")) {
			int b = 0;
			for (int i = 0; i < nrOfSlavesBeforeBroadcast; i++) {
				if (aCheck[i] == false) {
					sm.removeFromList(i - b);
					b++;
				}
			}
		}
		System.out.println("Es bleiben noch " + sm.getNumberOfSlaves() + " Teilnehmer.");
	}

	public static void checkOldFrame(int c, String messageType, int added) {

		if (added == (c / 2 + 1)) {
			switch (messageType) {
			case "request":
				System.out.println(
						"Zuletzt hinzugefügter Teilnehmer " + sm.getNumberOfSlaves() + " wird wieder gelöscht");
				sm.removeFromList(added - 1);
				break;
			case "check":
				System.out.println("Rückmeldung des letzten Teilnehmers " + sm.getNumberOfSlaves()
						+ " wird wieder gelöscht, da 2 Frames im gleichen Timeslot geantwortet haben.");
				aCheck[added - 1] = false;
				break;
			}
		}
		ready = true;
	}

	public static int checkFrame(String[] frame, BridgeLMNWiredImpl main, int c) {
		String mType = main.getMessageType();
		int added = 0;
		// Wenn c ungerade, ist Frame innerhalb eines Timeslots angekommen
		if (((c / 2 + 1) > main.getTimeslots()) && !((main.getMessageType().equals("dataRequest"))
				|| main.getMessageType().equals("noMessage") || main.getMessageType().equals("obis"))) {
			System.out.println("Achtung: Es kam ein Frame nach Ablauf der Response-Zeit an");
		} else if ((c % 2 != 0) || (main.getMessageType().equals("dataRequest"))
				|| (main.getMessageType().equals("obis"))) {
			System.out.println((c / 2 + 1) + " Timeslot");
			if ((((c / 2) + 1) != timeslotOccupied) || (main.getMessageType().equals("dataRequest"))) {
				if (!(main.getMessageType().equals("dataRequest"))) {
					timeslotOccupied = (c / 2) + 1;
				}
				if (Crc16.checkCrc16(frame, "HCS")) {
					if (Crc16.checkCrc16(frame, "CRC")) {
						// Bei Timeslotvorgabe im Broadcast ,muss geprüft werden, ob Frame im richtige
						// Timeslot angekommen ist
						if ((nrOfSlavesBeforeBroadcast != 0) && main.getMessageType().equals("check")) {
							if ((sm.rightTimeslot((c / 2) + 1))) {
								added = Switch(sm, frame, main, c, mType);
							} else
								System.out.println("Error! Frame hat nicht im vorgegebenen Timeslot geantwortet.");
						} else
							added = Switch(sm, frame, main, c, mType);

					} else
						System.out.println("CRC-Berechnung nicht übereinstimmend! Frame wird verworfen");
				} else
					System.out.println("HCS-Berechnung nicht übereinstimmend! Frame wird verworfen");
			} else {
				System.out.println(
						"Es sind mehrere vollständige Frames im selben Timeslot angekommen! Wenn bereits 1 Teilnehmer hinzugefügt wurde, wird er wieder gelöscht");
				checkOldFrame(c, main.getMessageType(), added);
			}
		}
		// Wenn c gerade, ist Frame innerhalb der Guardtime angekommen
		else {
			System.out.println("Frame ist außerhalb der Timeslots angekommen (Guard-Time) und wird verworfen");
		}

		ready = true;
		return added;
	}

	public static int Switch(SlaveManager s, String[] frame, BridgeLMNWiredImpl main, int c, String mType) {
		switch (mType) {
		case "request":
			// addressExisting gibt Teilnehmernummer zurück, wenn Adresse in Teilnehmerliste
			// existiert, sonst 0
			if ((sm.addressExisting(util.getAddressMask(frame[6])) == 0)
					&& (Integer.parseInt(util.getAddressMask(frame[6]), 2) < 128)) { // Falls Adresse noch nicht
																						// vorhanden ist...

				sm.addToList(frame[6]); // ...Teilnehmer hinzufügen
				return sm.getNumberOfSlaves();

			} else {
				System.out.println(
						"Achtung: Vom Zähler gewählte Adresse ist schon besetzt, Zähler hat sich öfter als 1 mal gemeldet oder Adresse ist zu groß! Bei nächstem Broadcast neue Chance auf Adresswahl");

				return 0;
			}
		case "check":
			if (s.addressExisting(util.getAddressMask(frame[6])) != 0) {
				aCheck[sm.addressExisting(util.getAddressMask(frame[6])) - 1] = true;
				System.out.println("\nTeilnehmer " + s.addressExisting(util.getAddressMask(frame[6]))
						+ " hat sich zurückgemeldet");
				return c / 2 + 1;
			} else {
				System.out
						.println("Error: Adresse des antwortenden Zählers ist nicht in der Teilnehmerliste vorhanden");
				return 0;
			}
		case "dataRequest":
			// Umwandlung von Bits Ascii:
//			if (timeslotOccupied == 0) {
				System.out.println("Empfangene Payload binär: " + frame[6]);
				System.out.println("Empfangene Payload Ascii: " + util.convertToAscii(frame[6]));
				CheckFrame checkframe = new CheckFrame();
//				checkframe.addListener(main);
				checkframe.Event_DataEnd();
//			}
			break;
		case "noMessage":
			System.out.println("Es kamen Daten ohne vorherige Anfrage des Masters an. Sie werden verworfen");
			break;

		case "obis":
			System.out.println("Empfangene Obis-Zahlen: ");
			System.out.println(util.convertToAscii(frame[6]));
			sm.addObis(Integer.parseInt(frame[3]), frame[6]);

			break;

		default:
			break;
		}
		return 0;
	}

	static void setReady() {
		ready = true;
	}
}
