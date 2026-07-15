package com.inqwise.indexer.metadata;

import java.time.Instant;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.indexers.MutationState;

public record IndexerRecord(
	Integer id,
	String prefix,
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
	PublicationState publicationState,
	MutationState mutationState,
	Instant createdAt,
	Instant updatedAt,
	long version
) {
	public String uid() {
		return MetadataUid.toToken(prefix, id);
	}
}
