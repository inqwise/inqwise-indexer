package com.inqwise.indexer.load.rest;

import com.inqwise.errors.ErrorTicket;
import com.inqwise.indexer.load.service.LoadServiceErrors;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

final class LoadHttpErrorMapper {
	private LoadHttpErrorMapper() {
	}

	static void write(RoutingContext context, Throwable error) {
		if (context.response().ended()) {
			return;
		}
		if (error instanceof HttpException httpError) {
			context.response()
				.setStatusCode(httpError.getStatusCode())
				.putHeader("content-type", "application/json")
				.end(new JsonObject()
					.put("status", httpError.getStatusCode())
					.put("message", httpError.getMessage())
					.encode());
			return;
		}

		ErrorTicket ticket = LoadServiceErrors.normalize(error);
		int status = ticket.optStatusCode()
			.orElseGet(() -> ticket.getStatus() == null ? 500 : ticket.getStatus());
		context.response()
			.setStatusCode(status)
			.putHeader("content-type", "application/json")
			.end(ticket.toJson().encode());
	}
}
