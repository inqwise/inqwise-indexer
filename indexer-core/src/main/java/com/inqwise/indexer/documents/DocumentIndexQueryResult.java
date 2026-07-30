package com.inqwise.indexer.documents;

import java.util.List;
import java.util.Objects;

public record DocumentIndexQueryResult(
	List<DocumentIndexHit> hits,
	boolean hasMore
) {
	public DocumentIndexQueryResult {
		hits = List.copyOf(Objects.requireNonNull(hits, "hits"));
	}

	@Override
	public List<DocumentIndexHit> hits() {
		return List.copyOf(hits);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<DocumentIndexHit> hits = List.of();
		private boolean hasMore;

		private Builder() {
		}

		public Builder withHits(List<DocumentIndexHit> value) {
			hits = value == null ? List.of() : List.copyOf(value);
			return this;
		}

		public Builder withHasMore(boolean value) {
			hasMore = value;
			return this;
		}

		public DocumentIndexQueryResult build() {
			return new DocumentIndexQueryResult(hits, hasMore);
		}
	}
}
