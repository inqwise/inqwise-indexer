package com.inqwise.indexer.catalog.targets;

import java.util.Objects;

import com.inqwise.indexer.provisioning.DocumentIndexNameValidator;

public record CreateTargetIndexerRequest(
	String prefix,
	String indexName,
	String queueName,
	InitialPublicationMode initialPublicationMode
) {
	public CreateTargetIndexerRequest {
		prefix = requireNonBlank(prefix, "prefix");
		indexName = DocumentIndexNameValidator.requireConcrete(indexName);
		queueName = requireNonBlank(queueName, "queueName");
		Objects.requireNonNull(initialPublicationMode, "initialPublicationMode");
	}

	private static String requireNonBlank(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String prefix;
		private String indexName;
		private String queueName;
		private InitialPublicationMode initialPublicationMode;

		private Builder() {
		}

		public Builder withPrefix(String value) {
			prefix = value;
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

		public Builder withInitialPublicationMode(InitialPublicationMode value) {
			initialPublicationMode = value;
			return this;
		}

		public CreateTargetIndexerRequest build() {
			return new CreateTargetIndexerRequest(
				prefix,
				indexName,
				queueName,
				initialPublicationMode
			);
		}
	}
}
