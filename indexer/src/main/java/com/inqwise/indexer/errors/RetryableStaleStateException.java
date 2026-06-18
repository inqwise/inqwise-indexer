package com.inqwise.indexer.errors;

import com.inqwise.errors.ErrorTicket;
import com.inqwise.errors.ProvidesErrorTicket;

public class RetryableStaleStateException extends RuntimeException implements ProvidesErrorTicket {
	private static final long serialVersionUID = 563164873291485037L;

	public RetryableStaleStateException(String message) {
		super(message);
	}

	public RetryableStaleStateException(String message, Throwable cause) {
		super(message, cause);
	}

	@Override
	public ErrorTicket.Builder getErrorTicketBuilder() {
		return ErrorTicket.builder()
			.withError(IndexerErrorCodes.RetryableStaleState)
			.withErrorGroup(IndexerErrorCodes.GROUP)
			.withDetails(getMessage());
	}
}
