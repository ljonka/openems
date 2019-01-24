package io.openems.edge.bridge.mbus.api.task;

public class MbusTask {
	protected int _primaryAddress;
	public MbusTask(int primaryAddress) {
		_primaryAddress = primaryAddress;
	}

	public int getPrimaryAddress() {
		return _primaryAddress;
	}
}
