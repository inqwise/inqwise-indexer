package com.inqwise.indexer.query;

public final class InvalidReportRequestException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public InvalidReportRequestException(String message) {
		super(message);
	}

	public InvalidReportRequestException(String message, Throwable cause) {
		super(message, cause);
	}
}
