package com.inqwise.indexer.example.hn;

import java.util.List;
import java.util.Objects;

import io.vertx.core.json.JsonObject;

public record HackerNewsUpdates(List<Long> itemIds) {
	public HackerNewsUpdates {
		itemIds = List.copyOf(Objects.requireNonNull(itemIds, "itemIds"));
	}

	public static HackerNewsUpdates fromJson(JsonObject json) {
		return builder()
			.withItemIds(json.getJsonArray("items").stream()
				.map(Number.class::cast)
				.map(Number::longValue)
				.toList())
			.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<Long> itemIds = List.of();

		private Builder() {
		}

		public Builder withItemIds(List<Long> value) {
			itemIds = value == null ? null : List.copyOf(value);
			return this;
		}

		public HackerNewsUpdates build() {
			return new HackerNewsUpdates(Objects.requireNonNull(itemIds, "itemIds"));
		}
	}
}
