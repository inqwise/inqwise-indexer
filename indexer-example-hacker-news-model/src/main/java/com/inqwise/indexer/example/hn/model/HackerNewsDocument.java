package com.inqwise.indexer.example.hn.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record HackerNewsDocument(
	long id,
	String type,
	String author,
	long time,
	String title,
	String url,
	String text,
	Long parent,
	Long poll,
	int score,
	int descendants,
	List<Long> kids,
	List<Long> parts,
	String source
) {
	private static final Set<String> SUPPORTED_TYPES = Set.of(
		"job",
		"story",
		"comment",
		"poll",
		"pollopt"
	);

	public HackerNewsDocument {
		if (id < 1) {
			throw new IllegalArgumentException("id must be positive");
		}
		if (type == null || type.isBlank()) {
			throw new IllegalArgumentException("type must not be blank");
		}
		if (!SUPPORTED_TYPES.contains(type)) {
			throw new IllegalArgumentException("unsupported Hacker News type: " + type);
		}
		if (time < 0) {
			throw new IllegalArgumentException("time must not be negative");
		}
		if ("story".equals(type) && (title == null || title.isBlank())) {
			throw new IllegalArgumentException("story title must not be blank");
		}
		if (parent != null && parent < 1) {
			throw new IllegalArgumentException("parent must be positive");
		}
		if (poll != null && poll < 1) {
			throw new IllegalArgumentException("poll must be positive");
		}
		if (score < 0) {
			throw new IllegalArgumentException("score must not be negative");
		}
		if (descendants < 0) {
			throw new IllegalArgumentException("descendants must not be negative");
		}
		kids = positiveIds(kids, "kids");
		parts = positiveIds(parts, "parts");
		if (!HackerNewsDocumentConstants.SOURCE_NAME.equals(source)) {
			throw new IllegalArgumentException("source must be hacker-news");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	private static List<Long> positiveIds(List<Long> values, String name) {
		List<Long> copy = List.copyOf(Objects.requireNonNull(values, name));
		if (copy.stream().anyMatch(value -> value == null || value < 1)) {
			throw new IllegalArgumentException(name + " must contain only positive ids");
		}
		return copy;
	}

	public static final class Builder {
		private Long id;
		private String type;
		private String author;
		private Long time;
		private String title;
		private String url;
		private String text;
		private Long parent;
		private Long poll;
		private int score;
		private int descendants;
		private List<Long> kids = List.of();
		private List<Long> parts = List.of();
		private String source = HackerNewsDocumentConstants.SOURCE_NAME;

		private Builder() {
		}

		public Builder withId(long value) {
			id = value;
			return this;
		}

		public Builder withType(String value) {
			type = value;
			return this;
		}

		public Builder withAuthor(String value) {
			author = value;
			return this;
		}

		public Builder withTime(long value) {
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

		public Builder withScore(int value) {
			score = value;
			return this;
		}

		public Builder withDescendants(int value) {
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

		public Builder withSource(String value) {
			source = value;
			return this;
		}

		public HackerNewsDocument build() {
			return new HackerNewsDocument(
				Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(type, "type"),
				author,
				Objects.requireNonNull(time, "time"),
				title,
				url,
				text,
				parent,
				poll,
				score,
				descendants,
				Objects.requireNonNull(kids, "kids"),
				Objects.requireNonNull(parts, "parts"),
				Objects.requireNonNull(source, "source")
			);
		}
	}
}
