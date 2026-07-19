package com.inqwise.indexer.lifecycle;

import java.util.List;
import java.util.Objects;

public record TargetInvalidationEntries(
	List<TargetInvalidationEntry> entries,
	boolean truncated
) {
	public TargetInvalidationEntries {
		entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<TargetInvalidationEntry> entries;
		private boolean truncated;

		private Builder() {
		}

		public Builder withEntries(List<TargetInvalidationEntry> value) {
			entries = value == null ? null : List.copyOf(value);
			return this;
		}

		public Builder withTruncated(boolean value) {
			truncated = value;
			return this;
		}

		public TargetInvalidationEntries build() {
			return new TargetInvalidationEntries(entries, truncated);
		}
	}
}
