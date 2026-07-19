package com.inqwise.indexer.load.api;

public record LoadCompletion(
	String auditRef
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String auditRef;

		private Builder() {
		}

		public Builder withAuditRef(String value) {
			auditRef = value;
			return this;
		}

		public LoadCompletion build() {
			return new LoadCompletion(auditRef);
		}
	}
}
