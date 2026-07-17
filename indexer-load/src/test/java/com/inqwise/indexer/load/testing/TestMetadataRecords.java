package com.inqwise.indexer.load.testing;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.publication.PublicationState;

public final class TestMetadataRecords {
	private TestMetadataRecords() {
	}

	public static InsertTarget readyTarget(String prefix, String targetName) {
		return new InsertTarget(
			prefix,
			targetName,
			null,
			null,
			null,
			TargetStatus.ACTIVE,
			TargetProvisioningState.READY
		);
	}

	public static InsertIndexer indexerRecord(
		String prefix,
		Integer targetId,
		String targetName,
		String indexName,
		String queueName,
		IndexerType type,
		IndexerRuntimeState runtimeState,
		PublicationState publicationState,
		MutationState mutationState
	) {
		return indexerRecord(
			prefix,
			targetId,
			targetName,
			indexName,
			queueName,
			type,
			IndexerRole.LIVE_WRITER,
			IndexResourceOwnership.OWNER,
			IndexerStatus.AVAILABLE,
			IndexerProvisioningState.READY,
			runtimeState,
			publicationState,
			mutationState
		);
	}

	public static InsertIndexer indexerRecord(
		String prefix,
		Integer targetId,
		String targetName,
		String indexName,
		String queueName,
		IndexerType type,
		IndexerRole role,
		IndexResourceOwnership indexOwnership,
		IndexerRuntimeState runtimeState,
		PublicationState publicationState,
		MutationState mutationState
	) {
		return indexerRecord(
			prefix,
			targetId,
			targetName,
			indexName,
			queueName,
			type,
			role,
			indexOwnership,
			IndexerStatus.AVAILABLE,
			IndexerProvisioningState.READY,
			runtimeState,
			publicationState,
			mutationState
		);
	}

	public static InsertIndexer indexerRecord(
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
		MutationState mutationState
	) {
		return new InsertIndexer(
			prefix,
			targetId,
			targetName,
			indexName,
			queueName,
			type,
			role,
			indexOwnership,
			status,
			provisioningState,
			runtimeState,
			publicationState,
			mutationState
		);
	}
}
