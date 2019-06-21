package io.openems.backend.metadata.dummy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.backend.common.component.AbstractOpenemsBackendComponent;
import io.openems.backend.metadata.api.BackendUser;
import io.openems.backend.metadata.api.Edge;
import io.openems.backend.metadata.api.Edge.State;
import io.openems.backend.metadata.api.Metadata;
import io.openems.common.types.EdgeConfig;
import io.openems.common.types.EdgeConfigDiff;
import io.openems.common.utils.StringUtils;

@Designate(ocd = Config.class, factory = false)
@Component(name = "Metadata.Dummy", configurationPolicy = ConfigurationPolicy.REQUIRE)
public class Dummy extends AbstractOpenemsBackendComponent implements Metadata {

	private static final Pattern NAME_NUMBER_PATTERN = Pattern.compile("[^0-9]+([0-9]+)$");

	private final Logger log = LoggerFactory.getLogger(Dummy.class);

	private final AtomicInteger nextUserId = new AtomicInteger(-1);
	private final AtomicInteger nextEdgeId = new AtomicInteger(-1);

	private final Map<String, BackendUser> users = new HashMap<>();
	private final Map<String, Edge> edges = new HashMap<>();

	public Dummy() {
		super("Metadata.Dummy");
	}

	@Activate
	void activate() {
		this.logInfo(this.log, "Activate");
	}

	@Deactivate
	void deactivate() {
		this.logInfo(this.log, "Deactivate");
	}

	@Override
	public Optional<String> getEdgeIdForApikey(String apikey) {
		Optional<Edge> edgeOpt = this.edges.values().stream() //
				.filter(edge -> apikey.equals(edge.getApikey())) //
				.findFirst();
		if (edgeOpt.isPresent()) {
			return Optional.ofNullable(edgeOpt.get().getId());
		}
		// not found. Is apikey a valid Edge-ID?
		Optional<Integer> idOpt = Dummy.parseNumberFromName(apikey);
		int id;
		String edgeId;
		if (idOpt.isPresent()) {
			edgeId = apikey;
			id = idOpt.get();
		} else {
			// create new ID
			id = this.nextEdgeId.incrementAndGet();
			edgeId = "edge" + id;
		}
		Edge edge = new Edge(edgeId, apikey, "OpenEMS Edge #" + id, State.ACTIVE, "", "", new EdgeConfig(), null, null,
				null);
		edge.onSetConfig(config -> {
			this.logInfo(this.log, "Edge [" + edgeId + "]. Update config: "
					+ StringUtils.toShortString(EdgeConfigDiff.diff(config, edge.getConfig()).getAsHtml(), 100));
		});
		edge.onSetSoc(soc -> {
			this.logInfo(this.log, "Edge [" + edgeId + "]. Set SoC: " + soc);
		});
		edge.onSetIpv4(ipv4 -> {
			this.logInfo(this.log, "Edge [" + edgeId + "]. Set IPv4: " + ipv4);
		});
		edge.onSetSumState((sumState, activeStateChannels) -> {
			String sumStateString;
			if (sumState != null) {
				sumStateString = sumState.getName().toLowerCase();
			} else {
				sumStateString = "";
			}
			String states = Metadata.activeStateChannelsToString(activeStateChannels);
			this.logInfo(this.log,
					"Edge [" + edgeId + "]. Set State \"" + sumStateString + "\". Long-Text: " + states);
		});
		this.edges.put(edgeId, edge);
		return Optional.ofNullable(edgeId);

	}

	@Override
	public Optional<Edge> getEdge(String edgeId) {
		Edge edge = this.edges.get(edgeId);
		return Optional.ofNullable(edge);
	}

	public static Optional<Integer> parseNumberFromName(String name) {
		try {
			Matcher matcher = NAME_NUMBER_PATTERN.matcher(name);
			if (matcher.find()) {
				String nameNumberString = matcher.group(1);
				return Optional.ofNullable(Integer.parseInt(nameNumberString));
			}
		} catch (NullPointerException e) {
			/* ignore */
		}
		return Optional.empty();
	}
}
