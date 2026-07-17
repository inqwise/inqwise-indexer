package com.inqwise.indexer.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.inqwise.errors.ErrorTicket;

class GatewayErrorResponsesTest {
	@Test
	void mapsOnlySafeUpstreamFailureCategories() {
		assertFailure(400, GatewayErrorCodes.InvalidRequest, "Request is invalid");
		assertFailure(422, GatewayErrorCodes.InvalidRequest, "Request is invalid");
		assertFailure(404, GatewayErrorCodes.ResourceNotFound, "Requested resource was not found");
		assertFailure(409, GatewayErrorCodes.Conflict, "Request conflicts with current state");
		assertFailure(401, GatewayErrorCodes.UpstreamUnavailable, "Upstream service unavailable");
		assertFailure(500, GatewayErrorCodes.UpstreamUnavailable, "Upstream service unavailable");
	}

	private void assertFailure(int upstreamStatus, GatewayErrorCodes code, String detail) {
		ErrorTicket ticket = GatewayErrorResponses.upstreamFailure(upstreamStatus);

		assertEquals(code.name(), ticket.toJson().getString("code"));
		assertEquals(code.statusCode(), ticket.toJson().getInteger("status"));
		assertEquals(detail, ticket.toJson().getString("detail"));
	}
}
