package io.openems.edge.bridge.mbus;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.openmuc.jmbus.MBusConnection;
import org.openmuc.jmbus.MBusConnection.MBusSerialBuilder;
import org.openmuc.jmbus.VariableDataStructure;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.Designate;


import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.worker.AbstractCycleWorker;
import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.bridge.mbus.api.task.MbusTask;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.channel.StateCollectorChannel;

/**
 * Provides a service for connecting to, querying and writing to a M-Bus device
 */
@Designate(ocd = ConfigSerial.class, factory = true)
@Component(name = "Bridge.Mbus.Serial", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE)
public class BridgeMbusSerialImpl extends AbstractOpenemsComponent
		implements IBridgeMbusSerial, OpenemsComponent, EventHandler {

	@Reference
	protected ConfigurationAdmin cm; 
	private String portName;
	private int baudrate;
	private final MbusWorker worker = new MbusWorker();
	private final Map<String, MbusTask> tasks = new HashMap<>();
	
	public BridgeMbusSerialImpl() {
		Stream.of( //
				Arrays.stream(OpenemsComponent.ChannelId.values()).map(channelId -> {
					switch (channelId) {
					case STATE:
						return new StateCollectorChannel(this, channelId);
					}
					return null;
				})).flatMap(channel -> channel).forEach(channel -> this.addChannel(channel));
	}

	@Override
	public void handleEvent(Event event) {
		// TODO Auto-generated method stub

	}

	public String getPortName() {
		return this.portName;
	}

	public int getBaudrate() {
		return this.baudrate;
	}

	private MBusSerialBuilder _builder = null;

	@SuppressWarnings("unused")
	private synchronized MBusSerialBuilder getMbusSerialBuilder() throws OpenemsException {
		if (this._builder == null) {
			/*
			 * create new connection
			 */

			MBusSerialBuilder builder = MBusConnection.newSerialBuilder("/dev/ttyS0").setBaudrate(2400);
			this._builder = builder;
		}
		return this._builder;
	}

	@Override
	public void addTask(String sourceId, MbusTask task) {
		this.tasks.put(sourceId, task);
	}

	@Override
	public void removeTask(String sourceId) {
		this.tasks.remove(sourceId);
	}

	@Deactivate
	protected void deactivate() {
		if (this.isEnabled()) {
			this.worker.deactivate();
		}
		super.deactivate();
	}

	@Activate
	protected void activate(ComponentContext context, ConfigSerial config) {
		super.activate(context, config.service_pid(), config.id(), config.enabled());
		this.portName = config.portName();
		this.baudrate = config.baudRate();
		if (this.isEnabled()) {
			this.worker.initial(config);
		}
	}

	private class MbusWorker extends AbstractCycleWorker {

		private MBusSerialBuilder _builder;

		public void initial(ConfigSerial config) {
			this._builder = MBusConnection.newSerialBuilder(config.portName()).setBaudrate(config.baudRate());
		}

		@Override
		protected void forever() {
			try (MBusConnection mBusConnection = _builder.build()) {
				for (MbusTask task : tasks.values()) {
					int primaryAddress = task.getPrimaryAddress();
					VariableDataStructure data = mBusConnection.read(primaryAddress);
					System.out.println(data.toString());
				}

				mBusConnection.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

}
