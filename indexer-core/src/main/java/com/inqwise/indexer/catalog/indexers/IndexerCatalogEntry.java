package com.inqwise.indexer.catalog.indexers;

import java.time.Instant;
import java.util.Objects;

public record IndexerCatalogEntry(
	Integer id,
	String uid,
	Integer targetId,
	String targetName,
	String indexName,
	String queueName,
	IndexerType type,
	IndexerRole role,
	IndexResourceOwnership indexOwnership,
	IndexerStatus status,
	IndexerProvisioningState provisioningState,
	IndexerRuntimeState runtimeState,
	MutationState mutationState,
	Instant createdAt,
	Instant updatedAt,
	long version
) {
	public IndexerCatalogEntry {
		id = Objects.requireNonNull(id, "id");
		uid = Objects.requireNonNull(uid, "uid");
		targetId = Objects.requireNonNull(targetId, "targetId");
		targetName = Objects.requireNonNull(targetName, "targetName");
		indexName = Objects.requireNonNull(indexName, "indexName");
		queueName = Objects.requireNonNull(queueName, "queueName");
		type = Objects.requireNonNull(type, "type");
		role = Objects.requireNonNull(role, "role");
		indexOwnership = Objects.requireNonNull(indexOwnership, "indexOwnership");
		status = Objects.requireNonNull(status, "status");
		provisioningState = Objects.requireNonNull(provisioningState, "provisioningState");
		runtimeState = Objects.requireNonNull(runtimeState, "runtimeState");
		mutationState = Objects.requireNonNull(mutationState, "mutationState");
	}
}
