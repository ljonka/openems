package io.openems.edge.meter.eastron.sdm220mbus;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import io.openems.edge.meter.eastron.sdm220mbus.MeterSDM220MBus;;

/*
 * Example JUNit test case
 *
 */

public class ProviderImplTest {

	/*
	 * Example test method
	 */

	@Test
	public void simple() {
		MeterSDM220MBus impl = new MeterSDM220MBus();
		assertNotNull(impl);
	}

}
