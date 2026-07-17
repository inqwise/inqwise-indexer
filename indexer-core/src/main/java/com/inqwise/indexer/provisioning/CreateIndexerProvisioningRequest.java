package com.inqwise.indexer.provisioning;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;

public record CreateIndexerProvisioningRequest(
	String prefix,
	Integer targetId,
	String indexName,
	String queueName,
	IndexerRole role,
	IndexResourceOwnership indexOwnership,
	IndexerRuntimeState runtimeState
) {
	public CreateIndexerProvisioningRequest {
		prefix = requireNonBlank(prefix, "prefix");
		targetId = Objects.requireNonNull(targetId, "targetId");
		indexName = DocumentIndexNameValidator.requireConcrete(indexName);
		queueName = requireNonBlank(queueName, "queueName");
		role = Objects.requireNonNull(role, "role");
		indexOwnership = Objects.requireNonNull(indexOwnership, "indexOwnership");
		runtimeState = Objects.requireNonNull(runtimeState, "runtimeState");
	}

	private static String requireNonBlank(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value;
	}
}
