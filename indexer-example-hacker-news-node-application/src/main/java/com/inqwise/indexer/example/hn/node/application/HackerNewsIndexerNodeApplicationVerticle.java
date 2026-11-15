package com.inqwise.indexer.example.hn.node.application;

import java.util.Objects;
import java.util.function.Supplier;

import com.inqwise.indexer.example.hn.HackerNewsApplicationVerticle;
import com.inqwise.indexer.node.application.IndexerNodeApplicationVerticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Deployable;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;

public final class HackerNewsIndexerNodeApplicationVerticle extends AbstractVerticle {
	private static final String CLIENT_CONFIG_KEY = "client";
	private final Supplier<? extends Verticle> nodeApplication;
	private final Supplier<? extends Verticle> ingestionApplication;

	public HackerNewsIndexerNodeApplicationVerticle() {
		this(IndexerNodeApplicationVerticle::new, HackerNewsApplicationVerticle::new);
	}

	HackerNewsIndexerNodeApplicationVerticle(
		Supplier<? extends Verticle> nodeApplication,
		Supplier<? extends Verticle> ingestionApplication
	) {
		this.nodeApplication = Objects.requireNonNull(nodeApplication, "nodeApplication");
		this.ingestionApplication = Objects.requireNonNull(
			ingestionApplication,
			"ingestionApplication"
		);
	}

	@Override
	public void start(Promise<Void> startPromise) {
		JsonObject rootConfig = config();
		JsonObject nodeConfig = rootConfig.copy();
		nodeConfig.remove(CLIENT_CONFIG_KEY);
		DeploymentOptions nodeDeployment = new DeploymentOptions()
			.setConfig(nodeConfig);
		DeploymentOptions clientDeployment = new DeploymentOptions()
			.setConfig(rootConfig.getJsonObject(CLIENT_CONFIG_KEY, new JsonObject()).copy());
		vertx.deployVerticle(nodeWithIngestion(clientDeployment), nodeDeployment)
			.map((Void) null)
			.onComplete(startPromise);
	}

	private Deployable nodeWithIngestion(DeploymentOptions clientDeployment) {
		Verticle node = nodeApplication.get();
		return new Deployable() {
			@Override
			public Future<?> deploy(Context context) throws Exception {
				// Ingestion is a child of the node deployment, so Vert.x awaits its
				// shutdown before invoking the node's undeploy lifecycle.
				return node.deploy(context).compose(ignored -> vertx.deployVerticle(
					ingestionApplication.get(), clientDeployment
				).recover(error -> stopNode(context).transform(stopped -> {
					if (stopped.failed()) {
						error.addSuppressed(stopped.cause());
					}
					return Future.failedFuture(error);
				})));
			}

			@Override
			public Future<?> undeploy(Context context) {
				return stopNode(context);
			}

			private Future<Void> stopNode(Context context) {
				try {
					return node.undeploy(context).mapEmpty();
				} catch (Exception error) {
					return Future.failedFuture(error);
				}
			}
		};
	}

}
