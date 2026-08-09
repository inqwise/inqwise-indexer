package com.inqwise.indexer.query.service;

import java.util.Map;
import java.util.Objects;

public final class ReportCaller {
	private final String consumerName;
	private final String subject;
	private final Map<String, String> trustedAttributes;

	private ReportCaller(Builder builder) {
		consumerName = builder.consumerName;
		subject = builder.subject;
		trustedAttributes = builder.trustedAttributes;
	}

	public String getConsumerName() {
		return consumerName;
	}

	public String getSubject() {
		return subject;
	}

	public Map<String, String> getTrustedAttributes() {
		return Map.copyOf(trustedAttributes);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String consumerName;
		private String subject;
		private Map<String, String> trustedAttributes = Map.of();

		private Builder() {
		}

		public Builder withConsumerName(String value) {
			consumerName = value;
			return this;
		}

		public Builder withSubject(String value) {
			subject = value;
			return this;
		}

		public Builder withTrustedAttributes(Map<String, String> value) {
			trustedAttributes = value == null ? null : Map.copyOf(value);
			return this;
		}

		public ReportCaller build() {
			if (consumerName == null || consumerName.isBlank()) {
				throw new IllegalArgumentException("consumerName must not be blank");
			}
			if (subject != null && subject.isBlank()) {
				throw new IllegalArgumentException("subject must not be blank");
			}
			Objects.requireNonNull(trustedAttributes, "trustedAttributes");
			return new ReportCaller(this);
		}
	}
}
