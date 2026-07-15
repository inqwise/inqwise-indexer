package com.inqwise.indexer.rest;

import com.inqwise.errors.ErrorTicket;
import com.inqwise.indexer.service.IndexerErrors;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

public final class HttpErrorMapper {
	private HttpErrorMapper() {
	}

	public static void write(RoutingContext context, Throwable error) {
		if (error instanceof HttpException httpError) {
			writeHttpException(context, httpError);
			return;
		}

		ErrorTicket ticket = IndexerErrors.normalize(error);
		int statusCode = ticket.optStatusCode().orElseGet(() -> ticket.getStatus() == null ? 500 : ticket.getStatus());
		JsonObject body = ticket.toJson();
		if (!context.response().ended()) {
			context.response()
				.setStatusCode(statusCode)
				.putHeader("content-type", "application/json")
				.end(body.encode());
		}
	}

	private static void writeHttpException(RoutingContext context, HttpException error) {
		JsonObject body = new JsonObject()
			.put("status", error.getStatusCode())
			.put("message", error.getPayload() == null ? error.getMessage() : error.getPayload());
		if (!context.response().ended()) {
			context.response()
				.setStatusCode(error.getStatusCode())
				.putHeader("content-type", "application/json")
				.end(body.encode());
		}
	}
}
