package com.inqwise.indexer.query.rest;

import com.inqwise.errors.ErrorTicket;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

final class ReportsRestErrorMapper {
	private ReportsRestErrorMapper() {
	}

	static void write(RoutingContext context, Throwable error) {
		if (context.response().ended()) {
			return;
		}
		if (error instanceof HttpException httpError) {
			write(
				context,
				httpError.getStatusCode(),
				new JsonObject()
					.put("status", httpError.getStatusCode())
					.put("message", httpError.getPayload() == null
						? httpError.getMessage()
						: httpError.getPayload())
			);
			return;
		}
		if (error instanceof ErrorTicket ticket) {
			int status = ticket.optStatusCode().orElseGet(() ->
				ticket.getStatus() == null ? 500 : ticket.getStatus()
			);
			write(context, status, ticket.toJson());
			return;
		}
		if (error instanceof IllegalArgumentException) {
			write(
				context,
				400,
				new JsonObject().put("status", 400).put("message", error.getMessage())
			);
			return;
		}
		write(
			context,
			500,
			new JsonObject().put("status", 500).put("message", "Internal server error")
		);
	}

	private static void write(RoutingContext context, int status, JsonObject body) {
		context.response()
			.setStatusCode(status)
			.putHeader("content-type", "application/json")
			.end(body.encode());
	}
}
