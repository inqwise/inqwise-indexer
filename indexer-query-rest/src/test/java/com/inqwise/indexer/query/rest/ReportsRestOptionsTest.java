package com.inqwise.indexer.query.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.query.service.ReportDiscoveryServices;
import com.inqwise.indexer.query.service.ReportsServices;

import io.vertx.core.json.JsonObject;

class ReportsRestOptionsTest {
	@Test
	void appliesDisabledLocalDefaults() {
		ReportsRestOptions options = ReportsRestOptions.from(new JsonObject());

		assertFalse(options.enabled());
		assertEquals(ReportsRestOptions.DEFAULT_HOST, options.host());
		assertEquals(ReportsRestOptions.DEFAULT_PORT, options.port());
		assertEquals(ReportsServices.DEFAULT_ADDRESS, options.reportsAddress());
		assertEquals(
			ReportDiscoveryServices.DEFAULT_ADDRESS,
			options.discoveryAddress()
		);
	}

	@Test
	void rejectsInvalidListenerValues() {
		assertThrows(
			IllegalArgumentException.class,
			() -> ReportsRestOptions.builder().withHost(" ").build()
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> ReportsRestOptions.builder().withPort(-1).build()
		);
	}
}
