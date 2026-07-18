package com.inqwise.indexer.service.target;

import java.util.Objects;
import java.util.Optional;

import com.inqwise.errors.ErrorTicket;
import com.inqwise.indexer.catalog.targets.CreateTargetIndexerRequest;
import com.inqwise.indexer.catalog.targets.CreateTargetRequest;
import com.inqwise.indexer.catalog.targets.RecoverTargetProvisioningRequest;
import com.inqwise.indexer.catalog.targets.TargetCatalogEntry;
import com.inqwise.indexer.catalog.targets.TargetCatalogReader;
import com.inqwise.indexer.catalog.targets.TargetManagementService;
import com.inqwise.indexer.provisioning.GeneratedIndexerResources;
import com.inqwise.indexer.provisioning.IndexerResourceNameGenerator;
import com.inqwise.indexer.service.IndexerErrors;

import io.vertx.core.Future;

public final class TargetCatalogServiceImpl implements TargetCatalogService {
	private final TargetCatalogReader reader;
	private final TargetManagementService management;

	public TargetCatalogServiceImpl(
		TargetCatalogReader reader,
		TargetManagementService management
	) {
		this.reader = Objects.requireNonNull(reader, "reader");
		this.management = Objects.requireNonNull(management, "management");
	}

	@Override
	public Future<TargetListResult> list(TargetQuery request) {
		return invoke(() -> reader.list(request == null
			? TargetQuery.builder().build().toCatalogQuery()
			: request.toCatalogQuery()).map(entries -> TargetListResult.builder()
				.withTargets(entries.stream()
					.map(entry -> TargetView.builder().withCatalogEntry(entry).build())
					.toList())
				.build()));
	}

	@Override
	public Future<TargetResult> get(TargetGetRequest request) {
		return invoke(() -> {
			TargetGetRequest required = required(request, "Request is required");
			if (required.getId() == null && blank(required.getUid())) {
				throw IndexerErrors.invalidRequest("Target id or uid is required");
			}
			if (required.getId() != null && !blank(required.getUid())) {
				throw IndexerErrors.invalidRequest("Only one target lookup key is allowed");
			}
			return (required.getId() == null
				? reader.findByUid(required.getUid())
				: reader.findById(required.getId()))
				.map(this::requiredTarget);
		});
	}

	@Override
	public Future<TargetResult> create(TargetCreateRequest request) {
		return invoke(() -> {
			TargetCreateRequest required = required(request, "Request is required");
			if (blank(required.getTargetName())) {
				throw IndexerErrors.invalidRequest("Target name is required");
			}
			CreateTargetIndexerRequest createIndexer = null;
			if (required.getInitialPublicationMode() != null) {
				GeneratedIndexerResources resources = IndexerResourceNameGenerator.forTarget(
					required.getTargetName()
				);
				createIndexer = CreateTargetIndexerRequest.builder()
					.withPrefix(resources.prefix())
					.withIndexName(resources.indexName())
					.withQueueName(resources.queueName())
					.withInitialPublicationMode(required.getInitialPublicationMode())
					.build();
			}
			return management.createTarget(CreateTargetRequest.builder()
				.withTargetName(required.getTargetName())
				.withTimestamp(required.getTimestamp())
				.withCreateIndexer(createIndexer)
				.build()).compose(created -> load(created.targetId()));
		});
	}

	@Override
	public Future<TargetResult> recoverProvisioning(TargetVersionRequest request) {
		return invoke(() -> {
			TargetVersionRequest required = required(request, "Request is required");
			if (required.getTargetId() == null) {
				throw IndexerErrors.invalidRequest("Target id is required");
			}
			if (required.getExpectedVersion() == null) {
				throw IndexerErrors.invalidRequest("Expected version is required");
			}
			if (required.getExpectedVersion() < 0L) {
				throw IndexerErrors.invalidRequest("Expected version must not be negative");
			}
			return management.recoverProvisioning(RecoverTargetProvisioningRequest.builder()
				.withTargetId(required.getTargetId())
				.withExpectedVersion(required.getExpectedVersion())
				.build()).compose(recovered -> load(recovered.targetId()));
		});
	}

	private Future<TargetResult> load(Integer id) {
		return reader.findById(id).map(this::requiredTarget);
	}

	private TargetResult requiredTarget(Optional<TargetCatalogEntry> found) {
		return found
			.map(entry -> TargetView.builder().withCatalogEntry(entry).build())
			.map(target -> TargetResult.builder().withTarget(target).build())
			.orElseThrow(() -> IndexerErrors.notFound("Target not found"));
	}

	private <T> Future<T> invoke(Operation<T> operation) {
		try {
			return operation.execute()
				.recover(error -> Future.failedFuture(normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(normalize(error));
		}
	}

	private ErrorTicket normalize(Throwable error) {
		if (error instanceof ErrorTicket ticket) {
			return ticket;
		}
		if (error instanceof IllegalArgumentException || error instanceof NullPointerException) {
			return IndexerErrors.invalidRequest(error.getMessage());
		}
		return IndexerErrors.normalize(error);
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private static <T> T required(T value, String message) {
		if (value == null) {
			throw IndexerErrors.invalidRequest(message);
		}
		return value;
	}

	@FunctionalInterface
	private interface Operation<T> {
		Future<T> execute();
	}
}
