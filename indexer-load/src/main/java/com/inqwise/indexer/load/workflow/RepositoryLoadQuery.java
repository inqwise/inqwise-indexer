package com.inqwise.indexer.load.workflow;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.api.LoadQuery;
import com.inqwise.indexer.load.repository.IndexerLoadRepository;

import io.vertx.core.Future;

public final class RepositoryLoadQuery implements LoadQuery {
	private final IndexerLoadRepository repository;

	public RepositoryLoadQuery(IndexerLoadRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	@Override
	public Future<List<IndexerLoadRecord>> list(int max) {
		return repository.list(max);
	}
}
