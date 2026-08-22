package com.inqwise.indexer.example.hn.node.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class HackerNewsIndexerNodeApplicationVerticleTest {
	@Test
	void startsNodeBeforeIngestionAndStopsInReverseOrder(
		Vertx vertx,
		VertxTestContext testContext
	) {
		List<String> lifecycle = new ArrayList<>();
		HackerNewsIndexerNodeApplicationVerticle application =
			new HackerNewsIndexerNodeApplicationVerticle(
				() -> component("node", lifecycle),
				() -> component("ingestion", lifecycle)
			);

		vertx.deployVerticle(
			application,
			new DeploymentOptions().setConfig(new JsonObject()
				.put("scope", "node")
				.put("client", new JsonObject().put("scope", "ingestion")))
		)
			.compose(vertx::undeploy)
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(List.of(
					"node-start:node:false",
					"ingestion-start:ingestion:false",
					"ingestion-stop",
					"node-stop"
				), lifecycle);
				testContext.completeNow();
			})));
	}

	@Test
	void rollsBackNodeWhenIngestionStartupFails(
		Vertx vertx,
		VertxTestContext testContext
	) {
		List<String> lifecycle = new ArrayList<>();
		HackerNewsIndexerNodeApplicationVerticle application =
			new HackerNewsIndexerNodeApplicationVerticle(
				() -> component("node", lifecycle),
				() -> failingComponent(lifecycle)
			);

		vertx.deployVerticle(application)
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("ingestion startup failed", error.getMessage());
				assertEquals(List.of(
					"node-start:null:false",
					"ingestion-start",
					"node-stop"
				), lifecycle);
				testContext.completeNow();
			})));
	}

	private AbstractVerticle component(String name, List<String> lifecycle) {
		return new AbstractVerticle() {
			@Override
			public void start(Promise<Void> startPromise) {
				lifecycle.add(name + "-start:"
					+ config().getString("scope")
					+ ":"
					+ config().containsKey("client"));
				startPromise.complete();
			}

			@Override
			public void stop(Promise<Void> stopPromise) {
				lifecycle.add(name + "-stop");
				stopPromise.complete();
			}
		};
	}

	private AbstractVerticle failingComponent(List<String> lifecycle) {
		return new AbstractVerticle() {
			@Override
			public void start(Promise<Void> startPromise) {
				lifecycle.add("ingestion-start");
				startPromise.fail("ingestion startup failed");
			}
		};
	}
}
