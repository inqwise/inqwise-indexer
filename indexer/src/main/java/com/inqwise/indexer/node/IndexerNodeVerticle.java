package com.inqwise.indexer.node;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class IndexerNodeVerticle extends AbstractVerticle {
	private IndexerNode node;

	@Override
	public void start(Promise<Void> startPromise) {
		IndexerNodeOptions options = new IndexerNodeOptions(config());
		node = IndexerNode.create(vertx, options);
		node.start().onComplete(startPromise);
	}

	@Override
	public void stop(Promise<Void> stopPromise) {
		Future<Void> stopped = node == null ? Future.succeededFuture() : node.stop();
		stopped.onComplete(stopPromise);
	}
}
