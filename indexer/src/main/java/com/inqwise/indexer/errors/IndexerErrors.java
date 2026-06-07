package com.inqwise.indexer.errors;

import com.inqwise.errors.ErrorTicket;
import com.inqwise.indexer.commands.CommandFailure;

public final class IndexerErrors {
	private IndexerErrors() {
	}

	public static ErrorTicket invalidRequest(String message) {
		return ErrorTicket.builder()
			.withError(IndexerErrorCodes.InvalidRequest)
			.withDetails(message)
			.build();
	}

	public static ErrorTicket notFound(String message) {
		return ErrorTicket.builder()
			.withError(IndexerErrorCodes.NotFound)
			.withDetails(message)
			.build();
	}

	public static ErrorTicket normalize(Throwable error) {
		if (error instanceof ErrorTicket ticket) {
			return ticket;
		}

		if (error instanceof CommandFailure failure) {
			return fromCommandFailure(failure);
		}

		return ErrorTicket.propagate(error, builder -> builder.withError(IndexerErrorCodes.InternalError));
	}

	private static ErrorTicket fromCommandFailure(CommandFailure failure) {
		IndexerErrorCodes code;
		if (failure.stableInvalid()) {
			code = IndexerErrorCodes.InvalidRoute;
		} else if (failure.retryable()) {
			code = IndexerErrorCodes.RouteRetryable;
		} else {
			code = IndexerErrorCodes.RouteFailed;
		}

		return ErrorTicket.builder()
			.withError(code)
			.withDetails(failure.getMessage())
			.build();
	}
}
