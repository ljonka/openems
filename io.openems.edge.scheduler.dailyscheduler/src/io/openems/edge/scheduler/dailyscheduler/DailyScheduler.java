package io.openems.edge.scheduler.dailyscheduler;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.scheduler.api.AbstractScheduler;
import io.openems.edge.scheduler.api.Scheduler;

@Designate(ocd = Config.class, factory = true)
@Component(name = "Scheduler.DailyScheduler", immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class DailyScheduler extends AbstractScheduler implements Scheduler, OpenemsComponent {

	private final Logger log = LoggerFactory.getLogger(DailyScheduler.class);

	private final List<Controller> alwaysRunControllers = new ArrayList<>();

	private final TreeMap<LocalTime, List<Controller>> contollersSchedule = new TreeMap<>();

	private Map<String, Controller> _controllers = new ConcurrentHashMap<>();

	private Config config = null;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MULTIPLE, target = "(enabled=true)")
	protected synchronized void addController(Controller controller) throws OpenemsNamedException {
		if (controller != null && controller.id() != null) {
			this._controllers.put(controller.id(), controller);
		}
		this.updateControllers();
	}

	protected synchronized void removeController(Controller controller) throws OpenemsNamedException {
		if (controller != null && controller.id() != null) {
			this._controllers.remove(controller.id(), controller);
		}
		this.updateControllers();
	}

	public enum ThisChannelId implements io.openems.edge.common.channel.ChannelId {
		;
		private final Doc doc;

		private ThisChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	public DailyScheduler() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Scheduler.ChannelId.values(), //
				ThisChannelId.values() //
		);
	}

	@Activate
	void activate(ComponentContext context, Config config) throws OpenemsNamedException {
		super.activate(context, config.id(), config.alias(), config.enabled(), config.cycleTime());
		this.config = config;
		this.updateControllers();
	}

	/**
	 * Adds Controllers using the order of alwaysRunControllers_ids and
	 * controllerTimes Config property
	 */
	private synchronized void updateControllers() throws OpenemsNamedException {
		// Controllers that are activated always.
		this.alwaysRunControllers.clear();
		for (String id : this.config.alwaysRunControllers_ids()) {
			Controller alwaysRunontroller = this._controllers.get(id);
			if (alwaysRunontroller == null) {
				this.logWarn(this.log, "Required Controller [" + id + "] is not available.");
			} else {
				this.alwaysRunControllers.add(alwaysRunontroller);
			}
		}

		// Controllers that are activated for a given time based on the Json input.
		if (this.config.controllerTimes() == null || this.config.controllerTimes().isEmpty()) {
			return;
		}

		this.contollersSchedule.clear();
		JsonArray controllerTimes = JsonUtils.getAsJsonArray(JsonUtils.parse(this.config.controllerTimes()));
		if (controllerTimes != null) {
			for (JsonElement controllerPeriod : controllerTimes) {
				LocalTime time = LocalTime.parse(JsonUtils.getAsString(controllerPeriod, "time"));
				JsonArray controllerIds = JsonUtils.getAsJsonArray(controllerPeriod, "controllers");

				List<Controller> controllers = new ArrayList<>();
				for (JsonElement controllerId : controllerIds) {
					Controller controller = this._controllers.get(JsonUtils.getAsString(controllerId));
					if (controller != null) {
						controllers.add(controller);
					}
				}
				this.contollersSchedule.put(time, controllers);
			}
		}

	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	/**
	 * returns the Controllers from both lists of controllers based on given time in
	 * a day.
	 */

	@Override
	public List<Controller> getControllers() {
		List<Controller> result = new ArrayList<>();
		LocalTime currentTime = LocalTime.now();

		// returns all the controllers that are activated at given time.
		if (!(this.contollersSchedule.isEmpty())) {
			Entry<LocalTime, List<Controller>> lowerEntry = this.contollersSchedule.lowerEntry(currentTime);
			if (lowerEntry != null) {
				result.addAll(lowerEntry.getValue());
			}
		}

		result.addAll(this.alwaysRunControllers);
		return result;
	}
}
