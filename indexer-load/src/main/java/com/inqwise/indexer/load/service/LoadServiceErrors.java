package com.inqwise.indexer.load.service;

import com.inqwise.errors.ErrorTicket;
import com.inqwise.indexer.errors.RetryableStaleStateException;

public final class LoadServiceErrors {
	private LoadServiceErrors() {
	}

	public static ErrorTicket invalidRequest(String message) {
		return ticket(LoadErrorCodes.InvalidRequest, message);
	}

	public static ErrorTicket normalize(Throwable error) {
		if (error instanceof ErrorTicket ticket) {
			return ticket;
		}
		if (error instanceof RetryableStaleStateException staleState) {
			return staleState.toErrorTicket();
		}
		if (error instanceof IllegalArgumentException || error instanceof NullPointerException) {
			return invalidRequest(error.getMessage());
		}
		return ErrorTicket.propagate(
			error,
			builder -> builder.withError(LoadErrorCodes.InternalError)
		);
	}

	private static ErrorTicket ticket(LoadErrorCodes code, String message) {
		return ErrorTicket.builder()
			.withError(code)
			.withDetails(message)
			.build();
	}
}
