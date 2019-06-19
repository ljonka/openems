package io.openems.edge.bridge.lmnwired;

import java.util.LinkedList;
import java.util.List;

public class SlaveManager {

	private List<Slave> slaveList;
	private int numberOfSlaves;

	public SlaveManager() {
		slaveList = new LinkedList<Slave>();
		numberOfSlaves = 0;
	}

	public void addToList(String payload) {
		numberOfSlaves++;
		Slave slave = new Slave(payload, numberOfSlaves);
		slaveList.add(slave);
		System.out.println("neuer Teilnehmer wurde hinzugefügt");
	}

	public void removeFromList(int i) {
		numberOfSlaves--;
		slaveList.remove(i);

		// Alle anderen Teilnehmer rutschen 1 vor
		for (int j = i; j < numberOfSlaves; j++) {
			Slave newSl = new Slave(getAddress(j), getDeviceID(j), getState(j), j, getObis(j));
			slaveList.set(j, newSl);
		}
		System.out.println("\nTeilnehmer wurde entfernt");
	}

	public int getNumberOfSlaves() {
		return numberOfSlaves;
	}

	public String getAddress(int j) {
		if (slaveList.isEmpty()) {
			return "";
		}
		return slaveList.get(j).getAdr();
	}

	public String getTimesl(int j) {
		if (slaveList.isEmpty()) {
			return "";
		}
		return slaveList.get(j).getTimesl();
	}

	public String getDeviceID(int j) {
		if (slaveList.isEmpty()) {
			return "";
		}
		return slaveList.get(j).getDeviceID();
	}

	public String getState(int j) {
		if (slaveList.isEmpty()) {
			return "";
		}
		return slaveList.get(j).getState();
	}

	public List<Slave> getSlaveList() {
		return slaveList;
	}
	
	public String getObis(int j) {
		if (slaveList.isEmpty()) {
			return "";
		}
		return slaveList.get(j).getObis();
	}

	public int addressExisting(String adr) {
		for (int j = 0; j < numberOfSlaves; j++) {
			Slave newSl = new Slave(getAddress(j), getDeviceID(j), getState(j), j, getObis(j));
			if (newSl.getAdr().equals(adr)) {
				return (j + 1);
			}
		}
		return 0;
	}
	
	public boolean rightTimeslot(int tsl) {
		for (int j = 0; j < numberOfSlaves; j++) {
			Slave newSl = new Slave(getAddress(j), getDeviceID(j), getState(j), j, getObis(j));
			//Existiert Timeslot in Teilnehmerliste und stimmt Timeslot mit Stelle in Liste überein?
			if ((Integer.parseInt((newSl.getTimesl()),2)==tsl)&&(tsl==(j+1))) {
				return true;
			}
		}
		return false;
	}
	
	public void addObis(int i,String s) {
		//Füge Obis-Kennzahlen zu best. Teilnehmer hinzu
		for (int j = 0; j < numberOfSlaves; j++) {
			
			if (i == (j+1)) {
				Slave newSl = new Slave(getAddress(j), getDeviceID(j), getState(j), j, s);
				slaveList.set(i, newSl);
				System.out.println("Obis-Kennzahlen von Teilnehmer "+i+" wurden hinzugefügt : ");
				System.out.println("Adresse: "+slaveList.get(i).getAdr());
				System.out.println(slaveList.get(i).getDeviceID());
				System.out.println(slaveList.get(i).getTimesl());
				System.out.println(slaveList.get(i).getState());
				System.out.println(slaveList.get(i).getObis());
			}
		}
	}
}
