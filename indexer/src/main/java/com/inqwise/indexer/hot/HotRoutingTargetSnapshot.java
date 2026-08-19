package com.inqwise.indexer.hot;

import java.util.List;
import java.util.Objects;

public record HotRoutingTargetSnapshot(
	Integer targetId,
	String targetName,
	List<Integer> hotIndexerIds,
	boolean indexersTruncated
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer targetId;
		private String targetName;
		private List<Integer> hotIndexerIds;
		private boolean indexersTruncated;

		private Builder() {
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withHotIndexerIds(List<Integer> value) {
			hotIndexerIds = value == null ? null : List.copyOf(value);
			return this;
		}

		public Builder withIndexersTruncated(boolean value) {
			indexersTruncated = value;
			return this;
		}

		public HotRoutingTargetSnapshot build() {
			return new HotRoutingTargetSnapshot(
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(targetName, "targetName"),
				List.copyOf(Objects.requireNonNull(hotIndexerIds, "hotIndexerIds")),
				indexersTruncated
			);
		}
	}
}
