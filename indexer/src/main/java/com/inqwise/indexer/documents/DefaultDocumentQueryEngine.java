package com.inqwise.indexer.documents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.publication.PublishedIndex;
import com.inqwise.indexer.publication.PublishedIndexQuery;
import com.inqwise.indexer.publication.PublishedIndexResolver;

import io.vertx.core.Future;

public final class DefaultDocumentQueryEngine implements DocumentQueryEngine {
	private final PublishedIndexResolver publishedIndexes;
	private final IndexerDocumentQueryProvider provider;

	public DefaultDocumentQueryEngine(
		PublishedIndexResolver publishedIndexes,
		IndexerDocumentQueryProvider provider
	) {
		this.publishedIndexes = Objects.requireNonNull(publishedIndexes, "publishedIndexes");
		this.provider = Objects.requireNonNull(provider, "provider");
	}

	@Override
	public Future<DocumentQueryResult> query(DocumentQuery query) {
		Objects.requireNonNull(query, "query");
		return publishedIndexes.resolvePublishedIndexes(PublishedIndexQuery.builder()
			.withTargetName(query.targetName())
			.withFromInclusive(query.fromInclusive())
			.withToExclusive(query.toExclusive())
			.build()).compose(indexes -> queryIndexes(query, indexes));
	}

	private Future<DocumentQueryResult> queryIndexes(
		DocumentQuery query,
		List<PublishedIndex> indexes
	) {
		if (indexes.isEmpty()) {
			return Future.succeededFuture(result(query, List.of(), false, 0));
		}

		int window = query.offset() + query.limit() + 1;
		List<Future<DocumentIndexQueryResult>> queries = indexes.stream()
			.map(index -> provider.query(DocumentIndexQuery.builder()
				.withIndexName(index.indexName())
				.withQueryText(query.queryText())
				.withLimit(window)
				.build()))
			.toList();
		return Future.join(queries).map(ignored -> merge(query, indexes, queries));
	}

	private DocumentQueryResult merge(
		DocumentQuery query,
		List<PublishedIndex> indexes,
		List<Future<DocumentIndexQueryResult>> queries
	) {
		List<DocumentHit> candidates = new ArrayList<>();
		boolean providerHasMore = false;
		for (int index = 0; index < indexes.size(); index++) {
			PublishedIndex published = indexes.get(index);
			DocumentIndexQueryResult queried = queries.get(index).result();
			providerHasMore |= queried.hasMore();
			for (DocumentIndexHit hit : queried.hits()) {
				candidates.add(DocumentHit.builder()
					.withIndexerId(published.indexerId())
					.withTargetId(published.targetId())
					.withUid(hit.uid())
					.withScore(hit.score())
					.withDocument(hit.document())
					.build());
			}
		}

		candidates.sort(Comparator
			.comparingDouble(DocumentHit::score)
			.reversed()
			.thenComparing(DocumentHit::targetId)
			.thenComparing(DocumentHit::indexerId)
			.thenComparing(DocumentHit::uid));
		int from = Math.min(query.offset(), candidates.size());
		int to = Math.min(from + query.limit(), candidates.size());
		boolean hasMore = providerHasMore || candidates.size() > to;
		return result(query, candidates.subList(from, to), hasMore, indexes.size());
	}

	private DocumentQueryResult result(
		DocumentQuery query,
		List<DocumentHit> hits,
		boolean hasMore,
		int publishedIndexCount
	) {
		return DocumentQueryResult.builder()
			.withHits(hits)
			.withOffset(query.offset())
			.withLimit(query.limit())
			.withHasMore(hasMore)
			.withPublishedIndexCount(publishedIndexCount)
			.build();
	}
}
