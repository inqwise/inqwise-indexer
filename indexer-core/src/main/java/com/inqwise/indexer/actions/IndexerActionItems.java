package com.inqwise.indexer.actions;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

public final class IndexerActionItems {
	private IndexerActionItems() {
	}

	public static PutDocumentActionItem putDocument(String uid, JsonObject document) {
		return PutDocumentActionItem.builder()
			.withUid(uid)
			.withDocument(document)
			.build();
	}

	public static PutDocumentActionItem concretePutDocument(
		Integer targetId,
		Integer indexerId,
		String indexName,
		String uid,
		JsonObject document
	) {
		return PutDocumentActionItem.builder()
			.withTargetId(Objects.requireNonNull(targetId, "targetId"))
			.withIndexerId(Objects.requireNonNull(indexerId, "indexerId"))
			.withIndexName(Objects.requireNonNull(indexName, "indexName"))
			.withUid(uid)
			.withDocument(document)
			.build();
	}

	public static RemoveDocumentActionItem removeDocument(String uid) {
		return RemoveDocumentActionItem.builder()
			.withUid(uid)
			.build();
	}

	public static RemoveDocumentActionItem concreteRemoveDocument(
		Integer targetId,
		Integer indexerId,
		String indexName,
		String uid
	) {
		return RemoveDocumentActionItem.builder()
			.withTargetId(Objects.requireNonNull(targetId, "targetId"))
			.withIndexerId(Objects.requireNonNull(indexerId, "indexerId"))
			.withIndexName(Objects.requireNonNull(indexName, "indexName"))
			.withUid(uid)
			.build();
	}
}
