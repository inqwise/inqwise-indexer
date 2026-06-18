package com.inqwise.indexer.gateway;

import com.inqwise.errors.ErrorTicket;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

final class GatewayErrorResponses {
	private GatewayErrorResponses() {
	}

	static void requestRejected(RoutingContext context, Throwable error) {
		if (error instanceof ErrorTicket ticket) {
			write(context, ticket);
		} else {
			write(context, ticket(GatewayErrorCodes.GatewayRequestRejected, "Gateway request rejected"));
		}
	}

	static void adminRestNotConfigured(RoutingContext context) {
		write(context, ticket(GatewayErrorCodes.AdminRestNotConfigured, "Gateway upstream is not configured"));
	}

	static void upstreamUnavailable(RoutingContext context) {
		write(context, ticket(GatewayErrorCodes.UpstreamUnavailable, "Upstream service unavailable"));
	}

	static ErrorTicket unauthenticated() {
		return ticket(GatewayErrorCodes.Unauthenticated, "Authentication is required");
	}

	static ErrorTicket forbidden() {
		return ticket(GatewayErrorCodes.Forbidden, "Access is forbidden");
	}

	static ErrorTicket rateLimited() {
		return ticket(GatewayErrorCodes.RateLimited, "Too many requests");
	}

	private static ErrorTicket ticket(GatewayErrorCodes code, String message) {
		return ErrorTicket.builder()
			.withError(code)
			.withErrorGroup(code.group())
			.withStatusCode(code.statusCode())
			.withDetails(message)
			.build();
	}

	private static void write(RoutingContext context, ErrorTicket ticket) {
		JsonObject body = ticket.toJson();
		context.response()
			.setStatusCode(ticket.optStatusCode().orElseGet(ticket::getStatus))
			.putHeader("content-type", "application/json")
			.end(body.encode());
	}
}
