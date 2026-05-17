package com.inqwise.indexer.metadata;

import java.time.Instant;

import io.vertx.core.json.JsonObject;

public record ManifestRecord(
	Integer id,
	String uid,
	Integer targetId,
	Integer indexerId,
	String targetName,
	String indexName,
	String schemaName,
	String schemaVersion,
	JsonObject manifest,
	ManifestStatus status,
	Instant createdAt,
	Instant updatedAt,
	long version
) {
	public ManifestRecord {
		manifest = manifest == null ? new JsonObject() : manifest.copy();
	}

	@Override
	public JsonObject manifest() {
		return manifest.copy();
	}
}
