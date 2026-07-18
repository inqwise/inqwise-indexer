package com.inqwise.indexer.load.service;

import java.util.Objects;

import com.inqwise.indexer.load.api.ApproveLoadPublicationRequest;
import com.inqwise.indexer.load.api.CancelLoadRequest;
import com.inqwise.indexer.load.api.LoadManagementService;
import com.inqwise.indexer.load.api.RecoverCreatedLoadRequest;
import com.inqwise.indexer.load.api.StartLoadRequest;

import io.vertx.core.Future;

public final class LoadServiceImpl implements LoadService {
	private final LoadManagementService delegate;

	public LoadServiceImpl(LoadManagementService delegate) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
	}

	@Override
	public Future<LoadResult> create(LoadCreateRequest request) {
		return invoke(() -> delegate.create(required(request, "Request is required").toDomainRequest()));
	}

	@Override
	public Future<LoadResult> start(LoadVersionRequest request) {
		return invoke(() -> delegate.start(StartLoadRequest.builder()
			.withIndexerId(indexerId(request))
			.withExpectedVersion(expectedVersion(request))
			.build()));
	}

	@Override
	public Future<LoadResult> recoverCreated(LoadVersionRequest request) {
		return invoke(() -> delegate.recoverCreated(RecoverCreatedLoadRequest.builder()
			.withIndexerId(indexerId(request))
			.withExpectedVersion(expectedVersion(request))
			.build()));
	}

	@Override
	public Future<LoadResult> approvePublication(LoadApprovalRequest request) {
		return invoke(() -> {
			LoadApprovalRequest required = required(request, "Request is required");
			return delegate.approvePublication(ApproveLoadPublicationRequest.builder()
				.withIndexerId(required.getIndexerId())
				.withApprovedAt(required.getApprovedAt())
				.withApprovedBy(required.getApprovedBy())
				.withApprovalReason(required.getApprovalReason())
				.withExpectedVersion(requiredVersion(required.getExpectedVersion()))
				.build());
		});
	}

	@Override
	public Future<Void> cancel(LoadCancelRequest request) {
		try {
			LoadCancelRequest required = required(request, "Request is required");
			if (required.getIndexerId() == null) {
				throw LoadServiceErrors.invalidRequest("Indexer id is required");
			}
			return delegate.cancel(CancelLoadRequest.builder()
				.withIndexerId(required.getIndexerId())
				.withReason(required.getReason())
				.withExpectedVersion(requiredVersion(required.getExpectedVersion()))
				.build()).recover(error -> Future.failedFuture(LoadServiceErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(LoadServiceErrors.normalize(error));
		}
	}

	private Future<LoadResult> invoke(Operation operation) {
		try {
			return operation.execute()
				.map(record -> LoadResult.builder().withRecord(record).build())
				.recover(error -> Future.failedFuture(LoadServiceErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(LoadServiceErrors.normalize(error));
		}
	}

	private Integer indexerId(LoadVersionRequest request) {
		LoadVersionRequest required = required(request, "Request is required");
		if (required.getIndexerId() == null) {
			throw LoadServiceErrors.invalidRequest("Indexer id is required");
		}
		return required.getIndexerId();
	}

	private long expectedVersion(LoadVersionRequest request) {
		return requiredVersion(required(request, "Request is required").getExpectedVersion());
	}

	private long requiredVersion(Long version) {
		if (version == null) {
			throw LoadServiceErrors.invalidRequest("Expected version is required");
		}
		if (version < 0) {
			throw LoadServiceErrors.invalidRequest("Expected version must not be negative");
		}
		return version;
	}

	private <T> T required(T value, String message) {
		if (value == null) {
			throw LoadServiceErrors.invalidRequest(message);
		}
		return value;
	}

	@FunctionalInterface
	private interface Operation {
		Future<com.inqwise.indexer.load.api.IndexerLoadRecord> execute();
	}
}
