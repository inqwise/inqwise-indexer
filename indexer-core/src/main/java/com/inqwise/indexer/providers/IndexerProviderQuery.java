package com.inqwise.indexer.providers;

import java.util.List;

import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;

public record IndexerProviderQuery(
	List<Integer> ids,
	List<Integer> targetIds,
	List<IndexerType> types,
	List<IndexerRole> roles,
	List<IndexerStatus> statuses,
	List<IndexerProvisioningState> provisioningStates,
	List<IndexerRuntimeState> runtimeStates,
	List<PublicationState> publicationStates,
	List<MutationState> mutationStates
) {
	public IndexerProviderQuery {
		ids = copy(ids);
		targetIds = copy(targetIds);
		types = copy(types);
		roles = copy(roles);
		statuses = copy(statuses);
		provisioningStates = copy(provisioningStates);
		runtimeStates = copy(runtimeStates);
		publicationStates = copy(publicationStates);
		mutationStates = copy(mutationStates);
	}

	private static <T> List<T> copy(List<T> values) {
		return values == null ? List.of() : List.copyOf(values);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<Integer> ids;
		private List<Integer> targetIds;
		private List<IndexerType> types;
		private List<IndexerRole> roles;
		private List<IndexerStatus> statuses;
		private List<IndexerProvisioningState> provisioningStates;
		private List<IndexerRuntimeState> runtimeStates;
		private List<PublicationState> publicationStates;
		private List<MutationState> mutationStates;

		private Builder() {
		}

		public Builder withIds(List<Integer> value) {
			ids = copy(value);
			return this;
		}

		public Builder withTargetIds(List<Integer> value) {
			targetIds = copy(value);
			return this;
		}

		public Builder withTypes(List<IndexerType> value) {
			types = copy(value);
			return this;
		}

		public Builder withRoles(List<IndexerRole> value) {
			roles = copy(value);
			return this;
		}

		public Builder withStatuses(List<IndexerStatus> value) {
			statuses = copy(value);
			return this;
		}

		public Builder withProvisioningStates(List<IndexerProvisioningState> value) {
			provisioningStates = copy(value);
			return this;
		}

		public Builder withRuntimeStates(List<IndexerRuntimeState> value) {
			runtimeStates = copy(value);
			return this;
		}

		public Builder withPublicationStates(List<PublicationState> value) {
			publicationStates = copy(value);
			return this;
		}

		public Builder withMutationStates(List<MutationState> value) {
			mutationStates = copy(value);
			return this;
		}

		public IndexerProviderQuery build() {
			return new IndexerProviderQuery(
				ids,
				targetIds,
				types,
				roles,
				statuses,
				provisioningStates,
				runtimeStates,
				publicationStates,
				mutationStates
			);
		}
	}
}
