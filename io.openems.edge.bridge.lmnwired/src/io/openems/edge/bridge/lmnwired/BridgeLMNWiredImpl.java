package io.openems.edge.bridge.lmnwired;

import java.util.HashMap;
import java.util.Map;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import io.openems.common.worker.AbstractCycleWorker;
import io.openems.edge.bridge.lmnwired.api.BridgeLMNWired;
import io.openems.edge.bridge.lmnwired.api.task.LMNWiredTask;
import io.openems.edge.bridge.lmnwired.hdlc.Addressing;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;

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

	SerialPort serialPort;

	private final Logger log = LoggerFactory.getLogger(BridgeLMNWiredImpl.class);

	private final LMNWiredWorker worker = new LMNWiredWorker();
	private final Map<String, LMNWiredTask> tasks = new HashMap<>();
	
	private Addressing addressing;

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

		serialPort = SerialPort.getCommPort(config.portName());

		serialPort.setNumDataBits(8);
		serialPort.setNumStopBits(1);
		serialPort.setParity(0);
		serialPort.setBaudRate(config.baudRate());
		serialPort.openPort();

		activateSerialDataListener(serialPort);
		
		addressing = new Addressing(serialPort);

	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
		addressing.shutdown();
		serialPort.removeDataListener();
		serialPort.closePort();
		worker.deactivate();
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

			for (LMNWiredTask task : tasks.values()) {
				Object data = null;
				data = task.getRequest();
				task.setResponse(data);
			}

		}
	}

	/**
	 * Activate data listener for incoming packages
	 * 
	 * @param serialPort
	 */
	private void activateSerialDataListener(SerialPort serialPort) {
		serialPort.addDataListener(new SerialPortDataListener() {
			@Override
			public int getListeningEvents() {
				return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
			}

			@Override
			public void serialEvent(SerialPortEvent event) {
				byte[] newData = event.getReceivedData();
				System.out.println("Received data of size: " + newData.length);
				for (int i = 0; i < newData.length; ++i)
					System.out.print((char) newData[i]);
				System.out.println("\n");
			}
		});
	}

	@Override
	public SerialPort getSerialConnection() {
		return serialPort;
	}

}
