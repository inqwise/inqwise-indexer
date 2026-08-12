package com.inqwise.indexer.load.service;

import java.util.Objects;

import com.inqwise.indexer.load.api.LoadQuery;

import io.vertx.core.Future;

public final class LoadQueryServiceImpl implements LoadQueryService {
	public static final int DEFAULT_MAX = 50;
	public static final int MAX_ALLOWED = 100;

	private final LoadQuery query;

	public LoadQueryServiceImpl(LoadQuery query) {
		this.query = Objects.requireNonNull(query, "query");
	}

	@Override
	public Future<LoadListResult> list(LoadListRequest request) {
		try {
			int max = request == null || request.getMax() == null
				? DEFAULT_MAX
				: request.getMax();
			if (max < 1 || max > MAX_ALLOWED) {
				throw LoadServiceErrors.invalidRequest("max must be between 1 and " + MAX_ALLOWED);
			}
			return query.list(max)
				.map(records -> LoadListResult.builder()
					.withLoads(records.stream()
						.map(record -> LoadView.builder().withRecord(record).build())
						.toList())
					.build())
				.recover(error -> Future.failedFuture(LoadServiceErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(LoadServiceErrors.normalize(error));
		}
	}
}
