package io.openems.edge.meter.consolinno.d0;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import io.openems.edge.bridge.lmnwired.api.AbstractOpenEmsLMNWiredComponent;
import io.openems.edge.bridge.lmnwired.api.BridgeLMNWired;
import io.openems.edge.bridge.lmnwired.api.task.LMNWiredTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.api.AsymmetricMeter;
import io.openems.edge.meter.api.MeterType;
import io.openems.edge.meter.api.SymmetricMeter;

import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.service.component.annotations.ConfigurationPolicy;

@Designate(ocd = Config.class, factory = true)
@Component(name = "io.openems.edge.meter.consolinno.d0", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE)

public class MeterConsolinnoD0 extends AbstractOpenEmsLMNWiredComponent
		implements SymmetricMeter, AsymmetricMeter, OpenemsComponent {

	@Reference
	protected ConfigurationAdmin cm;

	private MeterType meterType = MeterType.GRID;

	private final Logger log = LoggerFactory.getLogger(MeterConsolinnoD0.class);

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected BridgeLMNWired bridgeLMNWired;
	
	private String taskIDObis180;
	private String taskIDObis280;
	
	Config config;
	
	public MeterConsolinnoD0() {
		super(OpenemsComponent.ChannelId.values(), //
				SymmetricMeter.ChannelId.values(), //
				AsymmetricMeter.ChannelId.values());
	}

	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled(), this.cm, config.service_pid());
		
		this.config = config;

		// update filter for 'lmnwired'
		if (OpenemsComponent.updateReferenceFilter(cm, config.service_pid(), "lmnwired", config.lmnwired_id())) {
			return;
		}

		log.info("Add Tasks for Meter: " + config.serialNumber());

		// Add one read task per obis
		taskIDObis180 = config.id() + "_180";
		taskIDObis280 = config.id() + "_280";
		
		if (config.use180())
			this.bridgeLMNWired.addTask(taskIDObis180, new LMNWiredTask(this, bridgeLMNWired, config.serialNumber(),
					"1.8.0", SymmetricMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY));
		if (config.use280())
			this.bridgeLMNWired.addTask(taskIDObis280, new LMNWiredTask(this, bridgeLMNWired, config.serialNumber(),
					"2.8.0", SymmetricMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY));
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
		if (config.use180())
			this.bridgeLMNWired.removeTask(taskIDObis180);
		if (config.use280())
			this.bridgeLMNWired.removeTask(taskIDObis280);
	}

	@Override
	public MeterType getMeterType() {
		return meterType;
	}

}
