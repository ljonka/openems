package io.openems.edge.bridge.lmnwired.api;

import io.openems.edge.common.channel.Channel;

public class ChannelRecord {
	public Channel<?> channel;
	public int dataRecordPosition;

	public enum DataType {
		Positive_active_energy_total, Positive_active_energy_in_tariff_T1, Negative_active_energy_total,
		Negative_active_energy_in_tariff_T1, Absolute_active_energy_total, Sum_active_energy_without_reverse_blockade_total
	}

	public DataType dataType;

	/**
	 * In this case you will request secondary address values. eg. manufacturer,
	 * device id or meter type
	 * 
	 * @param channel
	 * @param dataType
	 */
	public ChannelRecord(Channel<?> channel, DataType dataType) {
		this.channel = channel;
		this.dataType = dataType;
	}

	/**
	 * In this case you will request usage data
	 * 
	 * @param channel
	 * @param dataRecordPosition
	 */
	public ChannelRecord(Channel<?> channel, int dataRecordPosition) {
		this.channel = channel;
		this.dataRecordPosition = dataRecordPosition;
	}

	public Channel<?> getChannel() {
		return channel;
	}

	public void setChannelId(Channel<?> channel) {
		this.channel = channel;
	}

	public int getdataRecordPosition() {
		return dataRecordPosition;
	}

	public void setdataRecordPosition(int dataRecordPosition) {
		this.dataRecordPosition = dataRecordPosition;
	}

	public DataType getDataType() {
		return this.dataType;
	}
}
