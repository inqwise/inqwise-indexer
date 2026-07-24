package com.inqwise.indexer.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

class GatewayRequestHooksTest {
	@Test
	void composesNarrowDeploymentContracts() {
		List<String> calls = new ArrayList<>();
		GatewayPrincipal principal = GatewayPrincipal.builder()
			.withSubject("operator-17")
			.withAuthenticationScheme("test")
			.withAuthenticated(true)
			.build();
		GatewayRequestMetadata request = GatewayRequestMetadata.builder()
			.withRequestId("request-1")
			.withOperationId("gatewayStatus")
			.withMethod("GET")
			.withPath("/gateway/status")
			.withRemoteAddress("127.0.0.1")
			.build();
		GatewayAuditEvent event = GatewayAuditEvent.builder()
			.withRequest(request)
			.withPrincipal(principal)
			.withOutcome(GatewayAuditOutcome.SUCCESS)
			.build();
		GatewayRequestHooks hooks = new GatewayRequestHooks(
			(context, actualRequest) -> {
				calls.add("authenticate");
				assertSame(request, actualRequest);
				return Future.succeededFuture(principal);
			},
			(actualRequest, actualPrincipal) -> {
				calls.add("authorize");
				assertSame(request, actualRequest);
				assertSame(principal, actualPrincipal);
				return Future.succeededFuture();
			},
			(actualRequest, actualPrincipal) -> {
				calls.add("rateLimit");
				assertSame(request, actualRequest);
				assertSame(principal, actualPrincipal);
				return Future.succeededFuture();
			},
			actualEvent -> {
				calls.add("audit");
				assertSame(event, actualEvent);
				return Future.succeededFuture();
			}
		);

		GatewayPrincipal authenticated = hooks.authenticate(null, request).result();
		hooks.authorize(request, authenticated);
		hooks.rateLimit(request, authenticated);
		hooks.audit(event);

		assertEquals(List.of("authenticate", "authorize", "rateLimit", "audit"), calls);
	}
}
