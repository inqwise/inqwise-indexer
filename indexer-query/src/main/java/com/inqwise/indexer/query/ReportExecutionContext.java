package com.inqwise.indexer.query;

import java.util.Map;
import java.util.Objects;

public record ReportExecutionContext(
	ReportQueryScope scope,
	Map<String, String> trustedAttributes
) {
	public ReportExecutionContext {
		scope = Objects.requireNonNull(scope, "scope");
		trustedAttributes = trustedAttributes == null
			? Map.of()
			: Map.copyOf(trustedAttributes);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private ReportQueryScope scope = ReportQueryScope.builder().build();
		private Map<String, String> trustedAttributes = Map.of();

		private Builder() {
		}

		public Builder withScope(ReportQueryScope value) {
			scope = value;
			return this;
		}

		public Builder withTrustedAttributes(Map<String, String> value) {
			trustedAttributes = value == null ? null : Map.copyOf(value);
			return this;
		}

		public ReportExecutionContext build() {
			return new ReportExecutionContext(
				Objects.requireNonNull(scope, "scope"),
				trustedAttributes
			);
		}
	}
}
