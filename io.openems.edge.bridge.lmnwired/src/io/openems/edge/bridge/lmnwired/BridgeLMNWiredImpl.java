package io.openems.edge.bridge.lmnwired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazecast.jSerialComm.SerialPort;

import io.openems.common.worker.AbstractCycleWorker;
import io.openems.edge.bridge.lmnwired.api.BridgeLMNWired;
import io.openems.edge.bridge.lmnwired.api.task.LMNWiredTask;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;

import org.osgi.service.metatype.annotations.Designate;

@Designate(ocd = Config.class, factory = true)
@Component(name = "io.openems.edge.bridge.lmnwired", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = { //
				EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE, //
				EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE //
		})

public class BridgeLMNWiredImpl extends AbstractOpenemsComponent
		implements BridgeLMNWired, OpenemsComponent, EventHandler {

	ScheduledExecutorService service;

	SerialPort comPort;
	private static SlaveManager sm = new SlaveManager(); // neuer Slavemanager: 0 Teilnehmer & leere Teilnehmerliste
	private static PayloadManager pm = new PayloadManager(); // neuer Payloadmanager

	private final Logger log = LoggerFactory.getLogger(BridgeLMNWiredImpl.class);

	private final LMNWiredWorker worker = new LMNWiredWorker();
	private final Map<String, LMNWiredTask> tasks = new HashMap<>();

	private static List<TimerStartEvent> listeners1 = new ArrayList<TimerStartEvent>();

	protected int registeredDevices = 0;

	private static String messageType;
	private static BridgeLMNWiredImpl bridgeLMNWiredImpl = new BridgeLMNWiredImpl();

	private static int timeslots = 32;
	private static int timeslotLength = 10; // Später zu 5 ändern

	private static long time = 0;

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		;

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	public BridgeLMNWiredImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				BridgeLMNWired.ChannelId.values(), //
				ChannelId.values() //
		);
	}

	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());

		this.worker.activate(config.id());

		comPort = SerialPort.getCommPort(config.portName());
		if (comPort == null) {
			log.debug("Serial Port not found.");
			return;
		}
		comPort.setNumDataBits(8);
		comPort.setNumStopBits(1);
		comPort.setParity(0);
		comPort.setBaudRate(config.baudRate());
		comPort.openPort();

		new PortManager(comPort, sm, this);

		// Sende Broadcast Adressvergabe
		Runnable runnableInviteNewDevices = new Runnable() {

			public void run() {

				log.info("Sende Broadcast Adressvergabe");

				// Noch kein Teilnehmer vorhanden
				if (registeredDevices <= 0) {
					BroadcastAddressRequest1();
				} else { // Mindestens 1 Teilnehmer vorhanden
					BroadcastAddressRequest2();
				}

			}

		};

		// Sende Broadcast Adressprüfung
		Runnable runnableCheckDevicePresence = new Runnable() {

			public void run() {

				log.info("Sende Broadcast Adressprüfung");

				if (registeredDevices > 0) {
					BroadcastAddressCheck();
				}

			}

		};

		service = Executors.newSingleThreadScheduledExecutor();

		service.scheduleAtFixedRate(runnableInviteNewDevices, 15, 30, TimeUnit.SECONDS);
		service.scheduleAtFixedRate(runnableCheckDevicePresence, 0, 30, TimeUnit.SECONDS);
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
		comPort.closePort();
		worker.deactivate();
		service.shutdown();
	}

	@Override
	public void handleEvent(Event event) {
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE:
			this.worker.triggerNextRun();
			break;
		}
	}

	@Override
	public void addTask(String sourceId, LMNWiredTask task) {
		this.tasks.put(sourceId, task);
	}

	@Override
	public void removeTask(String sourceId) {
		this.tasks.remove(sourceId);
	}

	@Override
	public SerialPort getSerialConnection() {
		return comPort;
	}

	private class LMNWiredWorker extends AbstractCycleWorker {

		@Override
		public void activate(String name) {
			super.activate(name);
		}

		@Override
		public void deactivate() {
			super.deactivate();
		}

		@Override
		protected void forever() {
			if (!comPort.isOpen()) {
				comPort.openPort();
			}

			for (LMNWiredTask task : tasks.values()) {
				Object data = null;
				data = task.getRequest();
				task.setResponse(data);
			}

			comPort.closePort();
		}
	}

	public String getMessageType() {
		return messageType;
	}

	public int getTimeslots() {
		return timeslots;
	}

	public long getTime() {
		return time;
	}

	public void Event1_Timer() {
		time = System.currentTimeMillis();

		for (TimerStartEvent e : listeners1) {
			e.startTimer(messageType, sm, this);
			break;
		}
	}

	public int getTimeslotLength() {
		return timeslotLength;
	}

	// Broadcast zur Adressvergabe, noch kein Teilnehmer vorhanden
	public static void BroadcastAddressRequest1() {
		System.out.println("\n---0s: Broadcast Adressvergabe (0 Teilnehmer):");
		messageType = "request";
		Frame frame1 = new Frame("AddressRequest1", timeslots, "", "");
		PortManager.setPortData(frame1.getFrameTyp1());
		bridgeLMNWiredImpl.Event1_Timer();
	}

	// Broadcast zur Adressvergabe, mindestens 1 Teilnehmer vorhanden
	public static void BroadcastAddressRequest2() {
		System.out.println("\n---30s: Broadcast Adressvergabe (mehr als 0 Teilnehmer):");
		Frame frame1 = new Frame("AddressRequest2", timeslots, "", (pm.getPayloadBroadcast(sm)).toString());
		messageType = "request";
		PortManager.setPortData(frame1.getFrameTyp2());
		bridgeLMNWiredImpl.Event1_Timer();
	}
	
	public static void BroadcastAddressCheck() {
		Frame frame1 = new Frame("AddressCheckB", timeslots, "", (pm.getPayloadBroadcast(sm)).toString());
		messageType = "check";
		PortManager.setPortData(frame1.getFrameTyp2());
		bridgeLMNWiredImpl.Event1_Timer();
	}

}
