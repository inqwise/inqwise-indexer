package com.inqwise.indexer;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class InMemoryIndexerRepository implements IndexerRepository {
	private final AtomicInteger idSequence = new AtomicInteger();
	private final Map<Integer, IndexerModel> modelsById = new ConcurrentHashMap<>();

	@Override
	public Future<Integer> save(IndexerModel model) {
		IndexerModel stored = model.getId() == null
			? copyWithId(model, idSequence.incrementAndGet())
			: copy(model);

		idSequence.updateAndGet(current -> Math.max(current, stored.getId()));
		modelsById.put(stored.getId(), stored);
		return Future.succeededFuture(stored.getId());
	}

	@Override
	public Future<Optional<IndexerModel>> get(Integer id) {
		return Future.succeededFuture(Optional.ofNullable(modelsById.get(id)).map(this::copy));
	}

	@Override
	public Future<List<IndexerModel>> getByTargetId(Integer targetId) {
		return Future.succeededFuture(modelsById.values().stream()
			.filter(model -> targetId.equals(model.getTargetId()))
			.sorted(Comparator.comparing(IndexerModel::getId))
			.map(this::copy)
			.toList());
	}

	@Override
	public Future<List<IndexerModel>> list() {
		return Future.succeededFuture(modelsById.values().stream()
			.sorted(Comparator.comparing(IndexerModel::getId))
			.map(this::copy)
			.toList());
	}

	@Override
	public Future<Optional<IndexerModel>> updateStatus(Integer id, IndexerStatus status) {
		IndexerModel updated = modelsById.computeIfPresent(id, (ignored, existing) -> {
			long version = existing.getStatus() == status
				? existing.getVersion()
				: existing.getVersion() + 1;

			return new IndexerModel(existing.toJson()
				.put("status", status.name())
				.put("version", version));
		});

		return Future.succeededFuture(Optional.ofNullable(updated).map(this::copy));
	}

	@Override
	public Future<Boolean> delete(Integer id) {
		return Future.succeededFuture(modelsById.remove(id) != null);
	}

	private IndexerModel copy(IndexerModel model) {
		return new IndexerModel(model.toJson());
	}

	private IndexerModel copyWithId(IndexerModel model, Integer id) {
		JsonObject json = model.toJson().put("id", id);
		return new IndexerModel(json);
	}
}
