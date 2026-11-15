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
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.metadata.ReplacePublishedIndexer;

import io.vertx.core.Future;

public final class MetadataLoadPublicationRepository
	implements LoadPublicationRepository, LoadCleanupRepository {
	private final DocumentStoreMetadataRepository metadataRepository;

	public MetadataLoadPublicationRepository(DocumentStoreMetadataRepository metadataRepository) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
	}

	@Override
	public Future<LoadPublication> prepare(IndexerLoadRecord load) {
		return getRequiredIndexer(load.indexerId(), "Load indexer not found: ")
			.compose(loadWriter -> resolveCandidate(load)
				.compose(candidate -> validate(load, loadWriter, candidate)
					.compose(valid -> metadataRepository.listPublishedIndexersByTargetId(load.targetId())
						.map(previous -> {
							if (previous.size() > 1) {
								throw new IllegalStateException("Multiple published indexers for target: " + load.targetId());
							}
							return publication(load, loadWriter, candidate, previous);
						}))));
	}

	private LoadPublication publication(
		IndexerLoadRecord load,
		IndexerRecord loadWriter,
		IndexerRecord candidate,
		List<IndexerRecord> previous
	) {
		return LoadPublication.builder()
			.withExpectedLoadVersion(load.version())
			.withLoadWriter(reference(loadWriter))
			.withCandidate(reference(candidate))
			.withOldPublished(previous.isEmpty() ? null : reference(previous.get(0)))
			.build();
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

	@Override
	public Future<Void> publish(LoadPublication publication) {
		LoadIndexerReference candidate = publication.candidate();
		LoadIndexerReference loadWriter = publication.loadWriter();
		LoadIndexerReference oldPublished = publication.oldPublished();
		return getRequiredIndexer(candidate.id(), "Candidate indexer not found: ")
			.compose(current -> {
				if (candidate.version() < Long.MAX_VALUE
					&& current.version() == candidate.version() + 1
					&& current.targetId().equals(candidate.targetId())
					&& current.publicationState() == PublicationState.PUBLISHED
					&& current.role() == IndexerRole.LIVE_WRITER
					&& current.indexOwnership() == IndexResourceOwnership.OWNER) {
					return Future.succeededFuture();
				}
				return metadataRepository.replacePublishedIndexer(ReplacePublishedIndexer.builder()
					.withTargetId(candidate.targetId())
					.withCandidateIndexerId(candidate.id())
					.withExpectedCandidateVersion(candidate.version())
					.withPreviousIndexerId(oldPublished == null ? null : oldPublished.id())
					.withExpectedPreviousVersion(oldPublished == null ? null : oldPublished.version())
					.withOwnershipSourceIndexerId(
						candidate.id().equals(loadWriter.id()) ? null : loadWriter.id()
					)
					.withExpectedOwnershipSourceVersion(
						candidate.id().equals(loadWriter.id()) ? null : loadWriter.version()
					)
					.build());
			});
	}

	private LoadIndexerReference reference(IndexerRecord indexer) {
		return LoadIndexerReference.builder()
			.withId(indexer.id())
			.withTargetId(indexer.targetId())
			.withVersion(indexer.version())
			.build();
	}
}
