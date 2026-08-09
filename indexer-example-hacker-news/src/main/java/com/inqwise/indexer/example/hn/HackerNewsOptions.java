package com.inqwise.indexer.example.hn;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

import io.vertx.core.json.JsonObject;

public record HackerNewsOptions(
	URI baseUri,
	Duration pollInterval,
	int maxChangesPerPoll,
	int requestConcurrency,
	Duration requestIdleTimeout,
	int actionBatchSize
) {
	public static final String CONFIG_KEY = "hacker_news";
	public static final String DEFAULT_BASE_URI = "https://hacker-news.firebaseio.com/v0";
	public HackerNewsOptions {
		Objects.requireNonNull(baseUri, "baseUri");
		Objects.requireNonNull(pollInterval, "pollInterval");
		Objects.requireNonNull(requestIdleTimeout, "requestIdleTimeout");
		if (!"http".equals(baseUri.getScheme()) && !"https".equals(baseUri.getScheme())) {
			throw new IllegalArgumentException("baseUri must use http or https");
		}
		if (pollInterval.isNegative() || pollInterval.isZero()) {
			throw new IllegalArgumentException("pollInterval must be positive");
		}
		if (maxChangesPerPoll < 1) {
			throw new IllegalArgumentException("maxChangesPerPoll must be positive");
		}
		if (requestConcurrency < 1) {
			throw new IllegalArgumentException("requestConcurrency must be positive");
		}
		if (requestIdleTimeout.isNegative() || requestIdleTimeout.isZero()) {
			throw new IllegalArgumentException("requestIdleTimeout must be positive");
		}
		if (actionBatchSize < 1 || actionBatchSize > 1000) {
			throw new IllegalArgumentException("actionBatchSize must be between 1 and 1000");
		}
	}

	public static HackerNewsOptions from(JsonObject rootConfig) {
		JsonObject config = rootConfig == null
			? new JsonObject()
			: rootConfig.getJsonObject(CONFIG_KEY, new JsonObject());
		return builder()
			.withBaseUri(URI.create(config.getString("base_uri", DEFAULT_BASE_URI)))
			.withPollInterval(Duration.ofMillis(config.getLong("poll_interval_ms", 5_000L)))
			.withMaxChangesPerPoll(config.getInteger("max_changes_per_poll", 100))
			.withRequestConcurrency(config.getInteger("request_concurrency", 8))
			.withRequestIdleTimeout(Duration.ofMillis(
				config.getLong("request_idle_timeout_ms", 10_000L)
			))
			.withActionBatchSize(config.getInteger("action_batch_size", 100))
			.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private URI baseUri = URI.create(DEFAULT_BASE_URI);
		private Duration pollInterval = Duration.ofSeconds(5);
		private int maxChangesPerPoll = 100;
		private int requestConcurrency = 8;
		private Duration requestIdleTimeout = Duration.ofSeconds(10);
		private int actionBatchSize = 100;

		private Builder() {
		}

		public Builder withBaseUri(URI value) {
			baseUri = value;
			return this;
		}

		public Builder withPollInterval(Duration value) {
			pollInterval = value;
			return this;
		}

		public Builder withMaxChangesPerPoll(int value) {
			maxChangesPerPoll = value;
			return this;
		}

		public Builder withRequestConcurrency(int value) {
			requestConcurrency = value;
			return this;
		}

		public Builder withRequestIdleTimeout(Duration value) {
			requestIdleTimeout = value;
			return this;
		}

		public Builder withActionBatchSize(int value) {
			actionBatchSize = value;
			return this;
		}

		public HackerNewsOptions build() {
			return new HackerNewsOptions(
				baseUri,
				pollInterval,
				maxChangesPerPoll,
				requestConcurrency,
				requestIdleTimeout,
				actionBatchSize
			);
		}
	}
}
