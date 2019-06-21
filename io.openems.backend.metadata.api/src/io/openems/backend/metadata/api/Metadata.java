package io.openems.backend.metadata.api;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import io.openems.common.access_control.RoleId;
import org.osgi.annotation.versioning.ProviderType;

import com.google.common.collect.HashMultimap;

import io.openems.common.channel.Level;
import io.openems.common.exceptions.OpenemsError;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.ChannelAddress;
import io.openems.common.types.EdgeConfig;
import io.openems.common.types.EdgeConfig.Component.Channel;
import io.openems.common.types.EdgeConfig.Component.Channel.ChannelDetail;
import io.openems.common.types.EdgeConfig.Component.Channel.ChannelDetailState;

@ProviderType
public interface Metadata {

    /**
     * Gets the Edge-ID for an API-Key, i.e. authenticates the API-Key.
     *
     * @param apikey the API-Key
     * @return the Edge-ID or Empty
     */
    Optional<String> getEdgeIdForApikey(String apikey);

    /**
     * Get an Edge by its unique Edge-ID.
     *
     * @param edgeId the Edge-ID
     * @return the Edge as Optional
     */
    Optional<Edge> getEdge(String edgeId);

    /**
     * Get an Edge by its unique Edge-ID. Throws an Exception if there is no Edge
     * with this ID.
     *
     * @param edgeId the Edge-ID
     * @return the Edge
     * @throws OpenemsException on error
     */
    default Edge getEdgeOrError(String edgeId) throws OpenemsException {
        Optional<Edge> edgeOpt = this.getEdge(edgeId);
        if (edgeOpt.isPresent()) {
            return edgeOpt.get();
        } else {
            throw new OpenemsException("Unable to get Edge for id [" + edgeId + "]");
        }
    }

	/**
	 * Helper method for creating a String of all active State-Channels by Level.
	 * 
	 * @param activeStateChannels Map of ChannelAddress and
	 *                            EdgeConfig.Component.Channel; as returned by
	 *                            Edge.onSetSumState()
	 * @return a string
	 */
	public static String activeStateChannelsToString(
			Map<ChannelAddress, EdgeConfig.Component.Channel> activeStateChannels) {
		// Sort active State-Channels by Level and Component-ID
		HashMap<Level, HashMultimap<String, Channel>> states = new HashMap<>();
		for (Entry<ChannelAddress, Channel> entry : activeStateChannels.entrySet()) {
			ChannelDetail detail = entry.getValue().getDetail();
			if (detail instanceof ChannelDetailState) {
				Level level = ((ChannelDetailState) detail).getLevel();
				HashMultimap<String, Channel> channelsByComponent = states.get(level);
				if (channelsByComponent == null) {
					channelsByComponent = HashMultimap.create();
					states.put(level, channelsByComponent);
				}
				channelsByComponent.put(//
						entry.getKey().getComponentId(), //
						entry.getValue());
			}
		}
		StringBuilder result = new StringBuilder();
		for (Level level : Level.values()) {
			HashMultimap<String, Channel> channelsByComponent = states.get(level);
			if (channelsByComponent != null) {
				if (result.length() > 0) {
					result.append("| ");
				}
				result.append(level.name() + ": ");
				StringBuilder subResult = new StringBuilder();
				for (Entry<String, Collection<Channel>> entry : channelsByComponent.asMap().entrySet()) {
					if (subResult.length() > 0) {
						subResult.append("; ");
					}
					subResult.append(entry.getKey() + ": ");
					subResult.append(entry.getValue().stream() //
							.map(channel -> {
								if (!channel.getText().isEmpty()) {
									return channel.getText();
								} else {
									return channel.getId();
								}
							}) //
							.collect(Collectors.joining(", ")));
				}
				result.append(subResult);
			}
		}
		return result.toString();
	}
}
