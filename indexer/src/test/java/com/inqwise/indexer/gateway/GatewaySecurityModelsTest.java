package com.inqwise.indexer.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class GatewaySecurityModelsTest {
	@Test
	void principalDefensivelyCopiesRoles() {
		List<String> roles = new ArrayList<>(List.of("reader"));
		GatewayPrincipal principal = GatewayPrincipal.builder()
			.withSubject("operator-17")
			.withAuthenticationScheme("oidc")
			.withAuthenticated(true)
			.withRoles(roles)
			.build();

		roles.add("administrator");

		assertEquals("operator-17", principal.subject());
		assertEquals("oidc", principal.authenticationScheme());
		assertTrue(principal.authenticated());
		assertEquals(List.of("reader"), List.copyOf(principal.roles()));
	}

	@Test
	void auditEventRequiresFailureCodeOnlyForFailures() {
		GatewayRequestMetadata request = request();

		assertThrows(
			IllegalArgumentException.class,
			() -> GatewayAuditEvent.builder()
				.withRequest(request)
				.withOutcome(GatewayAuditOutcome.SUCCESS)
				.withFailureCode("Forbidden")
				.build()
		);
		assertThrows(
			NullPointerException.class,
			() -> GatewayAuditEvent.builder()
				.withRequest(request)
				.withOutcome(GatewayAuditOutcome.FAILURE)
				.build()
		);
	}

	private static GatewayRequestMetadata request() {
		return GatewayRequestMetadata.builder()
			.withRequestId("request-1")
			.withOperationId("gatewayStatus")
			.withMethod("GET")
			.withPath("/gateway/status")
			.withRemoteAddress("127.0.0.1")
			.build();
	}
}
