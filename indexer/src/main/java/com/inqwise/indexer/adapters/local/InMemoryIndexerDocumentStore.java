package com.inqwise.indexer.adapters.local;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.inqwise.indexer.documents.IndexerDocumentStore;
import com.inqwise.indexer.provisioning.definitions.IndexDefinition;
import com.inqwise.indexer.provisioning.DocumentIndexNameValidator;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class InMemoryIndexerDocumentStore
	implements IndexerDocumentStore, IndexerDocumentIndexResourceManager {
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
	public Future<Void> delete(String indexName) {
		return drop(DocumentIndexNameValidator.requireConcrete(indexName));
	}

	public JsonObject get(String indexName, String uid) {
		Map<String, JsonObject> index = indexes.get(indexName);
		JsonObject document = index == null ? null : index.get(uid);
		return document == null ? null : document.copy();
	}

	public Map<String, JsonObject> documents(String indexName) {
		DocumentIndexNameValidator.requireConcrete(indexName);
		Map<String, JsonObject> index = indexes.get(indexName);
		if (index == null || index.isEmpty()) {
			return Map.of();
		}
		Map<String, JsonObject> snapshot = new LinkedHashMap<>();
		index.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> snapshot.put(entry.getKey(), entry.getValue().copy()));
		return Map.copyOf(snapshot);
	}
}
