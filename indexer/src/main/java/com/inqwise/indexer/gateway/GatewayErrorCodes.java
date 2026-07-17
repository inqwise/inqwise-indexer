package com.inqwise.indexer.gateway;

import com.inqwise.errors.ErrorCode;

public enum GatewayErrorCodes implements ErrorCode {
	Unauthenticated(401),
	Forbidden(403),
	RateLimited(429),
	InvalidRequest(400),
	ResourceNotFound(404),
	Conflict(409),
	GatewayRequestRejected(403),
	AdminRestNotConfigured(503),
	UpstreamUnavailable(502);

	public static final String GROUP = "gateway";

	private final int statusCode;

	GatewayErrorCodes(int statusCode) {
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
