package com.inqwise.indexer.query;

import java.util.List;
import java.util.Objects;

public record AllOfQueryFilter(List<QueryFilter> filters) implements QueryFilter {
	public AllOfQueryFilter {
		filters = List.copyOf(Objects.requireNonNull(filters, "filters"));
		if (filters.isEmpty() || filters.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("filters must contain non-null values");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<QueryFilter> filters;

		private Builder() {
		}

		public Builder withFilters(List<QueryFilter> value) {
			filters = value == null ? null : List.copyOf(value);
			return this;
		}

		public AllOfQueryFilter build() {
			return new AllOfQueryFilter(Objects.requireNonNull(filters, "filters"));
		}
	}
}
