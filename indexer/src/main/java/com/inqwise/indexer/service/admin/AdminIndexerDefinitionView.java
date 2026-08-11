package com.inqwise.indexer.service.admin;

import java.util.Objects;

import com.inqwise.indexer.provisioning.definitions.IndexerDefinition;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminIndexerDefinitionView {
	public static final class Keys {
		public static final String NAME = "name";
		public static final String INDEX = "index";
		public static final String QUEUE = "queue";
		public static final String SCHEMA_NAME = "schema_name";
		public static final String SCHEMA_VERSION = "schema_version";
		public static final String SETTINGS = "settings";
		public static final String MAPPINGS = "mappings";

		private Keys() {
		}
	}

	private String name;
	private String schemaName;
	private String schemaVersion;
	private JsonObject indexSettings = new JsonObject();
	private JsonObject indexMappings = new JsonObject();
	private JsonObject queueSettings = new JsonObject();

	public AdminIndexerDefinitionView() {
	}

	public AdminIndexerDefinitionView(JsonObject json) {
		this.name = json.getString(Keys.NAME);
		JsonObject index = json.getJsonObject(Keys.INDEX, new JsonObject());
		this.schemaName = index.getString(Keys.SCHEMA_NAME);
		this.schemaVersion = index.getString(Keys.SCHEMA_VERSION);
		this.indexSettings = index.getJsonObject(Keys.SETTINGS, new JsonObject()).copy();
		this.indexMappings = index.getJsonObject(Keys.MAPPINGS, new JsonObject()).copy();
		JsonObject queue = json.getJsonObject(Keys.QUEUE, new JsonObject());
		this.queueSettings = queue.getJsonObject(Keys.SETTINGS, new JsonObject()).copy();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static AdminIndexerDefinitionView from(String name, IndexerDefinition definition) {
		Objects.requireNonNull(definition, "definition");
		return builder()
			.withName(name)
			.withSchemaName(definition.index().schemaName())
			.withSchemaVersion(definition.index().schemaVersion())
			.withIndexSettings(definition.index().settings())
			.withIndexMappings(definition.index().mappings())
			.withQueueSettings(definition.queue().settings())
			.build();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.NAME, name)
			.put(Keys.INDEX, new JsonObject()
				.put(Keys.SCHEMA_NAME, schemaName)
				.put(Keys.SCHEMA_VERSION, schemaVersion)
				.put(Keys.SETTINGS, indexSettings.copy())
				.put(Keys.MAPPINGS, indexMappings.copy()))
			.put(Keys.QUEUE, new JsonObject()
				.put(Keys.SETTINGS, queueSettings.copy()));
	}

	public String getName() {
		return name;
	}

	public AdminIndexerDefinitionView setName(String name) {
		this.name = name;
		return this;
	}

	public String getSchemaName() {
		return schemaName;
	}

	public AdminIndexerDefinitionView setSchemaName(String schemaName) {
		this.schemaName = schemaName;
		return this;
	}

	public String getSchemaVersion() {
		return schemaVersion;
	}

	public AdminIndexerDefinitionView setSchemaVersion(String schemaVersion) {
		this.schemaVersion = schemaVersion;
		return this;
	}

	public JsonObject getIndexSettings() {
		return indexSettings.copy();
	}

	public AdminIndexerDefinitionView setIndexSettings(JsonObject indexSettings) {
		this.indexSettings = indexSettings == null ? new JsonObject() : indexSettings.copy();
		return this;
	}

	public JsonObject getIndexMappings() {
		return indexMappings.copy();
	}

	public AdminIndexerDefinitionView setIndexMappings(JsonObject indexMappings) {
		this.indexMappings = indexMappings == null ? new JsonObject() : indexMappings.copy();
		return this;
	}

	public JsonObject getQueueSettings() {
		return queueSettings.copy();
	}

	public AdminIndexerDefinitionView setQueueSettings(JsonObject queueSettings) {
		this.queueSettings = queueSettings == null ? new JsonObject() : queueSettings.copy();
		return this;
	}

	public static final class Builder {
		private String name;
		private String schemaName;
		private String schemaVersion;
		private JsonObject indexSettings = new JsonObject();
		private JsonObject indexMappings = new JsonObject();
		private JsonObject queueSettings = new JsonObject();

		private Builder() {
		}

		public Builder withName(String value) {
			name = value;
			return this;
		}

		public Builder withSchemaName(String value) {
			schemaName = value;
			return this;
		}

		public Builder withSchemaVersion(String value) {
			schemaVersion = value;
			return this;
		}

		public Builder withIndexSettings(JsonObject value) {
			indexSettings = value == null ? new JsonObject() : value.copy();
			return this;
		}

		public Builder withIndexMappings(JsonObject value) {
			indexMappings = value == null ? new JsonObject() : value.copy();
			return this;
		}

		public Builder withQueueSettings(JsonObject value) {
			queueSettings = value == null ? new JsonObject() : value.copy();
			return this;
		}

		public AdminIndexerDefinitionView build() {
			return new AdminIndexerDefinitionView()
				.setName(Objects.requireNonNull(name, "name"))
				.setSchemaName(Objects.requireNonNull(schemaName, "schemaName"))
				.setSchemaVersion(Objects.requireNonNull(schemaVersion, "schemaVersion"))
				.setIndexSettings(indexSettings)
				.setIndexMappings(indexMappings)
				.setQueueSettings(queueSettings);
		}
	}
}
