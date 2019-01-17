package io.openems.edge.bridge.mbus;

import io.openems.edge.bridge.mbus.api.task.MbusTask;

public interface IBridgeMbusSerial {
	public void addTask(String sourceId, MbusTask task);

	public void removeTask(String sourceId);
}
