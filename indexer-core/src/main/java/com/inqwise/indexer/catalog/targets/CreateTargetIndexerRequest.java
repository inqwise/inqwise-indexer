package com.inqwise.indexer.catalog.targets;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;

public record CreateTargetIndexerRequest(
	String prefix,
	String indexName,
	String queueName,
	IndexerRole role,
	IndexResourceOwnership indexOwnership,
	IndexerRuntimeState runtimeState,
	InitialPublicationMode initialPublicationMode
) {
	public CreateTargetIndexerRequest {
		Objects.requireNonNull(indexName, "indexName");
		role = role == null ? IndexerRole.LIVE_WRITER : role;
		indexOwnership = indexOwnership == null ? IndexResourceOwnership.OWNER : indexOwnership;
		runtimeState = runtimeState == null ? IndexerRuntimeState.NON_ACTIVE : runtimeState;
		Objects.requireNonNull(initialPublicationMode, "initialPublicationMode");
	}
}
