package com.inqwise.indexer.load.testing;

import java.time.Instant;

import com.inqwise.indexer.load.api.ApproveLoadPublicationRequest;
import com.inqwise.indexer.load.api.CancelLoadRequest;
import com.inqwise.indexer.load.api.CreateLoadRequest;
import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.api.IndexerLoadState;
import com.inqwise.indexer.load.api.LiveWriterPolicy;
import com.inqwise.indexer.load.api.LoadManagementService;
import com.inqwise.indexer.load.api.RecoverCreatedLoadRequest;
import com.inqwise.indexer.load.api.StartLoadRequest;

import io.vertx.core.Future;

public final class RecordingLoadManagementService implements LoadManagementService {
	private CreateLoadRequest created;
	private StartLoadRequest started;
	private RecoverCreatedLoadRequest recovered;
	private ApproveLoadPublicationRequest approved;
	private CancelLoadRequest cancelled;

	@Override
	public Future<IndexerLoadRecord> create(CreateLoadRequest request) {
		created = request;
		return Future.succeededFuture(record());
	}

	@Override
	public Future<IndexerLoadRecord> start(StartLoadRequest request) {
		started = request;
		return Future.succeededFuture(record());
	}

	@Override
	public Future<IndexerLoadRecord> recoverCreated(RecoverCreatedLoadRequest request) {
		recovered = request;
		return Future.succeededFuture(record());
	}

	@Override
	public Future<IndexerLoadRecord> approvePublication(ApproveLoadPublicationRequest request) {
		approved = request;
		return Future.succeededFuture(record());
	}

	@Override
	public Future<Void> cancel(CancelLoadRequest request) {
		cancelled = request;
		return Future.succeededFuture();
	}

	public CreateLoadRequest created() {
		return created;
	}

	public CancelLoadRequest cancelled() {
		return cancelled;
	}

	public StartLoadRequest started() {
		return started;
	}

	public RecoverCreatedLoadRequest recovered() {
		return recovered;
	}

	public ApproveLoadPublicationRequest approved() {
		return approved;
	}

	private IndexerLoadRecord record() {
		Instant createdAt = Instant.parse("2026-01-02T00:00:00Z");
		return new IndexerLoadRecord(
			91, 11, 92, LiveWriterPolicy.CREATE_IMMEDIATELY, "archive",
			IndexerLoadState.CREATED, null, null, null, null, null, null,
			true, null, null, null, null, null, null, null, null,
			createdAt, createdAt, 4
		);
	}
}
