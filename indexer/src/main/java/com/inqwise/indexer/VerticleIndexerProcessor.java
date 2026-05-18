package com.inqwise.indexer;

import java.util.Objects;
import java.util.function.Supplier;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class VerticleIndexerProcessor implements IndexerProcessor {
	private final Vertx vertx;
	private final Supplier<IndexerProcessorVerticle> verticleFactory;
	private String deploymentId;

	public VerticleIndexerProcessor(
		Vertx vertx,
		Supplier<IndexerProcessorVerticle> verticleFactory
	) {
		this.vertx = Objects.requireNonNull(vertx, "vertx");
		this.verticleFactory = Objects.requireNonNull(verticleFactory, "verticleFactory");
	}

	@Override
	public synchronized Future<Void> open() {
		if (deploymentId != null) {
			return Future.succeededFuture();
		}

		return vertx.deployVerticle(verticleFactory.get())
			.onSuccess(id -> deploymentId = id)
			.mapEmpty();
	}

	@Override
	public synchronized Future<Void> close() {
		if (deploymentId == null) {
			return Future.succeededFuture();
		}

		String closing = deploymentId;
		deploymentId = null;
		return vertx.undeploy(closing);
	}
}
