package com.inqwise.indexer.documents;

import java.util.List;
import java.util.Objects;

public record DocumentQueryResult(
	List<DocumentHit> hits,
	int offset,
	int limit,
	boolean hasMore,
	int publishedIndexCount
) {
	public DocumentQueryResult {
		hits = List.copyOf(Objects.requireNonNull(hits, "hits"));
		if (offset < 0) {
			throw new IllegalArgumentException("offset must not be negative");
		}
		if (limit < 1) {
			throw new IllegalArgumentException("limit must be positive");
		}
		if (publishedIndexCount < 0) {
			throw new IllegalArgumentException("publishedIndexCount must not be negative");
		}
	}

	@Override
	public List<DocumentHit> hits() {
		return List.copyOf(hits);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<DocumentHit> hits = List.of();
		private int offset;
		private int limit = DocumentQuery.DEFAULT_LIMIT;
		private boolean hasMore;
		private int publishedIndexCount;

		private Builder() {
		}

		public Builder withHits(List<DocumentHit> value) {
			hits = value == null ? List.of() : List.copyOf(value);
			return this;
		}

		public Builder withOffset(int value) {
			offset = value;
			return this;
		}

		public Builder withLimit(int value) {
			limit = value;
			return this;
		}

		public Builder withHasMore(boolean value) {
			hasMore = value;
			return this;
		}

		public Builder withPublishedIndexCount(int value) {
			publishedIndexCount = value;
			return this;
		}

		public DocumentQueryResult build() {
			return new DocumentQueryResult(
				hits,
				offset,
				limit,
				hasMore,
				publishedIndexCount
			);
		}
	}
}
