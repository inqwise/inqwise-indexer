package com.inqwise.indexer.providers;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import com.inqwise.indexer.actions.CatchUpBarrierActionItem;
import com.inqwise.indexer.actions.CompleteIndexActionItem;
import com.inqwise.indexer.catalog.indexers.IndexerModel;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

class IndexerPluginsTest {
	private final IndexerModel model = IndexerModel.builder()
		.withId(20)
		.withTargetId(10)
		.withTargetName("customers")
		.withIndexName("customers-1")
		.withQueueName("customers-1")
		.build();
	private final IndexerMarkerHandler handler = new IndexerMarkerHandler() {
		@Override
		public Future<Void> complete(IndexerModel model, CompleteIndexActionItem item) {
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> catchUpBarrier(IndexerModel model, CatchUpBarrierActionItem item) {
			return Future.succeededFuture();
		}
	};

	@Test
	void resolvesOnePluginMarkerHandler() {
		IndexerPlugins plugins = new IndexerPlugins(List.of(plugin(handler)));

		assertSame(handler, plugins.markerHandler(model));
	}

	@Test
	void rejectsAmbiguousPluginMarkerHandlers() {
		IndexerPlugins plugins = new IndexerPlugins(List.of(plugin(handler), plugin(handler)));

		assertThrows(IllegalStateException.class, () -> plugins.markerHandler(model));
	}

	private IndexerPlugin plugin(IndexerMarkerHandler markerHandler) {
		return new IndexerPlugin() {
			@Override
			public Optional<IndexerMarkerHandler> markerHandler(IndexerModel model) {
				return Optional.of(markerHandler);
			}
		};
	}
}
