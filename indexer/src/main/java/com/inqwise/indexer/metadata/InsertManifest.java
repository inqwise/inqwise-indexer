package com.inqwise.indexer.metadata;

import io.vertx.core.json.JsonObject;

public record InsertManifest(
	String uid,
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
		manifest = manifest == null ? new JsonObject() : manifest.copy();
	}

	@Override
	public JsonObject manifest() {
		return manifest.copy();
	}
}
