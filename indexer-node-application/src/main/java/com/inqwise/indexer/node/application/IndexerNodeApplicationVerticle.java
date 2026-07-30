package com.inqwise.indexer.node.application;

import com.inqwise.indexer.node.IndexerNode;
import com.inqwise.indexer.node.IndexerNodeOptions;
import com.inqwise.indexer.node.application.monitoring.MicrometerIndexerEventPublisher;
import com.inqwise.indexer.runtime.IndexerEventPublisher;
import com.inqwise.indexer.web.IndexerWebOptions;
import com.inqwise.indexer.web.IndexerWebVerticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.backends.BackendRegistries;

public final class IndexerNodeApplicationVerticle extends AbstractVerticle {
	public static final String WEB_CONFIG = "web";

	private IndexerNode node;
	private IndexerWebVerticle web;

	@Override
	public void start(Promise<Void> startPromise) {
		JsonObject applicationConfig = config();
		MicrometerIndexerEventPublisher operationalMetrics = null;
		if (BackendRegistries.getDefaultNow() != null) {
			operationalMetrics = new MicrometerIndexerEventPublisher(
				BackendRegistries.getDefaultNow()
			);
		}
		IndexerEventPublisher eventPublisher = operationalMetrics == null
			? IndexerEventPublisher.NOOP
			: operationalMetrics;
		node = IndexerNode.create(
			vertx,
			new IndexerNodeOptions(applicationConfig),
			null,
			eventPublisher,
			operationalMetrics
		);
		web = new IndexerWebVerticle(IndexerWebOptions.from(
			applicationConfig.getJsonObject(WEB_CONFIG, new JsonObject())
		));

		node.start()
			.compose(ignored -> vertx.deployVerticle(web))
			.map((Void) null)
			.recover(this::rollbackStartup)
			.onComplete(startPromise);
	}

	@Override
	public void stop(Promise<Void> stopPromise) {
		stopNode().onComplete(stopPromise);
	}

	int actualWebPort() {
		return web == null ? -1 : web.actualPort();
	}

	private Future<Void> stopNode() {
		return node == null ? Future.succeededFuture() : node.stop();
	}

	private Future<Void> rollbackStartup(Throwable startupError) {
		return stopNode().transform(stopResult -> {
			if (stopResult.failed()) {
				startupError.addSuppressed(stopResult.cause());
			}
			return Future.failedFuture(startupError);
		});
	}
}
