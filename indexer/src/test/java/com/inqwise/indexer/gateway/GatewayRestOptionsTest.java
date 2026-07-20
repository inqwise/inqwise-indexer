package com.inqwise.indexer.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GatewayRestOptionsTest {
	@Test
	void buildsConfiguredOptions() {
		GatewayRestOptions options = GatewayRestOptions.builder()
			.withHost("0.0.0.0")
			.withPort(0)
			.withOpenApiPath("openapi/custom-gateway.yaml")
			.withAdminRestBaseUri("http://127.0.0.1:9090")
			.withRequestTimeoutMs(1_000L)
			.withApiKey("secret")
			.withApiKeyHeader("x-indexer-key")
			.withRateLimitRequests(10)
			.withRateLimitWindowMs(2_000L)
			.build();

		assertEquals("0.0.0.0", options.getHost());
		assertEquals(0, options.getPort());
		assertEquals("openapi/custom-gateway.yaml", options.getOpenApiPath());
		assertEquals("http://127.0.0.1:9090", options.getAdminRestBaseUri());
		assertEquals(1_000L, options.getRequestTimeoutMs());
		assertEquals("secret", options.getApiKey());
		assertEquals("x-indexer-key", options.getApiKeyHeader());
		assertEquals(10, options.getRateLimitRequests());
		assertEquals(2_000L, options.getRateLimitWindowMs());
	}

	@Test
	void rejectsInvalidGatewayConfiguration() {
		assertThrows(
			IllegalArgumentException.class,
			() -> GatewayRestOptions.builder().withPort(65_536).build()
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> GatewayRestOptions.builder().withRequestTimeoutMs(0).build()
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> GatewayRestOptions.builder().withRateLimitRequests(-1).build()
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> GatewayRestOptions.builder().withRateLimitWindowMs(0).build()
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> GatewayRestOptions.builder().withApiKeyHeader(" ").build()
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> GatewayRestOptions.builder()
				.withAdminRestBaseUri("https://127.0.0.1:9090")
				.build()
		);
	}
}
