package com.inqwise.indexer.service.indexer;

import java.util.Objects;
import java.util.Optional;

import com.inqwise.errors.ErrorTicket;
import com.inqwise.indexer.catalog.indexers.IndexerCatalogEntry;
import com.inqwise.indexer.catalog.indexers.IndexerCatalogReader;
import com.inqwise.indexer.catalog.indexers.IndexerManagementService;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeStateRequest;
import com.inqwise.indexer.service.IndexerErrors;

import io.vertx.core.Future;

public final class IndexerCatalogServiceImpl implements IndexerCatalogService {
	private final IndexerCatalogReader reader;
	private final IndexerManagementService management;

	public IndexerCatalogServiceImpl(
		IndexerCatalogReader reader,
		IndexerManagementService management
	) {
		this.reader = Objects.requireNonNull(reader, "reader");
		this.management = Objects.requireNonNull(management, "management");
	}

	@Override
	public Future<IndexerListResult> list(IndexerQuery request) {
		return invoke(() -> reader.list(request == null
			? new IndexerQuery().toCatalogQuery()
			: request.toCatalogQuery()).map(entries -> new IndexerListResult().setIndexers(
				entries.stream().map(IndexerView::from).toList()
			)));
	}

	@Override
	public Future<IndexerResult> get(IndexerGetRequest request) {
		return invoke(() -> {
			IndexerGetRequest required = required(request, "Request is required");
			if (required.getId() == null && blank(required.getUid())) {
				throw IndexerErrors.invalidRequest("Indexer id or uid is required");
			}
			if (required.getId() != null && !blank(required.getUid())) {
				throw IndexerErrors.invalidRequest("Only one indexer lookup key is allowed");
			}
			return (required.getId() == null
				? reader.findByUid(required.getUid())
				: reader.findById(required.getId()))
				.map(this::requiredIndexer);
		});
	}

	@Override
	public Future<IndexerResult> activate(IndexerVersionRequest request) {
		return changeRuntimeState(request, true);
	}

	@Override
	public Future<IndexerResult> deactivate(IndexerVersionRequest request) {
		return changeRuntimeState(request, false);
	}

	private Future<IndexerResult> changeRuntimeState(
		IndexerVersionRequest request,
		boolean activate
	) {
		return invoke(() -> {
			IndexerVersionRequest required = required(request, "Request is required");
			if (required.getIndexerId() == null) {
				throw IndexerErrors.invalidRequest("Indexer id is required");
			}
			if (required.getExpectedVersion() == null) {
				throw IndexerErrors.invalidRequest("Expected version is required");
			}
			if (required.getExpectedVersion() < 0L) {
				throw IndexerErrors.invalidRequest("Expected version must not be negative");
			}
			IndexerRuntimeStateRequest domainRequest = new IndexerRuntimeStateRequest(
				required.getIndexerId(),
				required.getExpectedVersion()
			);
			return (activate
				? management.activate(domainRequest)
				: management.deactivate(domainRequest))
				.compose(changed -> load(changed.indexerId()));
		});
	}

	private Future<IndexerResult> load(Integer id) {
		return reader.findById(id).map(this::requiredIndexer);
	}

	private IndexerResult requiredIndexer(Optional<IndexerCatalogEntry> found) {
		return found
			.map(IndexerView::from)
			.map(indexer -> new IndexerResult().setIndexer(indexer))
			.orElseThrow(() -> IndexerErrors.notFound("Indexer not found"));
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
