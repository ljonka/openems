package io.openems.edge.bridge.lmnwired;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class TimerStart implements TimerStartEvent {

	private List<BridgeLMNWiredImpl> listeners = new ArrayList<BridgeLMNWiredImpl>();

	public void addListener(BridgeLMNWiredImpl frameCheckEvent, BridgeLMNWiredImpl mainEvent) {
		listeners.add(frameCheckEvent);
		listeners.add(mainEvent);
	}

	public void startTimer(String messageType, SlaveManager s, BridgeLMNWiredImpl m) {
		int nrOfSlavesBeforeBroadcast = s.getNumberOfSlaves();
		boolean[] aCheck = new boolean[nrOfSlavesBeforeBroadcast];
		CheckFrame f = new CheckFrame(aCheck, nrOfSlavesBeforeBroadcast, s);
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {

			int n = 0;
			public void run() {
				n++;
				
				if ((n-1) == m.getTimeslotLength()*2) {
					System.out.println("Zeit abgelaufen");
					TimerStart s = new TimerStart();
					timer.cancel();
				}
			}
		}, 0, m.getTimeslots());
	}

}
