package com.inqwise.indexer.service.document;

import java.util.Objects;

import com.inqwise.errors.ErrorTicket;
import com.inqwise.indexer.documents.DocumentQueryEngine;
import com.inqwise.indexer.service.IndexerErrors;

import io.vertx.core.Future;

public final class DocumentQueryServiceImpl implements DocumentQueryService {
	private final DocumentQueryEngine engine;

	public DocumentQueryServiceImpl(DocumentQueryEngine engine) {
		this.engine = Objects.requireNonNull(engine, "engine");
	}

	@Override
	public Future<DocumentSearchResult> search(DocumentSearchRequest request) {
		try {
			if (request == null) {
				throw IndexerErrors.invalidRequest("Request is required");
			}
			return engine.query(request.toDomainQuery())
				.map(result -> DocumentSearchResult.builder().withResult(result).build())
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
}
