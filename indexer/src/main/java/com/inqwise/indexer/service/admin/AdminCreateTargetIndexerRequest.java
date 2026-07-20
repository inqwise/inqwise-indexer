package com.inqwise.indexer.service.admin;

import java.util.Objects;

import com.inqwise.indexer.catalog.targets.CreateTargetIndexerRequest;
import com.inqwise.indexer.catalog.targets.InitialPublicationMode;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminCreateTargetIndexerRequest {
	public static final class Keys {
		public static final String PREFIX = "prefix";
		public static final String INDEX_NAME = "index_name";
		public static final String QUEUE_NAME = "queue_name";
		public static final String INITIAL_PUBLICATION_MODE = "initial_publication_mode";

		private Keys() {
		}
	}

	private String prefix;
	private String indexName;
	private String queueName;
	private InitialPublicationMode initialPublicationMode;

	public AdminCreateTargetIndexerRequest() {
	}

	public AdminCreateTargetIndexerRequest(JsonObject json) {
		this.prefix = json.getString(Keys.PREFIX);
		this.indexName = json.getString(Keys.INDEX_NAME);
		this.queueName = json.getString(Keys.QUEUE_NAME);
		String mode = json.getString(Keys.INITIAL_PUBLICATION_MODE);
		this.initialPublicationMode = mode == null ? null : InitialPublicationMode.valueOf(mode);
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.PREFIX, prefix)
			.put(Keys.INDEX_NAME, indexName)
			.put(Keys.QUEUE_NAME, queueName)
			.put(Keys.INITIAL_PUBLICATION_MODE, initialPublicationMode == null
				? null
				: initialPublicationMode.name());
	}

	CreateTargetIndexerRequest toTargetRequest() {
		return CreateTargetIndexerRequest.builder()
			.withPrefix(prefix)
			.withIndexName(indexName)
			.withQueueName(queueName)
			.withInitialPublicationMode(initialPublicationMode)
			.build();
	}

	public String getPrefix() {
		return prefix;
	}

	public AdminCreateTargetIndexerRequest setPrefix(String prefix) {
		this.prefix = prefix;
		return this;
	}

	public String getIndexName() {
		return indexName;
	}

	public AdminCreateTargetIndexerRequest setIndexName(String indexName) {
		this.indexName = indexName;
		return this;
	}

	public String getQueueName() {
		return queueName;
	}

	public AdminCreateTargetIndexerRequest setQueueName(String queueName) {
		this.queueName = queueName;
		return this;
	}

	public InitialPublicationMode getInitialPublicationMode() {
		return initialPublicationMode;
	}

	public AdminCreateTargetIndexerRequest setInitialPublicationMode(
		InitialPublicationMode initialPublicationMode
	) {
		this.initialPublicationMode = initialPublicationMode;
		return this;
	}

	private static String requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value;
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

		public AdminCreateTargetIndexerRequest build() {
			return new AdminCreateTargetIndexerRequest()
				.setPrefix(requireText(prefix, "prefix"))
				.setIndexName(requireText(indexName, "indexName"))
				.setQueueName(requireText(queueName, "queueName"))
				.setInitialPublicationMode(Objects.requireNonNull(
					initialPublicationMode,
					"initialPublicationMode"
				));
		}
	}
}
