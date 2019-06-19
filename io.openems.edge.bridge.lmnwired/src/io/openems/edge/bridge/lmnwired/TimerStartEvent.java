package io.openems.edge.bridge.lmnwired;

public interface TimerStartEvent {
	void startTimer(String messageType, SlaveManager s, BridgeLMNWiredImpl m);
}
