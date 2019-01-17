package io.openems.edge.bridge.mbus;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

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
		BridgeMbusSerialImpl impl = new BridgeMbusSerialImpl();
		assertNotNull(impl);
	}

}
