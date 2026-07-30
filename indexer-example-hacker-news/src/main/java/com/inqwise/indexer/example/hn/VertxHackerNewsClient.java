package com.inqwise.indexer.example.hn;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public final class VertxHackerNewsClient implements HackerNewsClient {
	private final WebClient client;
	private final String baseUri;

	private final long requestIdleTimeoutMs;

	public VertxHackerNewsClient(
		WebClient client,
		URI baseUri,
		Duration requestIdleTimeout
	) {
		this.client = Objects.requireNonNull(client, "client");
		String value = Objects.requireNonNull(baseUri, "baseUri").toString();
		this.baseUri = value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
		this.requestIdleTimeoutMs = Objects.requireNonNull(
			requestIdleTimeout,
			"requestIdleTimeout"
		).toMillis();
	}

	@Override
	public Future<HackerNewsUpdates> fetchUpdates() {
		return client.getAbs(baseUri + "/updates.json")
			.idleTimeout(requestIdleTimeoutMs)
			.send()
			.compose(this::requireSuccess)
			.map(response -> HackerNewsUpdates.fromJson(response.bodyAsJsonObject()));
	}

	@Override
	public Future<Optional<HackerNewsItem>> fetchItem(long id) {
		if (id < 1) {
			return Future.failedFuture("HN item id must be positive");
		}
		return client.getAbs(baseUri + "/item/" + id + ".json")
			.idleTimeout(requestIdleTimeoutMs)
			.send()
			.compose(this::requireSuccess)
			.map(response -> response.body() == null || "null".equals(response.bodyAsString())
				? Optional.empty()
				: Optional.of(HackerNewsItem.fromJson(response.bodyAsJsonObject())));
	}

	private Future<HttpResponse<Buffer>> requireSuccess(HttpResponse<Buffer> response) {
		if (response.statusCode() >= 200 && response.statusCode() < 300) {
			return Future.succeededFuture(response);
		}
		return Future.failedFuture(
			"HN API returned HTTP " + response.statusCode()
		);
	}
}
