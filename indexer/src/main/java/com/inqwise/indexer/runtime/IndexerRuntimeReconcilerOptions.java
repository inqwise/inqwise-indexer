package com.inqwise.indexer.runtime;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class IndexerRuntimeReconcilerOptions {
	public static final int DEFAULT_MAX_DIRTY_INDEXERS = 10_000;
	public static final long DEFAULT_SAFETY_SYNC_INTERVAL_MS = 300_000L;

	public static final class Keys {
		public static final String MAX_DIRTY_INDEXERS = "max_dirty_indexers";
		public static final String SAFETY_SYNC_INTERVAL_MS = "safety_sync_interval_ms";

		private Keys() {
		}
	}

	private int maxDirtyIndexers = DEFAULT_MAX_DIRTY_INDEXERS;
	private long safetySyncIntervalMs = DEFAULT_SAFETY_SYNC_INTERVAL_MS;

	public IndexerRuntimeReconcilerOptions() {
	}

	public IndexerRuntimeReconcilerOptions(JsonObject json) {
		maxDirtyIndexers = json.getInteger(
			Keys.MAX_DIRTY_INDEXERS,
			DEFAULT_MAX_DIRTY_INDEXERS
		);
		safetySyncIntervalMs = json.getLong(
			Keys.SAFETY_SYNC_INTERVAL_MS,
			DEFAULT_SAFETY_SYNC_INTERVAL_MS
		);
		validate();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.MAX_DIRTY_INDEXERS, maxDirtyIndexers)
			.put(Keys.SAFETY_SYNC_INTERVAL_MS, safetySyncIntervalMs);
	}

	public int getMaxDirtyIndexers() {
		return maxDirtyIndexers;
	}

	public IndexerRuntimeReconcilerOptions setMaxDirtyIndexers(int maxDirtyIndexers) {
		this.maxDirtyIndexers = maxDirtyIndexers;
		return this;
	}

	public long getSafetySyncIntervalMs() {
		return safetySyncIntervalMs;
	}

	public IndexerRuntimeReconcilerOptions setSafetySyncIntervalMs(
		long safetySyncIntervalMs
	) {
		this.safetySyncIntervalMs = safetySyncIntervalMs;
		return this;
	}

	public IndexerRuntimeReconcilerOptions validate() {
		if (maxDirtyIndexers <= 0) {
			throw new IllegalArgumentException("maxDirtyIndexers must be greater than zero");
		}
		if (safetySyncIntervalMs <= 0L) {
			throw new IllegalArgumentException(
				"safetySyncIntervalMs must be greater than zero"
			);
		}
		return this;
	}
}
