package com.inqwise.indexer.load.api;

import java.util.Objects;

public record LoadStopRequest(
	Integer indexerId,
	String reason
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private String reason;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withReason(String value) {
			reason = value;
			return this;
		}

		public LoadStopRequest build() {
			return new LoadStopRequest(
				Objects.requireNonNull(indexerId, "indexerId"),
				reason
			);
		}
	}
}
