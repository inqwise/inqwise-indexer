package com.inqwise.indexer.hot;

import java.util.List;
import java.util.Objects;

public record HotRoutingSnapshot(
	List<HotRoutingTargetSnapshot> targets,
	boolean truncated
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<HotRoutingTargetSnapshot> targets;
		private boolean truncated;

		private Builder() {
		}

		public Builder withTargets(List<HotRoutingTargetSnapshot> value) {
			targets = value == null ? null : List.copyOf(value);
			return this;
		}

		public Builder withTruncated(boolean value) {
			truncated = value;
			return this;
		}

		public HotRoutingSnapshot build() {
			return new HotRoutingSnapshot(
				List.copyOf(Objects.requireNonNull(targets, "targets")),
				truncated
			);
		}
	}
}
