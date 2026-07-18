package com.inqwise.indexer.testing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerCatalogEntry;
import com.inqwise.indexer.catalog.indexers.IndexerCatalogQuery;
import com.inqwise.indexer.catalog.indexers.IndexerCatalogReader;
import com.inqwise.indexer.catalog.indexers.IndexerManagementService;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeStateRequest;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeStateResult;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.indexers.MutationState;

import io.vertx.core.Future;

public final class RecordingIndexerCatalog implements IndexerCatalogReader, IndexerManagementService {
	private IndexerCatalogQuery query;
	private IndexerRuntimeStateRequest activated;
	private IndexerRuntimeStateRequest deactivated;
	private IndexerRuntimeState runtimeState = IndexerRuntimeState.NON_ACTIVE;
	private long version = 3L;

	@Override
	public Future<List<IndexerCatalogEntry>> list(IndexerCatalogQuery query) {
		this.query = query;
		return Future.succeededFuture(List.of(entry()));
	}

	@Override
	public Future<Optional<IndexerCatalogEntry>> findById(Integer id) {
		return Future.succeededFuture(id != null && id == 29
			? Optional.of(entry())
			: Optional.empty());
	}

	@Override
	public Future<Optional<IndexerCatalogEntry>> findByUid(String uid) {
		return Future.succeededFuture("indexer-29".equals(uid)
			? Optional.of(entry())
			: Optional.empty());
	}

	@Override
	public Future<IndexerRuntimeStateResult> activate(IndexerRuntimeStateRequest request) {
		activated = request;
		runtimeState = IndexerRuntimeState.ACTIVE;
		version = request.expectedVersion() + 1L;
		return Future.succeededFuture(result());
	}

	@Override
	public Future<IndexerRuntimeStateResult> deactivate(IndexerRuntimeStateRequest request) {
		deactivated = request;
		runtimeState = IndexerRuntimeState.NON_ACTIVE;
		version = request.expectedVersion() + 1L;
		return Future.succeededFuture(result());
	}

	public IndexerCatalogQuery query() {
		return query;
	}

	public IndexerRuntimeStateRequest activated() {
		return activated;
	}

	public IndexerRuntimeStateRequest deactivated() {
		return deactivated;
	}

	private IndexerCatalogEntry entry() {
		Instant createdAt = Instant.parse("2026-01-02T00:00:00Z");
		return new IndexerCatalogEntry(
			29,
			"indexer-29",
			17,
			"customers",
			"customers-2026-01",
			"customers-2026-01-actions",
			IndexerType.INDEX,
			IndexerRole.LIVE_WRITER,
			IndexResourceOwnership.OWNER,
			IndexerStatus.AVAILABLE,
			IndexerProvisioningState.READY,
			runtimeState,
			MutationState.WRITABLE,
			createdAt,
			createdAt,
			version
		);
	}

	private IndexerRuntimeStateResult result() {
		return new IndexerRuntimeStateResult(29, 17, runtimeState, version);
	}
}
