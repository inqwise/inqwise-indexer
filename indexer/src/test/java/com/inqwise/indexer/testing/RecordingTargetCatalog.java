package com.inqwise.indexer.testing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.inqwise.indexer.catalog.targets.CreateTargetRequest;
import com.inqwise.indexer.catalog.targets.RecoverTargetProvisioningRequest;
import com.inqwise.indexer.catalog.targets.TargetCatalogEntry;
import com.inqwise.indexer.catalog.targets.TargetCatalogQuery;
import com.inqwise.indexer.catalog.targets.TargetCatalogReader;
import com.inqwise.indexer.catalog.targets.TargetManagementResult;
import com.inqwise.indexer.catalog.targets.TargetManagementService;
import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;

import io.vertx.core.Future;

public final class RecordingTargetCatalog implements TargetCatalogReader, TargetManagementService {
	private TargetCatalogQuery query;
	private CreateTargetRequest created;
	private RecoverTargetProvisioningRequest recovered;

	@Override
	public Future<List<TargetCatalogEntry>> list(TargetCatalogQuery query) {
		this.query = query;
		return Future.succeededFuture(List.of(entry()));
	}

	@Override
	public Future<Optional<TargetCatalogEntry>> findById(Integer id) {
		return Future.succeededFuture(id != null && id == 17
			? Optional.of(entry())
			: Optional.empty());
	}

	@Override
	public Future<Optional<TargetCatalogEntry>> findByUid(String uid) {
		return Future.succeededFuture("target-17".equals(uid)
			? Optional.of(entry())
			: Optional.empty());
	}

	@Override
	public Future<TargetManagementResult> createTarget(CreateTargetRequest request) {
		created = request;
		return Future.succeededFuture(managementResult());
	}

	@Override
	public Future<TargetManagementResult> recoverProvisioning(
		RecoverTargetProvisioningRequest request
	) {
		recovered = request;
		return Future.succeededFuture(managementResult());
	}

	public TargetCatalogQuery query() {
		return query;
	}

	public CreateTargetRequest created() {
		return created;
	}

	public RecoverTargetProvisioningRequest recovered() {
		return recovered;
	}

	private TargetCatalogEntry entry() {
		Instant createdAt = Instant.parse("2026-01-02T00:00:00Z");
		return new TargetCatalogEntry(
			17,
			"target-17",
			"customers",
			"2026-01",
			Instant.parse("2026-01-01T00:00:00Z"),
			Instant.parse("2026-02-01T00:00:00Z"),
			TargetStatus.ACTIVE,
			TargetProvisioningState.READY,
			createdAt,
			createdAt,
			4L
		);
	}

	private TargetManagementResult managementResult() {
		return new TargetManagementResult(
			17,
			"customers",
			TargetStatus.ACTIVE,
			TargetProvisioningState.READY,
			4L
		);
	}
}
