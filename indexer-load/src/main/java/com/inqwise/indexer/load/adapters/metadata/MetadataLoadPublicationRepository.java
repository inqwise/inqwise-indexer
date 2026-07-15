package com.inqwise.indexer.load.adapters.metadata;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.repository.LoadCleanupRepository;
import com.inqwise.indexer.load.repository.LoadIndexerReference;
import com.inqwise.indexer.load.repository.LoadPublication;
import com.inqwise.indexer.load.repository.LoadPublicationRepository;
import com.inqwise.indexer.load.api.IndexerLoadState;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerProvisioningState;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.IndexerStatus;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.ReplacePublishedIndexer;

import io.vertx.core.Future;

public final class MetadataLoadPublicationRepository
	implements LoadPublicationRepository, LoadCleanupRepository {
	private final DocumentStoreMetadataRepository metadataRepository;

	public MetadataLoadPublicationRepository(DocumentStoreMetadataRepository metadataRepository) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
	}

	@Override
	public Future<LoadPublication> publish(IndexerLoadRecord load) {
		return getRequiredIndexer(load.indexerId(), "Load indexer not found: ")
			.compose(loadWriter -> resolveCandidate(load)
				.compose(candidate -> validate(load, loadWriter, candidate)
					.compose(valid -> metadataRepository.listPublishedIndexersByTargetId(load.targetId())
						.compose(previous -> replace(load, loadWriter, candidate, previous)
							.map(ignored -> new LoadPublication(
								reference(loadWriter),
								reference(candidate),
								previous.isEmpty() ? null : reference(previous.get(0))
							))))));
	}

	@Override
	public Future<Optional<LoadIndexerReference>> getIndexer(Integer indexerId) {
		return metadataRepository.getIndexerById(indexerId)
			.map(found -> found.map(this::reference));
	}

	private Future<IndexerRecord> resolveCandidate(IndexerLoadRecord load) {
		Integer candidateId = load.liveIndexerId() == null
			? load.indexerId()
			: load.liveIndexerId();
		return getRequiredIndexer(candidateId, "Candidate indexer not found: ");
	}

	private Future<IndexerRecord> getRequiredIndexer(Integer indexerId, String messagePrefix) {
		return metadataRepository.getIndexerById(indexerId)
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture(messagePrefix + indexerId)));
	}

	private Future<Void> validate(
		IndexerLoadRecord load,
		IndexerRecord loadWriter,
		IndexerRecord candidate
	) {
		if (!load.targetId().equals(loadWriter.targetId())
			|| !load.targetId().equals(candidate.targetId())) {
			return Future.failedFuture("Load target mismatch: " + load.indexerId());
		}

		if (load.liveIndexerId() != null) {
			if (!candidate.indexName().equals(loadWriter.indexName())) {
				return Future.failedFuture(
					"Linked live writer index mismatch: " + candidate.id()
				);
			}
			if (load.lastBarrierId() == null || load.lastBarrierReachedAt() == null) {
				return Future.failedFuture(
					"Catch-up barrier was not reached for load: " + load.indexerId()
				);
			}
			if (load.state() != IndexerLoadState.CATCH_UP_READY
				&& load.state() != IndexerLoadState.APPROVED) {
				return Future.failedFuture("Load is not publishable: " + load.state());
			}
		} else if (load.state() != IndexerLoadState.HISTORICAL_COMPLETE
			&& load.state() != IndexerLoadState.APPROVED) {
			return Future.failedFuture("Load is not publishable: " + load.state());
		}

		if (load.reviewRequired() && load.approvedAt() == null) {
			return Future.failedFuture(
				"Load publication review is not approved: " + load.indexerId()
			);
		}

		if (candidate.status() != IndexerStatus.AVAILABLE
			|| candidate.provisioningState() != IndexerProvisioningState.READY
			|| candidate.runtimeState() != IndexerRuntimeState.ACTIVE
			|| candidate.mutationState() != MutationState.WRITABLE
			|| candidate.publicationState() != PublicationState.UNPUBLISHED) {
			return Future.failedFuture("Candidate indexer is not publishable: " + candidate.id());
		}

		return Future.succeededFuture();
	}

	private Future<Void> replace(
		IndexerLoadRecord load,
		IndexerRecord loadWriter,
		IndexerRecord candidate,
		List<IndexerRecord> previous
	) {
		if (previous.size() > 1) {
			return Future.failedFuture("Multiple published indexers for target: " + load.targetId());
		}

		IndexerRecord oldPublished = previous.isEmpty() ? null : previous.get(0);
		return metadataRepository.replacePublishedIndexer(new ReplacePublishedIndexer(
			load.targetId(),
			candidate.id(),
			candidate.version(),
			oldPublished == null ? null : oldPublished.id(),
			oldPublished == null ? null : oldPublished.version(),
			load.liveIndexerId() == null ? null : loadWriter.id(),
			load.liveIndexerId() == null ? null : loadWriter.version()
		));
	}

	private LoadIndexerReference reference(IndexerRecord indexer) {
		return new LoadIndexerReference(
			indexer.id(),
			indexer.targetId(),
			indexer.version()
		);
	}
}
