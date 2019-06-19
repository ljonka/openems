package io.openems.edge.bridge.lmnwired;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import io.openems.edge.bridge.lmnwired.BridgeLMNWiredImpl;

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
		BridgeLMNWiredImpl impl = new BridgeLMNWiredImpl();
		assertNotNull(impl);
	}

}
