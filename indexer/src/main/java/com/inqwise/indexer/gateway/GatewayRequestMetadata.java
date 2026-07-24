package com.inqwise.indexer.gateway;

import java.util.Objects;

public final class GatewayRequestMetadata {
	private final String requestId;
	private final String operationId;
	private final String method;
	private final String path;
	private final String remoteAddress;

	private GatewayRequestMetadata(
		String requestId,
		String operationId,
		String method,
		String path,
		String remoteAddress
	) {
		this.requestId = requestId;
		this.operationId = operationId;
		this.method = method;
		this.path = path;
		this.remoteAddress = remoteAddress;
	}

	public static Builder builder() {
		return new Builder();
	}

	public String requestId() {
		return requestId;
	}

	public String operationId() {
		return operationId;
	}

	public String method() {
		return method;
	}

	public String path() {
		return path;
	}

	public String remoteAddress() {
		return remoteAddress;
	}

	public static final class Builder {
		private String requestId;
		private String operationId;
		private String method;
		private String path;
		private String remoteAddress;

		private Builder() {
		}

		public Builder withRequestId(String value) {
			requestId = value;
			return this;
		}

		public Builder withOperationId(String value) {
			operationId = value;
			return this;
		}

		public Builder withMethod(String value) {
			method = value;
			return this;
		}

		public Builder withPath(String value) {
			path = value;
			return this;
		}

		public Builder withRemoteAddress(String value) {
			remoteAddress = value;
			return this;
		}

		public GatewayRequestMetadata build() {
			requireText(requestId, "requestId");
			requireText(operationId, "operationId");
			requireText(method, "method");
			requireText(path, "path");
			requireText(remoteAddress, "remoteAddress");
			return new GatewayRequestMetadata(
				requestId,
				operationId,
				method,
				path,
				remoteAddress
			);
		}
	}

	private static void requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
	}
}
