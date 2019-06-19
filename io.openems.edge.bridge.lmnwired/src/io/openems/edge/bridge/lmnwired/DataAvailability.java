package io.openems.edge.bridge.lmnwired;

public enum DataAvailability {

	INITIAL, TIME_SET, TIME_SET_FETCHING_DATA, CORRUPT_DATA, DATA_AVAILABLE;

	@Override
	public String toString() {
		return this.name();
	}

}
