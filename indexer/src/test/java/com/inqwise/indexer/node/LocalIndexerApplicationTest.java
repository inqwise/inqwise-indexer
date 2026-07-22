package com.inqwise.indexer.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.catalog.targets.TargetPeriodStrategy;

class LocalIndexerApplicationTest {
	@Test
	void enablesLocalRestServicesWithoutGateway() {
		IndexerNodeOptions options = LocalIndexerApplication.localOptions();

		assertTrue(options.admin().isEnabled());
		assertTrue(options.adminRest().isEnabled());
		assertTrue(options.targetAction().isEnabled());
		assertTrue(options.targetActionRest().isEnabled());
		assertTrue(options.runtime().isEnabled());
		assertTrue(options.runtimeRest().isEnabled());
		assertFalse(options.gateway().isEnabled());
		assertEquals(8080, options.getAdminRestOptions().getPort());
		assertEquals(8081, options.getTargetActionRestOptions().getPort());
		assertEquals(8083, options.getRuntimeRestOptions().getPort());
	}

	@Test
	void suppliesCustomersDemoDefinition() {
		var definitions = LocalIndexerApplication.localTargetDefinitions();

		assertEquals(1, definitions.size());
		assertEquals("customers", definitions.get(0).targetName());
		assertEquals(TargetPeriodStrategy.MONTHLY, definitions.get(0).periodStrategy());
		assertTrue(definitions.get(0).autoProvisionOnWrite());
	}
}
