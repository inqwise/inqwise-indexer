package com.inqwise.indexer.adapters.local;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.inqwise.indexer.documents.DocumentIndexHit;
import com.inqwise.indexer.documents.DocumentIndexQuery;
import com.inqwise.indexer.documents.DocumentIndexQueryResult;
import com.inqwise.indexer.documents.IndexerDocumentStore;
import com.inqwise.indexer.documents.IndexerDocumentQueryProvider;
import com.inqwise.indexer.provisioning.definitions.IndexDefinition;
import com.inqwise.indexer.provisioning.DocumentIndexNameValidator;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class InMemoryIndexerDocumentStore
	implements IndexerDocumentStore, IndexerDocumentIndexResourceManager,
		IndexerDocumentQueryProvider {
	private final Map<String, Map<String, JsonObject>> indexes = new ConcurrentHashMap<>();
	private final Map<String, IndexDefinition> definitions = new ConcurrentHashMap<>();

	@Override
	public Future<Void> ensure(String indexName, IndexDefinition definition) {
		DocumentIndexNameValidator.requireConcrete(indexName);
		indexes.computeIfAbsent(indexName, ignored -> new ConcurrentHashMap<>());
		IndexDefinition current = definitions.putIfAbsent(indexName, definition);
		if (current == null || current.equals(definition)) {
			return Future.succeededFuture();
		}

		return Future.failedFuture("Document index resource already exists with different definition: " + indexName);
	}

	@Override
	public Future<Void> put(String indexName, String uid, JsonObject document) {
		indexes.computeIfAbsent(indexName, ignored -> new ConcurrentHashMap<>()).put(uid, document.copy());
		return Future.succeededFuture();
	}

	@Override
	public Future<Void> remove(String indexName, String uid) {
		Map<String, JsonObject> index = indexes.get(indexName);
		if (index != null) {
			index.remove(uid);
		}
		return Future.succeededFuture();
	}

	@Override
	public Future<Void> drop(String indexName) {
		indexes.remove(indexName);
		definitions.remove(indexName);
		return Future.succeededFuture();
	}

	@Override
	public Future<DocumentIndexQueryResult> query(DocumentIndexQuery query) {
		Objects.requireNonNull(query, "query");
		Map<String, JsonObject> index = indexes.get(query.indexName());
		if (index == null) {
			return Future.succeededFuture(DocumentIndexQueryResult.builder().build());
		}

		List<String> terms = terms(query.queryText());
		List<DocumentIndexHit> matching = index.entrySet().stream()
			.map(entry -> hit(entry.getKey(), entry.getValue(), terms))
			.filter(Objects::nonNull)
			.sorted(Comparator
				.comparingDouble(DocumentIndexHit::score)
				.reversed()
				.thenComparing(DocumentIndexHit::uid))
			.toList();
		int resultSize = Math.min(matching.size(), query.limit());
		return Future.succeededFuture(DocumentIndexQueryResult.builder()
			.withHits(matching.subList(0, resultSize))
			.withHasMore(matching.size() > resultSize)
			.build());
	}

	@Override
	public Future<Void> delete(String indexName) {
		return drop(DocumentIndexNameValidator.requireConcrete(indexName));
	}

	public JsonObject get(String indexName, String uid) {
		Map<String, JsonObject> index = indexes.get(indexName);
		JsonObject document = index == null ? null : index.get(uid);
		return document == null ? null : document.copy();
	}

	private static DocumentIndexHit hit(
		String uid,
		JsonObject document,
		List<String> terms
	) {
		String searchable = (uid + " " + document.encode()).toLowerCase(Locale.ROOT);
		double score = 1.0d;
		for (String term : terms) {
			int occurrences = occurrences(searchable, term);
			if (occurrences == 0) {
				return null;
			}
			score += occurrences;
		}
		return DocumentIndexHit.builder()
			.withUid(uid)
			.withScore(score)
			.withDocument(document)
			.build();
	}

	private static List<String> terms(String queryText) {
		return queryText.toLowerCase(Locale.ROOT).lines()
			.flatMap(line -> List.of(line.split("\\s+")).stream())
			.filter(term -> !term.isBlank())
			.distinct()
			.toList();
	}

	private static int occurrences(String value, String term) {
		int count = 0;
		int offset = 0;
		while ((offset = value.indexOf(term, offset)) >= 0) {
			count++;
			offset += term.length();
		}
		return count;
	}
}
