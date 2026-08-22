package com.inqwise.indexer.example.hn.node.application;

import java.util.Objects;
import java.util.function.Supplier;

import com.inqwise.indexer.example.hn.HackerNewsApplicationVerticle;
import com.inqwise.indexer.node.application.IndexerNodeApplicationVerticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;

public final class HackerNewsIndexerNodeApplicationVerticle extends AbstractVerticle {
	private static final String CLIENT_CONFIG_KEY = "client";
	private final Supplier<? extends Verticle> nodeApplication;
	private final Supplier<? extends Verticle> ingestionApplication;
	private String nodeDeploymentId;

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
		vertx.deployVerticle(nodeApplication.get(), nodeDeployment)
			.onSuccess(deploymentId -> nodeDeploymentId = deploymentId)
			.compose(ignored -> vertx.deployVerticle(
				ingestionApplication.get(),
				clientDeployment
			))
			.map((Void) null)
			.recover(this::rollbackStartup)
			.onComplete(startPromise);
	}

	private Future<Void> rollbackStartup(Throwable startupError) {
		return undeployNode().transform(stopResult -> {
			if (stopResult.failed()) {
				startupError.addSuppressed(stopResult.cause());
			}
			return Future.failedFuture(startupError);
		});
	}

	private Future<Void> undeployNode() {
		if (nodeDeploymentId == null) {
			return Future.succeededFuture();
		}
		String deploymentId = nodeDeploymentId;
		nodeDeploymentId = null;
		return vertx.undeploy(deploymentId);
	}
}
