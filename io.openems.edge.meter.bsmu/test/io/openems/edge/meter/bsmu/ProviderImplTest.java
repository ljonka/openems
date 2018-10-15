package io.openems.edge.meter.bsmu;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import io.openems.edge.meter.bsmu.BSMU;

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
		BSMU impl = new BSMU();
		assertNotNull(impl);
	}

}
