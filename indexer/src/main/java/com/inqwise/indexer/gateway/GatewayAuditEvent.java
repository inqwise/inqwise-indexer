package com.inqwise.indexer.gateway;

import java.util.Objects;
import java.util.Optional;

public final class GatewayAuditEvent {
	private final GatewayRequestMetadata request;
	private final GatewayPrincipal principal;
	private final GatewayAuditOutcome outcome;
	private final String failureCode;

	private GatewayAuditEvent(
		GatewayRequestMetadata request,
		GatewayPrincipal principal,
		GatewayAuditOutcome outcome,
		String failureCode
	) {
		this.request = request;
		this.principal = principal;
		this.outcome = outcome;
		this.failureCode = failureCode;
	}

	public static Builder builder() {
		return new Builder();
	}

	public GatewayRequestMetadata request() {
		return request;
	}

	public Optional<GatewayPrincipal> principal() {
		return Optional.ofNullable(principal);
	}

	public GatewayAuditOutcome outcome() {
		return outcome;
	}

	public Optional<String> failureCode() {
		return Optional.ofNullable(failureCode);
	}

	public static final class Builder {
		private GatewayRequestMetadata request;
		private GatewayPrincipal principal;
		private GatewayAuditOutcome outcome;
		private String failureCode;

		private Builder() {
		}

		public Builder withRequest(GatewayRequestMetadata value) {
			request = Objects.requireNonNull(value, "value");
			return this;
		}

		public Builder withPrincipal(GatewayPrincipal value) {
			principal = value;
			return this;
		}

		public Builder withOutcome(GatewayAuditOutcome value) {
			outcome = Objects.requireNonNull(value, "value");
			return this;
		}

		public Builder withFailureCode(String value) {
			failureCode = value;
			return this;
		}

		public GatewayAuditEvent build() {
			Objects.requireNonNull(request, "request");
			Objects.requireNonNull(outcome, "outcome");
			if (outcome == GatewayAuditOutcome.SUCCESS && failureCode != null) {
				throw new IllegalArgumentException("Successful audit event must not have a failure code");
			}
			if (outcome == GatewayAuditOutcome.FAILURE) {
				requireText(failureCode, "failureCode");
			}
			return new GatewayAuditEvent(request, principal, outcome, failureCode);
		}
	}

	private static void requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
	}
}
