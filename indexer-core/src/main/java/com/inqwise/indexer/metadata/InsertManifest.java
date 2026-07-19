package com.inqwise.indexer.metadata;

import java.util.Objects;

import com.inqwise.indexer.provisioning.ManifestStatus;

import io.vertx.core.json.JsonObject;

public record InsertManifest(
	String prefix,
	Integer targetId,
	Integer indexerId,
	String targetName,
	String indexName,
	String schemaName,
	String schemaVersion,
	JsonObject manifest,
	ManifestStatus status
) {
	public InsertManifest {
		Objects.requireNonNull(prefix, "prefix");
		manifest = manifest == null ? new JsonObject() : manifest.copy();
	}

	@Override
	public JsonObject manifest() {
		return manifest.copy();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String prefix;
		private Integer targetId;
		private Integer indexerId;
		private String targetName;
		private String indexName;
		private String schemaName;
		private String schemaVersion;
		private JsonObject manifest;
		private ManifestStatus status;

		private Builder() {
		}

		public Builder withPrefix(String value) {
			prefix = value;
			return this;
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withIndexName(String value) {
			indexName = value;
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

		public Builder withManifest(JsonObject value) {
			manifest = value == null ? null : value.copy();
			return this;
		}

		public Builder withStatus(ManifestStatus value) {
			status = value;
			return this;
		}

		public InsertManifest build() {
			return new InsertManifest(
				Objects.requireNonNull(prefix, "prefix"),
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(targetName, "targetName"),
				Objects.requireNonNull(indexName, "indexName"),
				Objects.requireNonNull(schemaName, "schemaName"),
				Objects.requireNonNull(schemaVersion, "schemaVersion"),
				manifest,
				Objects.requireNonNull(status, "status")
			);
		}
	}
}
