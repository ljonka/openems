package io.openems.edge.meter.eastron.sdm220mbus;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

import io.openems.edge.bridge.mbus.BridgeMbusSerialImpl;
import io.openems.edge.common.channel.doc.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.api.MeterType;
import io.openems.edge.meter.api.SymmetricMeter;

import org.osgi.service.metatype.annotations.Designate;


@Designate( ocd=Config.class, factory=true)
@Component( 
	name = "Meter.Eastron.SDM220MBus", 
	immediate = true, 
	configurationPolicy = ConfigurationPolicy.REQUIRE) 
public class MeterSDM220MBus extends BridgeMbusSerialImpl implements SymmetricMeter, OpenemsComponent{
	
	private MeterType meterType = MeterType.PRODUCTION;
	
	@Reference
	protected ConfigurationAdmin cm; 
	
	public MeterSDM220MBus() {
		Utils.initializeChannels(this).forEach(channel -> this.addChannel(channel));
	}

	@Activate
	void activate(ComponentContext context, Config config) { 
		this.meterType = config.type();

		super.activate(context, config.service_pid(), config.id(), config.enabled());
	}

	@Deactivate
	protected void deactivate() { 
		super.deactivate();
	}
	
	public enum ChannelId implements io.openems.edge.common.channel.doc.ChannelId { 
		;
		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		public Doc doc() {
			return this.doc;
		}
	}

	@Override
	public MeterType getMeterType() {
		return this.meterType;
	}
	
	
	@Override
	public String debugLog() { 
		return "L:" + this.getActivePower().value().asString();
	}

}
