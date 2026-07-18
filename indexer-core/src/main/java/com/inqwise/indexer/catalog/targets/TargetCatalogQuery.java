package com.inqwise.indexer.catalog.targets;

import java.util.List;

public record TargetCatalogQuery(
	List<Integer> ids,
	List<String> targetNames,
	List<TargetStatus> statuses,
	List<TargetProvisioningState> provisioningStates
) {
	public TargetCatalogQuery {
		ids = copy(ids);
		targetNames = copy(targetNames);
		statuses = copy(statuses);
		provisioningStates = copy(provisioningStates);
	}

	private static <T> List<T> copy(List<T> values) {
		return values == null ? List.of() : List.copyOf(values);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<Integer> ids = List.of();
		private List<String> targetNames = List.of();
		private List<TargetStatus> statuses = List.of();
		private List<TargetProvisioningState> provisioningStates = List.of();

		private Builder() {
		}

		public Builder withIds(List<Integer> values) {
			ids = copy(values);
			return this;
		}

		public Builder withTargetNames(List<String> values) {
			targetNames = copy(values);
			return this;
		}

		public Builder withStatuses(List<TargetStatus> values) {
			statuses = copy(values);
			return this;
		}

		public Builder withProvisioningStates(List<TargetProvisioningState> values) {
			provisioningStates = copy(values);
			return this;
		}

		public TargetCatalogQuery build() {
			return new TargetCatalogQuery(ids, targetNames, statuses, provisioningStates);
		}
	}
}
