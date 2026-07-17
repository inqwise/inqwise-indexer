package com.inqwise.indexer.load.service;

import com.inqwise.errors.ErrorCode;

public enum LoadErrorCodes implements ErrorCode {
	InvalidRequest(400),
	NotFound(404),
	Conflict(409),
	InternalError(500);

	private static final String GROUP = "indexer-load";
	private final int statusCode;

	LoadErrorCodes(int statusCode) {
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
