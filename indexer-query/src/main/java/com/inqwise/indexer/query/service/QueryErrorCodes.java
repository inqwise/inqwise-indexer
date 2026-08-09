package com.inqwise.indexer.query.service;

import com.inqwise.errors.ErrorCode;

public enum QueryErrorCodes implements ErrorCode {
	InvalidRequest(400),
	ReportNotFound(404),
	UnsupportedSchema(409),
	InternalError(500);

	public static final String GROUP = "indexer-query";

	private final int statusCode;

	QueryErrorCodes(int statusCode) {
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
