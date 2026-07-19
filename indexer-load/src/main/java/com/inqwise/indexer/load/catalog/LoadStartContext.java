package com.inqwise.indexer.load.catalog;

import com.inqwise.indexer.load.api.LoadRequest;

import java.util.Objects;

public record LoadStartContext(
	LoadRequest request,
	String indexName,
	String queueName
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private LoadRequest request;
		private String indexName;
		private String queueName;

		private Builder() {
		}

		public Builder withRequest(LoadRequest value) {
			request = value;
			return this;
		}

		public Builder withIndexName(String value) {
			indexName = value;
			return this;
		}

		public Builder withQueueName(String value) {
			queueName = value;
			return this;
		}

		public LoadStartContext build() {
			return new LoadStartContext(
				Objects.requireNonNull(request, "request"),
				Objects.requireNonNull(indexName, "indexName"),
				Objects.requireNonNull(queueName, "queueName")
			);
		}
	}
}
