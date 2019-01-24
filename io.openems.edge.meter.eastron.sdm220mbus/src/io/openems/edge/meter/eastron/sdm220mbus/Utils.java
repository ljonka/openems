package io.openems.edge.meter.eastron.sdm220mbus;

import java.util.Arrays;
import java.util.stream.Stream;

import io.openems.edge.common.channel.AbstractReadChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.StateCollectorChannel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.api.SymmetricMeter;

public class Utils {
	public static Stream<? extends AbstractReadChannel<?>> initializeChannels(MeterSDM220MBus c) { 
		return Stream.of( //
				Arrays.stream(OpenemsComponent.ChannelId.values()).map(channelId -> { 
					switch (channelId) { 
					case STATE:
						return new StateCollectorChannel(c, channelId); 
					}
					return null;
				}), Arrays.stream(SymmetricMeter.ChannelId.values()).map(channelId -> { 
					switch (channelId) { 
					case ACTIVE_POWER:
					case ACTIVE_CONSUMPTION_ENERGY:
					case ACTIVE_PRODUCTION_ENERGY:
					case CURRENT:
					case FREQUENCY:
					case MAX_ACTIVE_POWER:
					case MIN_ACTIVE_POWER:
					case REACTIVE_POWER:
					case VOLTAGE:
						return new IntegerReadChannel(c, channelId); 
					}
					return null;
				})).flatMap(channel -> channel);
	}
}
