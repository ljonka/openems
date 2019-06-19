package io.openems.edge.bridge.lmnwired;

public class Frame {
	private String[] frameTyp1 = new String[7];
	private String[] frameTyp2 = new String[9];

	private final String flags = "01111110"; // Start-&Stop-Flag
	private String frametype = "0"; // Frametype + Framelnge
	private String srcAdr = "0"; // Source-Adresse
	private String destAdr = "0"; // Destination-Adresse
	private String control = "0"; // Steuerbefehl
	private String hcs = "0"; // Crc16 von Frametype bis control
	private String payload; // Daten
	private String crc = "0"; // Crc16 von Frametype bis Payload

	public Frame(String type, int t, String meterAdr, String pld) throws RuntimeException {

		if (t > 63) {
			throw new RuntimeException("Error: Mehr als 63 Teilnehmer geht nicht!");
		}

		// Frame-Auswahl:
		switch (type) {

		// Fall: Broadcast, noch kein Teilnehmer vorhanden
		case "AddressRequest1":
			createA1Frame(t);
			break;
		// Fall: Broadcast, noch nicht alle Teilnehmer vorhanden
		case "AddressRequest2":
			if (!pld.isEmpty()) {
				createA2Frame(t, pld);
			} else
				throw new RuntimeException(
						"Error bei Adressvergabe: Payload ist leer! Möglicherweise kein Teilnehmer vorhanden");
			break;
		// Fall: Broadcast Adressprüfung
		case "AddressCheckB":
			if (!pld.isEmpty()) {
				createABFrame(t, pld);
			} else
				throw new RuntimeException(
						"Error bei Adressprüfung: Payload ist leer! Möglicherweise kein Teilnehmer vorhanden");
			break;
		// hier stehen später doch Daten drin -> hcl & payload hinzufügen
		case "AddressCheckU": // Fall: Unicast Adressprüfung
			if (meterAdr.length() > 7) {
				throw new RuntimeException("Error: Meter-Adresse zu lang! Maximal 7 Bit!");
			}

			if (!pld.isEmpty()) // später, wenn Info zum Frame in SML-Daten stehen
			{
				createDataFrame(meterAdr, pld); // pld für später
			} else
				throw new RuntimeException(
						"Error bei Adressprüfung: Payload ist leer! Möglicherweise kein Teilnehmer vorhanden");
			break;
		case "DataRequest": // Fall: Unicast Datenabfrage
			// Zähleradresse darf nicht länger als 7 Bit sein
			if (meterAdr.length() > 7) {
				throw new RuntimeException("Error: Meter-Adresse zu lang! Maximal 7 Bit!");
			}

			if (!pld.isEmpty()) // später, wenn Info zum Frame in SML-Daten stehen
			{
				createDataFrame(meterAdr, pld); // pld für später
			} else
				throw new RuntimeException(
						"Error bei Adressprüfung: Payload ist leer! Möglicherweise kein Teilnehmer vorhanden");
			break;
		case "ObisRequest":
			// Zähleradresse darf nicht länger als 7 Bit sein
			if (meterAdr.length() > 7) {
				throw new RuntimeException("Error: Meter-Adresse zu lang! Maximal 7 Bit!");
			}
			createObisFrame(meterAdr, pld); // pld für später
		default:
			break;
		}

	}

	// Frame ausgeben (Länge 7)
	public String[] getFrameTyp1() {
		return this.frameTyp1;
	}

	// Frame ausgeben (Länge 9)
	public String[] getFrameTyp2() {
		return this.frameTyp2;
	}
	
	public String getPayload() {
		return this.payload;
	}

	public void createA1Frame(int t) {

		frametype = "1010000000001000";
		destAdr = "11111111" + util.FillNull(t, 6) + Integer.toBinaryString(t) + "01";
		srcAdr = "00000001"; // 0x01 = Master
		control = "00010011"; // UI Frame

		frameTyp1[0] = flags;
		frameTyp1[1] = frametype;
		frameTyp1[2] = destAdr;
		frameTyp1[3] = srcAdr;
		frameTyp1[4] = control;
		frameTyp1[5] = Crc16.getCrc16(util.getCrcFrame(frameTyp1, 4), "HCS");
		frameTyp1[6] = flags;
	}

	public void createA2Frame(int t, String pld) {

		frametype = "1010" + util.FillNull(10 + pld.length() / 8, 12) + Integer.toBinaryString(10 + (pld.length()) / 8); // 10*8
																															// Framelänge
		destAdr = "11111111" + util.FillNull(t, 6) + Integer.toBinaryString(t) + "01";
		srcAdr = "00000001"; // 0x01 = Master
		control = "00010011"; // UI Frame
		payload = pld;

		frameTyp2[0] = flags;
		frameTyp2[1] = frametype;
		frameTyp2[2] = destAdr;
		frameTyp2[3] = srcAdr;
		frameTyp2[4] = control;
		frameTyp2[5] = Crc16.getCrc16(util.getCrcFrame(frameTyp2, 4), "HCS");
		frameTyp2[6] = payload;
		frameTyp2[7] = Crc16.getCrc16(util.getCrcFrame(frameTyp2, 6), "CRC");
		frameTyp2[8] = flags;
	}

	public void createABFrame(int t, String pld) {

		frametype = "1010" + util.FillNull(10 + pld.length() / 8, 12) + Integer.toBinaryString(10 + (pld.length()) / 8);
		destAdr = "11111111" + util.FillNull(t, 6) + Integer.toBinaryString(t) + Integer.toBinaryString(0x02);
		srcAdr = "00000001"; // 0x01 = Master
		control = "00010011"; // UI Frame
		payload = pld;

		frameTyp2[0] = flags;
		frameTyp2[1] = frametype;
		frameTyp2[2] = destAdr;
		frameTyp2[3] = srcAdr;
		frameTyp2[4] = control;
		frameTyp2[5] = Crc16.getCrc16(util.getCrcFrame(frameTyp2, 4), "HCS");
		frameTyp2[6] = payload;
		frameTyp2[7] = Crc16.getCrc16(util.getCrcFrame(frameTyp2, 6), "CRC");
		frameTyp2[8] = flags;
	}

	// Unicast Adressprüfung (Fall wird nicht verwendet)
	public void createAUFrame(String mAdr, String pld) {
		frametype = "1010" + util.FillNull(10 + pld.length() / 8, 12) + Integer.toBinaryString(10 + (pld.length()) / 8);
		destAdr = "0" + mAdr + "000000" + Integer.toBinaryString(0x03); // t=timeslots
		srcAdr = "00000001"; // 0x01 = Master
		control = "00010000"; // UI Frame
		payload = pld;

		frameTyp2[0] = flags;
		frameTyp2[1] = frametype;
		frameTyp2[2] = destAdr;
		frameTyp2[3] = srcAdr;
		frameTyp2[4] = control;
		frameTyp2[5] = Crc16.getCrc16(util.getCrcFrame(frameTyp2, 4), "HCS");
		frameTyp2[6] = payload;
		frameTyp2[7] = Crc16.getCrc16(util.getCrcFrame(frameTyp2, 6), "CRC");
		frameTyp2[8] = flags;
	}

	// Unicast Datenabfrage
	public void createDataFrame(String mAdr, String pld) {
		frametype = "1010" + util.FillNull(10 + pld.length() / 8, 12) + Integer.toBinaryString(10 + (pld.length()) / 8);
		destAdr = "0" + mAdr + "000000" + Integer.toBinaryString(0x03); // t=timeslots
		srcAdr = "00000001"; // 0x01 = Master
		control = "00010000"; // UI Frame
		payload = pld;

		frameTyp2[0] = flags;
		frameTyp2[1] = frametype;
		frameTyp2[2] = destAdr;
		frameTyp2[3] = srcAdr;
		frameTyp2[4] = control;
		frameTyp2[5] = Crc16.getCrc16(util.getCrcFrame(frameTyp2, 4), "HCS");
		frameTyp2[6] = payload;
		frameTyp2[7] = Crc16.getCrc16(util.getCrcFrame(frameTyp2, 6), "CRC");
		frameTyp2[8] = flags;
	}

	// Unicast Obis-Zahlen Abfrage
	public void createObisFrame(String mAdr, String pld) {
		frametype = "1010" + util.FillNull(10 + pld.length() / 8, 12) + Integer.toBinaryString(10 + (pld.length()) / 8);
		destAdr = "0" + mAdr + "000000" + Integer.toBinaryString(0x03); // t=timeslots
		srcAdr = "00000001"; // 0x01 = Master
		control = "00010000"; // UI Frame
		payload = pld;

		frameTyp2[0] = flags;
		frameTyp2[1] = frametype;
		frameTyp2[2] = destAdr;
		frameTyp2[3] = srcAdr;
		frameTyp2[4] = control;
		frameTyp2[5] = Crc16.getCrc16(util.getCrcFrame(frameTyp2, 4), "HCS");
		frameTyp2[6] = payload;
		frameTyp2[7] = Crc16.getCrc16(util.getCrcFrame(frameTyp2, 6), "CRC");
		frameTyp2[8] = flags;
	}
	
	// Testausgabe
	public void printFrameTyp1() {
		for (int i = 0; i < 7; i++) {
			// System.out.println(frame[i]); //Ausgabe alle Felder 1 Zeile pro Subframe
			System.out.println(frameTyp1[i]); // Ausgabe alle Felder zeilenweise
		}
		System.out.println("\n");
	}

	// Testausgabe
	public void printFrameTyp2() {
		for (int i = 0; i < 9; i++) {
			// System.out.println(frame[i]); //Ausgabe alle Felder 1 Zeile pro Subframe
			System.out.println(frameTyp2[i]); // Ausgabe alle Felder zeilenweise
		}
		System.out.println("\n");
	}
}
