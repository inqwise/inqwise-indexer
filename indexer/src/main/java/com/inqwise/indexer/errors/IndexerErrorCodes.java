package com.inqwise.indexer.errors;

import com.inqwise.errors.ErrorCode;

public enum IndexerErrorCodes implements ErrorCode {
	InvalidRequest(400),
	NotFound(404),
	InvalidRoute(404),
	RouteRetryable(503),
	RouteFailed(409),
	InternalError(500);

	public static final String GROUP = "indexer";

	private final int statusCode;

	IndexerErrorCodes(int statusCode) {
		this.statusCode = statusCode;
	}

	@Override
	public String group() {
		return GROUP;
	}

	@Override
	public int statusCode() {
		return statusCode;
	}
}
