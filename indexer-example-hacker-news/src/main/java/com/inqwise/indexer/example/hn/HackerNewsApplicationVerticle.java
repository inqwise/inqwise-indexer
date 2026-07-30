package com.inqwise.indexer.example.hn;

import com.inqwise.indexer.service.action.TargetActionServices;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.client.WebClient;

public final class HackerNewsApplicationVerticle extends AbstractVerticle {
	private HackerNewsIngestionService ingestion;
	private WebClient webClient;

	@Override
	public void start(Promise<Void> startPromise) {
		HackerNewsOptions hnOptions = HackerNewsOptions.from(config());
		webClient = WebClient.create(vertx);
		ingestion = new HackerNewsIngestionService(
			vertx,
			new VertxHackerNewsClient(
				webClient,
				hnOptions.baseUri(),
				hnOptions.requestIdleTimeout()
			),
			TargetActionServices.proxy(vertx),
			new HackerNewsDocumentProjector(),
			hnOptions
		);
		ingestion.start().onComplete(startPromise);
	}

	@Override
	public void stop(Promise<Void> stopPromise) {
		stopComponents().onComplete(stopPromise);
	}

	private Future<Void> stopComponents() {
		return (ingestion == null
			? Future.<Void>succeededFuture()
			: ingestion.stop()).onComplete(ignored -> {
			if (webClient != null) {
				webClient.close();
				webClient = null;
			}
		});
	}
}
