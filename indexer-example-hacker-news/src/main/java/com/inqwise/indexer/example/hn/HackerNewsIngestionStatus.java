package com.inqwise.indexer.example.hn;

import java.time.Instant;

import io.vertx.core.json.JsonObject;

public record HackerNewsIngestionStatus(
	boolean running,
	boolean pollInFlight,
	long pollsCompleted,
	long pollsSkipped,
	long itemsFetched,
	long itemFetchFailures,
	long actionsSubmitted,
	long unchangedItems,
	Instant lastPollStartedAt,
	Instant lastPollCompletedAt,
	String lastError
) {
	public JsonObject toJson() {
		return new JsonObject()
			.put("running", running)
			.put("poll_in_flight", pollInFlight)
			.put("polls_completed", pollsCompleted)
			.put("polls_skipped", pollsSkipped)
			.put("items_fetched", itemsFetched)
			.put("item_fetch_failures", itemFetchFailures)
			.put("actions_submitted", actionsSubmitted)
			.put("unchanged_items", unchangedItems)
			.put("last_poll_started_at", timestamp(lastPollStartedAt))
			.put("last_poll_completed_at", timestamp(lastPollCompletedAt))
			.put("last_error", lastError);
	}

	private static String timestamp(Instant value) {
		return value == null ? null : value.toString();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private boolean running;
		private boolean pollInFlight;
		private long pollsCompleted;
		private long pollsSkipped;
		private long itemsFetched;
		private long itemFetchFailures;
		private long actionsSubmitted;
		private long unchangedItems;
		private Instant lastPollStartedAt;
		private Instant lastPollCompletedAt;
		private String lastError;

		private Builder() {
		}

		public Builder withRunning(boolean value) {
			running = value;
			return this;
		}

		public Builder withPollInFlight(boolean value) {
			pollInFlight = value;
			return this;
		}

		public Builder withPollsCompleted(long value) {
			pollsCompleted = value;
			return this;
		}

		public Builder withPollsSkipped(long value) {
			pollsSkipped = value;
			return this;
		}

		public Builder withItemsFetched(long value) {
			itemsFetched = value;
			return this;
		}

		public Builder withItemFetchFailures(long value) {
			itemFetchFailures = value;
			return this;
		}

		public Builder withActionsSubmitted(long value) {
			actionsSubmitted = value;
			return this;
		}

		public Builder withUnchangedItems(long value) {
			unchangedItems = value;
			return this;
		}

		public Builder withLastPollStartedAt(Instant value) {
			lastPollStartedAt = value;
			return this;
		}

		public Builder withLastPollCompletedAt(Instant value) {
			lastPollCompletedAt = value;
			return this;
		}

		public Builder withLastError(String value) {
			lastError = value;
			return this;
		}

		public HackerNewsIngestionStatus build() {
			return new HackerNewsIngestionStatus(
				running,
				pollInFlight,
				pollsCompleted,
				pollsSkipped,
				itemsFetched,
				itemFetchFailures,
				actionsSubmitted,
				unchangedItems,
				lastPollStartedAt,
				lastPollCompletedAt,
				lastError
			);
		}
	}
}
