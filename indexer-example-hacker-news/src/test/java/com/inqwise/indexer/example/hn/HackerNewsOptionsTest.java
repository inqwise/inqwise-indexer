package com.inqwise.indexer.example.hn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class HackerNewsOptionsTest {
	@Test
	void readsApplicationConfigurationFromRoot() {
		HackerNewsOptions options = HackerNewsOptions.from(new JsonObject()
			.put("base_uri", "https://example.test/v0")
			.put("poll_interval_ms", 1234L)
			.put("max_changes_per_poll", 45)
			.put("request_concurrency", 6)
			.put("request_idle_timeout_ms", 7890L)
			.put("action_batch_size", 12));

		assertEquals(URI.create("https://example.test/v0"), options.baseUri());
		assertEquals(Duration.ofMillis(1234), options.pollInterval());
		assertEquals(45, options.maxChangesPerPoll());
		assertEquals(6, options.requestConcurrency());
		assertEquals(Duration.ofMillis(7890), options.requestIdleTimeout());
		assertEquals(12, options.actionBatchSize());
	}
}
