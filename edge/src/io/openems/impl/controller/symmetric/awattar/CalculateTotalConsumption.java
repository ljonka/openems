package io.openems.impl.controller.symmetric.awattar;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import io.openems.api.exception.InvalidValueException;

public class CalculateTotalConsumption {

	private final Logger log = LoggerFactory.getLogger(AwattarController.class);
	private final AwattarController awattarController;
	private LocalDate dateOfLastRun = null;

	private long totalConsumption = 0;
	private int totalConsumptionCounter = 0;
	private long requiredConsumption;
	private Integer t0 = null; // last time of the day when production > consumption
	private Integer t1 = null; // first time of the day when production > consumption
	private Integer t = 0;

	private State currentState = State.PRODUCTION_LOWER_THAN_CONSUMPTION;

	private enum State {

		PRODUCTION_LOWER_THAN_CONSUMPTION, PRODUCTION_DROPPED_BELOW_CONSUMPTION, PRODUCTION_HIGHER_THAN_CONSUMPTION, PRODUCTION_EXCEEDED_CONSUMPTION
	}

	public CalculateTotalConsumption(AwattarController awattarController) {
		this.awattarController = awattarController;
	}

	protected long getTotalConsumption() {
		// ...
		return this.requiredConsumption;
	}

	protected void run() {
		// ...
		try {
			/*
			 * Detect switch to next day
			 */
			LocalDate nowDate = LocalDate.now();
			if (dateOfLastRun == null || dateOfLastRun.isBefore(nowDate)) {
				// initialize
				this.t1 = null;
			}

			LocalDateTime now = LocalDateTime.now();
			int secondOfDay = now.getSecond() + now.getMinute() * 60 + now.getHour() * 3600;

			Ess ess = this.awattarController.ess.value();

			long pvproduction = this.awattarController.pvmeter.value().activePower.value();
			long essproduction = ess.allowedDischarge.value();
			long essconsumption = ess.allowedCharge.value();
			long gridproduction = 0;
			long gridconsumption = 0;

			if (this.awattarController.gridMeter.value().activePower.value() > 0) {
				gridproduction = this.awattarController.gridMeter.value().activePower.value();
			} else {
				gridconsumption = this.awattarController.gridMeter.value().activePower.value() * -1;
			}

			long loadvalue = gridproduction + pvproduction + essproduction - essconsumption - gridconsumption;
			long consumption = essconsumption + gridconsumption + loadvalue;
			long production = pvproduction + essproduction + gridproduction;

			log.info("total Consumption: " + consumption + " Total  production: " + production);

			switch (currentState) {
			case PRODUCTION_LOWER_THAN_CONSUMPTION:
				if (production > consumption) {
					log.info(production + "is greater than" + consumption + "so switching the state from PRODUCTION LOWER THAN CONSUMPTION to PRODUCTION EXCEEDING CONSUMPTION");
					this.currentState = State.PRODUCTION_EXCEEDED_CONSUMPTION;
				} else {
					this.totalConsumption += consumption;
					this.totalConsumptionCounter++;
				}
				break;

			case PRODUCTION_EXCEEDED_CONSUMPTION:
				if (this.t1 == null) {
					// this is the first time of the day that production > consumption
					this.t1 = secondOfDay;
					// calculate the required kWh
					System.out.println("Calculate kWh from [" + this.t0 + "] till [" + this.t1 + "]. Sum ["
							+ this.totalConsumption + "] Counter [" + this.totalConsumptionCounter + "].");
					t = t1 - t0;
					float hours = t / 3600;
					requiredConsumption = (long) ((totalConsumption * hours) / (2 * totalConsumptionCounter));
					log.info("Required Consumption" + requiredConsumption + "during " + now);

				}
				// reset values
				log.info("Resetting Values during" + now);
				this.t0 = null;
				this.totalConsumption = 0;
				this.totalConsumptionCounter = 0;
				log.info(production + "is greater than" + consumption + "so switching the state from PRODUCTION EXCEEDING CONSUMPTION to PRODUCTION HIGHER THAN CONSUMPTION");
				this.currentState = State.PRODUCTION_HIGHER_THAN_CONSUMPTION;
				break;

			case PRODUCTION_HIGHER_THAN_CONSUMPTION:
				if (production < consumption) {
					log.info(production + "is lesser than" + consumption + "so switching the state from PRODUCTION HIGHER THAN CONSUMPTION to PRODUCTION DROPPED BELOW CONSUMPTION");
					this.currentState = State.PRODUCTION_DROPPED_BELOW_CONSUMPTION;
				}
				break;

			case PRODUCTION_DROPPED_BELOW_CONSUMPTION:
				this.t0 = secondOfDay;
				log.info(production + "is lesser than" + consumption + "so switching the state from PRODUCTION DROPPED BELOW CONSUMPTION to PRODUCTION LOWER THAN CONSUMPTION");
				this.currentState = State.PRODUCTION_LOWER_THAN_CONSUMPTION;
				break;
			}

		} catch (InvalidValueException | JsonIOException | JsonSyntaxException e) {
			log.error(e.getMessage());
		}
	}
}
