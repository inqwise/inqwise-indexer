package com.inqwise.indexer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class IndexerWebOptionsTest {
	@Test
	void buildsValidatedImmutableOptions() {
		IndexerWebOptions options = IndexerWebOptions.builder()
			.withHost("0.0.0.0")
			.withPort(3100)
			.withAdminHost("admin.internal")
			.withAdminPort(9000)
			.withTargetActionHost("actions.internal")
			.withTargetActionPort(9001)
			.withRuntimeHost("runtime.internal")
			.withRuntimePort(9003)
			.withHealthHost("health.internal")
			.withHealthPort(9004)
			.withMetricsHost("metrics.internal")
			.withMetricsPort(9091)
			.withReportsHost("reports.internal")
			.withReportsPort(9006)
			.build();

		assertEquals("0.0.0.0", options.host());
		assertEquals(3100, options.port());
		assertEquals("admin.internal", options.adminHost());
		assertEquals(9000, options.adminPort());
		assertEquals("metrics.internal", options.metricsHost());
		assertEquals(9091, options.metricsPort());
		assertEquals("reports.internal", options.reportsHost());
		assertEquals(9006, options.reportsPort());
		assertEquals(options.toJson().encode(), IndexerWebOptions.from(
			options.toJson()
		).toJson().encode());
	}

	@Test
	void appliesLocalDefaultsFromEmptyConfiguration() {
		IndexerWebOptions options = IndexerWebOptions.from(new JsonObject());

		assertEquals(IndexerWebOptions.DEFAULT_HOST, options.host());
		assertEquals(IndexerWebOptions.DEFAULT_PORT, options.port());
		assertEquals(IndexerWebOptions.DEFAULT_ADMIN_PORT, options.adminPort());
		assertEquals(
			IndexerWebOptions.DEFAULT_TARGET_ACTION_PORT,
			options.targetActionPort()
		);
		assertEquals(IndexerWebOptions.DEFAULT_RUNTIME_PORT, options.runtimePort());
		assertEquals(IndexerWebOptions.DEFAULT_HEALTH_PORT, options.healthPort());
		assertEquals(IndexerWebOptions.DEFAULT_METRICS_PORT, options.metricsPort());
		assertEquals(IndexerWebOptions.DEFAULT_REPORTS_PORT, options.reportsPort());
	}

	@Test
	void rejectsInvalidListenerAndUpstreamValues() {
		assertThrows(
			IllegalArgumentException.class,
			() -> IndexerWebOptions.builder().withHost(" ").build()
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> IndexerWebOptions.builder().withPort(-1).build()
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> IndexerWebOptions.builder().withAdminPort(0).build()
		);
	}
}
