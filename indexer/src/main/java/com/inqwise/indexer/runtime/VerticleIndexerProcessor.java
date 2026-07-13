package com.inqwise.indexer.runtime;

import java.util.Objects;
import java.util.function.Supplier;

import com.inqwise.indexer.runtime.IndexerProcessor;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class VerticleIndexerProcessor implements IndexerProcessor {
	private final Vertx vertx;
	private final Supplier<IndexerProcessorVerticle> verticleFactory;
	private String deploymentId;
	private Future<Void> opening;

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

		if (opening != null) {
			return opening;
		}

		Future<Void> deploying = vertx.deployVerticle(verticleFactory.get())
			.onSuccess(id -> deploymentId = id)
			.mapEmpty();
		opening = deploying;
		deploying.onComplete(ignored -> clearOpening());

		return opening;
	}

	private synchronized void clearOpening() {
		opening = null;
	}

	@Override
	public synchronized Future<Void> close() {
		if (opening != null) {
			return opening.compose(ignored -> close());
		}

		if (deploymentId == null) {
			return Future.succeededFuture();
		}

		String closing = deploymentId;
		deploymentId = null;
		return vertx.undeploy(closing);
	}
}
