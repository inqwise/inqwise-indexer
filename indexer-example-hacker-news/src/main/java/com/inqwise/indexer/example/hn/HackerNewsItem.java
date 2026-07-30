package com.inqwise.indexer.example.hn;

import java.util.List;
import java.util.Objects;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public record HackerNewsItem(
	long id,
	boolean deleted,
	boolean dead,
	String type,
	String by,
	Long time,
	String title,
	String url,
	String text,
	Long parent,
	Long poll,
	Integer score,
	Integer descendants,
	List<Long> kids,
	List<Long> parts
) {
	public HackerNewsItem {
		if (id < 1) {
			throw new IllegalArgumentException("id must be positive");
		}
		kids = List.copyOf(Objects.requireNonNull(kids, "kids"));
		parts = List.copyOf(Objects.requireNonNull(parts, "parts"));
	}

	public static HackerNewsItem fromJson(JsonObject json) {
		JsonArray kids = json.getJsonArray("kids", new JsonArray());
		JsonArray parts = json.getJsonArray("parts", new JsonArray());
		return builder()
			.withId(json.getLong("id"))
			.withDeleted(json.getBoolean("deleted", false))
			.withDead(json.getBoolean("dead", false))
			.withType(json.getString("type"))
			.withBy(json.getString("by"))
			.withTime(json.getLong("time"))
			.withTitle(json.getString("title"))
			.withUrl(json.getString("url"))
			.withText(json.getString("text"))
			.withParent(json.getLong("parent"))
			.withPoll(json.getLong("poll"))
			.withScore(json.getInteger("score"))
			.withDescendants(json.getInteger("descendants"))
			.withKids(kids.stream()
				.map(Number.class::cast)
				.map(Number::longValue)
				.toList())
			.withParts(parts.stream()
				.map(Number.class::cast)
				.map(Number::longValue)
				.toList())
			.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Long id;
		private boolean deleted;
		private boolean dead;
		private String type;
		private String by;
		private Long time;
		private String title;
		private String url;
		private String text;
		private Long parent;
		private Long poll;
		private Integer score;
		private Integer descendants;
		private List<Long> kids = List.of();
		private List<Long> parts = List.of();

		private Builder() {
		}

		public Builder withId(long value) {
			id = value;
			return this;
		}

		public Builder withDeleted(boolean value) {
			deleted = value;
			return this;
		}

		public Builder withDead(boolean value) {
			dead = value;
			return this;
		}

		public Builder withType(String value) {
			type = value;
			return this;
		}

		public Builder withBy(String value) {
			by = value;
			return this;
		}

		public Builder withTime(Long value) {
			time = value;
			return this;
		}

		public Builder withTitle(String value) {
			title = value;
			return this;
		}

		public Builder withUrl(String value) {
			url = value;
			return this;
		}

		public Builder withText(String value) {
			text = value;
			return this;
		}

		public Builder withParent(Long value) {
			parent = value;
			return this;
		}

		public Builder withPoll(Long value) {
			poll = value;
			return this;
		}

		public Builder withScore(Integer value) {
			score = value;
			return this;
		}

		public Builder withDescendants(Integer value) {
			descendants = value;
			return this;
		}

		public Builder withKids(List<Long> value) {
			kids = value == null ? null : List.copyOf(value);
			return this;
		}

		public Builder withParts(List<Long> value) {
			parts = value == null ? null : List.copyOf(value);
			return this;
		}

		public HackerNewsItem build() {
			return new HackerNewsItem(
				Objects.requireNonNull(id, "id"),
				deleted,
				dead,
				type,
				by,
				time,
				title,
				url,
				text,
				parent,
				poll,
				score,
				descendants,
				Objects.requireNonNull(kids, "kids"),
				Objects.requireNonNull(parts, "parts")
			);
		}
	}
}
