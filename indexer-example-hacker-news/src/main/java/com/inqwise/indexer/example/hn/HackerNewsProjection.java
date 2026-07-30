package com.inqwise.indexer.example.hn;

import java.util.Objects;

import com.inqwise.indexer.actions.IndexerActionItem;

public record HackerNewsProjection(
	long itemId,
	String fingerprint,
	IndexerActionItem action
) {
	public HackerNewsProjection {
		if (itemId < 1) {
			throw new IllegalArgumentException("itemId must be positive");
		}
		Objects.requireNonNull(fingerprint, "fingerprint");
		Objects.requireNonNull(action, "action");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Long itemId;
		private String fingerprint;
		private IndexerActionItem action;

		private Builder() {
		}

		public Builder withItemId(long value) {
			itemId = value;
			return this;
		}

		public Builder withFingerprint(String value) {
			fingerprint = value;
			return this;
		}

		public Builder withAction(IndexerActionItem value) {
			action = value;
			return this;
		}

		public HackerNewsProjection build() {
			return new HackerNewsProjection(
				Objects.requireNonNull(itemId, "itemId"),
				fingerprint,
				action
			);
		}
	}
}
