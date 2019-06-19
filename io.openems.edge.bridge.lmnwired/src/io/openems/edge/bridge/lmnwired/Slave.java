package io.openems.edge.bridge.lmnwired;

public class Slave 
{
	private String address;
	private String timeslot;
	private String deviceID;
	private String state;			//Zustandssignalisierung, in diesem Projekt nicht gebraucht
	private String obis;

	public Slave(String payload,int numberOfSlaves)
	{
		address = util.getAddressMask(payload);			//Adresse wird aus Daten rauslesen
		timeslot = util.FillNull(Integer.parseInt(util.setTimeslot(numberOfSlaves),2),8) + util.setTimeslot(numberOfSlaves);//neue Timeslots von 0x01 aufsteigend reinscheiben, dann aber in util.getListeMitTeilnehmern auch ändern 
		deviceID = util.getDeviceIdMask(payload);
		state = "0000000000000000";				//ist in diesem Projekt fest fest
		obis = new String();
	}

	public Slave(String adr, String id, String st, int j, String s) 
	{
		address = adr;
		timeslot = (util.FillNull(j+1, 8) + Integer.toBinaryString(j+1));
		deviceID = id;
		state = st;
		obis = s;
	}
	
	public String getAdr()
	{
		return address;
	}
	public String getDeviceID()
	{
		return deviceID;
	}
	public String getTimesl()
	{
		return timeslot;
	}
	public String getState()
	{
		return state;
	}
	public String getObis() {
		return obis;
	}
}
