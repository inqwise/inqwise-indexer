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
}
